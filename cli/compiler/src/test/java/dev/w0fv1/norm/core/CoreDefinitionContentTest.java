package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CoreDefinitionContentTest {
  @Test
  void normalizesDeclarationIndicesAndRetainsExactLinkage() {
    var first = new CoreCompilationInput.Source(signature(7, 12));
    var second = new CoreCompilationInput.Source(signature(91, 3));

    assertEquals(first.content().id(), second.content().id());
    assertEquals(List.of(7, 12), first.linkage().declarations());
    assertEquals(List.of(91, 3), second.linkage().declarations());
    assertEquals(signature(7, 12), first.definition());
    assertEquals(signature(91, 3), second.definition());
  }

  @Test
  void relocatesBindingsWithoutRebuildingContent() {
    var first = new CoreCompilationInput.Source(signature(7, 12));
    var relocated = first.relocate(index -> index + 30);

    assertSame(first.content(), relocated.content());
    assertEquals(signature(37, 42), relocated.definition());
    assertEquals(java.util.Set.of(37, 42), relocated.references());
    assertEquals(List.of(7, 12), first.linkage().declarations());
  }

  @Test
  void preservesRepeatedTargetRelationships() {
    var shared = new CoreCompilationInput.Source(signature(7, 7));
    var distinct = new CoreCompilationInput.Source(signature(7, 12));

    assertNotEquals(shared.content().id(), distinct.content().id());
    assertEquals(List.of(7), shared.linkage().declarations());
  }

  @Test
  void rejectsIncompleteAndNegativeBindings() {
    var source = new CoreCompilationInput.Source(signature(7, 12));

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreCompilationInput.Source(source.content(), new CoreLinkage(List.of(7))));
    assertThrows(IllegalArgumentException.class, () -> source.relocate(index -> -1));
  }

  @Test
  void keepsResolvedReferencesInContentIdentity() {
    var first =
        new CoreCompilationInput.Source(signature(new DefinitionReference.RecursiveMember(0)));
    var second =
        new CoreCompilationInput.Source(signature(new DefinitionReference.RecursiveMember(1)));
    var slot = new CoreCompilationInput.Source(signature(new PendingDefinitionReference(0)));

    assertNotEquals(first.content().id(), second.content().id());
    assertNotEquals(first.content().id(), slot.content().id());
    assertEquals(List.of(), first.linkage().declarations());
  }

  @Test
  void retainsTemplateSlotsWhenBindingsMerge() {
    var source = new CoreCompilationInput.Source(signature(7, 12));
    var merged = source.relocate(index -> 9);

    assertSame(source.content(), merged.content());
    assertEquals(List.of(9, 9), merged.linkage().declarations());
    assertEquals(signature(9, 9), merged.definition());
  }

  @Test
  void rejectsNonCanonicalContentSlots() {
    assertThrows(IllegalArgumentException.class, () -> new CoreDefinitionContent(signature(0, 2)));
    assertThrows(IllegalArgumentException.class, () -> new CoreDefinitionContent(signature(1, 0)));
  }

  @Test
  void includesExactExternalTargetsInContentIdentity() {
    var firstHash = new byte[ContentHash.BYTE_LENGTH];
    var secondHash = new byte[ContentHash.BYTE_LENGTH];
    secondHash[0] = 1;
    var firstTarget = new DefinitionId(new DefinitionGroupId(ContentHash.of(firstHash)), 0);
    var secondTarget = new DefinitionId(new DefinitionGroupId(ContentHash.of(secondHash)), 0);
    var first =
        new CoreCompilationInput.Source(signature(new DefinitionReference.External(firstTarget)));
    var second =
        new CoreCompilationInput.Source(signature(new DefinitionReference.External(secondTarget)));

    assertNotEquals(first.content().id(), second.content().id());
    assertEquals(signature(new DefinitionReference.External(firstTarget)), first.definition());
    assertEquals(List.of(), first.linkage().declarations());
  }

  private static CoreDefinition signature(int receiver, int parameter) {
    return new CoreDefinition.MethodSignature(
        "method",
        type(new PendingDefinitionReference(receiver)),
        List.of(),
        List.of(type(new PendingDefinitionReference(parameter))),
        CoreType.VOID);
  }

  private static CoreDefinition signature(CoreDefinitionLink receiver) {
    return new CoreDefinition.MethodSignature(
        "method", type(receiver), List.of(), List.of(), CoreType.VOID);
  }

  private static CoreType type(CoreDefinitionLink link) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(link),
        List.of(),
        CoreValueCategory.IDENTITY,
        CoreNullability.NON_NULL);
  }
}
