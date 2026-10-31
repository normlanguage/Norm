package dev.w0fv1.norm.logging;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

final class DependencyLogProviderTest {
  @Test
  void routesARealDependencyLoggerToItsCurrentCommand() {
    var logger = LoggerFactory.getLogger("org.example.transport");
    var outer = new StringWriter();
    var inner = new StringWriter();
    dev.w0fv1.norm.cli.component.CommandJournal.withDependencyLogs(
        new PrintWriter(outer),
        false,
        () -> {
          logger.debug("hidden");
          logger.warn("retry {}", 2);
          dev.w0fv1.norm.cli.component.CommandJournal.withDependencyLogs(
              new PrintWriter(inner),
              true,
              () -> {
                logger.debug("connected");
                return 0;
              });
          logger.debug("hidden again");
          return 0;
        });
    assertTrue(outer.toString().contains("org.example.transport"));
    assertTrue(outer.toString().contains("retry 2"));
    assertFalse(outer.toString().contains("hidden"));
    assertFalse(outer.toString().contains("connected"));
    assertTrue(inner.toString().contains("connected"));
  }
}
