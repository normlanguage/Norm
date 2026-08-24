package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreCompilation(
    CoreProgram program,
    CoreNamespace namespace,
    CoreAuthoringMap authoring,
    CoreBuildReport buildReport,
    CoreDependencyIndex dependencies,
    CoreCompilationDelta delta) {
  public CoreCompilation {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(authoring, "authoring");
    Objects.requireNonNull(buildReport, "buildReport");
    Objects.requireNonNull(dependencies, "dependencies");
    Objects.requireNonNull(delta, "delta");
    validate(program, namespace, authoring);
  }

  public CoreCompilation withEntryPoint(DefinitionOccurrenceId entryPoint) {
    return new CoreCompilation(
        program, namespace, authoring.withEntryPoint(entryPoint), buildReport, dependencies, delta);
  }

  public CoreCompilation withDelta(CoreCompilationDelta delta) {
    return new CoreCompilation(program, namespace, authoring, buildReport, dependencies, delta);
  }

  public DefinitionOccurrenceId entryPoint() {
    return authoring.entryPoint();
  }

  public DefinitionId entryDefinition() {
    return entryPoint().representative();
  }

  public String displayName(DefinitionOccurrenceId occurrence) {
    Objects.requireNonNull(occurrence, "occurrence");
    return authoring.origin(occurrence).definitionName();
  }

  private static void validate(
      CoreProgram program, CoreNamespace namespace, CoreAuthoringMap authoring) {
    for (CoreDefinitionOccurrence occurrence : authoring.occurrences()) {
      for (DefinitionId definition : occurrence.representedDefinitions()) {
        if (program.definition(definition).isEmpty()) {
          throw new IllegalArgumentException("occurrence definition is absent: " + definition);
        }
      }
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      var references = CoreTree.referenceSites(definition);
      if (!references.keySet().equals(occurrence.references().keySet())) {
        throw new IllegalArgumentException("authoring references do not match the core definition");
      }
      references.forEach(
          (nodeIndex, reference) -> {
            DefinitionId resolved = program.resolve(occurrence.id().representative(), reference);
            CoreDefinitionOccurrence target =
                authoring.occurrence(occurrence.references().get(nodeIndex)).orElseThrow();
            if (!target.representedDefinitions().contains(resolved)) {
              throw new IllegalArgumentException(
                  "authoring reference target does not represent the core target");
            }
          });
    }
    if (!(program.definition(authoring.entryPoint().representative()).orElseThrow()
        instanceof CoreDefinition.Callable)) {
      throw new IllegalArgumentException("entry occurrence must be callable");
    }
    for (CoreBinding binding : namespace.bindings()) {
      if (authoring.occurrence(binding.occurrence()).isEmpty()) {
        throw new IllegalArgumentException("namespace binding occurrence is absent");
      }
      validateBinding(program, namespace, binding);
    }
  }

  private static void validateBinding(
      CoreProgram program, CoreNamespace namespace, CoreBinding binding) {
    DefinitionId id = binding.definition();
    CoreDefinition definition = program.definition(id).orElseThrow();
    CoreBindingKind expectedKind =
        switch (definition) {
          case CoreDefinition.Callable callable ->
              callable.hasReceiver() ? CoreBindingKind.METHOD : CoreBindingKind.FUNCTION;
          case CoreDefinition.Class ignored -> CoreBindingKind.CLASS;
          case CoreDefinition.Enum ignored -> CoreBindingKind.ENUM;
        };
    if (binding.kind() != expectedKind) throw bindingMismatch(binding);
    switch (definition) {
      case CoreDefinition.Callable callable -> {
        CoreBindingShape.Callable shape = (CoreBindingShape.Callable) binding.shape();
        if (shape.typeParameterCount() != callable.reifiedTypeLocals().size()
            || shape.parameters().size() != callable.parameterTypes().size()
            || !sameType(program, id, shape.returnType(), callable.returnType())) {
          throw bindingMismatch(binding);
        }
        for (int parameter = 0; parameter < shape.parameters().size(); parameter++) {
          if (!sameType(
              program,
              id,
              shape.parameters().get(parameter).type(),
              callable.parameterTypes().get(parameter))) {
            throw bindingMismatch(binding);
          }
        }
        if (callable.hasReceiver()) {
          CoreType.Declared receiver =
              (CoreType.Declared)
                  CoreTypes.absolute(callable.receiverType().orElseThrow(), id, program);
          CoreTypeConstructor.User constructor = (CoreTypeConstructor.User) receiver.constructor();
          DefinitionId owner =
              ((DefinitionReference.External) constructor.definition()).definition();
          boolean ownerBindingPresent =
              namespace.bindings().stream()
                  .anyMatch(
                      candidate ->
                          candidate.kind() == CoreBindingKind.CLASS
                              && candidate.packageName().equals(binding.packageName())
                              && candidate.name().equals(binding.ownerName().orElseThrow())
                              && candidate.definition().equals(owner));
          if (!ownerBindingPresent) throw bindingMismatch(binding);
        }
      }
      case CoreDefinition.Class declaration -> {
        CoreBindingShape.Class shape = (CoreBindingShape.Class) binding.shape();
        if (shape.typeParameterCount() != declaration.typeParameterCount()
            || shape.fields().size() != declaration.fields().size()) {
          throw bindingMismatch(binding);
        }
        for (int field = 0; field < shape.fields().size(); field++) {
          if (!sameType(
              program,
              id,
              shape.fields().get(field).type(),
              declaration.fields().get(field).type())) {
            throw bindingMismatch(binding);
          }
        }
      }
      case CoreDefinition.Enum declaration -> {
        CoreBindingShape.Enum shape = (CoreBindingShape.Enum) binding.shape();
        if (!shape.members().equals(declaration.members())) throw bindingMismatch(binding);
      }
    }
  }

  private static boolean sameType(
      CoreProgram program, DefinitionId owner, CoreType left, CoreType right) {
    return CoreTypes.absolute(left, owner, program)
        .equals(CoreTypes.absolute(right, owner, program));
  }

  private static IllegalArgumentException bindingMismatch(CoreBinding binding) {
    return new IllegalArgumentException(
        "namespace binding does not match its core definition ABI: " + binding.name());
  }
}
