package dev.w0fv1.norm.lsp;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.workspace.Workspace;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
final class LanguageServerLauncherTest {
  private static ProjectEnvironment environment;

  @BeforeAll
  static void prepareEnvironment() throws Exception {
    environment = ProjectEnvironment.bootstrap(new NormRuntime());
  }

  @Test
  void shutdownAndExitEndTheSessionWhileClientInputRemainsOpen() throws Exception {
    var existingThreads = Thread.getAllStackTraces().keySet();
    try (var client = new PipedOutputStream();
        var input = new PipedInputStream(client);
        var callers = Executors.newSingleThreadExecutor()) {
      var output = new ByteArrayOutputStream();
      var result =
          callers.submit(
              () -> LanguageServerLauncher.run(new Workspace(environment), input, output));
      write(
          client,
          "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"capabilities\":{}}}");
      write(client, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"shutdown\"}");
      write(client, "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}");
      assertEquals(0, result.get(20, TimeUnit.SECONDS));
      assertTrue(output.toString(StandardCharsets.UTF_8).contains("capabilities"));
      assertSessionThreadsStopped(existingThreads);
    }
  }

  @Test
  void exitWithoutShutdownReturnsProtocolFailure() throws Exception {
    try (var client = new PipedOutputStream();
        var input = new PipedInputStream(client);
        var callers = Executors.newSingleThreadExecutor()) {
      var result =
          callers.submit(
              () ->
                  LanguageServerLauncher.run(
                      new Workspace(environment), input, new ByteArrayOutputStream()));
      write(client, "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}");
      assertEquals(1, result.get(20, TimeUnit.SECONDS));
    }
  }

  @Test
  void eofTerminatesWithoutLeakingTransportThreads() throws Exception {
    var existingThreads = Thread.getAllStackTraces().keySet();
    assertEquals(
        1,
        LanguageServerLauncher.run(
            new Workspace(environment),
            new ByteArrayInputStream(new byte[0]),
            new ByteArrayOutputStream()));
    assertSessionThreadsStopped(existingThreads);
  }

  @Test
  void retainsTheActualTransportFailure() throws InterruptedException {
    var existingThreads = Thread.getAllStackTraces().keySet();
    var failure = new IOException("disconnected");
    InputStream input =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw failure;
          }
        };
    var actual =
        assertThrows(
            IOException.class,
            () ->
                LanguageServerLauncher.run(
                    new Workspace(environment), input, new ByteArrayOutputStream()));
    assertSame(failure, actual);
    assertSessionThreadsStopped(existingThreads);
  }

  private static void assertSessionThreadsStopped(Set<Thread> existingThreads)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    var sessionThreads =
        Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getName().startsWith("norm-lsp-"))
            .filter(thread -> !existingThreads.contains(thread))
            .toList();
    for (var thread : sessionThreads) {
      long remaining = deadline - System.nanoTime();
      if (remaining > 0)
        thread.join(
            TimeUnit.NANOSECONDS.toMillis(remaining),
            (int) (remaining % TimeUnit.MILLISECONDS.toNanos(1)));
      assertFalse(thread.isAlive(), thread.getName());
    }
  }

  private static void write(PipedOutputStream client, String message) throws IOException {
    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
    client.write(
        ("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    client.write(bytes);
    client.flush();
  }
}
