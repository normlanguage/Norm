package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class CoreEnumMigrationProtocolTest {
  @Test
  void appliesTheSameTypeMigrationProtocolToAnEnum() {
    var compilation =
        NormTestKit.compile(
            """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      @StateMigration(convert: convertMessage.function)
      enum Message { Current(String text) }
      Void convertMessage(MigrationState state) {}
      Void main() { var message = Message.Current(text: "Norm") }
      """);
    assertTrue(compilation.isSuccess(), compilation.diagnostics().toString());
    var artifact = compilation.output().orElseThrow().artifact();
    var identity = CoreRuntimeIdentity.create(artifact);
    var declaration =
        identity.id(artifact.namespace().occurrence("", "Message").orElseThrow().representative());
    assertTrue(CoreStateMigrationProtocol.appliedTypes(artifact, identity).contains(declaration));
  }

  @Test
  void requiresTheConverterForRemovedEnumVariantsAndChangedPayloads() {
    String source =
        """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      @StateMigration(convert: convertMessage.function)
      enum Message { Previous(Integer count) }
      Void convertMessage(MigrationState state) {}
      Void main() { var message = Message.Previous(count: 7) }
      """;
    var first = NormTestKit.compile(source);
    var second =
        NormTestKit.compile(
            source
                .replace("Previous(Integer count)", "Current(String text)")
                .replace("Message.Previous(count: 7)", "Message.Current(text: \"seven\")"));
    assertTrue(first.isSuccess(), first.diagnostics().toString());
    assertTrue(second.isSuccess(), second.diagnostics().toString());
    var before = first.output().orElseThrow().artifact();
    var after = second.output().orElseThrow().artifact();
    assertThrows(
        IllegalArgumentException.class,
        () -> CoreRuntimeSchema.create(before).requireCompatible(CoreRuntimeSchema.create(after)));
    assertDoesNotThrow(
        () -> CoreLiveSchema.create(before).requireCompatible(CoreLiveSchema.create(after)));
  }
}
