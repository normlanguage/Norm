package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Objects;

public record BoundSource(
    SourceFile source,
    String packageName,
    List<BoundEnumId> enums,
    List<BoundClassId> classes,
    List<BoundCallableId> callables) {
  public BoundSource {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(packageName, "packageName");
    enums = List.copyOf(enums);
    classes = List.copyOf(classes);
    callables = List.copyOf(callables);
  }
}
