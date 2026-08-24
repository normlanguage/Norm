package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record CoreDefinitionOrigin(
    String definitionName, SourceSpan rootSpan, Map<Integer, SourceSpan> nodeSpans)
    implements Comparable<CoreDefinitionOrigin> {
  public CoreDefinitionOrigin {
    Objects.requireNonNull(definitionName, "definitionName");
    if (definitionName.isBlank()) {
      throw new IllegalArgumentException("definition name must not be blank");
    }
    Objects.requireNonNull(rootSpan, "rootSpan");
    nodeSpans = Map.copyOf(nodeSpans);
  }

  public Optional<SourceSpan> span(int nodeIndex) {
    if (nodeIndex < 0) throw new IllegalArgumentException("node index must not be negative");
    return Optional.ofNullable(nodeSpans.get(nodeIndex));
  }

  @Override
  public int compareTo(CoreDefinitionOrigin other) {
    Objects.requireNonNull(other, "other");
    int documentOrder =
        rootSpan
            .source()
            .id()
            .uri()
            .toString()
            .compareTo(other.rootSpan.source().id().uri().toString());
    if (documentOrder != 0) return documentOrder;
    int offsetOrder = Integer.compare(rootSpan.startOffset(), other.rootSpan.startOffset());
    if (offsetOrder != 0) return offsetOrder;
    int lengthOrder = Integer.compare(rootSpan.length(), other.rootSpan.length());
    return lengthOrder != 0 ? lengthOrder : definitionName.compareTo(other.definitionName);
  }
}
