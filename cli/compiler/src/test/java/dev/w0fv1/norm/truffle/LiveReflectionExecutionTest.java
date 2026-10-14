package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
final class LiveReflectionExecutionTest {
  @Test
  void retainedFunctionKeepsMetadataOwnershipWhileItsImplementationUpdates(@TempDir Path directory)
      throws Exception {
    String source =
        """
        Integer target(Integer value) { value + 1 }
        Void main() {
          Function<Integer(Integer)> operation = target
          var count = 0
          for (count < 3) {
            printLine(operation.name())
            printLine(operation.parameters()[0].name())
            printLine(operation(19))
            count = count + 1
          }
        }
        """;
    assertUpdate(
        directory,
        source,
        3,
        List.of("target", "value", "20", "target", "value", "21", "target", "value", "21"));
  }

  @Test
  void retainedFunctionKeepsDefaultArgumentsAndJsonReflection(@TempDir Path directory)
      throws Exception {
    String source =
        """
        import std.json.invokeJson
        Integer target(Integer value = 19) { value + 1 }
        Void main() {
          Function<Integer(Integer)> operation = target
          var count = 0
          for (count < 3) {
            printLine(invokeJson(operation: operation, arguments: "{}"))
            count = count + 1
          }
        }
        """;
    assertUpdate(directory, source, 1, List.of("20", "21", "21"));
  }

  @Test
  void retainedFieldAndClassFollowLogicalMembersAfterFieldInsertion(@TempDir Path directory)
      throws Exception {
    String source =
        """
        class Counter { Integer value = 7 }
        Void main() {
          var counter = Counter()
          var retained = Counter.value.field
          var reflected = classOf(counter)
          var count = 0
          for (count < 3) {
            printLine(retained.read(receiver: counter))
            for field : reflected.fields() { printLine(field.name()) }
            count = count + 1
          }
        }
        """;
    assertUpdate(
        directory,
        source,
        source.replace("class Counter {", "class Counter { Integer extra = 11;"),
        2,
        List.of("7", "value", "7", "extra", "value", "7", "extra", "value"));
  }

  @Test
  void retainedClassMetadataRemainsAvailableAfterItsDeclarationIsRetired(@TempDir Path directory)
      throws Exception {
    String source =
        """
        class Retired { Integer value = 7 }
        Void main() {
          Any retained = Retired()
          var reflected = classOf(retained)
          var count = 0
          for (count < 3) {
            for field : reflected.fields() { printLine(field.name()) }
            count = count + 1
          }
        }
        """;
    String candidate =
        source
            .replace("class Retired { Integer value = 7 }", "")
            .replace("Any retained = Retired()", "Any retained = \"unused\"");
    assertUpdate(directory, source, candidate, 1, List.of("value", "value", "value"));
  }

  private static void assertUpdate(
      Path directory, String source, int updateAfter, List<String> expected) throws Exception {
    assertUpdate(
        directory, source, source.replace("value + 1", "value + 2"), updateAfter, expected);
  }

  private static void assertUpdate(
      Path directory, String source, String candidateSource, int updateAfter, List<String> expected)
      throws Exception {
    try (var compiler =
        ProjectEnvironment.bootstrap(new TruffleExecutionBackend()).compilerSession()) {
      var file = directory.resolve("reflection.norm");
      var originalResult = compiler.compile(SourceFile.of(file, source));
      assertTrue(originalResult.isSuccess(), originalResult.diagnostics().toString());
      var original = originalResult.output().orElseThrow().artifact();
      var candidateResult = compiler.compile(SourceFile.of(file, candidateSource));
      assertTrue(candidateResult.isSuccess(), candidateResult.diagnostics().toString());
      var candidate = candidateResult.output().orElseThrow().artifact();
      assertEquals(original.metadata().annotations(), candidate.metadata().annotations());
      var live = new TruffleExecutionBackend().prepareLive(original);
      var output = new StringBuilder();
      var writer =
          new Writer() {
            private int lines;

            @Override
            public void write(char[] characters, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char character = characters[index];
                output.append(character);
                if (character == '\n' && ++lines == updateAfter) live.update(candidate);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      live.execute(ExecutionContext.of(new PrintWriter(writer)));
      assertEquals(expected, output.toString().lines().toList());
    }
  }
}
