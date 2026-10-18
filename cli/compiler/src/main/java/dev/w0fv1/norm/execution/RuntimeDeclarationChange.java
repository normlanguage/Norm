package dev.w0fv1.norm.execution;

import java.util.Objects;
import java.util.Optional;

public record RuntimeDeclarationChange(
    RuntimeChangeKind kind,
    RuntimeDeclarationKind declarationKind,
    String identity,
    Optional<RuntimeDeclarationShape> before,
    Optional<RuntimeDeclarationShape> after,
    String source,
    int startOffset,
    int endOffset) {
  public RuntimeDeclarationChange {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(declarationKind, "declarationKind");
    Objects.requireNonNull(identity, "identity");
    before = Objects.requireNonNull(before, "before");
    after = Objects.requireNonNull(after, "after");
    Objects.requireNonNull(source, "source");
    if (startOffset < 0 || endOffset < startOffset) {
      throw new IllegalArgumentException("invalid declaration source range");
    }
    if (kind == RuntimeChangeKind.ADDED && before.isPresent()) {
      throw new IllegalArgumentException("added declaration cannot have a previous shape");
    }
    if (kind == RuntimeChangeKind.REMOVED && after.isPresent()) {
      throw new IllegalArgumentException("removed declaration cannot have a candidate shape");
    }
    if (kind == RuntimeChangeKind.MODIFIED && (before.isEmpty() || after.isEmpty())) {
      throw new IllegalArgumentException("modified declaration requires both shapes");
    }
  }
}
