package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.BodyAnalysisState.ControlContext;
import dev.w0fv1.norm.frontend.BodyAnalysisState.ControlKind;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.FunctionReferenceResolution;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceRequirement;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.frontend.TypeResolver.AggregateField;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ExpressionChecker implements ExpressionTyping {
  private final BodyAnalysisState body;
  private final dev.w0fv1.norm.builtin.BuiltinSymbols builtins;
  private final DiagnosticBag diagnostics;
  private final CompilationGuard guard;
  private final SemanticModelBuilder model;
  private final TypeResolutionState resolution;
  private final DeclarationAnalyzer declarationAnalyzer;
  private final AnalysisTransaction transactions;

  ExpressionChecker(
      BodyAnalysisState body,
      dev.w0fv1.norm.builtin.BuiltinSymbols builtins,
      DiagnosticBag diagnostics,
      CompilationGuard guard,
      SemanticModelBuilder model,
      TypeResolutionState resolution,
      TypeResolver typeResolver,
      DeclarationAnalyzer declarationAnalyzer,
      AnalysisTransaction transactions,
      FlowAnalyzer flow,
      java.util.function.Consumer<Syntax.Statement> statements,
      java.util.function.BiConsumer<Syntax.ForStatement, Runnable> iterations) {
    this.body = body;
    this.builtins = builtins;
    this.diagnostics = diagnostics;
    this.guard = guard;
    this.model = model;
    this.resolution = resolution;
    this.declarationAnalyzer = declarationAnalyzer;
    this.transactions = transactions;

    this.typeResolver = typeResolver;
    this.flow = flow;
    this.statements = statements;
    numeric = new NumericLiteralTyping(diagnostics);
    closures =
        new ClosureAnalyzer(
            body,
            model,
            resolution,
            diagnostics,
            typeResolver,
            declarationAnalyzer,
            flow,
            this,
            statements);
    collections =
        new CollectionAnalyzer(
            body, builtins, diagnostics, model, typeResolver, flow, this, iterations);
    calls = new CallResolver(diagnostics, model, builtins, resolution, typeResolver, this);
  }

  private final TypeResolver typeResolver;
  final FlowAnalyzer flow;

  final CallResolver calls;
  final ClosureAnalyzer closures;
  private final NumericLiteralTyping numeric;
  private final java.util.function.Consumer<Syntax.Statement> statements;
  private final CollectionAnalyzer collections;

  public SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    return typeOf(expression, expected, List.of());
  }

  public SemanticType typeOf(
      Syntax.Expression expression, SemanticType expected, List<String> contextualNames) {
    return typeOf(expression, expected, contextualNames, Optional.empty());
  }

  public SemanticType typeOf(
      Syntax.Expression expression,
      SemanticType expected,
      List<String> contextualNames,
      Optional<SemanticType> builder) {
    guard.checkpoint();
    SemanticType type =
        switch (expression) {
          case Syntax.IntegerLiteral integer ->
              numeric.numericIntegerType(integer.value(), expected, integer.span());
          case Syntax.DecimalLiteral decimal ->
              numeric.numericDecimalType(decimal.value(), expected, decimal.span());
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
          case Syntax.ArrayLiteral array -> collections.analyze(array, expected);
          case Syntax.IfExpression conditional -> analyzeIfExpression(conditional, expected);
          case Syntax.Name name -> analyzeNameValue(name, expected);
          case Syntax.Unary unary -> analyzeUnary(unary, expected);
          case Syntax.Binary binary -> analyzeBinary(binary, expected);
          case Syntax.Call call -> analyzeCall(call, expected);
          case Syntax.Member member -> memberType(member, expected);
          case Syntax.Lambda lambda ->
              builder.isPresent()
                  ? closures.analyzeBuilderLambda(lambda, expected, builder.orElseThrow())
                  : closures.analyzeLambda(lambda, expected, contextualNames);
          case Syntax.Index index -> analyzeIndex(index);
          case Syntax.SwitchExpression switchExpression ->
              analyzeSwitch(switchExpression, expected);
        };
    if (expected != null && type.equals(SemanticType.DYNAMIC)) {
      type = expected;
    }
    if (expected != null
        && expected.identity().equals("std.core.FieldHandle")
        && !type.identity().equals("std.core.FieldHandle")) {
      SourceSpan nameSpan =
          expression instanceof Syntax.Member member ? member.nameSpan() : expression.span();
      SymbolId fieldId = model.bindings().get(nameSpan);
      Symbol field = model.symbols().get(fieldId);
      SemanticType owner =
          expression instanceof Syntax.Member member && !member.nullSafe()
              ? typeOf(member.receiver(), null)
              : expression instanceof Syntax.Name && flow.findScoped("this") != null
                  ? flow.findScoped("this").declaredType()
                  : SemanticType.DYNAMIC;
      if (field != null
          && field.kind() == SymbolKind.FIELD
          && owner.category() == ValueCategory.IDENTITY
          && !owner.isNullable()) {
        SemanticType descriptor = builtins.instantiate("Field", List.of(owner, type));
        type = builtins.instantiate("FieldHandle", List.of(type));
        model.putCall(
            expression.span(),
            new ResolvedCall(
                ResolvedCall.Kind.FIELD_CAPTURE,
                fieldId,
                nameSpan,
                new dev.w0fv1.norm.semantic.ArgumentBinding(List.of(), Map.of()),
                List.of(),
                List.of(descriptor),
                type));
      }
    }
    model.putType(expression.span(), type);
    if (type.isReference()) {
      this.body.recordReferenceLifetime(expression.span(), referenceLifetime(expression));
    }
    return type;
  }

  LexicalLifetime referenceLifetime(Syntax.Expression expression) {
    LexicalLifetime known = this.body.referenceLifetime(expression.span());
    if (known != null) return known;
    if (expression instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol symbol = flow.findScoped(name.value());
      LexicalLifetime lifetime =
          symbol == null ? null : this.body.scopes().referenceLifetime(symbol);
      return lifetime == null ? LexicalLifetime.unusable() : lifetime;
    }
    if (expression instanceof Syntax.Unary unary && unary.operator() == TokenKind.AMPERSAND) {
      if (unary.operand() instanceof Syntax.Name name) {
        FlowScopes.ScopedSymbol symbol = flow.findScoped(name.value());
        if (symbol != null) {
          SymbolKind kind = scopedSymbol(symbol).kind();
          if (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER) {
            return this.body.scopes().storageLifetime(symbol);
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
      closures.read(symbol, name.span());
      return flow.lookup(name.value(), name.span());
    }
    FlowScopes.ScopedSymbol self = flow.findScoped("this");
    if (self != null && hasProperty(self.declaredType(), name.value())) {
      Syntax.Member member = PropertyAccess.implicit(name);
      return analyzeMethodCall(
          member, PropertyAccess.read(member), expected, typeOf(member.receiver(), null));
    }
    List<Syntax.FunctionDecl> candidates = typeResolver.resolveFunctions(name.value());
    if (!candidates.isEmpty()) {
      if (expected == null || !expected.isFunction()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "function reference '" + name.value() + "' requires an expected function type",
            name.span());
        return SemanticType.DYNAMIC;
      }
      List<FunctionReferenceResolution> matches =
          selectFunctionReferences(
              candidates.stream()
                  .map(candidate -> new FunctionPattern(candidate, functionType(candidate)))
                  .toList(),
              expected);
      if (matches.size() != 1) {
        diagnostics.error(
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
      typeResolver.bindDeclarationUse(name.span(), name.value(), selected);
      model.putFunctionReference(name.span(), resolution.reifiedArguments());
      return resolution.functionType();
    }
    return flow.lookup(name.value(), name.span());
  }

  SemanticType functionType(Syntax.FunctionDecl declaration) {
    Map<String, SemanticType> parameters = typeResolver.functionTypeParameters(declaration);
    return SemanticType.function(
        typeResolver.functionReturnType(declaration, parameters),
        declaration.parameters().stream()
            .map(
                parameter ->
                    typeResolver.resolveDeclarationType(parameter.type(), declaration, parameters))
            .toList());
  }

  Symbol scopedSymbol(FlowScopes.ScopedSymbol scoped) {
    return model.symbols().get(scoped.id());
  }

  Optional<FunctionReferenceResolution> resolveFunctionReference(
      Syntax.FunctionDecl declaration, SemanticType pattern, SemanticType expected) {
    SemanticType target = expected.nonNullable();
    Symbol symbol = model.symbols().get(model.declarationSymbols().get(declaration));
    if (symbol.typeParameters().isEmpty()) {
      return pattern.equals(target)
          ? Optional.of(new FunctionReferenceResolution(declaration, List.of(), pattern))
          : Optional.empty();
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(
            symbol.typeParameters().stream().map(TypeParameterInfo::type).toList(),
            typeResolver.typeRelations);
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
      if (bound != null && !typeResolver.isAssignable(bound, arguments.get(index)))
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
    var patterns =
        new PatternAnalyzer(
            body, model, resolution, diagnostics, typeResolver, declarationAnalyzer, flow, numeric);
    PatternCoverage<SemanticType> coverage = new PatternCoverage<>(patterns);
    ControlContext control = ControlContext.switchExpression(expected);
    FlowScopes.FlowState incoming = this.body.scopes().snapshot();
    List<FlowScopes.FlowState> caseFlows = new ArrayList<>();
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      flow.replaceFlow(incoming);
      flow.pushScope(switchCase.span());
      PatternCoverage.Pattern pattern = patterns.analyzePattern(switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        diagnostics.error(
            INVALID_CONTROL, "switch case is unreachable", switchCase.pattern().span());
      }
      previous.add(pattern);
      try (var controlScope = this.body.enterControl(control)) {
        switchCase.body().forEach(statements);
      }
      flow.popScope();
      caseFlows.add(this.body.scopes().snapshot());
    }
    if (!caseFlows.isEmpty()) {
      FlowScopes.FlowState merged = caseFlows.getFirst();
      for (int index = 1; index < caseFlows.size(); index++) {
        merged = flow.mergeFlows(incoming, merged, caseFlows.get(index));
      }
      flow.replaceFlow(merged);
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      diagnostics.error(INVALID_CONTROL, "switch is not exhaustive", switchExpression.span());
    }
    SemanticType result = control.resultType();
    if (result == null) return SemanticType.VOID;
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      if (!StatementFlow.definitelyYields(switchCase.body())) {
        diagnostics.error(
            INVALID_CONTROL, "switch expression case must produce a value", switchCase.span());
      }
    }
    return controlResult(control, switchExpression.span());
  }

  SemanticType analyzeIfExpression(Syntax.IfExpression expression, SemanticType expected) {
    Syntax.IfStatement statement = IfExpressionLowering.statement(expression);
    ControlContext control = ControlContext.switchExpression(expected);
    try (var controlScope = this.body.enterControl(control)) {
      statements.accept(statement);
    }
    if (!StatementFlow.definitelyYields(statement.thenBody())
        || !StatementFlow.definitelyYields(statement.elseBody())) {
      diagnostics.error(
          INVALID_CONTROL, "if expression branches must produce a value", expression.span());
    }
    return controlResult(control, expression.span());
  }

  private SemanticType controlResult(ControlContext control, SourceSpan span) {
    SemanticType result = control.resultType();
    if (result == null) return SemanticType.VOID;
    if (result.isReference()) {
      LexicalLifetime lifetime =
          control.referenceLifetime() == null
              ? LexicalLifetime.unusable()
              : control.referenceLifetime();
      LexicalLifetime useLifetime = this.body.scopes().currentLifetime();
      if (!lifetime.outlives(useLifetime)) {
        diagnostics.error(
            INVALID_CONTROL, "reference cannot outlive the addressed storage location", span);
        lifetime = useLifetime;
      }
      this.body.recordReferenceLifetime(span, lifetime);
    }
    return result;
  }

  void analyzeBreak(Syntax.BreakStatement statement) {
    if (this.body.currentControl() == null) {
      diagnostics.error(
          INVALID_CONTROL, "break is only valid inside for or switch", statement.span());
      if (statement.value() != null) typeOf(statement.value(), null);
      return;
    }
    ControlContext control = this.body.currentControl();
    if (control.kind() != ControlKind.SWITCH) {
      if (statement.value() != null) {
        diagnostics.error(INVALID_CONTROL, "loop break cannot produce a value", statement.span());
        typeOf(statement.value(), null);
      }
      return;
    }
    if (statement.value() == null) {
      diagnostics.error(INVALID_CONTROL, "switch break must produce a value", statement.span());
      return;
    }
    SemanticType actual = typeOf(statement.value(), control.resultType());
    if (actual.isReference()) {
      control.mergeReferenceLifetime(referenceLifetime(statement.value()));
    }
    if (control.resultType() == null || control.resultType().equals(SemanticType.DYNAMIC)) {
      control.setResultType(actual);
    } else {
      typeResolver.requireAssignable(control.resultType(), actual, statement.value().span());
    }
  }

  public TypeProbe probeType(Syntax.Expression expression, SemanticType expected) {
    return probeType(expression, expected, List.of());
  }

  public TypeProbe probeType(
      Syntax.Expression expression, SemanticType expected, List<String> contextualNames) {
    return probeType(expression, expected, contextualNames, Optional.empty());
  }

  public TypeProbe probeType(
      Syntax.Expression expression,
      SemanticType expected,
      List<String> contextualNames,
      Optional<SemanticType> builder) {
    try (var probe = transactions.probe()) {
      SemanticType type = typeOf(expression, expected, contextualNames, builder);
      boolean hasErrors = probe.hasErrors();
      return new TypeProbe(type, hasErrors);
    }
  }

  SemanticType analyzeNull(Syntax.NullLiteral literal, SemanticType expected) {
    if (expected == null || expected.equals(SemanticType.DYNAMIC)) {
      diagnostics.error(UNTYPED_NULL, "null requires an expected nullable type", literal.span());
      return SemanticType.DYNAMIC;
    }
    if (!expected.isNullable()) {
      diagnostics.error(
          NULLABILITY_MISMATCH,
          "null is not assignable to " + expected.displayName(),
          literal.span());
      return SemanticType.DYNAMIC;
    }
    return expected;
  }

  SemanticType analyzeUnary(Syntax.Unary unary, SemanticType expected) {
    if (unary.operator() == TokenKind.THROW) {
      statements.accept(new Syntax.ThrowStatement(unary.operand(), unary.span()));
      return expected == null ? SemanticType.VOID : expected;
    }
    if (unary.operator() == TokenKind.BANG_BANG) {
      SemanticType operand = typeOf(unary.operand(), null);
      if (operand.equals(SemanticType.NULL) || operand.equals(SemanticType.VOID)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "non-null assertion requires a value with an inhabitable non-null type",
            unary.span());
        return SemanticType.DYNAMIC;
      }
      return operand.nonNullable();
    }
    if (unary.operator() == TokenKind.AMPERSAND) return analyzeAddress(unary);
    if (unary.operator() == TokenKind.STAR) {
      SemanticType operand = typeOf(unary.operand(), null);
      if (!operand.isReference()) {
        diagnostics.error(TYPE_MISMATCH, "dereference requires ref<T>", unary.span());
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
      typeResolver.requireType(SemanticType.BOOLEAN, operand, unary.span());
      return SemanticType.BOOLEAN;
    }
    if (!NumericTypes.isLeaf(operand)) {
      diagnostics.error(TYPE_MISMATCH, "numeric negation requires a numeric leaf", unary.span());
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
                    && (!this.body.externalToLambda(scoped.id()))
                || kind == SymbolKind.FIELD
                    && this.body.currentAggregate() != null
                    && this.body.currentAggregate().kind() != Syntax.AggregateKind.VALUE;
      }
    } else if (target instanceof Syntax.Member member && !member.nullSafe()) {
      SemanticType receiver = model.semanticTypes().get(member.receiver().span());
      SymbolId fieldId = model.bindings().get(member.nameSpan());
      Symbol field = fieldId == null ? null : model.symbols().get(fieldId);
      addressable =
          receiver != null
              && receiver.nonNullable().category() == ValueCategory.IDENTITY
              && field != null
              && field.kind() == SymbolKind.FIELD;
    }
    if (!addressable) {
      diagnostics.error(
          TYPE_MISMATCH, "address-of requires a writable storage location", target.span());
      return SemanticType.DYNAMIC;
    }
    if (targetType.category() != ValueCategory.VALUE) {
      diagnostics.error(TYPE_MISMATCH, "ref target must be a value type", target.span());
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
        diagnostics.error(TYPE_MISMATCH, "left side of ?? must be nullable", binary.left().span());
      }
      SemanticType result = left.equals(SemanticType.DYNAMIC) ? expected : left.nonNullable();
      SemanticType right = typeOf(binary.right(), result);
      if (result == null) return right;
      typeResolver.requireAssignable(result, right, binary.right().span());
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
        FlowScopes.FlowState incoming = this.body.scopes().snapshot();
        flow.pushScope(binary.right().span());
        flow.applyNarrowings(flow.narrowingsFor(binary.left(), true));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        flow.popScope();
        flow.replaceFlow(incoming);
      } else if (binary.operator() == TokenKind.OR_OR) {
        FlowScopes.FlowState incoming = this.body.scopes().snapshot();
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
        yield numeric.requireNumericLeaves(left, right, binary.span())
            ? left
            : SemanticType.DYNAMIC;
      }
      case MINUS, STAR, SLASH, PERCENT -> {
        yield numeric.requireNumericLeaves(left, right, binary.span())
            ? left
            : SemanticType.DYNAMIC;
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        numeric.requireNumericLeaves(left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case AND_AND, OR_OR -> {
        typeResolver.requireBoth(SemanticType.BOOLEAN, left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        if (!typeResolver.isAssignable(left, right) && !typeResolver.isAssignable(right, left)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "cannot compare " + left.displayName() + " with " + right.displayName(),
              binary.span());
        }
        yield SemanticType.BOOLEAN;
      }
      default -> SemanticType.DYNAMIC;
    };
  }

  SemanticType analyzeCall(Syntax.Call call, SemanticType expected) {
    if (call.callee() instanceof Syntax.Name name) {
      return analyzeNamedCall(name, call, expected);
    }
    if (call.callee() instanceof Syntax.Member member) {
      if (member.receiver() instanceof Syntax.Name receiverName
          && (typeResolver.resolveEnum(receiverName.value()) != null
              || !builtins.typeMembers(receiverName.value(), member.name()).isEmpty())) {
        return analyzeMethodCall(member, call, expected, null);
      }
      SemanticType nullableReceiver = typeOf(member.receiver(), null);
      SemanticType memberType = memberTypeWithoutDiagnostics(member, nullableReceiver);
      if (memberType != null && memberType.isFunction()) {
        model.putType(member.span(), memberType);
        return analyzeFunctionInvocation(call, memberType, this.body.currentCallable());
      }
      return analyzeMethodCall(member, call, expected, nullableReceiver);
    }
    SemanticType calleeType = typeOf(call.callee(), null);
    if (calleeType.isFunction())
      return analyzeFunctionInvocation(call, calleeType, this.body.currentCallable());
    diagnostics.error(INVALID_CALL, "expression is not callable", call.callee().span());
    calls.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType analyzeFunctionInvocation(Syntax.Call call, SemanticType function, SymbolId target) {
    if (function.isUnknownFunction()) {
      diagnostics.error(
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
    Symbol contract = model.symbols().get(target);
    List<ParameterInfo> declaredParameters =
        contract != null && contract.type().isFunction() ? contract.parameters() : List.of();
    List<ParameterInfo> parameters =
        java.util.stream.IntStream.range(0, function.functionParameterTypes().size())
            .mapToObj(
                index ->
                    new ParameterInfo(
                        !declaredParameters.isEmpty()
                            ? declaredParameters.get(index).name()
                            : "argument" + index,
                        function.functionParameterTypes().get(index),
                        false,
                        !declaredParameters.isEmpty()
                            ? declaredParameters.get(index).policy().callbackParameterNames()
                            : List.of(),
                        !declaredParameters.isEmpty()
                            ? declaredParameters.get(index).policy().labelPolicy()
                            : dev.w0fv1.norm.value.ParameterPolicy.LabelPolicy.POSITIONAL_ONLY))
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

  boolean analyzePropertyAssignment(Syntax.Assignment assignment) {
    Syntax.Member member;
    if (assignment.target() instanceof Syntax.Member explicit) {
      member = explicit;
    } else if (assignment.target() instanceof Syntax.Name name
        && flow.findScoped(name.value()) == null
        && flow.findScoped("this") != null
        && hasProperty(flow.findScoped("this").declaredType(), name.value())) {
      member = PropertyAccess.implicit(name);
    } else {
      return false;
    }
    SemanticType receiver = typeOf(member.receiver(), null);
    if (!hasProperty(receiver.nonNullable(), member.name())) return false;
    if (member.nullSafe())
      diagnostics.error(TYPE_MISMATCH, "safe access cannot be assigned", member.span());
    analyzeMethodCall(member, PropertyAccess.write(assignment), SemanticType.VOID, receiver);
    return true;
  }

  private boolean hasProperty(SemanticType receiver, String name) {
    if (typeResolver.resolveAggregate(receiver) == null) return false;
    return typeResolver.aggregateViews(receiver).stream()
        .flatMap(view -> view.declaration().methods().stream())
        .anyMatch(
            method -> method.kind() == Syntax.FunctionKind.GETTER && method.name().equals(name));
  }

  SemanticType memberTypeWithoutDiagnostics(Syntax.Member member, SemanticType nullableReceiver) {
    SemanticType receiver = nullableReceiver.nonNullable();
    Syntax.AggregateDecl owner = typeResolver.resolveAggregate(receiver);
    if (owner == null) return null;
    AggregateField resolved = typeResolver.aggregateField(receiver, member.name());
    if (resolved == null) {
      return hasProperty(receiver, member.name())
          ? analyzeMethodCall(member, PropertyAccess.read(member), null, nullableReceiver)
          : null;
    }
    Syntax.FieldDecl field = resolved.field();
    accessibleReceiverType(member, nullableReceiver);
    owner = resolved.view().declaration();
    if (field.visibility() == Syntax.Visibility.PRIVATE && this.body.currentAggregate() != owner) {
      diagnostics.error(
          UNKNOWN_NAME,
          "field '"
              + member.name()
              + "' is private in "
              + TypeResolver.aggregateKeyword(owner)
              + " '"
              + owner.name()
              + "'",
          member.nameSpan());
    }
    model.putBinding(member.nameSpan(), model.declarationSymbols().get(field));
    SemanticType type =
        typeResolver
            .resolveDeclarationType(
                field.type(), field, typeResolver.aggregateTypeParameters(owner))
            .substitute(typeResolver.aggregateSubstitutions(owner, resolved.view().type()));
    return safeAccessResult(member, nullableReceiver, type);
  }

  SemanticType analyzeNamedCall(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    String callee = name.value();
    FlowScopes.ScopedSymbol scoped = flow.findScoped(callee);
    FlowScopes.ScopedSymbol self = flow.findScoped("this");
    if (scoped == null && self != null && hasProperty(self.declaredType(), callee)) {
      SemanticType property = typeOf(name, null);
      if (property.isFunction())
        return analyzeFunctionInvocation(call, property, this.body.currentCallable());
      diagnostics.error(INVALID_CALL, "property is not callable", name.span());
      calls.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    if (scoped != null && scoped.declaredType().isFunction()) {
      SemanticType function = scoped.declaredType();
      model.putBinding(name.span(), scoped.id());
      model.putType(name.span(), function);
      return analyzeFunctionInvocation(call, function, scoped.id());
    }
    builtins.type(callee).ifPresent(symbol -> model.putBinding(name.span(), symbol.id()));
    List<Symbol> builtinFunctions =
        builtins.globals(callee, resolution.program().span().source().id());
    if (!builtinFunctions.isEmpty()) {
      if (name.diamond()) {
        diagnostics.error(
            INVALID_CALL, "diamond is only valid for generic constructors", name.span());
      }
      var invocation =
          calls.resolveSymbolCall(
              builtinFunctions, name.typeArguments(), call, expected, name.span(), false);
      if (invocation == null) return SemanticType.DYNAMIC;
      Symbol symbol = invocation.declaration();
      model.putSymbolIfAbsent(symbol.id(), symbol);
      model.putBinding(name.span(), symbol.id());
      return calls.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          invocation.parameters(),
          invocation.reifiedArguments(),
          invocation.result());
    }
    Syntax.AggregateDecl aggregateDecl = typeResolver.resolveAggregate(callee);
    boolean builtinType = builtins.type(callee).isPresent();
    if (builtinType || aggregateDecl != null) {
      if (aggregateDecl != null)
        typeResolver.bindDeclarationUse(name.span(), callee, aggregateDecl);
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
    List<Syntax.FunctionDecl> functionCandidates = typeResolver.resolveFunctions(callee);
    if (!functionCandidates.isEmpty() && name.diamond()) {
      diagnostics.error(
          INVALID_CALL, "diamond is only valid for generic constructors", name.span());
    }
    CallResolver.CallResolution<Syntax.FunctionDecl> resolution =
        calls.resolveSourceCall(
            functionCandidates, name.typeArguments(), call, expected, Map.of(), name.span(), false);
    if (resolution != null) {
      typeResolver.bindDeclarationUse(name.span(), callee, resolution.declaration());
      return calls.recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CALLABLE,
          model.declarationSymbols().get(resolution.declaration()),
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (!functionCandidates.isEmpty()) return SemanticType.DYNAMIC;
    diagnostics.error(UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
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
      Syntax.EnumDecl enumDecl = typeResolver.resolveEnum(enumName.value());
      if (enumDecl != null) {
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant == null) {
          diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no variant '" + member.name() + "'",
              member.nameSpan());
          calls.analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        Symbol variantSymbol = model.symbols().get(model.declarationSymbols().get(variant));
        model.putBinding(enumName.span(), model.declarationSymbols().get(enumDecl));
        model.putBinding(member.nameSpan(), variantSymbol.id());
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
      List<Symbol> typeMethods = builtins.typeMembers(typeName.value(), member.name());
      if (!typeMethods.isEmpty()) {
        builtins
            .type(typeName.value())
            .ifPresent(symbol -> model.putBinding(typeName.span(), symbol.id()));
        var invocation =
            calls.resolveSymbolCall(
                typeMethods, member.typeArguments(), call, expected, member.span(), false);
        if (invocation == null) return SemanticType.DYNAMIC;
        Symbol symbol = invocation.declaration();
        model.putBinding(member.nameSpan(), symbol.id());
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
    SemanticType receiver = nullableReceiver.nonNullable();
    if (member.name().isEmpty()) {
      calls.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    List<Symbol> builtinMembers = builtins.members(receiver, member.name());
    if (!builtinMembers.isEmpty()) {
      accessibleReceiverType(member, nullableReceiver);
      List<Symbol> builtinMethods =
          builtinMembers.stream().filter(symbol -> symbol.kind() == SymbolKind.METHOD).toList();
      if (builtinMethods.isEmpty()) {
        diagnostics.error(
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
      model.putBinding(member.nameSpan(), symbol.id());
      List<SemanticType> reifiedArguments = invocation.reifiedArguments();
      dev.w0fv1.norm.abi.IntrinsicId intrinsic = builtins.intrinsic(symbol.id()).orElse(null);
      if ((intrinsic == dev.w0fv1.norm.abi.IntrinsicId.CLASS_ANNOTATION
              || intrinsic == dev.w0fv1.norm.abi.IntrinsicId.FIELD_ANNOTATION)
          && !reifiedArguments.isEmpty()
          && typeResolver.resolveAnnotation(reifiedArguments.getFirst().nonNullable()) == null) {
        diagnostics.error(
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
    Syntax.AggregateDecl aggregateDecl = typeResolver.resolveAggregate(aggregateReceiver);
    if (aggregateDecl != null) {
      if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE && member.name().equals("copy")) {
        accessibleReceiverType(member, nullableReceiver);
        model.putBinding(member.nameSpan(), model.copyMethod(receiver.identity()));
        typeResolver.validateTypeArgumentCount(
            member.name(), 0, member.typeArguments(), member.span());
        member
            .typeArguments()
            .forEach(
                argument -> typeResolver.resolveCheckedType(argument, resolution.parameters()));
        return calls.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.COPY,
            model.copyMethod(receiver.identity()),
            List.of(),
            List.of(),
            safeAccessResult(member, nullableReceiver, receiver));
      }
      boolean foundMethod =
          typeResolver.aggregateViews(aggregateReceiver).stream()
              .flatMap(view -> view.declaration().methods().stream())
              .anyMatch(method -> method.name().equals(member.name()));
      var accessibleMethods =
          typeResolver.aggregateMethods(
              aggregateReceiver, member.name(), this.body.currentAggregate());
      boolean foundAccessibleMethod = !accessibleMethods.isEmpty();
      if (foundAccessibleMethod) {
        accessibleReceiverType(member, nullableReceiver);
        CallResolver.CallResolution<Syntax.FunctionDecl> resolution =
            calls.resolveAggregateCall(accessibleMethods, member, call, expected, nullableReceiver);
        if (resolution == null) return SemanticType.DYNAMIC;
        Syntax.FunctionDecl method = resolution.declaration();
        model.putBinding(member.nameSpan(), model.declarationSymbols().get(method));
        return calls.recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.CALLABLE,
            model.declarationSymbols().get(method),
            resolution.parameters(),
            resolution.reifiedArguments(),
            safeAccessResult(member, nullableReceiver, resolution.result()));
      }
      if (foundMethod) {
        if (!foundAccessibleMethod) {
          diagnostics.error(
              UNKNOWN_NAME,
              "method '"
                  + member.name()
                  + "' is private in "
                  + TypeResolver.aggregateKeyword(aggregateDecl)
                  + " '"
                  + aggregateDecl.name()
                  + "'",
              member.nameSpan());
          calls.analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        diagnostics.error(
            INVALID_CALL, "no method overload accepts the supplied arguments", call.span());
        calls.analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
    }
    List<InterfaceRequirement> interfaceMethods =
        typeResolver.interfaceRequirements(receiver).stream()
            .filter(requirement -> requirement.method().name().equals(member.name()))
            .toList();
    CallResolver.CallResolution<InterfaceRequirement> interfaceResolution =
        calls.resolveInterfaceCall(interfaceMethods, member, call, expected, nullableReceiver);
    if (!interfaceMethods.isEmpty()) accessibleReceiverType(member, nullableReceiver);
    if (!interfaceMethods.isEmpty() && interfaceResolution == null) return SemanticType.DYNAMIC;
    if (interfaceResolution != null) {
      InterfaceRequirement interfaceMethod = interfaceResolution.declaration();
      Symbol target = model.symbols().get(model.declarationSymbols().get(interfaceMethod.method()));
      model.putBinding(member.nameSpan(), target.id());
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
        typeResolver.resolveFunctions(member.name()).stream()
            .filter(candidate -> candidate.kind() == Syntax.FunctionKind.EXTENSION)
            .toList();
    if (!extensions.isEmpty()) {
      if (member.nullSafe()) {
        diagnostics.error(
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
      SymbolId target = model.declarationSymbols().get(extension);
      model.putBinding(member.nameSpan(), target);
      return calls.recordExtensionCall(
          member,
          call,
          target,
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    accessibleReceiverType(member, nullableReceiver);
    if (builtins.isType(receiver.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
          member.span());
      calls.analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH, "type '" + receiver.displayName() + "' has no methods", member.span());
    calls.analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType memberType(Syntax.Member member, SemanticType expected) {
    SemanticType declarationReference = declarationReferenceType(member, expected);
    if (declarationReference != null) return declarationReference;
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = typeResolver.resolveEnum(enumName.value());
      if (enumDecl != null) {
        model.putBinding(enumName.span(), model.declarationSymbols().get(enumDecl));
        enumDecl.variants().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .map(model.declarationSymbols()::get)
            .ifPresent(id -> model.putBinding(member.nameSpan(), id));
        if (enumDecl.variants().stream().noneMatch(value -> value.name().equals(member.name()))) {
          diagnostics.error(
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
          diagnostics.error(
              INVALID_CALL,
              "enum variant '" + member.name() + "' requires construction arguments",
              member.span());
        }
        return typeResolver.appliedType(enumDecl.name(), enumName.typeArguments(), enumName.span());
      }
    }
    SemanticType nullableReceiverType = typeOf(member.receiver(), null);
    SemanticType receiverType = accessibleReceiverType(member, nullableReceiverType);
    if (member.name().isEmpty()) return SemanticType.DYNAMIC;
    Optional<Symbol> builtinMember = builtins.member(receiverType, member.name());
    if (builtinMember.isPresent() && builtinMember.orElseThrow().kind() != SymbolKind.METHOD) {
      Symbol symbol = builtinMember.orElseThrow();
      model.putBinding(member.nameSpan(), symbol.id());
      return safeAccessResult(member, nullableReceiverType, symbol.type());
    }
    if (builtins.isType(receiverType.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiverType.displayName() + "' has no field '" + member.name() + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    SemanticType aggregateReceiver = aggregateReceiver(receiverType);
    Syntax.AggregateDecl aggregateDecl = typeResolver.resolveAggregate(aggregateReceiver);
    if (aggregateDecl != null) {
      AggregateField resolved = typeResolver.aggregateField(aggregateReceiver, member.name());
      if (resolved != null) {
        Syntax.FieldDecl field = resolved.field();
        Syntax.AggregateDecl owner = resolved.view().declaration();
        if (field.visibility() == Syntax.Visibility.PRIVATE
            && this.body.currentAggregate() != owner) {
          diagnostics.error(
              UNKNOWN_NAME,
              "field '"
                  + member.name()
                  + "' is private in "
                  + TypeResolver.aggregateKeyword(owner)
                  + " '"
                  + owner.name()
                  + "'",
              member.nameSpan());
        }
        model.putBinding(member.nameSpan(), model.declarationSymbols().get(field));
        SemanticType result =
            typeResolver
                .resolveDeclarationType(
                    field.type(), field, typeResolver.aggregateTypeParameters(owner))
                .substitute(typeResolver.aggregateSubstitutions(owner, resolved.view().type()));
        return safeAccessResult(member, nullableReceiverType, result);
      }
      if (hasProperty(aggregateReceiver, member.name()))
        return analyzeMethodCall(
            member, PropertyAccess.read(member), expected, nullableReceiverType);
      SemanticType method = boundMethodType(member, aggregateReceiver, expected);
      if (method != null) return method;
      diagnostics.error(
          UNKNOWN_NAME,
          TypeResolver.aggregateKeyword(aggregateDecl)
              + " '"
              + receiverType.displayName()
              + "' has no field '"
              + member.name()
              + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH,
        "type '" + receiverType.displayName() + "' has no member '" + member.name() + "'",
        member.span());
    return SemanticType.DYNAMIC;
  }

  private SemanticType declarationReferenceType(Syntax.Member member, SemanticType expected) {
    if (!member.typeArguments().isEmpty()) return null;
    if (member.name().equals("class") && member.receiver() instanceof Syntax.Name typeName) {
      if (typeName.diamond()) {
        diagnostics.error(
            INVALID_CALL, "class reference cannot use diamond inference", typeName.span());
        return SemanticType.DYNAMIC;
      }
      if (typeName.value().equals("Void")
          && typeName.typeArguments().isEmpty()
          && !member.nullSafe()) {
        Symbol target = builtins.type("Void").orElseThrow();
        model.putBinding(typeName.span(), target.id());
        model.putDeclarationOperator(member.nameSpan(), target.id());
        return builtins.instantiate("Class", List.of(SemanticType.VOID));
      }
      SemanticType reflected = referencedType(typeName, member.nullSafe());
      if (!isReflectableType(reflected)) {
        diagnostics.error(TYPE_MISMATCH, "class reference requires a nominal type", member.span());
        return SemanticType.DYNAMIC;
      }
      SymbolId target = model.bindings().get(typeName.span());
      if (target != null) model.putDeclarationOperator(member.nameSpan(), target);
      return builtins.instantiate("Class", List.of(reflected));
    }
    if (member.nullSafe()) return null;
    if (member.name().equals("field")
        && member.receiver() instanceof Syntax.Member selected
        && selected.receiver() instanceof Syntax.Name ownerName) {
      SemanticType ownerType = referencedType(ownerName);
      AggregateField field = typeResolver.aggregateField(ownerType, selected.name());
      if (field == null) {
        diagnostics.error(
            UNKNOWN_NAME,
            "type '" + ownerType.displayName() + "' has no field '" + selected.name() + "'",
            selected.span());
        return SemanticType.DYNAMIC;
      }
      Syntax.FieldDecl declaration = field.field();
      if (declaration.visibility() == Syntax.Visibility.PRIVATE
          && this.body.currentAggregate() != field.view().declaration()) {
        diagnostics.error(
            UNKNOWN_NAME, "field '" + selected.name() + "' is private", selected.nameSpan());
      }
      SymbolId fieldId = model.declarationSymbols().get(declaration);
      model.putBinding(selected.nameSpan(), fieldId);
      model.putDeclarationOperator(member.nameSpan(), fieldId);
      SemanticType valueType =
          typeResolver
              .resolveDeclarationType(
                  declaration.type(),
                  declaration,
                  typeResolver.aggregateTypeParameters(field.view().declaration()))
              .substitute(
                  typeResolver.aggregateSubstitutions(
                      field.view().declaration(), field.view().type()));
      return builtins.instantiate("Field", List.of(ownerType, valueType));
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
    return typeResolver.resolveCheckedType(reference, resolution.parameters());
  }

  private SemanticType topLevelFunctionReference(
      Syntax.Member member, Syntax.Name name, SemanticType expected) {
    List<Syntax.FunctionDecl> candidates = typeResolver.resolveFunctions(name.value());
    if (candidates.isEmpty()) {
      diagnostics.error(UNKNOWN_NAME, "cannot find function '" + name.value() + "'", name.span());
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
    for (var candidate :
        typeResolver.aggregateMethods(ownerType, selected.name(), this.body.currentAggregate())) {
      var view = candidate.owner();
      var method = candidate.declaration();
      Map<String, SemanticType> substitutions =
          typeResolver.aggregateSubstitutions(view.declaration(), view.type());
      Map<String, SemanticType> parameters =
          typeResolver.typeParameters(method, view.declaration());
      List<SemanticType> signature = new ArrayList<>();
      signature.add(view.type());
      method.parameters().stream()
          .map(
              parameter ->
                  typeResolver.resolveDeclarationType(parameter.type(), method, parameters))
          .map(type -> type.substitute(substitutions))
          .forEach(signature::add);
      SemanticType result =
          typeResolver.functionReturnType(method, parameters).substitute(substitutions);
      candidates.add(new FunctionPattern(method, SemanticType.function(result, signature)));
    }
    List<FunctionReferenceResolution> matches = selectFunctionReferences(candidates, expected);
    return bindFunctionReference(member, selected.nameSpan(), selected.name(), matches, expected);
  }

  private SemanticType boundMethodType(
      Syntax.Member member, SemanticType receiver, SemanticType expected) {
    List<FunctionPattern> candidates = new ArrayList<>();
    for (var candidate :
        typeResolver.aggregateMethods(receiver, member.name(), this.body.currentAggregate())) {
      var view = candidate.owner();
      var method = candidate.declaration();
      Map<String, SemanticType> substitutions =
          typeResolver.aggregateSubstitutions(view.declaration(), view.type());
      Map<String, SemanticType> parameters =
          typeResolver.typeParameters(method, view.declaration());
      SemanticType pattern =
          SemanticType.function(
                  typeResolver.functionReturnType(method, parameters),
                  method.parameters().stream()
                      .map(
                          parameter ->
                              typeResolver.resolveDeclarationType(
                                  parameter.type(), method, parameters))
                      .toList())
              .substitute(substitutions);
      candidates.add(new FunctionPattern(method, pattern));
    }
    if (candidates.isEmpty()) return null;
    List<FunctionReferenceResolution> matches = selectFunctionReferences(candidates, expected);
    return bindFunctionReference(member, member.nameSpan(), member.name(), matches, expected);
  }

  private SemanticType aggregateReceiver(SemanticType receiver) {
    if (receiver.kind() != SemanticType.Kind.TYPE_PARAMETER) return receiver;
    SemanticType bound = resolution.upperBound(receiver.identity());
    return bound != null && typeResolver.resolveAggregate(bound) != null ? bound : receiver;
  }

  private List<FunctionReferenceResolution> selectFunctionReferences(
      List<FunctionPattern> candidates, SemanticType expected) {
    if (expected == null || expected.isUnknownFunction()) {
      if (candidates.size() != 1) return List.of();
      FunctionPattern candidate = candidates.getFirst();
      Symbol symbol = model.symbols().get(model.declarationSymbols().get(candidate.declaration()));
      Map<String, SemanticType> substitutions = new java.util.LinkedHashMap<>();
      symbol
          .typeParameters()
          .forEach(
              parameter ->
                  substitutions.put(parameter.type().identity(), SemanticType.EXISTENTIAL));
      return List.of(
          new FunctionReferenceResolution(
              candidate.declaration(),
              symbol.typeParameters().stream().map(parameter -> SemanticType.EXISTENTIAL).toList(),
              candidate.type().substitute(substitutions)));
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
      diagnostics.error(
          TYPE_MISMATCH,
          matches.isEmpty()
              ? "function reference '" + name + "' requires an unambiguous exact function type"
              : "function reference '" + name + "' is ambiguous",
          member.span());
      return SemanticType.DYNAMIC;
    }
    FunctionReferenceResolution resolution = matches.getFirst();
    model.putBinding(targetSpan, model.declarationSymbols().get(resolution.declaration()));
    if (!member.nameSpan().equals(targetSpan)) {
      model.putDeclarationOperator(
          member.nameSpan(), model.declarationSymbols().get(resolution.declaration()));
    }
    model.putFunctionReference(member.span(), resolution.reifiedArguments());
    return resolution.functionType();
  }

  private record FunctionPattern(Syntax.FunctionDecl declaration, SemanticType type) {}

  SemanticType accessibleReceiverType(Syntax.Member member, SemanticType receiverType) {
    return typeResolver.receiverType(receiverType, member.nullSafe(), member.receiver().span());
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
        typeResolver.receiverType(typeOf(index.receiver(), null), false, index.receiver().span());
    SemanticType indexType = typeOf(index.index(), null);
    Optional<BuiltinCatalog.ResolvedIndex> resolved = builtins.resolveIndex(receiverType);
    if (resolved.isEmpty()) {
      diagnostics.error(TYPE_MISMATCH, "only Array, List, and Map can be indexed", index.span());
      return SemanticType.DYNAMIC;
    }
    BuiltinCatalog.ResolvedIndex capability = resolved.orElseThrow();
    model.putIndex(
        index.span(),
        new ResolvedIndex(
            capability.kind(),
            capability.keyType(),
            capability.resultType(),
            capability.readIntrinsic(),
            capability.writeIntrinsic()));
    typeResolver.requireType(capability.keyType(), indexType, index.index().span());
    return capability.resultType();
  }

  boolean constructingOwnValue() {
    Symbol callable = model.symbols().get(this.body.currentCallable());
    return this.body.currentAggregate() != null
        && this.body.currentAggregate().kind() == Syntax.AggregateKind.VALUE
        && callable != null
        && callable.kind() == SymbolKind.CONSTRUCTOR
        && !this.body.inLambda();
  }

  SemanticType assignmentTargetType(Syntax.Expression target) {
    return switch (target) {
      case Syntax.Name name -> flow.lookupDeclared(name.value(), name.span());
      case Syntax.Member member -> {
        if (member.nullSafe()) {
          diagnostics.error(TYPE_MISMATCH, "safe access cannot be assigned", member.span());
        }
        SemanticType receiver = typeOf(member.receiver(), null);
        if (receiver.nonNullable().category() == ValueCategory.VALUE
            && typeResolver.resolveAggregate(receiver.nonNullable()) != null
            && !(constructingOwnValue()
                && member.receiver() instanceof Syntax.Name name
                && name.value().equals("this"))) {
          diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", member.span());
        }
        yield memberType(member, null);
      }
      case Syntax.Index index -> analyzeIndex(index);
      case Syntax.Unary unary when unary.operator() == TokenKind.STAR -> {
        SemanticType reference = typeOf(unary.operand(), null);
        if (!reference.isReference()) {
          diagnostics.error(TYPE_MISMATCH, "dereference assignment requires ref<T>", unary.span());
          yield SemanticType.DYNAMIC;
        }
        model.putType(unary.span(), reference.referenceTarget());
        yield reference.referenceTarget();
      }
      default -> {
        diagnostics.error(TYPE_MISMATCH, "invalid assignment target", target.span());
        yield SemanticType.DYNAMIC;
      }
    };
  }
}
