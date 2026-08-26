package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundArgument;
import dev.w0fv1.norm.bound.BoundBlock;
import dev.w0fv1.norm.bound.BoundCall;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundClosure;
import dev.w0fv1.norm.bound.BoundConstruct;
import dev.w0fv1.norm.bound.BoundExpression;
import dev.w0fv1.norm.bound.BoundIntrinsic;
import dev.w0fv1.norm.bound.BoundInvoke;
import dev.w0fv1.norm.bound.BoundIteration;
import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.bound.BoundPattern;
import dev.w0fv1.norm.bound.BoundRuntimeType;
import dev.w0fv1.norm.bound.BoundStatement;
import dev.w0fv1.norm.core.CoreArgument;
import dev.w0fv1.norm.core.CoreBinaryOperator;
import dev.w0fv1.norm.core.CoreBlock;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreFieldReference;
import dev.w0fv1.norm.core.CoreIteration;
import dev.w0fv1.norm.core.CoreLocal;
import dev.w0fv1.norm.core.CorePattern;
import dev.w0fv1.norm.core.CoreRuntimeType;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.CoreSwitchCase;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeCapture;
import dev.w0fv1.norm.core.CoreUnaryOperator;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

final class BoundCoreBodyConverter {
  private final BoundCoreTypeConverter types;
  private final Optional<CoreType> receiverType;
  private final ToIntFunction<String> declarationIndex;
  private final ToIntFunction<String> fieldOwnerIndex;
  private final LocalTable locals = new LocalTable();
  private final NodeTable nodes = new NodeTable();
  private final Map<Integer, Integer> referenceTargets = new LinkedHashMap<>();

  BoundCoreBodyConverter(
      BoundCoreTypeConverter types,
      Optional<CoreType> receiverType,
      ToIntFunction<String> declarationIndex,
      ToIntFunction<String> fieldOwnerIndex) {
    this.types = Objects.requireNonNull(types, "types");
    this.receiverType = Objects.requireNonNull(receiverType, "receiverType");
    this.declarationIndex = Objects.requireNonNull(declarationIndex, "declarationIndex");
    this.fieldOwnerIndex = Objects.requireNonNull(fieldOwnerIndex, "fieldOwnerIndex");
  }

  Result convert(BoundCallable declaration) {
    declaration
        .thisLocal()
        .ifPresent(value -> locals.add(value, receiverType.orElseThrow(), CoreLocal.Kind.RECEIVER));
    declaration
        .captures()
        .forEach(
            capture ->
                locals.add(capture.id(), types.convert(capture.type()), CoreLocal.Kind.CAPTURE));
    declaration
        .parameters()
        .forEach(
            parameter ->
                locals.add(
                    parameter.id(), types.convert(parameter.type()), CoreLocal.Kind.PARAMETER));
    declaration
        .reifiedParameters()
        .forEach(
            parameter ->
                locals.add(parameter.source(), CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE));
    scanLocals(declaration.body());
    nodes.add(0, declaration.span());
    CoreBlock body = convert(declaration.body());
    return new Result(body, locals.values(), locals.indices(), nodes.spans(), referenceTargets);
  }

