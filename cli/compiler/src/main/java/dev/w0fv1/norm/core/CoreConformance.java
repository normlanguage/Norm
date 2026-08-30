package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreConformance(CoreType interfaceType, List<CoreWitness> witnesses) {
  public CoreConformance {
    Objects.requireNonNull(interfaceType, "interfaceType");
    witnesses = List.copyOf(witnesses);
  }
}
