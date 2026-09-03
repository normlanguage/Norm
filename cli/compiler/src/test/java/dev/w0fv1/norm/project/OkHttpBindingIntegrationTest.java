package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OkHttpBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheOkHttpNarForARealSynchronousRequestAndResourceLifecycle() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/hello",
        exchange -> {
          byte[] body = "Norm HTTP".getBytes(StandardCharsets.UTF_8);
          exchange
              .getResponseHeaders()
              .add("X-Norm", exchange.getRequestHeaders().getFirst("X-Norm"));
          exchange.getResponseHeaders().add("X-Method", exchange.getRequestMethod());
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      Path workspace = Path.of("").toAbsolutePath().normalize();
      while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/okhttp"))) {
        workspace = workspace.getParent();
      }
      assertTrue(workspace != null, "workspace root is unavailable");
      Path module = workspace.resolve("java-binding/okhttp/okhttp/client/module.norm");
      Path repository = temporaryDirectory.resolve("repository");
      install(
          workspace, repository, "com/squareup/okhttp3/okhttp-jvm/5.5.0", "okhttp-jvm-5.5.0.jar");
      install(workspace, repository, "com/squareup/okio/okio-jvm/3.18.1", "okio-jvm-3.18.1.jar");
      install(
          workspace,
          repository,
          "org/jetbrains/kotlin/kotlin-stdlib/2.1.21",
          "kotlin-stdlib-2.1.21.jar");
      install(workspace, repository, "org/jetbrains/annotations/13.0", "annotations-13.0.jar");
      Files.writeString(
          repository.resolve("com/squareup/okhttp3/okhttp-jvm/5.5.0/okhttp-jvm-5.5.0.pom"),
          """
          <project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId><version>5.5.0</version>
            <dependencies>
              <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>2.1.21</version></dependency>
              <dependency><groupId>com.squareup.okio</groupId><artifactId>okio-jvm</artifactId><version>3.18.1</version></dependency>
            </dependencies>
          </project>
          """);
      Files.writeString(
          repository.resolve("com/squareup/okio/okio-jvm/3.18.1/okio-jvm-3.18.1.pom"),
          pomWithDependency(
              "com.squareup.okio",
              "okio-jvm",
              "3.18.1",
              "org.jetbrains.kotlin",
              "kotlin-stdlib",
              "2.1.21"));
      Files.writeString(
          repository.resolve("org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.pom"),
          pomWithDependency(
              "org.jetbrains.kotlin",
              "kotlin-stdlib",
              "2.1.21",
              "org.jetbrains",
              "annotations",
              "13.0"));
      Files.writeString(
          repository.resolve("org/jetbrains/annotations/13.0/annotations-13.0.pom"),
          minimalPom("org.jetbrains", "annotations", "13.0"));

      NormRuntime backend = new NormRuntime();
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
      try (ProjectLoader projects = environment.projectLoader(repository)) {
        new ModulePackager(projects).packageModule(module, repository);
      }

      Path app = Files.createDirectories(temporaryDirectory.resolve("app"));
      Path entry = app.resolve("Main.norm");
      Files.writeString(
          app.resolve("module.norm"),
          """
          Module module() {
            return module(
              name: "app",
              version: 1,
              exports: ["Main"],
              dependencies: [dependency(repository: "github", name: "okhttp.client", version: 1)]
            )
          }
          """);
      Files.writeString(
          entry,
          """
          package app

          import okhttp.client.Call
          import okhttp.client.OkHttpClient
          import okhttp.client.Request
          import okhttp.client.RequestBuilder
          import okhttp.client.Response
          import okhttp.client.ResponseBody
          import okhttp.client.okHttpClientNew
          import okhttp.client.requestBuilderNew

          Void main() {
            RequestBuilder? builder = requestBuilderNew()
            if builder != null {
              RequestBuilder? located = builder.url("http://127.0.0.1:%d/hello")
              if located != null {
                RequestBuilder? headed = located.header(arg0: "X-Norm", arg1: "Norm")
                if headed != null {
                  Request? request = headed.get()?.build()
                  OkHttpClient? client = okHttpClientNew()
                  if request != null && client != null {
                    Call? call = client.newCall(request)
                    if call != null {
                      Response? response = call.execute()
                      if response != null {
                        printLine(response.code())
                        printLine(response.isSuccessful())
                        printLine(response.header("X-Norm") ?? "")
                        printLine(response.header("X-Method") ?? "")
                        ResponseBody? body = response.body()
                        if body != null {
                          printLine(body.string() ?? "")
                        }
                        response.close()
                      }
                    }
                  }
                }
              }
            }
          }
          """
              .formatted(server.getAddress().getPort()));
      StringWriter output = new StringWriter();
      ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
      try (ProjectLauncher launcher =
          new ProjectLauncher(
              consumerEnvironment.projectLoader(repository),
              consumerEnvironment.compilerSession(),
              backend)) {
        var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      }
      assertEquals(
          String.join(System.lineSeparator(), "200", "true", "Norm", "GET", "Norm HTTP", ""),
          output.toString());
    } finally {
      server.stop(0);
    }
  }

  private static void install(Path workspace, Path repository, String coordinate, String fileName)
      throws Exception {
    Path directory = Files.createDirectories(repository.resolve(coordinate));
    Files.copy(
        workspace.resolve("java-binding/okhttp/okhttp/client/lib").resolve(fileName),
        directory.resolve(fileName));
  }

  private static String minimalPom(String group, String artifact, String version) {
    return """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version>
        </project>
        """
        .formatted(group, artifact, version);
  }

  private static String pomWithDependency(
      String group,
      String artifact,
      String version,
      String dependencyGroup,
      String dependencyArtifact,
      String dependencyVersion) {
    return """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version>
          <dependencies>
            <dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version></dependency>
          </dependencies>
        </project>
        """
        .formatted(
            group, artifact, version, dependencyGroup, dependencyArtifact, dependencyVersion);
  }
}
