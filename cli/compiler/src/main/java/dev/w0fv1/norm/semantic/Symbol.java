package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceLocation;
import java.util.List;
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
}
