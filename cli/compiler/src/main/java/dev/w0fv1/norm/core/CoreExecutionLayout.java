package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class CoreExecutionLayout extends CoreWalker {
  private final Map<Integer, String> blocks = new LinkedHashMap<>();
  private final Map<Integer, String> statements = new LinkedHashMap<>();
  private final Map<Integer, String> expressions = new LinkedHashMap<>();
  private final Set<Integer> controlBoundaryExpressions = new LinkedHashSet<>();
  private final Map<Integer, String> locals = new LinkedHashMap<>();
  private final Map<Integer, String> names;
  private final Map<String, java.util.List<String>> blockStatements = new LinkedHashMap<>();
  private final Map<String, java.util.List<String>> statementExpressions = new LinkedHashMap<>();
  private final Set<String> stateWrites = new LinkedHashSet<>();
  private final java.util.List<CoreCallableParameter> parameters;
  private final Map<Integer, Integer> guardedBlocks = new HashMap<>();
  private final Map<String, Set<Integer>> reads = new LinkedHashMap<>();
  private final Map<String, Set<Integer>> declarations = new LinkedHashMap<>();
  private final Map<String, java.util.List<Object>> siblings = new LinkedHashMap<>();
  private final Map<String, java.util.List<String>> routes = new LinkedHashMap<>();
  private final Deque<Scope> scopes = new ArrayDeque<>();

  public CoreExecutionLayout(CoreDefinition.Callable callable, CoreDefinitionOrigin origin) {
    names = origin.localNames();
    parameters = callable.parameters();
    for (CoreLocal local : callable.locals()) {
      locals.put(
          local.index(),
          local.kind() + ":" + names.getOrDefault(local.index(), Integer.toString(local.index())));
    }
    scopes.push(new Scope(""));
    walkExecution(callable);
    scopes.pop();
  }

  public boolean maps(String key, CoreExecutionLayout candidate) {
    var route = routes.get(key);
    return route != null
        && candidate.routes.containsKey(key)
        && route.stream()
            .allMatch(
                group ->
                    siblings.get(group).size()
                            == candidate.siblings.getOrDefault(group, java.util.List.of()).size()
                        && (siblings.get(group).size() == 1
                            || siblings.get(group).equals(candidate.siblings.get(group))));
  }

  public String block(int index) {
    return blocks.get(index);
  }

  public String statement(int index) {
    return statements.get(index);
  }

  public java.util.List<String> statements(String block) {
    return java.util.List.copyOf(blockStatements.getOrDefault(block, java.util.List.of()));
  }

  public java.util.List<String> expressions(String statement) {
    return java.util.List.copyOf(statementExpressions.getOrDefault(statement, java.util.List.of()));
  }

  public boolean writesState(String expression) {
    return stateWrites.contains(expression);
  }

  @Override
  protected void visitIntrinsic(dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
    if (!intrinsic.writesState()) return;
    for (Scope scope : scopes) {
      if (!scope.expression) break;
      stateWrites.add(scope.key);
    }
  }

  public String expression(int index) {
    return expressions.get(index);
  }

  public boolean isControlBoundaryExpression(int index) {
    return controlBoundaryExpressions.contains(index);
  }

  public String local(int index) {
    return locals.get(index);
  }

  public Set<Integer> required(String key) {
    var result = new LinkedHashSet<>(reads.getOrDefault(key, Set.of()));
    result.removeAll(declarations.getOrDefault(key, Set.of()));
    return Set.copyOf(result);
  }

  @Override
  protected void visitBlock(CoreBlock block, BlockRole role) {
    String key = scopes.peek().next("block:" + role.name(), block);
    blocks.put(block.nodeIndex(), key);
    var scope = new Scope(key);
    scope.guardedLocal = guardedBlocks.get(block.nodeIndex());
    scopes.push(scope);
  }

  @Override
  protected void leaveBlock(CoreBlock block) {
    scopes.pop();
  }

  @Override
  protected void visitStatement(CoreStatement statement) {
    if (statement instanceof CoreStatement.IfStatement conditional
        && conditional.condition() instanceof CoreExpression.ParameterProvided provided)
      guardedBlocks.put(
          conditional.thenBlock().nodeIndex(),
          parameters.get(provided.parameterIndex()).localIndex());
    String role =
        switch (statement) {
          case CoreStatement.LocalDeclaration local ->
              "local:"
                  + names.getOrDefault(local.localIndex(), Integer.toString(local.localIndex()));
          case CoreStatement.LocalAssignment local ->
              "assign:"
                  + names.getOrDefault(local.localIndex(), Integer.toString(local.localIndex()));
          default -> statement.getClass().getSimpleName();
        };
    String key = scopes.peek().next(role, statement);
    blockStatements
        .computeIfAbsent(scopes.peek().key, ignored -> new java.util.ArrayList<>())
        .add(key);
    statements.put(statement.nodeIndex(), key);
    if (statement instanceof CoreStatement.LocalDeclaration local) {
      locals.put(local.localIndex(), key);
      for (Scope scope : scopes)
        declarations
            .computeIfAbsent(scope.key, ignored -> new LinkedHashSet<>())
            .add(local.localIndex());
    }
    if (statement instanceof CoreStatement.IfStatement conditional)
      controlBoundaryExpressions.add(conditional.condition().nodeIndex());
    if (statement instanceof CoreStatement.ForStatement loop) {
      controlBoundaryExpressions.add(loop.iterable().nodeIndex());
      for (Scope scope : scopes) {
        Set<Integer> declared =
            declarations.computeIfAbsent(scope.key, ignored -> new LinkedHashSet<>());
        declared.add(loop.iteratorLocal());
        declared.add(loop.variableLocal());
        loop.indexLocal().ifPresent(declared::add);
      }
      locals.put(loop.iteratorLocal(), key + "/iterator");
      locals.put(loop.variableLocal(), key + "/element");
      loop.indexLocal().ifPresent(index -> locals.put(index, key + "/index"));
    }
    if (statement instanceof CoreStatement.ConditionalForStatement loop)
      controlBoundaryExpressions.add(loop.condition().nodeIndex());
    if (statement instanceof CoreStatement.TryStatement tried) {
      for (Scope scope : scopes)
        tried
            .catches()
            .forEach(
                clause ->
                    declarations
                        .computeIfAbsent(scope.key, ignored -> new LinkedHashSet<>())
                        .add(clause.localIndex()));
      for (int index = 0; index < tried.catches().size(); index++)
        locals.put(tried.catches().get(index).localIndex(), key + "/catch:" + index);
    }
    Scope scope = new Scope(key);
    scope.statement = true;
    scopes.push(scope);
  }

  @Override
  protected void leaveStatement(CoreStatement statement) {
    scopes.pop();
  }

  @Override
  protected void visitExpression(CoreExpression expression) {
    String key = scopes.peek().next("expression", expression);
    if (scopes.peek().statement)
      statementExpressions
          .computeIfAbsent(scopes.peek().key, ignored -> new java.util.ArrayList<>())
          .add(key);
    expressions.put(expression.nodeIndex(), key);
    Scope expressionScope = new Scope(key);
    expressionScope.expression = true;
    scopes.push(expressionScope);
    Integer local =
        switch (expression) {
          case CoreExpression.LocalRead read -> read.localIndex();
          case CoreExpression.AddressLocal address -> address.localIndex();
          default -> null;
        };
    if (local != null)
      for (Scope scope : scopes) {
        reads.computeIfAbsent(scope.key, ignored -> new LinkedHashSet<>()).add(local);
        if (local.equals(scope.guardedLocal)) break;
      }
    if (expression instanceof CoreExpression.Let let) {
      locals.put(let.localIndex(), key + "/let");
      for (Scope scope : scopes)
        declarations
            .computeIfAbsent(scope.key, ignored -> new LinkedHashSet<>())
            .add(let.localIndex());
    }
  }

  @Override
  protected void visitPattern(CorePattern pattern) {
    if (!(pattern instanceof CorePattern.Binding binding)) return;
    for (Scope scope : scopes)
      declarations
          .computeIfAbsent(scope.key, ignored -> new LinkedHashSet<>())
          .add(binding.localIndex());
  }

  @Override
  protected void leaveExpression(CoreExpression expression) {
    scopes.pop();
  }

  private final class Scope {
    private final String key;
    private Integer guardedLocal;
    private boolean statement;
    private boolean expression;
    private final Map<String, Integer> ordinals = new HashMap<>();

    private Scope(String key) {
      this.key = key;
    }

    private String next(String role, Object node) {
      String group = key + "/" + role.length() + ":" + role;
      int count = ordinals.merge(role, 1, Integer::sum);
      siblings.computeIfAbsent(group, ignored -> new java.util.ArrayList<>()).add(node);
      String location = group + ":" + (count - 1);
      var route = new java.util.ArrayList<String>();
      for (Scope scope : scopes)
        if (!scope.key.isEmpty()) route.add(scope.key.substring(0, scope.key.lastIndexOf(':')));
      route.add(group);
      routes.put(location, java.util.List.copyOf(route));
      return location;
    }
  }
}
