package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.BufferedReader;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void generatesMicronautBeanDefinitionsForANormController() throws Exception {
    Path sources = temporaryDirectory.resolve("sources");
    Path http = Files.createDirectories(sources.resolve("micronaut/http"));
    Path inject = Files.createDirectories(sources.resolve("micronaut/inject"));
    Path jakartaInject = Files.createDirectories(sources.resolve("jakarta/inject"));
    Path processor = Files.createDirectories(sources.resolve("micronaut/inject/processor"));
    Files.writeString(
        http.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "micronaut.http",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "io.micronaut",
                artifact: "micronaut-http",
                version: "5.1.13",
                resolution: sha256("b203720783ccf7504ab0a9da35144f0caab36108252dd4fa5f8c017d49e5c5d8")
              ),
              api: [
                jarType(name: "annotation.Controller", members: ["consumes", "port", "produces", "value"]),
                jarType(name: "annotation.Get", members: ["consumes", "headRoute", "processes", "produces", "single", "uri", "uris", "value"])
              ]
            )
          )
        }
        """);
    Files.writeString(
        inject.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "micronaut.inject",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "io.micronaut",
                artifact: "micronaut-inject",
                version: "5.1.13",
                resolution: sha256("b45f4e6890c1b649e5b96cab559d82412f6b886103da0e2f29313be5cc5f5cc2")
              ),
              api: [jarType(name: "context.annotation.Prototype", members: [])]
            )
          )
        }
        """);
    Files.writeString(
        jakartaInject.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "jakarta.inject",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "jakarta.inject",
                artifact: "jakarta.inject-api",
                version: "2.0.1",
                resolution: sha256("f7dc98062fccf14126abb751b64fab12c312566e8cbdc8483598bffcea93af7c")
              ),
              api: [jarType(name: "Inject", members: [])]
            )
          )
        }
        """);
    Files.writeString(
        processor.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "micronaut.inject.processor",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "io.micronaut",
                artifact: "micronaut-inject-java",
                version: "5.1.13",
                resolution: sha256("e943e3456967a694d884da42d76c20f25b65873988800a195de3077c81ce1d71")
              ),
              api: []
            )
          )
        }
        """);
    Path repository = repositoryRoot().resolve(".tmp/jar-cache");
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects =
        environment.projectLoader(repositoryRoot().resolve(".tmp/jar-cache"))) {
      ModulePackager packager = new ModulePackager(projects);
      packager.packageModule(http.resolve("module.norm"), repository);
      packager.packageModule(inject.resolve("module.norm"), repository);
      packager.packageModule(jakartaInject.resolve("module.norm"), repository);
      packager.packageModule(processor.resolve("module.norm"), repository);
    }

    Path application = temporaryDirectory.resolve("application");
    Path module = Files.createDirectories(application.resolve("sample"));
    Files.writeString(
        module.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: ["Main"],
            dependencies: [
              dependency(name: "micronaut.http", version: 1),
              dependency(name: "micronaut.inject", version: 1),
              dependency(name: "jakarta.inject", version: 1),
              dependency(name: "micronaut.inject.processor", version: 1)
            ]
          )
        }
        """);
    Path entry = module.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample

        import micronaut.http.annotation.Controller
        import micronaut.http.annotation.Get
        import micronaut.inject.context.annotation.Prototype
        import jakarta.inject.Inject

        @Prototype()
        class GreetingService {
          String greet(String name) {
            return "Hello, " + name
          }
        }

        @Controller(value: "/hello")
        class HelloController {
          GreetingService greetingService

          @Inject()
          HelloController(GreetingService greetingService) {
            this.greetingService = greetingService
          }

          @Get(value: "/{name}")
          String hello(String name) {
            return greetingService.greet(name)
          }
        }

        Void main() {
        }
        """);
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.compile(entry);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    Path classes = application.resolve("build/norm/java/classes");
    try (var files = Files.walk(classes)) {
      List<String> names = files.map(path -> path.getFileName().toString()).toList();
      assertTrue(names.stream().anyMatch(name -> name.contains("HelloController$Definition")));
      assertTrue(names.stream().anyMatch(name -> name.contains("GreetingService$Definition")));
    }
  }

  @Test
  @Timeout(300)
  void runsThePureNormMicronautBbsThroughNetty() throws Exception {
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
              "micronaut-data-model/micronaut/data/model",
              "micronaut-data-jdbc/micronaut/data/jdbc",
              "micronaut-data-processor/micronaut/data/processor",
              "micronaut-data-tx/micronaut/data/tx",
              "micronaut-jdbc-hikari/micronaut/jdbc/hikari",
              "h2-database/h2/database",
              "micronaut-security/micronaut/security",
              "reactor-core/reactor/core",
              "junit-jupiter/junit/jupiter",
              "micronaut-test-core/micronaut/test/core",
              "micronaut-test-junit5/micronaut/test/junit5",
              "okhttp/okhttp/client")) {
        packager.packageModule(bindings.resolve(module).resolve("module.norm"), repository);
      }
    }

    Path module = Files.createDirectories(temporaryDirectory.resolve("application/sample/bbs"));
    Path example = repositoryRoot().resolve("docs/examples/micronaut-bbs/app/sample/bbs");
    for (String source :
        List.of(
            "module.norm",
            "Application.norm",
            "Archive.norm",
            "Domain.norm",
            "Web.norm",
            "Main.norm",
            "BbsTest.norm")) {
      Files.copy(
          example.resolve(source),
          module.resolve(source),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    Path entry = module.resolve("Main.norm");
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    ProjectTestResult tests;
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository, jarCache),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      tests =
          launcher.test(
              module.resolve("BbsTest.norm"),
              ExecutionContext.of(new PrintWriter(new StringWriter())));
    }

    assertTrue(tests.compilation().isSuccess(), () -> tests.compilation().diagnostics().toString());
    assertTrue(tests.isSuccess(), () -> tests.report().toString());
    assertEquals(2, tests.report().orElseThrow().testsFound());
    assertEquals(2, tests.report().orElseThrow().testsSucceeded());

    assertEquals(
        String.join(
            System.lineSeparator(),
            "Welcome, Norm",
            "AOP:BBS",
            "true",
            "400",
            "{\"message\":\"BBS failure\"}",
            "{\"message\":\"Norm DTO\"}",
            "{\"message\":\"Echo Request DTO\"}",
            "400",
            "200",
            "{\"id\":1,\"username\":\"norm\"}",
            "session-norm",
            "200",
            "200",
            "200",
            "1",
            "200",
            "{\"content\":[{\"id\":1,\"boardId\":1,\"author\":\"norm\",\"title\":\"Welcome\",\"content\":\"First topic\"}],\"pageable\":{\"size\":10,\"number\":0,\"sort\":{},\"mode\":\"OFFSET\"},\"totalSize\":1}",
            "1",
            "200",
            "data: topics:1",
            "401",
            "200",
            "norm",
            "401",
            "401",
            "200",
            "norm session",
            ""),
        output.toString());

    CountDownLatch releaseServer = new CountDownLatch(1);
    com.sun.net.httpserver.HttpServer holdServer =
        com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    holdServer.createContext(
        "/hold",
        exchange -> {
          try {
            releaseServer.await();
            exchange.sendResponseHeaders(204, -1);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    holdServer.start();
    String holdAddress = "http://127.0.0.1:" + holdServer.getAddress().getPort() + "/hold";
    Files.delete(module.resolve("Main.norm"));
    Path concurrentEntry = module.resolve("ConcurrentMain.norm");
    Files.writeString(
        concurrentEntry,
        """
        package sample.bbs

        import okhttp.client.okHttpClientNew
        import okhttp.client.requestBuilderNew

        Void main() {
          var server = startBbsServer()
          if server != null {
            printLine(server.getURI()?.value ?? "")
            var client = okHttpClientNew()
            var request = requestBuilderNew().url("%s")?.get()?.build()
            var response = client.newCall(request)?.execute()
            response?.close()
            server.close()
          }
        }
        """
            .formatted(holdAddress));

    try (PipedWriter concurrentOutput = new PipedWriter();
        BufferedReader concurrentInput = new BufferedReader(new PipedReader(concurrentOutput));
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      ProjectEnvironment concurrentEnvironment = ProjectEnvironment.bootstrap(backend);
      try (ProjectLauncher launcher =
          new ProjectLauncher(
              concurrentEnvironment.projectLoader(repository, jarCache),
              concurrentEnvironment.compilerSession(),
              backend)) {
        var execution =
            executor.submit(
                () ->
                    launcher.run(
                        concurrentEntry,
                        ExecutionContext.builder()
                            .output(new PrintWriter(concurrentOutput, true))
                            .build()));
        try {
          long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
          while (!concurrentInput.ready() && !execution.isDone() && System.nanoTime() < deadline) {
            Thread.sleep(20);
          }
          if (execution.isDone() && !concurrentInput.ready()) {
            var result = execution.get();
            assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
          }
          assertTrue(concurrentInput.ready(), "Micronaut BBS server did not publish its address");
          URI root = URI.create(concurrentInput.readLine()).resolve("/bbs/");
          HttpClient client =
              HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
          HttpRequest board =
              HttpRequest.newBuilder(root.resolve("boards"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"General\"}"))
                  .build();
          HttpRequest topic =
              HttpRequest.newBuilder(root.resolve("topics"))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"boardId\":1,\"author\":\"norm\",\"title\":\"Concurrent\",\"content\":\"Concurrent topic\"}"))
                  .build();
          assertEquals(200, client.send(board, HttpResponse.BodyHandlers.ofString()).statusCode());
          assertEquals(200, client.send(topic, HttpResponse.BodyHandlers.ofString()).statusCode());

          List<CompletableFuture<HttpResponse<String>>> replies = new ArrayList<>();
          for (int index = 0; index < 12; index++) {
            HttpRequest reply =
                HttpRequest.newBuilder(root.resolve("replies"))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"topicId\":1,\"author\":\"norm\",\"content\":\"Reply "
                                + index
                                + "\"}"))
                    .build();
            replies.add(client.sendAsync(reply, HttpResponse.BodyHandlers.ofString()));
          }
          CompletableFuture.allOf(replies.toArray(CompletableFuture[]::new))
              .get(30, TimeUnit.SECONDS);
          for (CompletableFuture<HttpResponse<String>> reply : replies) {
            assertEquals(200, reply.join().statusCode(), reply.join().body());
          }
          HttpRequest count = HttpRequest.newBuilder(root.resolve("replies/count")).GET().build();
          HttpResponse<String> countResponse =
              client.send(count, HttpResponse.BodyHandlers.ofString());
          assertEquals(200, countResponse.statusCode());
          assertEquals("12", countResponse.body());
        } finally {
          releaseServer.countDown();
        }
        var result = execution.get(30, TimeUnit.SECONDS);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      }
    } finally {
      releaseServer.countDown();
      holdServer.stop(0);
    }

    Files.delete(concurrentEntry);
    Files.copy(
        example.resolve("Main.norm"),
        module.resolve("Main.norm"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    ProjectEnvironment publishingEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = publishingEnvironment.projectLoader(repository, jarCache)) {
      new ModulePackager(projects).packageModule(module.resolve("module.norm"), repository);
    }

    Path consumer = Files.createDirectories(temporaryDirectory.resolve("consumer/sample/consumer"));
    Files.writeString(
        consumer.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample.consumer",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(name: "sample.bbs", version: 1)]
          )
        }
        """);
    Path consumerEntry = consumer.resolve("Main.norm");
    Files.writeString(
        consumerEntry,
        """
        package sample.consumer

        import sample.bbs.runBbsArchiveSmoke

        Void main() {
          printLine(runBbsArchiveSmoke(name: "Archive"))
        }
        """);
    StringWriter consumerOutput = new StringWriter();
    ProjectEnvironment archiveConsumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            archiveConsumerEnvironment.projectLoader(repository, jarCache),
            archiveConsumerEnvironment.compilerSession(),
            backend)) {
      var result =
          launcher.run(consumerEntry, ExecutionContext.of(new PrintWriter(consumerOutput)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("Welcome, Archive" + System.lineSeparator(), consumerOutput.toString());
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
