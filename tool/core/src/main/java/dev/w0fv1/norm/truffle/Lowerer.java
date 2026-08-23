package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.bound.BoundArgument;
import dev.w0fv1.norm.bound.BoundBlock;
import dev.w0fv1.norm.bound.BoundCall;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundCallableId;
import dev.w0fv1.norm.bound.BoundClass;
import dev.w0fv1.norm.bound.BoundClassId;
import dev.w0fv1.norm.bound.BoundConstruct;
import dev.w0fv1.norm.bound.BoundExpression;
import dev.w0fv1.norm.bound.BoundIntrinsic;
import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.bound.BoundRuntimeType;
import dev.w0fv1.norm.bound.BoundStatement;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.LanguageMetadata;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class Lowerer {
  private final Language language;
  private final ExecutionContext context;
  private final Map<BoundClassId, RuntimeValues.ClassInfo> classInfo = new HashMap<>();
  private final Map<BoundCallableId, FunctionPlan> callables = new LinkedHashMap<>();
  private final Map<DocumentId, Source> sources = new HashMap<>();

  Lowerer(Language language, ExecutionContext context) {
    this.language = language;
    this.context = Objects.requireNonNull(context, "context");
  }

  ExecutableProgram lower(BoundProgram checkedProgram) {
    BoundProgram program = Objects.requireNonNull(checkedProgram, "checkedProgram");
    indexClasses(program);
    program.callables().forEach(callable -> callables.put(callable.id(), plan(callable)));
    createCallTargets();
    lowerBodies();
    return new ExecutableProgram(callables.get(program.entryPoint().orElseThrow()).target);
  }

  private void indexClasses(BoundProgram program) {
    for (BoundClass declaration : program.classes()) {
      classInfo.put(
          declaration.id(),
          new RuntimeValues.ClassInfo(declaration.name(), declaration.fields().size()));
    }
  }

  private FunctionPlan plan(BoundCallable declaration) {
    FunctionPlan plan = new FunctionPlan(declaration);
    declaration
        .thisLocal()
        .ifPresent(
            local -> {
              plan.thisBinding = plan.allocate(local, "this", SemanticType.DYNAMIC);
              plan.arguments.add(plan.thisBinding);
            });
    declaration
        .parameters()
        .forEach(
            parameter -> {
              FrameBinding binding =
                  plan.allocate(parameter.id(), parameter.name(), parameter.type());
              plan.arguments.add(binding);
            });
    declaration
        .reifiedParameters()
        .forEach(
            parameter -> {
              FrameBinding binding =
                  plan.allocate(parameter.source(), "$type", SemanticType.DYNAMIC);
              plan.arguments.add(binding);
            });
    scanBlock(declaration.body(), plan);
    plan.descriptor = plan.frame.build();
    return plan;
  }

  private void scanBlock(BoundBlock block, FunctionPlan plan) {
    for (BoundStatement statement : block.statements()) {
      switch (statement) {
        case BoundStatement.LocalDeclaration local ->
            plan.allocate(local.local(), local.name(), local.type());
        case BoundStatement.ForStatement loop -> {
          plan.allocate(loop.iterator(), "$iterator", SemanticType.DYNAMIC);
          plan.allocate(loop.variable(), loop.variableName(), loop.variableType());
          scanBlock(loop.body(), plan);
        }
        case BoundStatement.IfStatement conditional -> {
          scanBlock(conditional.thenBlock(), plan);
          scanBlock(conditional.elseBlock(), plan);
        }
        default -> {}
      }
    }
  }

  private void createCallTargets() {
    for (FunctionPlan plan : callables.values()) {
      plan.root =
          new FunctionRootNode(
              language,
              plan.declaration.name(),
              plan.descriptor,
              plan.arguments.toArray(FrameBinding[]::new),
              section(plan.declaration.span()));
      plan.target = plan.root.getCallTarget();
    }
  }

  private void lowerBodies() {
    callables
        .values()
        .forEach(plan -> plan.root.initialize(lowerBlock(plan.declaration.body(), plan)));
  }

  private StatementNode lowerBlock(BoundBlock block, FunctionPlan plan) {
    StatementNode[] statements =
        block.statements().stream()
            .map(statement -> lowerStatement(statement, plan))
            .toArray(StatementNode[]::new);
    return new StatementNodes.Block(statements).at(section(block.span()));
  }

  private StatementNode lowerStatement(BoundStatement statement, FunctionPlan plan) {
    StatementNode lowered =
        switch (statement) {
          case BoundStatement.LocalDeclaration local ->
              new StatementNodes.WriteLocal(
                  plan.binding(local.local()), lowerExpression(local.initializer(), plan));
          case BoundStatement.LocalAssignment assignment ->
              new StatementNodes.WriteLocal(
                  plan.binding(assignment.local()), lowerExpression(assignment.value(), plan));
          case BoundStatement.FieldAssignment assignment ->
              new StatementNodes.WriteField(
                  lowerExpression(assignment.receiver(), plan),
                  assignment.ordinal(),
                  lowerExpression(assignment.value(), plan));
          case BoundStatement.IntrinsicAssignment assignment -> {
            List<ExpressionNode> arguments = new ArrayList<>();
            assignment.index().ifPresent(value -> arguments.add(lowerExpression(value, plan)));
            arguments.add(lowerExpression(assignment.value(), plan));
            yield new StatementNodes.IntrinsicWrite(
                assignment.intrinsic(),
                lowerExpression(assignment.receiver(), plan),
                context,
                arguments.toArray(ExpressionNode[]::new));
          }
          case BoundStatement.ExpressionStatement expression ->
              new StatementNodes.ExpressionStatement(
                  lowerExpression(expression.expression(), plan));
          case BoundStatement.IfStatement conditional ->
              new StatementNodes.If(
                  lowerExpression(conditional.condition(), plan),
                  lowerBlock(conditional.thenBlock(), plan),
                  lowerBlock(conditional.elseBlock(), plan));
          case BoundStatement.ForStatement loop ->
              new StatementNodes.For(
                  plan.binding(loop.iterator()),
                  plan.binding(loop.variable()),
                  lowerExpression(loop.iterable(), plan),
                  lowerBlock(loop.body(), plan),
                  loop.iterationIntrinsic(),
                  context);
          case BoundStatement.ReturnStatement returned ->
              new StatementNodes.Return(
                  returned.value().map(value -> lowerExpression(value, plan)).orElse(null));
          case BoundStatement.BreakStatement ignored -> new StatementNodes.Break();
          case BoundStatement.ContinueStatement ignored -> new StatementNodes.Continue();
        };
    return lowered.at(section(statement.span()));
  }

  private ExpressionNode lowerExpression(BoundExpression expression, FunctionPlan plan) {
    ExpressionNode lowered =
        switch (expression) {
          case BoundExpression.Literal literal -> new ExpressionNodes.Literal(literal.value());
          case BoundExpression.ArrayLiteral array ->
              new ExpressionNodes.ArrayLiteral(
                  array.elements().stream()
                      .map(value -> lowerExpression(value, plan))
                      .toArray(ExpressionNode[]::new),
                  lowerRuntimeType(array.runtimeType(), plan));
          case BoundExpression.LocalRead local ->
              new ExpressionNodes.ReadLocal(plan.binding(local.local()));
          case BoundExpression.FieldRead field ->
              new ExpressionNodes.ReadField(
                  lowerExpression(field.receiver(), plan), field.ordinal());
          case BoundExpression.EnumMember member ->
              new ExpressionNodes.EnumMember(member.enumName(), member.memberName());
          case BoundExpression.Unary unary ->
              unary.operator() == dev.w0fv1.norm.bound.BoundUnaryOperator.NOT
                  ? new ExpressionNodes.Not(lowerExpression(unary.operand(), plan))
                  : new ExpressionNodes.Negate(lowerExpression(unary.operand(), plan));
          case BoundExpression.Binary binary -> lowerBinary(binary, plan);
          case BoundExpression.Index index ->
              new ExpressionNodes.Intrinsic(
                  index.readIntrinsic(),
                  lowerExpression(index.receiver(), plan),
                  new ExpressionNode[] {lowerExpression(index.index(), plan)},
                  new int[] {0},
                  null,
                  context);
          case BoundExpression.CopyObject copied ->
              new ExpressionNodes.CopyObject(lowerExpression(copied.receiver(), plan));
          case BoundCall call -> lowerCall(call, plan);
          case BoundConstruct construct ->
              new ExpressionNodes.Construct(
                  classInfo.get(construct.target()),
                  lowerRuntimeType(construct.runtimeType(), plan),
                  lowerArguments(construct.arguments(), plan),
                  parameterIndices(construct.arguments()));
          case BoundIntrinsic intrinsic ->
              new ExpressionNodes.Intrinsic(
                  intrinsic.intrinsic(),
                  intrinsic.receiver().map(value -> lowerExpression(value, plan)).orElse(null),
                  lowerArguments(intrinsic.arguments(), plan),
                  parameterIndices(intrinsic.arguments()),
                  intrinsic.runtimeType().map(value -> lowerRuntimeType(value, plan)).orElse(null),
                  context);
        };
    return lowered.at(section(expression.span()));
  }

  private ExpressionNode lowerBinary(BoundExpression.Binary binary, FunctionPlan plan) {
    ExpressionNode left = lowerExpression(binary.left(), plan);
    ExpressionNode right = lowerExpression(binary.right(), plan);
    return switch (binary.operator()) {
      case ADD -> new ExpressionNodes.Add(left, right);
      case STRING_CONCAT -> new ExpressionNodes.StringConcat(left, right);
      case SUBTRACT -> new ExpressionNodes.Subtract(left, right);
      case MULTIPLY -> new ExpressionNodes.Multiply(left, right);
      case DIVIDE -> new ExpressionNodes.Divide(left, right);
      case REMAINDER -> new ExpressionNodes.Remainder(left, right);
      case LESS -> new ExpressionNodes.Less(left, right);
      case LESS_EQUAL -> new ExpressionNodes.LessEqual(left, right);
      case GREATER -> new ExpressionNodes.Greater(left, right);
      case GREATER_EQUAL -> new ExpressionNodes.GreaterEqual(left, right);
      case EQUAL -> new ExpressionNodes.Equal(left, right);
      case NOT_EQUAL -> new ExpressionNodes.NotEqual(left, right);
      case AND -> new ExpressionNodes.And(left, right);
      case OR -> new ExpressionNodes.Or(left, right);
    };
  }

  private ExpressionNode lowerCall(BoundCall call, FunctionPlan plan) {
    FunctionPlan target = callables.get(call.target());
    if (target == null)
      throw new IllegalStateException("bound call target is absent: " + call.target());
    if (call.receiver().isPresent()) {
      return new ExpressionNodes.MethodCall(
          target.target,
          lowerExpression(call.receiver().orElseThrow(), plan),
          lowerArguments(call.arguments(), plan),
          parameterIndices(call.arguments()));
    }
    return new ExpressionNodes.FunctionCall(
        target.target,
        lowerArguments(call.arguments(), plan),
        parameterIndices(call.arguments()),
        call.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new));
  }

  private ExpressionNode[] lowerArguments(List<BoundArgument> arguments, FunctionPlan plan) {
    return arguments.stream()
        .map(argument -> lowerExpression(argument.value(), plan))
        .toArray(ExpressionNode[]::new);
  }

  private static int[] parameterIndices(List<BoundArgument> arguments) {
    return arguments.stream().mapToInt(BoundArgument::parameterIndex).toArray();
  }

  private ExpressionNode lowerRuntimeType(BoundRuntimeType type, FunctionPlan plan) {
    return new ExpressionNodes.TypeDescriptor(
        type.type(),
        type.reifiedArguments().stream()
            .map(dev.w0fv1.norm.bound.BoundReifiedArgument::typeParameterIdentity)
            .toArray(String[]::new),
        type.reifiedArguments().stream()
            .map(argument -> plan.binding(argument.source()))
            .toArray(FrameBinding[]::new));
  }

  private SourceSection section(SourceSpan span) {
    Source source =
        sources.computeIfAbsent(
            span.source().id(),
            ignored ->
                Source.newBuilder(
                        LanguageMetadata.ID, span.source().text(), span.source().displayName())
                    .uri(span.source().id().uri())
                    .build());
    return source.createSection(span.startOffset(), span.length());
  }

  private static FrameSlotKind slotKind(SemanticType type) {
    if (type.equals(SemanticType.DYNAMIC)) return FrameSlotKind.Object;
    return switch (type.identity()) {
      case "std.core.Integer" -> FrameSlotKind.Long;
      case "std.core.Boolean" -> FrameSlotKind.Boolean;
      default -> FrameSlotKind.Object;
    };
  }

  private static final class FunctionPlan {
    final BoundCallable declaration;
    final FrameDescriptor.Builder frame = FrameDescriptor.newBuilder();
    final List<FrameBinding> arguments = new ArrayList<>();
    final Map<BoundLocalId, FrameBinding> bindings = new LinkedHashMap<>();
    FrameBinding thisBinding;
    FrameDescriptor descriptor;
    FunctionRootNode root;
    RootCallTarget target;

    FunctionPlan(BoundCallable declaration) {
      this.declaration = declaration;
    }

    FrameBinding allocate(BoundLocalId id, String name, SemanticType type) {
      FrameBinding existing = bindings.get(id);
      if (existing != null) return existing;
      FrameSlotKind kind = slotKind(type);
      FrameBinding binding = new FrameBinding(frame.addSlot(kind, name, null), kind);
      bindings.put(id, binding);
      return binding;
    }

    FrameBinding binding(BoundLocalId id) {
      FrameBinding binding = bindings.get(id);
      if (binding == null) throw new IllegalStateException("bound local is absent: " + id);
      return binding;
    }
  }
}
