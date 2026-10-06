package dev.w0fv1.norm.packages;

import java.io.IOException;
import java.util.Objects;

public final class PackageResolutionException extends IOException {
  @java.io.Serial private static final long serialVersionUID = 1L;
  private final NormPackageResolver.ResolutionInputs inputs;

  PackageResolutionException(IOException cause, NormPackageResolver.ResolutionInputs inputs) {
    super(cause.getMessage(), cause);
    this.inputs = Objects.requireNonNull(inputs, "inputs");
  }

  public NormPackageResolver.ResolutionInputs inputs() {
    return inputs;
  }
}
