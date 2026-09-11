package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.TypeResolver.AggregateView;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.BlockResults;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class BodyAnalyzer {
  private final BodyAnalysisState body;
  private final SemanticModelBuilder model;
  private final TypeResolutionState resolution;
  private final DiagnosticBag diagnostics;
  private final CompilationGuard guard;
  private final DeclarationAnalyzer declarationAnalyzer;
  private final DeclarationPolicyResolver declarationPolicies;

  BodyAnalyzer(
      BodyAnalysisState body,
      SemanticModelBuilder model,
      TypeResolutionState resolution,
      DiagnosticBag diagnostics,
      CompilationGuard guard,
      dev.w0fv1.norm.builtin.BuiltinSymbols builtins,
      TypeResolver typeResolver,
      DeclarationAnalyzer declarationAnalyzer,
      DeclarationPolicyResolver declarationPolicies,
      AnalysisTransaction transactions) {
    this.body = body;
    this.model = model;
    this.resolution = resolution;
    this.diagnostics = diagnostics;
    this.guard = guard;
    this.declarationAnalyzer = declarationAnalyzer;
    this.declarationPolicies = declarationPolicies;

    this.typeResolver = typeResolver;
    flow = new FlowAnalyzer(body, model, resolution, diagnostics, this::analyzeStatements);
    expressionChecker =
        new ExpressionChecker(
            body,
            builtins,
            diagnostics,
            guard,
            model,
            resolution,
            typeResolver,
            declarationAnalyzer,
            transactions,
            flow,
            this::analyzeStatement,
            this::analyzeFor);
    calls = expressionChecker.calls;
  }

  private final TypeResolver typeResolver;
  private final FlowAnalyzer flow;
  final ExpressionChecker expressionChecker;
  private final CallResolver calls;

  void analyzeInterfaceDefault(
      Syntax.InterfaceDecl owner,
      Syntax.InterfaceMethodDecl method,
      Map<String, SemanticType> methodTypes,
      Map<String, SymbolId> methodSymbols) {
    try (var callableScope =
        this.body.enterCallable(
            declarationAnalyzer.defaultMethodId(method),
            typeResolver.resolveDeclarationType(method.returnType(), method, methodTypes),
            false,
            null)) {
      flow.pushScope(method.span());
      for (Syntax.TypeParameter parameter : owner.typeParameters()) {
        flow.declareExisting(
            parameter.name(),
            methodTypes.get(parameter.name()),
            parameter.nameSpan(),
            model.declarationSymbols().get(parameter));
      }
      for (Syntax.TypeParameter parameter : method.typeParameters()) {
        flow.declareExisting(
            parameter.name(),
            methodTypes.get(parameter.name()),
            parameter.nameSpan(),
            methodSymbols.get(parameter.name()));
      }
      flow.declareSelf(typeResolver.interfaceSelfType(owner), owner.nameSpan());
      for (Syntax.Parameter parameter : method.parameters()) {
        SemanticType type =
            typeResolver.resolveDeclarationType(parameter.type(), method, methodTypes);
        flow.declareExisting(
            parameter.name(),
            type,
            parameter.nameSpan(),
            model.declarationSymbols().get(parameter));
      }
      List<Syntax.Statement> body =
          BlockResults.returning(
              method.body().orElseThrow(),
              !this.body.expectedReturnType().equals(SemanticType.VOID));
      analyzeStatements(body);
      if (!this.body.expectedReturnType().equals(SemanticType.VOID)
          && !StatementFlow.definitelyExits(body)) {
        diagnostics.error(
            INVALID_CONTROL,
            "default method '"
                + method.name()
                + "' must return "
                + this.body.expectedReturnType().displayName(),
            method.span());
      }
      flow.popScope();
    }
  }

  void analyzeFunction(Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    if (!function.hasBody()) {
      String message =
          owner == null || owner.kind() != Syntax.AggregateKind.CLASS
              ? "method declarations require a class"
              : function.returnType().isEmpty()
                  ? "method declarations require an explicit return type"
                  : function.visibility() != Syntax.Visibility.PUBLIC
                      ? "managed method declarations must be public"
                      : declarationPolicies.managedImplementation(owner)
                          ? null
                          : "method declaration has no implementation provider";
      if (message != null) {
        diagnostics.error(INVALID_CONTROL, message, function.nameSpan());
      }
    }
    if (function.kind() == Syntax.FunctionKind.EXTENSION && function.parameters().isEmpty()) {
      diagnostics.error(
          INVALID_CALL, "extension function requires a receiver parameter", function.nameSpan());
    }
    try (var functionScope =
        resolution.enterParameters(
            typeResolver.typeParameters(function, owner),
            typeResolver.typeParameterSymbols(function, owner))) {
      if (owner != null)
        typeResolver.registerBounds(owner.typeParameters(), resolution.parameters());
      typeResolver.registerBounds(function.typeParameters(), resolution.parameters());
      typeResolver.validateTypeParameterDefaults(
          function.typeParameters(),
          resolution.parameters(),
          function.visibility() == Syntax.Visibility.PUBLIC
              && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC));
      function.returnType().ifPresent(type -> typeResolver.validateType(type, true));
      try (var callableScope =
          this.body.enterCallable(
              model.declarationSymbols().get(function),
              typeResolver.functionReturnType(function, resolution.parameters()),
              owner != null && function.returnType().isEmpty(),
              owner)) {
        if (function.visibility() == Syntax.Visibility.PUBLIC
            && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC)) {
          function.returnType().ifPresent(typeResolver::validatePublicType);
          function
              .parameters()
              .forEach(parameter -> typeResolver.validatePublicType(parameter.type()));
        }

        flow.pushScope(function.span());
        if (owner != null) {
          for (Syntax.TypeParameter parameter : owner.typeParameters()) {
            flow.declareExisting(
                parameter.name(),
                resolution.parameters().get(parameter.name()),
                parameter.nameSpan(),
                model.declarationSymbols().get(parameter));
          }
        }
        for (Syntax.TypeParameter parameter : function.typeParameters()) {
          flow.declareExisting(
              parameter.name(),
              resolution.parameters().get(parameter.name()),
              parameter.nameSpan(),
              model.declarationSymbols().get(parameter));
        }
        if (owner != null) {
          flow.declareSelf(typeResolver.aggregateSelfType(owner), owner.nameSpan());
          for (AggregateView view :
              typeResolver.aggregateViews(typeResolver.aggregateSelfType(owner))) {
            Map<String, SemanticType> substitutions =
                typeResolver.aggregateSubstitutions(view.declaration(), view.type());
            for (Syntax.FieldDecl field : view.declaration().fields()) {
              if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
                continue;
              flow.declareExisting(
                  field.name(),
                  typeResolver
                      .resolveDeclarationType(
                          field.type(),
                          field,
                          typeResolver.aggregateTypeParameters(view.declaration()))
                      .substitute(substitutions),
                  field.nameSpan(),
                  model.declarationSymbols().get(field));
            }
          }
        }
        for (Syntax.Parameter parameter : function.parameters()) {
          typeResolver.validateReferenceCapableType(parameter.type());
        }
        analyzeParameterDefaults(function.parameters());
        for (Syntax.Parameter parameter : function.parameters()) {
          Symbol symbol =
              declarationAnalyzer.register(
                  parameter,
                  parameter.name(),
                  SymbolKind.PARAMETER,
                  typeResolver.resolveType(parameter.type(), resolution.parameters()),
                  parameter.nameSpan(),
                  model.declarationSymbols().get(function),
                  List.of(),
                  declarationAnalyzer.parameters(
                      parameter.callableParameters().orElse(List.of()),
                      Map.of(),
                      resolution.parameters()));
          flow.declareExisting(
              parameter.name(),
              typeResolver.resolveType(parameter.type(), resolution.parameters()),
              parameter.nameSpan(),
              symbol.id());
        }
        List<Syntax.Statement> body =
            BlockResults.returning(
                function.body(),
                !this.body.expectedReturnType().equals(SemanticType.VOID)
                    && !this.body.implicitSelfReturn());
        analyzeStatements(body);
        if (function.hasBody()
            && !this.body.expectedReturnType().equals(SemanticType.VOID)
            && !this.body.implicitSelfReturn()
            && !StatementFlow.definitelyExits(body)) {
          diagnostics.error(
              INVALID_CONTROL,
              "function '"
                  + function.name()
                  + "' must return "
                  + this.body.expectedReturnType().displayName(),
              function.span());
        }
        flow.popScope();
      }
    }
  }

  void analyzeConstructor(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    try (var constructorScope =
        resolution.enterParameters(
            typeResolver.aggregateTypeParameters(owner),
            typeResolver.typeParameterSymbols(owner.typeParameters()))) {
      typeResolver.registerBounds(owner.typeParameters(), resolution.parameters());
      try (var callableScope =
          this.body.enterCallable(
              model.declarationSymbols().get(constructor), SemanticType.VOID, false, owner)) {
        flow.pushScope(constructor.span());
        for (Syntax.TypeParameter parameter : owner.typeParameters()) {
          flow.declareExisting(
              parameter.name(),
              resolution.parameters().get(parameter.name()),
              parameter.nameSpan(),
              model.declarationSymbols().get(parameter));
        }
        flow.declareSelf(typeResolver.aggregateSelfType(owner), owner.nameSpan());
        for (AggregateView view :
            typeResolver.aggregateViews(typeResolver.aggregateSelfType(owner))) {
          Map<String, SemanticType> substitutions =
              typeResolver.aggregateSubstitutions(view.declaration(), view.type());
          for (Syntax.FieldDecl field : view.declaration().fields()) {
            if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
              continue;
            flow.declareExisting(
                field.name(),
                typeResolver
                    .resolveDeclarationType(
                        field.type(),
                        field,
                        typeResolver.aggregateTypeParameters(view.declaration()))
                    .substitute(substitutions),
                field.nameSpan(),
                model.declarationSymbols().get(field));
          }
        }
        flow.pushScope(constructor.span());
        for (Syntax.Parameter parameter : constructor.parameters()) {
          typeResolver.validateReferenceCapableType(parameter.type());
        }
        analyzeParameterDefaults(constructor.parameters());
        for (Syntax.Parameter parameter : constructor.parameters()) {
          Symbol symbol =
              declarationAnalyzer.register(
                  parameter,
                  parameter.name(),
                  SymbolKind.PARAMETER,
                  typeResolver.resolveType(parameter.type(), resolution.parameters()),
                  parameter.nameSpan(),
                  this.body.currentCallable(),
                  List.of(),
                  declarationAnalyzer.parameters(
                      parameter.callableParameters().orElse(List.of()),
                      Map.of(),
                      resolution.parameters()));
          flow.declareExisting(parameter.name(), symbol.type(), parameter.nameSpan(), symbol.id());
        }
        analyzeSuperCall(constructor, owner);
        analyzeStatements(constructor.body());
        Map<SymbolId, String> fields = new LinkedHashMap<>();
        for (AggregateView view :
            typeResolver.aggregateViews(typeResolver.aggregateSelfType(owner))) {
          for (Syntax.FieldDecl field : view.declaration().fields()) {
            SymbolId fieldId = model.declarationSymbols().get(field);
            fields.put(fieldId, field.name());
          }
        }
        Set<SymbolId> inheritedFields = new HashSet<>(fields.keySet());
        owner.fields().stream()
            .map(model.declarationSymbols()::get)
            .forEach(inheritedFields::remove);
        List<ConstructorFlowAnalyzer.RequiredField> requiredFields =
            owner.fields().stream()
                .filter(
                    field ->
                        field.defaultValue().isEmpty() && !declarationPolicies.managedField(field))
                .map(
                    field ->
                        new ConstructorFlowAnalyzer.RequiredField(
                            model.declarationSymbols().get(field), field.name(), field.nameSpan()))
                .toList();
        new ConstructorFlowAnalyzer(model.bindings(), model.symbols())
            .analyze(
                new ConstructorFlowAnalyzer.Input(
                    constructor, fields, inheritedFields, requiredFields))
            .diagnostics()
            .forEach(diagnostics::report);
        flow.popScope();
        flow.popScope();
      }
    }
  }

  void analyzeParameterDefaults(List<Syntax.Parameter> parameters) {
    boolean defaultSeen = false;
    for (Syntax.Parameter parameter : parameters) {
      if (parameter.defaultValue().isPresent()) {
        defaultSeen = true;
        SemanticType expected = typeResolver.resolveType(parameter.type(), resolution.parameters());
        Syntax.Expression value = parameter.defaultValue().orElseThrow();
        typeResolver.requireType(expected, expressionChecker.typeOf(value, expected), value.span());
      } else if (defaultSeen) {
        diagnostics.error(
            INVALID_CALL, "required parameter follows a default parameter", parameter.nameSpan());
      }
    }
  }

  void analyzeImplicitSuperCall(Syntax.AggregateDecl owner) {
    owner
        .implicitSuperCall()
        .ifPresent(
            call -> {
              try (var constructorScope =
                  resolution.enterParameters(
                      typeResolver.aggregateTypeParameters(owner),
                      typeResolver.typeParameterSymbols(owner.typeParameters()))) {
                typeResolver
                    .directParentType(owner, typeResolver.aggregateSelfType(owner))
                    .ifPresent(parent -> resolveSuperCall(parent, call));
              }
            });
  }

  private void analyzeSuperCall(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    Optional<SemanticType> parentType =
        typeResolver.directParentType(owner, typeResolver.aggregateSelfType(owner));
    if (parentType.isEmpty()) {
      if (constructor.superCall().isPresent()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "root class constructor cannot call super",
            constructor.superCall().orElseThrow().span());
        calls.analyzeArguments(constructor.superCall().orElseThrow().arguments());
      }
      return;
    }
    if (constructor.superCall().isEmpty()) {
      diagnostics.error(
          INVALID_CONTROL, "subclass constructor must call super", constructor.nameSpan());
      return;
    }
    resolveSuperCall(parentType.orElseThrow(), constructor.superCall().orElseThrow());
  }

  private void resolveSuperCall(SemanticType parent, Syntax.SuperCall call) {
    Syntax.AggregateDecl declaration = typeResolver.resolveAggregate(parent);
    if (declaration == null) {
      calls.analyzeArguments(call.arguments());
      return;
    }
    Syntax.Call syntaxCall =
        new Syntax.Call(new Syntax.Name("super", call.span()), call.arguments(), call.span());
    CallResolver.CallResolution<SymbolId> selected =
        calls.resolveConstructor(declaration, parent, syntaxCall, call.span());
    if (selected == null) return;
    SymbolId target = selected.declaration();
    calls.recordCall(
        syntaxCall,
        call.span(),
        ResolvedCall.Kind.SUPER,
        target,
        selected.parameters(),
        List.of(),
        SemanticType.VOID);
  }

  void analyzeStatements(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      guard.checkpoint();
      analyzeStatement(statement);
    }
  }

  void analyzeStatement(Syntax.Statement statement) {
    switch (statement) {
      case Syntax.VariableDecl variable -> {
        SemanticType requested =
            variable
                .type()
                .map(
                    type -> {
                      typeResolver.validateReferenceCapableType(type);
                      return typeResolver.resolveType(type, resolution.parameters());
                    })
                .orElse(null);
        SemanticType actual = expressionChecker.typeOf(variable.initializer(), requested);
        if (requested != null)
          typeResolver.requireAssignable(requested, actual, variable.initializer().span());
        if (requested == null && CallResolver.containsDynamic(actual)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "cannot infer variable type from initializer",
              variable.initializer().span());
        }
        SemanticType declaredType = requested == null ? actual : requested;
        Symbol symbol =
            declarationAnalyzer.register(
                variable,
                variable.name(),
                SymbolKind.LOCAL_VARIABLE,
                declaredType,
                variable.nameSpan(),
                this.body.currentCallable(),
                List.of(),
                List.of());
        flow.declareExisting(variable.name(), declaredType, variable.nameSpan(), symbol.id());
        if (declaredType.isReference()) {
          FlowScopes.ScopedSymbol scoped = flow.findScoped(variable.name());
          if (scoped != null && scoped.id().equals(symbol.id())) {
            updateReferenceLifetime(scoped, variable.initializer());
          }
        }
        this.body.declareLambdaLocal(symbol.id());
      }
      case Syntax.Assignment assignment -> {
        if (expressionChecker.analyzePropertyAssignment(assignment)) break;
        SemanticType target = expressionChecker.assignmentTargetType(assignment.target());
        if (assignment.target() instanceof Syntax.Name name
            && this.body.currentAggregate() != null
            && this.body.currentAggregate().kind() == Syntax.AggregateKind.VALUE
            && !expressionChecker.constructingOwnValue()) {
          FlowScopes.ScopedSymbol scoped = flow.findScoped(name.value());
          if (scoped != null && expressionChecker.scopedSymbol(scoped).kind() == SymbolKind.FIELD) {
            diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", name.span());
          }
        }
        SemanticType value = expressionChecker.typeOf(assignment.value(), target);
        typeResolver.requireAssignable(target, value, assignment.value().span());
        if (assignment.target() instanceof Syntax.Name name) {
          FlowScopes.ScopedSymbol scoped = flow.findScoped(name.value());
          if (scoped != null && target.isReference() && value.isReference()) {
            updateReferenceLifetime(scoped, assignment.value());
          }
          if (scoped != null)
            expressionChecker.closures.assign(expressionChecker.scopedSymbol(scoped), name.span());
          flow.invalidateNarrowing(name.value());
        }
      }
      case Syntax.ExpressionStatement expression ->
          expressionChecker.typeOf(expression.expression(), null);
      case Syntax.IfStatement ifStatement -> {
        typeResolver.requireType(
            SemanticType.BOOLEAN,
            expressionChecker.typeOf(ifStatement.condition(), SemanticType.BOOLEAN),
            ifStatement.condition().span());
        FlowScopes.FlowState incoming = this.body.scopes().snapshot();
        FlowScopes.FlowState thenFlow =
            flow.analyzeBranch(
                ifStatement.thenBody(),
                flow.narrowingsFor(ifStatement.condition(), true),
                incoming);
        FlowScopes.FlowState elseFlow =
            flow.analyzeBranch(
                ifStatement.elseBody(),
                flow.narrowingsFor(ifStatement.condition(), false),
                incoming);
        boolean thenReturns = StatementFlow.definitelyExits(ifStatement.thenBody());
        boolean elseReturns = StatementFlow.definitelyExits(ifStatement.elseBody());
        if (thenReturns && !elseReturns) {
          flow.replaceFlow(elseFlow);
        } else if (elseReturns && !thenReturns) {
          flow.replaceFlow(thenFlow);
        } else if (!thenReturns && !elseReturns) {
          flow.replaceFlow(flow.mergeFlows(incoming, thenFlow, elseFlow));
        } else {
          flow.replaceFlow(incoming);
        }
      }
      case Syntax.ConditionalForStatement loop -> {
        typeResolver.requireType(
            SemanticType.BOOLEAN,
            expressionChecker.typeOf(loop.condition(), SemanticType.BOOLEAN),
            loop.condition().span());
        flow.pushScope(loop.span());
        flow.analyzeLoop(loop.body(), flow.narrowingsFor(loop.condition(), true));
        flow.popScope();
      }
      case Syntax.ForStatement forStatement ->
          analyzeFor(forStatement, () -> analyzeStatements(forStatement.body()));
      case Syntax.TryStatement tried -> analyzeTry(tried);
      case Syntax.ThrowStatement thrown -> {
        SemanticType type = expressionChecker.typeOf(thrown.exception(), SemanticType.EXCEPTION);
        if (!typeResolver.isAssignable(SemanticType.EXCEPTION, type)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "throw requires an Exception but found " + type.displayName(),
              thrown.exception().span());
        }
      }
      case Syntax.ReturnStatement returnStatement -> {
        if (this.body.implicitSelfReturn()) {
          if (returnStatement.value() != null) {
            expressionChecker.typeOf(returnStatement.value(), this.body.expectedReturnType());
            diagnostics.error(
                TYPE_MISMATCH,
                "fluent methods return their receiver; use a bare return",
                returnStatement.span());
          }
        } else {
          SemanticType actual =
              returnStatement.value() == null
                  ? SemanticType.VOID
                  : expressionChecker.typeOf(
                      returnStatement.value(), this.body.expectedReturnType());
          typeResolver.requireAssignable(
              this.body.expectedReturnType(), actual, returnStatement.span());
        }
      }
      case Syntax.BreakStatement breakStatement -> expressionChecker.analyzeBreak(breakStatement);
      case Syntax.ContinueStatement continueStatement ->
          flow.validateContinue(continueStatement.span());
    }
  }

  private void analyzeTry(Syntax.TryStatement tried) {
    FlowScopes.FlowState incoming = this.body.scopes().snapshot();
    List<FlowScopes.FlowState> completing = new ArrayList<>();
    FlowScopes.FlowState tryFlow = flow.analyzeBranch(tried.body(), Map.of(), incoming);
    if (!StatementFlow.definitelyExits(tried.body())) completing.add(tryFlow);
    List<SemanticType> preceding = new ArrayList<>();
    for (Syntax.CatchClause clause : tried.catches()) {
      typeResolver.validateType(clause.type(), false);
      SemanticType type = typeResolver.resolveType(clause.type(), resolution.parameters());
      if (!typeResolver.isAssignable(SemanticType.EXCEPTION, type)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "catch requires an Exception type but found " + type.displayName(),
            clause.type().span());
      }
      if (preceding.stream().anyMatch(previous -> typeResolver.isAssignable(previous, type))) {
        diagnostics.error(
            INVALID_CONTROL,
            "catch type " + type.displayName() + " is already covered by an earlier catch",
            clause.type().span());
      }
      preceding.add(type);
      flow.replaceFlow(incoming);
      flow.pushScope(clause.span());
      Symbol symbol =
          declarationAnalyzer.register(
              clause,
              clause.name(),
              SymbolKind.LOCAL_VARIABLE,
              type,
              clause.nameSpan(),
              this.body.currentCallable(),
              List.of(),
              List.of());
      flow.declareExisting(clause.name(), type, clause.nameSpan(), symbol.id());
      this.body.declareLambdaLocal(symbol.id());
      analyzeStatements(clause.body());
      flow.popScope();
      if (!StatementFlow.definitelyExits(clause.body()))
        completing.add(this.body.scopes().snapshot());
    }
    FlowScopes.FlowState normal = mergeCompletingFlows(incoming, completing);
    flow.replaceFlow(normal);
    if (tried.finallyClause().isPresent()) {
      var writes = this.body.collectWrites();
      FlowScopes.FlowState finalFlow;
      try (writes) {
        finalFlow =
            flow.analyzeBranch(tried.finallyClause().orElseThrow().body(), Map.of(), incoming);
      }
      Set<SymbolId> finalWrites = writes.writes();
      Map<SymbolId, SemanticType> types = new LinkedHashMap<>(normal.types());
      for (Map.Entry<SymbolId, SemanticType> entry : finalFlow.types().entrySet()) {
        if (finalWrites.contains(entry.getKey())
            || !entry.getValue().equals(incoming.types().get(entry.getKey()))) {
          types.put(entry.getKey(), entry.getValue());
        }
      }
      Map<SymbolId, LexicalLifetime> lifetimes = new LinkedHashMap<>(normal.referenceLifetimes());
      Set<SymbolId> lifetimeSymbols = new HashSet<>(incoming.referenceLifetimes().keySet());
      lifetimeSymbols.addAll(finalFlow.referenceLifetimes().keySet());
      for (SymbolId symbol : lifetimeSymbols) {
        LexicalLifetime before = incoming.referenceLifetimes().get(symbol);
        LexicalLifetime after = finalFlow.referenceLifetimes().get(symbol);
        if (!finalWrites.contains(symbol) && Objects.equals(before, after)) continue;
        if (after == null) lifetimes.remove(symbol);
        else lifetimes.put(symbol, after);
      }
      flow.replaceFlow(new FlowScopes.FlowState(types, lifetimes));
    }
  }

  private FlowScopes.FlowState mergeCompletingFlows(
      FlowScopes.FlowState incoming, List<FlowScopes.FlowState> flows) {
    if (flows.isEmpty()) return incoming;
    FlowScopes.FlowState result = flows.getFirst();
    for (int index = 1; index < flows.size(); index++) {
      result = flow.mergeFlows(incoming, result, flows.get(index));
    }
    return result;
  }

  private void updateReferenceLifetime(
      FlowScopes.ScopedSymbol destination, Syntax.Expression value) {
    LexicalLifetime sourceLifetime = expressionChecker.referenceLifetime(value);
    LexicalLifetime destinationLifetime = this.body.scopes().storageLifetime(destination);
    if (!sourceLifetime.outlives(destinationLifetime)) {
      diagnostics.error(
          INVALID_CONTROL, "reference cannot outlive the addressed storage location", value.span());
      this.body.scopes().updateReferenceLifetime(destination, LexicalLifetime.unusable());
      return;
    }
    this.body.scopes().updateReferenceLifetime(destination, sourceLifetime);
  }

  private void analyzeFor(Syntax.ForStatement forStatement, Runnable bodyAnalysis) {
    SemanticType declaredVariable =
        forStatement
            .variableType()
            .map(
                explicitType -> {
                  typeResolver.validateType(explicitType, false);
                  return typeResolver.resolveType(explicitType, resolution.parameters());
                })
            .orElse(null);
    SemanticType expectedIterable = typeResolver.expectedIterable(declaredVariable);
    SemanticType iterableType =
        typeResolver.receiverType(
            expressionChecker.typeOf(forStatement.iterable(), expectedIterable),
            false,
            forStatement.iterable().span());
    Optional<ResolvedIteration> iteration = typeResolver.resolveIteration(iterableType);
    iteration.ifPresent(value -> model.putIteration(forStatement.iterable().span(), value));
    Optional<SemanticType> elementType = iteration.map(ResolvedIteration::elementType);
    if (elementType.isEmpty()) {
      diagnostics.error(
          TYPE_MISMATCH, "for requires an iterable value", forStatement.iterable().span());
    }
    SemanticType variableType;
    if (forStatement.variableType().isPresent()) {
      variableType = declaredVariable;
      elementType.ifPresent(
          itemType ->
              typeResolver.requireAssignable(
                  variableType, itemType, forStatement.variableNameSpan()));
    } else {
      if (elementType.isEmpty()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "cannot infer loop variable type from " + iterableType.displayName(),
            forStatement.variableNameSpan());
        variableType = SemanticType.DYNAMIC;
      } else {
        variableType = elementType.orElseThrow();
      }
    }
    if (CallResolver.containsDynamic(variableType)) {
      diagnostics.error(
          TYPE_MISMATCH,
          "cannot infer loop variable type from " + iterableType.displayName(),
          forStatement.variableNameSpan());
    }
    flow.pushScope(forStatement.span());
    Symbol symbol =
        declarationAnalyzer.register(
            forStatement,
            forStatement.variableName(),
            SymbolKind.LOCAL_VARIABLE,
            variableType,
            forStatement.variableNameSpan(),
            this.body.currentCallable(),
            List.of(),
            List.of());
    flow.declareExisting(
        forStatement.variableName(), variableType, forStatement.variableNameSpan(), symbol.id());
    this.body.declareLambdaLocal(symbol.id());
    forStatement
        .index()
        .ifPresent(
            index -> {
              Symbol indexSymbol =
                  declarationAnalyzer.register(
                      index,
                      index.name(),
                      SymbolKind.LOCAL_VARIABLE,
                      SemanticType.INTEGER,
                      index.nameSpan(),
                      this.body.currentCallable(),
                      List.of(),
                      List.of());
              flow.declareExisting(
                  index.name(), SemanticType.INTEGER, index.nameSpan(), indexSymbol.id());
              this.body.declareLambdaLocal(indexSymbol.id());
            });
    flow.analyzeLoop(bodyAnalysis, Map.of());
    flow.popScope();
  }
}
