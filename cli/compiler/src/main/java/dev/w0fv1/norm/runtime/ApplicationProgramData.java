package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import java.util.List;
import java.util.Objects;

public record ApplicationProgramData(
    CoreArtifact artifact,
    CoreExecutionPlan execution,
    List<LinkedJarBinding> bindings,
    String packageName) {
  public ApplicationProgramData {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(execution, "execution");
    bindings = List.copyOf(bindings);
    Objects.requireNonNull(packageName, "packageName");
  }
}
