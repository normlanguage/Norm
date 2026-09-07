package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.BodyAnalysisState.ControlKind;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FlowAnalyzer {
  private final BodyAnalysisState body;
  private final SemanticModelBuilder model;
  private final TypeResolutionState resolution;
  private final DiagnosticBag diagnostics;
  private final java.util.function.Consumer<List<Syntax.Statement>> statementAnalysis;

  FlowAnalyzer(
      BodyAnalysisState body,
      SemanticModelBuilder model,
      TypeResolutionState resolution,
      DiagnosticBag diagnostics,
      java.util.function.Consumer<List<Syntax.Statement>> statementAnalysis) {
    this.body = body;
    this.model = model;
    this.resolution = resolution;
    this.diagnostics = diagnostics;
    this.statementAnalysis = statementAnalysis;
  }

  void declareExisting(String name, SemanticType type, SourceSpan span, SymbolId id) {
    if (!body.flowScopes.declare(name, type, id)) {
      diagnostics.error(DUPLICATE_NAME, "name '" + name + "' is already declared", span);
      return;
    }
    if (type.isReference()) {
      FlowScopes.ScopedSymbol scoped = body.flowScopes.find(name);
      Symbol symbol = model.symbols().get(id);
      body.flowScopes.updateReferenceLifetime(
          scoped,
          symbol != null && symbol.kind() == SymbolKind.PARAMETER
              ? LexicalLifetime.longLived()
              : LexicalLifetime.unusable());
    }
  }

  void declareSelf(SemanticType type, SourceSpan span) {
    SymbolId id = model.allocate(span.source().id());
    Symbol symbol =
        new Symbol(
            id,
            "this",
            SymbolKind.SELF,
            type,
            Optional.empty(),
            Optional.ofNullable(body.currentCallable),
            List.of(),
            List.of(),
            "");
    model.putSymbol(id, symbol);
    declareExisting("this", type, span, symbol.id());
  }

  SemanticType lookup(String name, SourceSpan span) {
    FlowScopes.ScopedSymbol symbol = body.flowScopes.find(name);
    if (symbol != null) {
      model.putBinding(span, symbol.id());
      return body.flowScopes.type(symbol);
    }
    diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
    return SemanticType.DYNAMIC;
  }

  SemanticType lookupDeclared(String name, SourceSpan span) {
    FlowScopes.ScopedSymbol symbol = findScoped(name);
    if (symbol == null) {
      diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
      return SemanticType.DYNAMIC;
    }
    model.putBinding(span, symbol.id());
    return symbol.declaredType();
  }

  FlowScopes.ScopedSymbol findScoped(String name) {
    return body.flowScopes.find(name);
  }

  void invalidateNarrowing(String name) {
    FlowScopes.ScopedSymbol symbol = findScoped(name);
    if (symbol == null) return;
    body.flowScopes.update(symbol, symbol.declaredType());
  }

  Map<String, SemanticType> narrowingsFor(Syntax.Expression condition, boolean truth) {
    if (condition instanceof Syntax.Unary unary && unary.operator() == TokenKind.BANG) {
      return narrowingsFor(unary.operand(), !truth);
    }
    if (condition instanceof Syntax.Binary binary) {
      if ((binary.operator() == TokenKind.AND_AND && truth)
          || (binary.operator() == TokenKind.OR_OR && !truth)) {
        Map<String, SemanticType> result = new LinkedHashMap<>();
        result.putAll(narrowingsFor(binary.left(), truth));
        result.putAll(narrowingsFor(binary.right(), truth));
        return result;
      }
      if (binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL) {
        Syntax.Name name = nullComparedName(binary);
        boolean nonNull =
            (binary.operator() == TokenKind.BANG_EQUAL && truth)
                || (binary.operator() == TokenKind.EQUAL_EQUAL && !truth);
        if (name != null && nonNull) {
          FlowScopes.ScopedSymbol scoped = findScoped(name.value());
          if (scoped != null
              && body.flowScopes.type(scoped).isNullable()
              && isFlowNarrowable(scoped.id())) {
            return Map.of(name.value(), body.flowScopes.type(scoped).nonNullable());
          }
        }
      }
    }
    return Map.of();
  }

  static Syntax.Name nullComparedName(Syntax.Binary binary) {
    if (binary.left() instanceof Syntax.Name name && binary.right() instanceof Syntax.NullLiteral)
      return name;
    if (binary.right() instanceof Syntax.Name name && binary.left() instanceof Syntax.NullLiteral)
      return name;
    return null;
  }

  boolean isFlowNarrowable(SymbolId id) {
    Symbol symbol = model.symbols().get(id);
    return symbol != null
        && (symbol.kind() == SymbolKind.LOCAL_VARIABLE || symbol.kind() == SymbolKind.PARAMETER);
  }

  void applyNarrowings(Map<String, SemanticType> narrowings) {
    for (Map.Entry<String, SemanticType> entry : narrowings.entrySet()) {
      FlowScopes.ScopedSymbol symbol = findScoped(entry.getKey());
      if (symbol != null) {
        body.flowScopes.update(symbol, entry.getValue());
      }
    }
  }

  FlowScopes.FlowState analyzeBranch(
      List<Syntax.Statement> statements,
      Map<String, SemanticType> narrowings,
      FlowScopes.FlowState incoming) {
    replaceFlow(incoming);
    pushScope(scopeSpan(statements));
    applyNarrowings(narrowings);
    statementAnalysis.accept(statements);
    popScope();
    return body.flowScopes.snapshot();
  }

  void analyzeLoop(List<Syntax.Statement> statements, Map<String, SemanticType> narrowings) {
    FlowScopes.FlowState incoming = body.flowScopes.snapshot();
    applyNarrowings(narrowings);
    body.controls.addFirst(ControlContext.loop());
    try {
      statementAnalysis.accept(statements);
      replaceFlow(mergeFlows(incoming, incoming, body.flowScopes.snapshot()));
    } finally {
      body.controls.removeFirst();
    }
  }

  FlowScopes.FlowState mergeFlows(
      FlowScopes.FlowState incoming, FlowScopes.FlowState left, FlowScopes.FlowState right) {
    Map<SymbolId, SemanticType> result = new HashMap<>();
    Map<SymbolId, LexicalLifetime> lifetimes = new HashMap<>();
    for (Map.Entry<SymbolId, SemanticType> entry : incoming.types().entrySet()) {
      SemanticType leftType = left.types().getOrDefault(entry.getKey(), entry.getValue());
      SemanticType rightType = right.types().getOrDefault(entry.getKey(), entry.getValue());
      SemanticType merged = entry.getValue();
      if (leftType.equals(rightType)) {
        merged = leftType;
      } else if (leftType.nonNullable().equals(rightType.nonNullable())) {
        merged = leftType.nonNullable().nullable();
      }
      result.put(entry.getKey(), merged);
      LexicalLifetime incomingLifetime = incoming.referenceLifetimes().get(entry.getKey());
      if (incomingLifetime != null) {
        LexicalLifetime leftLifetime =
            left.referenceLifetimes().getOrDefault(entry.getKey(), incomingLifetime);
        LexicalLifetime rightLifetime =
            right.referenceLifetimes().getOrDefault(entry.getKey(), incomingLifetime);
        lifetimes.put(entry.getKey(), leftLifetime.narrowest(rightLifetime));
      }
    }
    return new FlowScopes.FlowState(result, lifetimes);
  }

  void replaceFlow(FlowScopes.FlowState values) {
    body.flowScopes.replace(values);
  }

  void validateContinue(SourceSpan span) {
    if (body.controls.stream().noneMatch(context -> context.kind() == ControlKind.LOOP)) {
      diagnostics.error(INVALID_CONTROL, "continue is only valid inside for", span);
    }
  }

  void pushScope(SourceSpan span) {
    body.flowScopes.push(span);
  }

  void popScope() {
    body.flowScopes.pop();
  }

  SourceSpan scopeSpan(List<Syntax.Statement> statements) {
    if (statements.isEmpty()) return resolution.currentProgram.span();
    return statements.getFirst().span().cover(statements.getLast().span());
  }
}
