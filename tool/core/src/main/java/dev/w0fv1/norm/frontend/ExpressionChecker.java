package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.frontend.SemanticAnalysisContext.AnalysisCheckpoint;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.ControlContext;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.ControlKind;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.FunctionReferenceResolution;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceCallResolution;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceRequirement;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.SourceCallResolution;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateField;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateView;
import dev.w0fv1.norm.semantic.NumericTypes;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.PatternCoverage;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.LexicalLifetime;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ExpressionChecker {
  private final Analyzer analyzer;

  ExpressionChecker(Analyzer analyzer) {
    this.analyzer = java.util.Objects.requireNonNull(analyzer, "analyzer");
  }

  final SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    analyzer.context.guard.checkpoint();
    SemanticType type =
        switch (expression) {
          case Syntax.IntegerLiteral integer ->
              numericIntegerType(integer.value(), expected, integer.span());
          case Syntax.DecimalLiteral decimal ->
              numericDecimalType(decimal.value(), expected, decimal.span());
          case Syntax.CodePointLiteral ignored -> SemanticType.CODE_POINT;
          case Syntax.BooleanLiteral ignored -> SemanticType.BOOLEAN;
          case Syntax.NullLiteral literal -> analyzeNull(literal, expected);
          case Syntax.StringLiteralExpr ignored -> SemanticType.STRING;
          case Syntax.ArrayLiteral array -> analyzeArray(array, expected);
          case Syntax.Name name -> analyzeNameValue(name, expected);
          case Syntax.Unary unary -> analyzeUnary(unary, expected);
          case Syntax.Binary binary -> analyzeBinary(binary, expected);
          case Syntax.Call call -> analyzeCall(call, expected);
          case Syntax.Member member -> analyzer.memberType(member, expected);
          case Syntax.Lambda lambda -> analyzeLambda(lambda, expected);
          case Syntax.Index index -> analyzeIndex(index);
          case Syntax.SwitchExpression switchExpression ->
              analyzeSwitch(switchExpression, expected);
        };
    if (expected != null && type.equals(SemanticType.DYNAMIC)) {
      type = expected;
    }
    analyzer.context.semanticTypes.put(expression.span(), type);
    if (type.isReference()) {
      analyzer.context.referenceLifetimes.put(expression.span(), referenceLifetime(expression));
    }
    return type;
  }

  LexicalLifetime referenceLifetime(Syntax.Expression expression) {
    LexicalLifetime known = analyzer.context.referenceLifetimes.get(expression.span());
    if (known != null) return known;
    if (expression instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol symbol = analyzer.typeSystem.findScoped(name.value());
      LexicalLifetime lifetime =
          symbol == null ? null : analyzer.context.flowScopes.referenceLifetime(symbol);
      return lifetime == null ? LexicalLifetime.unusable() : lifetime;
    }
    if (expression instanceof Syntax.Unary unary && unary.operator() == TokenKind.AMPERSAND) {
      if (unary.operand() instanceof Syntax.Name name) {
        FlowScopes.ScopedSymbol symbol = analyzer.typeSystem.findScoped(name.value());
        if (symbol != null) {
          SymbolKind kind = scopedSymbol(symbol).kind();
          if (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER) {
            return analyzer.context.flowScopes.storageLifetime(symbol);
          }
          if (kind == SymbolKind.FIELD) return LexicalLifetime.longLived();
        }
      }
      if (unary.operand() instanceof Syntax.Member) {
        return LexicalLifetime.longLived();
      }
    }
    return LexicalLifetime.unusable();
  }

  SemanticType analyzeNameValue(Syntax.Name name, SemanticType expected) {
    FlowScopes.ScopedSymbol scoped = analyzer.typeSystem.findScoped(name.value());
    if (scoped != null) {
      Symbol symbol = scopedSymbol(scoped);
      if (!analyzer.context.lambdaLocals.isEmpty()
          && !analyzer.context.lambdaLocals.getFirst().contains(scoped.id())
          && (symbol.kind() == SymbolKind.LOCAL_VARIABLE
              || symbol.kind() == SymbolKind.PARAMETER
              || symbol.kind() == SymbolKind.SELF)) {
        if (symbol.type().isReference()) {
          analyzer.context.diagnostics.error(
              TYPE_MISMATCH, "ref cannot be captured by a lambda", name.span());
        }
        analyzer.context.capturedLocals.add(scoped.id());
        if (analyzer.context.assignedLocals.contains(scoped.id()))
          reportMutableCapture(scoped.id(), name.span());
      }
      return analyzer.typeSystem.lookup(name.value(), name.span());
    }
    List<Syntax.FunctionDecl> candidates = analyzer.typeSystem.resolveFunctions(name.value());
    if (!candidates.isEmpty()) {
      if (expected == null || !expected.isFunction()) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            "function reference '" + name.value() + "' requires an expected function type",
            name.span());
        return SemanticType.DYNAMIC;
      }
      List<FunctionReferenceResolution> matches =
          candidates.stream()
              .map(
                  candidate ->
                      resolveFunctionReference(candidate, functionType(candidate), expected))
              .flatMap(Optional::stream)
              .toList();
      if (matches.size() != 1) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            matches.isEmpty()
                ? "no overload of '" + name.value() + "' matches " + expected.displayName()
                : "function reference '"
                    + name.value()
                    + "' is ambiguous for "
                    + expected.displayName(),
            name.span());
        return SemanticType.DYNAMIC;
      }
      FunctionReferenceResolution resolution = matches.getFirst();
      Syntax.FunctionDecl selected = resolution.declaration();
      analyzer.typeSystem.bindDeclarationUse(name.span(), name.value(), selected);
      analyzer.context.functionReferenceTypeArguments.put(
          name.span(), resolution.reifiedArguments());
      return expected.nonNullable();
    }
    return analyzer.typeSystem.lookup(name.value(), name.span());
  }

  SemanticType functionType(Syntax.FunctionDecl declaration) {
    Map<String, SemanticType> parameters = analyzer.typeSystem.functionTypeParameters(declaration);
    return SemanticType.function(
        analyzer.functionReturnType(declaration, parameters),
        declaration.parameters().stream()
            .map(
                parameter ->
                    analyzer.typeSystem.resolveDeclarationType(
                        parameter.type(), declaration, parameters))
            .toList());
  }

  final SemanticType functionReturnType(
      Syntax.FunctionDecl declaration, Map<String, SemanticType> typeParameters) {
    return declaration
        .returnType()
        .map(type -> analyzer.typeSystem.resolveDeclarationType(type, declaration, typeParameters))
        .orElseGet(
            () -> {
              Syntax.AggregateDecl owner = analyzer.typeSystem.ownerOf(declaration);
              return owner == null
                  ? SemanticType.VOID
                  : analyzer.typeSystem.aggregateSelfType(owner);
            });
  }

  SemanticType analyzeLambda(Syntax.Lambda lambda, SemanticType expected) {
    SemanticType expectedFunction = expected != null && expected.isFunction() ? expected : null;
    if (expectedFunction != null
        && expectedFunction.functionParameterTypes().size() != lambda.parameters().size()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "lambda requires "
              + expectedFunction.functionParameterTypes().size()
              + " parameter(s), found "
              + lambda.parameters().size(),
          lambda.span());
      expectedFunction = null;
    }
    List<SemanticType> parameterTypes = new ArrayList<>();
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      SemanticType contextual =
          expectedFunction == null ? null : expectedFunction.functionParameterTypes().get(index);
      SemanticType explicit =
          parameter
              .type()
              .map(
                  type -> {
                    analyzer.typeSystem.validateType(type, false);
                    SemanticType resolved =
                        analyzer.typeSystem.resolveType(
                            type, analyzer.context.activeTypeParameters);
                    return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                  })
              .orElse(null);
      if (explicit != null && contextual != null)
        analyzer.typeSystem.requireType(contextual, explicit, parameter.span());
      SemanticType resolved = explicit != null ? explicit : contextual;
      if (resolved == null) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "cannot infer lambda parameter type", parameter.span());
        resolved = SemanticType.DYNAMIC;
      }
      parameterTypes.add(resolved);
    }
    SemanticType previousReturn = analyzer.context.expectedReturnType;
    boolean previousImplicitSelfReturn = analyzer.context.implicitSelfReturn;
    SemanticType declaredContextualReturn =
        lambda
            .returnType()
            .map(
                type -> {
                  analyzer.typeSystem.validateType(type, true);
                  SemanticType resolved =
                      analyzer.typeSystem.resolveType(type, analyzer.context.activeTypeParameters);
                  return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                })
            .orElse(expectedFunction == null ? null : expectedFunction.functionReturnType());
    SemanticType contextualReturn =
        declaredContextualReturn != null
                && declaredContextualReturn.kind() == SemanticType.Kind.TYPE_PARAMETER
            ? null
            : declaredContextualReturn;
    if (lambda.returnType().isPresent() && expectedFunction != null) {
      analyzer.typeSystem.requireType(
          expectedFunction.functionReturnType(),
          declaredContextualReturn,
          lambda.returnType().orElseThrow().span());
    }
    analyzer.context.expectedReturnType =
        contextualReturn == null ? SemanticType.DYNAMIC : contextualReturn;
    analyzer.context.implicitSelfReturn = false;
    analyzer.typeSystem.pushScope(lambda.span());
    Deque<ControlContext> outerControls = new ArrayDeque<>(analyzer.context.controls);
    analyzer.context.controls.clear();
    Set<SymbolId> localSymbols = new HashSet<>();
    analyzer.context.lambdaLocals.addFirst(localSymbols);
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      Symbol symbol =
          analyzer.typeSystem.register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              parameterTypes.get(index),
              parameter.nameSpan(),
              analyzer.context.currentCallable,
              List.of(),
              List.of());
      analyzer.typeSystem.declareExisting(
          parameter.name(), parameterTypes.get(index), parameter.nameSpan(), symbol.id());
      localSymbols.add(symbol.id());
    }
    SemanticType result = contextualReturn;
    int last = lambda.body().size() - 1;
    for (int index = 0; index < lambda.body().size(); index++) {
      Syntax.Statement statement = lambda.body().get(index);
      if (index == last && statement instanceof Syntax.ExpressionStatement expression) {
        result = analyzer.typeOf(expression.expression(), contextualReturn);
        if (contextualReturn != null)
          analyzer.typeSystem.requireAssignable(contextualReturn, result, expression.span());
      } else {
        analyzer.analyzeStatement(statement);
      }
    }
    analyzer.context.controls.addAll(outerControls);
    analyzer.typeSystem.popScope();
    analyzer.context.lambdaLocals.removeFirst();
    analyzer.context.expectedReturnType = previousReturn;
    analyzer.context.implicitSelfReturn = previousImplicitSelfReturn;
    if (result == null) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "lambda return type requires an expected type or a final expression",
          lambda.span());
      result = SemanticType.DYNAMIC;
    }
    if (result.containsReference()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "lambda return type cannot contain ref", lambda.span());
      result = SemanticType.DYNAMIC;
    }
    return SemanticType.function(result, parameterTypes);
  }

  Symbol scopedSymbol(FlowScopes.ScopedSymbol scoped) {
    return analyzer.context.symbols.get(scoped.id());
  }

  void reportMutableCapture(SymbolId symbol, SourceSpan span) {
    if (analyzer.context.reportedMutableCaptures.add(symbol)) {
      analyzer.context.diagnostics.error(
          INVALID_CONTROL,
          "captured local '"
              + analyzer.context.symbols.get(symbol).name()
              + "' must be effectively final",
          span);
    }
  }

  Optional<FunctionReferenceResolution> resolveFunctionReference(
      Syntax.FunctionDecl declaration, SemanticType pattern, SemanticType expected) {
    SemanticType target = expected.nonNullable();
    Symbol symbol =
        analyzer.context.symbols.get(analyzer.context.declarationSymbols.get(declaration));
    if (symbol.typeParameters().isEmpty()) {
      return pattern.equals(target)
          ? Optional.of(new FunctionReferenceResolution(declaration, List.of(), pattern))
          : Optional.empty();
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(
            symbol.typeParameters().stream().map(TypeParameterInfo::type).toList());
    analyzer.typeSystem.constrainInference(solver, pattern, target);
    TypeConstraintSolver.Solution solution = solver.solve();
    if (!solution.missing().isEmpty() || !solution.conflicts().isEmpty()) {
      return Optional.empty();
    }
    List<SemanticType> arguments =
        symbol.typeParameters().stream()
            .map(parameter -> solution.substitutions().get(parameter.type().identity()))
            .toList();
    if (arguments.stream().anyMatch(java.util.Objects::isNull)) return Optional.empty();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      TypeParameterInfo parameter = symbol.typeParameters().get(index);
      SemanticType bound =
          parameter
              .upperBound()
              .map(value -> value.substitute(solution.substitutions()))
              .orElse(null);
      if (bound != null && !analyzer.typeSystem.isAssignable(bound, arguments.get(index)))
        return Optional.empty();
    }
    SemanticType resolved = pattern.substitute(solution.substitutions());
    return resolved.equals(target)
        ? Optional.of(new FunctionReferenceResolution(declaration, arguments, resolved))
        : Optional.empty();
  }

  SemanticType analyzeSwitch(Syntax.SwitchExpression switchExpression, SemanticType expected) {
    SemanticType valueType = analyzer.typeOf(switchExpression.value(), null);
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    PatternCoverage<SemanticType> coverage = new PatternCoverage<>(new SemanticPatternDomain());
    ControlContext context = ControlContext.switchExpression(expected);
    FlowScopes.FlowState incoming = analyzer.context.flowScopes.snapshot();
    List<FlowScopes.FlowState> caseFlows = new ArrayList<>();
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      analyzer.typeSystem.replaceFlow(incoming);
      analyzer.typeSystem.pushScope(switchCase.span());
      PatternCoverage.Pattern pattern = analyzePattern(switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        analyzer.context.diagnostics.error(
            INVALID_CONTROL, "switch case is unreachable", switchCase.pattern().span());
      }
      previous.add(pattern);
      analyzer.context.controls.addFirst(context);
      analyzer.analyzeStatements(switchCase.body());
      analyzer.context.controls.removeFirst();
      analyzer.typeSystem.popScope();
      caseFlows.add(analyzer.context.flowScopes.snapshot());
    }
    if (!caseFlows.isEmpty()) {
      FlowScopes.FlowState merged = caseFlows.getFirst();
      for (int index = 1; index < caseFlows.size(); index++) {
        merged = analyzer.typeSystem.mergeFlows(incoming, merged, caseFlows.get(index));
      }
      analyzer.typeSystem.replaceFlow(merged);
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      analyzer.context.diagnostics.error(
          INVALID_CONTROL, "switch is not exhaustive", switchExpression.span());
    }
    SemanticType result = context.resultType();
    if (result == null) return SemanticType.VOID;
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      if (!definitelyYields(switchCase.body())) {
        analyzer.context.diagnostics.error(
            INVALID_CONTROL, "switch expression case must produce a value", switchCase.span());
      }
    }
    if (result.isReference()) {
      LexicalLifetime lifetime =
          context.referenceLifetime() == null
              ? LexicalLifetime.unusable()
              : context.referenceLifetime();
      LexicalLifetime useLifetime = analyzer.context.flowScopes.currentLifetime();
      if (!lifetime.outlives(useLifetime)) {
        analyzer.context.diagnostics.error(
            INVALID_CONTROL,
            "reference cannot outlive the addressed storage location",
            switchExpression.span());
        lifetime = useLifetime;
      }
      analyzer.context.referenceLifetimes.put(switchExpression.span(), lifetime);
    }
    return result;
  }

  PatternCoverage.Pattern analyzePattern(Syntax.Pattern pattern, SemanticType expected) {
    if (expected.isNullable() && !(pattern instanceof Syntax.NullPattern)) {
      if (pattern instanceof Syntax.WildcardPattern) return PatternCoverage.Pattern.any();
      return PatternCoverage.Pattern.constructor(
          "$value", List.of(analyzeNonNullPattern(pattern, expected.nonNullable())));
    }
    if (pattern instanceof Syntax.NullPattern) {
      if (!expected.isNullable()) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return analyzeNonNullPattern(pattern, expected.nonNullable());
  }

  PatternCoverage.Pattern analyzeNonNullPattern(Syntax.Pattern pattern, SemanticType expected) {
    return switch (pattern) {
      case Syntax.WildcardPattern ignored -> PatternCoverage.Pattern.any();
      case Syntax.BindingPattern binding -> {
        analyzer.typeSystem.validateType(binding.type(), false);
        SemanticType type =
            analyzer.typeSystem.resolveType(binding.type(), analyzer.context.activeTypeParameters);
        if (!type.equals(expected)) {
          analyzer.context.diagnostics.error(
              TYPE_MISMATCH,
              "pattern type " + type.displayName() + " does not match " + expected.displayName(),
              binding.type().span());
        }
        Symbol symbol =
            analyzer.typeSystem.register(
                binding,
                binding.name(),
                SymbolKind.LOCAL_VARIABLE,
                type,
                binding.nameSpan(),
                analyzer.context.currentCallable,
                List.of(),
                List.of());
        analyzer.typeSystem.declareExisting(binding.name(), type, binding.nameSpan(), symbol.id());
        if (!analyzer.context.lambdaLocals.isEmpty())
          analyzer.context.lambdaLocals.getFirst().add(symbol.id());
        yield PatternCoverage.Pattern.any();
      }
      case Syntax.VariantPattern variant -> analyzeVariantPattern(variant, expected);
      case Syntax.IntegerPattern integer -> {
        SemanticType literalType = numericIntegerType(integer.value(), expected, integer.span());
        analyzer.typeSystem.requireType(expected, literalType, integer.span());
        analyzer.context.semanticTypes.put(integer.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? integer.value()
                    : NumericTypes.materialize(integer.value(), literalType)),
            List.of());
      }
      case Syntax.DecimalPattern decimal -> {
        SemanticType literalType = numericDecimalType(decimal.value(), expected, decimal.span());
        analyzer.typeSystem.requireType(expected, literalType, decimal.span());
        analyzer.context.semanticTypes.put(decimal.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? decimal.value()
                    : NumericTypes.materialize(decimal.value(), literalType)),
            List.of());
      }
      case Syntax.CodePointPattern codePoint -> {
        analyzer.typeSystem.requireType(SemanticType.CODE_POINT, expected, codePoint.span());
        yield PatternCoverage.Pattern.constructor("codepoint:" + codePoint.value(), List.of());
      }
      case Syntax.BooleanPattern bool -> {
        analyzer.typeSystem.requireType(SemanticType.BOOLEAN, expected, bool.span());
        yield PatternCoverage.Pattern.constructor("boolean:" + bool.value(), List.of());
      }
      case Syntax.StringPattern string -> {
        analyzer.typeSystem.requireType(SemanticType.STRING, expected, string.span());
        yield PatternCoverage.Pattern.constructor("string:" + string.value(), List.of());
      }
      case Syntax.NullPattern ignored -> {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
        yield PatternCoverage.Pattern.constructor("$null", List.of());
      }
    };
  }

  PatternCoverage.Pattern analyzeVariantPattern(
      Syntax.VariantPattern pattern, SemanticType expected) {
    Syntax.EnumDecl enumDecl = analyzer.typeSystem.resolveEnum(expected);
    if (enumDecl == null) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern requires an enum value, found " + expected.displayName(),
          pattern.span());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    Syntax.EnumVariant variant =
        enumDecl.variants().stream()
            .filter(candidate -> candidate.name().equals(pattern.name()))
            .findFirst()
            .orElse(null);
    if (variant == null) {
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME,
          "enum '" + enumDecl.name() + "' has no variant '" + pattern.name() + "'",
          pattern.nameSpan());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    analyzer.context.bindings.put(
        pattern.nameSpan(), analyzer.context.declarationSymbols.get(variant));
    Map<String, SemanticType> substitutions =
        analyzer.typeSystem.enumSubstitutions(enumDecl, expected);
    List<SemanticType> payloadTypes =
        variant.parameters().stream()
            .map(
                parameter ->
                    analyzer
                        .typeSystem
                        .resolveDeclarationType(
                            parameter.type(),
                            parameter,
                            analyzer.typeSystem.enumTypeParameters(enumDecl))
                        .substitute(substitutions))
            .toList();
    if (pattern.arguments().size() != payloadTypes.size()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern '"
              + pattern.name()
              + "' requires "
              + payloadTypes.size()
              + " argument(s), found "
              + pattern.arguments().size(),
          pattern.span());
    }
    List<PatternCoverage.Pattern> arguments = new ArrayList<>();
    for (int index = 0;
        index < Math.min(pattern.arguments().size(), payloadTypes.size());
        index++) {
      arguments.add(analyzePattern(pattern.arguments().get(index), payloadTypes.get(index)));
    }
    return PatternCoverage.Pattern.constructor("variant:" + variant.name(), arguments);
  }

  void analyzeBreak(Syntax.BreakStatement statement) {
    if (analyzer.context.controls.isEmpty()) {
      analyzer.context.diagnostics.error(
          INVALID_CONTROL, "break is only valid inside for or switch", statement.span());
      if (statement.value() != null) analyzer.typeOf(statement.value(), null);
      return;
    }
    ControlContext context = analyzer.context.controls.getFirst();
    if (context.kind() != ControlKind.SWITCH) {
      if (statement.value() != null) {
        analyzer.context.diagnostics.error(
            INVALID_CONTROL, "loop break cannot produce a value", statement.span());
        analyzer.typeOf(statement.value(), null);
      }
      return;
    }
    if (statement.value() == null) {
      analyzer.context.diagnostics.error(
          INVALID_CONTROL, "switch break must produce a value", statement.span());
      return;
    }
    SemanticType actual = analyzer.typeOf(statement.value(), context.resultType());
    if (actual.isReference()) {
      context.mergeReferenceLifetime(referenceLifetime(statement.value()));
    }
    if (context.resultType() == null || context.resultType().equals(SemanticType.DYNAMIC)) {
      context.setResultType(actual);
    } else {
      analyzer.typeSystem.requireAssignable(context.resultType(), actual, statement.value().span());
    }
  }

  TypeProbe probeType(Syntax.Expression expression, SemanticType expected) {
    AnalysisCheckpoint checkpoint = checkpoint();
    SemanticType type = analyzer.typeOf(expression, expected);
    boolean hasErrors = analyzer.context.diagnostics.hasErrorsSince(checkpoint.diagnosticMark());
    restore(checkpoint);
    return new TypeProbe(type, hasErrors);
  }

  AnalysisCheckpoint checkpoint() {
    return new AnalysisCheckpoint(
        Map.copyOf(analyzer.context.bindings),
        Map.copyOf(analyzer.context.semanticTypes),
        Map.copyOf(analyzer.context.resolvedCalls),
        Map.copyOf(analyzer.context.functionReferenceTypeArguments),
        Map.copyOf(analyzer.context.iterations),
        Map.copyOf(analyzer.context.indexes),
        Map.copyOf(analyzer.context.referenceLifetimes),
        analyzer.context.flowScopes.snapshot(),
        analyzer.context.flowScopes.semanticScopeCount(),
        analyzer.context.diagnostics.mark());
  }

  void restore(AnalysisCheckpoint checkpoint) {
    restore(analyzer.context.bindings, checkpoint.bindings());
    restore(analyzer.context.semanticTypes, checkpoint.semanticTypes());
    restore(analyzer.context.resolvedCalls, checkpoint.resolvedCalls());
    restore(
        analyzer.context.functionReferenceTypeArguments,
        checkpoint.functionReferenceTypeArguments());
    restore(analyzer.context.iterations, checkpoint.iterations());
    restore(analyzer.context.indexes, checkpoint.indexes());
    restore(analyzer.context.referenceLifetimes, checkpoint.referenceLifetimes());
    analyzer.context.flowScopes.replace(checkpoint.flowState());
    analyzer.context.flowScopes.restoreSemanticScopes(checkpoint.semanticScopeCount());
    analyzer.context.diagnostics.rollback(checkpoint.diagnosticMark());
  }

  static <K, V> void restore(Map<K, V> target, Map<K, V> snapshot) {
    target.clear();
    target.putAll(snapshot);
  }

  SemanticType analyzeNull(Syntax.NullLiteral literal, SemanticType expected) {
    if (expected == null || expected.equals(SemanticType.DYNAMIC)) {
      analyzer.context.diagnostics.error(
          UNTYPED_NULL, "null requires an expected nullable type", literal.span());
      return SemanticType.DYNAMIC;
    }
    if (!expected.isNullable()) {
      analyzer.context.diagnostics.error(
          NULLABILITY_MISMATCH,
          "null is not assignable to " + expected.displayName(),
          literal.span());
      return SemanticType.DYNAMIC;
    }
    return expected;
  }

  SemanticType analyzeArray(Syntax.ArrayLiteral array, SemanticType expected) {
    SemanticType expectedArray =
        expected == null
            ? null
            : analyzer
                .context
                .builtins
                .resolveCollectionLiteral(expected)
                .map(value -> value.type())
                .orElse(null);
    SemanticType expectedElement =
        expectedArray != null && expectedArray.arguments().size() == 1
            ? expectedArray.arguments().getFirst()
            : null;
    SemanticType elementType = expectedElement;
    for (Syntax.Expression element : array.elements()) {
      SemanticType current = analyzer.typeOf(element, expectedElement);
      if (elementType == null && !TypeSystem.containsDynamic(current)) {
        elementType = current;
      } else if (elementType != null && !TypeSystem.containsDynamic(current)) {
        if (expectedElement != null) {
          if (!analyzer.typeSystem.isAssignable(elementType, current)) {
            analyzer.context.diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          }
        } else {
          SemanticType common = analyzer.typeSystem.commonType(elementType, current).orElse(null);
          if (common == null) {
            analyzer.context.diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          } else {
            elementType = common;
          }
        }
      }
    }
    SemanticType inferredElement = elementType == null ? SemanticType.DYNAMIC : elementType;
    if (inferredElement.containsReference()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "collection element type cannot contain ref", array.span());
      return SemanticType.DYNAMIC;
    }
    return expectedArray == null
        ? analyzer.context.builtins.instantiate("Array", List.of(inferredElement))
        : expectedArray;
  }

  SemanticType analyzeUnary(Syntax.Unary unary, SemanticType expected) {
    if (unary.operator() == TokenKind.AMPERSAND) return analyzeAddress(unary);
    if (unary.operator() == TokenKind.STAR) {
      SemanticType operand = analyzer.typeOf(unary.operand(), null);
      if (!operand.isReference()) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "dereference requires ref<T>", unary.span());
        return SemanticType.DYNAMIC;
      }
      return operand.referenceTarget();
    }
    SemanticType required =
        unary.operator() == TokenKind.BANG
            ? SemanticType.BOOLEAN
            : NumericTypes.isLeaf(expected == null ? SemanticType.DYNAMIC : expected)
                ? expected.nonNullable()
                : null;
    SemanticType operand = analyzer.typeOf(unary.operand(), required);
    if (unary.operator() == TokenKind.BANG) {
      analyzer.typeSystem.requireType(SemanticType.BOOLEAN, operand, unary.span());
      return SemanticType.BOOLEAN;
    }
    if (!NumericTypes.isLeaf(operand)) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "numeric negation requires a numeric leaf", unary.span());
      return SemanticType.DYNAMIC;
    }
    return operand;
  }

  SemanticType analyzeAddress(Syntax.Unary unary) {
    Syntax.Expression target = unary.operand();
    SemanticType targetType = analyzer.typeOf(target, null);
    boolean addressable = false;
    if (target instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol scoped = analyzer.typeSystem.findScoped(name.value());
      if (scoped != null) {
        SymbolKind kind = scopedSymbol(scoped).kind();
        addressable =
            (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER)
                    && (analyzer.context.lambdaLocals.isEmpty()
                        || analyzer.context.lambdaLocals.getFirst().contains(scoped.id()))
                || kind == SymbolKind.FIELD
                    && analyzer.context.currentAggregate != null
                    && analyzer.context.currentAggregate.kind() != Syntax.AggregateKind.VALUE;
      }
    } else if (target instanceof Syntax.Member member && !member.nullSafe()) {
      SemanticType receiver = analyzer.context.semanticTypes.get(member.receiver().span());
      SymbolId fieldId = analyzer.context.bindings.get(member.nameSpan());
      Symbol field = fieldId == null ? null : analyzer.context.symbols.get(fieldId);
      addressable =
          receiver != null
              && receiver.nonNullable().category() == ValueCategory.IDENTITY
              && field != null
              && field.kind() == SymbolKind.FIELD;
    }
    if (!addressable) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "address-of requires a writable storage location", target.span());
      return SemanticType.DYNAMIC;
    }
    if (targetType.category() != ValueCategory.VALUE) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "ref target must be a value type", target.span());
      return SemanticType.DYNAMIC;
    }
    return SemanticType.reference(targetType);
  }

  SemanticType analyzeBinary(Syntax.Binary binary, SemanticType expected) {
    if (binary.operator() == TokenKind.QUESTION_QUESTION) {
      SemanticType leftExpected =
          expected == null || expected.isReference() ? expected : expected.nullable();
      SemanticType left = analyzer.typeOf(binary.left(), leftExpected);
      if (!left.mayContainNull()) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "left side of ?? must be nullable", binary.left().span());
      }
      SemanticType result = left.equals(SemanticType.DYNAMIC) ? expected : left.nonNullable();
      SemanticType right = analyzer.typeOf(binary.right(), result);
      if (result == null) return right;
      analyzer.typeSystem.requireAssignable(result, right, binary.right().span());
      return result;
    }
    SemanticType left;
    SemanticType right;
    if ((binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL)
        && binary.left() instanceof Syntax.NullLiteral) {
      right = analyzer.typeOf(binary.right(), null);
      left = analyzer.typeOf(binary.left(), right);
    } else {
      SemanticType numericExpected =
          expected != null && NumericTypes.isLeaf(expected) ? expected.nonNullable() : null;
      left = analyzer.typeOf(binary.left(), numericExpected);
      right = null;
    }
    if (right == null) {
      if (binary.operator() == TokenKind.AND_AND) {
        FlowScopes.FlowState incoming = analyzer.context.flowScopes.snapshot();
        analyzer.typeSystem.pushScope(binary.right().span());
        analyzer.typeSystem.applyNarrowings(analyzer.typeSystem.narrowingsFor(binary.left(), true));
        right = analyzer.typeOf(binary.right(), SemanticType.BOOLEAN);
        analyzer.typeSystem.popScope();
        analyzer.typeSystem.replaceFlow(incoming);
      } else if (binary.operator() == TokenKind.OR_OR) {
        FlowScopes.FlowState incoming = analyzer.context.flowScopes.snapshot();
        analyzer.typeSystem.pushScope(binary.right().span());
        analyzer.typeSystem.applyNarrowings(
            analyzer.typeSystem.narrowingsFor(binary.left(), false));
        right = analyzer.typeOf(binary.right(), SemanticType.BOOLEAN);
        analyzer.typeSystem.popScope();
        analyzer.typeSystem.replaceFlow(incoming);
      } else {
        right = analyzer.typeOf(binary.right(), left);
      }
    }
    return switch (binary.operator()) {
      case PLUS -> {
        if (left.equals(SemanticType.STRING) && right.equals(SemanticType.STRING)) {
          yield SemanticType.STRING;
        }
        yield requireNumericLeaves(left, right, binary.span()) ? left : SemanticType.DYNAMIC;
      }
      case MINUS, STAR, SLASH, PERCENT -> {
        yield requireNumericLeaves(left, right, binary.span()) ? left : SemanticType.DYNAMIC;
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        requireNumericLeaves(left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case AND_AND, OR_OR -> {
        analyzer.typeSystem.requireBoth(SemanticType.BOOLEAN, left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        if (!analyzer.typeSystem.isAssignable(left, right)
            && !analyzer.typeSystem.isAssignable(right, left)) {
          analyzer.context.diagnostics.error(
              TYPE_MISMATCH,
              "cannot compare " + left.displayName() + " with " + right.displayName(),
              binary.span());
        }
        yield SemanticType.BOOLEAN;
      }
      default -> SemanticType.DYNAMIC;
    };
  }

  SemanticType numericIntegerType(
      java.math.BigInteger value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.integerLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      analyzer.context.diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  SemanticType numericDecimalType(
      java.math.BigDecimal value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.decimalLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      analyzer.context.diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  boolean requireNumericLeaves(SemanticType left, SemanticType right, SourceSpan span) {
    if (NumericTypes.isLeaf(left) && left.equals(right)) return true;
    analyzer.context.diagnostics.error(
        TYPE_MISMATCH,
        "numeric operands require the same concrete leaf type; found "
            + left.displayName()
            + " and "
            + right.displayName(),
        span);
    return false;
  }

  SemanticType analyzeCall(Syntax.Call call, SemanticType expected) {
    if (call.callee() instanceof Syntax.Name name) {
      return analyzeNamedCall(name, call, expected);
    }
    if (call.callee() instanceof Syntax.Member member) {
      if (member.receiver() instanceof Syntax.Name receiverName
          && (analyzer.typeSystem.resolveEnum(receiverName.value()) != null
              || !analyzer
                  .context
                  .builtins
                  .typeMembers(receiverName.value(), member.name())
                  .isEmpty())) {
        return analyzeMethodCall(member, call, expected, null);
      }
      SemanticType nullableReceiver = analyzer.typeOf(member.receiver(), null);
      SemanticType memberType = memberTypeWithoutDiagnostics(member, nullableReceiver);
      if (memberType != null && memberType.isFunction()) {
        analyzer.context.semanticTypes.put(member.span(), memberType);
        return analyzeFunctionInvocation(call, memberType, analyzer.context.currentCallable);
      }
      return analyzeMethodCall(member, call, expected, nullableReceiver);
    }
    SemanticType calleeType = analyzer.typeOf(call.callee(), null);
    if (calleeType.isFunction())
      return analyzeFunctionInvocation(call, calleeType, analyzer.context.currentCallable);
    analyzer.context.diagnostics.error(
        INVALID_CALL, "expression is not callable", call.callee().span());
    analyzer.typeSystem.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType analyzeFunctionInvocation(Syntax.Call call, SemanticType function, SymbolId target) {
    if (function.isUnknownFunction()) {
      analyzer.context.diagnostics.error(
          INVALID_CALL, "Function<?> has no callable signature", call.callee().span());
      analyzer.typeSystem.analyzeArguments(call.arguments());
      return analyzer.typeSystem.recordCall(
          call,
          call.callee().span(),
          ResolvedCall.Kind.INVOKE,
          target,
          List.of(),
          List.of(),
          SemanticType.DYNAMIC);
    }
    List<ParameterInfo> parameters =
        java.util.stream.IntStream.range(0, function.functionParameterTypes().size())
            .mapToObj(
                index ->
                    new ParameterInfo(
                        "argument" + index, function.functionParameterTypes().get(index)))
            .toList();
    return analyzer.typeSystem.recordCall(
        call,
        call.callee().span(),
        ResolvedCall.Kind.INVOKE,
        target,
        parameters,
        List.of(),
        function.functionReturnType());
  }

  SemanticType memberTypeWithoutDiagnostics(Syntax.Member member, SemanticType nullableReceiver) {
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    Syntax.AggregateDecl owner = analyzer.typeSystem.resolveAggregate(receiver);
    if (owner == null) return null;
    AggregateField resolved = analyzer.typeSystem.aggregateField(receiver, member.name());
    if (resolved == null) return null;
    Syntax.FieldDecl field = resolved.field();
    owner = resolved.view().declaration();
    if (field.visibility() == Syntax.Visibility.PRIVATE
        && analyzer.context.currentAggregate != owner) {
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME,
          "field '"
              + member.name()
              + "' is private in "
              + TypeSystem.aggregateKeyword(owner)
              + " '"
              + owner.name()
              + "'",
          member.nameSpan());
    }
    analyzer.context.bindings.put(
        member.nameSpan(), analyzer.context.declarationSymbols.get(field));
    SemanticType type =
        analyzer
            .typeSystem
            .resolveDeclarationType(
                field.type(), field, analyzer.typeSystem.aggregateTypeParameters(owner))
            .substitute(analyzer.typeSystem.aggregateSubstitutions(owner, resolved.view().type()));
    return safeAccessResult(member, nullableReceiver, type);
  }

  SemanticType analyzeNamedCall(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    String callee = name.value();
    FlowScopes.ScopedSymbol scoped = analyzer.typeSystem.findScoped(callee);
    if (scoped != null && scoped.declaredType().isFunction()) {
      SemanticType function = scoped.declaredType();
      analyzer.context.bindings.put(name.span(), scoped.id());
      analyzer.context.semanticTypes.put(name.span(), function);
      return analyzeFunctionInvocation(call, function, scoped.id());
    }
    analyzer
        .context
        .builtins
        .type(callee)
        .ifPresent(symbol -> analyzer.context.bindings.put(name.span(), symbol.id()));
    List<Symbol> builtinFunctions =
        analyzer.context.builtins.globals(
            callee, analyzer.context.currentProgram.span().source().id());
    if (!builtinFunctions.isEmpty()) {
      if (name.diamond()) {
        analyzer.context.diagnostics.error(
            INVALID_CALL, "diamond is only valid for generic constructors", name.span());
      }
      Symbol symbol =
          analyzer.typeSystem.selectBuiltinOverload(builtinFunctions, call, name.span());
      if (symbol == null) return SemanticType.DYNAMIC;
      analyzer.context.symbols.putIfAbsent(symbol.id(), symbol);
      analyzer.context.bindings.put(name.span(), symbol.id());
      Map<String, SemanticType> substitutions =
          analyzer.typeSystem.inferBuiltinTypeArguments(
              symbol, name.typeArguments(), call, expected, name.span());
      List<ParameterInfo> parameters =
          symbol.parameters().stream()
              .map(
                  parameter ->
                      new ParameterInfo(
                          parameter.name(), parameter.type().substitute(substitutions)))
              .toList();
      List<SemanticType> reifiedArguments =
          symbol.typeParameters().stream()
              .map(parameter -> substitutions.get(parameter.type().identity()))
              .toList();
      return analyzer.typeSystem.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          parameters,
          reifiedArguments,
          symbol.type().substitute(substitutions));
    }
    SemanticType constructedType = analyzer.typeSystem.constructedType(name, call, expected);
    Optional<List<ParameterInfo>> constructor =
        analyzer.context.builtins.constructorParameters(constructedType);
    if (constructor.isPresent()) {
      Symbol target = analyzer.context.builtins.type(callee).orElseThrow();
      return analyzer.typeSystem.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          target.id(),
          constructor.orElseThrow(),
          List.of(),
          constructedType);
    }
    Syntax.AggregateDecl aggregateDecl = analyzer.typeSystem.resolveAggregate(callee);
    if (aggregateDecl != null) {
      analyzer.typeSystem.bindDeclarationUse(name.span(), callee, aggregateDecl);
      OverloadResolver.Candidate selected =
          analyzer.typeSystem.resolveConstructor(aggregateDecl, constructedType, call, name.span());
      if (selected == null) return SemanticType.DYNAMIC;
      SymbolId target =
          selected.target() instanceof Syntax.ConstructorDecl declaration
              ? analyzer.context.declarationSymbols.get(declaration)
              : analyzer.context.declarationSymbols.get(aggregateDecl);
      return analyzer.typeSystem.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CONSTRUCT,
          target,
          selected.parameters(),
          List.of(),
          constructedType);
    }
    List<Syntax.FunctionDecl> functionCandidates = analyzer.typeSystem.resolveFunctions(callee);
    if (!functionCandidates.isEmpty() && name.diamond()) {
      analyzer.context.diagnostics.error(
          INVALID_CALL, "diamond is only valid for generic constructors", name.span());
    }
    SourceCallResolution resolution =
        analyzer.typeSystem.resolveSourceCall(
            functionCandidates,
            name.typeArguments(),
            call,
            expected,
            Map.of(),
            name.span(),
            "function");
    if (resolution != null) {
      analyzer.typeSystem.bindDeclarationUse(name.span(), callee, resolution.declaration());
      return analyzer.typeSystem.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CALLABLE,
          analyzer.context.declarationSymbols.get(resolution.declaration()),
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (!functionCandidates.isEmpty()) return SemanticType.DYNAMIC;
    analyzer.context.diagnostics.error(
        UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
    analyzer.typeSystem.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private boolean isReflectableType(SemanticType type) {
    SemanticType value = type.nonNullable();
    return value.kind() == SemanticType.Kind.TYPE_PARAMETER
        || value.kind() == SemanticType.Kind.DECLARED && !value.isFunction();
  }

  SemanticType analyzeMethodCall(
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected,
      SemanticType analyzedReceiver) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = analyzer.typeSystem.resolveEnum(enumName.value());
      if (enumDecl != null) {
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant == null) {
          analyzer.context.diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no variant '" + member.name() + "'",
              member.nameSpan());
          analyzer.typeSystem.analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        Symbol variantSymbol =
            analyzer.context.symbols.get(analyzer.context.declarationSymbols.get(variant));
        analyzer.context.bindings.put(
            enumName.span(), analyzer.context.declarationSymbols.get(enumDecl));
        analyzer.context.bindings.put(member.nameSpan(), variantSymbol.id());
        Map<String, SemanticType> substitutions =
            analyzer.typeSystem.inferBuiltinTypeArguments(
                variantSymbol, enumName.typeArguments(), call, expected, member.span());
        List<ParameterInfo> parameters =
            variantSymbol.parameters().stream()
                .map(
                    parameter ->
                        new ParameterInfo(
                            parameter.name(), parameter.type().substitute(substitutions)))
                .toList();
        SemanticType result = variantSymbol.type().substitute(substitutions);
        List<SemanticType> reifiedArguments =
            variantSymbol.typeParameters().stream()
                .map(TypeParameterInfo::type)
                .map(parameter -> substitutions.get(parameter.identity()))
                .toList();
        return analyzer.typeSystem.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.ENUM_CONSTRUCT,
            variantSymbol.id(),
            parameters,
            reifiedArguments,
            result);
      }
    }
    if (member.receiver() instanceof Syntax.Name typeName) {
      List<Symbol> typeMethods =
          analyzer.context.builtins.typeMembers(typeName.value(), member.name());
      if (!typeMethods.isEmpty()) {
        analyzer
            .context
            .builtins
            .type(typeName.value())
            .ifPresent(symbol -> analyzer.context.bindings.put(typeName.span(), symbol.id()));
        Symbol symbol =
            analyzer.typeSystem.selectBuiltinOverload(typeMethods, call, member.nameSpan());
        if (symbol == null) return SemanticType.DYNAMIC;
        analyzer.context.bindings.put(member.nameSpan(), symbol.id());
        Map<String, SemanticType> substitutions =
            analyzer.typeSystem.inferBuiltinTypeArguments(
                symbol, member.typeArguments(), call, expected, member.span());
        List<ParameterInfo> parameters =
            symbol.parameters().stream()
                .map(
                    parameter ->
                        new ParameterInfo(
                            parameter.name(), parameter.type().substitute(substitutions)))
                .toList();
        SemanticType result = symbol.type().substitute(substitutions);
        List<SemanticType> reifiedArguments =
            symbol.typeParameters().stream()
                .map(TypeParameterInfo::type)
                .map(parameter -> substitutions.get(parameter.identity()))
                .toList();
        return analyzer.typeSystem.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.INTRINSIC,
            symbol.id(),
            parameters,
            reifiedArguments,
            result);
      }
    }
    SemanticType nullableReceiver =
        analyzedReceiver == null ? analyzer.typeOf(member.receiver(), null) : analyzedReceiver;
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    if (member.name().isEmpty()) {
      analyzer.typeSystem.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    List<Symbol> builtinMembers = analyzer.context.builtins.members(receiver, member.name());
    if (!builtinMembers.isEmpty()) {
      List<Symbol> builtinMethods =
          builtinMembers.stream().filter(symbol -> symbol.kind() == SymbolKind.METHOD).toList();
      if (builtinMethods.isEmpty()) {
        analyzer.context.diagnostics.error(
            UNKNOWN_NAME,
            "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
            call.span());
        analyzer.typeSystem.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      Symbol symbol =
          analyzer.typeSystem.selectBuiltinOverload(builtinMethods, call, member.nameSpan());
      if (symbol == null) return SemanticType.DYNAMIC;
      analyzer.context.bindings.put(member.nameSpan(), symbol.id());
      Map<String, SemanticType> substitutions =
          analyzer.typeSystem.inferBuiltinTypeArguments(
              symbol,
              member.typeArguments(),
              call,
              callableExpected(member, nullableReceiver, expected),
              member.span());
      List<SemanticType> reifiedArguments =
          symbol.typeParameters().stream()
              .map(parameter -> substitutions.get(parameter.type().identity()))
              .toList();
      dev.w0fv1.norm.abi.IntrinsicId intrinsic =
          analyzer.context.builtins.intrinsic(symbol.id()).orElse(null);
      if ((intrinsic == dev.w0fv1.norm.abi.IntrinsicId.CLASS_ANNOTATION
              || intrinsic == dev.w0fv1.norm.abi.IntrinsicId.FIELD_ANNOTATION)
          && !reifiedArguments.isEmpty()
          && analyzer.typeSystem.resolveAnnotation(reifiedArguments.getFirst().nonNullable())
              == null) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "annotation query requires an annotation type", member.span());
      }
      List<ParameterInfo> parameters =
          symbol.parameters().stream()
              .map(
                  parameter ->
                      new ParameterInfo(
                          parameter.name(), parameter.type().substitute(substitutions)))
              .toList();
      return analyzer.typeSystem.recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          parameters,
          reifiedArguments,
          safeAccessResult(member, nullableReceiver, symbol.type().substitute(substitutions)));
    }
    Syntax.AggregateDecl aggregateDecl = analyzer.typeSystem.resolveAggregate(receiver);
    if (aggregateDecl != null) {
      if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE && member.name().equals("copy")) {
        analyzer.context.bindings.put(
            member.nameSpan(), analyzer.context.copyMethods.get(receiver.identity()));
        analyzer.typeSystem.validateTypeArgumentCount(
            member.name(), 0, member.typeArguments(), member.span());
        member
            .typeArguments()
            .forEach(
                argument ->
                    analyzer.typeSystem.resolveCheckedType(
                        argument, analyzer.context.activeTypeParameters));
        return analyzer.typeSystem.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.COPY,
            analyzer.context.copyMethods.get(receiver.identity()),
            List.of(),
            List.of(),
            safeAccessResult(member, nullableReceiver, receiver));
      }
      boolean foundMethod = false;
      boolean foundAccessibleMethod = false;
      for (AggregateView view : analyzer.typeSystem.aggregateViews(receiver)) {
        List<Syntax.FunctionDecl> methods =
            view.declaration().methods().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .toList();
        foundMethod |= !methods.isEmpty();
        List<Syntax.FunctionDecl> accessibleMethods =
            methods.stream()
                .filter(
                    candidate ->
                        candidate.visibility() != Syntax.Visibility.PRIVATE
                            || analyzer.context.currentAggregate == view.declaration())
                .toList();
        foundAccessibleMethod |= !accessibleMethods.isEmpty();
        Map<String, SemanticType> substitutions =
            analyzer.typeSystem.aggregateSubstitutions(view.declaration(), view.type());
        boolean structuralMatch =
            accessibleMethods.stream()
                .anyMatch(
                    candidate ->
                        analyzer.context.overloads.argumentIndices(
                                call,
                                analyzer.typeSystem.parametersOf(candidate, substitutions),
                                false)
                            != null);
        if (!structuralMatch) continue;
        SourceCallResolution resolution =
            analyzer.typeSystem.resolveSourceCall(
                accessibleMethods,
                member.typeArguments(),
                call,
                callableExpected(member, nullableReceiver, expected),
                substitutions,
                member.nameSpan(),
                "method");
        if (resolution == null) return SemanticType.DYNAMIC;
        Syntax.FunctionDecl method = resolution.declaration();
        analyzer.context.bindings.put(
            member.nameSpan(), analyzer.context.declarationSymbols.get(method));
        return analyzer.typeSystem.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.CALLABLE,
            analyzer.context.declarationSymbols.get(method),
            resolution.parameters(),
            resolution.reifiedArguments(),
            safeAccessResult(member, nullableReceiver, resolution.result()));
      }
      if (foundMethod) {
        if (!foundAccessibleMethod) {
          analyzer.context.diagnostics.error(
              UNKNOWN_NAME,
              "method '"
                  + member.name()
                  + "' is private in "
                  + TypeSystem.aggregateKeyword(aggregateDecl)
                  + " '"
                  + aggregateDecl.name()
                  + "'",
              member.nameSpan());
          analyzer.typeSystem.analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        analyzer.context.diagnostics.error(
            INVALID_CALL, "no method overload accepts the supplied arguments", call.span());
        analyzer.typeSystem.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
    }
    List<InterfaceRequirement> interfaceMethods =
        interfaceRequirements(receiver).stream()
            .filter(requirement -> requirement.method().name().equals(member.name()))
            .toList();
    OverloadResolver.Candidate selectedInterfaceMethod =
        analyzer.context.overloads.select(
            interfaceMethods.stream()
                .map(
                    requirement ->
                        new OverloadResolver.Candidate(requirement, requirement.parameters()))
                .toList(),
            call,
            member.nameSpan());
    InterfaceRequirement interfaceMethod =
        selectedInterfaceMethod == null
            ? null
            : (InterfaceRequirement) selectedInterfaceMethod.target();
    if (!interfaceMethods.isEmpty() && interfaceMethod == null) return SemanticType.DYNAMIC;
    if (interfaceMethod != null) {
      Symbol target =
          analyzer.context.symbols.get(
              analyzer.context.declarationSymbols.get(interfaceMethod.method()));
      InterfaceCallResolution interfaceResolution =
          resolveInterfaceCall(interfaceMethod, target, member, call, expected);
      analyzer.context.bindings.put(member.nameSpan(), target.id());
      return analyzer.typeSystem.recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTERFACE_CALL,
          target.id(),
          interfaceResolution.parameters(),
          interfaceResolution.reifiedArguments(),
          safeAccessResult(member, nullableReceiver, interfaceResolution.result()));
    }
    List<Syntax.FunctionDecl> extensions =
        analyzer.typeSystem.resolveFunctions(member.name()).stream()
            .filter(candidate -> candidate.kind() == Syntax.FunctionKind.EXTENSION)
            .toList();
    if (!extensions.isEmpty()) {
      if (member.nullSafe()) {
        analyzer.context.diagnostics.error(
            INVALID_CALL, "null-safe extension calls are not supported", member.span());
        analyzer.typeSystem.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      SourceCallResolution resolution =
          analyzer.typeSystem.resolveExtensionCall(
              extensions,
              member.typeArguments(),
              member.receiver(),
              call,
              expected,
              member.nameSpan());
      if (resolution == null) return SemanticType.DYNAMIC;
      Syntax.FunctionDecl extension = resolution.declaration();
      SymbolId target = analyzer.context.declarationSymbols.get(extension);
      analyzer.context.bindings.put(member.nameSpan(), target);
      return analyzer.typeSystem.recordExtensionCall(
          member,
          call,
          target,
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (analyzer.context.builtins.isType(receiver.name())) {
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
          member.span());
      analyzer.typeSystem.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    analyzer.context.diagnostics.error(
        TYPE_MISMATCH, "type '" + receiver.displayName() + "' has no methods", member.span());
    analyzer.typeSystem.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  InterfaceCallResolution resolveInterfaceCall(
      InterfaceRequirement requirement,
      Symbol method,
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected) {
    Map<String, SemanticType> substitutions =
        new LinkedHashMap<>(
            analyzer.interfaceSubstitutions(requirement.owner(), requirement.receiver()));
    List<TypeConstraintSolver.Conflict> inferenceConflicts = List.of();
    List<SemanticType> explicit =
        member.typeArguments().stream()
            .map(
                argument ->
                    analyzer.typeSystem.resolveCheckedType(
                        argument, analyzer.context.activeTypeParameters))
            .toList();
    if (!explicit.isEmpty()) {
      analyzer.typeSystem.validateTypeArgumentCount(
          member.name(), method.typeParameters().size(), member.typeArguments(), member.span());
      for (int index = 0;
          index < Math.min(explicit.size(), method.typeParameters().size());
          index++) {
        substitutions.put(
            method.typeParameters().get(index).type().identity(), explicit.get(index));
      }
    } else {
      TypeConstraintSolver solver =
          new TypeConstraintSolver(
              method.typeParameters().stream().map(TypeParameterInfo::type).toList());
      Set<String> variables = TypeSystem.solverVariables(method.typeParameters());
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        analyzer.typeSystem.constrainInference(
            solver, requirement.result().substitute(substitutions), expected);
      }
      Map<String, SemanticType> contextualSubstitutions = new LinkedHashMap<>(substitutions);
      contextualSubstitutions.putAll(solver.solve().substitutions());
      List<Integer> indices =
          analyzer.context.overloads.argumentIndices(call, requirement.parameters(), false);
      if (indices != null) {
        for (int index = 0; index < call.arguments().size(); index++) {
          Syntax.Expression argument = call.arguments().get(index).value();
          SemanticType inferencePattern =
              requirement.parameters().get(indices.get(index)).type().substitute(substitutions);
          SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
          SemanticType argumentExpected =
              TypeSystem.containsTypeParameter(pattern, variables)
                      && !(argument instanceof Syntax.Lambda && pattern.isFunction())
                  ? null
                  : pattern;
          analyzer.typeSystem.constrainInference(
              solver, inferencePattern, analyzer.typeOf(argument, argumentExpected));
        }
      }
      TypeConstraintSolver.Solution inferred = solver.solve();
      substitutions.putAll(inferred.substitutions());
      inferenceConflicts = inferred.conflicts();
    }
    List<SemanticType> reified = new ArrayList<>();
    for (TypeParameterInfo parameter : method.typeParameters()) {
      SemanticType argument = substitutions.get(parameter.type().identity());
      if (argument == null) {
        analyzer.context.diagnostics.error(
            INVALID_CALL, "cannot infer type argument '" + parameter.name() + "'", member.span());
        argument = SemanticType.DYNAMIC;
        substitutions.put(parameter.type().identity(), argument);
      }
      SemanticType bound =
          parameter.upperBound().map(value -> value.substitute(substitutions)).orElse(null);
      if (bound != null && !analyzer.typeSystem.isAssignable(bound, argument)) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            "type argument '"
                + argument.displayName()
                + "' does not satisfy bound '"
                + bound.displayName()
                + "' for '"
                + parameter.name()
                + "'",
            member.span());
      }
      reified.add(argument);
    }
    Map<String, String> parameterNames =
        method.typeParameters().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    parameter -> parameter.type().identity(),
                    TypeParameterInfo::name,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (TypeConstraintSolver.Conflict conflict : inferenceConflicts) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + parameterNames.get(conflict.variable())
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          member.span());
    }
    List<ParameterInfo> parameters =
        requirement.parameters().stream()
            .map(value -> new ParameterInfo(value.name(), value.type().substitute(substitutions)))
            .toList();
    return new InterfaceCallResolution(
        parameters, requirement.result().substitute(substitutions), reified);
  }

  SemanticType memberType(Syntax.Member member, SemanticType expected) {
    SemanticType declarationReference = declarationReferenceType(member, expected);
    if (declarationReference != null) return declarationReference;
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = analyzer.typeSystem.resolveEnum(enumName.value());
      if (enumDecl != null) {
        analyzer.context.bindings.put(
            enumName.span(), analyzer.context.declarationSymbols.get(enumDecl));
        enumDecl.variants().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .map(analyzer.context.declarationSymbols::get)
            .ifPresent(id -> analyzer.context.bindings.put(member.nameSpan(), id));
        if (enumDecl.variants().stream().noneMatch(value -> value.name().equals(member.name()))) {
          analyzer.context.diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no member '" + member.name() + "'",
              member.span());
        }
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(value -> value.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant != null && !variant.parameters().isEmpty()) {
          analyzer.context.diagnostics.error(
              INVALID_CALL,
              "enum variant '" + member.name() + "' requires construction arguments",
              member.span());
        }
        return analyzer.typeSystem.appliedType(
            enumDecl.name(), enumName.typeArguments(), enumName.span());
      }
    }
    SemanticType nullableReceiverType = analyzer.typeOf(member.receiver(), null);
    SemanticType receiverType = accessibleReceiverType(member, nullableReceiverType);
    if (member.name().isEmpty()) return SemanticType.DYNAMIC;
    Optional<Symbol> builtinMember = analyzer.context.builtins.member(receiverType, member.name());
    if (builtinMember.isPresent() && builtinMember.orElseThrow().kind() != SymbolKind.METHOD) {
      Symbol symbol = builtinMember.orElseThrow();
      analyzer.context.bindings.put(member.nameSpan(), symbol.id());
      return safeAccessResult(member, nullableReceiverType, symbol.type());
    }
    if (analyzer.context.builtins.isType(receiverType.name())) {
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiverType.displayName() + "' has no field '" + member.name() + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    Syntax.AggregateDecl aggregateDecl = analyzer.typeSystem.resolveAggregate(receiverType);
    if (aggregateDecl != null) {
      AggregateField resolved = analyzer.typeSystem.aggregateField(receiverType, member.name());
      if (resolved != null) {
        Syntax.FieldDecl field = resolved.field();
        Syntax.AggregateDecl owner = resolved.view().declaration();
        if (field.visibility() == Syntax.Visibility.PRIVATE
            && analyzer.context.currentAggregate != owner) {
          analyzer.context.diagnostics.error(
              UNKNOWN_NAME,
              "field '"
                  + member.name()
                  + "' is private in "
                  + TypeSystem.aggregateKeyword(owner)
                  + " '"
                  + owner.name()
                  + "'",
              member.nameSpan());
        }
        analyzer.context.bindings.put(
            member.nameSpan(), analyzer.context.declarationSymbols.get(field));
        SemanticType result =
            analyzer
                .typeSystem
                .resolveDeclarationType(
                    field.type(), field, analyzer.typeSystem.aggregateTypeParameters(owner))
                .substitute(
                    analyzer.typeSystem.aggregateSubstitutions(owner, resolved.view().type()));
        return safeAccessResult(member, nullableReceiverType, result);
      }
      SemanticType method = boundMethodType(member, receiverType, expected);
      if (method != null) return method;
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME,
          TypeSystem.aggregateKeyword(aggregateDecl)
              + " '"
              + receiverType.displayName()
              + "' has no field '"
              + member.name()
              + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    analyzer.context.diagnostics.error(
        TYPE_MISMATCH,
        "type '" + receiverType.displayName() + "' has no member '" + member.name() + "'",
        member.span());
    return SemanticType.DYNAMIC;
  }

  private SemanticType declarationReferenceType(Syntax.Member member, SemanticType expected) {
    if (!member.typeArguments().isEmpty()) return null;
    if (member.name().equals("class") && member.receiver() instanceof Syntax.Name typeName) {
      if (typeName.diamond()) {
        analyzer.context.diagnostics.error(
            INVALID_CALL, "class reference cannot use diamond inference", typeName.span());
        return SemanticType.DYNAMIC;
      }
      SemanticType reflected = referencedType(typeName, member.nullSafe());
      if (!isReflectableType(reflected)) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "class reference requires a nominal type", member.span());
        return SemanticType.DYNAMIC;
      }
      SymbolId target = analyzer.context.bindings.get(typeName.span());
      if (target != null) analyzer.context.bindings.put(member.nameSpan(), target);
      return analyzer.context.builtins.instantiate("Class", List.of(reflected));
    }
    if (member.nullSafe()) return null;
    if (member.name().equals("field")
        && member.receiver() instanceof Syntax.Member selected
        && selected.receiver() instanceof Syntax.Name ownerName) {
      SemanticType ownerType = referencedType(ownerName);
      AggregateField field = analyzer.typeSystem.aggregateField(ownerType, selected.name());
      if (field == null) {
        analyzer.context.diagnostics.error(
            UNKNOWN_NAME,
            "type '" + ownerType.displayName() + "' has no field '" + selected.name() + "'",
            selected.span());
        return SemanticType.DYNAMIC;
      }
      Syntax.FieldDecl declaration = field.field();
      if (declaration.visibility() == Syntax.Visibility.PRIVATE
          && analyzer.context.currentAggregate != field.view().declaration()) {
        analyzer.context.diagnostics.error(
            UNKNOWN_NAME, "field '" + selected.name() + "' is private", selected.nameSpan());
      }
      SymbolId fieldId = analyzer.context.declarationSymbols.get(declaration);
      analyzer.context.bindings.put(selected.nameSpan(), fieldId);
      analyzer.context.bindings.put(member.nameSpan(), fieldId);
      SemanticType valueType =
          analyzer
              .typeSystem
              .resolveDeclarationType(
                  declaration.type(),
                  declaration,
                  analyzer.typeSystem.aggregateTypeParameters(field.view().declaration()))
              .substitute(
                  analyzer.typeSystem.aggregateSubstitutions(
                      field.view().declaration(), field.view().type()));
      return analyzer.context.builtins.instantiate("Field", List.of(ownerType, valueType));
    }
    if (!member.name().equals("function")) return null;
    if (member.receiver() instanceof Syntax.Name functionName) {
      return topLevelFunctionReference(member, functionName, expected);
    }
    if (member.receiver() instanceof Syntax.Member selected
        && selected.receiver() instanceof Syntax.Name ownerName) {
      return unboundMethodReference(member, selected, ownerName, expected);
    }
    return null;
  }

  private SemanticType referencedType(Syntax.Name name) {
    return referencedType(name, false);
  }

  private SemanticType referencedType(Syntax.Name name, boolean nullable) {
    Syntax.TypeRef reference =
        new Syntax.TypeRef(name.value(), name.typeArguments(), nullable, name.span());
    return analyzer.typeSystem.resolveCheckedType(reference, analyzer.context.activeTypeParameters);
  }

  private SemanticType topLevelFunctionReference(
      Syntax.Member member, Syntax.Name name, SemanticType expected) {
    List<Syntax.FunctionDecl> candidates = analyzer.typeSystem.resolveFunctions(name.value());
    if (candidates.isEmpty()) {
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME, "cannot find function '" + name.value() + "'", name.span());
      return SemanticType.DYNAMIC;
    }
    List<FunctionReferenceResolution> matches =
        selectFunctionReferences(
            candidates.stream()
                .map(candidate -> new FunctionPattern(candidate, functionType(candidate)))
                .toList(),
            expected);
    return bindFunctionReference(member, name.span(), name.value(), matches, expected);
  }

  private SemanticType unboundMethodReference(
      Syntax.Member member, Syntax.Member selected, Syntax.Name ownerName, SemanticType expected) {
    SemanticType ownerType = referencedType(ownerName);
    List<FunctionPattern> candidates = new ArrayList<>();
    for (AggregateView view : analyzer.typeSystem.aggregateViews(ownerType)) {
      Map<String, SemanticType> substitutions =
          analyzer.typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FunctionDecl method : view.declaration().methods()) {
        if (!method.name().equals(selected.name())) continue;
        if (method.visibility() == Syntax.Visibility.PRIVATE
            && analyzer.context.currentAggregate != view.declaration()) continue;
        Map<String, SemanticType> parameters =
            analyzer.typeSystem.typeParameters(method, view.declaration());
        List<SemanticType> signature = new ArrayList<>();
        signature.add(view.type());
        method.parameters().stream()
            .map(
                parameter ->
                    analyzer.typeSystem.resolveDeclarationType(
                        parameter.type(), method, parameters))
            .map(type -> type.substitute(substitutions))
            .forEach(signature::add);
        SemanticType result =
            analyzer.functionReturnType(method, parameters).substitute(substitutions);
        candidates.add(new FunctionPattern(method, SemanticType.function(result, signature)));
      }
      if (!candidates.isEmpty()) break;
    }
    List<FunctionReferenceResolution> matches = selectFunctionReferences(candidates, expected);
    SemanticType type =
        bindFunctionReference(member, selected.nameSpan(), selected.name(), matches, expected);
    if (!type.equals(SemanticType.DYNAMIC)) {
      analyzer.context.bindings.put(
          member.nameSpan(), analyzer.context.bindings.get(selected.nameSpan()));
    }
    return type;
  }

  private SemanticType boundMethodType(
      Syntax.Member member, SemanticType receiver, SemanticType expected) {
    List<FunctionPattern> candidates = new ArrayList<>();
    for (AggregateView view : analyzer.typeSystem.aggregateViews(receiver)) {
      Map<String, SemanticType> substitutions =
          analyzer.typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FunctionDecl method : view.declaration().methods()) {
        if (!method.name().equals(member.name())) continue;
        if (method.visibility() == Syntax.Visibility.PRIVATE
            && analyzer.context.currentAggregate != view.declaration()) continue;
        Map<String, SemanticType> parameters =
            analyzer.typeSystem.typeParameters(method, view.declaration());
        SemanticType pattern =
            SemanticType.function(
                    analyzer.functionReturnType(method, parameters),
                    method.parameters().stream()
                        .map(
                            parameter ->
                                analyzer.typeSystem.resolveDeclarationType(
                                    parameter.type(), method, parameters))
                        .toList())
                .substitute(substitutions);
        candidates.add(new FunctionPattern(method, pattern));
      }
      if (!candidates.isEmpty()) break;
    }
    if (candidates.isEmpty()) return null;
    List<FunctionReferenceResolution> matches = selectFunctionReferences(candidates, expected);
    return bindFunctionReference(member, member.nameSpan(), member.name(), matches, expected);
  }

  private List<FunctionReferenceResolution> selectFunctionReferences(
      List<FunctionPattern> candidates, SemanticType expected) {
    if (expected == null || expected.isUnknownFunction()) {
      return candidates.size() == 1
          ? List.of(
              new FunctionReferenceResolution(
                  candidates.getFirst().declaration(), List.of(), candidates.getFirst().type()))
          : List.of();
    }
    if (!expected.isFunction()) return List.of();
    return candidates.stream()
        .map(
            candidate ->
                resolveFunctionReference(candidate.declaration(), candidate.type(), expected))
        .flatMap(Optional::stream)
        .toList();
  }

  private SemanticType bindFunctionReference(
      Syntax.Member member,
      SourceSpan targetSpan,
      String name,
      List<FunctionReferenceResolution> matches,
      SemanticType expected) {
    if (matches.size() != 1) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          matches.isEmpty()
              ? "function reference '" + name + "' requires an unambiguous exact function type"
              : "function reference '" + name + "' is ambiguous",
          member.span());
      return SemanticType.DYNAMIC;
    }
    FunctionReferenceResolution resolution = matches.getFirst();
    analyzer.context.bindings.put(
        targetSpan, analyzer.context.declarationSymbols.get(resolution.declaration()));
    analyzer.context.bindings.put(
        member.nameSpan(), analyzer.context.declarationSymbols.get(resolution.declaration()));
    analyzer.context.functionReferenceTypeArguments.put(
        member.span(), resolution.reifiedArguments());
    return resolution.functionType();
  }

  private record FunctionPattern(Syntax.FunctionDecl declaration, SemanticType type) {}

  List<InterfaceRequirement> interfaceRequirements(SemanticType receiver) {
    SemanticType interfaceType = receiver;
    if (receiver.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      interfaceType = analyzer.context.typeParameterBounds.get(receiver.identity());
    }
    if (interfaceType == null) return List.of();
    Syntax.InterfaceDecl root = analyzer.typeSystem.resolveInterface(interfaceType);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    if (root != null) {
      analyzer.collectConformances(
          root, interfaceType, conformances, analyzer.context.currentProgram.span());
    } else {
      for (Syntax.InterfaceDecl declaration : analyzer.context.declarations.interfaces()) {
        String identity =
            analyzer
                .context
                .symbols
                .get(analyzer.context.declarationSymbols.get(declaration))
                .type()
                .identity();
        conformanceTo(interfaceType, identity)
            .ifPresent(value -> conformances.putIfAbsent(value.identity(), value));
      }
    }
    if (conformances.isEmpty()) return List.of();
    Map<String, InterfaceRequirement> result = new LinkedHashMap<>();
    for (SemanticType conformance : conformances.values()) {
      Syntax.InterfaceDecl declaration = analyzer.typeSystem.resolveInterface(conformance);
      if (declaration == null) continue;
      analyzer
          .directRequirements(declaration, conformance)
          .forEach(requirement -> result.putIfAbsent(requirement.key(), requirement));
    }
    return mostSpecificRequirements(List.copyOf(result.values()));
  }

  final List<InterfaceRequirement> mostSpecificRequirements(
      List<InterfaceRequirement> requirements) {
    return requirements.stream()
        .filter(
            requirement ->
                requirements.stream()
                    .noneMatch(
                        candidate ->
                            candidate != requirement
                                && requirementShape(candidate).equals(requirementShape(requirement))
                                && analyzer.typeSystem.isAssignable(
                                    requirement.receiver(), candidate.receiver())
                                && !analyzer.typeSystem.isAssignable(
                                    candidate.receiver(), requirement.receiver())))
        .toList();
  }

  final String requirementShape(InterfaceRequirement requirement) {
    Symbol symbol =
        analyzer.context.symbols.get(analyzer.context.declarationSymbols.get(requirement.method()));
    Map<String, String> typeParameters = new LinkedHashMap<>();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      typeParameters.put(symbol.typeParameters().get(index).type().identity(), "$" + index);
    }
    return requirement.method().name()
        + "("
        + requirement.parameters().stream()
            .map(
                parameter ->
                    parameter.name() + ":" + semanticTypeShape(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")->"
        + semanticTypeShape(requirement.result(), typeParameters);
  }

  private static String semanticTypeShape(SemanticType type, Map<String, String> typeParameters) {
    String identity = typeParameters.getOrDefault(type.identity(), type.identity());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> semanticTypeShape(argument, typeParameters))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"));
    return identity + arguments + (type.isNullable() ? "?" : "");
  }

  Optional<ResolvedIteration> resolveInterfaceIteration(SemanticType iterableType) {
    if (analyzer.context.builtins.resolveIterable(iterableType).isPresent())
      return Optional.empty();
    SemanticType iterableInterface = conformanceTo(iterableType, "std.core.Iterable").orElse(null);
    if (iterableInterface == null || iterableInterface.arguments().size() != 1) {
      return Optional.empty();
    }
    InterfaceRequirement iterator =
        interfaceRequirements(iterableInterface).stream()
            .filter(requirement -> requirement.method().name().equals("iterator"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    if (iterator == null) return Optional.empty();
    SemanticType iteratorInterface = iterator.result();
    InterfaceRequirement hasNext =
        interfaceRequirements(iteratorInterface).stream()
            .filter(requirement -> requirement.method().name().equals("hasNext"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    InterfaceRequirement next =
        interfaceRequirements(iteratorInterface).stream()
            .filter(requirement -> requirement.method().name().equals("next"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    if (hasNext == null || next == null || !hasNext.result().equals(SemanticType.BOOLEAN)) {
      return Optional.empty();
    }
    return Optional.of(
        new ResolvedIteration(
            iterableInterface.arguments().getFirst(),
            new ResolvedIteration.Strategy.Interface(
                iterableInterface,
                analyzer.context.declarationSymbols.get(iterator.method()),
                iteratorInterface,
                analyzer.context.declarationSymbols.get(hasNext.method()),
                analyzer.context.declarationSymbols.get(next.method()))));
  }

  Optional<SemanticType> conformanceTo(SemanticType concrete, String interfaceIdentity) {
    if (concrete.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType bound = analyzer.context.typeParameterBounds.get(concrete.identity());
      if (bound == null) return Optional.empty();
      return conformanceTo(bound, interfaceIdentity);
    }
    Syntax.InterfaceDecl directInterface = interfaceByIdentity(concrete.identity());
    if (directInterface != null) {
      Map<String, SemanticType> conformances = new LinkedHashMap<>();
      analyzer.collectConformances(
          directInterface, concrete, conformances, analyzer.context.currentProgram.span());
      return Optional.ofNullable(conformances.get(interfaceIdentity));
    }
    Optional<SemanticType> builtinConformance =
        analyzer.context.builtins.protocolConformances(concrete).stream()
            .filter(value -> value.identity().equals(interfaceIdentity))
            .findFirst();
    if (builtinConformance.isPresent()) return builtinConformance;
    if (analyzer.typeSystem.resolveAggregate(concrete) == null) return Optional.empty();
    Syntax.Program previous = analyzer.context.currentProgram;
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (AggregateView view : analyzer.typeSystem.aggregateViews(concrete)) {
      analyzer.context.currentProgram = analyzer.context.declarations.owner(view.declaration());
      Map<String, SemanticType> parameters =
          analyzer.typeSystem.aggregateTypeParameters(view.declaration());
      Map<String, SemanticType> substitutions =
          analyzer.typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.TypeRef interfaceRef : view.declaration().implementedInterfaces()) {
        SemanticType conformance =
            analyzer.typeSystem.resolveType(interfaceRef, parameters).substitute(substitutions);
        Syntax.InterfaceDecl contract = analyzer.typeSystem.resolveInterface(conformance);
        if (contract != null) {
          analyzer.collectConformances(contract, conformance, conformances, interfaceRef.span());
        }
      }
    }
    analyzer.context.currentProgram = previous;
    return Optional.ofNullable(conformances.get(interfaceIdentity));
  }

  Syntax.InterfaceDecl interfaceByIdentity(String identity) {
    for (Syntax.InterfaceDecl declaration : analyzer.context.declarations.interfaces()) {
      Syntax.Program owner = analyzer.context.declarations.owner(declaration);
      String candidate = TypeSystem.qualifiedName(owner.packageName(), declaration.name());
      if (declaration.visibility() == Syntax.Visibility.PRIVATE) {
        candidate = TypeSystem.fileLocalIdentity(candidate, owner);
      }
      if (candidate.equals(identity)) return declaration;
    }
    return null;
  }

  SemanticType accessibleReceiverType(Syntax.Member member, SemanticType receiverType) {
    if (receiverType.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType bound = analyzer.context.typeParameterBounds.get(receiverType.identity());
      if (bound != null && !bound.mayContainNull()) return receiverType;
    }
    if (!receiverType.mayContainNull()) return receiverType;
    if (!member.nullSafe()) {
      analyzer.context.diagnostics.error(
          UNSAFE_NULLABLE_ACCESS,
          "nullable value of type "
              + receiverType.displayName()
              + " must be narrowed or accessed with ?.",
          member.receiver().span());
    }
    return receiverType.equals(SemanticType.DYNAMIC)
        ? SemanticType.DYNAMIC
        : receiverType.nonNullable();
  }

  static SemanticType safeAccessResult(
      Syntax.Member member, SemanticType receiverType, SemanticType result) {
    if (!member.nullSafe()
        || !receiverType.mayContainNull()
        || result.kind() == SemanticType.Kind.VOID
        || result.equals(SemanticType.DYNAMIC)) {
      return result;
    }
    return result.nullable();
  }

  static SemanticType callableExpected(
      Syntax.Member member, SemanticType receiverType, SemanticType expected) {
    if (expected != null
        && member.nullSafe()
        && receiverType.mayContainNull()
        && expected.isNullable()) {
      return expected.nonNullable();
    }
    return expected;
  }

  SemanticType analyzeIndex(Syntax.Index index) {
    SemanticType receiverType = analyzer.typeOf(index.receiver(), null);
    SemanticType indexType = analyzer.typeOf(index.index(), null);
    Optional<dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIndex> resolved =
        analyzer.context.builtins.resolveIndex(receiverType);
    if (resolved.isEmpty()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "only Array, List, and Map can be indexed", index.span());
      return SemanticType.DYNAMIC;
    }
    dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIndex capability = resolved.orElseThrow();
    analyzer.context.indexes.put(
        index.span(),
        new ResolvedIndex(
            capability.kind(),
            capability.keyType(),
            capability.resultType(),
            capability.readIntrinsic(),
            capability.writeIntrinsic()));
    analyzer.typeSystem.requireType(capability.keyType(), indexType, index.index().span());
    return capability.resultType();
  }

  SemanticType assignmentTargetType(Syntax.Expression target) {
    return switch (target) {
      case Syntax.Name name -> analyzer.typeSystem.lookupDeclared(name.value(), name.span());
      case Syntax.Member member -> {
        if (member.nullSafe()) {
          analyzer.context.diagnostics.error(
              TYPE_MISMATCH, "safe access cannot be assigned", member.span());
        }
        SemanticType receiver = analyzer.typeOf(member.receiver(), null);
        if (receiver.nonNullable().category() == dev.w0fv1.norm.semantic.ValueCategory.VALUE
            && analyzer.typeSystem.resolveAggregate(receiver.nonNullable()) != null) {
          analyzer.context.diagnostics.error(
              TYPE_MISMATCH, "value field cannot be assigned", member.span());
        }
        yield analyzer.memberType(member, null);
      }
      case Syntax.Index index -> analyzeIndex(index);
      case Syntax.Unary unary when unary.operator() == TokenKind.STAR -> {
        SemanticType reference = analyzer.typeOf(unary.operand(), null);
        if (!reference.isReference()) {
          analyzer.context.diagnostics.error(
              TYPE_MISMATCH, "dereference assignment requires ref<T>", unary.span());
          yield SemanticType.DYNAMIC;
        }
        analyzer.context.semanticTypes.put(unary.span(), reference.referenceTarget());
        yield reference.referenceTarget();
      }
      default -> {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "invalid assignment target", target.span());
        yield SemanticType.DYNAMIC;
      }
    };
  }

  static boolean definitelyYields(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement
          || statement instanceof Syntax.ThrowStatement
          || statement instanceof Syntax.BreakStatement broken && broken.value() != null) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && definitelyYields(conditional.thenBody())
          && definitelyYields(conditional.elseBody())) {
        return true;
      }
      if (statement instanceof Syntax.TryStatement tried) {
        if (tried.finallyClause().isPresent()
            && definitelyYields(tried.finallyClause().orElseThrow().body())) {
          return true;
        }
        if (definitelyYields(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyYields(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  final class SemanticPatternDomain implements PatternCoverage.Domain<SemanticType> {
    @Override
    public List<PatternCoverage.Constructor<SemanticType>> constructors(SemanticType type) {
      if (type.isNullable()) {
        return List.of(
            new PatternCoverage.Constructor<>("$null", List.of()),
            new PatternCoverage.Constructor<>("$value", List.of(type.nonNullable())));
      }
      if (type.equals(SemanticType.BOOLEAN)) {
        return List.of(
            new PatternCoverage.Constructor<>("boolean:false", List.of()),
            new PatternCoverage.Constructor<>("boolean:true", List.of()));
      }
      Syntax.EnumDecl declaration = analyzer.typeSystem.resolveEnum(type);
      if (declaration == null) return List.of();
      Map<String, SemanticType> substitutions =
          analyzer.typeSystem.enumSubstitutions(declaration, type);
      Map<String, SemanticType> parameters = analyzer.typeSystem.enumTypeParameters(declaration);
      return declaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.name(),
                      variant.parameters().stream()
                          .map(
                              field ->
                                  analyzer
                                      .typeSystem
                                      .resolveDeclarationType(field.type(), field, parameters)
                                      .substitute(substitutions))
                          .toList()))
          .toList();
    }

    @Override
    public PatternCoverage.Constructor<SemanticType> openConstructor(
        SemanticType type, String key) {
      return constructors(type).isEmpty()
          ? new PatternCoverage.Constructor<>(key, List.of())
          : null;
    }
  }
}
