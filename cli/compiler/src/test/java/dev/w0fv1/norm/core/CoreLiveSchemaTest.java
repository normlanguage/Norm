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
  void permitsNewConstructorFramesAfterExplicitFieldTypeMigration() {
    String before =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: convertState.function)
        class State { Integer total = 1 }
        Void convertState(MigrationState state) {
          state.write(field: State.total.field, value: 1)
        }
        Void main() { var state = State() }
        """;
    String after =
        before
            .replace("Integer total = 1", "String total = \"one\"")
            .replace("value: 1", "value: state.read<Integer>(\"total\").toString()");
    var first = dev.w0fv1.norm.testing.NormTestKit.compile(before);
    var second = dev.w0fv1.norm.testing.NormTestKit.compile(after);
    assertTrue(first.isSuccess(), first.diagnostics().toString());
    assertTrue(second.isSuccess(), second.diagnostics().toString());
    assertDoesNotThrow(
        () ->
            CoreLiveSchema.create(first.output().orElseThrow().artifact())
                .requireCompatible(
                    CoreLiveSchema.create(second.output().orElseThrow().artifact())));
  }

  @Test
  void permitsExplicitValueFieldMigrationWithRuntimeCollectionReindexing() {
    var first = compile("value Key { Integer count = 1 } Void main() { var key = Key() }");
    var second = compile("value Key { String count = \"one\" } Void main() { var key = Key() }");
    var before = CoreRuntimeSchema.create(first);
    var after = CoreRuntimeSchema.create(second);
    var id =
        before
            .identity()
            .id(first.namespace().occurrence("", "Key").orElseThrow().representative());
    assertThrows(IllegalArgumentException.class, () -> before.requireCompatible(after));
    assertDoesNotThrow(() -> before.requireCompatible(after, java.util.Set.of(id)));
  }

  @Test
  void migratesStoredFieldsAndVersionsCallableContractsIndependently() {
    var first =
        compile(
            "class State { Integer count = 1 } Integer value() { 1 } Void main() { var state = State() }");
    var second =
        compile(
            "class State { String count = \"one\" } Integer value() { 1 } Void main() { var state = State() }");
    var before = CoreRuntimeSchema.create(first);
    var after = CoreRuntimeSchema.create(second);
    var id =
        before
            .identity()
            .id(first.namespace().occurrence("", "State").orElseThrow().representative());
    assertThrows(IllegalArgumentException.class, () -> before.requireCompatible(after));
    assertDoesNotThrow(() -> before.requireCompatible(after, java.util.Set.of(id)));
    var changedFunction =
        CoreRuntimeSchema.create(
            compile(
                "class State { String count = \"one\" } String value() { \"one\" } Void main() { var state = State() }"));
    assertDoesNotThrow(() -> before.requireCompatible(changedFunction, java.util.Set.of(id)));
  }

  @Test
  void requiresAnExplicitTypeScopedMigrationForDestructiveStateChanges() {
    var first = compile("class State { Integer count = 1 } Void main() { var state = State() }");
    var second =
        compile("class State { String text = \"one\" } Void main() { var state = State() }");
    var before = CoreRuntimeSchema.create(first);
    var after = CoreRuntimeSchema.create(second);
    var id =
        before
            .identity()
            .id(first.namespace().occurrence("", "State").orElseThrow().representative());
    assertThrows(IllegalArgumentException.class, () -> before.requireCompatible(after));
    assertDoesNotThrow(() -> before.requireCompatible(after, java.util.Set.of(id)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            before.requireCompatible(
                after, java.util.Set.of(DefinitionId.parse("0".repeat(64) + ":0"))));
  }

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
  void versionsReorderedSameTypedClosureCaptures() {
    String text =
        "Function<Integer()> deferred() { var first = 1 var second = 2; () { first + second } } Void main() { printLine(deferred()()) }";
    var schema = CoreLiveSchema.create(compile(text));
    assertDoesNotThrow(
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
