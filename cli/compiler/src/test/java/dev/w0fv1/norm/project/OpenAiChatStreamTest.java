package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.testing.NormTestKit;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class OpenAiChatStreamTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"null", "empty", "after_stop"})
  void normalizesAnEmptyFinishReasonAndRejectsDataAfterARealFinish(String scenario)
      throws Exception {
    Path library =
        Path.of(System.getProperty("norm.test.stdlib"))
            .getParent()
            .getParent()
            .resolve("libraries/openai");
    Path dependency = Files.createDirectories(directory.resolve("dependencies/openai"));
    try (var files = Files.list(library)) {
      for (Path source : files.filter(path -> path.toString().endsWith(".norm")).toList())
        Files.copy(source, dependency.resolve(source.getFileName()));
    }
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          String finish =
              switch (scenario) {
                case "empty" -> "\"\"";
                case "after_stop" -> "\"stop\"";
                default -> "null";
              };
          String data =
              """
        data: {"id":"one","choices":[{"index":0,"delta":{"role":"assistant","content":"hello"},"finish_reason":%s}]}

        data: {"id":"one","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":"stop"}]}

        data: [DONE]

        """
                  .formatted(finish);
          exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("app"));
      Files.writeString(
          app.resolve("module.norm"),
          "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"openai\", version: 1)]) }");
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
        package app
        import openai.Client
        import openai.OpenAiException
        Void main() {
          var client = Client(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions")
          try { printLine(client.generate(input: "hello").text) }
          catch OpenAiException error { printLine(error.code) }
        }
        """
              .formatted(server.getAddress().getPort()));
      assertEquals(
          (scenario.equals("after_stop") ? "OPENAI-PROTOCOL" : "hello world") + "\n",
          NormTestKit.run(entry).replace("\r\n", "\n"));
    } finally {
      server.stop(0);
    }
  }
}
