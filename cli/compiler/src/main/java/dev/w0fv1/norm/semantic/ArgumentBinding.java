package dev.w0fv1.norm.semantic;

import java.util.List;

public record ArgumentBinding(List<Integer> parameterIndices) {
  public ArgumentBinding {
    parameterIndices = List.copyOf(parameterIndices);
  }
}
