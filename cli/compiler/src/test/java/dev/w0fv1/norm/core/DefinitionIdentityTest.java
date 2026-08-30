package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DefinitionIdentityTest {
  private static final String GROUP_HEX = "0123456789abcdef".repeat(4);

  @Test
  void groupIdentityRoundTripsThroughText() {
    DefinitionGroupId group = new DefinitionGroupId(ContentHash.parse(GROUP_HEX));

    assertEquals(group, DefinitionGroupId.parse(GROUP_HEX.toUpperCase()));
    assertEquals(GROUP_HEX, group.toString());
    assertEquals(ContentHash.parse(GROUP_HEX), group.hash());
  }

  @Test
  void definitionIdentityRoundTripsThroughText() {
    DefinitionGroupId group = DefinitionGroupId.parse(GROUP_HEX);
    DefinitionId definition = new DefinitionId(group, 42);

    assertEquals(definition, DefinitionId.parse(GROUP_HEX + ":42"));
    assertEquals(GROUP_HEX + ":42", definition.toString());
    assertEquals(group, definition.group());
    assertEquals(42, definition.memberIndex());
  }

  @Test
  void rejectsInvalidIdentityValuesAndText() {
    assertThrows(NullPointerException.class, () -> new DefinitionGroupId(null));
    assertThrows(NullPointerException.class, () -> DefinitionGroupId.parse(null));
    assertThrows(IllegalArgumentException.class, () -> DefinitionGroupId.parse("x"));
    assertThrows(NullPointerException.class, () -> new DefinitionId(null, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefinitionId(DefinitionGroupId.parse(GROUP_HEX), -1));
    assertThrows(NullPointerException.class, () -> DefinitionId.parse(null));
    assertThrows(IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX));
    assertThrows(IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX + ":"));
    assertThrows(IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX + ":-1"));
    assertThrows(IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX + ":+1"));
    assertThrows(IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX + ":01"));
    assertThrows(
        IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX + ":2147483648"));
    assertThrows(IllegalArgumentException.class, () -> DefinitionId.parse(GROUP_HEX + ":1:2"));
  }

  @Test
  void comparesDefinitionsByGroupThenMemberIndex() {
    DefinitionGroupId firstGroup = DefinitionGroupId.parse("0".repeat(64));
    DefinitionGroupId secondGroup = DefinitionGroupId.parse("f".repeat(64));
    DefinitionId firstMember = new DefinitionId(firstGroup, 0);
    DefinitionId secondMember = new DefinitionId(firstGroup, 1);

    assertTrue(firstGroup.compareTo(secondGroup) < 0);
    assertTrue(firstMember.compareTo(secondMember) < 0);
    assertTrue(secondMember.compareTo(new DefinitionId(secondGroup, 0)) < 0);
  }
}
