package dev.w0fv1.norm.cli.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.logging.RollingLogFile;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.slf4j.event.Level;

public final class CommandJournal {
  private static final ScopedValue<RollingLogFile> CURRENT = ScopedValue.newInstance();

  private CommandJournal() {}

  public static <T> T withDependencyLogs(PrintWriter errors, boolean debug, Supplier<T> action) {
    return dev.w0fv1.norm.logging.DependencyLogs.within(
        new dev.w0fv1.norm.logging.DependencyLogs.Destination() {
          public boolean enabled(Level level) {
            return level.toInt() >= (debug ? Level.DEBUG : Level.WARN).toInt();
          }

          public void write(Level level, String source, String message, Throwable cause) {
            record(
                "dependency",
                () -> {
                  var event = new JsonObject();
                  event.addProperty("level", level.name());
                  event.addProperty("source", source);
                  event.addProperty("message", message);
                  if (cause != null) {
                    var trace = new java.io.StringWriter();
                    cause.printStackTrace(new PrintWriter(trace));
                    event.addProperty("exception", trace.toString());
                  }
                  return event;
                });
            dev.w0fv1.norm.logging.DependencyLogs.print(errors, level, source, message, cause);
          }
        },
        action);
  }

  public static int capture(String command, PrintWriter err, IntSupplier execute) {
    Path root =
        Path.of(System.getProperty("user.home"), ".norm", "cache", "command-reports", command);
    try (var file = new RollingLogFile(root, 10L * 1024 * 1024, 7, 200L * 1024 * 1024)) {
      err.println("Command diagnostics: " + file.directory());
      return ScopedValue.where(CURRENT, file)
          .call(
              () -> {
                record("command", () -> new com.google.gson.JsonPrimitive(command));
                int code = execute.getAsInt();
                record("exit", () -> new com.google.gson.JsonPrimitive(code));
                return code;
              });
    } catch (IOException | java.io.UncheckedIOException failure) {
      err.println("Cannot persist command diagnostics: " + failure.getMessage());
      return dev.w0fv1.norm.cli.value.ExitCode.INTERNAL_ERROR;
    }
  }

  public static void record(String kind, Supplier<JsonElement> data) {
    if (!CURRENT.isBound()) return;
    var event = new JsonObject();
    event.addProperty("schemaVersion", 1);
    event.addProperty("time", Instant.now().toString());
    event.addProperty("kind", kind);
    event.add("data", data.get());
    var bytes = (event.toString() + "\n").getBytes(StandardCharsets.UTF_8);
    CURRENT.get().write(bytes, 0, bytes.length);
  }
}
