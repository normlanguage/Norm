package dev.w0fv1.norm.core;

import java.util.Objects;

public final class CoreArtifact {
  private final CoreProgram program;
  private final CoreNamespace namespace;
  private final CoreAuthoringMap authoring;

  public CoreArtifact(CoreProgram program, CoreNamespace namespace, CoreAuthoringMap authoring) {
    this.program = Objects.requireNonNull(program, "program");
    this.namespace = Objects.requireNonNull(namespace, "namespace");
    this.authoring = Objects.requireNonNull(authoring, "authoring");
    validate(program, namespace, authoring);
  }

  public CoreProgram program() {
    return program;
  }

  public CoreNamespace namespace() {
    return namespace;
  }

  public CoreAuthoringMap authoring() {
    return authoring;
  }

  public CoreArtifact withEntryPoint(DefinitionOccurrenceId entryPoint) {
    return new CoreArtifact(program, namespace, authoring.withEntryPoint(entryPoint));
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
          case CoreDefinition.Interface ignored -> CoreBindingKind.INTERFACE;
          case CoreDefinition.InterfaceMethod ignored -> CoreBindingKind.INTERFACE_METHOD;
          case CoreDefinition.BuiltinConformance ignored ->
              throw new IllegalArgumentException(
                  "builtin conformances cannot be namespace bindings");
        };
    if (binding.kind() != expectedKind) throw bindingMismatch(binding);
    switch (definition) {
      case CoreDefinition.Callable callable -> {
        CoreBindingShape.Callable shape = (CoreBindingShape.Callable) binding.shape();
        if (!sameTypeParameters(program, id, shape.typeParameters(), callable.typeParameters())
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
        if (!sameTypeParameters(program, id, shape.typeParameters(), declaration.typeParameters())
            || shape.fields().size() != declaration.fields().size()
            || shape.conformances().size() != declaration.conformances().size()) {
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
        for (CoreType conformance : shape.conformances()) {
          boolean present =
              declaration.conformances().stream()
                  .anyMatch(value -> sameType(program, id, conformance, value.interfaceType()));
          if (!present) throw bindingMismatch(binding);
        }
      }
      case CoreDefinition.Enum declaration -> {
        CoreBindingShape.Enum shape = (CoreBindingShape.Enum) binding.shape();
        if (!sameTypeParameters(program, id, shape.typeParameters(), declaration.typeParameters())
            || shape.variants().size() != declaration.variants().size()) {
          throw bindingMismatch(binding);
        }
        for (int variantIndex = 0; variantIndex < shape.variants().size(); variantIndex++) {
          CoreBindingShape.Variant variant = shape.variants().get(variantIndex);
          CoreEnumVariant definitionVariant = declaration.variants().get(variantIndex);
          if (!variant.name().equals(definitionVariant.key())
              || variant.fields().size() != definitionVariant.fields().size()) {
            throw bindingMismatch(binding);
          }
          for (int fieldIndex = 0; fieldIndex < variant.fields().size(); fieldIndex++) {
            if (!sameType(
                program,
                id,
                variant.fields().get(fieldIndex).type(),
                definitionVariant.fields().get(fieldIndex).type())) {
              throw bindingMismatch(binding);
            }
          }
        }
      }
      case CoreDefinition.Interface declaration -> {
        CoreBindingShape.Interface shape = (CoreBindingShape.Interface) binding.shape();
        if (!sameTypeParameters(program, id, shape.typeParameters(), declaration.typeParameters())
            || shape.directParents().size() != declaration.directParents().size()) {
          throw bindingMismatch(binding);
        }
        for (CoreType parent : shape.directParents()) {
          boolean present =
              declaration.directParents().stream()
                  .anyMatch(value -> sameType(program, id, parent, value));
          if (!present) throw bindingMismatch(binding);
        }
      }
      case CoreDefinition.InterfaceMethod method -> {
        CoreBindingShape.InterfaceMethod shape = (CoreBindingShape.InterfaceMethod) binding.shape();
        if (!sameTypeParameters(program, id, shape.typeParameters(), method.typeParameters())
            || shape.parameters().size() != method.parameterTypes().size()
            || !sameType(program, id, shape.returnType(), method.returnType())) {
          throw bindingMismatch(binding);
        }
        for (int parameter = 0; parameter < shape.parameters().size(); parameter++) {
          if (!sameType(
              program,
              id,
              shape.parameters().get(parameter).type(),
              method.parameterTypes().get(parameter))) {
            throw bindingMismatch(binding);
          }
        }
      }
      case CoreDefinition.BuiltinConformance ignored -> throw bindingMismatch(binding);
    }
  }

  private static boolean sameType(
      CoreProgram program, DefinitionId owner, CoreType left, CoreType right) {
    return CoreTypes.absolute(left, owner, program)
        .equals(CoreTypes.absolute(right, owner, program));
  }

  private static boolean sameTypeParameters(
      CoreProgram program,
      DefinitionId owner,
      java.util.List<CoreTypeParameter> left,
      java.util.List<CoreTypeParameter> right) {
    if (left.size() != right.size()) return false;
    for (int index = 0; index < left.size(); index++) {
      CoreTypeParameter leftParameter = left.get(index);
      CoreTypeParameter rightParameter = right.get(index);
      if (leftParameter.index() != rightParameter.index()
          || leftParameter.upperBound().isPresent() != rightParameter.upperBound().isPresent()) {
        return false;
      }
      if (leftParameter.upperBound().isPresent()
          && !sameType(
              program,
              owner,
              leftParameter.upperBound().orElseThrow(),
              rightParameter.upperBound().orElseThrow())) {
        return false;
      }
    }
    return true;
  }

  private static IllegalArgumentException bindingMismatch(CoreBinding binding) {
    return new IllegalArgumentException(
        "namespace binding does not match its core definition ABI: " + binding.name());
  }
}
