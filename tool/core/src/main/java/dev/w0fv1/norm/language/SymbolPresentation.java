package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;

final class SymbolPresentation {
  private SymbolPresentation() {}

  static String signature(Symbol symbol) {
    String typeParameters =
        symbol.typeParameters().isEmpty()
            ? ""
            : "<"
                + symbol.typeParameters().stream()
                    .map(
                        parameter -> {
                          if (parameter.type().kind()
                              != dev.w0fv1.norm.semantic.SemanticType.Kind.TYPE_PARAMETER) {
                            return parameter.type().displayName();
                          }
                          return parameter.name()
                              + parameter
                                  .upperBound()
                                  .map(bound -> " extends " + bound.displayName())
                                  .orElse("");
                        })
                    .collect(java.util.stream.Collectors.joining(", "))
                + ">";
    if (symbol.kind() == SymbolKind.FUNCTION
        || symbol.kind() == SymbolKind.METHOD
        || symbol.kind() == SymbolKind.TYPE_METHOD
        || symbol.kind() == SymbolKind.INTERFACE_METHOD) {
      String parameters =
          symbol.parameters().stream()
              .map(parameter -> parameter.type().displayName() + " " + parameter.name())
              .collect(java.util.stream.Collectors.joining(", "));
      return symbol.type().displayName()
          + " "
          + symbol.name()
          + typeParameters
          + "("
          + parameters
          + ")";
    }
    if (symbol.kind() == SymbolKind.ENUM_VARIANT) {
      String parameters =
          symbol.parameters().stream()
              .map(parameter -> parameter.type().displayName() + " " + parameter.name())
              .collect(java.util.stream.Collectors.joining(", "));
      return symbol.type().displayName()
          + " "
          + symbol.name()
          + (parameters.isEmpty() ? "" : "(" + parameters + ")");
    }
    if (symbol.kind() == SymbolKind.TYPE || symbol.kind() == SymbolKind.INTERFACE) {
      String parameters =
          symbol.parameters().stream()
              .map(parameter -> parameter.type().displayName() + " " + parameter.name())
              .collect(java.util.stream.Collectors.joining(", "));
      return symbol.name() + typeParameters + (parameters.isEmpty() ? "" : "(" + parameters + ")");
    }
    return symbol.type().displayName() + " " + symbol.name();
  }
}