  private void scanLocals(BoundBlock block) {
    for (BoundStatement statement : block.statements()) {
      switch (statement) {
        case BoundStatement.LocalDeclaration local -> {
          locals.add(local.local(), types.convert(local.type()), CoreLocal.Kind.VARIABLE);
          scanExpression(local.initializer());
        }
        case BoundStatement.LocalAssignment assignment -> scanExpression(assignment.value());
        case BoundStatement.FieldAssignment assignment -> {
          scanExpression(assignment.receiver());
          scanExpression(assignment.value());
        }
        case BoundStatement.IntrinsicAssignment assignment -> {
          scanExpression(assignment.receiver());
          assignment.index().ifPresent(this::scanExpression);
          scanExpression(assignment.value());
        }
        case BoundStatement.IfStatement conditional -> {
          scanExpression(conditional.condition());
          scanLocals(conditional.thenBlock());
          scanLocals(conditional.elseBlock());
        }
        case BoundStatement.ConditionalForStatement loop -> {
          scanExpression(loop.condition());
          scanLocals(loop.body());
        }
        case BoundStatement.ForStatement loop -> {
          locals.add(loop.iterator(), CoreType.DYNAMIC, CoreLocal.Kind.ITERATOR);
          locals.add(loop.variable(), types.convert(loop.variableType()), CoreLocal.Kind.VARIABLE);
          loop.index()
              .ifPresent(index -> locals.add(index, CoreType.INTEGER, CoreLocal.Kind.VARIABLE));
          scanExpression(loop.iterable());
          scanLocals(loop.body());
        }
        case BoundStatement.ExpressionStatement expression ->
            scanExpression(expression.expression());
        case BoundStatement.ReturnStatement returned ->
            returned.value().ifPresent(this::scanExpression);
        case BoundStatement.YieldStatement yielded -> scanExpression(yielded.value());
        case BoundStatement.BreakStatement ignored -> {}
        case BoundStatement.ContinueStatement ignored -> {}
      }
    }
  }

