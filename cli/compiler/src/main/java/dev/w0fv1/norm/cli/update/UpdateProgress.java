package dev.w0fv1.norm.cli.update;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class UpdateProgress implements AutoCloseable {
  private final PrintWriter output;
  private final boolean interactive;
  private final ScheduledExecutorService timer;
  private String stage;
  private long started;
  private long lastData;
  private long transferred = -1;
  private long total = -1;
  private int lineWidth;
  private boolean closed;

  public UpdateProgress(PrintWriter output) {
    this(output, System.console() != null || Boolean.getBoolean("norm.console"));
  }

  public UpdateProgress(PrintWriter output, boolean interactive) {
    this(output, interactive, Duration.ofMillis(interactive ? 250 : 5000));
  }

  UpdateProgress(PrintWriter output, boolean interactive, Duration interval) {
    this.output = output;
    this.interactive = interactive;
    timer =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("norm-update-progress").factory());
    timer.scheduleAtFixedRate(
        this::refresh, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  public synchronized void stage(String label) {
    endLine();
    stage = label;
    started = lastData = System.nanoTime();
    transferred = total = -1;
    render(label + "...");
  }

  public synchronized void download(long bytes, long size) {
    if (bytes != transferred) lastData = System.nanoTime();
    transferred = bytes;
    total = size;
  }

  synchronized void refresh() {
    if (closed || stage == null) return;
    long now = System.nanoTime();
    double seconds = (now - started) / 1_000_000_000.0;
    String status = String.format(Locale.ROOT, "%s... %.0fs", stage, seconds);
    if (transferred >= 0) {
      String amount =
          total > 0
              ? String.format(
                  Locale.ROOT,
                  "%d%% · %.1f / %.1f MB",
                  Math.min(100, (long) (100.0 * transferred / total)),
                  transferred / 1_000_000.0,
                  total / 1_000_000.0)
              : String.format(Locale.ROOT, "%.1f MB", transferred / 1_000_000.0);
      String rate =
          now - lastData >= TimeUnit.SECONDS.toNanos(3)
              ? "waiting for data"
              : String.format(
                  Locale.ROOT, "%.1f MB/s", transferred / 1_000_000.0 / Math.max(seconds, 0.001));
      status = String.format(Locale.ROOT, "%s... %s · %s · %.0fs", stage, amount, rate, seconds);
    }
    render(status);
  }

  public synchronized void message(String text) {
    endLine();
    stage = null;
    output.println(text);
    output.flush();
  }

  public synchronized void detail(String text) {
    endLine();
    output.println(text);
    output.flush();
  }

  private void render(String text) {
    if (interactive) {
      output.print("\r" + text + " ".repeat(Math.max(0, lineWidth - text.length())));
      lineWidth = text.length();
    } else output.println(text);
    output.flush();
  }

  private void endLine() {
    if (lineWidth > 0) {
      output.println();
      lineWidth = 0;
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    timer.shutdownNow();
    endLine();
    output.flush();
  }
}
