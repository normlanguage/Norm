package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreWitness(CoreDefinitionLink requirement, CoreWitnessTarget implementation) {
  public CoreWitness {
    Objects.requireNonNull(requirement, "requirement");
    Objects.requireNonNull(implementation, "implementation");
  }
}
