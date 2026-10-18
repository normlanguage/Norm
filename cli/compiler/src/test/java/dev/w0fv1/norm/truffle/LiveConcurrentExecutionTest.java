package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
final class LiveConcurrentExecutionTest {
  @Test
  void publishesFromAnotherThreadAndUpdatesTheActiveCondition(@TempDir Path directory)
      throws Exception {
    String source =
        """
        Void main() {
          printLine("init")
          var count = 0
          for (count < 100) {
            printLine("old:" + count.toString())
            count = count + 1
          }
          printLine("finished:" + count.toString())
        }
        """;
    try (var compiler = new CompilerSession()) {
      var file = directory.resolve("concurrent.norm");
      var initial = compile(compiler, file, source);
      var candidate =
          compile(
              compiler,
              file,
              source.replace("count < 100", "count < 3").replace("old:", "hellonorm:"));
      var live = new TruffleExecutionBackend().prepareLive(initial);
      var output = new PausingWriter();
      try (var executor = Executors.newSingleThreadExecutor()) {
        var execution =
            executor.submit(() -> live.execute(ExecutionContext.of(new PrintWriter(output))));
        try {
          assertTrue(output.paused.await(10, TimeUnit.SECONDS));
          live.update(candidate);
        } finally {
          output.resume.countDown();
        }
        execution.get(10, TimeUnit.SECONDS);
      }
      assertEquals(
          List.of("init", "old:0", "hellonorm:1", "hellonorm:2", "finished:3"), output.lines());
    }
  }

  private static CoreArtifact compile(CompilerSession compiler, Path file, String text) {
    var result = compiler.compile(SourceFile.of(file, text));
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    return result.output().orElseThrow().artifact();
  }

  private static final class PausingWriter extends Writer {
    private final StringBuilder output = new StringBuilder();
    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private int lines;

    @Override
    public void write(char[] characters, int offset, int length) throws IOException {
      for (int index = offset; index < offset + length; index++) {
        char character = characters[index];
        output.append(character);
        if (character == '\n' && ++lines == 2) {
          paused.countDown();
          try {
            if (!resume.await(10, TimeUnit.SECONDS)) throw new IOException("live update timed out");
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
          }
        }
      }
    }

    List<String> lines() {
      return output.toString().lines().toList();
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
