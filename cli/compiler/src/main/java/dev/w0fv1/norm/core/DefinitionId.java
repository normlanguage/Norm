package dev.w0fv1.norm.core;

import java.util.Objects;

public record DefinitionId(DefinitionGroupId group, int memberIndex)
    implements Comparable<DefinitionId> {
  public DefinitionId {
    Objects.requireNonNull(group, "group");
    if (memberIndex < 0) {
      throw new IllegalArgumentException("definition member index must not be negative");
    }
  }

  public static DefinitionId parse(String text) {
    Objects.requireNonNull(text, "text");
    int separator = text.indexOf(':');
    if (separator != ContentHash.BYTE_LENGTH * 2 || text.indexOf(':', separator + 1) >= 0) {
      throw new IllegalArgumentException(
          "definition id must contain a group hash and member index");
    }

    String memberText = text.substring(separator + 1);
    if (!isCanonicalMemberIndex(memberText)) {
      throw new IllegalArgumentException(
          "definition member index must be a canonical non-negative integer");
    }

    try {
      return new DefinitionId(
          DefinitionGroupId.parse(text.substring(0, separator)), Integer.parseInt(memberText));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "definition member index exceeds the supported range", exception);
    }
  }

  private static boolean isCanonicalMemberIndex(String text) {
    if (text.isEmpty()) {
      return false;
    }
    if (text.equals("0")) {
      return true;
    }
    if (text.charAt(0) < '1' || text.charAt(0) > '9') {
      return false;
    }
    for (int index = 1; index < text.length(); index++) {
      if (text.charAt(index) < '0' || text.charAt(index) > '9') {
        return false;
      }
    }
    return true;
  }

  @Override
  public int compareTo(DefinitionId other) {
    Objects.requireNonNull(other, "other");
    int groupComparison = group.compareTo(other.group);
    return groupComparison != 0 ? groupComparison : Integer.compare(memberIndex, other.memberIndex);
  }

  @Override
  public String toString() {
    return group + ":" + memberIndex;
  }
}
