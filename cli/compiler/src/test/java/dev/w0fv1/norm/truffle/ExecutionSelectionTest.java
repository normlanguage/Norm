package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ExecutionSelectionTest {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "value Item { String name } Void main() { Item? item = null }",
        "import std.core.Exception class Item extends Exception { Item(String message) { super(message: message) } } Void main() { Item? item = null }",
        "class Item {} Void main() { printLine(Item.class.constructors().size()) }"
      })
  void preservesRuntimeAndReflectiveConstructors(String source) {
    var compiled = NormTestKit.compile(source);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.output().orElseThrow().artifact();
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    var record =
        artifact.program().definitions().stream()
            .filter(
                candidate ->
                    candidate.definition() instanceof CoreDefinition.Aggregate aggregate
                        && aggregate.nominalType().name().equals("Item"))
            .findFirst()
            .orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) record.definition();
    assertFalse(aggregate.constructors().isEmpty());
    for (var constructor : aggregate.constructors()) {
      var id = artifact.program().resolve(record.id(), (DefinitionReference) constructor);
      assertTrue(executable.targets().containsKey(id));
    }
    executable.execute(
        ExecutionContext.of(
            new java.io.PrintWriter(new java.io.StringWriter()), JdkSystemPlatform.standard()));
  }

  @Test
  void typeReferencesDoNotPrepareReferenceClassConstructors() {
    var compiled =
        NormTestKit.compile(
            """
        class Dormant {
          Dormant() { printLine("unexpected construction") }
        }
        Void main() { Dormant? item = null printLine("ready") }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.output().orElseThrow().artifact();
    var record =
        artifact.program().definitions().stream()
            .filter(
                candidate ->
                    candidate.definition() instanceof CoreDefinition.Aggregate aggregate
                        && aggregate.nominalType().name().equals("Dormant"))
            .findFirst()
            .orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) record.definition();
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    for (var constructor : aggregate.constructors()) {
      var id = artifact.program().resolve(record.id(), (DefinitionReference) constructor);
      assertFalse(executable.targets().containsKey(id));
      assertTrue(executable.program().callable(id).isPresent());
    }
    var output = new java.io.StringWriter();
    executable.execute(
        ExecutionContext.of(new java.io.PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("ready" + System.lineSeparator(), output.toString());
  }

  @Test
  void onlyPreparesRequestedMethodSlots() {
    var compiled =
        NormTestKit.compile(
            """
        class Item {
          String used() { return "ready" }
          String dormant() { return "dormant" }
        }
        Void main() { Item item = Item() printLine(item.used()) }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.output().orElseThrow().artifact();
    var dormant =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("dormant"))
            .findFirst()
            .orElseThrow()
            .definition();
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    assertFalse(executable.targets().containsKey(dormant));
    assertTrue(executable.program().callable(dormant).isPresent());
    var owner =
        CoreTypes.absolute(
            executable.program().callable(dormant).orElseThrow().receiverType().orElseThrow(),
            dormant,
            executable.program());
    assertFalse(executable.values().allocate(owner).objectInfo.dispatch().containsKey(dormant));
    var output = new java.io.StringWriter();
    executable.execute(
        ExecutionContext.of(new java.io.PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("ready" + System.lineSeparator(), output.toString());
  }

  @Test
  void doesNotPrepareUnreferencedFunctionBodies() {
    var compiled =
        NormTestKit.compile(
            """
        String dormant() { return "dormant" }
        String used() { return "ready" }
        Void main() { printLine(used()) }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.output().orElseThrow().artifact();
    var dormant =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("dormant"))
            .findFirst()
            .orElseThrow()
            .definition();
    var used =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("used"))
            .findFirst()
            .orElseThrow()
            .definition();
    var backend = new TruffleExecutionBackend();
    var executable = backend.compile(null, artifact);
    assertFalse(executable.targets().containsKey(dormant));
    assertTrue(executable.targets().containsKey(used));
    assertTrue(executable.program().callable(dormant).isPresent());
    var output = new java.io.StringWriter();
    executable.execute(
        ExecutionContext.of(new java.io.PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("ready" + System.lineSeparator(), output.toString());
    var external = CoreExecutionPlan.forArtifact(artifact, java.util.Set.of(dormant));
    var withExternalEntry = backend.compile(null, artifact, external);
    assertTrue(withExternalEntry.targets().containsKey(dormant));
    assertEquals(2, backend.cachedArtifacts());
    assertSame(executable, backend.compile(null, artifact));
    assertThrows(UnsupportedOperationException.class, external.callables()::clear);
  }
}
