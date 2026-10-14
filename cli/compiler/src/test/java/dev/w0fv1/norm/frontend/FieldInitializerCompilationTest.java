package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FieldInitializerCompilationTest {
  @TempDir Path directory;

  @Test
  void givesFieldDefaultsAnIndependentCallableUsedByConstructorsAndPersistence() throws Exception {
    var artifact =
        compile(
            "Integer seed() { 8 } class Box { Integer first = 8 Integer second = seed() + 1 Box() {} } Void main() { printLine(Box().second) }");
    DefinitionId owner = artifact.namespace().definition("", "Box").orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) artifact.program().definition(owner).orElseThrow();
    for (var field : aggregate.fields()) {
      var initializer =
          artifact
              .program()
              .resolve(owner, (DefinitionReference) field.initializer().orElseThrow().target());
      var callable =
          (CoreDefinition.Callable) artifact.program().definition(initializer).orElseThrow();
      assertTrue(callable.receiverType().isEmpty());
      assertEquals(1, callable.parameters().size());
      assertEquals("$receiver", callable.parameters().getFirst().name());
      assertEquals(field.type(), callable.returnType());
      assertTrue(callable.captureTypes().isEmpty());
      assertTrue(
          artifact.authoring().occurrences(initializer).stream()
              .anyMatch(
                  occurrence ->
                      occurrence
                          .origin()
                          .declarationIdentity()
                          .orElseThrow()
                          .endsWith("/initializer")));
    }
    var reloaded =
        PortableObjectCodec.decode(PortableObjectCodec.encode(artifact), CoreArtifact.class);
    var output = new StringWriter();
    new NormRuntime().run(reloaded, new PrintWriter(output));
    assertEquals("9" + System.lineSeparator(), output.toString());
    assertEquals(
        aggregate.fields(),
        ((CoreDefinition.Aggregate) reloaded.program().definition(owner).orElseThrow()).fields());
  }

  @Test
  void doesNotInventInitializerFromConstructorParameterAssignments() {
    var artifact =
        compile(
            "class Box { Integer value Box(Integer input) { this.value = input } } Void main() { printLine(Box(input: 8).value) }");
    var owner = artifact.namespace().definition("", "Box").orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) artifact.program().definition(owner).orElseThrow();
    assertTrue(aggregate.fields().getFirst().initializer().isEmpty());
    var output = new StringWriter();
    new NormRuntime().run(artifact, new PrintWriter(output));
    assertEquals("8" + System.lineSeparator(), output.toString());
  }

  @Test
  void preservesOwnerGenericParametersInFieldInitializerContract() {
    var artifact =
        compile(
            "class Box<T> { T? value = null } Void main() { var box = Box<Integer>(); printLine(box.value) }");
    var owner = artifact.namespace().definition("", "Box").orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) artifact.program().definition(owner).orElseThrow();
    var target =
        artifact
            .program()
            .resolve(
                owner,
                (DefinitionReference) aggregate.fields().getFirst().initializer().orElseThrow().target());
    var callable = (CoreDefinition.Callable) artifact.program().definition(target).orElseThrow();
    assertEquals(1, callable.typeParameters().size());
    assertEquals(1, callable.reifiedTypeLocals().size());
    assertEquals(1, callable.parameters().size());
  }

  @Test
  void identifiesEqualContentInitializersByTheirExactAuthoredOccurrence() throws Exception {
    String source = "class Box { Integer first = 7 Integer second = 7 Box() {} } Void main() { printLine(Box().second) }";
    try (var compiler = new CompilerSession()) {
      var file = directory.resolve("identity.norm");
      String firstIdentity = null;
      String secondIdentity = null;
      for (String text : java.util.List.of(source, source.replace("first = 7", "first = 8"))) {
        var result = compiler.compile(SourceFile.of(file, text));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
        var artifact = PortableObjectCodec.decode(PortableObjectCodec.encode(result.output().orElseThrow().artifact()), CoreArtifact.class);
        var owner = artifact.namespace().occurrence("", "Box").orElseThrow();
        var aggregate = (CoreDefinition.Aggregate) artifact.program().definition(owner.representative()).orElseThrow();
        var first = artifact.authoring().target(owner, aggregate.fields().getFirst().initializer().orElseThrow().nodeIndex());
        var second = artifact.authoring().target(owner, aggregate.fields().getLast().initializer().orElseThrow().nodeIndex());
        assertNotEquals(first, second);
        if (firstIdentity == null) {
          firstIdentity = artifact.authoring().origin(first).declarationIdentity().orElseThrow();
          secondIdentity = artifact.authoring().origin(second).declarationIdentity().orElseThrow();
        } else {
          assertEquals(firstIdentity, artifact.authoring().origin(first).declarationIdentity().orElseThrow());
          assertEquals(secondIdentity, artifact.authoring().origin(second).declarationIdentity().orElseThrow());
          assertNotEquals(first.representative(), second.representative());
        }
      }
    }
  }

  @Test
  void keepsInitializerSourceNodeOutsideTheSemanticContentIdentity() {
    var artifact = compile("class Box { Integer value = 7 } Void main() {} ");
    var owner = artifact.namespace().definition("", "Box").orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) artifact.program().definition(owner).orElseThrow();
    var movedFields = aggregate.fields().stream().map(field -> new dev.w0fv1.norm.core.CoreField(
        field.visibility(), field.name(), field.ordinal(), field.type(), field.interceptors(),
        field.initializer().map(initializer -> new dev.w0fv1.norm.core.CoreFieldInitializer(initializer.nodeIndex() + 100, initializer.target())))).toList();
    var moved = new CoreDefinition.Aggregate(aggregate.nominalType(), aggregate.kind(), aggregate.valueCategory(),
        aggregate.typeParameters(), aggregate.parentType(), aggregate.fieldCount(), movedFields,
        aggregate.dispatch(), aggregate.constructors(), aggregate.conformances());
    assertEquals(new dev.w0fv1.norm.core.CoreDefinitionContent(aggregate).id(), new dev.w0fv1.norm.core.CoreDefinitionContent(moved).id());
    assertNotEquals(aggregate, moved);
  }

  private CoreArtifact compile(String source) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(directory.resolve("fields.norm"), source));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }
}
