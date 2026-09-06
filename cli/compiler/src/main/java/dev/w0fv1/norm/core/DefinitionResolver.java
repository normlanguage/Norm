package dev.w0fv1.norm.core;

@FunctionalInterface
public interface DefinitionResolver {
  DefinitionId resolve(DefinitionId owner, DefinitionReference reference);
}
