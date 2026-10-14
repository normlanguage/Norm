package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoreLiveSchemaTest {
  @TempDir Path directory;

  @Test
  void bindsDeclarationsAcrossContentAndSourcePositionChanges() {
    var first =
        compile("Integer first() { 1 } Integer second() { 1 } Void main() { printLine(first()) }");
    var second =
        compile(
            "Integer extra() { 7 } Integer first() { 200 } Integer second() { 1 } Void main() { printLine(first()) }");
    var before = CoreLiveSchema.create(first);
    var after = CoreLiveSchema.create(second);
    assertDoesNotThrow(() -> before.requireCompatible(after));
    assertEquals(
        before.key(first.namespace().occurrence("", "first").orElseThrow()),
        after.key(second.namespace().occurrence("", "first").orElseThrow()));
    assertNotEquals(
        before.key(first.namespace().occurrence("", "first").orElseThrow()),
        before.key(first.namespace().occurrence("", "second").orElseThrow()));
  }

  @Test
  void acceptsAdditiveLocalsAndChangedControlShape() {
    String text =
        "Void main() { var count = 0 for (count < 3) { printLine(count) count = count + 1 } }";
    var schema = CoreLiveSchema.create(compile(text));
    assertDoesNotThrow(
        () ->
            schema.requireCompatible(
                CoreLiveSchema.create(
                    compile(text.replace("var count = 0", "var other = 1 var count = 0")))));
    assertDoesNotThrow(
        () ->
            schema.requireCompatible(
                CoreLiveSchema.create(
                    compile(text.replace("printLine(count)", "if (true) { printLine(count) }")))));
    assertDoesNotThrow(
        () ->
            schema.requireCompatible(
                CoreLiveSchema.create(
                    compile(
                        text.replace("count < 3", "count < 5")
                            .replace("count + 1", "count + 2")))));
  }

  @Test
  void retainsLocalIdentityWhenSlotsAreReordered() {
    var first = compile("Void main() { var first = 1 var second = 2 printLine(first) }");
    var second = compile("Void main() { var second = 2 var first = 1 printLine(first) }");
    var before = CoreLiveSchema.create(first);
    var after = CoreLiveSchema.create(second);
    assertDoesNotThrow(() -> before.requireCompatible(after));
    assertEquals(
        before.layout(first.entryPoint()).local(0), after.layout(second.entryPoint()).local(1));
    assertEquals(
        before.layout(first.entryPoint()).local(1), after.layout(second.entryPoint()).local(0));
  }

  @Test
  void recordsParameterAndCapturedLocalNames() {
    var artifact =
        compile(
            "Function<Integer()> deferred(Integer input) { var retained = input; () { retained } } Void main() { printLine(deferred(8)()) }");
    var origins =
        artifact.authoring().occurrences().stream().map(CoreDefinitionOccurrence::origin).toList();
    assertTrue(origins.stream().anyMatch(origin -> origin.localNames().containsValue("input")));
    assertTrue(origins.stream().anyMatch(origin -> origin.localNames().containsValue("retained")));
    assertTrue(
        artifact.authoring().occurrences().stream()
            .filter(occurrence -> occurrence.role() == CoreDefinitionRole.LAMBDA)
            .anyMatch(occurrence -> occurrence.origin().localNames().containsValue("retained")));
  }

  @Test
  void rejectsReorderedSameTypedClosureCaptures() {
    String text =
        "Function<Integer()> deferred() { var first = 1 var second = 2; () { first + second } } Void main() { printLine(deferred()()) }";
    var schema = CoreLiveSchema.create(compile(text));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            schema.requireCompatible(
                CoreLiveSchema.create(compile(text.replace("first + second", "second + first")))));
  }

  @Test
  void acceptsDefaultPolicyForNewInvocations() {
    String required =
        "Integer answer(Integer input) { input } Void main() { printLine(answer(7)) }";
    var schema = CoreLiveSchema.create(compile(required));
    var defaulted =
        CoreLiveSchema.create(compile(required.replace("Integer input)", "Integer input = 1)")));
    assertDoesNotThrow(() -> schema.requireCompatible(defaulted));
    var updatedDefault =
        CoreLiveSchema.create(compile(required.replace("Integer input)", "Integer input = 2)")));
    assertDoesNotThrow(() -> defaulted.requireCompatible(updatedDefault));
  }

  private CoreArtifact compile(String text) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(directory.resolve("main.norm"), text));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }
}
