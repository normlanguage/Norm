package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.testing.NormTestKit;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;

final class OpenAiAgentTest {
  @TempDir Path directory;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"annotation", "schema"})
  void retainsTheAcceptedCatalogUntilARejectedToolIsRepaired(String scenario) throws Exception {
    installLibraries();
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
            int round = requests.getAndIncrement();
            assertEquals(
                round < 2 ? "install" : "added",
                body.getAsJsonArray("tools")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name")
                    .getAsString());
            if (round == 1)
              assertTrue(body.toString().contains("OPENAI-INVALID-TOOL"), body.toString());
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            String data =
                round < 2
                    ? """
          data: {"id":"r1","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"install","function":{"name":"install","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}

          data: [DONE]

          """
                    : """
          data: {"id":"r2","choices":[{"index":0,"delta":{"role":"assistant","content":"done"},"finish_reason":"stop"}]}

          data: [DONE]

          """;
            exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
          } catch (Throwable error) {
            failure.compareAndSet(null, error);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("app"));
      Files.writeString(
          app.resolve("module.norm"),
          "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"agent\", version: 1)]) }");
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
        package app
        import agent.Agent
        import openai.Tool
        class Unsupported {}
        class Catalog {
          Integer stage = 0
          @Tool(description: "Install")
          Integer install() { stage = stage + 1 return stage }
          %s
          Integer invalid(%s) { return 0 }
          @Tool(description: "Added")
          Integer added() { return stage }
          List<Function<?>> tools() {
            if stage == 0 { return [this.install] }
            if stage == 1 { return [this.invalid] }
            return [this.added]
          }
        }
        Void main() {
          var catalog = Catalog()
          var agent = Agent(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions",
            toolProvider: catalog.tools)
          agent.chat("install and repair")
          require(condition: catalog.stage == 2, message: "accepted catalog remains callable")
          printLine("done")
        }
        """
              .formatted(
                  scenario.equals("schema") ? "@Tool(description: \"Unsupported schema\")" : "",
                  scenario.equals("schema") ? "Unsupported value" : "",
                  server.getAddress().getPort()));
      assertEquals("done\n", NormTestKit.run(entry).replace("\r\n", "\n"));
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertEquals(3, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.api.Test
  void loadsSkillDataAtRunTimeAndSupportsFluentPluginRegistration() throws Exception {
    installLibraries();
    var failure = new AtomicReference<Throwable>();
    var skillRequests = new AtomicInteger();
    var requests = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/skill.md",
        exchange -> {
          skillRequests.incrementAndGet();
          byte[] body =
              "Use the Norm tool catalog before editing source.".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("Use the Norm tool catalog before editing source."), body);
            int request = requests.getAndIncrement();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            String event =
                request == 0
                    ? "data: {\"id\":\"r1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"echo\",\"function\":{\"name\":\"echo\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                    : "data: {\"id\":\"r2\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"done\"},\"finish_reason\":\"stop\"}]}\n\n";
            exchange
                .getResponseBody()
                .write((event + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
          } catch (Throwable error) {
            failure.compareAndSet(null, error);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("plugin_api_app"));
      Files.writeString(
          app.resolve("module.norm"),
          "Module module() { return module(name: \"plugin_api_app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"agent\", version: 1)]) }");
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
          package plugin_api_app
          import agent.Agent
          import agent.Skill
          import openai.Tool
          import std.io.print
          @Tool(description: "Echo")
          String echo() { return "ok" }
          Void main() {
            var agent = Agent(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions",
              instruction: "base", tools: [])
            agent.onText { print(text: text) }
            agent.skill(Skill(name: "norm-skill", url: "http://127.0.0.1:%d/skill.md"))
            agent.skill("http://127.0.0.1:%d/skill.md")
            agent.tool(echo.function)
            var response = agent.chat("hello")
            require(condition: response.text == "done", message: "agent response")
            printLine("done")
          }
          """
              .formatted(
                  server.getAddress().getPort(),
                  server.getAddress().getPort(),
                  server.getAddress().getPort()));
      assertEquals("donedone\n", NormTestKit.run(entry).replace("\r\n", "\n"));
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertEquals(2, skillRequests.get());
      assertEquals(2, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.api.Test
  void closesToolScopeAndNotifiesFailureWhenPublicationFails() throws Exception {
    installLibraries();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          exchange
              .getResponseBody()
              .write(
                  """
        data: {"id":"r1","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"one","function":{"name":"noop","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}

        data: [DONE]

        """
                      .getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("app"));
      Files.writeString(
          app.resolve("module.norm"),
          "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"agent\", version: 1)]) }");
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
        package app
        import agent.Agent
        import openai.Tool
        import agent.ToolScope
        import std.core.Exception
        class Scope implements ToolScope {
          Boolean closed = false
          Integer failed = 0
          public String? complete() { throw Exception(message: "publication failed") }
          public Void close() { closed = true }
          ToolScope begin() { return this }
          @Tool(description: "Do nothing")
          Void noop() {}
        }
        Void main() {
          var scope = Scope()
          var agent = Agent(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions",
            tools: [scope.noop], beginToolScope: scope.begin)
          try {
            agent.chat("run", onFailed: (Exception error) {
              require(condition: scope.closed && error.message == "publication failed", message: "close before failure notification")
              scope.failed = scope.failed + 1
            })
          } catch Exception error { require(condition: error.message == "publication failed", message: "original exception") }
          require(condition: scope.closed && scope.failed == 1, message: "scope cleanup")
          printLine("done")
        }
        """
              .formatted(server.getAddress().getPort()));
      assertEquals("done\n", NormTestKit.run(entry).replace("\r\n", "\n"));
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.api.Test
  void refreshesToolsBetweenRoundsAndPreservesAnExplicitSession() throws Exception {
    installLibraries();
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
            int round = requests.getAndIncrement();
            assertEquals(
                round == 0 ? "install" : "added",
                body.getAsJsonArray("tools")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name")
                    .getAsString());
            if (round == 0) assertEquals(1, body.getAsJsonArray("messages").size());
            if (round == 1) {
              assertEquals(
                  "2",
                  body.getAsJsonArray("messages")
                      .get(3)
                      .getAsJsonObject()
                      .get("content")
                      .getAsString());
              assertEquals(
                  "published",
                  body.getAsJsonArray("messages")
                      .get(4)
                      .getAsJsonObject()
                      .get("content")
                      .getAsString());
            }
            if (round == 2) {
              var messages = body.getAsJsonArray("messages");
              assertEquals(7, messages.size());
              assertEquals("done", messages.get(5).getAsJsonObject().get("content").getAsString());
              assertEquals(
                  "continue", messages.get(6).getAsJsonObject().get("content").getAsString());
            }
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            String data =
                round == 0
                    ? """
          data: {"id":"r1","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"one","function":{"name":"install","arguments":"{}"}},{"index":1,"id":"two","function":{"name":"install","arguments":"{}"}}]},"finish_reason":null}]}

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
          "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"agent\", version: 1)]) }");
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
        package app
        import agent.Agent
        import agent.AgentSession
        import openai.Tool
        import agent.ToolScope
        class Catalog {
          Integer count = 0
          Integer scopes = 0
          Integer completed = 0
          Integer closed = 0
          @Tool(description: "Install a tool")
          Integer install() { count = count + 1 return count }
          @Tool(description: "Added tool")
          Integer added() { return count }
          List<Function<?>> tools() { return if count == 0 { [this.install] } else { [this.added] } }
          ToolScope begin() { scopes = scopes + 1 return Scope(catalog: this) }
        }
        class Scope implements ToolScope {
          Catalog catalog
          public String? complete() { catalog.completed = catalog.completed + 1 return "published" }
          public Void close() { catalog.closed = catalog.closed + 1 }
        }
        Void main() {
          var catalog = Catalog()
          var agent = Agent(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions",
            toolProvider: catalog.tools, beginToolScope: catalog.begin)
          var session = AgentSession()
          agent.chat("install", session: session)
          require(condition: catalog.count == 2, message: "response tool snapshot")
          agent.chat("continue", session: session)
          require(condition: catalog.scopes == 1 && catalog.completed == 1 && catalog.closed == 1, message: "whole response scope")
          printLine("done")
        }
        """
              .formatted(server.getAddress().getPort()));
      assertEquals("done\n", NormTestKit.run(entry).replace("\r\n", "\n"));
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertEquals(3, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"http", "truncated", "callback"})
  void notifiesFailureOnceAndPropagatesTheOriginalError(String scenario) throws Exception {
    installLibraries();
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
    installLibraries();
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
            dependencies: [dependency(repository: "github", name: "agent", version: 1)]) }
          """);
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
          package app
          import agent.Agent
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
            var agent = Agent(apiKey: "test", model: "model", endpoint: "http://127.0.0.1:%d/v1/chat/completions",
              tools: [counter.add])
            var response = agent.chat("add three",
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
            agent.chat("add three again")
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

  @org.junit.jupiter.api.Test
  void discoversAndInvokesMcpToolsThroughTheAgentProvider() throws Exception {
    installLibraries();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/mcp",
        exchange -> {
          var request =
              JsonParser.parseString(
                      new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                  .getAsJsonObject();
          String method = request.get("method").getAsString();
          String body =
              method.equals("tools/list")
                  ? "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"remote\",\"description\":\"Remote tool\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}"
                  : "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}";
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
          exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try {
      Path app = Files.createDirectories(directory.resolve("mcp_app"));
      Files.writeString(
          app.resolve("module.norm"),
          "Module module() { return module(name: \"mcp_app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"agent\", version: 1)]) }");
      Path entry = app.resolve("main.norm");
      Files.writeString(
          entry,
          """
          package mcp_app
          import agent.McpClient
          import openai.ToolCall
          Void main() {
            var client = McpClient(endpoint: "http://127.0.0.1:%d/mcp")
            require(condition: client.definitions().size() == 1, message: "MCP discovery")
            require(condition: client.supports(name: "remote"), message: "MCP tool lookup")
            String result = client.invoke(call: ToolCall(callId: "call-1", name: "remote", arguments: "{}"))
            require(condition: result.contains(value: "ok"), message: "MCP invocation")
            printLine("done")
          }
          """
              .formatted(server.getAddress().getPort()));
      assertEquals("done\n", NormTestKit.run(entry).replace("\r\n", "\n"));
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.api.Test
  void usesJsonSessionStoreByDefaultAndAcceptsStorageAdapters() throws Exception {
    installLibraries();
    Path app = Files.createDirectories(directory.resolve("session_app"));
    Files.writeString(
        app.resolve("module.norm"),
        "Module module() { return module(name: \"session_app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"agent\", version: 1)]) }");
    Path entry = app.resolve("main.norm");
    Files.writeString(
        entry,
        """
        package session_app
        import agent.AgentSession
        import agent.SessionStore
        import std.json.JsonValue
        class Store implements SessionStore {
          JsonValue state = JsonValue.Array(values: [])
          public JsonValue load() { return state }
          public Void save(JsonValue value) { state = value }
        }
        Void main() {
          var defaultSession = AgentSession()
          defaultSession.addContext(text: "json")
          require(condition: defaultSession.items().size() == 1, message: "default JSON store")
          var store = Store()
          var customSession = AgentSession(store: store)
          customSession.addContext(text: "adapter")
          require(condition: store.state != JsonValue.Null, message: "custom store adapter")
          printLine("done")
        }
        """);
    assertEquals("done\n", NormTestKit.run(entry).replace("\r\n", "\n"));
  }

  private void installLibraries() throws Exception {
    Path libraries =
        Path.of(System.getProperty("norm.test.stdlib"))
            .getParent()
            .getParent()
            .resolve("libraries");
    for (String name : List.of("openai", "agent")) {
      Path sourceRoot = libraries.resolve(name);
      Path dependency = Files.createDirectories(directory.resolve("dependencies").resolve(name));
      try (var files = Files.list(sourceRoot)) {
        for (Path source : files.filter(path -> path.toString().endsWith(".norm")).toList())
          Files.copy(source, dependency.resolve(source.getFileName()));
      }
    }
  }
}
