package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.LanguageMetadata;
import dev.w0fv1.norm.value.SourceSpan;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class Lowerer {
  private final Language language;
  private final PrintWriter output;
  private final Map<String, FunctionPlan> functions = new LinkedHashMap<>();
  private final Map<String, Syntax.ClassDecl> classes = new HashMap<>();
  private final Map<String, Syntax.EnumDecl> enums = new HashMap<>();
  private final Map<String, RuntimeValues.ClassInfo> classInfo = new HashMap<>();
  private TypedProgram program;
  private Source source;

  Lowerer(Language language, PrintWriter output) {
    this.language = language;
    this.output = Objects.requireNonNull(output, "output");
  }

  ExecutableProgram lower(TypedProgram checkedProgram) {
    program = Objects.requireNonNull(checkedProgram, "checkedProgram");
    var sourceFile = program.syntax().span().source();
    source =
        Source.newBuilder(LanguageMetadata.ID, sourceFile.text(), sourceFile.path().toString())
            .build();
    indexDeclarations();
    planFunctions();
    createCallTargets();
    lowerBodies();
    Map<String, RootCallTarget> targets = new LinkedHashMap<>();
    functions.forEach((name, plan) -> targets.put(name, plan.target));
    return new ExecutableProgram(functions.get("main").target, targets);
  }

  private void indexDeclarations() {
    for (Syntax.EnumDecl declaration : program.syntax().enums()) {
      enums.put(declaration.name(), declaration);
    }
    for (Syntax.ClassDecl declaration : program.syntax().classes()) {
      classes.put(declaration.name(), declaration);
      Map<String, Integer> fields = new LinkedHashMap<>();
      for (int index = 0; index < declaration.fields().size(); index++) {
        fields.put(declaration.fields().get(index).name(), index);
      }
      classInfo.put(
          declaration.name(),
          new RuntimeValues.ClassInfo(declaration.name(), fields, fields.size()));
    }
  }

  private void planFunctions() {
    for (Syntax.FunctionDecl declaration : program.syntax().functions()) {
      functions.put(declaration.name(), plan(declaration, null));
    }
    for (Syntax.ClassDecl owner : program.syntax().classes()) {
      for (Syntax.FunctionDecl declaration : owner.methods()) {
        functions.put(methodKey(owner.name(), declaration.name()), plan(declaration, owner));
      }
    }
  }

  private FunctionPlan plan(Syntax.FunctionDecl declaration, Syntax.ClassDecl owner) {
    FunctionPlan plan = new FunctionPlan(declaration, owner);
    plan.pushScope();
    if (owner != null) {
      plan.thisBinding = plan.allocate("this", owner.name());
      plan.arguments.add(plan.thisBinding);
      plan.scopes.getFirst().put("this", plan.thisBinding);
    }
    for (Syntax.Parameter parameter : declaration.parameters()) {
      FrameBinding binding = plan.allocate(parameter.name(), parameter.type().displayName());
      plan.arguments.add(binding);
      plan.scopes.getFirst().put(parameter.name(), binding);
      plan.declarations.put(parameter.span(), binding);
    }
    scanStatements(declaration.body(), plan);
    plan.popScope();
    plan.descriptor = plan.frame.build();
    return plan;
  }

  private void scanStatements(List<Syntax.Statement> statements, FunctionPlan plan) {
    for (Syntax.Statement statement : statements) {
      switch (statement) {
        case Syntax.VariableDecl variable -> {
          scanExpression(variable.initializer(), plan);
          FrameBinding binding = plan.allocate(variable.name(), variable.type().displayName());
          plan.scopes.getFirst().put(variable.name(), binding);
          plan.declarations.put(variable.span(), binding);
        }
        case Syntax.Assignment assignment -> {
          scanExpression(assignment.target(), plan);
          scanExpression(assignment.value(), plan);
        }
        case Syntax.ExpressionStatement expression -> scanExpression(expression.expression(), plan);
        case Syntax.IfStatement conditional -> {
          scanExpression(conditional.condition(), plan);
          plan.pushScope();
          scanStatements(conditional.thenBody(), plan);
          plan.popScope();
          plan.pushScope();
          scanStatements(conditional.elseBody(), plan);
          plan.popScope();
        }
        case Syntax.ForStatement loop -> {
          scanExpression(loop.iterable(), plan);
          plan.pushScope();
          String variableType =
              program
                  .semanticModel()
                  .symbolOf(loop.variableNameSpan())
                  .orElseThrow(() -> new IllegalStateException("loop binding has no symbol"))
                  .type()
                  .displayName();
          FrameBinding variable = plan.allocate(loop.variableName(), variableType);
          plan.scopes.getFirst().put(loop.variableName(), variable);
          plan.declarations.put(loop.span(), variable);
          plan.iterators.put(loop.span(), plan.allocate("$iterator", "value"));
          scanStatements(loop.body(), plan);
          plan.popScope();
        }
        case Syntax.ReturnStatement returned -> {
          if (returned.value() != null) scanExpression(returned.value(), plan);
        }
        case Syntax.BreakStatement ignored -> {}
        case Syntax.ContinueStatement ignored -> {}
      }
    }
  }

  private void scanExpression(Syntax.Expression expression, FunctionPlan plan) {
    switch (expression) {
      case Syntax.IntegerLiteral ignored -> {}
      case Syntax.BooleanLiteral ignored -> {}
      case Syntax.StringLiteralExpr ignored -> {}
      case Syntax.ArrayLiteral array ->
          array.elements().forEach(item -> scanExpression(item, plan));
      case Syntax.Name name -> {
        FrameBinding binding = plan.resolve(name.value());
        if (binding != null) plan.names.put(name.span(), binding);
      }
      case Syntax.Unary unary -> scanExpression(unary.operand(), plan);
      case Syntax.Binary binary -> {
        scanExpression(binary.left(), plan);
        scanExpression(binary.right(), plan);
      }
      case Syntax.Call call -> {
        scanExpression(call.callee(), plan);
        call.arguments().forEach(argument -> scanExpression(argument.value(), plan));
      }
      case Syntax.Member member -> scanExpression(member.receiver(), plan);
      case Syntax.Index index -> {
        scanExpression(index.receiver(), plan);
        scanExpression(index.index(), plan);
      }
    }
  }

  private void createCallTargets() {
    for (Map.Entry<String, FunctionPlan> entry : functions.entrySet()) {
      FunctionPlan plan = entry.getValue();
      plan.root =
          new FunctionRootNode(
              language,
              entry.getKey(),
              plan.descriptor,
              plan.arguments.toArray(FrameBinding[]::new),
              section(plan.declaration.span()));
      plan.target = plan.root.getCallTarget();
    }
  }

  private void lowerBodies() {
    for (FunctionPlan plan : functions.values()) {
      plan.root.initialize(lowerBlock(plan.declaration.body(), plan));
    }
  }

  private StatementNode lowerBlock(List<Syntax.Statement> statements, FunctionPlan plan) {
    StatementNode[] lowered = new StatementNode[statements.size()];
    for (int index = 0; index < statements.size(); index++) {
      lowered[index] = lowerStatement(statements.get(index), plan);
    }
    SourceSpan span =
        statements.isEmpty()
            ? plan.declaration.span()
            : statements.getFirst().span().cover(statements.getLast().span());
    return new StatementNodes.Block(lowered).at(section(span));
  }

  private StatementNode lowerStatement(Syntax.Statement statement, FunctionPlan plan) {
    StatementNode lowered =
        switch (statement) {
          case Syntax.VariableDecl variable ->
              new StatementNodes.WriteLocal(
                  plan.declarations.get(variable.span()),
                  lowerExpression(variable.initializer(), plan));
          case Syntax.Assignment assignment -> lowerAssignment(assignment, plan);
          case Syntax.ExpressionStatement expression ->
              new StatementNodes.ExpressionStatement(
                  lowerExpression(expression.expression(), plan));
          case Syntax.IfStatement conditional ->
              new StatementNodes.If(
                  lowerExpression(conditional.condition(), plan),
                  lowerBlock(conditional.thenBody(), plan),
                  lowerBlock(conditional.elseBody(), plan));
          case Syntax.ForStatement loop ->
              new StatementNodes.For(
                  plan.iterators.get(loop.span()),
                  plan.declarations.get(loop.span()),
                  lowerExpression(loop.iterable(), plan),
                  lowerBlock(loop.body(), plan));
          case Syntax.ReturnStatement returned ->
              new StatementNodes.Return(
                  returned.value() == null ? null : lowerExpression(returned.value(), plan));
          case Syntax.BreakStatement ignored -> new StatementNodes.Break();
          case Syntax.ContinueStatement ignored -> new StatementNodes.Continue();
        };
    return lowered.at(section(statement.span()));
  }

  private StatementNode lowerAssignment(Syntax.Assignment assignment, FunctionPlan plan) {
    ExpressionNode value = lowerExpression(assignment.value(), plan);
    return switch (assignment.target()) {
      case Syntax.Name name -> {
        FrameBinding binding = plan.names.get(name.span());
        if (binding != null) yield new StatementNodes.WriteLocal(binding, value);
        int field = fieldIndex(plan.owner, name.value());
        yield new StatementNodes.WriteField(
            new ExpressionNodes.ReadLocal(plan.thisBinding), field, value);
      }
      case Syntax.Member member -> {
        ExpressionNode receiver = lowerExpression(member.receiver(), plan);
        String receiverType = typeOf(member.receiver());
        if (receiverType.equals("Pair")) {
          yield new StatementNodes.WritePair(receiver, member.name().equals("first"), value);
        }
        yield new StatementNodes.WriteField(
            receiver, fieldIndex(classes.get(receiverType), member.name()), value);
      }
      case Syntax.Index index ->
          new StatementNodes.WriteIndex(
              lowerExpression(index.receiver(), plan), lowerExpression(index.index(), plan), value);
      default -> throw new IllegalStateException("invalid assignment target");
    };
  }

  private ExpressionNode lowerExpression(Syntax.Expression expression, FunctionPlan plan) {
    ExpressionNode lowered =
        switch (expression) {
          case Syntax.IntegerLiteral integer -> new ExpressionNodes.Literal(integer.value());
          case Syntax.BooleanLiteral bool -> new ExpressionNodes.Literal(bool.value());
          case Syntax.StringLiteralExpr string -> new ExpressionNodes.Literal(string.value());
          case Syntax.ArrayLiteral array ->
              new ExpressionNodes.ArrayLiteral(
                  array.elements().stream()
                      .map(item -> lowerExpression(item, plan))
                      .toArray(ExpressionNode[]::new));
          case Syntax.Name name -> lowerName(name, plan);
          case Syntax.Unary unary -> lowerUnary(unary, plan);
          case Syntax.Binary binary -> lowerBinary(binary, plan);
          case Syntax.Call call -> lowerCall(call, plan);
          case Syntax.Member member -> lowerMember(member, plan);
          case Syntax.Index index ->
              new ExpressionNodes.Index(
                  lowerExpression(index.receiver(), plan), lowerExpression(index.index(), plan));
        };
    return lowered.at(section(expression.span()));
  }

  private ExpressionNode lowerName(Syntax.Name name, FunctionPlan plan) {
    FrameBinding binding = plan.names.get(name.span());
    if (binding != null) return new ExpressionNodes.ReadLocal(binding);
    if (plan.owner != null
        && classInfo.get(plan.owner.name()).fieldIndices().containsKey(name.value())) {
      return new ExpressionNodes.ReadField(
          new ExpressionNodes.ReadLocal(plan.thisBinding),
          fieldIndex(plan.owner, name.value()),
          false);
    }
    throw new IllegalStateException("name is not a runtime value: " + name.value());
  }

  private ExpressionNode lowerUnary(Syntax.Unary unary, FunctionPlan plan) {
    ExpressionNode operand = lowerExpression(unary.operand(), plan);
    return unary.operator() == TokenKind.BANG
        ? new ExpressionNodes.Not(operand)
        : new ExpressionNodes.Negate(operand);
  }

  private ExpressionNode lowerBinary(Syntax.Binary binary, FunctionPlan plan) {
    ExpressionNode left = lowerExpression(binary.left(), plan);
    ExpressionNode right = lowerExpression(binary.right(), plan);
    return switch (binary.operator()) {
      case PLUS ->
          typeOf(binary).equals("String")
              ? new ExpressionNodes.StringConcat(left, right)
              : new ExpressionNodes.Add(left, right);
      case MINUS -> new ExpressionNodes.Subtract(left, right);
      case STAR -> new ExpressionNodes.Multiply(left, right);
      case SLASH -> new ExpressionNodes.Divide(left, right);
      case PERCENT -> new ExpressionNodes.Remainder(left, right);
      case LESS -> new ExpressionNodes.Less(left, right);
      case LESS_EQUAL -> new ExpressionNodes.LessEqual(left, right);
      case GREATER -> new ExpressionNodes.Greater(left, right);
      case GREATER_EQUAL -> new ExpressionNodes.GreaterEqual(left, right);
      case EQUAL_EQUAL -> new ExpressionNodes.Equal(left, right);
      case BANG_EQUAL -> new ExpressionNodes.NotEqual(left, right);
      case AND_AND -> new ExpressionNodes.And(left, right);
      case OR_OR -> new ExpressionNodes.Or(left, right);
      default -> throw new IllegalStateException("unsupported binary operator");
    };
  }

  private ExpressionNode lowerCall(Syntax.Call call, FunctionPlan plan) {
    if (call.callee() instanceof Syntax.Name name) {
      return lowerNamedCall(name.value(), call, plan);
    }
    if (call.callee() instanceof Syntax.Member member) {
      return lowerMethodCall(member, call, plan);
    }
    throw new IllegalStateException("expression is not callable");
  }

  private ExpressionNode lowerNamedCall(String name, Syntax.Call call, FunctionPlan plan) {
    return switch (name) {
      case "print" ->
          new ExpressionNodes.Print(
              lowerExpression(call.arguments().getFirst().value(), plan), output);
      case "range", "Range" -> {
        yield new ExpressionNodes.Range(lowerArguments(call, plan), argumentIndices(call));
      }
      case "min" -> binaryBuiltin(call, plan, true);
      case "max" -> binaryBuiltin(call, plan, false);
      case "abs" ->
          new ExpressionNodes.Absolute(lowerExpression(call.arguments().getFirst().value(), plan));
      case "Array" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.ARRAY);
      case "List" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.LIST);
      case "Map" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.MAP);
      case "Set" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.SET);
      case "Stack" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.STACK);
      case "Queue" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.QUEUE);
      case "Deque" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.DEQUE);
      case "StringBuilder" -> new ExpressionNodes.NewValue(ExpressionNodes.NewKind.BUILDER);
      case "Pair" -> {
        yield new ExpressionNodes.Pair(lowerArguments(call, plan), argumentIndices(call));
      }
      default -> {
        Syntax.ClassDecl classDeclaration = classes.get(name);
        if (classDeclaration != null) {
          yield new ExpressionNodes.Construct(
              classInfo.get(name), lowerArguments(call, plan), argumentIndices(call));
        }
        FunctionPlan target = functions.get(name);
        yield new ExpressionNodes.FunctionCall(
            target.target, lowerArguments(call, plan), argumentIndices(call));
      }
    };
  }

  private ExpressionNode binaryBuiltin(Syntax.Call call, FunctionPlan plan, boolean minimum) {
    ExpressionNode[] arguments = lowerArguments(call, plan);
    int[] parameterIndices = argumentIndices(call);
    return minimum
        ? new ExpressionNodes.Minimum(arguments, parameterIndices)
        : new ExpressionNodes.Maximum(arguments, parameterIndices);
  }

  private ExpressionNode lowerMethodCall(
      Syntax.Member member, Syntax.Call call, FunctionPlan plan) {
    ExpressionNode receiver = lowerExpression(member.receiver(), plan);
    String receiverType = typeOf(member.receiver());
    Syntax.ClassDecl owner = classes.get(receiverType);
    if (owner != null) {
      if (member.name().equals("copy")) return new ExpressionNodes.CopyObject(receiver);
      FunctionPlan target = functions.get(methodKey(receiverType, member.name()));
      return new ExpressionNodes.MethodCall(
          target.target, receiver, lowerArguments(call, plan), argumentIndices(call));
    }
    return new ExpressionNodes.BuiltinMethod(
        methodKind(receiverType, member.name()),
        receiver,
        lowerArguments(call, plan),
        argumentIndices(call));
  }

  private ExpressionNode lowerMember(Syntax.Member member, FunctionPlan plan) {
    if (member.receiver() instanceof Syntax.Name name && enums.containsKey(name.value())) {
      return new ExpressionNodes.EnumMember(name.value(), member.name());
    }
    ExpressionNode receiver = lowerExpression(member.receiver(), plan);
    String receiverType = typeOf(member.receiver());
    if (receiverType.equals("Pair")) {
      return new ExpressionNodes.ReadPair(receiver, member.name().equals("first"));
    }
    if (member.name().equals("length")) return new ExpressionNodes.Length(receiver);
    return new ExpressionNodes.ReadField(
        receiver, fieldIndex(classes.get(receiverType), member.name()), true);
  }

  private ExpressionNodes.MethodKind methodKind(String receiver, String method) {
    if (method.equals("isEmpty")) return ExpressionNodes.MethodKind.IS_EMPTY;
    return ExpressionNodes.MethodKind.valueOf(
        switch (receiver + "." + method) {
          case "List.add" -> "LIST_ADD";
          case "List.get" -> "LIST_GET";
          case "List.removeAt" -> "LIST_REMOVE_AT";
          case "Map.put" -> "MAP_PUT";
          case "Map.get" -> "MAP_GET";
          case "Map.containsKey" -> "MAP_CONTAINS_KEY";
          case "Map.remove" -> "MAP_REMOVE";
          case "Set.add" -> "SET_ADD";
          case "Set.contains" -> "SET_CONTAINS";
          case "Set.remove" -> "SET_REMOVE";
          case "Stack.push" -> "STACK_PUSH";
          case "Stack.pop" -> "STACK_POP";
          case "Stack.peek" -> "STACK_PEEK";
          case "Queue.add" -> "QUEUE_ADD";
          case "Queue.remove" -> "QUEUE_REMOVE";
          case "Queue.peek" -> "QUEUE_PEEK";
          case "Deque.addFirst" -> "DEQUE_ADD_FIRST";
          case "Deque.addLast" -> "DEQUE_ADD_LAST";
          case "Deque.removeFirst" -> "DEQUE_REMOVE_FIRST";
          case "Deque.removeLast" -> "DEQUE_REMOVE_LAST";
          case "Deque.peekFirst" -> "DEQUE_PEEK_FIRST";
          case "Deque.peekLast" -> "DEQUE_PEEK_LAST";
          case "StringBuilder.append" -> "BUILDER_APPEND";
          case "StringBuilder.toString" -> "BUILDER_TO_STRING";
          default ->
              throw new IllegalStateException("unsupported method " + receiver + "." + method);
        });
  }

  private ExpressionNode[] lowerArguments(Syntax.Call call, FunctionPlan plan) {
    return call.arguments().stream()
        .map(argument -> lowerExpression(argument.value(), plan))
        .toArray(ExpressionNode[]::new);
  }

  private int[] argumentIndices(Syntax.Call call) {
    return program
        .semanticModel()
        .argumentsOf(call.span())
        .orElseThrow(() -> new IllegalStateException("call has no checked argument binding"))
        .parameterIndices()
        .stream()
        .mapToInt(Integer::intValue)
        .toArray();
  }

  private String typeOf(Syntax.Expression expression) {
    String type =
        program
            .semanticModel()
            .typeOf(expression.span())
            .orElseThrow(() -> new IllegalStateException("expression has no semantic type"))
            .displayName();
    if (type == null) throw new IllegalStateException("expression has no checked type");
    return type;
  }

  private SourceSection section(SourceSpan span) {
    return source.createSection(span.startOffset(), span.length());
  }

  private static int fieldIndex(Syntax.ClassDecl owner, String name) {
    if (owner == null) throw new IllegalStateException("field has no owning class");
    for (int index = 0; index < owner.fields().size(); index++) {
      if (owner.fields().get(index).name().equals(name)) return index;
    }
    throw new IllegalStateException("unknown field " + owner.name() + "." + name);
  }

  private static String methodKey(String owner, String method) {
    return owner + "." + method;
  }

  private static FrameSlotKind slotKind(String type) {
    return switch (type) {
      case "int" -> FrameSlotKind.Long;
      case "bool" -> FrameSlotKind.Boolean;
      default -> FrameSlotKind.Object;
    };
  }

  private static final class FunctionPlan {
    final Syntax.FunctionDecl declaration;
    final Syntax.ClassDecl owner;
    final FrameDescriptor.Builder frame = FrameDescriptor.newBuilder();
    final List<FrameBinding> arguments = new ArrayList<>();
    final Deque<Map<String, FrameBinding>> scopes = new ArrayDeque<>();
    final Map<SourceSpan, FrameBinding> names = new HashMap<>();
    final Map<SourceSpan, FrameBinding> declarations = new HashMap<>();
    final Map<SourceSpan, FrameBinding> iterators = new HashMap<>();
    FrameBinding thisBinding;
    FrameDescriptor descriptor;
    FunctionRootNode root;
    RootCallTarget target;

    FunctionPlan(Syntax.FunctionDecl declaration, Syntax.ClassDecl owner) {
      this.declaration = declaration;
      this.owner = owner;
    }

    FrameBinding allocate(String name, String type) {
      FrameSlotKind kind = slotKind(type);
      return new FrameBinding(frame.addSlot(kind, name, null), kind);
    }

    FrameBinding resolve(String name) {
      for (Map<String, FrameBinding> scope : scopes) {
        FrameBinding binding = scope.get(name);
        if (binding != null) return binding;
      }
      return null;
    }

    void pushScope() {
      scopes.addFirst(new HashMap<>());
    }

    void popScope() {
      scopes.removeFirst();
    }
  }
}
