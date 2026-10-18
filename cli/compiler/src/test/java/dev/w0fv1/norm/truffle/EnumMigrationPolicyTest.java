package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class EnumMigrationPolicyTest {
  @Test
  void declaresAnEnumConverterThroughTheUnifiedMigrationState() {
    var compilation =
        NormTestKit.compile(
            """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      @StateMigration(convert: convertMessage.function)
      enum Message { Current(String text) }
      Void convertMessage(MigrationState state) {
        Class<?> previous = state.type()
        String? variant = state.variant()
        if variant == "Previous" {
          Integer payload = state.read<Integer>(index: 0)
          state.replace(value: Message.Current(text: payload.toString()))
        }
      }
      Void main() { printLine(Message.Current(text: "Norm")) }
      """);
    assertTrue(compilation.isSuccess(), compilation.diagnostics().toString());
  }

  @Test
  void rejectsAnEnumPayloadIndexWithAnIncompatibleType() {
    var compilation =
        NormTestKit.compile(
            """
      import std.runtime.MigrationState
      Void convertMessage(MigrationState state) { state.read<Integer>(index: "first") }
      Void main() {}
      """);
    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic.message().contains("Integer")
                        && diagnostic.message().contains("String")),
        compilation.diagnostics().toString());
  }
}
