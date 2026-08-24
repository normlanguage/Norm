package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.DefinitionHasher;
import dev.w0fv1.norm.core.DefinitionId;
import org.junit.jupiter.api.Test;

final class RuntimeValuesTest {
  @Test
  void comparesEnumValuesByDefinitionAndOrdinal() {
    DefinitionId first = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    DefinitionId second = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {2}), 0);

    assertTrue(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, 0, "State", "Ready"),
            new RuntimeValues.EnumValue(first, 0, "Renamed", "Changed")));
    assertFalse(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, 0, "State", "Ready"),
            new RuntimeValues.EnumValue(second, 0, "State", "Ready")));
    assertFalse(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, 0, "State", "Ready"),
            new RuntimeValues.EnumValue(first, 1, "State", "Ready")));
  }
}
