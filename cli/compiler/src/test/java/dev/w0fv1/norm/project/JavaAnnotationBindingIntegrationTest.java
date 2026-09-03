package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
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
                jarType(name: "GeneratedInvoker", members: ["contextRoundTrip", "contextValue", "hydrate", "invoke", "managed", "mutate", "read", "write"])
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

        @Endpoint(path: "/bbs", protocol: Endpoint_Protocol.HTTP)
        class Controller {
          @Endpoint(path: "/greet")
          String greet(String name) {
            return "Hello, " + name
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
        }

        class ChildController extends Controller {
          ChildController() {
            super()
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
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("maven-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
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
            ""),
        output.toString());
    Path processorOutput =
        temporaryDirectory.resolve("build/norm/java/classes/processor/endpoints.txt");
    assertEquals(
        String.join(
            System.lineSeparator(),
            "sample.binding.BoxConsumer:/box:http,json:HTTPS",
            "sample.binding.ChildController:/bbs:http,json:HTTP",
            "sample.binding.Controller:/bbs:http,json:HTTP",
            "sample.binding.RepeatedController:/first,/second:http,json:HTTPS",
            "sample.binding.Response:/response:http,json:HTTPS",
            "sample.binding.StringBox:/string-box:http,json:HTTPS",
            "sample.binding.StringBoxValue:/string-box-value:http,json:HTTPS",
            "sample.binding.StringConverter:/converter:http,json:HTTPS",
            ""),
        Files.readString(processorOutput));
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

          public static String contextValue() {
            return CONTEXT.get();
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
