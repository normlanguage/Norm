package dev.w0fv1.norm.logging;

import java.io.PrintWriter;
import java.util.function.Supplier;
import org.slf4j.event.Level;

public final class DependencyLogs {
  public interface Destination {
    boolean enabled(Level level);

    void write(Level level, String source, String message, Throwable cause);
  }

  private static final ScopedValue<Destination> CURRENT = ScopedValue.newInstance();

  private DependencyLogs() {}

  static boolean enabled(Level level) {
    return CURRENT.isBound() ? CURRENT.get().enabled(level) : level.toInt() >= Level.WARN.toInt();
  }

  static void write(Level level, String source, String message, Throwable cause) {
    if (CURRENT.isBound()) CURRENT.get().write(level, source, message, cause);
    else print(new PrintWriter(System.err, true), level, source, message, cause);
  }

  public static <T> T within(Destination destination, Supplier<T> action) {
    return ScopedValue.where(CURRENT, destination).call(action::get);
  }

  public static <T> T command(PrintWriter errors, boolean debug, Supplier<T> action) {
    return within(
        new Destination() {
          public boolean enabled(Level level) {
            return level.toInt() >= (debug ? Level.DEBUG : Level.WARN).toInt();
          }

          public void write(Level level, String source, String message, Throwable cause) {
            print(errors, level, source, message, cause);
          }
        },
        action);
  }

  private static void print(
      PrintWriter target, Level level, String source, String message, Throwable cause) {
    synchronized (target) {
      target.printf("[%s] %s %s%n", level, source, message);
      if (cause != null) cause.printStackTrace(target);
      target.flush();
    }
  }
}
