package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreMethodDispatch(
    CoreDefinitionLink slot, CoreDefinitionLink implementation, CoreType receiverType) {
  public CoreMethodDispatch {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(implementation, "implementation");
    Objects.requireNonNull(receiverType, "receiverType");
  }
}
