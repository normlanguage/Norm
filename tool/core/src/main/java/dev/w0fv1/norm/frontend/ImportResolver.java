package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.DUPLICATE_NAME;
import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.UNKNOWN_NAME;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ImportResolver {
  Result resolve(Input input) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<SymbolId, Symbol> aliases = new LinkedHashMap<>();
    Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
    Map<Syntax.ImportDecl, SymbolId> importAliases = new IdentityHashMap<>();
    Map<SymbolId, List<SymbolId>> aliasTargets = new LinkedHashMap<>();
    int nextSymbolId = input.nextSymbolId();
    for (Syntax.Program program : input.programs()) {
      Set<String> localNames = new HashSet<>();
      program.enums().forEach(declaration -> localNames.add(declaration.name()));
      program.interfaces().forEach(declaration -> localNames.add(declaration.name()));
      program.aggregates().forEach(declaration -> localNames.add(declaration.name()));
      program.functions().forEach(declaration -> localNames.add(declaration.name()));
      Set<String> importedNames = new HashSet<>();
      for (Syntax.ImportDecl imported : program.imports()) {
        if (!importedNames.add(imported.localName()) || localNames.contains(imported.localName())) {
          diagnostics.add(
              Diagnostic.error(
                  DUPLICATE_NAME,
                  "import name '" + imported.localName() + "' is already declared",
                  imported.span()));
        }
        List<Syntax.FunctionDecl> importedFunctions =
            input.declarations().functions(imported.qualifiedName());
        Object declaration = importedFunctions.isEmpty() ? null : importedFunctions.getFirst();
        if (declaration == null) {
          declaration = input.declarations().declaration(imported.qualifiedName());
        }
        if (declaration == null || !input.declarations().canImport(program, declaration)) {
          diagnostics.add(
              Diagnostic.error(
                  UNKNOWN_NAME,
                  "cannot import inaccessible or unknown declaration '"
                      + imported.qualifiedName()
                      + "'",
                  imported.span()));
          continue;
        }
        Symbol target = input.symbols().get(input.declarationSymbols().get(declaration));
        bindings.put(imported.nameSpan(), target.id());
        if (imported.alias().isEmpty()) continue;
        SymbolId aliasId = SymbolId.source(imported.nameSpan().source().id(), nextSymbolId++);
        Symbol alias =
            new Symbol(
                aliasId,
                imported.localName(),
                target.kind(),
                target.type(),
                java.util.Optional.of(imported.aliasSpan().orElseThrow().location()),
                java.util.Optional.empty(),
                target.typeParameters(),
                target.parameters(),
                "");
        aliases.put(aliasId, alias);
        bindings.put(imported.aliasSpan().orElseThrow(), aliasId);
        importAliases.put(imported, aliasId);
        List<SymbolId> targets =
            importedFunctions.isEmpty()
                ? List.of(target.id())
                : importedFunctions.stream()
                    .filter(candidate -> input.declarations().canImport(program, candidate))
                    .map(input.declarationSymbols()::get)
                    .toList();
        aliasTargets.put(aliasId, targets);
      }
    }
    return new Result(diagnostics, aliases, bindings, importAliases, aliasTargets, nextSymbolId);
  }

  record Input(
      List<Syntax.Program> programs,
      DeclarationCatalog declarations,
      Map<SymbolId, Symbol> symbols,
      Map<Object, SymbolId> declarationSymbols,
      int nextSymbolId) {
    Input {
      programs = List.copyOf(programs);
      java.util.Objects.requireNonNull(declarations, "declarations");
      symbols = Collections.unmodifiableMap(new LinkedHashMap<>(symbols));
      declarationSymbols = Collections.unmodifiableMap(new IdentityHashMap<>(declarationSymbols));
      if (nextSymbolId < 0) throw new IllegalArgumentException("next symbol id cannot be negative");
    }
  }

  record Result(
      List<Diagnostic> diagnostics,
      Map<SymbolId, Symbol> aliases,
      Map<SourceSpan, SymbolId> bindings,
      Map<Syntax.ImportDecl, SymbolId> importAliases,
      Map<SymbolId, List<SymbolId>> aliasTargets,
      int nextSymbolId) {
    Result {
      diagnostics = List.copyOf(diagnostics);
      aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
      bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
      importAliases = Collections.unmodifiableMap(new IdentityHashMap<>(importAliases));
      Map<SymbolId, List<SymbolId>> stableTargets = new LinkedHashMap<>();
      aliasTargets.forEach((alias, targets) -> stableTargets.put(alias, List.copyOf(targets)));
      aliasTargets = Collections.unmodifiableMap(stableTargets);
    }
  }
}
