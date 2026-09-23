package dev.w0fv1.norm.platform.jdk;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.PlatformDuration;
import dev.w0fv1.norm.platform.process.PlatformProcessException;
import dev.w0fv1.norm.platform.process.ProcessOutcome;
import dev.w0fv1.norm.platform.process.ProcessRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JdkProcessRunnerTest {
  @TempDir Path directory;

  @Test
  void drainsBothStreamsAndCapturesInputWithBoundedOutput() {
    var request =
        new ProcessRequest(
            java(),
            List.of("-cp", System.getProperty("java.class.path"), Probe.class.getName(), "copy"),
            directory,
            Map.of("NORM_PROCESS_TEST", "中文"),
            "a  b".getBytes(StandardCharsets.UTF_8),
            32);
    var result =
        new JdkProcessRunner()
            .run(request, new OperationControl(() -> false, new PlatformDuration(15, 0)));
    assertEquals(ProcessOutcome.EXITED, result.outcome());
    assertEquals(7, result.exitCode());
    assertTrue(new String(result.stdout(), StandardCharsets.UTF_8).startsWith("a  b中文"));
    assertEquals(32, result.stdout().length);
    assertEquals(32, result.stderr().length);
    assertTrue(result.stdoutTruncated());
    assertTrue(result.stderrTruncated());
  }

  @Test
  void distinguishesTimeoutCancellationAndStartFailure() {
    var request =
        new ProcessRequest(
            java(),
            List.of("-cp", System.getProperty("java.class.path"), Probe.class.getName(), "sleep"),
            directory,
            Map.of(),
            new byte[0],
            100);
    assertEquals(
        ProcessOutcome.TIMED_OUT,
        new JdkProcessRunner()
            .run(request, new OperationControl(() -> false, new PlatformDuration(0, 300_000_000)))
            .outcome());
    assertEquals(
        ProcessOutcome.CANCELLED,
        new JdkProcessRunner()
            .run(request, new OperationControl(() -> true, new PlatformDuration(5, 0)))
            .outcome());
    var missing =
        new ProcessRequest(
            directory.resolve("missing-executable").toString(),
            List.of(),
            directory,
            Map.of(),
            new byte[0],
            100);
    assertThrows(
        PlatformProcessException.class,
        () ->
            new JdkProcessRunner()
                .run(missing, new OperationControl(() -> false, new PlatformDuration(5, 0))));
  }

  private String java() {
    return Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
        .toString();
  }

  @Test
  void cancelsRunningProcessAndItsObservedChild() {
    var request =
        new ProcessRequest(
            java(),
            List.of("-cp", System.getProperty("java.class.path"), Probe.class.getName(), "tree"),
            directory,
            Map.of(),
            new byte[0],
            200);
    long started = System.nanoTime();
    var result =
        new JdkProcessRunner()
            .run(
                request,
                new OperationControl(
                    () -> System.nanoTime() - started > TimeUnit.SECONDS.toNanos(2),
                    new PlatformDuration(10, 0)));
    assertEquals(ProcessOutcome.CANCELLED, result.outcome());
    var pids =
        new String(result.stdout(), StandardCharsets.US_ASCII)
            .lines()
            .map(Long::parseLong)
            .toList();
    assertEquals(2, pids.size());
    for (long pid : pids)
      assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
  }

  public static final class Probe {
    public static void main(String[] arguments) throws Exception {
      if (arguments[0].equals("sleep")) {
        Thread.sleep(30_000);
        return;
      }
      if (arguments[0].equals("tree")) {
        Process child =
            new ProcessBuilder(
                    ProcessHandle.current().info().command().orElseThrow(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    Probe.class.getName(),
                    "sleep")
                .inheritIO()
                .start();
        System.out.println(ProcessHandle.current().pid());
        System.out.println(child.pid());
        System.out.flush();
        Thread.sleep(30_000);
        return;
      }
      System.out.write(System.in.readAllBytes());
      System.out.write(System.getenv("NORM_PROCESS_TEST").getBytes(StandardCharsets.UTF_8));
      byte[] chunk = new byte[8192];
      for (int index = 0; index < 32; index++) {
        System.out.write(chunk);
        System.err.write(chunk);
      }
      System.exit(7);
    }
  }
}
