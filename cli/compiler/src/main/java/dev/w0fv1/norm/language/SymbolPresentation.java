package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import java.util.List;

final class SymbolPresentation {
  private SymbolPresentation() {}

  static Symbol callable(Symbol symbol) {
    if (!symbol.type().isFunction()) return symbol;
    List<dev.w0fv1.norm.semantic.ParameterInfo> parameters =
        java.util.stream.IntStream.range(0, symbol.type().functionParameterTypes().size())
            .mapToObj(
                index ->
                    new dev.w0fv1.norm.semantic.ParameterInfo(
                        "argument" + index, symbol.type().functionParameterTypes().get(index)))
            .toList();
    return new Symbol(
        symbol.id(),
        symbol.name(),
        SymbolKind.FUNCTION,
        symbol.type().functionReturnType(),
        symbol.declaration(),
        symbol.owner(),
        List.of(),
        parameters,
        symbol.documentation());
  }

  static Symbol annotation(dev.w0fv1.norm.semantic.SemanticModel model, Symbol symbol) {
    return model
        .annotations()
        .schema(symbol.id())
        .map(
            schema ->
                new Symbol(
                    symbol.id(),
                    symbol.name(),
                    symbol.kind(),
                    symbol.type(),
                    symbol.declaration(),
                    symbol.owner(),
                    symbol.typeParameters(),
                    schema.parameters().stream()
                        .map(
                            parameter ->
                                new dev.w0fv1.norm.semantic.ParameterInfo(
                                    parameter.name(), parameter.type()))
                        .toList(),
                    symbol.documentation()))
        .orElse(symbol);
  }

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
                                  .orElse("")
                              + parameter
                                  .defaultType()
                                  .map(type -> " = " + type.displayName())
                                  .orElse("");
                        })
                    .collect(java.util.stream.Collectors.joining(", "))
                + ">";
    if (symbol.kind() == SymbolKind.FUNCTION
        || symbol.kind() == SymbolKind.EXTENSION
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
