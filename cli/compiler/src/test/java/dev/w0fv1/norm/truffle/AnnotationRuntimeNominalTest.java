package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.testing.NormTestKit;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnnotationRuntimeNominalTest {
  @Test
  void preservesInterfacePriorityAndConcreteCandidateOrder() {
    var artifact =
        NormTestKit.compile(
                "interface Item {} class First implements Item {} "
                    + "class Second implements Item {} Void main() {}")
            .output()
            .orElseThrow()
            .artifact();
    var runtime = new AnnotationRuntime(artifact);
    var definitions = artifact.program().definitions();
    var item = nominal(definitions, "Item");
    var first = nominal(definitions, "First");
    var second = nominal(definitions, "Second");
    var expected = type(item);

    assertEquals(
        expected, runtime.jarReferenceType(expected, List.of(reference(first), reference(item))));
    assertEquals(
        type(first),
        runtime.jarReferenceType(expected, List.of(reference(first), reference(second))));
    assertEquals(
        type(second),
        runtime.jarReferenceType(expected, List.of(reference(second), reference(first))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtime.jarReferenceType(
                expected,
                List.of(
                    new JarBindingClassReference.Nominal(
                        nominalType(first).module(), "absent", "Absent"))));

    var classType =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Class")),
            List.of(expected),
            CoreValueCategory.POLYMORPHIC,
            CoreNullability.NON_NULL);
    assertEquals(
        type(first), runtime.jarClassValue(classType, List.of(reference(first))).reflectedType());
    assertThrows(
        IllegalArgumentException.class,
        () -> runtime.jarClassValue(classType, List.of(reference(first), reference(second))));
  }

  private static CoreDefinitionRecord nominal(List<CoreDefinitionRecord> definitions, String name) {
    return definitions.stream()
        .filter(
            record -> {
              var definition = record.definition();
              return (definition instanceof CoreDefinition.Aggregate aggregate
                      && aggregate.nominalType().name().equals(name))
                  || (definition instanceof CoreDefinition.Interface declaration
                      && declaration.nominalType().name().equals(name));
            })
        .findFirst()
        .orElseThrow();
  }

  private static CoreNominalTypeKey nominalType(CoreDefinitionRecord record) {
    return switch (record.definition()) {
      case CoreDefinition.Aggregate aggregate -> aggregate.nominalType();
      case CoreDefinition.Interface declaration -> declaration.nominalType();
      default -> throw new IllegalArgumentException("declaration is not nominal");
    };
  }

  private static JarBindingClassReference.Nominal reference(CoreDefinitionRecord record) {
    var nominal = nominalType(record);
    return new JarBindingClassReference.Nominal(
        nominal.module(), nominal.packageName(), nominal.name());
  }

  private static CoreType type(CoreDefinitionRecord record) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(record.id())),
        List.of(),
        switch (record.definition()) {
          case CoreDefinition.Aggregate aggregate -> aggregate.valueCategory();
          case CoreDefinition.Interface declaration -> CoreValueCategory.POLYMORPHIC;
          default -> throw new IllegalArgumentException("declaration is not nominal");
        },
        CoreNullability.NON_NULL);
  }
}
