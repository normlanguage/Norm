package dev.w0fv1.norm.execution;

import java.util.List;
import java.util.Objects;

public record RuntimeDeclarationShape(
    RuntimeDeclarationKind kind,
    String identity,
    String signature,
    String bodyContentId,
    List<String> members,
    List<String> annotations) {
  public RuntimeDeclarationShape {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(signature, "signature");
    Objects.requireNonNull(bodyContentId, "bodyContentId");
    members = List.copyOf(members);
    annotations = List.copyOf(annotations);
  }
}
