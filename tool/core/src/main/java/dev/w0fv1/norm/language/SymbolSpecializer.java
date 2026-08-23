package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SymbolSpecializer {
  Symbol specialize(Symbol symbol, List<SemanticType> arguments) {
    if (arguments.isEmpty() || arguments.size() != symbol.typeParameters().size()) return symbol;
    Map<String, SemanticType> substitutions = new java.util.LinkedHashMap<>();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      String name = symbol.typeParameters().get(index);
      Optional<String> identity = typeParameterIdentity(symbol.type(), name);
      if (identity.isEmpty()) {
        identity =
            symbol.parameters().stream()
                .map(ParameterInfo::type)
                .map(type -> typeParameterIdentity(type, name))
                .flatMap(Optional::stream)
                .findFirst();
      }
      if (identity.isPresent()) {
        substitutions.put(identity.orElseThrow(), arguments.get(index));
      }
    }
    if (substitutions.size() != arguments.size()) return symbol;
    SemanticType type = symbol.type().substitute(substitutions);
    if (symbol.kind() == dev.w0fv1.norm.semantic.SymbolKind.TYPE) {
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
        arguments.stream().map(SemanticType::displayName).toList(),
        symbol.parameters().stream()
            .map(
                parameter ->
                    new ParameterInfo(parameter.name(), parameter.type().substitute(substitutions)))
            .toList(),
        symbol.documentation());
  }

  private static Optional<String> typeParameterIdentity(SemanticType type, String name) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER && type.name().equals(name)) {
      return Optional.of(type.identity());
    }
    return type.arguments().stream()
        .map(argument -> typeParameterIdentity(argument, name))
        .flatMap(Optional::stream)
        .findFirst();
  }
}
