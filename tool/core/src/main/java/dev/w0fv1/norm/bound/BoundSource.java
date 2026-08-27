package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Objects;

public record BoundSource(
    SourceFile source,
    String packageName,
    List<BoundEnumId> enums,
    List<BoundInterfaceId> interfaces,
    List<BoundAggregateId> aggregates,
    List<BoundCallableId> callables) {
  public BoundSource {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(packageName, "packageName");
    enums = List.copyOf(enums);
    interfaces = List.copyOf(interfaces);
    aggregates = List.copyOf(aggregates);
    callables = List.copyOf(callables);
  }
}
