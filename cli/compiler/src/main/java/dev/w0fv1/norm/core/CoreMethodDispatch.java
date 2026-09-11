package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreMethodDispatch(
    CoreDefinitionLink slot, CoreDefinitionLink target, CoreType receiverType) {
  public CoreMethodDispatch {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(receiverType, "receiverType");
  }
}
