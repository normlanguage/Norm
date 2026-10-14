package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CoreRuntimeSchemaTest {
  @Test
  void nominalAndGenericTypesKeepIdentityAcrossMethodImplementationChanges() {
    String source =
        """
        class Box<T> {
          T value
          Box(T value) { this.value = value }
          T get() { value }
          Integer step() { 1 }
        }
        Void main() { var box = Box<Integer>(19); printLine(box.get()); printLine(box.step()) }
        """;
    try (var compiler = new CompilerSession()) {
      var initial = compile(compiler, source);
      var candidate = compile(compiler, source.replace("step() { 1 }", "step() { 2 }"));
      var first = CoreRuntimeSchema.create(initial);
      var second = CoreRuntimeSchema.create(candidate);
      assertDoesNotThrow(() -> first.requireCompatible(second));
      var initialBox = initial.namespace().definition("", "Box").orElseThrow();
      var candidateBox = candidate.namespace().definition("", "Box").orElseThrow();
      assertNotEquals(initialBox, candidateBox);
      assertEquals(first.identity().id(initialBox), second.identity().id(candidateBox));
    }
  }

  @Test
  void interfaceWitnessBodiesMayChangeWhileTheirTypeContractStaysStable() {
    String source =
        """
        interface Counter { Integer next() }
        class CounterImpl implements Counter {
          Integer value = 0
          Integer next() { value = value + 1; value }
        }
        Void main() { Counter counter = CounterImpl(); printLine(counter.next()) }
        """;
    try (var compiler = new CompilerSession()) {
      var initial = compile(compiler, source);
      var candidate = compile(compiler, source.replace("value + 1", "value + 2"));
      assertDoesNotThrow(
          () ->
              CoreRuntimeSchema.create(initial)
                  .requireCompatible(CoreRuntimeSchema.create(candidate)));
    }
  }

  @Test
  void changingAnExistingFieldTypeRequiresAnExplicitMigration() {
    String source = "class Box { Integer value = 1 } Void main() { printLine(Box().value) }";
    try (var compiler = new CompilerSession()) {
      var initial = compile(compiler, source);
      var candidate =
          compile(compiler, source.replace("Integer value = 1", "String value = \"one\""));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              CoreRuntimeSchema.create(initial)
                  .requireCompatible(CoreRuntimeSchema.create(candidate)));
    }
  }

  private static CoreArtifact compile(CompilerSession compiler, String source) {
    var result = compiler.compile(SourceFile.of(Path.of("runtime-types.norm"), source));
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    return result.output().orElseThrow().artifact();
  }
}
