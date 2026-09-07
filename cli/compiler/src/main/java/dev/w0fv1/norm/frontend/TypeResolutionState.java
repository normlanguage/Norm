package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.HashMap;
import java.util.Map;

final class TypeResolutionState {
  Syntax.Program currentProgram;
  Map<String, SemanticType> activeTypeParameters = Map.of();
  Map<String, SymbolId> activeTypeParameterSymbols = Map.of();
  final Map<String, SemanticType> typeParameterBounds = new HashMap<>();

  Checkpoint checkpoint() {
    return new Checkpoint(
        currentProgram,
        activeTypeParameters,
        activeTypeParameterSymbols,
        Map.copyOf(typeParameterBounds));
  }

  void restore(Checkpoint checkpoint) {
    currentProgram = checkpoint.program();
    activeTypeParameters = checkpoint.parameters();
    activeTypeParameterSymbols = checkpoint.symbols();
    typeParameterBounds.clear();
    typeParameterBounds.putAll(checkpoint.bounds());
  }

  record Checkpoint(
      Syntax.Program program,
      Map<String, SemanticType> parameters,
      Map<String, SymbolId> symbols,
      Map<String, SemanticType> bounds) {}
}
