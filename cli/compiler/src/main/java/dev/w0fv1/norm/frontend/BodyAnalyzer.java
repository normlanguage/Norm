package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateView;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
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
  private final SemanticAnalysisContext context;
  private final TypeSystem typeSystem;
  private final FlowAnalyzer flow;
  final ExpressionChecker expressionChecker;
  private final CallResolver calls;

  BodyAnalyzer(SemanticAnalysisContext context, TypeSystem typeSystem) {
    this.context = context;
    this.typeSystem = typeSystem;
    flow =
        new FlowAnalyzer(
            context.body,
            context.model,
            context.resolution,
            context.diagnostics,
            this::analyzeStatements);
    expressionChecker = new ExpressionChecker(context, typeSystem, flow, this::analyzeStatement);
    calls = expressionChecker.calls;
  }

  void analyzeInterfaceDefault(
      Syntax.InterfaceDecl owner,
      Syntax.InterfaceMethodDecl method,
      Map<String, SemanticType> methodTypes,
      Map<String, SymbolId> methodSymbols) {
    SemanticType previousReturn = context.body.expectedReturnType;
    SymbolId previousCallable = context.body.currentCallable;
    context.body.expectedReturnType =
        typeSystem.resolveDeclarationType(method.returnType(), method, methodTypes);
    context.body.currentCallable = typeSystem.defaultMethodId(method);
    context.body.flowScopes.clear();
    flow.pushScope(method.span());
    for (Syntax.TypeParameter parameter : owner.typeParameters()) {
      flow.declareExisting(
          parameter.name(),
          methodTypes.get(parameter.name()),
          parameter.nameSpan(),
          context.model.declarationSymbols().get(parameter));
    }
    for (Syntax.TypeParameter parameter : method.typeParameters()) {
      flow.declareExisting(
          parameter.name(),
          methodTypes.get(parameter.name()),
          parameter.nameSpan(),
          methodSymbols.get(parameter.name()));
    }
    flow.declareSelf(typeSystem.interfaceSelfType(owner), owner.nameSpan());
    for (Syntax.Parameter parameter : method.parameters()) {
      SemanticType type = typeSystem.resolveDeclarationType(parameter.type(), method, methodTypes);
      flow.declareExisting(
          parameter.name(),
          type,
          parameter.nameSpan(),
          context.model.declarationSymbols().get(parameter));
    }
    analyzeStatements(method.body().orElseThrow());
    if (!context.body.expectedReturnType.equals(SemanticType.VOID)
        && !StatementFlow.definitelyExits(method.body().orElseThrow())) {
      context.diagnostics.error(
          INVALID_CONTROL,
          "default method '"
              + method.name()
              + "' must return "
              + context.body.expectedReturnType.displayName(),
          method.span());
    }
    flow.popScope();
    context.body.expectedReturnType = previousReturn;
    context.body.currentCallable = previousCallable;
  }

  void analyzeFunction(Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    if (function.kind() == Syntax.FunctionKind.EXTENSION && function.parameters().isEmpty()) {
      context.diagnostics.error(
          INVALID_CALL, "extension function requires a receiver parameter", function.nameSpan());
    }
    context.resolution.activeTypeParameters = typeSystem.typeParameters(function, owner);
    context.resolution.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(function, owner);
    if (owner != null)
      typeSystem.registerBounds(owner.typeParameters(), context.resolution.activeTypeParameters);
    typeSystem.registerBounds(function.typeParameters(), context.resolution.activeTypeParameters);
    typeSystem.validateTypeParameterDefaults(
        function.typeParameters(),
        context.resolution.activeTypeParameters,
        function.visibility() == Syntax.Visibility.PUBLIC
            && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC));
    function.returnType().ifPresent(type -> typeSystem.validateType(type, true));
    context.body.expectedReturnType =
        typeSystem.functionReturnType(function, context.resolution.activeTypeParameters);
    context.body.implicitSelfReturn = owner != null && function.returnType().isEmpty();
    context.body.currentAggregate = owner;
    if (function.visibility() == Syntax.Visibility.PUBLIC
        && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC)) {
      function.returnType().ifPresent(typeSystem::validatePublicType);
      function.parameters().forEach(parameter -> typeSystem.validatePublicType(parameter.type()));
    }
    context.body.currentCallable = context.model.declarationSymbols().get(function);
    context.body.flowScopes.clear();
    context.body.assignedLocals.clear();
    context.body.capturedLocals.clear();
    context.body.reportedMutableCaptures.clear();
    context.body.lambdaLocals.clear();
    flow.pushScope(function.span());
    if (owner != null) {
      for (Syntax.TypeParameter parameter : owner.typeParameters()) {
        flow.declareExisting(
            parameter.name(),
            context.resolution.activeTypeParameters.get(parameter.name()),
            parameter.nameSpan(),
            context.model.declarationSymbols().get(parameter));
      }
    }
    for (Syntax.TypeParameter parameter : function.typeParameters()) {
      flow.declareExisting(
          parameter.name(),
          context.resolution.activeTypeParameters.get(parameter.name()),
          parameter.nameSpan(),
          context.model.declarationSymbols().get(parameter));
    }
    if (owner != null) {
      flow.declareSelf(typeSystem.aggregateSelfType(owner), owner.nameSpan());
      for (AggregateView view : typeSystem.aggregateViews(typeSystem.aggregateSelfType(owner))) {
        Map<String, SemanticType> substitutions =
            typeSystem.aggregateSubstitutions(view.declaration(), view.type());
        for (Syntax.FieldDecl field : view.declaration().fields()) {
          if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
            continue;
          flow.declareExisting(
              field.name(),
              typeSystem
                  .resolveDeclarationType(
                      field.type(), field, typeSystem.aggregateTypeParameters(view.declaration()))
                  .substitute(substitutions),
              field.nameSpan(),
              context.model.declarationSymbols().get(field));
        }
      }
    }
    for (Syntax.Parameter parameter : function.parameters()) {
      typeSystem.validateReferenceCapableType(parameter.type());
    }
    analyzeParameterDefaults(function.parameters());
    for (Syntax.Parameter parameter : function.parameters()) {
      Symbol symbol =
          typeSystem.register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              typeSystem.resolveType(parameter.type(), context.resolution.activeTypeParameters),
              parameter.nameSpan(),
              context.model.declarationSymbols().get(function),
              List.of(),
              List.of());
      flow.declareExisting(
          parameter.name(),
          typeSystem.resolveType(parameter.type(), context.resolution.activeTypeParameters),
          parameter.nameSpan(),
          symbol.id());
    }
    analyzeStatements(function.body());
    if (!context.body.expectedReturnType.equals(SemanticType.VOID)
        && !context.body.implicitSelfReturn
        && !StatementFlow.definitelyExits(function.body())) {
      context.diagnostics.error(
          INVALID_CONTROL,
          "function '"
              + function.name()
              + "' must return "
              + context.body.expectedReturnType.displayName(),
          function.span());
    }
    flow.popScope();
    context.body.currentCallable = null;
    context.body.currentAggregate = null;
    context.body.implicitSelfReturn = false;
    context.resolution.activeTypeParameters = Map.of();
    context.resolution.activeTypeParameterSymbols = Map.of();
  }

  void analyzeConstructor(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    context.resolution.activeTypeParameters = typeSystem.aggregateTypeParameters(owner);
    context.resolution.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(owner.typeParameters());
    typeSystem.registerBounds(owner.typeParameters(), context.resolution.activeTypeParameters);
    context.body.expectedReturnType = SemanticType.VOID;
    context.body.implicitSelfReturn = false;
    context.body.currentAggregate = owner;
    context.body.currentCallable = context.model.declarationSymbols().get(constructor);
    context.body.flowScopes.clear();
    context.body.assignedLocals.clear();
    context.body.capturedLocals.clear();
    context.body.reportedMutableCaptures.clear();
    context.body.lambdaLocals.clear();
    flow.pushScope(constructor.span());
    for (Syntax.TypeParameter parameter : owner.typeParameters()) {
      flow.declareExisting(
          parameter.name(),
          context.resolution.activeTypeParameters.get(parameter.name()),
          parameter.nameSpan(),
          context.model.declarationSymbols().get(parameter));
    }
    flow.declareSelf(typeSystem.aggregateSelfType(owner), owner.nameSpan());
    for (AggregateView view : typeSystem.aggregateViews(typeSystem.aggregateSelfType(owner))) {
      Map<String, SemanticType> substitutions =
          typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FieldDecl field : view.declaration().fields()) {
        if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
          continue;
        flow.declareExisting(
            field.name(),
            typeSystem
                .resolveDeclarationType(
                    field.type(), field, typeSystem.aggregateTypeParameters(view.declaration()))
                .substitute(substitutions),
            field.nameSpan(),
            context.model.declarationSymbols().get(field));
      }
    }
    flow.pushScope(constructor.span());
    for (Syntax.Parameter parameter : constructor.parameters()) {
      typeSystem.validateReferenceCapableType(parameter.type());
    }
    analyzeParameterDefaults(constructor.parameters());
    for (Syntax.Parameter parameter : constructor.parameters()) {
      Symbol symbol =
          typeSystem.register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              typeSystem.resolveType(parameter.type(), context.resolution.activeTypeParameters),
              parameter.nameSpan(),
              context.body.currentCallable,
              List.of(),
              List.of());
      flow.declareExisting(parameter.name(), symbol.type(), parameter.nameSpan(), symbol.id());
    }
    analyzeSuperCall(constructor, owner);
    analyzeStatements(constructor.body());
    Map<SymbolId, String> fields = new LinkedHashMap<>();
    for (AggregateView view : typeSystem.aggregateViews(typeSystem.aggregateSelfType(owner))) {
      for (Syntax.FieldDecl field : view.declaration().fields()) {
        SymbolId fieldId = context.model.declarationSymbols().get(field);
        fields.put(fieldId, field.name());
      }
    }
    Set<SymbolId> inheritedFields = new HashSet<>(fields.keySet());
    owner.fields().stream()
        .map(context.model.declarationSymbols()::get)
        .forEach(inheritedFields::remove);
    List<ConstructorFlowAnalyzer.RequiredField> requiredFields =
        owner.fields().stream()
            .filter(field -> field.defaultValue().isEmpty())
            .map(
                field ->
                    new ConstructorFlowAnalyzer.RequiredField(
                        context.model.declarationSymbols().get(field),
                        field.name(),
                        field.nameSpan()))
            .toList();
    new ConstructorFlowAnalyzer(context.model.bindings(), context.model.symbols())
        .analyze(
            new ConstructorFlowAnalyzer.Input(constructor, fields, inheritedFields, requiredFields))
        .diagnostics()
        .forEach(context.diagnostics::report);
    flow.popScope();
    flow.popScope();
    context.body.currentCallable = null;
    context.body.currentAggregate = null;
    context.resolution.activeTypeParameters = Map.of();
    context.resolution.activeTypeParameterSymbols = Map.of();
  }

  void analyzeParameterDefaults(List<Syntax.Parameter> parameters) {
    boolean defaultSeen = false;
    for (Syntax.Parameter parameter : parameters) {
      if (parameter.defaultValue().isPresent()) {
        defaultSeen = true;
        SemanticType expected =
            typeSystem.resolveType(parameter.type(), context.resolution.activeTypeParameters);
        Syntax.Expression value = parameter.defaultValue().orElseThrow();
        typeSystem.requireType(expected, expressionChecker.typeOf(value, expected), value.span());
      } else if (defaultSeen) {
        context.diagnostics.error(
            INVALID_CALL, "required parameter follows a default parameter", parameter.nameSpan());
      }
    }
  }

  private void analyzeSuperCall(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    Optional<SemanticType> parentType =
        typeSystem.directParentType(owner, typeSystem.aggregateSelfType(owner));
    if (parentType.isEmpty()) {
      if (constructor.superCall().isPresent()) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "root class constructor cannot call super",
            constructor.superCall().orElseThrow().span());
        calls.analyzeArguments(constructor.superCall().orElseThrow().arguments());
      }
      return;
    }
    if (constructor.superCall().isEmpty()) {
      context.diagnostics.error(
          INVALID_CONTROL, "subclass constructor must call super", constructor.nameSpan());
      return;
    }
    SemanticType parent = parentType.orElseThrow();
    Syntax.AggregateDecl declaration = typeSystem.resolveAggregate(parent);
    if (declaration == null) {
      calls.analyzeArguments(constructor.superCall().orElseThrow().arguments());
      return;
    }
    Syntax.SuperCall call = constructor.superCall().orElseThrow();
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
      context.guard.checkpoint();
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
                      typeSystem.validateReferenceCapableType(type);
                      return typeSystem.resolveType(type, context.resolution.activeTypeParameters);
                    })
                .orElse(null);
        SemanticType actual = expressionChecker.typeOf(variable.initializer(), requested);
        if (requested != null)
          typeSystem.requireAssignable(requested, actual, variable.initializer().span());
        if (requested == null && CallResolver.containsDynamic(actual)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "cannot infer variable type from initializer",
              variable.initializer().span());
        }
        SemanticType declaredType = requested == null ? actual : requested;
        Symbol symbol =
            typeSystem.register(
                variable,
                variable.name(),
                SymbolKind.LOCAL_VARIABLE,
                declaredType,
                variable.nameSpan(),
                context.body.currentCallable,
                List.of(),
                List.of());
        flow.declareExisting(variable.name(), declaredType, variable.nameSpan(), symbol.id());
        if (declaredType.isReference()) {
          FlowScopes.ScopedSymbol scoped = flow.findScoped(variable.name());
          if (scoped != null && scoped.id().equals(symbol.id())) {
            updateReferenceLifetime(scoped, variable.initializer());
          }
        }
        if (!context.body.lambdaLocals.isEmpty())
          context.body.lambdaLocals.getFirst().add(symbol.id());
      }
      case Syntax.Assignment assignment -> {
        SemanticType target = expressionChecker.assignmentTargetType(assignment.target());
        if (assignment.target() instanceof Syntax.Name name
            && context.body.currentAggregate != null
            && context.body.currentAggregate.kind() == Syntax.AggregateKind.VALUE) {
          FlowScopes.ScopedSymbol scoped = flow.findScoped(name.value());
          if (scoped != null && expressionChecker.scopedSymbol(scoped).kind() == SymbolKind.FIELD) {
            context.diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", name.span());
          }
        }
        SemanticType value = expressionChecker.typeOf(assignment.value(), target);
        typeSystem.requireAssignable(target, value, assignment.value().span());
        if (assignment.target() instanceof Syntax.Name name) {
          FlowScopes.ScopedSymbol scoped = flow.findScoped(name.value());
          if (scoped != null && target.isReference() && value.isReference()) {
            updateReferenceLifetime(scoped, assignment.value());
          }
          if (scoped != null
              && (expressionChecker.scopedSymbol(scoped).kind() == SymbolKind.LOCAL_VARIABLE
                  || expressionChecker.scopedSymbol(scoped).kind() == SymbolKind.PARAMETER)) {
            if (!context.body.lambdaLocals.isEmpty()
                && !context.body.lambdaLocals.getFirst().contains(scoped.id())) {
              context.body.capturedLocals.add(scoped.id());
              expressionChecker.reportMutableCapture(scoped.id(), name.span());
            }
            context.body.assignedLocals.add(scoped.id());
            context.body.flowWriteCollectors.forEach(writes -> writes.add(scoped.id()));
            if (context.body.capturedLocals.contains(scoped.id())) {
              expressionChecker.reportMutableCapture(scoped.id(), name.span());
            }
          }
          flow.invalidateNarrowing(name.value());
        }
      }
      case Syntax.ExpressionStatement expression ->
          expressionChecker.typeOf(expression.expression(), null);
      case Syntax.IfStatement ifStatement -> {
        typeSystem.requireType(
            SemanticType.BOOLEAN,
            expressionChecker.typeOf(ifStatement.condition(), SemanticType.BOOLEAN),
            ifStatement.condition().span());
        FlowScopes.FlowState incoming = context.body.flowScopes.snapshot();
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
        typeSystem.requireType(
            SemanticType.BOOLEAN,
            expressionChecker.typeOf(loop.condition(), SemanticType.BOOLEAN),
            loop.condition().span());
        flow.pushScope(loop.span());
        flow.analyzeLoop(loop.body(), flow.narrowingsFor(loop.condition(), true));
        flow.popScope();
      }
      case Syntax.ForStatement forStatement -> {
        SemanticType iterableType =
            typeSystem.receiverType(
                expressionChecker.typeOf(forStatement.iterable(), null),
                false,
                forStatement.iterable().span());
        Optional<BuiltinCatalog.ResolvedIterable> builtinIterable =
            context.builtins.resolveIterable(iterableType);
        Optional<ResolvedIteration> interfaceIteration =
            typeSystem.resolveInterfaceIteration(iterableType);
        builtinIterable.ifPresent(
            capability ->
                context.model.putIteration(
                    forStatement.iterable().span(),
                    new ResolvedIteration(
                        capability.elementType(),
                        new ResolvedIteration.Strategy.Builtin(capability.intrinsic()))));
        interfaceIteration.ifPresent(
            resolution -> context.model.putIteration(forStatement.iterable().span(), resolution));
        Optional<SemanticType> elementType =
            builtinIterable
                .map(BuiltinCatalog.ResolvedIterable::elementType)
                .or(() -> interfaceIteration.map(ResolvedIteration::elementType));
        if (elementType.isEmpty()) {
          context.diagnostics.error(
              TYPE_MISMATCH, "for requires an iterable value", forStatement.iterable().span());
        }
        SemanticType variableType;
        if (forStatement.variableType().isPresent()) {
          Syntax.TypeRef explicitType = forStatement.variableType().orElseThrow();
          typeSystem.validateType(explicitType, false);
          variableType =
              typeSystem.resolveType(explicitType, context.resolution.activeTypeParameters);
          elementType.ifPresent(
              itemType ->
                  typeSystem.requireAssignable(
                      variableType, itemType, forStatement.variableNameSpan()));
        } else {
          if (elementType.isEmpty()) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "cannot infer loop variable type from " + iterableType.displayName(),
                forStatement.variableNameSpan());
            variableType = SemanticType.DYNAMIC;
          } else {
            variableType = elementType.orElseThrow();
          }
        }
        flow.pushScope(forStatement.span());
        Symbol symbol =
            typeSystem.register(
                forStatement,
                forStatement.variableName(),
                SymbolKind.LOCAL_VARIABLE,
                variableType,
                forStatement.variableNameSpan(),
                context.body.currentCallable,
                List.of(),
                List.of());
        flow.declareExisting(
            forStatement.variableName(),
            variableType,
            forStatement.variableNameSpan(),
            symbol.id());
        if (!context.body.lambdaLocals.isEmpty())
          context.body.lambdaLocals.getFirst().add(symbol.id());
        forStatement
            .index()
            .ifPresent(
                index -> {
                  Symbol indexSymbol =
                      typeSystem.register(
                          index,
                          index.name(),
                          SymbolKind.LOCAL_VARIABLE,
                          SemanticType.INTEGER,
                          index.nameSpan(),
                          context.body.currentCallable,
                          List.of(),
                          List.of());
                  flow.declareExisting(
                      index.name(), SemanticType.INTEGER, index.nameSpan(), indexSymbol.id());
                  if (!context.body.lambdaLocals.isEmpty())
                    context.body.lambdaLocals.getFirst().add(indexSymbol.id());
                });
        flow.analyzeLoop(forStatement.body(), Map.of());
        flow.popScope();
      }
      case Syntax.TryStatement tried -> analyzeTry(tried);
      case Syntax.ThrowStatement thrown -> {
        SemanticType type = expressionChecker.typeOf(thrown.exception(), SemanticType.EXCEPTION);
        if (!typeSystem.isAssignable(SemanticType.EXCEPTION, type)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "throw requires an Exception but found " + type.displayName(),
              thrown.exception().span());
        }
      }
      case Syntax.ReturnStatement returnStatement -> {
        if (context.body.implicitSelfReturn) {
          if (returnStatement.value() != null) {
            expressionChecker.typeOf(returnStatement.value(), context.body.expectedReturnType);
            context.diagnostics.error(
                TYPE_MISMATCH,
                "fluent methods return their receiver; use a bare return",
                returnStatement.span());
          }
        } else {
          SemanticType actual =
              returnStatement.value() == null
                  ? SemanticType.VOID
                  : expressionChecker.typeOf(
                      returnStatement.value(), context.body.expectedReturnType);
          typeSystem.requireAssignable(
              context.body.expectedReturnType, actual, returnStatement.span());
        }
      }
      case Syntax.BreakStatement breakStatement -> expressionChecker.analyzeBreak(breakStatement);
      case Syntax.ContinueStatement continueStatement ->
          flow.validateContinue(continueStatement.span());
    }
  }

  private void analyzeTry(Syntax.TryStatement tried) {
    FlowScopes.FlowState incoming = context.body.flowScopes.snapshot();
    List<FlowScopes.FlowState> completing = new ArrayList<>();
    FlowScopes.FlowState tryFlow = flow.analyzeBranch(tried.body(), Map.of(), incoming);
    if (!StatementFlow.definitelyExits(tried.body())) completing.add(tryFlow);
    List<SemanticType> preceding = new ArrayList<>();
    for (Syntax.CatchClause clause : tried.catches()) {
      typeSystem.validateType(clause.type(), false);
      SemanticType type =
          typeSystem.resolveType(clause.type(), context.resolution.activeTypeParameters);
      if (!typeSystem.isAssignable(SemanticType.EXCEPTION, type)) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "catch requires an Exception type but found " + type.displayName(),
            clause.type().span());
      }
      if (preceding.stream().anyMatch(previous -> typeSystem.isAssignable(previous, type))) {
        context.diagnostics.error(
            INVALID_CONTROL,
            "catch type " + type.displayName() + " is already covered by an earlier catch",
            clause.type().span());
      }
      preceding.add(type);
      flow.replaceFlow(incoming);
      flow.pushScope(clause.span());
      Symbol symbol =
          typeSystem.register(
              clause,
              clause.name(),
              SymbolKind.LOCAL_VARIABLE,
              type,
              clause.nameSpan(),
              context.body.currentCallable,
              List.of(),
              List.of());
      flow.declareExisting(clause.name(), type, clause.nameSpan(), symbol.id());
      if (!context.body.lambdaLocals.isEmpty())
        context.body.lambdaLocals.getFirst().add(symbol.id());
      analyzeStatements(clause.body());
      flow.popScope();
      if (!StatementFlow.definitelyExits(clause.body()))
        completing.add(context.body.flowScopes.snapshot());
    }
    FlowScopes.FlowState normal = mergeCompletingFlows(incoming, completing);
    flow.replaceFlow(normal);
    if (tried.finallyClause().isPresent()) {
      Set<SymbolId> finalWrites = new HashSet<>();
      context.body.flowWriteCollectors.addFirst(finalWrites);
      FlowScopes.FlowState finalFlow;
      try {
        finalFlow =
            flow.analyzeBranch(tried.finallyClause().orElseThrow().body(), Map.of(), incoming);
      } finally {
        context.body.flowWriteCollectors.removeFirst();
      }
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
    LexicalLifetime destinationLifetime = context.body.flowScopes.storageLifetime(destination);
    if (!sourceLifetime.outlives(destinationLifetime)) {
      context.diagnostics.error(
          INVALID_CONTROL, "reference cannot outlive the addressed storage location", value.span());
      context.body.flowScopes.updateReferenceLifetime(destination, LexicalLifetime.unusable());
      return;
    }
    context.body.flowScopes.updateReferenceLifetime(destination, sourceLifetime);
  }
}
