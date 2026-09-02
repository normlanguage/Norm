package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.BufferedReader;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(300)
  void runsTheTypedPureNormMicronautBbsThroughNetty() throws Exception {
    Path jarCache = repositoryRoot().resolve(".tmp/jar-cache");
    Path repository = Files.createDirectories(temporaryDirectory.resolve("repository"));
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(jarCache)) {
      ModulePackager packager = new ModulePackager(projects);
      Path bindings = repositoryRoot().resolve("java-binding");
      for (String module :
          List.of(
              "micronaut-http/micronaut/http",
              "micronaut-http-client-core/micronaut/http/client",
              "micronaut-aop/micronaut/aop",
              "micronaut-core/micronaut/core",
              "micronaut-inject/micronaut/inject",
              "micronaut-runtime/micronaut/runtime",
              "micronaut-http-server-netty/micronaut/server/netty",
              "micronaut-json/micronaut/json",
              "micronaut-jackson/micronaut/jackson",
              "micronaut-serde-api/micronaut/serde/api",
              "micronaut-serde-jackson/micronaut/serde/jackson",
              "micronaut-serde-processor/micronaut/serde/processor",
              "micronaut-http-client/micronaut/http/client/netty",
              "micronaut-management/micronaut/management",
              "jakarta-inject/jakarta/inject",
              "jakarta-validation/jakarta/validation",
              "micronaut-inject-java/micronaut/inject/processor",
              "micronaut-validation/micronaut/validation",
              "micronaut-validation-processor/micronaut/validation/processor",
              "micronaut-jdbc-hikari/micronaut/jdbc/hikari",
              "h2-database/h2/database",
              "micronaut-security/micronaut/security",
              "reactor-core/reactor/core",
              "micronaut-web/micronaut/web")) {
        packager.packageModule(bindings.resolve(module).resolve("module.norm"), repository);
      }
    }
    ProjectEnvironment ormEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = ormEnvironment.projectLoader(jarCache)) {
      ModulePackager packager = new ModulePackager(projects);
      Path bindings = repositoryRoot().resolve("java-binding");
      for (String module :
          List.of(
              "orm-api/orm",
              "orm-hibernate/orm/hibernate",
              "orm-micronaut/orm/micronaut",
              "orm-micronaut-tx/orm/micronaut/tx")) {
        packager.packageModule(bindings.resolve(module).resolve("module.norm"), repository);
      }
    }

    Path module = Files.createDirectories(temporaryDirectory.resolve("application/sample/bbs"));
    Path example = repositoryRoot().resolve("docs/examples/micronaut-bbs/app/sample/bbs");
    for (String source : List.of("module.norm", "application.norm", "Domain.norm", "Web.norm")) {
      Files.copy(example.resolve(source), module.resolve(source));
    }
    Path application = module.resolve("application.norm");
    Files.writeString(
        application,
        Files.readString(application)
            .replace(
                "server: Server(host: \"127.0.0.1\")",
                "server: Server(host: \"127.0.0.1\", port: -1)")
            .replace(
                "h2DataSource(database: \"./.tmp/micronaut-bbs/bbs\")",
                "h2DataSource(database: \"normbbs\", storage: H2Storage.Memory)")
            .replace(
                "import micronaut.web.HealthDetails",
                "import micronaut.web.HealthDetails\nimport micronaut.web.H2Storage")
            .replace("schema: SchemaMode.Update", "schema: SchemaMode.CreateDrop"));
    try (var resources = Files.walk(example.resolve("resources"))) {
      for (Path resource : resources.toList()) {
        Path target =
            module
                .resolve("resources")
                .resolve(example.resolve("resources").relativize(resource).toString());
        if (Files.isDirectory(resource)) Files.createDirectories(target);
        else Files.copy(resource, target);
      }
    }

    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (PipedWriter output = new PipedWriter();
        BufferedReader input = new BufferedReader(new PipedReader(output))) {
      ProjectEnvironment consumer = ProjectEnvironment.bootstrap(backend);
      try (ProjectLauncher launcher =
          new ProjectLauncher(
              consumer.projectLoader(repository, jarCache), consumer.compilerSession(), backend)) {
        Thread server =
            Thread.ofVirtual()
                .start(
                    () -> {
                      try {
                        var result =
                            launcher.run(
                                application, ExecutionContext.of(new PrintWriter(output, true)));
                        if (!result.isSuccess()) {
                          failure.set(new AssertionError(result.diagnostics().toString()));
                        }
                      } catch (Throwable exception) {
                        failure.set(exception);
                      }
                    });
        try {
          long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
          while (!input.ready() && failure.get() == null && System.nanoTime() < deadline) {
            Thread.sleep(20);
          }
          assertTrue(input.ready(), () -> "Micronaut startup failed: " + failure.get());
          String address = input.readLine();
          assertTrue(address.startsWith("Micronaut: "), address);
          URI root = URI.create(address.substring("Micronaut: ".length()));
          HttpClient client =
              HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

          HttpResponse<String> page =
              client.send(
                  HttpRequest.newBuilder(root.resolve("/")).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
          assertEquals(200, page.statusCode());
          assertTrue(page.body().contains("Norm BBS"));

          HttpResponse<String> health =
              client.send(
                  HttpRequest.newBuilder(root.resolve("/health")).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
          assertEquals(200, health.statusCode());
          assertTrue(health.body().contains("\"status\":\"UP\""));

          HttpRequest registration =
              HttpRequest.newBuilder(root.resolve("/bbs/users"))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"username\":\"norm\",\"password\":\"password\"}"))
                  .build();
          assertEquals(
              200, client.send(registration, HttpResponse.BodyHandlers.ofString()).statusCode());

          HttpRequest login =
              HttpRequest.newBuilder(root.resolve("/bbs/sessions"))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"username\":\"norm\",\"password\":\"password\"}"))
                  .build();
          HttpResponse<String> loginResponse =
              client.send(login, HttpResponse.BodyHandlers.ofString());
          assertEquals(200, loginResponse.statusCode());
          assertEquals("session-norm", loginResponse.body());

          HttpRequest board =
              HttpRequest.newBuilder(root.resolve("/bbs/session/boards"))
                  .header("Content-Type", "application/json")
                  .header("X-Session", "session-norm")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"General\"}"))
                  .build();
          assertEquals(200, client.send(board, HttpResponse.BodyHandlers.ofString()).statusCode());

          HttpRequest topic =
              HttpRequest.newBuilder(root.resolve("/bbs/session/topics"))
                  .header("Content-Type", "application/json")
                  .header("X-Session", "session-norm")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"boardId\":1,\"title\":\"Welcome\",\"content\":\"First topic\"}"))
                  .build();
          assertEquals(200, client.send(topic, HttpResponse.BodyHandlers.ofString()).statusCode());

          HttpRequest reply =
              HttpRequest.newBuilder(root.resolve("/bbs/session/replies"))
                  .header("Content-Type", "application/json")
                  .header("X-Session", "session-norm")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"topicId\":1,\"content\":\"First reply\"}"))
                  .build();
          assertEquals(200, client.send(reply, HttpResponse.BodyHandlers.ofString()).statusCode());

          HttpResponse<String> topics =
              client.send(
                  HttpRequest.newBuilder(root.resolve("/bbs/topics?page=0&size=10")).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
          assertEquals(200, topics.statusCode());
          assertTrue(topics.body().contains("\"title\":\"Welcome\""));

          HttpResponse<String> replies =
              client.send(
                  HttpRequest.newBuilder(root.resolve("/bbs/topics/1/replies")).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
          assertEquals(200, replies.statusCode());
          assertTrue(replies.body().contains("\"content\":\"First reply\""));
        } finally {
          server.interrupt();
          server.join(Duration.ofSeconds(30));
        }
        assertFalse(server.isAlive());
        assertEquals(null, failure.get());
      }
    }
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
      current = current.getParent();
    }
    if (current == null) throw new IllegalStateException("Norm repository root is absent");
    return current;
  }
}
