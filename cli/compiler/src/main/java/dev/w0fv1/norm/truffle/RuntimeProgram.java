package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationPolicy;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreRuntimeIdentity;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.core.DefinitionResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class RuntimeProgram implements DefinitionResolver {
  private final Map<DefinitionId, CoreDefinition> structures;
  private final List<CoreDefinitionRecord> orderedStructures;
  private final Map<DefinitionId, Callable> callables;
  private final Map<DefinitionId, CoreAnnotationPolicy> annotationPolicies;
  private final CoreRuntimeIdentity identity;

  private RuntimeProgram(
      Map<DefinitionId, CoreDefinition> structures,
      Map<DefinitionId, Callable> callables,
      Map<DefinitionId, CoreAnnotationPolicy> annotationPolicies,
      CoreRuntimeIdentity identity) {
    this.identity = identity;
    this.structures = Map.copyOf(structures);
    var ordered = new ArrayList<CoreDefinitionRecord>(structures.size());
    structures.forEach((id, definition) -> ordered.add(new CoreDefinitionRecord(id, definition)));
    ordered.sort(java.util.Comparator.comparing(CoreDefinitionRecord::id));
    this.orderedStructures = List.copyOf(ordered);
    this.callables = Map.copyOf(callables);
    this.annotationPolicies = Map.copyOf(annotationPolicies);
  }

  static RuntimeProgram from(CoreArtifact artifact, CoreRuntimeIdentity identity) {
    Map<DefinitionId, CoreDefinition> structures = new LinkedHashMap<>();
    Map<DefinitionId, Callable> callables = new LinkedHashMap<>();
    Map<DefinitionId, CoreAnnotationPolicy> policies = new LinkedHashMap<>();
    for (var record : identity.definitions()) {
      if (record.definition() instanceof CoreDefinition.Callable callable) {
        callables.put(
            record.id(),
            new Callable(
                callable.receiverType(),
                callable.parameters().stream()
                    .map(parameter -> new Parameter(parameter.name(), parameter.type()))
                    .toList(),
                callable.returnType(),
                callable.captureTypes().size(),
                callable.typeParameters().size(),
                callable.receiverTypeParameterCount()));
      } else {
        structures.put(record.id(), record.definition());
        if (record.definition() instanceof CoreDefinition.Aggregate aggregate
            && aggregate.kind() == CoreAggregateKind.ANNOTATION) {
          var source = identity.source(record.id());
          policies.put(
              record.id(),
              CoreAnnotationPolicy.resolve(
                  artifact.program(),
                  source,
                  (CoreDefinition.Aggregate) artifact.program().definition(source).orElseThrow()));
        }
      }
    }
    return new RuntimeProgram(structures, callables, policies, identity);
  }

  CoreAnnotationPolicy annotationPolicy(DefinitionId id) {
    var policy = annotationPolicies.get(identity.id(Objects.requireNonNull(id)));
    if (policy == null)
      throw new IllegalArgumentException("annotation policy requires an annotation aggregate");
    return policy;
  }

  Optional<CoreDefinition> structure(DefinitionId id) {
    return Optional.ofNullable(
        structures.get(identity.id(sourceMember(Objects.requireNonNull(id)))));
  }

  Optional<Callable> callable(DefinitionId id) {
    return Optional.ofNullable(callables.get(sourceMember(Objects.requireNonNull(id))));
  }

  List<CoreDefinitionRecord> structures() {
    return orderedStructures;
  }

  DefinitionId sourceMember(DefinitionId slot) {
    return identity.sourceMember(slot);
  }

  CoreType type(CoreType type) {
    return identity.absoluteType(type);
  }

  DefinitionId id(DefinitionId source) {
    return identity.id(source);
  }

  CoreRuntimeIdentity identity() {
    return identity;
  }

  public DefinitionId resolve(DefinitionId owner, DefinitionReference reference) {
    Objects.requireNonNull(owner);
    if (reference instanceof DefinitionReference.External external) {
      DefinitionId resolved = identity.id(external.definition());
      if (structures.containsKey(resolved) || callables.containsKey(resolved)) return resolved;
    }
    return identity.id(
        Objects.requireNonNull(reference)
            .resolve(
                identity.source(owner),
                id -> structure(id).isPresent() || callables.containsKey(id)));
  }

  record Parameter(String name, CoreType type) {
    Parameter {
      Objects.requireNonNull(name);
      Objects.requireNonNull(type);
    }
  }

  record Callable(
      Optional<CoreType> receiverType,
      List<Parameter> parameters,
      CoreType returnType,
      int captureCount,
      int typeParameterCount,
      int receiverTypeParameterCount) {
    Callable {
      Objects.requireNonNull(receiverType);
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(returnType);
      if (captureCount < 0 || typeParameterCount < 0 || receiverTypeParameterCount < 0) {
        throw new IllegalArgumentException("callable signature counts must not be negative");
      }
    }

    List<CoreType> parameterTypes() {
      return parameters.stream().map(Parameter::type).toList();
    }

    boolean hasReceiver() {
      return receiverType.isPresent();
    }
  }
}