  private void scanExpression(BoundExpression expression) {
    switch (expression) {
      case BoundExpression.Literal ignored -> {}
      case BoundExpression.NullLiteral ignored -> {}
      case BoundExpression.CollectionLiteral collection ->
          collection.elements().forEach(this::scanExpression);
      case BoundExpression.LocalRead ignored -> {}
      case BoundExpression.FieldRead field -> scanExpression(field.receiver());
      case BoundExpression.EnumConstruct construct ->
          construct.arguments().forEach(argument -> scanExpression(argument.value()));
      case BoundExpression.Unary unary -> scanExpression(unary.operand());
      case BoundExpression.Binary binary -> {
        scanExpression(binary.left());
        scanExpression(binary.right());
      }
      case BoundExpression.Switch switched -> {
        scanExpression(switched.value());
        switched.cases().forEach(value -> scanPattern(value.pattern()));
        switched.cases().forEach(value -> scanLocals(value.body()));
      }
      case BoundExpression.Index index -> {
        scanExpression(index.receiver());
        scanExpression(index.index());
      }
      case BoundExpression.CopyObject copied -> scanExpression(copied.receiver());
      case BoundClosure closure -> {
        closure.receiver().ifPresent(this::scanExpression);
        closure.captures().forEach(this::scanExpression);
      }
      case BoundInvoke invoke -> {
        scanExpression(invoke.callee());
        invoke.arguments().forEach(argument -> scanExpression(argument.value()));
      }
      case BoundExpression.InterfaceCall call -> {
        scanExpression(call.receiver());
        call.arguments().forEach(argument -> scanExpression(argument.value()));
      }
      case BoundCall call -> {
        call.receiver().ifPresent(this::scanExpression);
        call.arguments().forEach(argument -> scanExpression(argument.value()));
      }
      case BoundConstruct construct ->
          construct.arguments().forEach(argument -> scanExpression(argument.value()));
      case BoundIntrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(this::scanExpression);
        intrinsic.arguments().forEach(argument -> scanExpression(argument.value()));
      }
    }
  }

  private void scanPattern(BoundPattern pattern) {
    switch (pattern) {
      case BoundPattern.Binding binding ->
          locals.add(binding.local(), types.convert(binding.type()), CoreLocal.Kind.VARIABLE);
      case BoundPattern.Variant variant -> variant.arguments().forEach(this::scanPattern);
      case BoundPattern.Wildcard ignored -> {}
      case BoundPattern.Literal ignored -> {}
      case BoundPattern.Null ignored -> {}
    }
  }

  private CoreBlock convert(BoundBlock block) {
    int node = nodes.add(block.span());
    return new CoreBlock(node, block.statements().stream().map(this::convert).toList());
  }

  private CoreStatement convert(BoundStatement statement) {
    int node = nodes.add(statement.span());
    return switch (statement) {
      case BoundStatement.LocalDeclaration local ->
          new CoreStatement.LocalDeclaration(
              node, locals.index(local.local()), convert(local.initializer()));
      case BoundStatement.LocalAssignment assignment ->
          new CoreStatement.LocalAssignment(
              node, locals.index(assignment.local()), convert(assignment.value()));
      case BoundStatement.FieldAssignment assignment ->
          new CoreStatement.FieldAssignment(
              node,
              convert(assignment.receiver()),
              field(assignment.field().value(), assignment.ordinal()),
              convert(assignment.value()));
      case BoundStatement.IntrinsicAssignment assignment ->
          new CoreStatement.IntrinsicAssignment(
              node,
              assignment.intrinsic(),
              convert(assignment.receiver()),
              assignment.index().map(this::convert),
              convert(assignment.value()));
      case BoundStatement.ExpressionStatement expression ->
          new CoreStatement.ExpressionStatement(node, convert(expression.expression()));
      case BoundStatement.IfStatement conditional ->
          new CoreStatement.IfStatement(
              node,
              convert(conditional.condition()),
              convert(conditional.thenBlock()),
              convert(conditional.elseBlock()));
      case BoundStatement.ConditionalForStatement loop ->
          new CoreStatement.ConditionalForStatement(
              node, convert(loop.condition()), convert(loop.body()));
      case BoundStatement.ForStatement loop ->
          new CoreStatement.ForStatement(
              node,
              locals.index(loop.iterator()),
              locals.index(loop.variable()),
              loop.index().isPresent()
                  ? java.util.OptionalInt.of(locals.index(loop.index().orElseThrow()))
                  : java.util.OptionalInt.empty(),
              convert(loop.iterable()),
              convert(loop.body()),
              convert(loop.iteration()));
      case BoundStatement.ReturnStatement returned ->
          new CoreStatement.ReturnStatement(node, returned.value().map(this::convert));
      case BoundStatement.YieldStatement yielded ->
          new CoreStatement.YieldStatement(node, convert(yielded.value()));
      case BoundStatement.BreakStatement ignored -> new CoreStatement.BreakStatement(node);
      case BoundStatement.ContinueStatement ignored -> new CoreStatement.ContinueStatement(node);
    };
  }

  private CoreExpression convert(BoundExpression expression) {
    int node = nodes.add(expression.span());
    return switch (expression) {
      case BoundExpression.Literal literal ->
          new CoreExpression.Literal(node, literal.value(), types.convert(literal.type()));
      case BoundExpression.NullLiteral literal ->
          new CoreExpression.NullLiteral(node, types.convert(literal.type()));
      case BoundExpression.CollectionLiteral collection ->
          new CoreExpression.CollectionLiteral(
              node,
              collection.elements().stream().map(this::convert).toList(),
              collection.materializer(),
              runtimeType(collection.runtimeType()),
              types.convert(collection.type()));
      case BoundExpression.LocalRead local ->
          new CoreExpression.LocalRead(
              node, locals.index(local.local()), types.convert(local.type()));
      case BoundExpression.FieldRead field ->
          new CoreExpression.FieldRead(
              node,
              convert(field.receiver()),
              field(field.field().value(), field.ordinal()),
              field.nullSafe(),
              types.convert(field.type()));
      case BoundExpression.EnumConstruct construct ->
          new CoreExpression.EnumConstruct(
              node,
              reference(node, construct.enumId().value()),
              construct.variantName(),
              runtimeType(construct.runtimeType()),
              arguments(construct.arguments()),
              types.convert(construct.type()));
      case BoundExpression.Unary unary ->
          new CoreExpression.Unary(
              node,
              CoreUnaryOperator.valueOf(unary.operator().name()),
              convert(unary.operand()),
              types.convert(unary.type()));
      case BoundExpression.Binary binary ->
          new CoreExpression.Binary(
              node,
              convert(binary.left()),
              CoreBinaryOperator.valueOf(binary.operator().name()),
              convert(binary.right()),
              types.convert(binary.type()));
      case BoundExpression.Switch switched ->
          new CoreExpression.Switch(
              node,
              convert(switched.value()),
              switched.cases().stream()
                  .map(
                      switchCase ->
                          new CoreSwitchCase(
                              convert(switchCase.pattern()), convert(switchCase.body())))
                  .toList(),
              types.convert(switched.type()));
      case BoundExpression.Index index ->
          new CoreExpression.Index(
              node,
              convert(index.receiver()),
              convert(index.index()),
              index.readIntrinsic(),
              index.writeIntrinsic(),
              types.convert(index.type()));
      case BoundExpression.CopyObject copied ->
          new CoreExpression.CopyObject(
              node, convert(copied.receiver()), copied.nullSafe(), types.convert(copied.type()));
      case BoundClosure closure ->
          new CoreExpression.Closure(
              node,
              reference(node, closure.target().value()),
              closure.receiver().map(this::convert),
              closure.captures().stream().map(this::convert).toList(),
              closure.reifiedArguments().stream().map(this::runtimeType).toList(),
              closure.receiverTypeArguments().stream().map(this::runtimeType).toList(),
              closure.virtual(),
              types.convert(closure.type()));
      case BoundInvoke invoke ->
          new CoreExpression.Invoke(
              node,
              convert(invoke.callee()),
              arguments(invoke.arguments()),
              types.convert(invoke.type()));
      case BoundExpression.InterfaceCall call ->
          new CoreExpression.InterfaceCall(
              node,
              reference(node, call.requirement().value()),
              convert(call.receiver()),
              arguments(call.arguments()),
              call.reifiedArguments().stream().map(this::runtimeType).toList(),
              call.nullSafe(),
              types.convert(call.type()));
      case BoundCall call ->
          new CoreExpression.Call(
              node,
              reference(node, call.target().value()),
              call.receiver().map(this::convert),
              arguments(call.arguments()),
              call.reifiedArguments().stream().map(this::runtimeType).toList(),
              call.receiverTypeArguments().stream().map(this::runtimeType).toList(),
              call.virtual(),
              call.nullSafe(),
              types.convert(call.type()));
      case BoundConstruct construct ->
          new CoreExpression.Construct(
              node,
              reference(node, construct.target().value()),
              new PendingDefinitionReference(
                  declarationIndex.applyAsInt(construct.initializer().value())),
              runtimeType(construct.runtimeType()),
              arguments(construct.arguments()),
              types.convert(construct.type()));
      case BoundIntrinsic intrinsic ->
          new CoreExpression.Intrinsic(
              node,
              intrinsic.intrinsic(),
              intrinsic.receiver().map(this::convert),
              arguments(intrinsic.arguments()),
              intrinsic.runtimeType().map(this::runtimeType),
              intrinsic.nullSafe(),
              types.convert(intrinsic.type()));
    };
  }

  private CoreIteration convert(BoundIteration iteration) {
    return switch (iteration) {
      case BoundIteration.Builtin builtin -> new CoreIteration.Builtin(builtin.intrinsic());
      case BoundIteration.Interface protocol ->
          new CoreIteration.Interface(
              new PendingDefinitionReference(
                  declarationIndex.applyAsInt(protocol.iteratorRequirement().value())),
              new PendingDefinitionReference(
                  declarationIndex.applyAsInt(protocol.hasNextRequirement().value())),
              new PendingDefinitionReference(
                  declarationIndex.applyAsInt(protocol.nextRequirement().value())));
    };
  }

  private CorePattern convert(BoundPattern pattern) {
    return switch (pattern) {
      case BoundPattern.Variant variant ->
          new CorePattern.Variant(
              variant.variantKey(), variant.arguments().stream().map(this::convert).toList());
      case BoundPattern.Binding binding ->
          new CorePattern.Binding(locals.index(binding.local()), types.convert(binding.type()));
      case BoundPattern.Wildcard ignored -> CorePattern.Wildcard.INSTANCE;
      case BoundPattern.Literal literal ->
          new CorePattern.Literal(literal.value(), types.convert(literal.type()));
      case BoundPattern.Null ignored -> CorePattern.Null.INSTANCE;
    };
  }

  private List<CoreArgument> arguments(List<BoundArgument> arguments) {
    return arguments.stream()
        .map(argument -> new CoreArgument(convert(argument.value()), argument.parameterIndex()))
        .toList();
  }

  private CoreRuntimeType runtimeType(BoundRuntimeType type) {
    return new CoreRuntimeType(
        types.convert(type.type()),
        type.reifiedArguments().stream()
            .map(
                capture ->
                    new CoreTypeCapture(
                        types.parameterIndex(capture.typeParameterIdentity()),
                        locals.index(capture.source())))
            .toList());
  }

  private CoreFieldReference field(String field, int ordinal) {
    return new CoreFieldReference(
        new PendingDefinitionReference(fieldOwnerIndex.applyAsInt(field)), ordinal);
  }

  private PendingDefinitionReference reference(int nodeIndex, String declaration) {
    int target = declarationIndex.applyAsInt(declaration);
    if (referenceTargets.putIfAbsent(nodeIndex, target) != null) {
      throw new IllegalStateException("bound reference node is duplicated");
    }
    return new PendingDefinitionReference(target);
  }

  static final class Result {
    private final CoreBlock body;
    private final List<CoreLocal> locals;
    private final Map<BoundLocalId, Integer> localIndices;
    private final Map<Integer, SourceSpan> nodeSpans;
    private final Map<Integer, Integer> referenceTargets;

    private Result(
        CoreBlock body,
        List<CoreLocal> locals,
        Map<BoundLocalId, Integer> localIndices,
        Map<Integer, SourceSpan> nodeSpans,
        Map<Integer, Integer> referenceTargets) {
      this.body = Objects.requireNonNull(body, "body");
      this.locals = List.copyOf(locals);
      this.localIndices = Map.copyOf(localIndices);
      this.nodeSpans = Map.copyOf(nodeSpans);
      this.referenceTargets = Map.copyOf(referenceTargets);
    }

    CoreBlock body() {
      return body;
    }

    List<CoreLocal> locals() {
      return locals;
    }

    Map<Integer, SourceSpan> nodeSpans() {
      return nodeSpans;
    }

    Map<Integer, Integer> referenceTargets() {
      return referenceTargets;
    }

    int localIndex(BoundLocalId id) {
      Integer index = localIndices.get(id);
      if (index == null) throw new IllegalStateException("core local is absent: " + id);
      return index;
    }
  }

  private static final class NodeTable {
    private final Map<Integer, SourceSpan> spans = new LinkedHashMap<>();
    private int next = 1;

    private int add(SourceSpan span) {
      int index = next++;
      spans.put(index, span);
      return index;
    }

    private void add(int index, SourceSpan span) {
      spans.put(index, span);
    }

    private Map<Integer, SourceSpan> spans() {
      return Map.copyOf(spans);
    }
  }

  private static final class LocalTable {
    private final Map<BoundLocalId, Integer> indices = new LinkedHashMap<>();
    private final List<CoreLocal> values = new ArrayList<>();

    private void add(BoundLocalId id, CoreType type, CoreLocal.Kind kind) {
      if (indices.containsKey(id)) return;
      int index = values.size();
      indices.put(id, index);
      values.add(new CoreLocal(index, type, kind));
    }

    private int index(BoundLocalId id) {
      Integer index = indices.get(id);
      if (index == null) throw new IllegalStateException("core local is absent: " + id);
      return index;
    }

    private List<CoreLocal> values() {
      return List.copyOf(values);
    }

    private Map<BoundLocalId, Integer> indices() {
      return Map.copyOf(indices);
    }
  }
}
