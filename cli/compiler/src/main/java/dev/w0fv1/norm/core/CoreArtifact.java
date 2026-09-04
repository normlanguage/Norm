package dev.w0fv1.norm.core;

import java.util.Objects;

public final class CoreArtifact {
  private final CoreProgram program;
  private final CoreNamespace namespace;
  private final CoreAuthoringMap authoring;
  private final CoreMetadata metadata;

  public CoreArtifact(
      CoreProgram program,
      CoreNamespace namespace,
      CoreAuthoringMap authoring,
      CoreMetadata metadata) {
    this.program = Objects.requireNonNull(program, "program");
    this.namespace = Objects.requireNonNull(namespace, "namespace");
    this.authoring = Objects.requireNonNull(authoring, "authoring");
    this.metadata = Objects.requireNonNull(metadata, "metadata");
    validate(program, namespace, authoring, metadata);
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

  public CoreMetadata metadata() {
    return metadata;
  }

  public CoreArtifact withEntryPoint(DefinitionOccurrenceId entryPoint) {
    return new CoreArtifact(program, namespace, authoring.withEntryPoint(entryPoint), metadata);
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
      CoreProgram program,
      CoreNamespace namespace,
      CoreAuthoringMap authoring,
      CoreMetadata metadata) {
    java.util.Set<DefinitionId> aggregateOccurrences = new java.util.HashSet<>();
    for (CoreDefinitionOccurrence occurrence : authoring.occurrences()) {
      for (DefinitionId definition : occurrence.representedDefinitions()) {
        if (program.definition(definition).isEmpty()) {
          throw new IllegalArgumentException("occurrence definition is absent: " + definition);
        }
      }
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      if (definition instanceof CoreDefinition.Aggregate
          && !aggregateOccurrences.add(occurrence.id().representative())) {
        throw new IllegalArgumentException("aggregate definition requires a unique occurrence");
      }
      validateRole(definition, occurrence.role());
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
    CoreDefinitionOccurrence entry = authoring.occurrence(authoring.entryPoint()).orElseThrow();
    if (!(program.definition(entry.id().representative()).orElseThrow()
            instanceof CoreDefinition.Callable)
        || entry.role() != CoreDefinitionRole.FUNCTION) {
      throw new IllegalArgumentException("entry occurrence must be a function");
    }
    for (CoreBinding binding : namespace.bindings()) {
      if (authoring.occurrence(binding.occurrence()).isEmpty()) {
        throw new IllegalArgumentException("namespace binding occurrence is absent");
      }
      validateBinding(program, namespace, authoring, binding);
    }
    CoreAnnotationVerifier.verifyArtifact(program, authoring, metadata);
    CoreArtifactMutabilityVerifier.verify(program, authoring);
  }

  private static void validateRole(CoreDefinition definition, CoreDefinitionRole role) {
    boolean valid =
        switch (definition) {
          case CoreDefinition.Aggregate ignored -> role == CoreDefinitionRole.AGGREGATE;
          case CoreDefinition.Enum ignored -> role == CoreDefinitionRole.ENUM;
          case CoreDefinition.Interface ignored -> role == CoreDefinitionRole.INTERFACE;
          case CoreDefinition.InterfaceMethod ignored ->
              role == CoreDefinitionRole.INTERFACE_METHOD;
          case CoreDefinition.BuiltinConformance ignored ->
              role == CoreDefinitionRole.BUILTIN_CONFORMANCE;
          case CoreDefinition.Callable callable ->
              switch (role) {
                case CONSTRUCTOR, METHOD -> callable.hasReceiver();
                case FUNCTION, EXTENSION, LAMBDA -> !callable.hasReceiver();
                default -> false;
              };
        };
    if (!valid) throw new IllegalArgumentException("definition occurrence role is invalid");
  }

  private static void validateBinding(
      CoreProgram program,
      CoreNamespace namespace,
      CoreAuthoringMap authoring,
      CoreBinding binding) {
    DefinitionId id = binding.definition();
    CoreDefinition definition = program.definition(id).orElseThrow();
    CoreDefinitionRole role = authoring.occurrence(binding.occurrence()).orElseThrow().role();
    if (binding.shape() instanceof CoreBindingShape.Callable callable
        && (callable.kind() == CoreCallableBindingKind.METHOD) != binding.ownerName().isPresent()) {
      throw bindingMismatch(binding);
    }
    CoreBindingKind expectedKind =
        switch (role) {
          case FUNCTION -> CoreBindingKind.FUNCTION;
          case EXTENSION -> CoreBindingKind.EXTENSION;
          case METHOD -> CoreBindingKind.METHOD;
          case ENUM -> CoreBindingKind.ENUM;
          case INTERFACE -> CoreBindingKind.INTERFACE;
          case INTERFACE_METHOD -> CoreBindingKind.INTERFACE_METHOD;
          case AGGREGATE -> {
            CoreDefinition.Aggregate declaration = (CoreDefinition.Aggregate) definition;
            yield switch (declaration.kind()) {
              case CLASS -> CoreBindingKind.CLASS;
              case VALUE -> CoreBindingKind.VALUE;
              case ANNOTATION -> CoreBindingKind.ANNOTATION;
            };
          }
          case CONSTRUCTOR, LAMBDA, BUILTIN_CONFORMANCE ->
              throw new IllegalArgumentException(
                  "definition occurrence role cannot be a namespace binding: " + role);
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
          if (!shape
                  .parameters()
                  .get(parameter)
                  .label()
                  .equals(callable.parameters().get(parameter).name())
              || !sameType(
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
          CoreDefinition ownerDefinition = program.definition(owner).orElseThrow();
          CoreBindingKind ownerKind =
              switch (ownerDefinition) {
                case CoreDefinition.Aggregate aggregate ->
                    switch (aggregate.kind()) {
                      case CLASS -> CoreBindingKind.CLASS;
                      case VALUE -> CoreBindingKind.VALUE;
                      case ANNOTATION -> CoreBindingKind.ANNOTATION;
                    };
                case CoreDefinition.Interface ignored -> CoreBindingKind.INTERFACE;
                default -> throw bindingMismatch(binding);
              };
          boolean ownerBindingPresent =
              namespace.bindings().stream()
                  .anyMatch(
                      candidate ->
                          candidate.kind() == ownerKind
                              && candidate.packageName().equals(binding.packageName())
                              && candidate.name().equals(binding.ownerName().orElseThrow())
                              && candidate.definition().equals(owner));
          if (!ownerBindingPresent) throw bindingMismatch(binding);
        }
      }
      case CoreDefinition.Aggregate declaration -> {
        CoreBindingShape.Aggregate shape = (CoreBindingShape.Aggregate) binding.shape();
        if (shape.kind() != declaration.kind()
            || shape.valueCategory() != declaration.valueCategory()
            || !sameTypeParameters(
                program, id, shape.typeParameters(), declaration.typeParameters())
            || shape.parentType().isPresent() != declaration.parentType().isPresent()
            || shape.fields().size() != declaration.fields().size()
            || shape.constructors().size() != declaration.constructors().size()
            || shape.conformances().size() != declaration.conformances().size()) {
          throw bindingMismatch(binding);
        }
        if (shape.parentType().isPresent()
            && !sameType(
                program,
                id,
                shape.parentType().orElseThrow(),
                declaration.parentType().orElseThrow())) {
          throw bindingMismatch(binding);
        }
        for (int field = 0; field < shape.fields().size(); field++) {
          if (!shape.fields().get(field).name().equals(declaration.fields().get(field).name())
              || !sameType(
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
        java.util.Set<DefinitionId> matched = new java.util.HashSet<>();
        for (CoreBindingShape.Constructor constructorShape : shape.constructors()) {
          DefinitionId constructorId =
              declaration.constructors().stream()
                  .map(reference -> program.resolve(id, (DefinitionReference) reference))
                  .filter(candidate -> !matched.contains(candidate))
                  .filter(candidate -> matchesConstructor(program, candidate, constructorShape))
                  .findFirst()
                  .orElseThrow(() -> bindingMismatch(binding));
          matched.add(constructorId);
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

  private static boolean matchesConstructor(
      CoreProgram program, DefinitionId constructorId, CoreBindingShape.Constructor shape) {
    CoreDefinition definition = program.definition(constructorId).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable constructor)
        || shape.parameters().size() != constructor.parameterTypes().size()) {
      return false;
    }
    for (int parameter = 0; parameter < shape.parameters().size(); parameter++) {
      if (!shape
              .parameters()
              .get(parameter)
              .label()
              .equals(constructor.parameters().get(parameter).name())
          || !sameType(
              program,
              constructorId,
              shape.parameters().get(parameter).type(),
              constructor.parameterTypes().get(parameter))) {
        return false;
      }
    }
    return true;
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
          || leftParameter.upperBound().isPresent() != rightParameter.upperBound().isPresent()
          || leftParameter.defaultType().isPresent() != rightParameter.defaultType().isPresent()) {
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
      if (leftParameter.defaultType().isPresent()
          && !sameType(
              program,
              owner,
              leftParameter.defaultType().orElseThrow(),
              rightParameter.defaultType().orElseThrow())) {
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
