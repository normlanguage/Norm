package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
final class FileAgentApplicationTest {
  @TempDir Path directory;

  @Test
  void runsWithOnlyTheInitialFileTools() throws Exception {
    Path norm = Path.of(System.getProperty("norm.test.stdlib")).getParent().getParent();
    Path app = Files.createDirectories(directory.resolve("self_extending_agent"));
    for (var mapping :
        Map.of(
                norm.resolve("examples/self_extending_agent"), app,
                norm.resolve("libraries/openai"), directory.resolve("dependencies/openai"),
                norm.resolve("libraries/agent"), directory.resolve("dependencies/agent"))
            .entrySet()) {
      Files.createDirectories(mapping.getValue());
      try (var files = Files.list(mapping.getKey())) {
        for (Path source : files.filter(path -> path.toString().endsWith(".norm")).toList())
          Files.copy(source, mapping.getValue().resolve(source.getFileName()));
      }
    }
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
            assertEquals(0, rounds.getAndIncrement());
            Set<String> names =
                request.getAsJsonArray("tools").asList().stream()
                    .map(
                        item ->
                            item.getAsJsonObject()
                                .getAsJsonObject("function")
                                .get("name")
                                .getAsString())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("read_file", "write_file"), names, request.toString());
            JsonObject delta = new JsonObject();
            delta.addProperty("role", "assistant");
            delta.addProperty("content", "FILE-AGENT-VERIFIED");
            JsonObject choice = new JsonObject();
            choice.addProperty("index", 0);
            choice.add("delta", delta);
            choice.addProperty("finish_reason", "stop");
            JsonArray choices = new JsonArray();
            choices.add(choice);
            JsonObject response = new JsonObject();
            response.addProperty("id", "file-agent");
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
        java.util.List.of(
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
            "Inspect the project.");
    var builder =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(output.toFile())
            .redirectError(diagnostics.toFile());
    builder.environment().put("OPENAI_API_KEY", "test");
    builder.environment().put("OPENAI_MODEL", "model");
    builder
        .environment()
        .put(
            "OPENAI_BASE_URL",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
    Process child = builder.start();
    try {
      assertTrue(child.waitFor(90, TimeUnit.SECONDS));
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertEquals(0, child.exitValue(), Files.readString(diagnostics));
      assertTrue(Files.readString(output).contains("FILE-AGENT-VERIFIED"));
      assertEquals(1, rounds.get());
    } finally {
      server.stop(0);
      if (child.isAlive()) {
        child.destroyForcibly();
        child.waitFor(10, TimeUnit.SECONDS);
      }
    }
  }
}
