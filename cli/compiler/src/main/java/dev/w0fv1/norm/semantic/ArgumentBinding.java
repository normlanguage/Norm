package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.source.SourceSpan;
import java.util.List;
import java.util.Map;

public record ArgumentBinding(List<Integer> parameterIndices, Map<SourceSpan, Integer> labels) {
  public ArgumentBinding {
    parameterIndices = List.copyOf(parameterIndices);
    labels = Map.copyOf(labels);
  }
}
