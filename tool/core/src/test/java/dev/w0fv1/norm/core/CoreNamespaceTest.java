package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreNamespaceTest {
  @Test
  void ordersIdenticalPrivateBindingsByOccurrence() {
    DefinitionId definition = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    CoreBinding first = binding(new DefinitionOccurrenceId(definition, 0));
    CoreBinding second = binding(new DefinitionOccurrenceId(definition, 1));

    CoreNamespace forward = CoreNamespace.create(List.of(first, second));
    CoreNamespace reverse = CoreNamespace.create(List.of(second, first));

    assertEquals(forward.id(), reverse.id());
    assertEquals(forward.bindings(), reverse.bindings());
    assertEquals(List.of(first, second), forward.bindings());
  }

  private static CoreBinding binding(DefinitionOccurrenceId occurrence) {
    return new CoreBinding(
        "sample",
        Optional.empty(),
        "Hidden",
        CoreVisibility.PRIVATE,
        new CoreBindingShape.Enum(List.of()),
        occurrence,
        false);
  }
}
