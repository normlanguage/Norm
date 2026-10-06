package dev.w0fv1.norm.project;

import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.util.Objects;

record ProvidedModule(ModuleDescriptor descriptor, ResolvedJarBinding binding) {
  ProvidedModule {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(binding, "binding");
    if (descriptor.binding().isEmpty())
      throw new IllegalArgumentException("provided Java module must declare its binding");
  }
}
