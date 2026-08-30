package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;

public record BoundMethodDispatch(
    BoundCallableId slot, BoundCallableId implementation, SemanticType receiverType) {
  public BoundMethodDispatch {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(implementation, "implementation");
    Objects.requireNonNull(receiverType, "receiverType");
  }
}
