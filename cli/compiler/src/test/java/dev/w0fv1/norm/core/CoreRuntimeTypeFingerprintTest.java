package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CoreRuntimeTypeFingerprintTest {
  @Test
  void nestedEnumLayoutsArePartOfStoredTypeContracts() {
    String source =
        """
        enum Phase { Ready(Integer number) }
        class Nested { Phase? phase = null }
        class State { List<Nested> entries = [] }
        Void main() {}
        """;
    try (var compiler = new CompilerSession()) {
      var before = compile(compiler, source, "State");
      var after =
          compile(compiler, source.replace("Ready(Integer number)", "Ready(String text)"), "State");
      assertNotEquals(before, after);
    }
  }

  @Test
  void interfaceMembersArePartOfStoredTypeContracts() {
    String source =
        """
        interface Tag { Integer value() }
        class State { Tag? tag = null }
        Void main() {}
        """;
    try (var compiler = new CompilerSession()) {
      assertNotEquals(
          compile(compiler, source, "State"),
          compile(compiler, source.replace("Integer value()", "String value()"), "State"));
    }
  }

  @Test
  void recursiveContractsRemainStableWhenOnlyBodiesChange() {
    String source =
        """
        class Node { Node? next = null Integer number() { 1 } }
        Void main() {}
        """;
    try (var compiler = new CompilerSession()) {
      assertEquals(
          compile(compiler, source, "Node"),
          compile(compiler, source.replace("number() { 1 }", "number() { 2 }"), "Node"));
    }
  }

  private static CoreRuntimeSchema.TypeFingerprint compile(
      CompilerSession compiler, String source, String name) {
    var result = compiler.compile(SourceFile.of(Path.of("fingerprint.norm"), source));
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var artifact = result.output().orElseThrow().artifact();
    var schema = CoreRuntimeSchema.create(artifact);
    var definition = schema.identity().id(artifact.namespace().definition("", name).orElseThrow());
    return schema.typeFingerprint(
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL));
  }
}
