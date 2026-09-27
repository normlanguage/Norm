package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DocumentAnnotationIndex(
    Map<DocumentId, Document> packages, Map<SymbolId, Document> symbols) {
  public record Document(
      String description, List<SymbolId> types, List<SymbolId> functions, List<SymbolId> fields) {
    public Document {
      types = List.copyOf(types);
      functions = List.copyOf(functions);
      fields = List.copyOf(fields);
    }
  }

  public DocumentAnnotationIndex {
    packages = Map.copyOf(packages);
    symbols = Map.copyOf(symbols);
  }

  public static DocumentAnnotationIndex from(
      AnnotationIndex annotations, Map<SymbolId, Symbol> symbols) {
    Map<DocumentId, Document> packages = new LinkedHashMap<>();
    Map<SymbolId, Document> declarations = new LinkedHashMap<>();
    for (AnnotationApplication application : annotations.applications()) {
      AnnotationSchema schema = annotations.schema(application.annotation()).orElseThrow();
      Symbol annotation = Objects.requireNonNull(symbols.get(application.annotation()));
      if (!schema.name().equals("Document")
          || !annotation.type().identity().equals(AnnotationAbi.PACKAGE + ".Document")) {
        continue;
      }
      Map<String, AnnotationValue> values = new LinkedHashMap<>();
      for (int index = 0; index < schema.parameters().size(); index++) {
        values.put(schema.parameters().get(index).name(), application.values().get(index));
      }
      AnnotationValue description = values.get("description");
      Document document =
          new Document(
              description != null && description.value() instanceof AnnotationValue.Literal literal
                  ? literal.value().toString()
                  : "",
              references(values.get("types")),
              references(values.get("functions")),
              references(values.get("fields")));
      switch (application.target()) {
        case AnnotationSite.Package site -> packages.put(site.document(), document);
        case AnnotationSite.Symbol site -> declarations.put(site.symbol(), document);
      }
    }
    return new DocumentAnnotationIndex(packages, declarations);
  }

  private static List<SymbolId> references(AnnotationValue value) {
    if (value == null || value.value() == AnnotationValue.Null.INSTANCE) return List.of();
    if (!(value.value() instanceof AnnotationValue.ListValue list)) return List.of();
    return list.values().stream()
        .map(AnnotationValue::value)
        .filter(AnnotationDeclarationReference.class::isInstance)
        .map(AnnotationDeclarationReference.class::cast)
        .map(AnnotationDeclarationReference::target)
        .toList();
  }
}
