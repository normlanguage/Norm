package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VisibilityResolver {
  private final Input input;

  VisibilityResolver(Input input) {
    this.input = input;
  }

  Result build() {
    List<SemanticScope> scopes = new ArrayList<>(input.programs().size());
    for (Syntax.Program program : input.programs()) {
      LinkedHashMap<SymbolId, SymbolId> visible = new LinkedHashMap<>();
      input.symbols().values().stream()
          .filter(symbol -> symbol.id().value().startsWith("builtin/"))
          .filter(symbol -> symbol.owner().isEmpty())
          .forEach(symbol -> visible.put(symbol.id(), symbol.id()));
      for (Syntax.Program candidate : input.programs()) {
        boolean sameFile = candidate == program;
        boolean samePackage =
            candidate.packageName().equals(program.packageName())
                && input
                    .scope()
                    .sameModule(program.span().source().id(), candidate.span().source().id());
        for (Syntax.EnumDecl declaration : candidate.enums()) {
          addVisibleDeclaration(
              visible, declaration, declaration.visibility(), sameFile, samePackage);
        }
        for (Syntax.InterfaceDecl declaration : candidate.interfaces()) {
          addVisibleDeclaration(
              visible, declaration, declaration.visibility(), sameFile, samePackage);
        }
        for (Syntax.AggregateDecl declaration : candidate.aggregates()) {
          addVisibleDeclaration(
              visible, declaration, declaration.visibility(), sameFile, samePackage);
        }
        for (Syntax.FunctionDecl declaration : candidate.functions()) {
          addVisibleDeclaration(
              visible, declaration, declaration.visibility(), sameFile, samePackage);
        }
      }
      for (Syntax.ImportDecl imported : program.imports()) {
        Object declaration = declaration(program, imported.localName());
        if (declaration == null) continue;
        SymbolId id =
            imported.alias().isPresent()
                ? input.importAliases().get(imported)
                : input.declarationSymbols().get(declaration);
        visible.put(id, id);
      }
      scopes.add(new SemanticScope(program.span(), 0, List.copyOf(visible.values())));
    }
    return new Result(scopes, importableSymbols());
  }

  private List<ImportableSymbol> importableSymbols() {
    List<ImportableSymbol> result = new ArrayList<>();
    for (Syntax.Program program : input.programs()) {
      program.enums().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(declaration -> importable(program, declaration, declaration.name()))
          .forEach(result::add);
      program.interfaces().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(declaration -> importable(program, declaration, declaration.name()))
          .forEach(result::add);
      program.aggregates().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(declaration -> importable(program, declaration, declaration.name()))
          .forEach(result::add);
      program.functions().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(declaration -> importable(program, declaration, declaration.name()))
          .forEach(result::add);
    }
    return List.copyOf(result);
  }

  private ImportableSymbol importable(Syntax.Program program, Object declaration, String name) {
    String qualified = program.packageName().isEmpty() ? name : program.packageName() + "." + name;
    return new ImportableSymbol(
        input.symbols().get(input.declarationSymbols().get(declaration)),
        qualified,
        input.exportedSources().contains(program.span().source().id()));
  }

  private void addVisibleDeclaration(
      Map<SymbolId, SymbolId> visible,
      Object declaration,
      Syntax.Visibility visibility,
      boolean sameFile,
      boolean samePackage) {
    if (sameFile || samePackage && visibility == Syntax.Visibility.PUBLIC) {
      SymbolId id = input.declarationSymbols().get(declaration);
      visible.put(id, id);
    }
  }

  private Object declaration(Syntax.Program program, String name) {
    Object declaration = input.declarations().resolveFunction(program, name);
    if (declaration == null) declaration = input.declarations().resolveAggregate(program, name);
    if (declaration == null) declaration = input.declarations().resolveEnum(program, name);
    if (declaration == null) declaration = input.declarations().resolveInterface(program, name);
    return declaration;
  }

  record Input(
      List<Syntax.Program> programs,
      CompilationScope scope,
      Map<SymbolId, Symbol> symbols,
      Map<Object, SymbolId> declarationSymbols,
      Map<Syntax.ImportDecl, SymbolId> importAliases,
      DeclarationCatalog declarations,
      java.util.Set<DocumentId> exportedSources) {
    Input {
      programs = List.copyOf(programs);
      java.util.Objects.requireNonNull(scope, "scope");
      symbols = Collections.unmodifiableMap(new LinkedHashMap<>(symbols));
      declarationSymbols = identityMap(declarationSymbols);
      importAliases = identityMap(importAliases);
      java.util.Objects.requireNonNull(declarations, "declarations");
      exportedSources = java.util.Set.copyOf(exportedSources);
    }

    private static <K, V> Map<K, V> identityMap(Map<K, V> source) {
      return Collections.unmodifiableMap(new IdentityHashMap<>(source));
    }
  }

  record Result(List<SemanticScope> scopes, List<ImportableSymbol> importableSymbols) {
    Result {
      scopes = List.copyOf(scopes);
      importableSymbols = List.copyOf(importableSymbols);
    }
  }
}
