package dev.w0fv1.norm.cli.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class UpdateProgressTest {
  @Test
  void preservesLauncherConsoleDetectionWhenTheRuntimeOutputIsPiped() {
    String previous = System.setProperty("norm.console", "true");
    var text = new StringWriter();
    try (var progress = new UpdateProgress(new PrintWriter(text))) {
      progress.stage("Checking updates");
      assertTrue(text.toString().contains("\r"));
    } finally {
      if (previous == null) System.clearProperty("norm.console");
      else System.setProperty("norm.console", previous);
    }
  }

  @Test
  void flushesStagesAndRendersActualBytesWithoutInventingTotals() {
    var text = new StringWriter();
    try (var progress =
        new UpdateProgress(
            new PrintWriter(new java.io.BufferedWriter(text)), false, Duration.ofHours(1))) {
      progress.stage("Checking Norm updates");
      assertTrue(text.toString().contains("Checking Norm updates"));
      progress.stage("Downloading Norm v1.2.3");
      progress.download(1024, -1);
      progress.refresh();
      assertTrue(text.toString().contains("0.0 MB"));
      assertFalse(text.toString().contains("%"));
      progress.download(1024, 2048);
      progress.refresh();
      assertTrue(text.toString().contains("50%"));
      progress.message("Downloaded Norm v1.2.3.");
    }
    assertFalse(text.toString().replace(System.lineSeparator(), "").contains("\r"));
    assertFalse(text.toString().contains("\u001b"));
  }

  @Test
  void emitsHeartbeatWhileAnOperationIsWaiting() throws Exception {
    var text = new StringWriter();
    var heartbeat = new java.util.concurrent.CountDownLatch(1);
    var writer =
        new PrintWriter(text) {
          @Override
          public void println(String value) {
            super.println(value);
            if (value.contains("0s")) heartbeat.countDown();
          }
        };
    try (var progress = new UpdateProgress(writer, false, Duration.ofMillis(25))) {
      progress.stage("Checking release");
      assertTrue(heartbeat.await(2, java.util.concurrent.TimeUnit.SECONDS));
    }
  }

  @Test
  void interactiveRefreshReusesOneLineAndClosesItBeforeMessages() {
    var text = new StringWriter();
    try (var progress = new UpdateProgress(new PrintWriter(text), true, Duration.ofHours(1))) {
      progress.stage("Downloading Norm");
      progress.download(1024, 2048);
      progress.refresh();
      progress.message("Download failed.");
    }
    assertTrue(text.toString().contains("\r"));
    assertTrue(text.toString().contains("50%"));
    assertTrue(text.toString().endsWith("Download failed." + System.lineSeparator()));
  }
}
