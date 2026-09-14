package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.HashMap;
import java.util.Map;

final class TypeResolutionState {
  private Syntax.Program currentProgram;
  private Map<String, SemanticType> activeTypeParameters = Map.of();
  private Map<String, SymbolId> activeTypeParameterSymbols = Map.of();
  private final Map<String, SemanticType> typeParameterBounds = new HashMap<>();
  private final AnalysisJournal journal = new AnalysisJournal();
  private Scope activeScope;

  Syntax.Program program() {
    return currentProgram;
  }

  Map<String, SemanticType> parameters() {
    return activeTypeParameters;
  }

  Map<String, SymbolId> parameterSymbols() {
    return activeTypeParameterSymbols;
  }

  SemanticType upperBound(String identity) {
    return typeParameterBounds.get(identity);
  }

  void declareBound(String identity, SemanticType type) {
    journal.put(typeParameterBounds, identity, type);
  }

  Scope enterProgram(Syntax.Program program) {
    return enter(program, activeTypeParameters, activeTypeParameterSymbols);
  }

  Scope enterParameters(Map<String, SemanticType> parameters, Map<String, SymbolId> symbols) {
    return enter(currentProgram, parameters, symbols);
  }

  Scope enter(
      Syntax.Program program, Map<String, SemanticType> parameters, Map<String, SymbolId> symbols) {
    var checkedParameters = Map.copyOf(parameters);
    var checkedSymbols = Map.copyOf(symbols);
    var scope = new Scope();
    currentProgram = program;
    activeTypeParameters = checkedParameters;
    activeTypeParameterSymbols = checkedSymbols;
    activeScope = scope;
    return scope;
  }

  Checkpoint checkpoint() {
    return new Checkpoint(
        currentProgram,
        activeTypeParameters,
        activeTypeParameterSymbols,
        journal.checkpoint(),
        activeScope);
  }

  void restore(Checkpoint checkpoint) {
    journal.restore(checkpoint.bounds());
    currentProgram = checkpoint.program();
    activeTypeParameters = checkpoint.parameters();
    activeTypeParameterSymbols = checkpoint.parameterSymbols();
    activeScope = checkpoint.scope();
  }

  final class Scope implements AutoCloseable {
    private final Scope parent = activeScope;
    private final Syntax.Program program = currentProgram;
    private final Map<String, SemanticType> parameters = activeTypeParameters;
    private final Map<String, SymbolId> symbols = activeTypeParameterSymbols;
    private boolean closed;

    private Scope() {}

    @Override
    public void close() {
      if (closed) return;
      if (activeScope != this)
        throw new IllegalStateException("Type resolution scopes must close in nesting order");
      currentProgram = program;
      activeTypeParameters = parameters;
      activeTypeParameterSymbols = symbols;
      activeScope = parent;
      closed = true;
    }
  }

  record Checkpoint(
      Syntax.Program program,
      Map<String, SemanticType> parameters,
      Map<String, SymbolId> parameterSymbols,
      AnalysisJournal.Checkpoint bounds,
      Scope scope) {}
}
