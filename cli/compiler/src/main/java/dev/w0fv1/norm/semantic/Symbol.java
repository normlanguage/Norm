package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.source.SourceLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Symbol(
    SymbolId id,
    String name,
    SymbolKind kind,
    SemanticType type,
    Optional<SourceLocation> declaration,
    Optional<SymbolId> owner,
    List<TypeParameterInfo> typeParameters,
    List<ParameterInfo> parameters,
    String documentation) {
  public Symbol {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(type, "type");
    declaration = Objects.requireNonNull(declaration, "declaration");
    owner = Objects.requireNonNull(owner, "owner");
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
    documentation = Objects.requireNonNull(documentation, "documentation");
  }

  public Optional<Symbol> specialize(List<SemanticType> arguments) {
    return TypeArguments.complete(typeParameters, arguments)
        .map(
            completed -> {
              Map<String, SemanticType> substitutions = new LinkedHashMap<>();
              for (int index = 0; index < typeParameters.size(); index++) {
                substitutions.put(
                    typeParameters.get(index).type().identity(), completed.get(index));
              }
              return substitute(substitutions);
            });
  }

  public Symbol substitute(Map<String, SemanticType> substitutions) {
    if (substitutions.isEmpty()) return this;
    List<TypeParameterInfo> specialized =
        typeParameters.stream()
            .map(
                parameter ->
                    new TypeParameterInfo(
                        parameter.name(),
                        parameter.type().substitute(substitutions),
                        parameter.upperBound().map(bound -> bound.substitute(substitutions)),
                        parameter.defaultType().map(value -> value.substitute(substitutions))))
            .toList();
    SemanticType result = type.substitute(substitutions);
    if (kind == SymbolKind.TYPE || kind == SymbolKind.INTERFACE) {
      result =
          SemanticType.declared(
              type.identity(),
              type.name(),
              specialized.stream().map(TypeParameterInfo::type).toList(),
              type.category());
    }
    return new Symbol(
        id,
        name,
        kind,
        result,
        declaration,
        owner,
        specialized,
        parameters.stream()
            .map(
                parameter ->
                    new ParameterInfo(
                        parameter.name(),
                        parameter.type().substitute(substitutions),
                        parameter.hasDefault()))
            .toList(),
        documentation);
  }
}
