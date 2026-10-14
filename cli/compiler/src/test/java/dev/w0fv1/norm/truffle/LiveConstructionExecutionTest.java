package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class LiveConstructionExecutionTest {
  @Test
  void constructsWithOmittedReceiverDefaultsAcrossRunningVersions() {
    String source =
        "class Box { Integer first = 8 Integer second = first + 1 } Void main() { var count = 0; for (count < 3) { printLine(Box().second); count = count + 1 } }";
    assertEquals(
        java.util.List.of("9", "10", "10"),
        run(source, source.replace("first + 1", "first + 2"), "9"));
  }

  @Test
  void finishesAnActiveConstructorWithItsOwnParameterContractAndUsesTheNewOneForFreshCalls() {
    String source =
        """
        Integer seed() { printLine("seed"); 7 }
        Integer tailSeed() { printLine("tail"); 8 }
        Integer addedSeed() { printLine("added"); 19 }
        class Box { Integer value = seed() Integer tail = tailSeed() }
        Void fresh() { var box = Box(); printLine("fresh:" + box.value.toString() + ":" + box.tail.toString()) }
        Void main() { var first = Box(); printLine("old-finished"); fresh() }
        """;
    String candidate =
        source
            .replace("class Box {", "class Box { Integer added = addedSeed();")
            .replace(
                "\"fresh:\" + box.value.toString()",
                "\"fresh:\" + box.added.toString() + \":\" + box.value.toString()");
    var lines = run(source, candidate, "seed");
    assertEquals(java.util.List.of("seed", "tail"), lines.subList(0, 2), lines.toString());
    assertTrue(lines.contains("old-finished"), lines.toString());
    assertEquals("fresh:19:7:8", lines.getLast(), lines.toString());
    assertTrue(lines.indexOf("added") > lines.indexOf("tail"), lines.toString());
  }

  private java.util.List<String> run(String source, String candidate, String trigger) {
    try (var compiler = new CompilerSession()) {
      var original = compiler.compile(SourceFile.of(Path.of("construction.norm"), source));
      assertTrue(original.isSuccess(), original.diagnostics().toString());
      var changed = compiler.compile(SourceFile.of(Path.of("construction.norm"), candidate));
      assertTrue(changed.isSuccess(), changed.diagnostics().toString());
      try (var execution =
          new TruffleExecutionBackend().prepareLive(original.output().orElseThrow().artifact())) {
        var lines = new ArrayList<String>();
        var updated = new java.util.concurrent.atomic.AtomicBoolean();
        var pending = new StringBuilder();
        var writer =
            new Writer() {
              @Override
              public void write(char[] buffer, int offset, int length) {
                pending.append(buffer, offset, length);
                int boundary;
                while ((boundary = pending.indexOf("\n")) >= 0) {
                  String line = pending.substring(0, boundary).replace("\r", "");
                  pending.delete(0, boundary + 1);
                  lines.add(line);
                  if (line.equals(trigger) && updated.compareAndSet(false, true))
                    execution.update(changed.output().orElseThrow().artifact());
                }
              }

              @Override
              public void flush() {}

              @Override
              public void close() {}
            };
        execution.execute(ExecutionContext.of(new PrintWriter(writer)));
        assertTrue(execution.status().pendingLocations().isEmpty(), execution.status().toString());
        return java.util.List.copyOf(lines);
      }
    }
  }
}
