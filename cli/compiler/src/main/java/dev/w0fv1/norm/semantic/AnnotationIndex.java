package dev.w0fv1.norm.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AnnotationIndex {
  private final Map<SymbolId, AnnotationSchema> schemas;
  private final List<AnnotationApplication> applications;

  public AnnotationIndex(
      Map<SymbolId, AnnotationSchema> schemas, List<AnnotationApplication> applications) {
    this.schemas = Map.copyOf(schemas);
    this.applications = List.copyOf(applications);
  }

  public Map<SymbolId, AnnotationSchema> schemas() {
    return schemas;
  }

  public List<AnnotationApplication> applications() {
    return applications;
  }

  public Optional<AnnotationSchema> schema(SymbolId symbol) {
    return Optional.ofNullable(schemas.get(Objects.requireNonNull(symbol, "symbol")));
  }

  public static AnnotationIndex empty() {
    return new AnnotationIndex(Map.of(), List.of());
  }
}
