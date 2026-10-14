package dev.w0fv1.norm.core;

import dev.w0fv1.norm.abi.OpaqueValueAbi;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record CoreStateMigrationProtocol(
    DefinitionId annotationId,
    CoreValueCategory annotationCategory,
    DefinitionId stateTypeId,
    CoreValueCategory stateCategory) {
  public static final String CONVERTER_FIELD = "convert";

  public static Optional<CoreStateMigrationProtocol> resolve(CoreProgram program) {
    return resolve(
        program.definitions().stream()
            .map(
                record ->
                    new CoreDefinitionRecord(
                        record.id(),
                        CoreRewriter.map(
                            record.definition(),
                            link ->
                                new DefinitionReference.External(
                                    program.resolve(record.id(), (DefinitionReference) link)))))
            .toList());
  }

  public static Optional<CoreStateMigrationProtocol> resolve(
      List<CoreDefinitionRecord> definitions) {
    var identity = OpaqueValueAbi.MIGRATION_STATE;
    DefinitionId annotation = null;
    DefinitionId state = null;
    CoreType converterType = null;
    CoreValueCategory annotationCategory = null;
    CoreValueCategory stateCategory = null;
    for (var definition : definitions) {
      if (!(definition.definition() instanceof CoreDefinition.Aggregate aggregate)) continue;
      var nominal = aggregate.nominalType();
      if (!nominal.module().name().equals(identity.moduleName())
          || nominal.module().version() != identity.moduleVersion()
          || !nominal.packageName().equals(identity.packageName())) continue;
      if (nominal.name().equals("StateMigration")
          && aggregate.kind() == CoreAggregateKind.ANNOTATION) {
        if (annotation != null)
          throw new IllegalArgumentException("Ambiguous state migration annotation");
        if (aggregate.fields().stream().noneMatch(field -> field.name().equals(CONVERTER_FIELD)))
          throw new IllegalArgumentException("State migration annotation has no converter");
        converterType =
            aggregate.fields().stream()
                .filter(field -> field.name().equals(CONVERTER_FIELD))
                .findFirst()
                .orElseThrow()
                .type();
        annotation = definition.id();
        annotationCategory = aggregate.valueCategory();
      }
      if (nominal.name().equals(identity.typeName())
          && aggregate.kind() == CoreAggregateKind.CLASS) {
        if (state != null) throw new IllegalArgumentException("Ambiguous managed migration state");
        state = definition.id();
        stateCategory = aggregate.valueCategory();
      }
    }
    if (annotation == null) return Optional.empty();
    if (state == null) throw new IllegalArgumentException("State migration context is absent");
    var protocol =
        new CoreStateMigrationProtocol(annotation, annotationCategory, state, stateCategory);
    var signature =
        new CoreType.Function(
            CoreType.VOID, List.of(protocol.stateType()), CoreNullability.NON_NULL);
    if (!signature.equals(converterType))
      throw new IllegalArgumentException(
          "State migration converter must be Function<Void(MigrationState)>");
    return Optional.of(protocol);
  }

  public static Set<DefinitionId> appliedTypes(
      CoreArtifact artifact, CoreRuntimeIdentity identity) {
    return resolve(artifact.program())
        .map(
            protocol ->
                artifact.metadata().annotations().stream()
                    .filter(application -> application.annotation().equals(protocol.annotationId()))
                    .map(CoreAnnotationApplication::target)
                    .filter(
                        target ->
                            target instanceof CoreAnnotationTarget.Definition definition
                                && definition.kind() == AnnotationTarget.TYPE)
                    .map(
                        target ->
                            identity.id(
                                ((CoreAnnotationTarget.Definition) target)
                                    .occurrence()
                                    .representative()))
                    .collect(Collectors.toUnmodifiableSet()))
        .orElse(Set.of());
  }

  public CoreType annotationType() {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(annotationId)),
        List.of(),
        annotationCategory,
        CoreNullability.NON_NULL);
  }

  public CoreType stateType() {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(stateTypeId)),
        List.of(),
        stateCategory,
        CoreNullability.NON_NULL);
  }
}
