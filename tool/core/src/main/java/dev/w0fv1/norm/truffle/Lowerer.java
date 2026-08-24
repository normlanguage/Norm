package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.CoreArgument;
import dev.w0fv1.norm.core.CoreBlock;
import dev.w0fv1.norm.core.CoreCompilation;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionLink;
import dev.w0fv1.norm.core.CoreDefinitionOccurrence;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreLocal;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreRuntimeType;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
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
  private final Map<DefinitionOccurrenceId, RuntimeValues.ClassInfo> classInfo = new HashMap<>();
  private final Map<DefinitionOccurrenceId, FunctionPlan> callables = new LinkedHashMap<>();
  private final Map<DocumentId, Source> sources = new HashMap<>();
  private CoreCompilation compilation;
  private CoreProgram program;

  Lowerer(Language language) {
    this.language = language;
  }

  ExecutableProgram lower(CoreCompilation checkedCompilation) {
    compilation = Objects.requireNonNull(checkedCompilation, "checkedCompilation");
    program = compilation.program();
    indexDefinitions();
    createCallTargets();
    lowerBodies();
    DefinitionOccurrenceId entry = compilation.entryPoint();
    FunctionPlan entryPlan = callables.get(entry);
    if (entryPlan == null) throw new IllegalStateException("entry callable is absent");
    return new ExecutableProgram(entryPlan.target);
  }

  private void indexDefinitions() {
    for (CoreDefinitionOccurrence occurrence : compilation.authoring().occurrences()) {
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      switch (definition) {
        case CoreDefinition.Class declaration ->
            classInfo.put(
                occurrence.id(),
                new RuntimeValues.ClassInfo(
                    compilation.displayName(occurrence.id()), declaration.fields().size()));
        case CoreDefinition.Callable declaration ->
            callables.put(occurrence.id(), plan(occurrence.id(), declaration));
        case CoreDefinition.Enum ignored -> {}
      }
    }
  }

  private FunctionPlan plan(DefinitionOccurrenceId id, CoreDefinition.Callable declaration) {
    FunctionPlan plan = new FunctionPlan(id, declaration);
    for (CoreLocal local : declaration.locals()) plan.allocate(local);
    if (declaration.hasReceiver()) plan.arguments.add(plan.binding(0));
    declaration.parameterLocals().forEach(local -> plan.arguments.add(plan.binding(local)));
    declaration.reifiedTypeLocals().forEach(local -> plan.arguments.add(plan.binding(local)));
    plan.descriptor = plan.frame.build();
    return plan;
  }

  private void createCallTargets() {
    for (FunctionPlan plan : callables.values()) {
      plan.root =
          new FunctionRootNode(
              language,
              compilation.displayName(plan.id),
              plan.descriptor,
              plan.arguments.toArray(FrameBinding[]::new),
              section(plan.id, 0));
      plan.target = plan.root.getCallTarget();
    }
  }

  private void lowerBodies() {
    callables
        .values()
        .forEach(plan -> plan.root.initialize(lowerBlock(plan.declaration.body(), plan)));
  }

  private StatementNode lowerBlock(CoreBlock block, FunctionPlan plan) {
    StatementNode[] statements =
        block.statements().stream()
            .map(statement -> lowerStatement(statement, plan))
            .toArray(StatementNode[]::new);
    return new StatementNodes.Block(statements).at(section(plan.id, block.nodeIndex()));
  }

  private StatementNode lowerStatement(CoreStatement statement, FunctionPlan plan) {
    StatementNode lowered =
        switch (statement) {
          case CoreStatement.LocalDeclaration local ->
              new StatementNodes.WriteLocal(
                  plan.binding(local.localIndex()), lowerExpression(local.initializer(), plan));
          case CoreStatement.LocalAssignment assignment ->
              new StatementNodes.WriteLocal(
                  plan.binding(assignment.localIndex()), lowerExpression(assignment.value(), plan));
          case CoreStatement.FieldAssignment assignment ->
              new StatementNodes.WriteField(
                  lowerExpression(assignment.receiver(), plan),
                  assignment.field().ordinal(),
                  lowerExpression(assignment.value(), plan));
          case CoreStatement.IntrinsicAssignment assignment -> {
            List<ExpressionNode> arguments = new ArrayList<>();
            assignment.index().ifPresent(value -> arguments.add(lowerExpression(value, plan)));
            arguments.add(lowerExpression(assignment.value(), plan));
            yield new StatementNodes.IntrinsicWrite(
                assignment.intrinsic(),
                lowerExpression(assignment.receiver(), plan),
                arguments.toArray(ExpressionNode[]::new));
          }
          case CoreStatement.ExpressionStatement expression ->
              new StatementNodes.ExpressionStatement(
                  lowerExpression(expression.expression(), plan));
          case CoreStatement.IfStatement conditional ->
              new StatementNodes.If(
                  lowerExpression(conditional.condition(), plan),
                  lowerBlock(conditional.thenBlock(), plan),
                  lowerBlock(conditional.elseBlock(), plan));
          case CoreStatement.ForStatement loop ->
              new StatementNodes.For(
                  plan.binding(loop.iteratorLocal()),
                  plan.binding(loop.variableLocal()),
                  lowerExpression(loop.iterable(), plan),
                  lowerBlock(loop.body(), plan),
                  loop.iterationIntrinsic());
          case CoreStatement.ConditionalForStatement loop ->
              new StatementNodes.ConditionalFor(
                  lowerExpression(loop.condition(), plan), lowerBlock(loop.body(), plan));
          case CoreStatement.ReturnStatement returned ->
              new StatementNodes.Return(
                  returned.value().map(value -> lowerExpression(value, plan)).orElse(null));
          case CoreStatement.BreakStatement ignored -> new StatementNodes.Break();
          case CoreStatement.ContinueStatement ignored -> new StatementNodes.Continue();
        };
    return lowered.at(section(plan.id, statement.nodeIndex()));
  }

  private ExpressionNode lowerExpression(CoreExpression expression, FunctionPlan plan) {
    ExpressionNode lowered =
        switch (expression) {
          case CoreExpression.Literal literal ->
              new ExpressionNodes.Literal(
                  literal.type().equals(CoreType.CODE_POINT)
                      ? new RuntimeValues.CodePointValue(((Number) literal.value()).intValue())
                      : literal.value());
          case CoreExpression.NullLiteral ignored -> new ExpressionNodes.NullLiteral();
          case CoreExpression.ArrayLiteral array ->
              new ExpressionNodes.ArrayLiteral(
                  array.elements().stream()
                      .map(value -> lowerExpression(value, plan))
                      .toArray(ExpressionNode[]::new),
                  lowerRuntimeType(array.runtimeType(), plan));
          case CoreExpression.LocalRead local ->
              new ExpressionNodes.ReadLocal(plan.binding(local.localIndex()));
          case CoreExpression.FieldRead field ->
              new ExpressionNodes.ReadField(
                  lowerExpression(field.receiver(), plan),
                  field.field().ordinal(),
                  field.nullSafe());
          case CoreExpression.EnumMember member -> lowerEnumMember(member, plan);
          case CoreExpression.Unary unary ->
              unary.operator() == dev.w0fv1.norm.core.CoreUnaryOperator.NOT
                  ? new ExpressionNodes.Not(lowerExpression(unary.operand(), plan))
                  : new ExpressionNodes.Negate(lowerExpression(unary.operand(), plan));
          case CoreExpression.Binary binary -> lowerBinary(binary, plan);
          case CoreExpression.Index index ->
              new ExpressionNodes.Intrinsic(
                  index.readIntrinsic(),
                  lowerExpression(index.receiver(), plan),
                  new ExpressionNode[] {lowerExpression(index.index(), plan)},
                  new int[] {0},
                  null,
                  false);
          case CoreExpression.CopyObject copied ->
              new ExpressionNodes.CopyObject(
                  lowerExpression(copied.receiver(), plan), copied.nullSafe());
          case CoreExpression.Call call -> lowerCall(call, plan);
          case CoreExpression.Construct construct -> lowerConstruct(construct, plan);
          case CoreExpression.Intrinsic intrinsic ->
              new ExpressionNodes.Intrinsic(
                  intrinsic.intrinsic(),
                  intrinsic.receiver().map(value -> lowerExpression(value, plan)).orElse(null),
                  lowerArguments(intrinsic.arguments(), plan),
                  parameterIndices(intrinsic.arguments()),
                  intrinsic.runtimeType().map(value -> lowerRuntimeType(value, plan)).orElse(null),
                  intrinsic.nullSafe());
        };
    return lowered.at(section(plan.id, expression.nodeIndex()));
  }

  private ExpressionNode lowerEnumMember(CoreExpression.EnumMember member, FunctionPlan plan) {
    DefinitionOccurrenceId target = resolve(plan, member.nodeIndex(), member.target());
    CoreDefinition.Enum declaration =
        (CoreDefinition.Enum) program.definition(target.representative()).orElseThrow();
    return new ExpressionNodes.EnumMember(
        target.representative(),
        member.memberOrdinal(),
        compilation.displayName(target),
        declaration.members().get(member.memberOrdinal()));
  }

  private ExpressionNode lowerConstruct(CoreExpression.Construct construct, FunctionPlan plan) {
    DefinitionOccurrenceId target = resolve(plan, construct.nodeIndex(), construct.target());
    RuntimeValues.ClassInfo info = classInfo.get(target);
    if (info == null) throw new IllegalStateException("core class target is absent: " + target);
    return new ExpressionNodes.Construct(
        info,
        lowerRuntimeType(construct.runtimeType(), plan),
        lowerArguments(construct.arguments(), plan),
        parameterIndices(construct.arguments()));
  }

  private ExpressionNode lowerBinary(CoreExpression.Binary binary, FunctionPlan plan) {
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
      case COALESCE -> new ExpressionNodes.Coalesce(left, right);
    };
  }

  private ExpressionNode lowerCall(CoreExpression.Call call, FunctionPlan plan) {
    DefinitionOccurrenceId targetId = resolve(plan, call.nodeIndex(), call.target());
    FunctionPlan target = callables.get(targetId);
    if (target == null) throw new IllegalStateException("core call target is absent: " + targetId);
    if (call.receiver().isPresent()) {
      return new ExpressionNodes.MethodCall(
          target.target,
          lowerExpression(call.receiver().orElseThrow(), plan),
          lowerArguments(call.arguments(), plan),
          parameterIndices(call.arguments()),
          call.reifiedArguments().stream()
              .map(value -> lowerRuntimeType(value, plan))
              .toArray(ExpressionNode[]::new),
          call.nullSafe());
    }
    return new ExpressionNodes.FunctionCall(
        target.target,
        lowerArguments(call.arguments(), plan),
        parameterIndices(call.arguments()),
        call.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new));
  }

  private DefinitionOccurrenceId resolve(
      FunctionPlan plan, int nodeIndex, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalStateException("pending definition reached the backend");
    }
    DefinitionOccurrenceId target = compilation.authoring().target(plan.id, nodeIndex);
    DefinitionId resolved = program.resolve(plan.id.representative(), reference);
    if (!compilation
        .authoring()
        .occurrence(target)
        .orElseThrow()
        .representedDefinitions()
        .contains(resolved)) {
      throw new IllegalStateException("authoring target does not represent the core reference");
    }
    return target;
  }

  private ExpressionNode[] lowerArguments(List<CoreArgument> arguments, FunctionPlan plan) {
    return arguments.stream()
        .map(argument -> lowerExpression(argument.value(), plan))
        .toArray(ExpressionNode[]::new);
  }

  private static int[] parameterIndices(List<CoreArgument> arguments) {
    return arguments.stream().mapToInt(CoreArgument::parameterIndex).toArray();
  }

  private ExpressionNode lowerRuntimeType(CoreRuntimeType type, FunctionPlan plan) {
    return new ExpressionNodes.TypeDescriptor(
        CoreTypes.absolute(type.template(), plan.id.representative(), program),
        type.captures().stream()
            .mapToInt(dev.w0fv1.norm.core.CoreTypeCapture::typeParameterIndex)
            .toArray(),
        type.captures().stream()
            .map(capture -> plan.binding(capture.localIndex()))
            .toArray(FrameBinding[]::new));
  }

  private SourceSection section(DefinitionOccurrenceId occurrence, int nodeIndex) {
    return compilation.authoring().span(occurrence, nodeIndex).map(this::section).orElse(null);
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

  private static FrameSlotKind slotKind(CoreType type) {
    if (type.equals(CoreType.DYNAMIC) || type.isNullable()) return FrameSlotKind.Object;
    if (type instanceof CoreType.Declared declared) {
      if (!(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)) {
        return FrameSlotKind.Object;
      }
      return switch (builtin.id().value()) {
        case "std.core.Integer" -> FrameSlotKind.Long;
        case "std.core.Boolean" -> FrameSlotKind.Boolean;
        default -> FrameSlotKind.Object;
      };
    }
    return FrameSlotKind.Object;
  }

  private static final class FunctionPlan {
    private final DefinitionOccurrenceId id;
    private final CoreDefinition.Callable declaration;
    private final FrameDescriptor.Builder frame = FrameDescriptor.newBuilder();
    private final List<FrameBinding> arguments = new ArrayList<>();
    private final Map<Integer, FrameBinding> bindings = new LinkedHashMap<>();
    private FrameDescriptor descriptor;
    private FunctionRootNode root;
    private RootCallTarget target;

    private FunctionPlan(DefinitionOccurrenceId id, CoreDefinition.Callable declaration) {
      this.id = id;
      this.declaration = declaration;
    }

    private void allocate(CoreLocal local) {
      FrameSlotKind kind = slotKind(local.type());
      bindings.put(
          local.index(), new FrameBinding(frame.addSlot(kind, "$" + local.index(), null), kind));
    }

    private FrameBinding binding(int local) {
      FrameBinding binding = bindings.get(local);
      if (binding == null) throw new IllegalStateException("core local is absent: " + local);
      return binding;
    }
  }
}
