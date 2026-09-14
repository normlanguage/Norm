package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreDefinitionOrigin;
import dev.w0fv1.norm.core.CoreDependencyIndex;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.semantic.SpanIndex;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.TokenSpanMapping;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

final class CoreReusePlan {
  private final CoreArtifact artifact;
  private final CoreBuildHistory history;
  private final Map<CoreBuildHistory.Key, CoreDefinitionOrigin> origins;
  private final Map<DefinitionOccurrenceId, CoreBuildHistory.Key> keys;
  private final Map<DefinitionOccurrenceId, CoreBinding> bindings;
  private final java.util.Set<CoreBuildHistory.Key> relinked;

  CoreReusePlan(
      CoreArtifact artifact,
      CoreBuildHistory history,
      IncrementalAnalysisPlan analysis,
      Map<CoreBuildHistory.Key, SourceSpan> current) {
    this.artifact = artifact;
    this.history = history;
    var mappings = SpanIndex.from(analysis.mappings());
    Map<CoreBuildHistory.Key, CoreDefinitionOrigin> reusable = new LinkedHashMap<>();
    var changed = new LinkedHashSet<DefinitionOccurrenceId>();
    keys =
        history.occurrences().entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getValue, Map.Entry::getKey));
    bindings =
        artifact.namespace().bindings().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    CoreBinding::occurrence, value -> value));
    for (var occurrence : artifact.authoring().occurrences()) {
      var key = keys.get(occurrence.id());
      var origin = occurrence.origin();
      var root = rebase(origin.rootSpan(), mappings);
      Map<Integer, SourceSpan> nodes = new LinkedHashMap<>();
      boolean reusableOrigin =
          key != null && root.isPresent() && root.orElseThrow().equals(current.get(key));
      if (reusableOrigin) {
        for (var node : origin.nodeSpans().entrySet()) {
          var mapped = rebase(node.getValue(), mappings);
          if (mapped.isEmpty()) {
            reusableOrigin = false;
            break;
          }
          nodes.put(node.getKey(), mapped.orElseThrow());
        }
      }
      if (reusableOrigin) {
        reusable.put(
            key, new CoreDefinitionOrigin(origin.definitionName(), root.orElseThrow(), nodes));
      } else {
        changed.add(occurrence.id());
      }
    }
    changed.addAll(CoreDependencyIndex.create(artifact).transitiveDependentsOf(changed));
    relinked =
        changed.stream()
            .map(keys::get)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    origins = Map.copyOf(reusable);
  }

  boolean requiresLinking(CoreBuildHistory.Key key) {
    return relinked.contains(key);
  }

  CoreArtifact artifact() {
    return artifact;
  }

  CoreBuildHistory history() {
    return history;
  }

  Optional<CoreDefinitionOrigin> origin(CoreBuildHistory.Key key) {
    return Optional.ofNullable(origins.get(key));
  }

  CoreBuildHistory.Key key(DefinitionOccurrenceId occurrence) {
    return java.util.Objects.requireNonNull(keys.get(occurrence), "core declaration key");
  }

  Optional<CoreBinding> binding(DefinitionOccurrenceId occurrence) {
    return Optional.ofNullable(bindings.get(occurrence));
  }

  private static Optional<SourceSpan> rebase(
      SourceSpan span, SpanIndex<TokenSpanMapping> mappings) {
    return mappings
        .at(span.source().id(), span.startOffset())
        .filter(entry -> span.endOffset() <= entry.span().endOffset())
        .map(entry -> entry.value().rebase(span));
  }
}
