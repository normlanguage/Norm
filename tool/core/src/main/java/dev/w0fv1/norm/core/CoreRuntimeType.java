package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreRuntimeType(CoreType template, List<CoreTypeCapture> captures) {
  public CoreRuntimeType {
    Objects.requireNonNull(template, "template");
    captures =
        List.copyOf(captures).stream()
            .sorted(java.util.Comparator.comparingInt(CoreTypeCapture::typeParameterIndex))
            .toList();
    for (int index = 1; index < captures.size(); index++) {
      if (captures.get(index - 1).typeParameterIndex()
          == captures.get(index).typeParameterIndex()) {
        throw new IllegalArgumentException("runtime type captures must be unique");
      }
    }
  }
}
