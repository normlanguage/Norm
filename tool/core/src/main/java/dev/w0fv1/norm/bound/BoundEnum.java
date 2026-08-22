package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundEnum(
    BoundEnumId id, String name, SemanticType type, List<BoundEnumMember> members, SourceSpan span)
    implements BoundNode {
  public BoundEnum {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    members = List.copyOf(members);
    Objects.requireNonNull(span, "span");
  }
}
