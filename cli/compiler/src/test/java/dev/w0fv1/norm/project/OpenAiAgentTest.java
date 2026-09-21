package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.testing.NormTestKit;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;

final class OpenAiAgentTest {
  @TempDir Path directory;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"http", "truncated", "callback"})
  void notifiesFailureOnceAndPropagatesTheOriginalError(String scenario) throws Exception {
    Path library =
        Path.of(System.getProperty("norm.test.stdlib"))
            .getParent()
            .getParent()
            .resolve("libraries/openai");
    Path dependency = Files.createDirectories(directory.resolve("dependencies/openai"));
    try (var files = Files.list(library)) {
      for (Path source : files.filter(p -> p.toString().endsWith(".norm")).toList())
        Files.copy(source, dependency.resolve(source.getFileName()));
    }
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/responses",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          boolean http = scenario.equals("http");
          exchange
              .getResponseHeaders()
              .set("Content-Type", http ? "application/json" : "text/event-stream");
          exchange.sendResponseHeaders(http ? 401 : 200, 0);
          String body =
              http
                  ? "{\"error\":{\"code\":\"AUTH\",\"message\":\"denied\"}}"
                  : """
            data: {"type":"response.created","response":{"id":"one"}}

            data: {"type":"response.output_text.delta","delta":"hello"}

            """;
          exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("app"));
      Files.writeString(
          app.resolve("module.norm"),
          """
          Module module() { return module(name: "app", version: 1,
            dependencies: [dependency(repository: "github", name: "openai", version: 1)]) }
          """);
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
          package app
          import openai.Client
          import openai.GenerationEvent
          import openai.OpenAiException
          import openai.Response
          import std.core.Exception
          import std.json.JsonException
          class Events { Integer general = 0 Integer specific = 0 Integer completed = 0 }
          Void main() {
            var events = Events()
            var client = Client(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/responses")
            Function<Void(GenerationEvent)> general = (GenerationEvent event) {
              switch event { case Failed(Exception error) { events.general = events.general + 1 } case _ {} }
              return
            }
            try {
              var response = client.generate(input: "hello",
                onEvent: general,
                onFailed: (Exception error) {
                  require(condition: events.general == 1, message: "general callback precedes specific callback")
                  events.specific = events.specific + 1
                },
                onResponseCompleted: (Response response) { events.completed = events.completed + 1 },
                onText: (String text) {
                  if %s { throw JsonException(code: "MY-CALLBACK", message: "callback failed", path: "$", offset: 0, line: 1, column: 1) }
                })
            } catch OpenAiException error { printLine(error.code) }
            catch JsonException error { printLine(error.code) }
            require(condition: events.general == 1 && events.specific == 1 && events.completed == 0, message: "terminal failure")
          }
          """
              .formatted(server.getAddress().getPort(), scenario.equals("callback")));
      String expected =
          switch (scenario) {
            case "http" -> "AUTH";
            case "callback" -> "MY-CALLBACK";
            default -> "OPENAI-STREAM-INCOMPLETE";
          };
      assertEquals(expected + "\n", NormTestKit.run(entry).replace("\r\n", "\n"));
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void streamsEventsRunsAnnotatedToolsAndStartsEachRunWithoutHistory(boolean failTool)
      throws Exception {
    Path library =
        Path.of(System.getProperty("norm.test.stdlib"))
            .getParent()
            .getParent()
            .resolve("libraries/openai");
    Path dependency = Files.createDirectories(directory.resolve("dependencies/openai"));
    try (var files = Files.list(library)) {
      for (Path source : files.filter(p -> p.toString().endsWith(".norm")).toList())
        Files.copy(source, dependency.resolve(source.getFileName()));
    }
    var requests = new AtomicInteger();
    var failure = new AtomicReference<Throwable>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            var body =
                JsonParser.parseString(
                        new String(
                            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(body.get("stream").getAsBoolean());
            assertEquals("model", body.get("model").getAsString());
            var definition =
                body.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("function");
            assertEquals("add", definition.get("name").getAsString());
            assertEquals(
                "Amount",
                definition
                    .getAsJsonObject("parameters")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("amount")
                    .get("description")
                    .getAsString());
            int round = requests.getAndIncrement();
            if (round % 2 == 0) assertEquals(1, body.getAsJsonArray("messages").size());
            else {
              var messages = body.getAsJsonArray("messages");
              assertEquals(
                  "tool",
                  messages.get(messages.size() - 1).getAsJsonObject().get("role").getAsString());
              String output =
                  messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString();
              if (failTool)
                assertEquals(
                    "deliberate",
                    JsonParser.parseString(output).getAsJsonObject().get("error").getAsString());
              else assertEquals(round == 1 ? "3" : "6", output);
            }
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            String data =
                round % 2 == 0
                    ? """
              data: {"id":"r1","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call1","function":{"name":"add","arguments":"{\\\"amount\\\":3}"}}]},"finish_reason":null}]}

              data: {"id":"r1","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

              data: [DONE]

              """
                    : """
              data: {"id":"r2","choices":[{"index":0,"delta":{"role":"assistant","content":"done"},"finish_reason":null}]}

              data: {"id":"r2","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

              data: [DONE]

              """;
            exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
          } catch (Throwable error) {
            failure.set(error);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("app"));
      Files.writeString(
          app.resolve("module.norm"),
          """
          Module module() { return module(name: "app", version: 1,
            dependencies: [dependency(repository: "github", name: "openai", version: 1)]) }
          """);
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
          package app
          import openai.Client
          import openai.Agent
          import openai.Tool
          import openai.ToolParameter
          import openai.GenerationEvent
          import openai.ToolCall
          import openai.ToolResult
          import openai.ToolFailure
          import openai.Message
          import openai.Response
          import std.core.Exception
          class Counter {
            Integer number = 0
            Integer events = 0
            Boolean reject = %s
            @Tool(name: "add", description: "Add a number")
            Integer add(@ToolParameter(description: "Amount") Integer amount) {
              if reject { throw Exception(message: "deliberate") }
              number = number + amount
              return number
            }
          }
          Void main() {
            var counter = Counter()
            var client = Client(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions")
            var agent = Agent(client: client, tools: [counter.add])
            var response = agent.run(input: "add three",
              onEvent: (GenerationEvent event) { counter.events = counter.events + 1 },
              onRunStarted: (String input) { printLine("run") },
              onResponseStarted: (String id) { printLine("response") },
              onToolCallReceived: (ToolCall call) { printLine("received") },
              onToolStarted: (ToolCall call) { require(condition: counter.number == 0, message: "before") printLine("start") },
              onToolCompleted: (ToolResult result) { require(condition: counter.number == 3 && result.output == "3", message: "after") printLine("tool") },
              onToolFailed: (ToolFailure failure) { require(condition: failure.error.message == "deliberate", message: "tool error") printLine("tool failed") },
              onMessageStarted: (String id) { printLine("message") },
              onText: (String text) { printLine(text) },
              onMessageCompleted: (Message message) { printLine("message done") },
              onResponseCompleted: (Response response) { printLine("response done") },
              onRunCompleted: (Response response) { printLine("run done") })
            require(condition: response.text == "done" && counter.events == 12, message: "event stream")
            agent.run(input: "add three again")
            require(condition: counter.number == if counter.reject { 0 } else { 6 }, message: "second run")
          }
          """
              .formatted(failTool, server.getAddress().getPort()));
      assertEquals(
          "run\nresponse\nreceived\nresponse done\nstart\n"
              + (failTool ? "tool failed" : "tool")
              + "\nresponse\nmessage\ndone\nmessage done\nresponse done\nrun done\n",
          NormTestKit.run(entry).replace("\r\n", "\n"));
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertEquals(4, requests.get());
    } finally {
      server.stop(0);
    }
  }
}
