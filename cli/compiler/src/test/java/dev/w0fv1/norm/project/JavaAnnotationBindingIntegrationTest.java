package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JavaApplicationMethodIndex;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaAnnotationBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void scansGeneratesAppliesAndReflectsAJavaAnnotationAsOrdinaryNorm() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("sample/binding"));
    Path jar = annotationJar(moduleRoot.resolve("lib/annotations.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/annotations.jar",
                integrity: sha256("%s")
              ),
              api: [
                jarType(name: "Endpoint", members: ["enabled", "order", "path", "protocol", "protocols", "tags"]),
                jarType(name: "Box", members: ["get"]),
                jarType(name: "Converter", members: ["convert", "fallback"]),
                jarType(name: "GeneratedInvoker", members: ["callbacks", "contextRoundTrip", "contextValue", "failure", "frameworkAllocated", "hydrate", "invoke", "managed", "mutate", "proxy", "read", "write"])
              ]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample.binding

        import std.core.Exception
        import std.io.Resource

        interface DefaultContract {
          Void init() {}
          Void dispose() {}
          String prefix()
          String greet(String name) { this.prefix() + name }
        }

        class DefaultController implements DefaultContract {
          String prefix() { "default:" }
        }

        interface DerivedContract extends DefaultContract {}

        class AlternateController implements DerivedContract {
          String prefix() { "alternate:" }
        }

        interface LocalResource extends Resource {}

        class ResourceController implements LocalResource {
          Void close() {}
        }

        class Callbacks {
          List<List<String?>> echoLists(List<List<String?>> items) {
            require(condition: items[0][0] == null, message: "nullable list item was lost")
            items
          }
          Function<Void()> bind(Function<Void()> action) { action }
          Function<Void(String)> bind(Function<Void(String)> action) { action }
          Function<String(String)> transform() { (String text) { "callback:" + text } }
          String apply(Function<String(String)> transform) { transform("Norm") }
          String catchFailure(Function<String(String)> transform) {
            try { return transform("Norm") } catch Exception failure { return failure.message }
          }
          Function<Integer(Integer, Integer)> sum() { (Integer first, Integer second) { first + second } }
          Function<Void()> fail() { () { throw Exception(message: "guest-failure") } }
          Void released(Function<Void(Resource)> callback) {}
        }

        @Endpoint(path: "/bbs", protocol: Endpoint_Protocol.HTTP)
        class Controller {
          private String greeting = "Hello, "
          private Integer greetings = 0
          String suffix = ""

          @Endpoint(path: "/greet")
          String greet(String name) {
            greetings = greetings + 1
            require(condition: greetings > 0, message: "private state must remain writable after Java attachment")
            return greeting + name + suffix
          }

          @Endpoint(path: "/response")
          Response response() {
            return Response(message: "Norm DTO")
          }

          @Endpoint(path: "/echo")
          String echo(Response response) {
            return response.message
          }

          Void rename(Response response) {
            response.message = "Norm DTO"
          }

          String context() {
            return generatedInvokerContextValue() ?? ""
          }

          String fail() {
            throw Exception(message: "boundary failure")
          }
        }

        class ChildController extends Controller {
          ChildController() {
            super()
          }
        }

        @Endpoint(path: "/generic")
        class GenericBase<T> {
          T value

          GenericBase(T value) {
            this.value = value
          }
        }

        @Endpoint(path: "/generic-child")
        class GenericChild extends GenericBase<String> {
          GenericChild() {
            super(value: "generic")
          }
        }

        @Endpoint(path: "/response")
        class Response {
          String message

          Response() {
            this.message = ""
          }

          Response(@Endpoint(path: "/message") String message) {
            this.message = message
          }

          Response(Integer number) {
            this.message = "Number ${number}"
          }
        }

        @Endpoint(path: "/managed-response")
        class ManagedResponse {
          String message

          ManagedResponse(String message) {
            this.message = message
          }
        }

        @Endpoint(path: "/box")
        class BoxConsumer {
          Box<String> box
        }

        @Endpoint(path: "/string-box")
        interface StringBox extends Box<String> {
        }

        @Endpoint(path: "/string-box-value")
        class StringBoxValue implements Box<String> {
          String get() {
            return "value"
          }
        }

        @Endpoint(path: "/converter")
        class StringConverter implements Converter<String> {
          String convert(String? value) {
            return value ?? ""
          }

          String convert(Integer value) {
            return value.toString()
          }
        }

        @Endpoint(path: "/first")
        @Endpoint(path: "/second")
        class RepeatedController {
        }

        @Endpoint(path: "/health", enabled: false)
        Void health() {
        }

        Void main() {
          require(condition: generatedInvokerCallbacks() == "callback:Norm",
            message: "Java must call the exported Norm function")
          require(condition: generatedInvokerInvoke(arg0: "sample.binding.DefaultController", arg1: "greet", arg2: "Norm") == "default:Norm",
            message: "Java callers must execute inherited Norm interface defaults")
          require(condition: generatedInvokerInvoke(arg0: "sample.binding.AlternateController", arg1: "greet", arg2: "Norm") == "alternate:Norm",
            message: "shared interface defaults must dispatch to each concrete receiver")
          var proxy = generatedInvokerProxy<Controller>(Controller.class)
          if proxy == null { throw Exception(message: "proxy missing") }
          require(condition: proxy.greet("Norm") == "proxy:Hello, Norm", message: "ordinary calls must enter Java proxy overrides")
          require(condition: proxy.context() == "proxy-context", message: "nested host calls must preserve proxy thread context")
          Endpoint? endpoint = Controller.class.annotation<Endpoint>()
          if endpoint != null {
            printLine(endpoint.path)
            printLine(endpoint.enabled)
            printLine(endpoint.order)
            printLine(endpoint.tags[0])
            if endpoint.protocol == Endpoint_Protocol.HTTP {
              printLine("HTTP")
            }
          }
          printLine(
            generatedInvokerInvoke(
              arg0: "sample.binding.Controller",
              arg1: "greet",
              arg2: "Norm"
            )
          )
          printLine(generatedInvokerManaged())
          printLine(generatedInvokerMutate())
          Response hydrated = Response(message: "Initial")
          generatedInvokerHydrate(hydrated)
          printLine(hydrated.message)
          printLine(generatedInvokerRead())
          printLine(generatedInvokerWrite())
          printLine(generatedInvokerContextRoundTrip())
          printLine(generatedInvokerFailure())
          printLine(generatedInvokerFrameworkAllocated())
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    String processorOutput;
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("maven-cache"));
        ApplicationRunner launcher =
            new ApplicationRunner(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      Path supportJar = temporaryDirectory.resolve("selected-support.jar");
      try (var archive = new JarOutputStream(Files.newOutputStream(supportJar))) {}
      var support =
          new ResolvedJarArtifact(
              new MavenJarIdentity(new MavenArtifactCoordinate("sample", "support", "2")),
              supportJar,
              Sha256Digest.compute(supportJar));
      try (var compiled =
          launcher.compileApplication(
              entry,
              message -> {},
              java.util.List.of(
                  new ResolvedJarGraph(
                      support, java.util.List.of(support), java.util.List.of())))) {
        assertTrue(compiled.result().isSuccess(), compiled.result().diagnostics().toString());
        var annotationOutput = compiled.application().orElseThrow().annotations();
        var index =
            JavaApplicationMethodIndex.analyze(
                annotationOutput.classes(), annotationOutput.stubs());
        var artifact = compiled.result().output().orElseThrow().artifact();
        var constructors = new java.util.HashSet<DefinitionId>();
        for (var record : artifact.program().definitions()) {
          if (record.definition() instanceof CoreDefinition.Aggregate aggregate
              && aggregate.nominalType().name().equals("Response")) {
            aggregate
                .constructors()
                .forEach(
                    reference ->
                        constructors.add(
                            artifact
                                .program()
                                .resolve(record.id(), (DefinitionReference) reference)));
          }
        }
        assertEquals(3, constructors.size());
        assertTrue(index.entryPoints().containsAll(constructors));
        assertTrue(java.util.Collections.disjoint(index.instanceMethods().keySet(), constructors));
        for (var id : index.entryPoints()) {
          assertTrue(
              artifact.program().definition(id).orElseThrow() instanceof CoreDefinition.Callable);
        }
        var capturedSupport =
            compiled.application().orElseThrow().javaClasspath().artifacts().stream()
                .filter(value -> value.identity().equals(support.identity()))
                .findFirst()
                .orElseThrow();
        assertEquals(support.content(), capturedSupport.content());
        assertTrue(
            Files.readString(
                    compiled.application().orElseThrow().annotations().root().resolve("javac.args"))
                .contains(capturedSupport.file().toString().replace('\\', '/')));
        processorOutput =
            Files.readString(
                compiled
                    .application()
                    .orElseThrow()
                    .annotations()
                    .classes()
                    .resolve("processor/endpoints.txt"));
      }
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "/bbs",
            "true",
            "7",
            "http",
            "HTTP",
            "Hello, Norm",
            "Hydrated DTO",
            "Norm DTO",
            "Java Hydrated DTO",
            "Norm DTO",
            "Java DTO",
            "framework-context",
            "boundary failure",
            "Framework Allocated",
            ""),
        output.toString());
    assertEquals(
        String.join(
            System.lineSeparator(),
            "sample.binding.BoxConsumer:/box:http,json:HTTPS",
            "sample.binding.ChildController:/bbs:http,json:HTTP",
            "sample.binding.Controller:/bbs:http,json:HTTP",
            "sample.binding.GenericBase:/generic:http,json:HTTPS",
            "sample.binding.GenericChild:/generic-child:http,json:HTTPS",
            "sample.binding.ManagedResponse:/managed-response:http,json:HTTPS",
            "sample.binding.RepeatedController:/first,/second:http,json:HTTPS",
            "sample.binding.Response:/response:http,json:HTTPS",
            "sample.binding.StringBox:/string-box:http,json:HTTPS",
            "sample.binding.StringBoxValue:/string-box-value:http,json:HTTPS",
            "sample.binding.StringConverter:/converter:http,json:HTTPS",
            ""),
        processorOutput);
  }

  private static Path annotationJar(Path path) throws Exception {
    Path sourceRoot = Files.createDirectories(path.getParent().resolve("processor-source"));
    Path classes = Files.createDirectories(path.getParent().resolve("processor-classes"));
    Path annotationSource = sourceRoot.resolve("sample/Endpoint.java");
    Files.createDirectories(annotationSource.getParent());
    Files.writeString(
        annotationSource,
        """
        package sample;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Inherited;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Repeatable;
        import java.lang.annotation.Target;

        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
        @Retention(RetentionPolicy.RUNTIME)
        @Repeatable(Endpoints.class)
        @Inherited
        public @interface Endpoint {
          enum Protocol {
            HTTP,
            HTTPS
          }

          boolean enabled() default true;
          int order() default 7;
          String path();
          Protocol protocol() default Protocol.HTTPS;
          Protocol[] protocols() default {Protocol.HTTP};
          String[] tags() default {"http", "json"};
        }
        """);
    Path containerSource = sourceRoot.resolve("sample/Endpoints.java");
    Files.writeString(
        containerSource,
        """
        package sample;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Inherited;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
        @Retention(RetentionPolicy.RUNTIME)
        @Inherited
        public @interface Endpoints {
          Endpoint[] value();
        }
        """);
    Path boxSource = sourceRoot.resolve("sample/Box.java");
    Files.writeString(
        boxSource,
        """
        package sample;

        public interface Box<T> {
          T get();
        }
        """);
    Path converterSource = sourceRoot.resolve("sample/Converter.java");
    Files.writeString(
        converterSource,
        """
        package sample;

        public interface Converter<T> {
          T convert(String value);
          T convert(int value);

          default T fallback(String value) {
            return convert(value);
          }
        }
        """);
    Path processorSource = sourceRoot.resolve("sample/EndpointProcessor.java");
    Files.writeString(
        processorSource,
        """
        package sample;

        import java.io.IOException;
        import java.io.Writer;
        import java.util.Comparator;
        import java.util.Arrays;
        import java.util.Set;
        import java.util.stream.Collectors;
        import javax.annotation.processing.AbstractProcessor;
        import javax.annotation.processing.RoundEnvironment;
        import javax.annotation.processing.SupportedAnnotationTypes;
        import javax.annotation.processing.SupportedSourceVersion;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.element.Element;
        import javax.lang.model.element.TypeElement;
        import javax.tools.StandardLocation;

        @SupportedAnnotationTypes("sample.Endpoint")
        @SupportedSourceVersion(SourceVersion.RELEASE_17)
        public final class EndpointProcessor extends AbstractProcessor {
          private boolean written;

          @Override
          public boolean process(
              Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (written || roundEnvironment.processingOver()) return false;
            String endpoints =
                roundEnvironment.getRootElements().stream()
                    .filter(element -> element instanceof TypeElement)
                    .filter(
                        element ->
                            ((TypeElement) element).getAnnotationsByType(Endpoint.class).length > 0)
                    .map(
                        element -> {
                          TypeElement type = (TypeElement) element;
                          Endpoint[] applied = type.getAnnotationsByType(Endpoint.class);
                          return type.getQualifiedName()
                              + ":"
                              + Arrays.stream(applied)
                                  .map(Endpoint::path)
                                  .collect(Collectors.joining(","))
                              + ":"
                              + String.join(",", applied[0].tags())
                              + ":"
                              + applied[0].protocol().name();
                        })
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
            try (Writer output =
                processingEnv
                    .getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", "processor/endpoints.txt")
                    .openWriter()) {
              output.write(endpoints);
            } catch (IOException exception) {
              throw new IllegalStateException(exception);
            }
            try (Writer output = processingEnv.getFiler().createSourceFile("sample.binding.ControllerProxy").openWriter()) {
              output.write("package sample.binding; public class ControllerProxy extends Controller { public String greet(String name) { return \\\"proxy:\\\" + super.greet(name); } public String context() { sample.GeneratedInvoker.beginContext(); try { return super.context(); } finally { sample.GeneratedInvoker.endContext(); } } }");
            } catch (IOException exception) {
              throw new IllegalStateException(exception);
            }
            written = true;
            return false;
          }
        }
        """);
    Path invokerSource = sourceRoot.resolve("sample/GeneratedInvoker.java");
    Files.writeString(
        invokerSource,
        """
        package sample;

        public final class GeneratedInvoker {
          public static String callbacks() throws Exception {
            var componentType = Class.forName("sample.binding.DefaultController");
            Object component = componentType.getConstructor().newInstance();
            componentType.getMethod("init").invoke(component);
            componentType.getMethod("dispose").invoke(component);
            var type = Class.forName("sample.binding.Callbacks");
            Object receiver = type.getConstructor().newInstance();
            var lists = type.getMethod("echoLists", java.util.List.class);
            var input = java.util.List.of(java.util.Arrays.asList(null, "中文任务"));
            var output = (java.util.List<?>) lists.invoke(receiver, input);
            if (!output.equals(input)) throw new AssertionError("nested list round trip failed");
            try {
              ((java.util.List<?>) output.get(0)).clear();
              throw new AssertionError("Norm list was exposed as mutable");
            } catch (UnsupportedOperationException expected) {}
            var callback = (java.util.function.Function<String, String>) type.getMethod("transform").invoke(receiver);
            type.getMethod("bind", Runnable.class);
            type.getMethod("bind", java.util.function.Consumer.class);
            java.util.function.Function<String, String> incoming = text -> "host:" + text;
            if (!type.getMethod("apply", java.util.function.Function.class).invoke(receiver, incoming).equals("host:Norm")) {
              throw new AssertionError("Java callback did not execute through Norm");
            }
            java.util.function.Function<String, String> throwing = text -> { throw new IllegalArgumentException("host-failure"); };
            if (!type.getMethod("catchFailure", java.util.function.Function.class).invoke(receiver, throwing).equals("host-failure")) {
              throw new AssertionError("Java callback failure did not become a Norm exception");
            }
            var sum = (java.util.function.BiFunction<Integer, Integer, Integer>) type.getMethod("sum").invoke(receiver);
            if (sum.apply(19, 23) != 42) throw new AssertionError("binary function boxing failed");
            var failure = (Runnable) type.getMethod("fail").invoke(receiver);
            if (type.getMethod("bind", Runnable.class).invoke(receiver, failure) != failure) {
              throw new AssertionError("function round trip changed identity");
            }
            try {
              failure.run();
              throw new AssertionError("Norm callback failure was lost");
            } catch (RuntimeException expected) {
              if (!expected.getMessage().equals("guest-failure")) throw expected;
            }
            return callback.apply("Norm");
          }
          private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

          private GeneratedInvoker() {
          }

          public static String invoke(String className, String method, String argument) {
            try {
              Class<?> type = Class.forName(className, true, GeneratedInvoker.class.getClassLoader());
              Object instance = type.getConstructor().newInstance();
              return (String) type.getMethod(method, String.class).invoke(instance, argument);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static <T> T proxy(Class<T> type) {
            try {
              return type.cast(Class.forName(type.getName() + "Proxy").getConstructor().newInstance());
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static String contextRoundTrip() {
            CONTEXT.set("framework-context");
            try {
              Class<?> type = Class.forName("sample.binding.Controller");
              Object instance = type.getConstructor().newInstance();
              return (String) type.getMethod("context").invoke(instance);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            } finally {
              CONTEXT.remove();
            }
          }

          public static void beginContext() { CONTEXT.set("proxy-context"); }
          public static void endContext() { CONTEXT.remove(); }

          public static String contextValue() {
            return CONTEXT.get();
          }

          public static String frameworkAllocated() {
            try {
              Class<?> type = Class.forName("sample.binding.ManagedResponse");
              Object response = type.getDeclaredConstructor().newInstance();
              type.getField("message").set(response, "Framework Allocated");
              return (String) type.getField("message").get(response);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static String failure() {
            try {
              Class<?> type = Class.forName("sample.binding.Controller");
              Object instance = type.getConstructor().newInstance();
              type.getMethod("fail").invoke(instance);
              return "";
            } catch (java.lang.reflect.InvocationTargetException exception) {
              return exception.getCause().getMessage();
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static void hydrate(Object response) {
            try {
              response.getClass().getField("message").set(response, "Java Hydrated DTO");
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static String read() {
            try {
              Class<?> type = Class.forName("sample.binding.Response");
              Class<?> controller = Class.forName("sample.binding.Controller");
              Object instance = controller.getConstructor().newInstance();
              Object response = controller.getMethod("response").invoke(instance);
              return (String) type.getField("message").get(response);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static String managed() {
            try {
              Class<?> type = Class.forName("sample.binding.Response");
              Class<?> controller = Class.forName("sample.binding.Controller");
              Object response = type.getConstructor().newInstance();
              type.getField("message").set(response, "Hydrated DTO");
              Object instance = controller.getConstructor().newInstance();
              return (String) controller.getMethod("echo", type).invoke(instance, response);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }

          public static String mutate() {
            try {
              Class<?> type = Class.forName("sample.binding.Response");
              Class<?> controller = Class.forName("sample.binding.Controller");
              Object response = type.getConstructor().newInstance();
              type.getField("message").set(response, "Hydrated DTO");
              Object instance = controller.getConstructor().newInstance();
              controller.getMethod("rename", type).invoke(instance, response);
              return (String) type.getField("message").get(response);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }


          public static String write() {
            try {
              Class<?> type = Class.forName("sample.binding.Response");
              Class<?> controller = Class.forName("sample.binding.Controller");
              Object response = type.getConstructor(String.class).newInstance("Initial");
              if (!type.getField("message").get(response).equals("Initial"))
                throw new AssertionError("String constructor was not invoked");
              Object numbered = type.getConstructor(int.class).newInstance(7);
              if (!type.getField("message").get(numbered).equals("Number 7"))
                throw new AssertionError("Integer constructor was not invoked");
              type.getField("message").set(response, "Java DTO");
              Object instance = controller.getConstructor().newInstance();
              return (String) controller.getMethod("echo", type).invoke(instance, response);
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          }
        }
        """);
    int status =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "--release",
                "17",
                "-d",
                classes.toString(),
                annotationSource.toString(),
                boxSource.toString(),
                converterSource.toString(),
                containerSource.toString(),
                invokerSource.toString(),
                processorSource.toString());
    assertEquals(0, status);
    Path service = classes.resolve("META-INF/services/javax.annotation.processing.Processor");
    Files.createDirectories(service.getParent());
    Files.writeString(service, "sample.EndpointProcessor\n");
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path));
        var files = Files.walk(classes)) {
      for (Path file :
          files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
        output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
        output.write(Files.readAllBytes(file));
        output.closeEntry();
      }
    }
    return path;
  }
}
