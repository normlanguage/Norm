package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;

final class SymbolPresentation {
  private SymbolPresentation() {}

  static String signature(Symbol symbol) {
    String typeParameters =
        symbol.typeParameters().isEmpty()
            ? ""
            : "<" + String.join(", ", symbol.typeParameters()) + ">";
    if (symbol.kind() == SymbolKind.FUNCTION || symbol.kind() == SymbolKind.METHOD) {
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
    if (symbol.kind() == SymbolKind.TYPE) {
      String parameters =
          symbol.parameters().stream()
              .map(parameter -> parameter.type().displayName() + " " + parameter.name())
              .collect(java.util.stream.Collectors.joining(", "));
      return symbol.name() + typeParameters + (parameters.isEmpty() ? "" : "(" + parameters + ")");
    }
    return symbol.type().displayName() + " " + symbol.name();
  }
}
