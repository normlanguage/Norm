package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreBindingKind;
import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessorPipeline;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.CompilationScope;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class JavaManagedMethodProjectionTest {
  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void compilesAbstractGenericMethodsAndIndexesOnlyActualBodies(boolean archived) throws Exception {
    var source =
        SourceFile.of(
            root.resolve("repository.norm"),
            """
            package todo
            import std.core.Exception
            import std.annotation.ManagedImplementation
            import std.annotation.FunctionTarget
            import std.annotation.ParameterTarget
            import std.annotation.RuntimeRetention
            annotation Query implements FunctionTarget, RuntimeRetention { String value }
            annotation Named implements ParameterTarget, RuntimeRetention { String value }
            annotation Generated implements ManagedImplementation, RuntimeRetention {}
            @Generated() class Repository<T> {
              @Query("select todo where id = :id")
              T? find(@Named("id") Long id)
              @Query("select todo where :completed is null or todo.completed = :completed")
              List<T> findByCompleted(Boolean? completed)
              @Query("delete from Todo todo where todo.completed = true")
              Void clearCompleted()
              R map<R>(R item)
              Long count() { 1 }
              List<T> listed() { this.findByCompleted(null) }
              List<List<T?>> nested() { [] }
            }
            class Inherited<T> extends Repository<T> {}
            @Generated() class KeyedRepository<I, T> extends Inherited<T> { T? byKey(I id) }
            class StringRepository extends KeyedRepository<Long, String> {}
            class Implemented<T> extends Repository<T> {
              T? find(Long id) { null }
              List<T> findByCompleted(Boolean? completed) { [] }
              Void clearCompleted() {}
              R map<R>(R item) { item }
            }
            @Generated() class ManagedService {
              List<String> find(Boolean? completed)
              Void fail()
              Void clear()
              String required()
            }
            class Client {
              String? keyed(StringRepository repository) { repository.byKey(42) }
              String? genericNullable(StringRepository repository) { repository.map<String?>(null) }
              List<String> inheritedBody(StringRepository repository) { repository.listed() }
              List<String> generic(StringRepository repository) {
                var items = repository.map<List<String>>(repository.findByCompleted(null))
                require(condition: items.size() == 1, message: "generic list was not materialized")
                items
              }
              List<String> genericReference(StringRepository repository) {
                Function<List<String>(List<String>)> map = repository.map
                map(repository.findByCompleted(false))
              }
              List<String> run(ManagedService service, Boolean? completed) { service.find(completed) }
              List<String>? optional(ManagedService? service) { service?.find(null) }
              Void clear(ManagedService service) { service.clear() }
              String required(ManagedService service) { service.required() }
              List<String> reference(ManagedService service, Boolean? completed) {
                var find = service.find
                find(completed)
              }
              List<String> unbound(ManagedService service, Boolean? completed) {
                Function<List<String>(ManagedService, Boolean?)> find = ManagedService.find.function
                find(service, completed)
              }
              String failure(ManagedService service) {
                try { service.fail() return "missing failure" }
                catch Exception failure { return failure.message }
              }
            }
            Void main() {}
            """);
    var environment = ProjectEnvironment.bootstrap(new TruffleExecutionBackend());
    try (var compiler = environment.compilerSession()) {
      var compilation = compiler.compile(source);
      assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
      var artifact = compilation.output().orElseThrow().artifact();
      var scope = CompilationScope.anonymous(List.of(source));
      var output =
          new JavaAnnotationProcessorPipeline()
              .process(
                  artifact,
                  List.of(),
                  JarBindingClasspath.prepare(List.of()),
                  root,
                  scope,
                  source.id(),
                  Set.of());
      try (var loader =
          new URLClassLoader(
              new java.net.URL[] {output.classes().toUri().toURL()}, getClass().getClassLoader())) {
        var repository = loader.loadClass("todo.Repository");
        assertTrue(Modifier.isAbstract(repository.getModifiers()));
        assertTrue(Modifier.isAbstract(loader.loadClass("todo.Inherited").getModifiers()));
        assertFalse(Modifier.isAbstract(loader.loadClass("todo.Implemented").getModifiers()));
        var find = repository.getDeclaredMethod("find", long.class);
        assertTrue(Modifier.isAbstract(find.getModifiers()));
        assertEquals("id", find.getParameters()[0].getName());
        var query =
            java.util.Arrays.stream(find.getDeclaredAnnotations())
                .filter(value -> value.annotationType().getName().equals("todo.Query"))
                .findFirst()
                .orElseThrow();
        assertEquals(
            "select todo where id = :id", query.annotationType().getMethod("value").invoke(query));
        var named =
            java.util.Arrays.stream(find.getParameters()[0].getDeclaredAnnotations())
                .filter(value -> value.annotationType().getName().equals("todo.Named"))
                .findFirst()
                .orElseThrow();
        assertEquals("id", named.annotationType().getMethod("value").invoke(named));
        assertTrue(find.getGenericReturnType() instanceof TypeVariable<?>);
        var nested =
            (java.lang.reflect.AnnotatedParameterizedType)
                repository.getDeclaredMethod("nested").getAnnotatedReturnType();
        var inner =
            (java.lang.reflect.AnnotatedParameterizedType)
                nested.getAnnotatedActualTypeArguments()[0];
        var item = inner.getAnnotatedActualTypeArguments()[0];
        assertTrue(item.getType() instanceof TypeVariable<?>);
        assertTrue(
            java.util.Arrays.stream(item.getAnnotations())
                .anyMatch(
                    value ->
                        value
                            .annotationType()
                            .getName()
                            .equals("org.jspecify.annotations.Nullable")));
        var filtered = repository.getDeclaredMethod("findByCompleted", Boolean.class);
        assertTrue(
            java.util.Arrays.stream(filtered.getAnnotatedParameterTypes()[0].getAnnotations())
                .anyMatch(
                    value ->
                        value
                            .annotationType()
                            .getName()
                            .equals("org.jspecify.annotations.Nullable")));
        assertTrue(
            java.util.Arrays.stream(find.getAnnotatedReturnType().getAnnotations())
                .anyMatch(
                    value ->
                        value
                            .annotationType()
                            .getName()
                            .equals("org.jspecify.annotations.Nullable")));
        assertTrue(Modifier.isAbstract(filtered.getModifiers()));
        var result = (java.lang.reflect.ParameterizedType) filtered.getGenericReturnType();
        assertEquals(List.class, result.getRawType());
        assertEquals(repository.getTypeParameters()[0], result.getActualTypeArguments()[0]);
        var clear = repository.getDeclaredMethod("clearCompleted");
        assertTrue(Modifier.isAbstract(clear.getModifiers()));
        assertEquals(void.class, clear.getReturnType());
        var map = repository.getDeclaredMethod("map", Object.class);
        assertEquals(1, map.getTypeParameters().length);
        assertEquals(map.getTypeParameters()[0], map.getGenericReturnType());
      }
      for (var binding : artifact.namespace().bindings()) {
        if (binding.kind() != CoreBindingKind.METHOD_SIGNATURE
            || !binding.packageName().equals("todo")) continue;
        assertTrue(output.methods().instanceMethods().containsKey(binding.definition()));
        assertFalse(output.methods().entryPoints().contains(binding.definition()));
      }
      var host = root.resolve("HostService.java");
      java.nio.file.Files.writeString(
          host,
          """
          package todo;
          class HostRepository extends StringRepository {
            public String find(long id) { return "任务"; }
            public String byKey(Long id) { return "任务" + id; }
            public java.util.List<String> findByCompleted(Boolean completed) {
              return java.util.List.of(completed == null ? "全部" : "未完成");
            }
            public void clearCompleted() {}
            public <R> R map(R item) { return item; }
          }
          public class HostService extends ManagedService {
            public int clears;
            public java.util.List<String> find(Boolean completed) {
              return java.util.List.of(completed == null ? "全部" : completed ? "已完成" : "未完成");
            }
            public void fail() { throw new IllegalStateException("查询失败"); }
            public void clear() { clears++; }
            public String required() { return null; }
          }
          """);
      var bridge =
          Path.of(
              dev.w0fv1.norm.bridge.JavaApplicationBridge.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI());
      assertEquals(
          0,
          javax.tools.ToolProvider.getSystemJavaCompiler()
              .run(
                  null,
                  null,
                  null,
                  "-encoding",
                  "UTF-8",
                  "-classpath",
                  output.classes() + java.io.File.pathSeparator + bridge,
                  "-d",
                  output.classes().toString(),
                  host.toString()));
      try (var runtime =
          new dev.w0fv1.norm.jvm.JvmJarBindingRuntime(List.of(), List.of(output.classes()))) {
        var context =
            dev.w0fv1.norm.execution.ExecutionContext.of(
                    new java.io.PrintWriter(new java.io.StringWriter()))
                .withJarBindingRuntime(runtime)
                .withJavaApplicationEntrypoint(
                    loader -> {
                      try {
                        var serviceType = loader.loadClass("todo.ManagedService");
                        var service =
                            loader.loadClass("todo.HostService").getConstructor().newInstance();
                        var clientType = loader.loadClass("todo.Client");
                        var client = clientType.getConstructor().newInstance();
                        var repositoryType = loader.loadClass("todo.StringRepository");
                        var hostConstructor =
                            loader.loadClass("todo.HostRepository").getDeclaredConstructor();
                        hostConstructor.setAccessible(true);
                        var repository = hostConstructor.newInstance();
                        assertEquals(
                            List.of("全部"),
                            clientType
                                .getMethod("generic", repositoryType)
                                .invoke(client, repository));
                        assertEquals(
                            List.of("未完成"),
                            clientType
                                .getMethod("genericReference", repositoryType)
                                .invoke(client, repository));
                        org.junit.jupiter.api.Assertions.assertNull(
                            clientType
                                .getMethod("genericNullable", repositoryType)
                                .invoke(client, repository));
                        assertEquals(
                            List.of("全部"),
                            clientType
                                .getMethod("inheritedBody", repositoryType)
                                .invoke(client, repository));
                        assertEquals(
                            "任务42",
                            clientType
                                .getMethod("keyed", repositoryType)
                                .invoke(client, repository));
                        var run = clientType.getMethod("run", serviceType, Boolean.class);
                        assertEquals(List.of("全部"), run.invoke(client, service, null));
                        assertEquals(List.of("已完成"), run.invoke(client, service, true));
                        assertEquals(List.of("未完成"), run.invoke(client, service, false));
                        org.junit.jupiter.api.Assertions.assertNull(
                            clientType
                                .getMethod("optional", serviceType)
                                .invoke(client, new Object[] {null}));
                        clientType.getMethod("clear", serviceType).invoke(client, service);
                        assertEquals(1, service.getClass().getField("clears").get(service));
                        var nullFailure =
                            org.junit.jupiter.api.Assertions.assertThrows(
                                java.lang.reflect.InvocationTargetException.class,
                                () ->
                                    clientType
                                        .getMethod("required", serviceType)
                                        .invoke(client, service));
                        assertEquals(
                            "Java application value is unexpectedly null",
                            nullFailure.getCause().getMessage());
                        assertEquals(
                            List.of("全部"),
                            clientType
                                .getMethod("reference", serviceType, Boolean.class)
                                .invoke(client, service, null));
                        assertEquals(
                            List.of("已完成"),
                            clientType
                                .getMethod("unbound", serviceType, Boolean.class)
                                .invoke(client, service, true));
                        assertEquals(
                            "查询失败",
                            clientType.getMethod("failure", serviceType).invoke(client, service));
                      } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                      }
                    });
        var application =
            new dev.w0fv1.norm.runtime.ApplicationProgramData(
                artifact,
                dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(
                    artifact, output.methods().entryPoints()),
                List.of(),
                "todo");
        if (archived) {
          var archive = root.resolve("application.bin");
          dev.w0fv1.norm.runtime.ApplicationProgramArchive.write(application, archive);
          application = dev.w0fv1.norm.runtime.ApplicationProgramArchive.read(archive);
        }
        new TruffleExecutionBackend()
            .execute(application.artifact(), application.execution(), context);
      }
    }
  }
}
