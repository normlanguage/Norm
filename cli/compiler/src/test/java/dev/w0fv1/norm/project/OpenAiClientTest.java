package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class OpenAiClientTest {
  @TempDir Path directory;

  @org.junit.jupiter.api.Test
  void registersAnnotatedFunctionsWithDefaultsAndBoundReceivers() throws Exception {
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
        import openai.Tool
        import openai.ToolParameter
        import openai.ToolRegistry
        import openai.ToolCall
        import openai.OpenAiException
        import openai.structuredOutput
        import std.serialization.Serializable
        import std.json.writeJson
        @Serializable()
        value Answer { String text String? detail }
        class Counter {
          Integer total = 10
          @Tool(description: "Increment", name: "add")
          Integer add(@ToolParameter(description: "Amount") Integer amount = 2) {
            total = total + amount
            return total
          }
          @Tool(description: "Read the counter")
          Integer current() { return total }
        }
        Void main() {
          var counter = Counter()
          var registry = ToolRegistry(tools: [counter.add, counter.current])
          printLine(registry.invoke(call: ToolCall(callId: "one", name: "add", arguments: "{}")))
          require(condition: registry.invoke(call: ToolCall(callId: "two", name: "current", arguments: " ")) == "12", message: "empty nullary arguments")
          var duplicate = false
          try { ToolRegistry(tools: [counter.add, counter.add]) }
          catch OpenAiException error { duplicate = true }
          require(condition: duplicate, message: "duplicate registration")
          var schema = writeJson(value: structuredOutput<Answer>().schema)
          require(condition: schema.contains(value: "required") && schema.contains(value: "detail"), message: "typed schema")
          printLine(counter.total)
        }
        """);
    assertEquals("12\n12\n", NormTestKit.run(entry).replace("\r\n", "\n"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"completed", "incomplete", "refusal", "http_error", "malformed", "json_object"})
  void exchangesRealHttpAndPreservesResponseSemantics(String scenario) throws Exception {
    Path library =
        Path.of(System.getProperty("norm.test.stdlib"))
            .getParent()
            .getParent()
            .resolve("libraries/openai");
    assertTrue(Files.isDirectory(library), "OpenAI module must exist");
    Path project = Files.createDirectories(directory.resolve("dependencies/openai"));
    try (var files = Files.list(library)) {
      for (Path source : files.filter(p -> p.toString().endsWith(".norm")).toList())
        Files.copy(source, project.resolve(source.getFileName()));
    }
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setSoTimeout(30_000);
      var exchange =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(15_000);
                  var input = socket.getInputStream();
                  var headerBytes = new ByteArrayOutputStream();
                  int matched = 0;
                  while (matched < 4) {
                    int next = input.read();
                    if (next < 0) throw new java.io.EOFException();
                    headerBytes.write(next);
                    matched = next == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : 0;
                  }
                  String headers = headerBytes.toString(StandardCharsets.UTF_8);
                  assertTrue(headers.startsWith("POST /v1/responses HTTP/1.1"), headers);
                  assertTrue(headers.toLowerCase().contains("authorization: bearer test-key"));
                  int length =
                      headers
                          .lines()
                          .filter(line -> line.toLowerCase().startsWith("content-length:"))
                          .mapToInt(line -> Integer.parseInt(line.substring(15).trim()))
                          .findFirst()
                          .orElseThrow();
                  var body =
                      JsonParser.parseString(
                              new String(input.readNBytes(length), StandardCharsets.UTF_8))
                          .getAsJsonObject();
                  assertEquals("test-model", body.get("model").getAsString());
                  assertEquals("创建分支", body.get("input").getAsString());
                  assertFalse(body.get("store").getAsBoolean());
                  if (scenario.equals("json_object")) {
                    assertEquals(
                        "json_object",
                        body.getAsJsonObject("text")
                            .getAsJsonObject("format")
                            .get("type")
                            .getAsString());
                    assertEquals(1, body.getAsJsonObject("text").getAsJsonObject("format").size());
                    assertTrue(
                        body.get("instructions").getAsString().contains("JSON schema action:"));
                  } else {
                    assertTrue(
                        body.getAsJsonObject("text")
                            .getAsJsonObject("format")
                            .get("strict")
                            .getAsBoolean());
                    assertEquals(
                        "object",
                        body.getAsJsonObject("text")
                            .getAsJsonObject("format")
                            .getAsJsonObject("schema")
                            .get("type")
                            .getAsString());
                  }
                  String content =
                      scenario.equals("refusal")
                          ? "{\"type\":\"refusal\",\"refusal\":\"declined\"}"
                          : "{\"type\":\"output_text\",\"text\":\"{\\\"branch\\\":\\\"demo\\\"}\"}";
                  String response =
                      "{\"id\":\"resp_test\",\"status\":\""
                          + (scenario.equals("incomplete") ? "incomplete" : "completed")
                          + "\",\"output\":[{\"type\":\"reasoning\",\"summary\":[]},{\"type\":\"message\",\"content\":["
                          + content
                          + "]}],\"usage\":{\"input_tokens\":12,\"output_tokens\":5,\"total_tokens\":17},\"incomplete_details\":"
                          + (scenario.equals("incomplete")
                              ? "{\"reason\":\"max_output_tokens\"}"
                              : "null")
                          + ",\"future_field\":true}";
                  if (scenario.equals("http_error"))
                    response =
                        "{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"try later\"}}";
                  if (scenario.equals("malformed")) response = "{\"status\":123}";
                  byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 "
                                  + (scenario.equals("http_error")
                                      ? "429 Too Many Requests"
                                      : "200 OK")
                                  + "\r\nContent-Type: application/json\r\nx-request-id: req_test\r\nContent-Length: "
                                  + bytes.length
                                  + "\r\nConnection: close\r\n\r\n")
                              .getBytes(StandardCharsets.US_ASCII));
                  socket.getOutputStream().write(bytes);
                  return null;
                }
              });
      Path application = Files.createDirectories(directory.resolve("app"));
      Files.writeString(
          application.resolve("module.norm"),
          "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"openai\", version: 1)]) }");
      Path entry = application.resolve("application.norm");
      Files.writeString(
          entry,
          """
          package app
          import openai.Client
          import openai.ResponseInput
          import openai.ResponseRequest
          import openai.StructuredOutput
          import openai.StructuredOutputMode
          import openai.OpenAiException
          import std.http.Uri
          import std.json.parseJson
          Void main() {
            Client client = Client(apiKey: "test-key", model: "test-model", endpoint: "http://127.0.0.1:%d/v1/responses")
            try {
              var result = client.exchange(stream: false, request: ResponseRequest(model: "test-model", input: ResponseInput.Text(value: "创建分支"),
                format: StructuredOutput(mode: StructuredOutputMode.%s, name: "action", schema: parseJson(value: "{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{},\\\"additionalProperties\\\":false}"))))
              printLine(result.status)
              printLine(result.refusal ?? "none")
              printLine(result.incompleteReason ?? "none")
              printLine(result.usage?.totalTokens ?? 0)
            } catch OpenAiException error { printLine(error.code) printLine(error.httpStatus ?? 0) }
          }
          """
              .formatted(
                  server.getLocalPort(),
                  scenario.equals("json_object") ? "JsonObject" : "JsonSchema"));
      String actual = NormTestKit.run(entry).replace("\r\n", "\n");
      String expected =
          switch (scenario) {
            case "http_error" -> "rate_limit_exceeded\n429\n";
            case "malformed" -> "OPENAI-PROTOCOL\n200\n";
            case "incomplete" -> "ResponseStatus.Incomplete\nnone\nmax_output_tokens\n17\n";
            case "refusal" -> "ResponseStatus.Completed\ndeclined\nnone\n17\n";
            default -> "ResponseStatus.Completed\nnone\nnone\n17\n";
          };
      assertEquals(expected, actual);
      exchange.get(15, TimeUnit.SECONDS);
    }
  }
}
