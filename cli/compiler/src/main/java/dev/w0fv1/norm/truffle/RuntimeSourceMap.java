package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreAuthoringMap;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.source.SourceSpan;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

final class RuntimeSourceMap {
  private final Map<DefinitionOccurrenceId, Occurrence> occurrences;

  private RuntimeSourceMap(Map<DefinitionOccurrenceId, Occurrence> occurrences) {
    this.occurrences = Map.copyOf(occurrences);
  }

  static RuntimeSourceMap from(CoreAuthoringMap authoring) {
    Map<DefinitionOccurrenceId, Occurrence> occurrences = new HashMap<>();
    for (var occurrence : authoring.occurrences()) {
      Map<Integer, Location> nodes = new HashMap<>();
      occurrence
          .origin()
          .nodeSpans()
          .forEach((index, span) -> nodes.put(index, Location.from(span)));
      occurrences.put(
          occurrence.id(),
          new Occurrence(Location.from(occurrence.origin().rootSpan()), Map.copyOf(nodes)));
    }
    return new RuntimeSourceMap(occurrences);
  }

  Location location(DefinitionOccurrenceId occurrence, int nodeIndex) {
    if (nodeIndex < 0) throw new IllegalArgumentException("node index must not be negative");
    Occurrence positions = occurrences.get(occurrence);
    if (positions == null) throw new IllegalArgumentException("definition occurrence is absent");
    return positions.nodes().getOrDefault(nodeIndex, positions.root());
  }

  private record Occurrence(Location root, Map<Integer, Location> nodes) {}

  record Location(URI uri, int line, int column) {
    static Location from(SourceSpan span) {
      return new Location(span.source().id().uri(), span.start().line(), span.start().column());
    }
  }
}
