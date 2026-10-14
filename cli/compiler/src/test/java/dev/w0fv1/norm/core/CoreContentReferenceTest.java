package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreContentReferenceTest {
  @Test
  void sortedParentsKeepSlotAndFixedRecursiveReferencesDistinct() {
    var nominal =
        new CoreNominalTypeKey(
            new ModuleCoordinate("test", 1), "", "Child", CoreVisibility.PUBLIC, Optional.empty());
    var slot = new PendingDefinitionReference(7);
    var recursive = new DefinitionReference.RecursiveMember(0);
    var first =
        new CoreCompilationInput.Source(
            new CoreDefinition.Interface(
                nominal,
                List.of(),
                List.of(parent(slot, CoreType.INTEGER), parent(recursive, CoreType.STRING)),
                List.of()));
    var second =
        new CoreCompilationInput.Source(
            new CoreDefinition.Interface(
                nominal,
                List.of(),
                List.of(parent(slot, CoreType.STRING), parent(recursive, CoreType.INTEGER)),
                List.of()));

    assertEquals(first.linkage(), second.linkage());
    assertNotEquals(first.definition(), second.definition());
    assertNotEquals(first.content().id(), second.content().id());
    assertNotEquals(first.content(), second.content());
    assertNotEquals(first, second);
  }

  private static CoreType parent(CoreDefinitionLink link, CoreType argument) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(link),
        List.of(argument),
        CoreValueCategory.POLYMORPHIC,
        CoreNullability.NON_NULL);
  }
}
