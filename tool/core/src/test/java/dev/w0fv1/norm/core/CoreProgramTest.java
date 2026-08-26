package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreProgramTest {
  @Test
  void rejectsDuplicateDefinitionGroupIds() {
    CoreDefinitionGroup group =
        CoreDefinitionGroup.create(
            List.of(
                new CoreDefinition.Enum(
                    new CoreNominalTypeKey(
                        new ModuleCoordinate("sample", 1),
                        "sample",
                        "State",
                        CoreVisibility.PUBLIC,
                        Optional.empty()),
                    List.of(),
                    List.of(new CoreEnumVariant("Ready", List.of())))));

    CoreDefinitionGroup duplicate = CoreDefinitionGroup.create(group.definitions());

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group, duplicate)));
  }

  @Test
  void enumIdentityUsesStableVariantKeysInsteadOfDeclarationOrder() {
    CoreNominalTypeKey nominal =
        new CoreNominalTypeKey(
            new ModuleCoordinate("sample", 1),
            "sample",
            "Result",
            CoreVisibility.PUBLIC,
            Optional.empty());
    CoreEnumVariant ok =
        new CoreEnumVariant(
            "Ok", List.of(new CoreField(0, new CoreType.Parameter(0, CoreNullability.NON_NULL))));
    CoreEnumVariant error =
        new CoreEnumVariant(
            "Error",
            List.of(new CoreField(0, new CoreType.Parameter(1, CoreNullability.NON_NULL))));

    DefinitionId ordered =
        CoreDefinitionGroup.create(
                List.of(new CoreDefinition.Enum(nominal, typeParameters(2), List.of(ok, error))))
            .definitionId(0);
    DefinitionId reordered =
        CoreDefinitionGroup.create(
                List.of(new CoreDefinition.Enum(nominal, typeParameters(2), List.of(error, ok))))
            .definitionId(0);
    DefinitionId changedPayload =
        CoreDefinitionGroup.create(
                List.of(
                    new CoreDefinition.Enum(
                        nominal,
                        typeParameters(2),
                        List.of(
                            new CoreEnumVariant("Ok", List.of(new CoreField(0, CoreType.INTEGER))),
                            error))))
            .definitionId(0);

    assertEquals(ordered, reordered);
    assertNotEquals(ordered, changedPayload);
  }

  @Test
  void rejectsMissingTypeDependencies() {
    DefinitionId missing = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {9}), 0);
    CoreType fieldType =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(missing)),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    CoreDefinitionGroup group =
        CoreDefinitionGroup.create(
            List.of(
                new CoreDefinition.Aggregate(
                    new CoreNominalTypeKey(
                        new ModuleCoordinate("sample", 1),
                        "sample",
                        "Box",
                        CoreVisibility.PUBLIC,
                        Optional.empty()),
                    CoreValueCategory.IDENTITY,
                    List.of(),
                    List.of(new CoreField(0, fieldType)),
                    List.of())));
    CoreDefinitionGroup enumGroup =
        CoreDefinitionGroup.create(
            List.of(
                new CoreDefinition.Enum(
                    new CoreNominalTypeKey(
                        new ModuleCoordinate("sample", 1),
                        "sample",
                        "Choice",
                        CoreVisibility.PUBLIC,
                        Optional.empty()),
                    List.of(),
                    List.of(new CoreEnumVariant("Value", List.of(new CoreField(0, fieldType)))))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group)));
    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(enumGroup)));
  }

  private static List<CoreTypeParameter> typeParameters(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> new CoreTypeParameter(index, Optional.empty()))
        .toList();
  }
}
