package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreNamespaceTest {
  @Test
  void exposesRoundTrippableNamespaceIdentity() {
    CoreNamespaceId id = CoreNamespace.create(List.of()).id();

    assertEquals(id, CoreNamespaceId.parse(id.toString()));
  }

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

  @Test
  void enumNamespaceIdentityIsOrderIndependentAndIncludesFieldAbi() {
    DefinitionId definition = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    DefinitionOccurrenceId occurrence = new DefinitionOccurrenceId(definition, 0);
    CoreBindingShape.Variant empty = new CoreBindingShape.Variant("Empty", List.of());
    CoreBindingShape.Variant value =
        new CoreBindingShape.Variant(
            "Value", List.of(new CoreBindingShape.Parameter("value", CoreType.INTEGER)));
    List<CoreTypeParameter> parameters = List.of(new CoreTypeParameter(0, Optional.empty()));
    CoreBinding forward =
        binding(occurrence, new CoreBindingShape.Enum(parameters, List.of(empty, value)));
    CoreBinding reverse =
        binding(occurrence, new CoreBindingShape.Enum(parameters, List.of(value, empty)));
    CoreBinding renamedField =
        binding(
            occurrence,
            new CoreBindingShape.Enum(
                parameters,
                List.of(
                    empty,
                    new CoreBindingShape.Variant(
                        "Value",
                        List.of(new CoreBindingShape.Parameter("item", CoreType.INTEGER))))));

    assertEquals(
        CoreNamespace.create(List.of(forward)).id(), CoreNamespace.create(List.of(reverse)).id());
    assertNotEquals(
        CoreNamespace.create(List.of(forward)).id(),
        CoreNamespace.create(List.of(renamedField)).id());
  }

  @Test
  void classNamespaceIdentityIncludesConformances() {
    DefinitionId definition = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    DefinitionId protocol = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {2}), 0);
    DefinitionOccurrenceId occurrence = new DefinitionOccurrenceId(definition, 0);
    CoreType interfaceType =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(protocol)),
            List.of(),
            CoreValueCategory.POLYMORPHIC,
            CoreNullability.NON_NULL);
    CoreBinding plain =
        binding(occurrence, new CoreBindingShape.Class(List.of(), List.of(), List.of()));
    CoreBinding conforming =
        binding(
            occurrence, new CoreBindingShape.Class(List.of(), List.of(), List.of(interfaceType)));

    assertNotEquals(
        CoreNamespace.create(List.of(plain)).id(), CoreNamespace.create(List.of(conforming)).id());
  }

  @Test
  void interfaceNamespaceIdentityDoesNotDependOnParentOrder() {
    DefinitionId definition = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    DefinitionOccurrenceId occurrence = new DefinitionOccurrenceId(definition, 0);
    CoreType first = interfaceType(2);
    CoreType second = interfaceType(3);
    CoreBinding forward =
        binding(occurrence, new CoreBindingShape.Interface(List.of(), List.of(first, second)));
    CoreBinding reverse =
        binding(occurrence, new CoreBindingShape.Interface(List.of(), List.of(second, first)));

    assertEquals(
        CoreNamespace.create(List.of(forward)).id(), CoreNamespace.create(List.of(reverse)).id());
  }

  private static CoreType interfaceType(int seed) {
    DefinitionId definition =
        new DefinitionId(DefinitionHasher.hashGroup(new byte[] {(byte) seed}), 0);
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
        List.of(),
        CoreValueCategory.POLYMORPHIC,
        CoreNullability.NON_NULL);
  }

  private static CoreBinding binding(DefinitionOccurrenceId occurrence) {
    return binding(occurrence, new CoreBindingShape.Enum(List.of(), List.of()));
  }

  private static CoreBinding binding(DefinitionOccurrenceId occurrence, CoreBindingShape shape) {
    return new CoreBinding(
        "sample", Optional.empty(), "Hidden", CoreVisibility.PRIVATE, shape, occurrence, false);
  }
}
