package dev.w0fv1.norm.core;

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
                    List.of("Ready"))));

    CoreDefinitionGroup duplicate = CoreDefinitionGroup.create(group.definitions());

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group, duplicate)));
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
                new CoreDefinition.Class(
                    new CoreNominalTypeKey(
                        new ModuleCoordinate("sample", 1),
                        "sample",
                        "Box",
                        CoreVisibility.PUBLIC,
                        Optional.empty()),
                    0,
                    List.of(new CoreField(0, fieldType)))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group)));
  }
}
