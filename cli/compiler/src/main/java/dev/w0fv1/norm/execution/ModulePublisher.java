package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.value.ModuleDeclaration;

@FunctionalInterface
public interface ModulePublisher {
  void publish(ModuleDeclaration declaration);
}
