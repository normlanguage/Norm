package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import java.util.List;
import java.util.Map;

final class SymbolSpecializer {
  Symbol specialize(Symbol symbol, List<SemanticType> arguments) {
    if (arguments.isEmpty() || arguments.size() != symbol.typeParameters().size()) return symbol;
    Map<String, SemanticType> substitutions = new java.util.LinkedHashMap<>();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      substitutions.put(symbol.typeParameters().get(index).type().identity(), arguments.get(index));
    }
    if (substitutions.size() != arguments.size()) return symbol;
    SemanticType type = symbol.type().substitute(substitutions);
    if (symbol.kind() == dev.w0fv1.norm.semantic.SymbolKind.TYPE
        || symbol.kind() == dev.w0fv1.norm.semantic.SymbolKind.INTERFACE) {
      type =
          SemanticType.declared(
              symbol.type().identity(), symbol.type().name(), arguments, symbol.type().category());
    }
    return new Symbol(
        symbol.id(),
        symbol.name(),
        symbol.kind(),
        type,
        symbol.declaration(),
        symbol.owner(),
        java.util.stream.IntStream.range(0, arguments.size())
            .mapToObj(
                index ->
                    new dev.w0fv1.norm.semantic.TypeParameterInfo(
                        symbol.typeParameters().get(index).name(),
                        arguments.get(index),
                        symbol
                            .typeParameters()
                            .get(index)
                            .upperBound()
                            .map(value -> value.substitute(substitutions))))
            .toList(),
        symbol.parameters().stream()
            .map(
                parameter ->
                    new ParameterInfo(parameter.name(), parameter.type().substitute(substitutions)))
            .toList(),
        symbol.documentation());
  }
}
