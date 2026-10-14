package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CoreRuntimeIdentity {
  private static final String DOMAIN = "norm:runtime:nominal:v1\0";
  private final CoreArtifact artifact;
  private final Map<DefinitionId, DefinitionId> nominalIds;
  private final Map<DefinitionId, DefinitionId> sources;
  private final Map<DefinitionOccurrenceId, String> members;
  private final Map<DefinitionOccurrenceId, String> memberNames;
  private final Map<DefinitionId, List<DefinitionOccurrenceId>> occurrences;
  private final List<CoreDefinitionRecord> definitions;
  private final Map<DefinitionId, DefinitionId> sourceMembers;

  private CoreRuntimeIdentity(CoreArtifact artifact) {
    this.artifact = artifact;
    var ids = new HashMap<DefinitionId, DefinitionId>();
    var indexedSources = new HashMap<DefinitionId, DefinitionId>();
    for (var record : artifact.program().definitions()) {
      Optional<CoreNominalTypeKey> nominal =
          switch (record.definition()) {
            case CoreDefinition.Aggregate aggregate -> Optional.of(aggregate.nominalType());
            case CoreDefinition.Interface declared -> Optional.of(declared.nominalType());
            case CoreDefinition.Enum declared -> Optional.of(declared.nominalType());
            default -> Optional.empty();
          };
      if (nominal.isEmpty()) continue;
      var key = nominal.orElseThrow();
      var writer =
          new CanonicalWriter()
              .writeString(key.module().name())
              .writeInt(key.module().version())
              .writeString(key.packageName())
              .writeString(key.name())
              .writeString(key.visibility().name())
              .writeBoolean(key.privateSourcePath().isPresent());
      key.privateSourcePath().ifPresent(writer::writeString);
      var id =
          new DefinitionId(
              new DefinitionGroupId(
                  ContentHasher.hash(DOMAIN, CoreIdentityVersion.CURRENT, writer.toByteArray())),
              0);
      ids.put(record.id(), id);
      DefinitionId previous = indexedSources.putIfAbsent(id, record.id());
      if (previous != null && !previous.equals(record.id()))
        throw new IllegalArgumentException("ambiguous runtime nominal type: " + key);
    }
    nominalIds = Map.copyOf(ids);
    sources = Map.copyOf(indexedSources);
    var indexedMembers = new HashMap<DefinitionOccurrenceId, String>();
    var indexedOccurrences = new HashMap<DefinitionId, List<DefinitionOccurrenceId>>();
    for (var occurrence : artifact.authoring().occurrences()) {
      occurrence
          .origin()
          .declarationIdentity()
          .ifPresent(key -> indexedMembers.put(occurrence.id(), key));
      for (var represented : occurrence.representedDefinitions())
        indexedOccurrences
            .computeIfAbsent(represented, ignored -> new ArrayList<>())
            .add(occurrence.id());
    }
    indexedOccurrences.replaceAll((id, values) -> List.copyOf(values));
    members = Map.copyOf(indexedMembers);
    var indexedNames = new HashMap<DefinitionOccurrenceId, String>();
    artifact
        .namespace()
        .bindings()
        .forEach(binding -> indexedNames.putIfAbsent(binding.occurrence(), binding.name()));
    artifact
        .authoring()
        .occurrences()
        .forEach(
            occurrence ->
                indexedNames.putIfAbsent(occurrence.id(), occurrence.origin().definitionName()));
    memberNames = Map.copyOf(indexedNames);
    occurrences = Map.copyOf(indexedOccurrences);
    definitions =
        artifact.program().definitions().stream()
            .map(
                record ->
                    new CoreDefinitionRecord(
                        id(record.id()), definition(record.id(), record.definition())))
            .toList();
    var slots = new HashMap<DefinitionId, DefinitionId>();
    for (var record : definitions) {
      if (!(record.definition() instanceof CoreDefinition.Callable)
          && !(record.definition() instanceof CoreDefinition.MethodSignature)) continue;
      for (var occurrence : occurrences(record.id())) {
        var slot = slot(occurrence);
        var previous = slots.putIfAbsent(slot, occurrence.representative());
        if (previous != null && !previous.equals(occurrence.representative())) {
          var before = artifact.program().definition(previous).orElseThrow();
          var after = artifact.program().definition(occurrence.representative()).orElseThrow();
          if (before instanceof CoreDefinition.MethodSignature
              && after instanceof CoreDefinition.Callable)
            slots.put(slot, occurrence.representative());
          else if (!(before instanceof CoreDefinition.Callable
              && after instanceof CoreDefinition.MethodSignature))
            throw new IllegalArgumentException("ambiguous runtime member slot: " + slot);
        }
      }
    }
    sourceMembers = Map.copyOf(slots);
  }

  public DefinitionId sourceMember(DefinitionId slot) {
    return sourceMembers.getOrDefault(Objects.requireNonNull(slot), slot);
  }

  public static CoreRuntimeIdentity create(CoreArtifact artifact) {
    return new CoreRuntimeIdentity(Objects.requireNonNull(artifact));
  }

  public DefinitionId id(DefinitionId source) {
    return nominalIds.getOrDefault(Objects.requireNonNull(source), source);
  }

  public DefinitionId source(DefinitionId runtime) {
    return sources.getOrDefault(Objects.requireNonNull(runtime), runtime);
  }

  public CoreType type(DefinitionId owner, CoreType type) {
    return CoreTypes.mapLinks(
        type,
        link -> {
          if (link instanceof DefinitionReference.External external
              && sources.containsKey(external.definition())) return external;
          return new DefinitionReference.External(
              id(artifact.program().resolve(source(owner), (DefinitionReference) link)));
        });
  }

  public CoreType absoluteType(CoreType type) {
    return CoreTypes.mapLinks(
        type,
        link -> {
          if (!(link instanceof DefinitionReference.External external))
            throw new IllegalArgumentException("runtime type requires an absolute reference");
          return new DefinitionReference.External(id(external.definition()));
        });
  }

  public CoreAnnotationValue annotationValue(CoreAnnotationValue value) {
    return CoreRewriter.resolveAnnotationValue(
        value,
        link ->
            new DefinitionReference.External(
                id(((DefinitionReference.External) link).definition())));
  }

  public String key(DefinitionOccurrenceId occurrence) {
    String key = members.get(Objects.requireNonNull(occurrence));
    return key == null ? "artifact:" + occurrence : key;
  }

  public String name(DefinitionOccurrenceId occurrence) {
    return Objects.requireNonNull(
        memberNames.get(Objects.requireNonNull(occurrence)), "runtime member name");
  }

  public DefinitionId slot(DefinitionOccurrenceId occurrence) {
    var source = artifact.program().definition(occurrence.representative()).orElseThrow();
    CoreDefinition.MethodSignature signature =
        switch (source) {
          case CoreDefinition.MethodSignature method ->
              (CoreDefinition.MethodSignature) definition(occurrence.representative(), method);
          case CoreDefinition.Callable callable when callable.receiverType().isPresent() -> {
            var normalized =
                (CoreDefinition.Callable) definition(occurrence.representative(), callable);
            yield new CoreDefinition.MethodSignature(
                name(occurrence),
                normalized.receiverType().orElseThrow(),
                normalized.typeParameters(),
                normalized.parameters().stream().map(CoreCallableParameter::type).toList(),
                normalized.returnType());
          }
          default -> null;
        };
    var bytes =
        signature == null
            ? new CanonicalWriter().writeString(key(occurrence)).toByteArray()
            : CoreCodec.encodeDefinition(signature, link -> (DefinitionReference) link);
    return new DefinitionId(
        new DefinitionGroupId(
            ContentHasher.hash(
                artifact.authoring().occurrence(occurrence).orElseThrow().role()
                        == CoreDefinitionRole.CONSTRUCTOR
                    ? "norm:runtime:constructor:v1\0"
                    : "norm:runtime:member:v1\0",
                CoreIdentityVersion.CURRENT,
                bytes)),
        0);
  }

  public DefinitionId slot(DefinitionId source) {
    var candidates = occurrences(source);
    if (candidates.isEmpty())
      throw new IllegalArgumentException("runtime dispatch member has no declaration occurrence");
    var ids = candidates.stream().map(this::slot).distinct().toList();
    if (ids.size() != 1)
      throw new IllegalArgumentException(
          "runtime dispatch member requires an exact declaration occurrence");
    return ids.getFirst();
  }

  public List<DefinitionOccurrenceId> occurrences(DefinitionId source) {
    return occurrences.getOrDefault(Objects.requireNonNull(source), List.of());
  }

  public List<CoreDefinitionRecord> definitions() {
    return definitions;
  }

  public CoreDefinition definition(DefinitionId owner, CoreDefinition definition) {
    CoreDefinition declaration = definition;
    if (definition instanceof CoreDefinition.Callable callable) {
      declaration =
          new CoreDefinition.Callable(
              callable.receiverType(),
              callable.typeParameters(),
              callable.captureTypes(),
              callable.captureLocals(),
              callable.parameters(),
              callable.reifiedTypeLocals(),
              callable.interceptors(),
              callable.returnType(),
              callable.locals(),
              new CoreBlock(0, List.of()));
    }
    return CoreRewriter.map(
        declaration,
        link ->
            new DefinitionReference.External(
                id(artifact.program().resolve(owner, (DefinitionReference) link))));
  }
}
