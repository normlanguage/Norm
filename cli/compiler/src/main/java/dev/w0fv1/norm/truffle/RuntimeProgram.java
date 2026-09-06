package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationPolicy;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreProgram;
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
  private final Map<DefinitionId, Callable> callables;
  private final Map<DefinitionId, CoreAnnotationPolicy> annotationPolicies;

  private RuntimeProgram(
      Map<DefinitionId, CoreDefinition> structures,
      Map<DefinitionId, Callable> callables,
      Map<DefinitionId, CoreAnnotationPolicy> annotationPolicies) {
    this.structures = Map.copyOf(structures);
    this.callables = Map.copyOf(callables);
    this.annotationPolicies = Map.copyOf(annotationPolicies);
  }

  static RuntimeProgram from(CoreProgram program) {
    Map<DefinitionId, CoreDefinition> structures = new LinkedHashMap<>();
    Map<DefinitionId, Callable> callables = new LinkedHashMap<>();
    Map<DefinitionId, CoreAnnotationPolicy> policies = new LinkedHashMap<>();
    for (var record : program.definitions()) {
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
          policies.put(record.id(), CoreAnnotationPolicy.resolve(program, record.id(), aggregate));
        }
      }
    }
    return new RuntimeProgram(structures, callables, policies);
  }

  CoreAnnotationPolicy annotationPolicy(DefinitionId id) {
    var policy = annotationPolicies.get(Objects.requireNonNull(id));
    if (policy == null)
      throw new IllegalArgumentException("annotation policy requires an annotation aggregate");
    return policy;
  }

  Optional<CoreDefinition> structure(DefinitionId id) {
    return Optional.ofNullable(structures.get(Objects.requireNonNull(id)));
  }

  Optional<Callable> callable(DefinitionId id) {
    return Optional.ofNullable(callables.get(Objects.requireNonNull(id)));
  }

  List<CoreDefinitionRecord> structures() {
    var result = new ArrayList<CoreDefinitionRecord>();
    structures.forEach((id, definition) -> result.add(new CoreDefinitionRecord(id, definition)));
    result.sort(java.util.Comparator.comparing(CoreDefinitionRecord::id));
    return List.copyOf(result);
  }

  public DefinitionId resolve(DefinitionId owner, DefinitionReference reference) {
    return Objects.requireNonNull(reference)
        .resolve(owner, id -> structures.containsKey(id) || callables.containsKey(id));
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
