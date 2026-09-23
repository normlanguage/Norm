package dev.w0fv1.norm.platform.jdk;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.process.PlatformProcessException;
import dev.w0fv1.norm.platform.process.ProcessFailure;
import dev.w0fv1.norm.platform.process.ProcessOutcome;
import dev.w0fv1.norm.platform.process.ProcessRequest;
import dev.w0fv1.norm.platform.process.ProcessResult;
import dev.w0fv1.norm.platform.process.ProcessRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class JdkProcessRunner implements ProcessRunner {
  @Override
  public ProcessResult run(ProcessRequest request, OperationControl control) {
    if (control.isCancellationRequested() || control.hasTimedOut())
      return new ProcessResult(
          control.isCancellationRequested() ? ProcessOutcome.CANCELLED : ProcessOutcome.TIMED_OUT,
          null,
          new byte[0],
          new byte[0],
          false,
          false);
    var command = new ArrayList<String>();
    command.add(request.executable());
    command.addAll(request.arguments());
    Process process;
    try {
      var builder = new ProcessBuilder(command).directory(request.directory().toFile());
      request.environment().forEach(builder.environment()::put);
      process = builder.start();
    } catch (IllegalArgumentException failure) {
      throw new PlatformProcessException(
          ProcessFailure.INVALID_REQUEST, request.executable(), failure);
    } catch (IOException failure) {
      throw new PlatformProcessException(
          ProcessFailure.START_FAILED, request.executable(), failure);
    }
    var output = new Capture(request.maximumOutputBytes());
    var error = new Capture(request.maximumOutputBytes());
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    var descendants = new LinkedHashSet<ProcessHandle>();
    Future<?> stdout =
        executor.submit(
            () -> {
              output.read(process.getInputStream());
              return null;
            });
    Future<?> stderr =
        executor.submit(
            () -> {
              error.read(process.getErrorStream());
              return null;
            });
    Future<?> stdin =
        executor.submit(
            () -> {
              try (var stream = process.getOutputStream()) {
                stream.write(request.input());
              }
              return null;
            });
    ProcessOutcome outcome = ProcessOutcome.EXITED;
    boolean interrupted = false;
    boolean completed = false;
    try {
      while (process.isAlive() || !stdout.isDone() || !stderr.isDone() || !stdin.isDone()) {
        process.descendants().forEach(descendants::add);
        if (Thread.interrupted()) {
          interrupted = true;
          outcome = ProcessOutcome.CANCELLED;
          break;
        }
        if (control.isCancellationRequested()) {
          outcome = ProcessOutcome.CANCELLED;
          break;
        }
        if (control.hasTimedOut()) {
          outcome = ProcessOutcome.TIMED_OUT;
          break;
        }
        if (stdout.isDone()) stdout.get();
        if (stderr.isDone()) stderr.get();
        if (stdin.isDone()) stdin.get();
        Thread.sleep(10);
      }
      if (outcome == ProcessOutcome.EXITED) {
        stdout.get();
        stderr.get();
        stdin.get();
        completed = true;
      }
    } catch (InterruptedException failure) {
      interrupted = true;
      outcome = ProcessOutcome.CANCELLED;
    } catch (ExecutionException failure) {
      throw new PlatformProcessException(
          ProcessFailure.IO, request.executable(), failure.getCause());
    } finally {
      process.descendants().forEach(descendants::add);
      if (!completed) {
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
      executor.shutdown();
      try {
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
          executor.shutdownNow();
          throw new PlatformProcessException(
              ProcessFailure.IO,
              request.executable(),
              new IOException("process streams did not terminate"));
        }
        if (process.isAlive() && !process.waitFor(2, TimeUnit.SECONDS))
          throw new PlatformProcessException(
              ProcessFailure.IO,
              request.executable(),
              new IOException("process did not terminate"));
      } catch (InterruptedException failure) {
        interrupted = true;
        executor.shutdownNow();
        throw new PlatformProcessException(ProcessFailure.IO, request.executable(), failure);
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
    }
    return new ProcessResult(
        outcome,
        process.isAlive() ? null : process.exitValue(),
        output.bytes(),
        error.bytes(),
        output.truncated,
        error.truncated);
  }

  private static final class Capture {
    private final int maximum;
    private final ByteArrayOutputStream content = new ByteArrayOutputStream();
    private boolean truncated;

    Capture(int maximum) {
      this.maximum = maximum;
    }

    void read(InputStream input) throws IOException {
      try (input) {
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) >= 0; ) {
          int retained = Math.min(count, maximum - content.size());
          content.write(buffer, 0, retained);
          truncated |= retained != count;
        }
      }
    }

    byte[] bytes() {
      return content.toByteArray();
    }
  }
}
