package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.BodyAnalysisState.ControlContext;
import dev.w0fv1.norm.frontend.BodyAnalysisState.ControlKind;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.AnalysisCheckpoint;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.FunctionReferenceResolution;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceRequirement;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateField;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateView;
import dev.w0fv1.norm.pattern.PatternCoverage;
import dev.w0fv1.norm.semantic.NumericTypes;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ExpressionChecker implements ExpressionTyping {
  private final SemanticAnalysisContext context;
  private final TypeSystem typeSystem;
  final FlowAnalyzer flow;
  final CallResolver calls;
  private final java.util.function.Consumer<Syntax.Statement> statements;

  ExpressionChecker(
      SemanticAnalysisContext context,
      TypeSystem typeSystem,
      FlowAnalyzer flow,
      java.util.function.Consumer<Syntax.Statement> statements) {
    this.context = context;
    this.typeSystem = typeSystem;
    this.flow = flow;
    this.statements = statements;
    calls =
        new CallResolver(
            context.diagnostics,
            context.model,
            context.builtins,
            context.resolution,
            typeSystem,
            this);
  }

  public SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    context.guard.checkpoint();
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
          case Syntax.InterpolatedStringExpr interpolation -> {
            for (int index = 0; index < interpolation.expressions().size(); index++) {
              typeOf(interpolation.stringConversion(index), SemanticType.STRING);
            }
            yield SemanticType.STRING;
          }
          case Syntax.ArrayLiteral array -> analyzeArray(array, expected);
          case Syntax.Name name -> analyzeNameValue(name, expected);
          case Syntax.Unary unary -> analyzeUnary(unary, expected);
          case Syntax.Binary binary -> analyzeBinary(binary, expected);
          case Syntax.Call call -> analyzeCall(call, expected);
          case Syntax.Member member -> memberType(member, expected);
          case Syntax.Lambda lambda -> analyzeLambda(lambda, expected);
          case Syntax.Index index -> analyzeIndex(index);
          case Syntax.SwitchExpression switchExpression ->
              analyzeSwitch(switchExpression, expected);
        };
    if (expected != null && type.equals(SemanticType.DYNAMIC)) {
      type = expected;
    }
    context.model.putType(expression.span(), type);
    if (type.isReference()) {
      context.body.referenceLifetimes.put(expression.span(), referenceLifetime(expression));
    }
    return type;
  }

  LexicalLifetime referenceLifetime(Syntax.Expression expression) {
    LexicalLifetime known = context.body.referenceLifetimes.get(expression.span());
    if (known != null) return known;
    if (expression instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol symbol = flow.findScoped(name.value());
      LexicalLifetime lifetime =
          symbol == null ? null : context.body.flowScopes.referenceLifetime(symbol);
      return lifetime == null ? LexicalLifetime.unusable() : lifetime;
    }
    if (expression instanceof Syntax.Unary unary && unary.operator() == TokenKind.AMPERSAND) {
      if (unary.operand() instanceof Syntax.Name name) {
        FlowScopes.ScopedSymbol symbol = flow.findScoped(name.value());
        if (symbol != null) {
          SymbolKind kind = scopedSymbol(symbol).kind();
          if (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER) {
            return context.body.flowScopes.storageLifetime(symbol);
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
    FlowScopes.ScopedSymbol scoped = flow.findScoped(name.value());
    if (scoped != null) {
      Symbol symbol = scopedSymbol(scoped);
      if (!context.body.lambdaLocals.isEmpty()
          && !context.body.lambdaLocals.getFirst().contains(scoped.id())
          && (symbol.kind() == SymbolKind.LOCAL_VARIABLE
              || symbol.kind() == SymbolKind.PARAMETER
              || symbol.kind() == SymbolKind.SELF)) {
        if (symbol.type().isReference()) {
          context.diagnostics.error(
              TYPE_MISMATCH, "ref cannot be captured by a lambda", name.span());
        }
        context.body.capturedLocals.add(scoped.id());
        if (context.body.assignedLocals.contains(scoped.id()))
          reportMutableCapture(scoped.id(), name.span());
      }
      return flow.lookup(name.value(), name.span());
    }
    List<Syntax.FunctionDecl> candidates = typeSystem.resolveFunctions(name.value());
    if (!candidates.isEmpty()) {
      if (expected == null || !expected.isFunction()) {
        context.diagnostics.error(
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
        context.diagnostics.error(
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
      typeSystem.bindDeclarationUse(name.span(), name.value(), selected);
      context.model.putFunctionReference(name.span(), resolution.reifiedArguments());
      return expected.nonNullable();
    }
    return flow.lookup(name.value(), name.span());
  }

  SemanticType functionType(Syntax.FunctionDecl declaration) {
    Map<String, SemanticType> parameters = typeSystem.functionTypeParameters(declaration);
    return SemanticType.function(
        typeSystem.functionReturnType(declaration, parameters),
        declaration.parameters().stream()
            .map(
                parameter ->
                    typeSystem.resolveDeclarationType(parameter.type(), declaration, parameters))
            .toList());
  }

  SemanticType analyzeLambda(Syntax.Lambda lambda, SemanticType expected) {
    SemanticType expectedFunction = expected != null && expected.isFunction() ? expected : null;
    if (expectedFunction != null
        && expectedFunction.functionParameterTypes().size() != lambda.parameters().size()) {
      context.diagnostics.error(
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
                    typeSystem.validateType(type, false);
                    SemanticType resolved =
                        typeSystem.resolveType(type, context.resolution.activeTypeParameters);
                    return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                  })
              .orElse(null);
      if (explicit != null && contextual != null)
        typeSystem.requireType(contextual, explicit, parameter.span());
      SemanticType resolved = explicit != null ? explicit : contextual;
      if (resolved == null) {
        context.diagnostics.error(
            TYPE_MISMATCH, "cannot infer lambda parameter type", parameter.span());
        resolved = SemanticType.DYNAMIC;
      }
      parameterTypes.add(resolved);
    }
    SemanticType previousReturn = context.body.expectedReturnType;
    boolean previousImplicitSelfReturn = context.body.implicitSelfReturn;
    SemanticType declaredContextualReturn =
        lambda
            .returnType()
            .map(
                type -> {
                  typeSystem.validateType(type, true);
                  SemanticType resolved =
                      typeSystem.resolveType(type, context.resolution.activeTypeParameters);
                  return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                })
            .orElse(expectedFunction == null ? null : expectedFunction.functionReturnType());
    SemanticType contextualReturn =
        declaredContextualReturn != null
                && declaredContextualReturn.kind() == SemanticType.Kind.TYPE_PARAMETER
            ? null
            : declaredContextualReturn;
    if (lambda.returnType().isPresent() && expectedFunction != null) {
      typeSystem.requireType(
          expectedFunction.functionReturnType(),
          declaredContextualReturn,
          lambda.returnType().orElseThrow().span());
    }
    context.body.expectedReturnType =
        contextualReturn == null ? SemanticType.DYNAMIC : contextualReturn;
    context.body.implicitSelfReturn = false;
    flow.pushScope(lambda.span());
    Deque<ControlContext> outerControls = new ArrayDeque<>(context.body.controls);
    context.body.controls.clear();
    Set<SymbolId> localSymbols = new HashSet<>();
    context.body.lambdaLocals.addFirst(localSymbols);
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      Symbol symbol =
          typeSystem.register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              parameterTypes.get(index),
              parameter.nameSpan(),
              context.body.currentCallable,
              List.of(),
              List.of());
      flow.declareExisting(
          parameter.name(), parameterTypes.get(index), parameter.nameSpan(), symbol.id());
      localSymbols.add(symbol.id());
    }
    SemanticType result = contextualReturn;
    int last = lambda.body().size() - 1;
    for (int index = 0; index < lambda.body().size(); index++) {
      Syntax.Statement statement = lambda.body().get(index);
      if (index == last && statement instanceof Syntax.ExpressionStatement expression) {
        result = typeOf(expression.expression(), contextualReturn);
        if (contextualReturn != null)
          typeSystem.requireAssignable(contextualReturn, result, expression.span());
      } else {
        statements.accept(statement);
      }
    }
    context.body.controls.addAll(outerControls);
    flow.popScope();
    context.body.lambdaLocals.removeFirst();
    context.body.expectedReturnType = previousReturn;
    context.body.implicitSelfReturn = previousImplicitSelfReturn;
    if (result == null) {
      context.diagnostics.error(
          TYPE_MISMATCH,
          "lambda return type requires an expected type or a final expression",
          lambda.span());
      result = SemanticType.DYNAMIC;
    }
    if (result.containsReference()) {
      context.diagnostics.error(
          TYPE_MISMATCH, "lambda return type cannot contain ref", lambda.span());
      result = SemanticType.DYNAMIC;
    }
    return SemanticType.function(result, parameterTypes);
  }

  Symbol scopedSymbol(FlowScopes.ScopedSymbol scoped) {
    return context.model.symbols().get(scoped.id());
  }

  void reportMutableCapture(SymbolId symbol, SourceSpan span) {
    if (context.body.reportedMutableCaptures.add(symbol)) {
      context.diagnostics.error(
          INVALID_CONTROL,
          "captured local '"
              + context.model.symbols().get(symbol).name()
              + "' must be effectively final",
          span);
    }
  }

  Optional<FunctionReferenceResolution> resolveFunctionReference(
      Syntax.FunctionDecl declaration, SemanticType pattern, SemanticType expected) {
    SemanticType target = expected.nonNullable();
    Symbol symbol =
        context.model.symbols().get(context.model.declarationSymbols().get(declaration));
    if (symbol.typeParameters().isEmpty()) {
      return pattern.equals(target)
          ? Optional.of(new FunctionReferenceResolution(declaration, List.of(), pattern))
          : Optional.empty();
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(
            symbol.typeParameters().stream().map(TypeParameterInfo::type).toList(),
            typeSystem.typeRelations);
    solver.constrain(pattern, target);
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
      if (bound != null && !typeSystem.isAssignable(bound, arguments.get(index)))
        return Optional.empty();
    }
    SemanticType resolved = pattern.substitute(solution.substitutions());
    return resolved.equals(target)
        ? Optional.of(new FunctionReferenceResolution(declaration, arguments, resolved))
        : Optional.empty();
  }

  SemanticType analyzeSwitch(Syntax.SwitchExpression switchExpression, SemanticType expected) {
    SemanticType valueType = typeOf(switchExpression.value(), null);
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    PatternCoverage<SemanticType> coverage = new PatternCoverage<>(new SemanticPatternDomain());
    ControlContext control = ControlContext.switchExpression(expected);
    FlowScopes.FlowState incoming = context.body.flowScopes.snapshot();
    List<FlowScopes.FlowState> caseFlows = new ArrayList<>();
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      flow.replaceFlow(incoming);
      flow.pushScope(switchCase.span());
      PatternCoverage.Pattern pattern = analyzePattern(switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        context.diagnostics.error(
            INVALID_CONTROL, "switch case is unreachable", switchCase.pattern().span());
      }
      previous.add(pattern);
      context.body.controls.addFirst(control);
      switchCase.body().forEach(statements);
      context.body.controls.removeFirst();
      flow.popScope();
      caseFlows.add(context.body.flowScopes.snapshot());
    }
    if (!caseFlows.isEmpty()) {
      FlowScopes.FlowState merged = caseFlows.getFirst();
      for (int index = 1; index < caseFlows.size(); index++) {
        merged = flow.mergeFlows(incoming, merged, caseFlows.get(index));
      }
      flow.replaceFlow(merged);
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      context.diagnostics.error(
          INVALID_CONTROL, "switch is not exhaustive", switchExpression.span());
    }
    SemanticType result = control.resultType();
    if (result == null) return SemanticType.VOID;
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      if (!StatementFlow.definitelyYields(switchCase.body())) {
        context.diagnostics.error(
            INVALID_CONTROL, "switch expression case must produce a value", switchCase.span());
      }
    }
    if (result.isReference()) {
      LexicalLifetime lifetime =
          control.referenceLifetime() == null
              ? LexicalLifetime.unusable()
              : control.referenceLifetime();
      LexicalLifetime useLifetime = context.body.flowScopes.currentLifetime();
      if (!lifetime.outlives(useLifetime)) {
        context.diagnostics.error(
            INVALID_CONTROL,
            "reference cannot outlive the addressed storage location",
            switchExpression.span());
        lifetime = useLifetime;
      }
      context.body.referenceLifetimes.put(switchExpression.span(), lifetime);
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
        context.diagnostics.error(
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
        typeSystem.validateType(binding.type(), false);
        SemanticType type =
            typeSystem.resolveType(binding.type(), context.resolution.activeTypeParameters);
        if (!type.equals(expected)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "pattern type " + type.displayName() + " does not match " + expected.displayName(),
              binding.type().span());
        }
        Symbol symbol =
            typeSystem.register(
                binding,
                binding.name(),
                SymbolKind.LOCAL_VARIABLE,
                type,
                binding.nameSpan(),
                context.body.currentCallable,
                List.of(),
                List.of());
        flow.declareExisting(binding.name(), type, binding.nameSpan(), symbol.id());
        if (!context.body.lambdaLocals.isEmpty())
          context.body.lambdaLocals.getFirst().add(symbol.id());
        yield PatternCoverage.Pattern.any();
      }
      case Syntax.VariantPattern variant -> analyzeVariantPattern(variant, expected);
      case Syntax.IntegerPattern integer -> {
        SemanticType literalType = numericIntegerType(integer.value(), expected, integer.span());
        typeSystem.requireType(expected, literalType, integer.span());
        context.model.putType(integer.span(), literalType);
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
        typeSystem.requireType(expected, literalType, decimal.span());
        context.model.putType(decimal.span(), literalType);
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
        typeSystem.requireType(SemanticType.CODE_POINT, expected, codePoint.span());
        yield PatternCoverage.Pattern.constructor("codepoint:" + codePoint.value(), List.of());
      }
      case Syntax.BooleanPattern bool -> {
        typeSystem.requireType(SemanticType.BOOLEAN, expected, bool.span());
        yield PatternCoverage.Pattern.constructor("boolean:" + bool.value(), List.of());
      }
      case Syntax.StringPattern string -> {
        typeSystem.requireType(SemanticType.STRING, expected, string.span());
        yield PatternCoverage.Pattern.constructor("string:" + string.value(), List.of());
      }
      case Syntax.NullPattern ignored -> {
        context.diagnostics.error(
            TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
        yield PatternCoverage.Pattern.constructor("$null", List.of());
      }
    };
  }

  PatternCoverage.Pattern analyzeVariantPattern(
      Syntax.VariantPattern pattern, SemanticType expected) {
    Syntax.EnumDecl enumDecl = typeSystem.resolveEnum(expected);
    if (enumDecl == null) {
      context.diagnostics.error(
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
      context.diagnostics.error(
          UNKNOWN_NAME,
          "enum '" + enumDecl.name() + "' has no variant '" + pattern.name() + "'",
          pattern.nameSpan());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    context.model.putBinding(pattern.nameSpan(), context.model.declarationSymbols().get(variant));
    Map<String, SemanticType> substitutions = typeSystem.enumSubstitutions(enumDecl, expected);
    List<SemanticType> payloadTypes =
        variant.parameters().stream()
            .map(
                parameter ->
                    typeSystem
                        .resolveDeclarationType(
                            parameter.type(), parameter, typeSystem.enumTypeParameters(enumDecl))
                        .substitute(substitutions))
            .toList();
    int requiredPayloads = 0;
    for (int index = 0; index < variant.parameters().size(); index++) {
      if (variant.parameters().get(index).defaultValue().isEmpty()) requiredPayloads = index + 1;
    }
    if (pattern.arguments().size() < requiredPayloads
        || pattern.arguments().size() > payloadTypes.size()) {
      context.diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern '"
              + pattern.name()
              + "' accepts "
              + requiredPayloads
              + " to "
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
    for (int index = arguments.size(); index < payloadTypes.size(); index++) {
      arguments.add(PatternCoverage.Pattern.any());
    }
    return PatternCoverage.Pattern.constructor("variant:" + variant.name(), arguments);
  }

  void analyzeBreak(Syntax.BreakStatement statement) {
    if (context.body.controls.isEmpty()) {
      context.diagnostics.error(
          INVALID_CONTROL, "break is only valid inside for or switch", statement.span());
      if (statement.value() != null) typeOf(statement.value(), null);
      return;
    }
    ControlContext control = context.body.controls.getFirst();
    if (control.kind() != ControlKind.SWITCH) {
      if (statement.value() != null) {
        context.diagnostics.error(
            INVALID_CONTROL, "loop break cannot produce a value", statement.span());
        typeOf(statement.value(), null);
      }
      return;
    }
    if (statement.value() == null) {
      context.diagnostics.error(
          INVALID_CONTROL, "switch break must produce a value", statement.span());
      return;
    }
    SemanticType actual = typeOf(statement.value(), control.resultType());
    if (actual.isReference()) {
      control.mergeReferenceLifetime(referenceLifetime(statement.value()));
    }
    if (control.resultType() == null || control.resultType().equals(SemanticType.DYNAMIC)) {
      control.setResultType(actual);
    } else {
      typeSystem.requireAssignable(control.resultType(), actual, statement.value().span());
    }
  }

  public TypeProbe probeType(Syntax.Expression expression, SemanticType expected) {
    AnalysisCheckpoint checkpoint = checkpoint();
    try {
      SemanticType type = typeOf(expression, expected);
      boolean hasErrors = context.diagnostics.hasErrorsSince(checkpoint.diagnosticMark());
      return new TypeProbe(type, hasErrors);
    } finally {
      restore(checkpoint);
    }
  }

  AnalysisCheckpoint checkpoint() {
    return new AnalysisCheckpoint(
        context.model.checkpoint(),
        context.body.checkpoint(),
        context.resolution.checkpoint(),
        context.diagnostics.mark());
  }

  void restore(AnalysisCheckpoint checkpoint) {
    context.model.restore(checkpoint.model());
    context.body.restore(checkpoint.body());
    context.resolution.restore(checkpoint.resolution());
    context.diagnostics.rollback(checkpoint.diagnosticMark());
  }

  SemanticType analyzeNull(Syntax.NullLiteral literal, SemanticType expected) {
    if (expected == null || expected.equals(SemanticType.DYNAMIC)) {
      context.diagnostics.error(
          UNTYPED_NULL, "null requires an expected nullable type", literal.span());
      return SemanticType.DYNAMIC;
    }
    if (!expected.isNullable()) {
      context.diagnostics.error(
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
            : context
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
      SemanticType current = typeOf(element, expectedElement);
      if (elementType == null && !CallResolver.containsDynamic(current)) {
        elementType = current;
      } else if (elementType != null && !CallResolver.containsDynamic(current)) {
        if (expectedElement != null) {
          if (!typeSystem.isAssignable(elementType, current)) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          }
        } else {
          SemanticType common = typeSystem.commonType(elementType, current).orElse(null);
          if (common == null) {
            context.diagnostics.error(
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
      context.diagnostics.error(
          TYPE_MISMATCH, "collection element type cannot contain ref", array.span());
      return SemanticType.DYNAMIC;
    }
    return expectedArray == null
        ? context.builtins.instantiate("Array", List.of(inferredElement))
        : expectedArray;
  }

  SemanticType analyzeUnary(Syntax.Unary unary, SemanticType expected) {
    if (unary.operator() == TokenKind.AMPERSAND) return analyzeAddress(unary);
    if (unary.operator() == TokenKind.STAR) {
      SemanticType operand = typeOf(unary.operand(), null);
      if (!operand.isReference()) {
        context.diagnostics.error(TYPE_MISMATCH, "dereference requires ref<T>", unary.span());
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
    SemanticType operand = typeOf(unary.operand(), required);
    if (unary.operator() == TokenKind.BANG) {
      typeSystem.requireType(SemanticType.BOOLEAN, operand, unary.span());
      return SemanticType.BOOLEAN;
    }
    if (!NumericTypes.isLeaf(operand)) {
      context.diagnostics.error(
          TYPE_MISMATCH, "numeric negation requires a numeric leaf", unary.span());
      return SemanticType.DYNAMIC;
    }
    return operand;
  }

  SemanticType analyzeAddress(Syntax.Unary unary) {
    Syntax.Expression target = unary.operand();
    SemanticType targetType = typeOf(target, null);
    boolean addressable = false;
    if (target instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol scoped = flow.findScoped(name.value());
      if (scoped != null) {
        SymbolKind kind = scopedSymbol(scoped).kind();
        addressable =
            (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER)
                    && (context.body.lambdaLocals.isEmpty()
                        || context.body.lambdaLocals.getFirst().contains(scoped.id()))
                || kind == SymbolKind.FIELD
                    && context.body.currentAggregate != null
                    && context.body.currentAggregate.kind() != Syntax.AggregateKind.VALUE;
      }
    } else if (target instanceof Syntax.Member member && !member.nullSafe()) {
      SemanticType receiver = context.model.semanticTypes().get(member.receiver().span());
      SymbolId fieldId = context.model.bindings().get(member.nameSpan());
      Symbol field = fieldId == null ? null : context.model.symbols().get(fieldId);
      addressable =
          receiver != null
              && receiver.nonNullable().category() == ValueCategory.IDENTITY
              && field != null
              && field.kind() == SymbolKind.FIELD;
    }
    if (!addressable) {
      context.diagnostics.error(
          TYPE_MISMATCH, "address-of requires a writable storage location", target.span());
      return SemanticType.DYNAMIC;
    }
    if (targetType.category() != ValueCategory.VALUE) {
      context.diagnostics.error(TYPE_MISMATCH, "ref target must be a value type", target.span());
      return SemanticType.DYNAMIC;
    }
    return SemanticType.reference(targetType);
  }

  SemanticType analyzeBinary(Syntax.Binary binary, SemanticType expected) {
    if (binary.operator() == TokenKind.QUESTION_QUESTION) {
      SemanticType leftExpected =
          expected == null || expected.isReference() ? expected : expected.nullable();
      SemanticType left = typeOf(binary.left(), leftExpected);
      if (!left.mayContainNull()) {
        context.diagnostics.error(
            TYPE_MISMATCH, "left side of ?? must be nullable", binary.left().span());
      }
      SemanticType result = left.equals(SemanticType.DYNAMIC) ? expected : left.nonNullable();
      SemanticType right = typeOf(binary.right(), result);
      if (result == null) return right;
      typeSystem.requireAssignable(result, right, binary.right().span());
      return result;
    }
    SemanticType left;
    SemanticType right;
    if ((binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL)
        && binary.left() instanceof Syntax.NullLiteral) {
      right = typeOf(binary.right(), null);
      left = typeOf(binary.left(), right);
    } else {
      SemanticType numericExpected =
          expected != null && NumericTypes.isLeaf(expected) ? expected.nonNullable() : null;
      left = typeOf(binary.left(), numericExpected);
      right = null;
    }
    if (right == null) {
      if (binary.operator() == TokenKind.AND_AND) {
        FlowScopes.FlowState incoming = context.body.flowScopes.snapshot();
        flow.pushScope(binary.right().span());
        flow.applyNarrowings(flow.narrowingsFor(binary.left(), true));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        flow.popScope();
        flow.replaceFlow(incoming);
      } else if (binary.operator() == TokenKind.OR_OR) {
        FlowScopes.FlowState incoming = context.body.flowScopes.snapshot();
        flow.pushScope(binary.right().span());
        flow.applyNarrowings(flow.narrowingsFor(binary.left(), false));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        flow.popScope();
        flow.replaceFlow(incoming);
      } else {
        right = typeOf(binary.right(), left);
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
        typeSystem.requireBoth(SemanticType.BOOLEAN, left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        if (!typeSystem.isAssignable(left, right) && !typeSystem.isAssignable(right, left)) {
          context.diagnostics.error(
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
      context.diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  SemanticType numericDecimalType(
      java.math.BigDecimal value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.decimalLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      context.diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  boolean requireNumericLeaves(SemanticType left, SemanticType right, SourceSpan span) {
    if (NumericTypes.isLeaf(left) && left.equals(right)) return true;
    context.diagnostics.error(
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
          && (typeSystem.resolveEnum(receiverName.value()) != null
              || !context.builtins.typeMembers(receiverName.value(), member.name()).isEmpty())) {
        return analyzeMethodCall(member, call, expected, null);
      }
      SemanticType nullableReceiver = typeOf(member.receiver(), null);
      SemanticType memberType = memberTypeWithoutDiagnostics(member, nullableReceiver);
      if (memberType != null && memberType.isFunction()) {
        context.model.putType(member.span(), memberType);
        return analyzeFunctionInvocation(call, memberType, context.body.currentCallable);
      }
      return analyzeMethodCall(member, call, expected, nullableReceiver);
    }
    SemanticType calleeType = typeOf(call.callee(), null);
    if (calleeType.isFunction())
      return analyzeFunctionInvocation(call, calleeType, context.body.currentCallable);
    context.diagnostics.error(INVALID_CALL, "expression is not callable", call.callee().span());
    calls.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType analyzeFunctionInvocation(Syntax.Call call, SemanticType function, SymbolId target) {
    if (function.isUnknownFunction()) {
      context.diagnostics.error(
          INVALID_CALL, "Function<?> has no callable signature", call.callee().span());
      calls.analyzeArguments(call.arguments());
      return calls.recordCall(
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
    return calls.recordCall(
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
    Syntax.AggregateDecl owner = typeSystem.resolveAggregate(receiver);
    if (owner == null) return null;
    AggregateField resolved = typeSystem.aggregateField(receiver, member.name());
    if (resolved == null) return null;
    Syntax.FieldDecl field = resolved.field();
    owner = resolved.view().declaration();
    if (field.visibility() == Syntax.Visibility.PRIVATE && context.body.currentAggregate != owner) {
      context.diagnostics.error(
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
    context.model.putBinding(member.nameSpan(), context.model.declarationSymbols().get(field));
    SemanticType type =
        typeSystem
            .resolveDeclarationType(field.type(), field, typeSystem.aggregateTypeParameters(owner))
            .substitute(typeSystem.aggregateSubstitutions(owner, resolved.view().type()));
    return safeAccessResult(member, nullableReceiver, type);
  }

  SemanticType analyzeNamedCall(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    String callee = name.value();
    FlowScopes.ScopedSymbol scoped = flow.findScoped(callee);
    if (scoped != null && scoped.declaredType().isFunction()) {
      SemanticType function = scoped.declaredType();
      context.model.putBinding(name.span(), scoped.id());
      context.model.putType(name.span(), function);
      return analyzeFunctionInvocation(call, function, scoped.id());
    }
    context
        .builtins
        .type(callee)
        .ifPresent(symbol -> context.model.putBinding(name.span(), symbol.id()));
    List<Symbol> builtinFunctions =
        context.builtins.globals(callee, context.resolution.currentProgram.span().source().id());
    if (!builtinFunctions.isEmpty()) {
      if (name.diamond()) {
        context.diagnostics.error(
            INVALID_CALL, "diamond is only valid for generic constructors", name.span());
      }
      var invocation =
          calls.resolveSymbolCall(
              builtinFunctions, name.typeArguments(), call, expected, name.span(), false);
      if (invocation == null) return SemanticType.DYNAMIC;
      Symbol symbol = invocation.declaration();
      context.model.putSymbolIfAbsent(symbol.id(), symbol);
      context.model.putBinding(name.span(), symbol.id());
      return calls.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          invocation.parameters(),
          invocation.reifiedArguments(),
          invocation.result());
    }
    Syntax.AggregateDecl aggregateDecl = typeSystem.resolveAggregate(callee);
    boolean builtinType = context.builtins.type(callee).isPresent();
    if (builtinType || aggregateDecl != null) {
      if (aggregateDecl != null) typeSystem.bindDeclarationUse(name.span(), callee, aggregateDecl);
      var selected = calls.resolveConstruction(name, call, expected);
      if (selected == null) return SemanticType.DYNAMIC;
      return calls.recordCall(
          call,
          name.span(),
          builtinType ? ResolvedCall.Kind.INTRINSIC : ResolvedCall.Kind.CONSTRUCT,
          selected.declaration(),
          selected.parameters(),
          List.of(),
          selected.result());
    }
    List<Syntax.FunctionDecl> functionCandidates = typeSystem.resolveFunctions(callee);
    if (!functionCandidates.isEmpty() && name.diamond()) {
      context.diagnostics.error(
          INVALID_CALL, "diamond is only valid for generic constructors", name.span());
    }
    CallResolver.CallResolution<Syntax.FunctionDecl> resolution =
        calls.resolveSourceCall(
            functionCandidates, name.typeArguments(), call, expected, Map.of(), name.span(), false);
    if (resolution != null) {
      typeSystem.bindDeclarationUse(name.span(), callee, resolution.declaration());
      return calls.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CALLABLE,
          context.model.declarationSymbols().get(resolution.declaration()),
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (!functionCandidates.isEmpty()) return SemanticType.DYNAMIC;
    context.diagnostics.error(
        UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
    calls.analyzeArguments(call.arguments());
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
      Syntax.EnumDecl enumDecl = typeSystem.resolveEnum(enumName.value());
      if (enumDecl != null) {
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant == null) {
          context.diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no variant '" + member.name() + "'",
              member.nameSpan());
          calls.analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        Symbol variantSymbol =
            context.model.symbols().get(context.model.declarationSymbols().get(variant));
        context.model.putBinding(enumName.span(), context.model.declarationSymbols().get(enumDecl));
        context.model.putBinding(member.nameSpan(), variantSymbol.id());
        var invocation =
            calls.resolveSymbolCall(
                List.of(variantSymbol),
                enumName.typeArguments(),
                call,
                expected,
                member.span(),
                false);
        if (invocation == null) return SemanticType.DYNAMIC;
        return calls.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.ENUM_CONSTRUCT,
            variantSymbol.id(),
            invocation.parameters(),
            invocation.reifiedArguments(),
            invocation.result());
      }
    }
    if (member.receiver() instanceof Syntax.Name typeName) {
      List<Symbol> typeMethods = context.builtins.typeMembers(typeName.value(), member.name());
      if (!typeMethods.isEmpty()) {
        context
            .builtins
            .type(typeName.value())
            .ifPresent(symbol -> context.model.putBinding(typeName.span(), symbol.id()));
        var invocation =
            calls.resolveSymbolCall(
                typeMethods, member.typeArguments(), call, expected, member.span(), false);
        if (invocation == null) return SemanticType.DYNAMIC;
        Symbol symbol = invocation.declaration();
        context.model.putBinding(member.nameSpan(), symbol.id());
        return calls.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.INTRINSIC,
            symbol.id(),
            invocation.parameters(),
            invocation.reifiedArguments(),
            invocation.result());
      }
    }
    SemanticType nullableReceiver =
        analyzedReceiver == null ? typeOf(member.receiver(), null) : analyzedReceiver;
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    if (member.name().isEmpty()) {
      calls.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    List<Symbol> builtinMembers = context.builtins.members(receiver, member.name());
    if (!builtinMembers.isEmpty()) {
      List<Symbol> builtinMethods =
          builtinMembers.stream().filter(symbol -> symbol.kind() == SymbolKind.METHOD).toList();
      if (builtinMethods.isEmpty()) {
        context.diagnostics.error(
            UNKNOWN_NAME,
            "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
            call.span());
        calls.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      var invocation =
          calls.resolveSymbolCall(
              builtinMethods,
              member.typeArguments(),
              call,
              expected,
              member.span(),
              member.nullSafe() && nullableReceiver.mayContainNull());
      if (invocation == null) return SemanticType.DYNAMIC;
      Symbol symbol = invocation.declaration();
      context.model.putBinding(member.nameSpan(), symbol.id());
      List<SemanticType> reifiedArguments = invocation.reifiedArguments();
      dev.w0fv1.norm.abi.IntrinsicId intrinsic =
          context.builtins.intrinsic(symbol.id()).orElse(null);
      if ((intrinsic == dev.w0fv1.norm.abi.IntrinsicId.CLASS_ANNOTATION
              || intrinsic == dev.w0fv1.norm.abi.IntrinsicId.FIELD_ANNOTATION)
          && !reifiedArguments.isEmpty()
          && typeSystem.resolveAnnotation(reifiedArguments.getFirst().nonNullable()) == null) {
        context.diagnostics.error(
            TYPE_MISMATCH, "annotation query requires an annotation type", member.span());
      }
      return calls.recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          invocation.parameters(),
          reifiedArguments,
          safeAccessResult(member, nullableReceiver, invocation.result()));
    }
    SemanticType aggregateReceiver = aggregateReceiver(receiver);
    Syntax.AggregateDecl aggregateDecl = typeSystem.resolveAggregate(aggregateReceiver);
    if (aggregateDecl != null) {
      if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE && member.name().equals("copy")) {
        context.model.putBinding(member.nameSpan(), context.model.copyMethod(receiver.identity()));
        typeSystem.validateTypeArgumentCount(
            member.name(), 0, member.typeArguments(), member.span());
        member
            .typeArguments()
            .forEach(
                argument ->
                    typeSystem.resolveCheckedType(
                        argument, context.resolution.activeTypeParameters));
        return calls.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.COPY,
            context.model.copyMethod(receiver.identity()),
            List.of(),
            List.of(),
            safeAccessResult(member, nullableReceiver, receiver));
      }
      boolean foundMethod = false;
      boolean foundAccessibleMethod = false;
      for (AggregateView view : typeSystem.aggregateViews(aggregateReceiver)) {
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
                            || context.body.currentAggregate == view.declaration())
                .toList();
        foundAccessibleMethod |= !accessibleMethods.isEmpty();
        Map<String, SemanticType> substitutions =
            typeSystem.aggregateSubstitutions(view.declaration(), view.type());
        boolean structuralMatch =
            accessibleMethods.stream()
                .anyMatch(
                    candidate ->
                        calls.arguments.argumentIndices(
                                call, typeSystem.parametersOf(candidate, substitutions), false)
                            != null);
        if (!structuralMatch) continue;
        CallResolver.CallResolution<Syntax.FunctionDecl> resolution =
            calls.resolveSourceCall(
                accessibleMethods,
                member.typeArguments(),
                call,
                expected,
                substitutions,
                member.nameSpan(),
                member.nullSafe() && nullableReceiver.mayContainNull());
        if (resolution == null) return SemanticType.DYNAMIC;
        Syntax.FunctionDecl method = resolution.declaration();
        context.model.putBinding(member.nameSpan(), context.model.declarationSymbols().get(method));
        return calls.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.CALLABLE,
            context.model.declarationSymbols().get(method),
            resolution.parameters(),
            resolution.reifiedArguments(),
            safeAccessResult(member, nullableReceiver, resolution.result()));
      }
      if (foundMethod) {
        if (!foundAccessibleMethod) {
          context.diagnostics.error(
              UNKNOWN_NAME,
              "method '"
                  + member.name()
                  + "' is private in "
                  + TypeSystem.aggregateKeyword(aggregateDecl)
                  + " '"
                  + aggregateDecl.name()
                  + "'",
              member.nameSpan());
          calls.analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        context.diagnostics.error(
            INVALID_CALL, "no method overload accepts the supplied arguments", call.span());
        calls.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
    }
    List<InterfaceRequirement> interfaceMethods =
        typeSystem.interfaceRequirements(receiver).stream()
            .filter(requirement -> requirement.method().name().equals(member.name()))
            .toList();
    CallResolver.CallResolution<InterfaceRequirement> interfaceResolution =
        calls.resolveInterfaceCall(interfaceMethods, member, call, expected, nullableReceiver);
    if (!interfaceMethods.isEmpty() && interfaceResolution == null) return SemanticType.DYNAMIC;
    if (interfaceResolution != null) {
      InterfaceRequirement interfaceMethod = interfaceResolution.declaration();
      Symbol target =
          context
              .model
              .symbols()
              .get(context.model.declarationSymbols().get(interfaceMethod.method()));
      context.model.putBinding(member.nameSpan(), target.id());
      return calls.recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTERFACE_CALL,
          target.id(),
          interfaceResolution.parameters(),
          interfaceResolution.reifiedArguments(),
          safeAccessResult(member, nullableReceiver, interfaceResolution.result()));
    }
    List<Syntax.FunctionDecl> extensions =
        typeSystem.resolveFunctions(member.name()).stream()
            .filter(candidate -> candidate.kind() == Syntax.FunctionKind.EXTENSION)
            .toList();
    if (!extensions.isEmpty()) {
      if (member.nullSafe()) {
        context.diagnostics.error(
            INVALID_CALL, "null-safe extension calls are not supported", member.span());
        calls.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      CallResolver.CallResolution<Syntax.FunctionDecl> resolution =
          calls.resolveExtensionCall(
              extensions,
              member.typeArguments(),
              member.receiver(),
              call,
              expected,
              member.nameSpan());
      if (resolution == null) return SemanticType.DYNAMIC;
      Syntax.FunctionDecl extension = resolution.declaration();
      SymbolId target = context.model.declarationSymbols().get(extension);
      context.model.putBinding(member.nameSpan(), target);
      return calls.recordExtensionCall(
          member,
          call,
          target,
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (context.builtins.isType(receiver.name())) {
      context.diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
          member.span());
      calls.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    context.diagnostics.error(
        TYPE_MISMATCH, "type '" + receiver.displayName() + "' has no methods", member.span());
    calls.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType memberType(Syntax.Member member, SemanticType expected) {
    SemanticType declarationReference = declarationReferenceType(member, expected);
    if (declarationReference != null) return declarationReference;
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = typeSystem.resolveEnum(enumName.value());
      if (enumDecl != null) {
        context.model.putBinding(enumName.span(), context.model.declarationSymbols().get(enumDecl));
        enumDecl.variants().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .map(context.model.declarationSymbols()::get)
            .ifPresent(id -> context.model.putBinding(member.nameSpan(), id));
        if (enumDecl.variants().stream().noneMatch(value -> value.name().equals(member.name()))) {
          context.diagnostics.error(
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
          context.diagnostics.error(
              INVALID_CALL,
              "enum variant '" + member.name() + "' requires construction arguments",
              member.span());
        }
        return typeSystem.appliedType(enumDecl.name(), enumName.typeArguments(), enumName.span());
      }
    }
    SemanticType nullableReceiverType = typeOf(member.receiver(), null);
    SemanticType receiverType = accessibleReceiverType(member, nullableReceiverType);
    if (member.name().isEmpty()) return SemanticType.DYNAMIC;
    Optional<Symbol> builtinMember = context.builtins.member(receiverType, member.name());
    if (builtinMember.isPresent() && builtinMember.orElseThrow().kind() != SymbolKind.METHOD) {
      Symbol symbol = builtinMember.orElseThrow();
      context.model.putBinding(member.nameSpan(), symbol.id());
      return safeAccessResult(member, nullableReceiverType, symbol.type());
    }
    if (context.builtins.isType(receiverType.name())) {
      context.diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiverType.displayName() + "' has no field '" + member.name() + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    SemanticType aggregateReceiver = aggregateReceiver(receiverType);
    Syntax.AggregateDecl aggregateDecl = typeSystem.resolveAggregate(aggregateReceiver);
    if (aggregateDecl != null) {
      AggregateField resolved = typeSystem.aggregateField(aggregateReceiver, member.name());
      if (resolved != null) {
        Syntax.FieldDecl field = resolved.field();
        Syntax.AggregateDecl owner = resolved.view().declaration();
        if (field.visibility() == Syntax.Visibility.PRIVATE
            && context.body.currentAggregate != owner) {
          context.diagnostics.error(
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
        context.model.putBinding(member.nameSpan(), context.model.declarationSymbols().get(field));
        SemanticType result =
            typeSystem
                .resolveDeclarationType(
                    field.type(), field, typeSystem.aggregateTypeParameters(owner))
                .substitute(typeSystem.aggregateSubstitutions(owner, resolved.view().type()));
        return safeAccessResult(member, nullableReceiverType, result);
      }
      SemanticType method = boundMethodType(member, aggregateReceiver, expected);
      if (method != null) return method;
      context.diagnostics.error(
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
    context.diagnostics.error(
        TYPE_MISMATCH,
        "type '" + receiverType.displayName() + "' has no member '" + member.name() + "'",
        member.span());
    return SemanticType.DYNAMIC;
  }

  private SemanticType declarationReferenceType(Syntax.Member member, SemanticType expected) {
    if (!member.typeArguments().isEmpty()) return null;
    if (member.name().equals("class") && member.receiver() instanceof Syntax.Name typeName) {
      if (typeName.diamond()) {
        context.diagnostics.error(
            INVALID_CALL, "class reference cannot use diamond inference", typeName.span());
        return SemanticType.DYNAMIC;
      }
      if (typeName.value().equals("Void")
          && typeName.typeArguments().isEmpty()
          && !member.nullSafe()) {
        Symbol target = context.builtins.type("Void").orElseThrow();
        context.model.putBinding(typeName.span(), target.id());
        context.model.putBinding(member.nameSpan(), target.id());
        return context.builtins.instantiate("Class", List.of(SemanticType.VOID));
      }
      SemanticType reflected = referencedType(typeName, member.nullSafe());
      if (!isReflectableType(reflected)) {
        context.diagnostics.error(
            TYPE_MISMATCH, "class reference requires a nominal type", member.span());
        return SemanticType.DYNAMIC;
      }
      SymbolId target = context.model.bindings().get(typeName.span());
      if (target != null) context.model.putBinding(member.nameSpan(), target);
      return context.builtins.instantiate("Class", List.of(reflected));
    }
    if (member.nullSafe()) return null;
    if (member.name().equals("field")
        && member.receiver() instanceof Syntax.Member selected
        && selected.receiver() instanceof Syntax.Name ownerName) {
      SemanticType ownerType = referencedType(ownerName);
      AggregateField field = typeSystem.aggregateField(ownerType, selected.name());
      if (field == null) {
        context.diagnostics.error(
            UNKNOWN_NAME,
            "type '" + ownerType.displayName() + "' has no field '" + selected.name() + "'",
            selected.span());
        return SemanticType.DYNAMIC;
      }
      Syntax.FieldDecl declaration = field.field();
      if (declaration.visibility() == Syntax.Visibility.PRIVATE
          && context.body.currentAggregate != field.view().declaration()) {
        context.diagnostics.error(
            UNKNOWN_NAME, "field '" + selected.name() + "' is private", selected.nameSpan());
      }
      SymbolId fieldId = context.model.declarationSymbols().get(declaration);
      context.model.putBinding(selected.nameSpan(), fieldId);
      context.model.putBinding(member.nameSpan(), fieldId);
      SemanticType valueType =
          typeSystem
              .resolveDeclarationType(
                  declaration.type(),
                  declaration,
                  typeSystem.aggregateTypeParameters(field.view().declaration()))
              .substitute(
                  typeSystem.aggregateSubstitutions(
                      field.view().declaration(), field.view().type()));
      return context.builtins.instantiate("Field", List.of(ownerType, valueType));
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
    return typeSystem.resolveCheckedType(reference, context.resolution.activeTypeParameters);
  }

  private SemanticType topLevelFunctionReference(
      Syntax.Member member, Syntax.Name name, SemanticType expected) {
    List<Syntax.FunctionDecl> candidates = typeSystem.resolveFunctions(name.value());
    if (candidates.isEmpty()) {
      context.diagnostics.error(
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
    for (AggregateView view : typeSystem.aggregateViews(ownerType)) {
      Map<String, SemanticType> substitutions =
          typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FunctionDecl method : view.declaration().methods()) {
        if (!method.name().equals(selected.name())) continue;
        if (method.visibility() == Syntax.Visibility.PRIVATE
            && context.body.currentAggregate != view.declaration()) continue;
        Map<String, SemanticType> parameters =
            typeSystem.typeParameters(method, view.declaration());
        List<SemanticType> signature = new ArrayList<>();
        signature.add(view.type());
        method.parameters().stream()
            .map(
                parameter ->
                    typeSystem.resolveDeclarationType(parameter.type(), method, parameters))
            .map(type -> type.substitute(substitutions))
            .forEach(signature::add);
        SemanticType result =
            typeSystem.functionReturnType(method, parameters).substitute(substitutions);
        candidates.add(new FunctionPattern(method, SemanticType.function(result, signature)));
      }
      if (!candidates.isEmpty()) break;
    }
    List<FunctionReferenceResolution> matches = selectFunctionReferences(candidates, expected);
    SemanticType type =
        bindFunctionReference(member, selected.nameSpan(), selected.name(), matches, expected);
    if (!type.equals(SemanticType.DYNAMIC)) {
      context.model.putBinding(
          member.nameSpan(), context.model.bindings().get(selected.nameSpan()));
    }
    return type;
  }

  private SemanticType boundMethodType(
      Syntax.Member member, SemanticType receiver, SemanticType expected) {
    List<FunctionPattern> candidates = new ArrayList<>();
    for (AggregateView view : typeSystem.aggregateViews(receiver)) {
      Map<String, SemanticType> substitutions =
          typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FunctionDecl method : view.declaration().methods()) {
        if (!method.name().equals(member.name())) continue;
        if (method.visibility() == Syntax.Visibility.PRIVATE
            && context.body.currentAggregate != view.declaration()) continue;
        Map<String, SemanticType> parameters =
            typeSystem.typeParameters(method, view.declaration());
        SemanticType pattern =
            SemanticType.function(
                    typeSystem.functionReturnType(method, parameters),
                    method.parameters().stream()
                        .map(
                            parameter ->
                                typeSystem.resolveDeclarationType(
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

  private SemanticType aggregateReceiver(SemanticType receiver) {
    if (receiver.kind() != SemanticType.Kind.TYPE_PARAMETER) return receiver;
    SemanticType bound = context.resolution.typeParameterBounds.get(receiver.identity());
    return bound != null && typeSystem.resolveAggregate(bound) != null ? bound : receiver;
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
      context.diagnostics.error(
          TYPE_MISMATCH,
          matches.isEmpty()
              ? "function reference '" + name + "' requires an unambiguous exact function type"
              : "function reference '" + name + "' is ambiguous",
          member.span());
      return SemanticType.DYNAMIC;
    }
    FunctionReferenceResolution resolution = matches.getFirst();
    context.model.putBinding(
        targetSpan, context.model.declarationSymbols().get(resolution.declaration()));
    context.model.putBinding(
        member.nameSpan(), context.model.declarationSymbols().get(resolution.declaration()));
    context.model.putFunctionReference(member.span(), resolution.reifiedArguments());
    return resolution.functionType();
  }

  private record FunctionPattern(Syntax.FunctionDecl declaration, SemanticType type) {}

  SemanticType accessibleReceiverType(Syntax.Member member, SemanticType receiverType) {
    return typeSystem.receiverType(receiverType, member.nullSafe(), member.receiver().span());
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

  SemanticType analyzeIndex(Syntax.Index index) {
    SemanticType receiverType =
        typeSystem.receiverType(typeOf(index.receiver(), null), false, index.receiver().span());
    SemanticType indexType = typeOf(index.index(), null);
    Optional<BuiltinCatalog.ResolvedIndex> resolved = context.builtins.resolveIndex(receiverType);
    if (resolved.isEmpty()) {
      context.diagnostics.error(
          TYPE_MISMATCH, "only Array, List, and Map can be indexed", index.span());
      return SemanticType.DYNAMIC;
    }
    BuiltinCatalog.ResolvedIndex capability = resolved.orElseThrow();
    context.model.putIndex(
        index.span(),
        new ResolvedIndex(
            capability.kind(),
            capability.keyType(),
            capability.resultType(),
            capability.readIntrinsic(),
            capability.writeIntrinsic()));
    typeSystem.requireType(capability.keyType(), indexType, index.index().span());
    return capability.resultType();
  }

  SemanticType assignmentTargetType(Syntax.Expression target) {
    return switch (target) {
      case Syntax.Name name -> flow.lookupDeclared(name.value(), name.span());
      case Syntax.Member member -> {
        if (member.nullSafe()) {
          context.diagnostics.error(TYPE_MISMATCH, "safe access cannot be assigned", member.span());
        }
        SemanticType receiver = typeOf(member.receiver(), null);
        if (receiver.nonNullable().category() == ValueCategory.VALUE
            && typeSystem.resolveAggregate(receiver.nonNullable()) != null) {
          context.diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", member.span());
        }
        yield memberType(member, null);
      }
      case Syntax.Index index -> analyzeIndex(index);
      case Syntax.Unary unary when unary.operator() == TokenKind.STAR -> {
        SemanticType reference = typeOf(unary.operand(), null);
        if (!reference.isReference()) {
          context.diagnostics.error(
              TYPE_MISMATCH, "dereference assignment requires ref<T>", unary.span());
          yield SemanticType.DYNAMIC;
        }
        context.model.putType(unary.span(), reference.referenceTarget());
        yield reference.referenceTarget();
      }
      default -> {
        context.diagnostics.error(TYPE_MISMATCH, "invalid assignment target", target.span());
        yield SemanticType.DYNAMIC;
      }
    };
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
      Syntax.EnumDecl declaration = typeSystem.resolveEnum(type);
      if (declaration == null) return List.of();
      Map<String, SemanticType> substitutions = typeSystem.enumSubstitutions(declaration, type);
      Map<String, SemanticType> parameters = typeSystem.enumTypeParameters(declaration);
      return declaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.name(),
                      variant.parameters().stream()
                          .map(
                              field ->
                                  typeSystem
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
