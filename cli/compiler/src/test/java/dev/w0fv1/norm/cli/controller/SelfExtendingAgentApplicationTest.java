package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.testing.MavenTestRepository;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.Sha256Digest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(240)
final class SelfExtendingAgentApplicationTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void repairsSourceAddsDependencyAndInvokesNewToolInTheSameProcess(boolean jarDependency)
      throws Exception {
    Path norm = Path.of(System.getProperty("norm.test.stdlib")).getParent().getParent();
    Path app = Files.createDirectories(directory.resolve("agent"));
    for (var mapping :
        Map.of(
                norm.resolve("examples/agent"),
                app,
                norm.resolve("libraries/openai"),
                directory.resolve("dependencies/openai"))
            .entrySet()) {
      Files.createDirectories(mapping.getValue());
      try (var files = Files.list(mapping.getKey())) {
        for (Path source : files.filter(path -> path.toString().endsWith(".norm")).toList())
          Files.copy(source, mapping.getValue().resolve(source.getFileName()));
      }
    }
    String originalTools = Files.readString(app.resolve("tools.norm"));
    String toolName = jarDependency ? "reverse_text" : "answer";
    String expandedTools =
        originalTools.replace(
            "writeFile.function]",
            "writeFile.function, " + (jarDependency ? "reverseText" : "answer") + ".function]");
    String binding = "";
    if (jarDependency) {
      Path jar = Files.createDirectories(app.resolve("lib")).resolve("commons-lang3.jar");
      try (var resolver =
          new JarResolver(MavenTestRepository.prepare(directory.resolve("maven-cache")))) {
        var graph =
            resolver.resolve(
                directory,
                new JarBinding(
                    new MavenJarTarget(MavenTestRepository.commonsLang(), Optional.empty())));
        Files.copy(graph.root().file(), jar);
      }
      binding =
          ", binding: jarBinding(target: localJar(path: \"lib/commons-lang3.jar\", integrity: sha256(\""
              + Sha256Digest.compute(jar).value()
              + "\")), api: [jarType(name: \"org.apache.commons.lang3.StringUtils\", members: [\"reverse\"])])";
    }
    String dependencyModule =
        "Module module() { module(name: \"agent\", version: 1, dependencies: [dependency(repository: \"github\", name: \"openai\", version: 1)"
            + (jarDependency
                ? ""
                : ", dependency(repository: \"github\", name: \"capability\", version: 1)")
            + "]"
            + binding
            + ") }";
    String dependencyTool =
        jarDependency
            ? """
        package agent
        import openai.Tool
        import openai.ToolParameter
        @Tool(name: "reverse_text", description: "Reverse text using Apache Commons Lang")
        String reverseText(@ToolParameter(description: "Text to reverse") String text) {
          return stringUtilsReverse(text) ?? ""
        }
        """
            : "package agent\nimport openai.Tool\nimport capability.compute\n@Tool(name: \"answer\", description: \"Compute the answer through the added dependency\")\nInteger answer() { compute() }";
    var rounds = new AtomicInteger();
    var failure = new AtomicReference<Throwable>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            var request =
                JsonParser.parseString(
                        new String(
                            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            int round = rounds.getAndIncrement();
            Set<String> names =
                request.getAsJsonArray("tools").asList().stream()
                    .map(
                        item ->
                            item.getAsJsonObject()
                                .getAsJsonObject("function")
                                .get("name")
                                .getAsString())
                    .collect(Collectors.toSet());
            assertEquals(
                round < 2
                    ? Set.of("read_file", "write_file")
                    : Set.of("read_file", "write_file", toolName),
                names,
                request.toString());
            JsonArray calls = new JsonArray();
            if (round == 0) {
              calls.add(
                  writeCall(0, app.resolve("extra.norm"), "package agent\nInteger broken( {"));
            } else if (round == 1) {
              assertTrue(request.toString().contains("Rejected"), request.toString());
              if (!jarDependency) {
                calls.add(
                    writeCall(
                        0,
                        directory.resolve("dependencies/capability/module.norm"),
                        "Module module() { module(name: \"capability\", version: 1, exports: [\"value\"]) }"));
                calls.add(
                    writeCall(
                        1,
                        directory.resolve("dependencies/capability/value.norm"),
                        "package capability\npublic Integer compute() { 42 }"));
              }
              calls.add(writeCall(calls.size(), app.resolve("module.norm"), dependencyModule));
              calls.add(writeCall(calls.size(), app.resolve("extra.norm"), dependencyTool));
              calls.add(writeCall(calls.size(), app.resolve("tools.norm"), expandedTools));
            } else if (round == 2) {
              assertTrue(request.toString().contains("Published"), request.toString());
              JsonObject call = new JsonObject();
              call.addProperty("index", 0);
              call.addProperty("id", "answer-call");
              call.addProperty("type", "function");
              JsonObject function = new JsonObject();
              function.addProperty("name", toolName);
              function.addProperty(
                  "arguments", jarDependency ? "{\"text\":\"Norm evolves 42\"}" : "{}");
              call.add("function", function);
              calls.add(call);
            } else {
              assertEquals(3, round);
              assertTrue(
                  request.getAsJsonArray("messages").asList().stream()
                      .anyMatch(
                          item -> {
                            var message = item.getAsJsonObject();
                            return message.has("tool_call_id")
                                && message.get("tool_call_id").getAsString().equals("answer-call")
                                && message
                                    .get("content")
                                    .getAsString()
                                    .equals(jarDependency ? "\"24 sevlove mroN\"" : "42");
                          }),
                  request.toString());
            }
            JsonObject delta = new JsonObject();
            delta.addProperty("role", "assistant");
            if (calls.isEmpty()) delta.addProperty("content", "SELF-EXTENSION-VERIFIED");
            else delta.add("tool_calls", calls);
            JsonObject choice = new JsonObject();
            choice.addProperty("index", 0);
            choice.add("delta", delta);
            choice.addProperty("finish_reason", calls.isEmpty() ? "stop" : "tool_calls");
            JsonArray choices = new JsonArray();
            choices.add(choice);
            JsonObject response = new JsonObject();
            response.addProperty("id", "round-" + round);
            response.add("choices", choices);
            byte[] body =
                ("data: " + response + "\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } catch (Throwable error) {
            failure.compareAndSet(null, error);
          } finally {
            exchange.close();
          }
        });
    server.start();
    Path output = directory.resolve("stdout.txt");
    Path diagnostics = directory.resolve("stderr.txt");
    var command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--add-modules",
                "ALL-SYSTEM",
                "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                "--sun-misc-unsafe-memory-access=allow",
                "--enable-native-access=org.graalvm.truffle",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                "--module-path",
                System.getProperty("norm.test.compilerJar")
                    + java.io.File.pathSeparator
                    + Path.of(System.getProperty("norm.test.runtimeDirectory"), "lib"),
                "--module",
                "dev.w0fv1.norm/dev.w0fv1.norm.cli.Main",
                "run",
                app.resolve("main.norm").toString(),
                "--",
                "Extend yourself and compute the answer."));
    var builder =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(output.toFile())
            .redirectError(diagnostics.toFile());
    builder
        .environment()
        .putAll(
            Map.of(
                "OPENAI_API_KEY",
                "test",
                "OPENAI_MODEL",
                "model",
                "OPENAI_BASE_URL",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions"));
    Process process = builder.start();
    try {
      assertTrue(process.waitFor(180, TimeUnit.SECONDS), "Self-extension timed out");
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertEquals(0, process.exitValue(), Files.readString(diagnostics));
      assertTrue(
          Files.readString(output).contains("SELF-EXTENSION-VERIFIED"), Files.readString(output));
      assertEquals(4, rounds.get());
      assertEquals(expandedTools, Files.readString(app.resolve("tools.norm")));
    } finally {
      server.stop(0);
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
      }
    }
  }

  private static JsonObject writeCall(int index, Path path, String content) {
    JsonObject arguments = new JsonObject();
    arguments.addProperty("path", path.toString());
    arguments.addProperty("content", content);
    JsonObject function = new JsonObject();
    function.addProperty("name", "write_file");
    function.addProperty("arguments", arguments.toString());
    JsonObject call = new JsonObject();
    call.addProperty("index", index);
    call.addProperty("id", "write-" + index);
    call.addProperty("type", "function");
    call.add("function", function);
    return call;
  }
}
