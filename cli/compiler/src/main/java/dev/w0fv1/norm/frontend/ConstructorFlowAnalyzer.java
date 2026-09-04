package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.INVALID_CONTROL;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ConstructorFlowAnalyzer {
  private final Map<SourceSpan, SymbolId> bindings;
  private final Map<SymbolId, Symbol> symbols;
  private final List<Diagnostic> diagnostics = new ArrayList<>();

  ConstructorFlowAnalyzer(Map<SourceSpan, SymbolId> bindings, Map<SymbolId, Symbol> symbols) {
    this.bindings = Map.copyOf(bindings);
    this.symbols = Map.copyOf(symbols);
  }

  Result analyze(Input input) {
    ConstructorInitialization beforeSuper = new ConstructorInitialization(input.fields(), true);
    ConstructorFlow prefix = ConstructorFlow.normal(Set.of());
    if (input.constructor().superCall().isPresent()) {
      for (Syntax.CallArgument argument :
          input.constructor().superCall().orElseThrow().arguments()) {
        if (prefix.normal().isEmpty()) break;
        prefix =
            prefix.then(
                expressionFlow(argument.value(), prefix.normal().orElseThrow(), beforeSuper));
      }
    }
    if (prefix.returned().isPresent()) {
      error(
          "constructor cannot return before super initialization",
          input.constructor().superCall().orElseThrow().span());
    }
    ConstructorInitialization body = new ConstructorInitialization(input.fields(), false);
    ConstructorFlow flow =
        prefix.then(
            prefix.normal().isPresent()
                ? flow(input.constructor().body(), input.inheritedFields(), body)
                : ConstructorFlow.empty());
    List<Set<SymbolId>> exits = new ArrayList<>();
    flow.normal().ifPresent(exits::add);
    flow.returned().ifPresent(exits::add);
    for (RequiredField field : input.requiredFields()) {
      if (exits.stream().anyMatch(assigned -> !assigned.contains(field.id()))) {
        error("constructor must initialize field '" + field.name() + "'", field.span());
      }
    }
    return new Result(diagnostics);
  }

  private ConstructorFlow flow(
      List<Syntax.Statement> statements,
      Set<SymbolId> incoming,
      ConstructorInitialization initialization) {
    ConstructorFlow flow = ConstructorFlow.normal(incoming);
    for (Syntax.Statement statement : statements) {
      if (flow.normal().isEmpty()) break;
      ConstructorFlow next = statementFlow(statement, flow.normal().orElseThrow(), initialization);
      flow = flow.then(next);
    }
    return flow;
  }

  private ConstructorFlow statementFlow(
      Syntax.Statement statement,
      Set<SymbolId> incoming,
      ConstructorInitialization initialization) {
    Set<SymbolId> assigned = new HashSet<>(incoming);
    return switch (statement) {
      case Syntax.VariableDecl variable ->
          expressionFlow(variable.initializer(), assigned, initialization);
      case Syntax.Assignment assignment -> {
        SymbolId field = fieldBinding(assignment.target(), initialization);
        ConstructorFlow targetFlow =
            field == null
                ? expressionFlow(assignment.target(), assigned, initialization)
                : ConstructorFlow.normal(assigned);
        if (targetFlow.normal().isEmpty()) yield targetFlow;
        if (field != null && initialization.beforeSuper()) {
          error("field cannot be assigned before super initialization", assignment.target().span());
        }
        ConstructorFlow valueFlow =
            expressionFlow(assignment.value(), targetFlow.normal().orElseThrow(), initialization);
        ConstructorFlow flow = targetFlow.then(valueFlow);
        if (field == null || flow.normal().isEmpty() || initialization.beforeSuper()) yield flow;
        assigned = new HashSet<>(flow.normal().orElseThrow());
        assigned.add(field);
        yield flow.withNormal(assigned);
      }
      case Syntax.ExpressionStatement expression ->
          expressionFlow(expression.expression(), assigned, initialization);
      case Syntax.IfStatement conditional -> {
        ConstructorFlow condition =
            expressionFlow(conditional.condition(), assigned, initialization);
        if (condition.normal().isEmpty()) yield condition;
        Set<SymbolId> afterCondition = condition.normal().orElseThrow();
        ConstructorFlow branches =
            flow(conditional.thenBody(), afterCondition, initialization)
                .merge(flow(conditional.elseBody(), afterCondition, initialization));
        yield condition.withoutNormal().merge(branches);
      }
      case Syntax.ConditionalForStatement loop -> {
        ConstructorFlow condition = expressionFlow(loop.condition(), assigned, initialization);
        if (condition.normal().isEmpty()) yield condition;
        Set<SymbolId> afterCondition = condition.normal().orElseThrow();
        ConstructorFlow body = flow(loop.body(), afterCondition, initialization);
        ConstructorFlow completion =
            new ConstructorFlow(
                Optional.of(afterCondition),
                body.returned(),
                Optional.empty(),
                Optional.empty(),
                ConstructorFlow.mergeAssigned(Optional.of(afterCondition), body.thrown()));
        yield condition.withoutNormal().merge(completion);
      }
      case Syntax.ForStatement loop -> {
        ConstructorFlow iterable = expressionFlow(loop.iterable(), assigned, initialization);
        if (iterable.normal().isEmpty()) yield iterable;
        Set<SymbolId> afterIterable = iterable.normal().orElseThrow();
        ConstructorFlow body = flow(loop.body(), afterIterable, initialization);
        ConstructorFlow completion =
            new ConstructorFlow(
                Optional.of(afterIterable),
                body.returned(),
                Optional.empty(),
                Optional.empty(),
                ConstructorFlow.mergeAssigned(Optional.of(afterIterable), body.thrown()));
        yield iterable.withoutNormal().merge(completion);
      }
      case Syntax.TryStatement tried -> tryFlow(tried, assigned, initialization);
      case Syntax.ThrowStatement thrown -> {
        ConstructorFlow exception = expressionFlow(thrown.exception(), assigned, initialization);
        ConstructorFlow completion =
            exception.normal().isPresent()
                ? ConstructorFlow.thrown(exception.normal().orElseThrow())
                : ConstructorFlow.empty();
        yield exception.withoutNormal().merge(completion);
      }
      case Syntax.ReturnStatement returned -> {
        if (returned.value() == null) yield ConstructorFlow.returned(assigned);
        ConstructorFlow value = expressionFlow(returned.value(), assigned, initialization);
        ConstructorFlow completion =
            value.normal().isPresent()
                ? ConstructorFlow.returned(value.normal().orElseThrow())
                : ConstructorFlow.empty();
        yield value.withoutNormal().merge(completion);
      }
      case Syntax.BreakStatement broken -> {
        if (broken.value() == null) yield ConstructorFlow.broken(assigned);
        ConstructorFlow value = expressionFlow(broken.value(), assigned, initialization);
        ConstructorFlow completion =
            value.normal().isPresent()
                ? ConstructorFlow.broken(value.normal().orElseThrow())
                : ConstructorFlow.empty();
        yield value.withoutNormal().merge(completion);
      }
      case Syntax.ContinueStatement ignored -> ConstructorFlow.continued(assigned);
    };
  }

  private ConstructorFlow tryFlow(
      Syntax.TryStatement tried, Set<SymbolId> incoming, ConstructorInitialization initialization) {
    ConstructorFlow triedFlow = flow(tried.body(), incoming, initialization);
    ConstructorFlow combined = triedFlow;
    if (triedFlow.thrown().isPresent()) {
      Set<SymbolId> catchEntry = triedFlow.thrown().orElseThrow();
      for (Syntax.CatchClause clause : tried.catches()) {
        combined = combined.merge(flow(clause.body(), catchEntry, initialization));
      }
    }
    if (tried.finallyClause().isEmpty()) return combined;
    List<Set<SymbolId>> entries = new ArrayList<>(combined.completionStates());
    ConstructorFlow finalFlow =
        flow(
            tried.finallyClause().orElseThrow().body(),
            ConstructorFlow.intersect(entries),
            initialization);
    ConstructorFlow preserved = combined.afterFinally(finalFlow.normal());
    return preserved.merge(finalFlow.withoutNormal());
  }

  private ConstructorFlow expressionFlow(
      Syntax.Expression expression,
      Set<SymbolId> incoming,
      ConstructorInitialization initialization) {
    Set<SymbolId> assigned = new HashSet<>(incoming);
    return switch (expression) {
      case Syntax.Name name -> {
        validateBindingRead(name.span(), assigned, initialization);
        yield ConstructorFlow.normal(assigned);
      }
      case Syntax.Unary unary -> expressionFlow(unary.operand(), assigned, initialization);
      case Syntax.Binary binary -> {
        ConstructorFlow left = expressionFlow(binary.left(), assigned, initialization);
        if (left.normal().isEmpty()) yield left;
        ConstructorFlow right =
            expressionFlow(binary.right(), left.normal().orElseThrow(), initialization);
        if (binary.operator() == TokenKind.AND_AND || binary.operator() == TokenKind.OR_OR) {
          yield left.withoutNormal()
              .merge(right.withoutNormal())
              .withNormal(left.normal().orElseThrow());
        }
        ConstructorFlow flow = left.then(right);
        if ((binary.operator() == TokenKind.SLASH || binary.operator() == TokenKind.PERCENT)
            && flow.normal().isPresent()) {
          flow = flow.merge(ConstructorFlow.thrown(flow.normal().orElseThrow()));
        }
        yield flow;
      }
      case Syntax.Call call -> {
        SymbolId callee = binding(call.callee());
        if (call.callee() instanceof Syntax.Name
            && callee != null
            && symbols.get(callee) != null
            && symbols.get(callee).kind() == SymbolKind.METHOD) {
          requireInitializedReceiver(call.callee().span(), assigned, initialization);
        }
        ConstructorFlow flow = expressionFlow(call.callee(), assigned, initialization);
        for (Syntax.CallArgument argument : call.arguments()) {
          if (flow.normal().isEmpty()) break;
          flow =
              flow.then(
                  expressionFlow(argument.value(), flow.normal().orElseThrow(), initialization));
        }
        if (flow.normal().isPresent()) {
          flow = flow.merge(ConstructorFlow.thrown(flow.normal().orElseThrow()));
        }
        yield flow;
      }
      case Syntax.Member member -> {
        SymbolId receiver = binding(member.receiver());
        SymbolId field = bindings.get(member.nameSpan());
        if (receiver != null
            && symbols.get(receiver) != null
            && symbols.get(receiver).kind() == SymbolKind.SELF
            && field != null
            && initialization.fields().containsKey(field)) {
          validateBindingRead(member.nameSpan(), assigned, initialization);
          yield ConstructorFlow.normal(assigned);
        } else {
          yield expressionFlow(member.receiver(), assigned, initialization);
        }
      }
      case Syntax.ArrayLiteral array -> {
        ConstructorFlow flow = ConstructorFlow.normal(assigned);
        for (Syntax.Expression value : array.elements()) {
          if (flow.normal().isEmpty()) break;
          flow = flow.then(expressionFlow(value, flow.normal().orElseThrow(), initialization));
        }
        yield flow;
      }
      case Syntax.Index index -> {
        ConstructorFlow receiver = expressionFlow(index.receiver(), assigned, initialization);
        if (receiver.normal().isEmpty()) yield receiver;
        ConstructorFlow flow =
            receiver.then(
                expressionFlow(index.index(), receiver.normal().orElseThrow(), initialization));
        if (flow.normal().isPresent()) {
          flow = flow.merge(ConstructorFlow.thrown(flow.normal().orElseThrow()));
        }
        yield flow;
      }
      case Syntax.SwitchExpression switched -> {
        ConstructorFlow value = expressionFlow(switched.value(), assigned, initialization);
        if (value.normal().isEmpty()) yield value;
        List<Set<SymbolId>> caseExits = new ArrayList<>();
        ConstructorFlow abrupt = value.withoutNormal();
        for (Syntax.SwitchCase branch : switched.cases()) {
          ConstructorFlow branchFlow =
              flow(branch.body(), value.normal().orElseThrow(), initialization);
          ConstructorFlow.mergeAssigned(branchFlow.normal(), branchFlow.broken())
              .ifPresent(caseExits::add);
          abrupt = abrupt.merge(branchFlow.withoutNormalAndBroken());
        }
        yield caseExits.isEmpty()
            ? abrupt
            : abrupt.withNormal(ConstructorFlow.intersect(caseExits));
      }
      case Syntax.Lambda lambda -> {
        if (statementsUseSelf(lambda.body())) {
          requireInitializedReceiver(lambda.span(), assigned, initialization);
        }
        yield ConstructorFlow.normal(assigned);
      }
      case Syntax.IntegerLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.DecimalLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.CodePointLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.BooleanLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.NullLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.StringLiteralExpr ignored -> ConstructorFlow.normal(assigned);
      case Syntax.InterpolatedStringExpr interpolation -> {
        ConstructorFlow flow = ConstructorFlow.normal(assigned);
        for (Syntax.Expression value : interpolation.expressions()) {
          if (flow.normal().isEmpty()) break;
          flow = flow.then(expressionFlow(value, flow.normal().orElseThrow(), initialization));
        }
        yield flow;
      }
    };
  }

  private boolean statementsUseSelf(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      boolean usesSelf =
          switch (statement) {
            case Syntax.VariableDecl variable -> expressionUsesSelf(variable.initializer());
            case Syntax.Assignment assignment ->
                expressionUsesSelf(assignment.target()) || expressionUsesSelf(assignment.value());
            case Syntax.ExpressionStatement expression ->
                expressionUsesSelf(expression.expression());
            case Syntax.IfStatement conditional ->
                expressionUsesSelf(conditional.condition())
                    || statementsUseSelf(conditional.thenBody())
                    || statementsUseSelf(conditional.elseBody());
            case Syntax.ConditionalForStatement loop ->
                expressionUsesSelf(loop.condition()) || statementsUseSelf(loop.body());
            case Syntax.ForStatement loop ->
                expressionUsesSelf(loop.iterable()) || statementsUseSelf(loop.body());
            case Syntax.TryStatement tried ->
                statementsUseSelf(tried.body())
                    || tried.catches().stream().anyMatch(clause -> statementsUseSelf(clause.body()))
                    || tried.finallyClause().stream()
                        .anyMatch(clause -> statementsUseSelf(clause.body()));
            case Syntax.ThrowStatement thrown -> expressionUsesSelf(thrown.exception());
            case Syntax.ReturnStatement returned ->
                returned.value() != null && expressionUsesSelf(returned.value());
            case Syntax.BreakStatement broken ->
                broken.value() != null && expressionUsesSelf(broken.value());
            case Syntax.ContinueStatement ignored -> false;
          };
      if (usesSelf) return true;
    }
    return false;
  }

  private boolean expressionUsesSelf(Syntax.Expression expression) {
    return switch (expression) {
      case Syntax.Name name -> {
        SymbolId id = bindings.get(name.span());
        Symbol symbol = id == null ? null : symbols.get(id);
        yield symbol != null
            && (symbol.kind() == SymbolKind.SELF
                || symbol.kind() == SymbolKind.FIELD
                || symbol.kind() == SymbolKind.METHOD);
      }
      case Syntax.Unary unary -> expressionUsesSelf(unary.operand());
      case Syntax.Binary binary ->
          expressionUsesSelf(binary.left()) || expressionUsesSelf(binary.right());
      case Syntax.Call call ->
          expressionUsesSelf(call.callee())
              || call.arguments().stream()
                  .anyMatch(argument -> expressionUsesSelf(argument.value()));
      case Syntax.Member member -> expressionUsesSelf(member.receiver());
      case Syntax.ArrayLiteral array ->
          array.elements().stream().anyMatch(this::expressionUsesSelf);
      case Syntax.Index index ->
          expressionUsesSelf(index.receiver()) || expressionUsesSelf(index.index());
      case Syntax.SwitchExpression switched ->
          expressionUsesSelf(switched.value())
              || switched.cases().stream().anyMatch(branch -> statementsUseSelf(branch.body()));
      case Syntax.Lambda lambda -> statementsUseSelf(lambda.body());
      case Syntax.IntegerLiteral ignored -> false;
      case Syntax.DecimalLiteral ignored -> false;
      case Syntax.CodePointLiteral ignored -> false;
      case Syntax.BooleanLiteral ignored -> false;
      case Syntax.NullLiteral ignored -> false;
      case Syntax.StringLiteralExpr ignored -> false;
      case Syntax.InterpolatedStringExpr interpolation ->
          interpolation.expressions().stream().anyMatch(this::expressionUsesSelf);
    };
  }

  private void validateBindingRead(
      SourceSpan span, Set<SymbolId> assigned, ConstructorInitialization initialization) {
    SymbolId id = bindings.get(span);
    if (id == null) return;
    String field = initialization.fields().get(id);
    if (field != null && !assigned.contains(id)) {
      error("field '" + field + "' is read before initialization", span);
    } else if (symbols.get(id) != null && symbols.get(id).kind() == SymbolKind.SELF) {
      requireInitializedReceiver(span, assigned, initialization);
    }
  }

  private SymbolId fieldBinding(
      Syntax.Expression target, ConstructorInitialization initialization) {
    if (target instanceof Syntax.Name) {
      SymbolId id = binding(target);
      return id != null && initialization.fields().containsKey(id) ? id : null;
    }
    if (target instanceof Syntax.Member member) {
      SymbolId receiver = binding(member.receiver());
      SymbolId id = bindings.get(member.nameSpan());
      if (receiver != null
          && symbols.get(receiver) != null
          && symbols.get(receiver).kind() == SymbolKind.SELF
          && id != null
          && initialization.fields().containsKey(id)) {
        return id;
      }
    }
    return null;
  }

  private SymbolId binding(Syntax.Expression expression) {
    return expression instanceof Syntax.Member member
        ? bindings.get(member.nameSpan())
        : bindings.get(expression.span());
  }

  private void requireInitializedReceiver(
      SourceSpan span, Set<SymbolId> assigned, ConstructorInitialization initialization) {
    if (initialization.beforeSuper() || !assigned.containsAll(initialization.fields().keySet())) {
      error("this cannot be used before initialization completes", span);
    }
  }

  private void error(String message, SourceSpan span) {
    diagnostics.add(Diagnostic.error(INVALID_CONTROL, message, span));
  }

  record Input(
      Syntax.ConstructorDecl constructor,
      Map<SymbolId, String> fields,
      Set<SymbolId> inheritedFields,
      List<RequiredField> requiredFields) {
    Input {
      fields = Map.copyOf(fields);
      inheritedFields = Set.copyOf(inheritedFields);
      requiredFields = List.copyOf(requiredFields);
    }
  }

  record RequiredField(SymbolId id, String name, SourceSpan span) {}

  record Result(List<Diagnostic> diagnostics) {
    Result {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  private record ConstructorInitialization(Map<SymbolId, String> fields, boolean beforeSuper) {
    private ConstructorInitialization {
      fields = Map.copyOf(fields);
    }
  }
}
