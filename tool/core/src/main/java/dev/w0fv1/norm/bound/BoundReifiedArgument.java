package dev.w0fv1.norm.bound;

import java.util.Objects;

public record BoundReifiedArgument(String typeParameterIdentity, BoundLocalId source) {
  public BoundReifiedArgument {
    Objects.requireNonNull(typeParameterIdentity, "typeParameterIdentity");
    Objects.requireNonNull(source, "source");
  }
}
