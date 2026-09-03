package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.CompilationResult;
import java.io.BufferedReader;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautWebSocketBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(300)
  void exchangesMessagesWithAPureNormServerWebSocket() throws Exception {
    Path repository = temporaryDirectory.resolve("repository");
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects =
        environment.projectLoader(temporaryDirectory.resolve("maven-cache"))) {
      ModulePackager packager = new ModulePackager(projects);
      Path bindings = repositoryRoot().resolve("java-binding");
      for (String module :
          List.of(
              "micronaut-websocket/micronaut/websocket",
              "micronaut-core/micronaut/core",
              "micronaut-http/micronaut/http",
              "micronaut-inject/micronaut/inject",
              "micronaut-runtime/micronaut/runtime",
              "micronaut-http-server-netty/micronaut/server/netty",
              "micronaut-json/micronaut/json",
              "micronaut-jackson/micronaut/jackson",
              "micronaut-inject-java/micronaut/inject/processor",
              "okhttp/okhttp/client")) {
        packager.packageModule(bindings.resolve(module).resolve("module.norm"), repository);
      }
    }
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

    Path module = Files.createDirectories(temporaryDirectory.resolve("application/sample/socket"));
    Files.writeString(
        module.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample.socket",
            version: 1,
            exports: ["Main"],
            dependencies: [
              dependency(repository: "github", name: "micronaut.websocket", version: 1),
              dependency(repository: "github", name: "micronaut.core", version: 1),
              dependency(repository: "github", name: "micronaut.http", version: 1),
              dependency(repository: "github", name: "micronaut.inject", version: 1),
              dependency(repository: "github", name: "micronaut.runtime", version: 1),
              dependency(repository: "github", name: "micronaut.server.netty", version: 1),
              dependency(repository: "github", name: "micronaut.json", version: 1),
              dependency(repository: "github", name: "micronaut.jackson", version: 1),
              dependency(repository: "github", name: "micronaut.inject.processor", version: 1),
              dependency(repository: "github", name: "okhttp.client", version: 1)
            ]
          )
        }
        """);
    Path entry = module.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample.socket

        import micronaut.inject.context.applicationContextRun
        import micronaut.inject.javaStringArrayNew
        import micronaut.runtime.runtime.server.EmbeddedServer
        import micronaut.websocket.WebSocketSession
        import micronaut.websocket.annotation.OnMessage
        import micronaut.websocket.annotation.ServerWebSocket
        import okhttp.client.okHttpClientNew
        import okhttp.client.requestBuilderNew
        import std.collections.MutableMap
        import std.collections.mutableMap

        @ServerWebSocket(value: "/echo")
        class EchoSocket {
          @OnMessage()
          Void onMessage(String message, WebSocketSession session) {
            session.sendAsync(arg0: "echo:" + message)
          }
        }

        Void main() {
          var arguments = javaStringArrayNew(size: 0)
          MutableMap<String?, Any?> properties = mutableMap()
          properties.put(key: "micronaut.server.port", value: -1)
          EmbeddedServer? server = applicationContextRun<EmbeddedServer>(
            arg0: EmbeddedServer.class,
            arg1: properties,
            arg2: arguments
          )
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

    try (PipedWriter output = new PipedWriter();
        BufferedReader input = new BufferedReader(new PipedReader(output));
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
      try (ProjectLauncher launcher =
          new ProjectLauncher(
              consumerEnvironment.projectLoader(repository),
              consumerEnvironment.compilerSession(),
              backend)) {
        var execution =
            executor.submit(
                () ->
                    launcher.run(
                        entry,
                        ExecutionContext.builder().output(new PrintWriter(output, true)).build()));
        try {
          long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
          while (!input.ready() && !execution.isDone() && System.nanoTime() < deadline) {
            Thread.sleep(20);
          }
          if (execution.isDone() && !input.ready()) {
            CompilationResult result = execution.get();
            assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
          }
          assertTrue(input.ready(), "Micronaut WebSocket server did not publish its address");
          URI httpAddress = URI.create(input.readLine());
          URI webSocketAddress =
              new URI(
                  httpAddress.getScheme().equals("https") ? "wss" : "ws",
                  httpAddress.getUserInfo(),
                  httpAddress.getHost(),
                  httpAddress.getPort(),
                  "/echo",
                  null,
                  null);
          CompletableFuture<String> reply = new CompletableFuture<>();
          StringBuilder fragments = new StringBuilder();
          HttpClient client = HttpClient.newHttpClient();
          WebSocket socket =
              client
                  .newWebSocketBuilder()
                  .connectTimeout(Duration.ofSeconds(10))
                  .buildAsync(
                      webSocketAddress,
                      new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                          webSocket.request(1);
                        }

                        @Override
                        public java.util.concurrent.CompletionStage<?> onText(
                            WebSocket webSocket, CharSequence data, boolean last) {
                          fragments.append(data);
                          if (last) reply.complete(fragments.toString());
                          webSocket.request(1);
                          return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                          reply.completeExceptionally(error);
                        }
                      })
                  .get(10, TimeUnit.SECONDS);
          try {
            socket.sendText("Norm", true).get(10, TimeUnit.SECONDS);
            assertEquals("echo:Norm", reply.get(10, TimeUnit.SECONDS));
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
          } finally {
            socket.abort();
          }
        } finally {
          releaseServer.countDown();
        }
        CompilationResult result = execution.get(10, TimeUnit.SECONDS);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      }
    } finally {
      releaseServer.countDown();
      holdServer.stop(0);
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
