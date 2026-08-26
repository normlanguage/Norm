package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.value.ModuleDescriptor;

@FunctionalInterface
public interface ModulePublisher {
  void publish(ModuleDescriptor descriptor);
}
