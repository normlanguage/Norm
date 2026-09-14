package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record DeclarationContract(
    String name,
    SymbolKind kind,
    SemanticType type,
    Optional<SymbolId> owner,
    List<TypeParameterInfo> typeParameters,
    List<ParameterInfo> parameters,
    Symbol.Accessor accessor,
    Syntax.Visibility visibility,
    Optional<Syntax.AggregateKind> aggregateKind,
    boolean implemented,
    List<SemanticType> parents) {
  DeclarationContract {
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
    parents = List.copyOf(parents);
  }

  static Map<SymbolId, DeclarationContract> capture(
      SemanticModelBuilder model, TypeResolver types) {
    var result = new LinkedHashMap<SymbolId, DeclarationContract>();
    model
        .declarationSymbols()
        .forEach(
            (declaration, id) -> {
              var symbol = model.symbols().get(id);
              var visibility =
                  switch (declaration) {
                    case Syntax.FunctionDecl value -> value.visibility();
                    case Syntax.AggregateDecl value -> value.visibility();
                    case Syntax.InterfaceDecl value -> value.visibility();
                    case Syntax.EnumDecl value -> value.visibility();
                    case Syntax.FieldDecl value -> value.visibility();
                    case Syntax.InterfaceMethodDecl ignored -> Syntax.Visibility.PUBLIC;
                    case Syntax.ConstructorDecl ignored -> Syntax.Visibility.PUBLIC;
                    case Syntax.Parameter ignored -> Syntax.Visibility.PUBLIC;
                    case Syntax.TypeParameter ignored -> Syntax.Visibility.PUBLIC;
                    case Syntax.EnumVariant ignored -> Syntax.Visibility.PUBLIC;
                    default ->
                        throw new IllegalArgumentException("unsupported declaration contract");
                  };
              boolean implemented =
                  switch (declaration) {
                    case Syntax.FunctionDecl value -> value.hasBody();
                    case Syntax.InterfaceMethodDecl value -> value.body().isPresent();
                    case Syntax.ConstructorDecl ignored -> true;
                    case Syntax.EnumVariant ignored -> true;
                    default -> false;
                  };
              List<SemanticType> parents =
                  symbol.kind() == SymbolKind.TYPE || symbol.kind() == SymbolKind.INTERFACE
                      ? types.directParents(
                          symbol
                              .specialize(
                                  symbol.typeParameters().stream()
                                      .map(TypeParameterInfo::type)
                                      .toList())
                              .orElseThrow()
                              .type())
                      : List.of();
              result.put(
                  id,
                  new DeclarationContract(
                      symbol.name(),
                      symbol.kind(),
                      symbol.type(),
                      symbol.owner(),
                      symbol.typeParameters(),
                      symbol.parameters(),
                      symbol.accessor(),
                      visibility,
                      declaration instanceof Syntax.AggregateDecl value
                          ? Optional.of(value.kind())
                          : Optional.empty(),
                      implemented,
                      parents));
            });
    return Map.copyOf(result);
  }
}
