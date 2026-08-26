package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreArgument;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreBlock;
import dev.w0fv1.norm.core.CoreConformance;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionLink;
import dev.w0fv1.norm.core.CoreDefinitionOccurrence;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreIteration;
import dev.w0fv1.norm.core.CoreLocal;
import dev.w0fv1.norm.core.CoreMethodDispatch;
import dev.w0fv1.norm.core.CorePattern;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreRuntimeType;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreWitness;
import dev.w0fv1.norm.core.CoreWitnessTarget;
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
  private final Map<DefinitionOccurrenceId, RuntimeValues.AggregateInfo> aggregateInfo =
      new HashMap<>();
  private final Map<DefinitionOccurrenceId, CoreDefinition.Aggregate> aggregates =
      new LinkedHashMap<>();
  private final Map<DefinitionOccurrenceId, FunctionPlan> callables = new LinkedHashMap<>();
  private final Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>>
      builtinDispatch = new HashMap<>();
  private final Map<DocumentId, Source> sources = new HashMap<>();
  private CoreArtifact artifact;
  private CoreProgram program;

  Lowerer(Language language) {
    this.language = language;
  }

  ExecutableProgram lower(CoreArtifact checkedArtifact) {
    artifact = Objects.requireNonNull(checkedArtifact, "checkedArtifact");
    program = artifact.program();
    indexDefinitions();
    createCallTargets();
    indexDispatch();
    lowerBodies();
    DefinitionOccurrenceId entry = artifact.entryPoint();
    FunctionPlan entryPlan = callables.get(entry);
    if (entryPlan == null) throw new IllegalStateException("entry callable is absent");
    return new ExecutableProgram(entryPlan.target);
  }

  private void indexDefinitions() {
    for (CoreDefinitionOccurrence occurrence : artifact.authoring().occurrences()) {
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      switch (definition) {
        case CoreDefinition.Aggregate declaration -> aggregates.put(occurrence.id(), declaration);
        case CoreDefinition.Callable declaration ->
            callables.put(occurrence.id(), plan(occurrence.id(), declaration));
        case CoreDefinition.Enum ignored -> {}
        case CoreDefinition.Interface ignored -> {}
        case CoreDefinition.InterfaceMethod ignored -> {}
        case CoreDefinition.BuiltinConformance ignored -> {}
      }
    }
  }

  private FunctionPlan plan(DefinitionOccurrenceId id, CoreDefinition.Callable declaration) {
    FunctionPlan plan = new FunctionPlan(id, declaration);
    for (CoreLocal local : declaration.locals()) plan.allocate(local);
    if (declaration.hasReceiver()) plan.arguments.add(plan.binding(0));
    declaration.captureLocals().forEach(local -> plan.arguments.add(plan.binding(local)));
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
              artifact.displayName(plan.id),
              plan.descriptor,
              plan.arguments.toArray(FrameBinding[]::new),
              section(plan.id, 0));
      plan.target = plan.root.getCallTarget();
    }
  }

  private void indexDispatch() {
    Map<DefinitionId, FunctionPlan> callableByDefinition = new HashMap<>();
    callables
        .values()
        .forEach(plan -> callableByDefinition.putIfAbsent(plan.id.representative(), plan));
    for (Map.Entry<DefinitionOccurrenceId, CoreDefinition.Aggregate> entry :
        aggregates.entrySet()) {
      DefinitionOccurrenceId occurrence = entry.getKey();
      Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch = new HashMap<>();
      Map<DefinitionId, RuntimeValues.DispatchTarget> methodTargets = new HashMap<>();
      for (CoreMethodDispatch method : entry.getValue().dispatch()) {
        DefinitionId slot = resolve(occurrence.representative(), method.slot());
        DefinitionId implementation = resolve(occurrence.representative(), method.implementation());
        FunctionPlan plan = callableByDefinition.get(implementation);
        if (plan == null) throw new IllegalStateException("method dispatch target is absent");
        CoreType receiverType =
            CoreTypes.absolute(method.receiverType(), occurrence.representative(), program);
        List<CoreType> arguments = ((CoreType.Declared) receiverType).arguments();
        RuntimeValues.DispatchTarget target =
            new RuntimeValues.DispatchTarget.Callable(plan.target, arguments);
        dispatch.put(slot, target);
        methodTargets.put(slot, target);
      }
      List<CoreType> rootArguments =
          java.util.stream.IntStream.range(0, entry.getValue().typeParameters().size())
              .mapToObj(
                  index ->
                      (CoreType)
                          new CoreType.Parameter(
                              index, dev.w0fv1.norm.core.CoreNullability.NON_NULL))
              .toList();
      for (RuntimeConformance inherited :
          aggregateConformances(occurrence.representative(), entry.getValue(), rootArguments)) {
        CoreConformance conformance = inherited.conformance();
        for (CoreWitness witness : conformance.witnesses()) {
          DefinitionId requirement = resolve(inherited.owner(), witness.requirement());
          DefinitionId implementation =
              witness.implementation() instanceof CoreWitnessTarget.Callable callable
                  ? resolve(inherited.owner(), callable.definition())
                  : null;
          RuntimeValues.DispatchTarget target = methodTargets.get(implementation);
          if (target == null) {
            target =
                lowerWitnessTarget(
                    inherited.owner(), witness.implementation(), callableByDefinition);
          }
          if (target instanceof RuntimeValues.DispatchTarget.Callable callable
              && isDefaultWitness(inherited.owner(), witness.implementation())) {
            CoreType interfaceType = inherited.interfaceType();
            target =
                new RuntimeValues.DispatchTarget.Callable(
                    callable.target(), ((CoreType.Declared) interfaceType).arguments());
          }
          if (dispatch.put(requirement, target) != null) {
            throw new IllegalStateException("verified aggregate dispatch is duplicated");
          }
        }
      }
      aggregateInfo.put(
          occurrence,
          new RuntimeValues.AggregateInfo(
              occurrence.representative(),
              artifact.displayName(occurrence),
              entry.getValue().fieldCount(),
              dispatch));
    }
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.BuiltinConformance conformance)) continue;
      CoreType concrete =
          CoreTypes.absolute(conformance.concreteBuiltinType(), record.id(), program);
      CoreType.Declared declared = (CoreType.Declared) concrete;
      BuiltinTypeId builtin = ((CoreTypeConstructor.Builtin) declared.constructor()).id();
      Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch =
          builtinDispatch.computeIfAbsent(builtin, ignored -> new HashMap<>());
      for (CoreWitness witness : conformance.witnesses()) {
        DefinitionId requirement = resolve(record.id(), witness.requirement());
        RuntimeValues.DispatchTarget target =
            lowerWitnessTarget(record.id(), witness.implementation(), callableByDefinition);
        if (target instanceof RuntimeValues.DispatchTarget.Callable callable) {
          CoreType interfaceType =
              CoreTypes.absolute(conformance.interfaceType(), record.id(), program);
          target =
              new RuntimeValues.DispatchTarget.Callable(
                  callable.target(), ((CoreType.Declared) interfaceType).arguments());
        }
        if (dispatch.putIfAbsent(requirement, target) != null) {
          throw new IllegalStateException("verified builtin dispatch is duplicated");
        }
      }
    }
  }

  private RuntimeValues.DispatchTarget lowerWitnessTarget(
      DefinitionId owner,
      CoreWitnessTarget target,
      Map<DefinitionId, FunctionPlan> callableByDefinition) {
    return switch (target) {
      case CoreWitnessTarget.Callable callable -> {
        FunctionPlan plan = callableByDefinition.get(resolve(owner, callable.definition()));
        if (plan == null) throw new IllegalStateException("witness callable target is absent");
        yield new RuntimeValues.DispatchTarget.Callable(plan.target);
      }
      case CoreWitnessTarget.Intrinsic intrinsic ->
          new RuntimeValues.DispatchTarget.Intrinsic(intrinsic.intrinsic());
    };
  }

  private List<RuntimeConformance> aggregateConformances(
      DefinitionId owner, CoreDefinition.Aggregate aggregate, List<CoreType> ownerArguments) {
    List<RuntimeConformance> result = new ArrayList<>();
    for (CoreConformance conformance : aggregate.conformances()) {
      CoreType interfaceType =
          CoreTypes.absolute(conformance.interfaceType(), owner, program)
              .substitute(ownerArguments::get);
      result.add(new RuntimeConformance(owner, conformance, interfaceType));
    }
    if (aggregate.parentType().isPresent()) {
      CoreType parent =
          CoreTypes.absolute(aggregate.parentType().orElseThrow(), owner, program)
              .substitute(ownerArguments::get);
      CoreType.Declared declared = (CoreType.Declared) parent;
      DefinitionId parentId =
          ((DefinitionReference.External)
                  ((CoreTypeConstructor.User) declared.constructor()).definition())
              .definition();
      CoreDefinition parentDefinition = program.definition(parentId).orElseThrow();
      result.addAll(
          aggregateConformances(
              parentId, (CoreDefinition.Aggregate) parentDefinition, declared.arguments()));
    }
    return List.copyOf(result);
  }

  private record RuntimeConformance(
      DefinitionId owner, CoreConformance conformance, CoreType interfaceType) {}

  private boolean isDefaultWitness(DefinitionId owner, CoreWitnessTarget target) {
    if (!(target instanceof CoreWitnessTarget.Callable callable)) return false;
    DefinitionId implementationId = resolve(owner, callable.definition());
    CoreDefinition implementation = program.definition(implementationId).orElseThrow();
    if (!(implementation instanceof CoreDefinition.Callable method)
        || method.receiverType().isEmpty()) {
      return false;
    }
    CoreType receiver =
        CoreTypes.absolute(method.receiverType().orElseThrow(), implementationId, program);
    return receiver instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user
        && user.definition() instanceof dev.w0fv1.norm.core.DefinitionReference.External external
        && !external.definition().equals(owner);
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
          case CoreStatement.ReferenceAssignment assignment ->
              new StatementNodes.WriteReference(
                  lowerExpression(assignment.reference(), plan),
                  lowerExpression(assignment.value(), plan));
          case CoreStatement.ExpressionStatement expression ->
              new StatementNodes.ExpressionStatement(
                  lowerExpression(expression.expression(), plan));
          case CoreStatement.IfStatement conditional ->
              new StatementNodes.If(
                  lowerExpression(conditional.condition(), plan),
                  lowerBlock(conditional.thenBlock(), plan),
                  lowerBlock(conditional.elseBlock(), plan));
          case CoreStatement.ForStatement loop -> {
            StatementNodes.IteratorFactoryNode factory;
            StatementNodes.IteratorCursorNode cursor;
            switch (loop.iteration()) {
              case CoreIteration.Builtin builtin -> {
                factory = new StatementNodes.BuiltinIteratorFactory(builtin.intrinsic());
                cursor = new StatementNodes.BuiltinIteratorCursor();
              }
              case CoreIteration.Interface protocol -> {
                factory =
                    new StatementNodes.InterfaceIteratorFactory(
                        resolve(plan.id.representative(), protocol.iteratorRequirement()),
                        builtinDispatch);
                cursor =
                    new StatementNodes.InterfaceIteratorCursor(
                        resolve(plan.id.representative(), protocol.hasNextRequirement()),
                        resolve(plan.id.representative(), protocol.nextRequirement()),
                        builtinDispatch);
              }
            }
            yield new StatementNodes.For(
                plan.binding(loop.iteratorLocal()),
                plan.binding(loop.variableLocal()),
                loop.indexLocal().isPresent()
                    ? java.util.Optional.of(plan.binding(loop.indexLocal().orElseThrow()))
                    : java.util.Optional.empty(),
                lowerExpression(loop.iterable(), plan),
                lowerBlock(loop.body(), plan),
                factory,
                cursor);
          }
          case CoreStatement.ConditionalForStatement loop ->
              new StatementNodes.ConditionalFor(
                  lowerExpression(loop.condition(), plan), lowerBlock(loop.body(), plan));
          case CoreStatement.ReturnStatement returned ->
              new StatementNodes.Return(
                  returned.value().map(value -> lowerExpression(value, plan)).orElse(null));
          case CoreStatement.YieldStatement yielded ->
              new StatementNodes.Yield(lowerExpression(yielded.value(), plan));
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
          case CoreExpression.CollectionLiteral collection ->
              new ExpressionNodes.CollectionLiteral(
                  collection.materializer(),
                  collection.elements().stream()
                      .map(value -> lowerExpression(value, plan))
                      .toArray(ExpressionNode[]::new),
                  lowerRuntimeType(collection.runtimeType(), plan));
          case CoreExpression.LocalRead local ->
              new ExpressionNodes.ReadLocal(plan.binding(local.localIndex()));
          case CoreExpression.FieldRead field ->
              new ExpressionNodes.ReadField(
                  lowerExpression(field.receiver(), plan),
                  field.field().ordinal(),
                  field.nullSafe());
          case CoreExpression.AddressLocal address ->
              new ExpressionNodes.AddressLocal(plan.binding(address.localIndex()));
          case CoreExpression.AddressField address ->
              new ExpressionNodes.AddressField(
                  lowerExpression(address.receiver(), plan), address.field().ordinal());
          case CoreExpression.Dereference dereference ->
              new ExpressionNodes.Dereference(lowerExpression(dereference.reference(), plan));
          case CoreExpression.EnumConstruct construct -> lowerEnumConstruct(construct, plan);
          case CoreExpression.Unary unary ->
              unary.operator() == dev.w0fv1.norm.core.CoreUnaryOperator.NOT
                  ? new ExpressionNodes.Not(lowerExpression(unary.operand(), plan))
                  : new ExpressionNodes.Negate(lowerExpression(unary.operand(), plan));
          case CoreExpression.Binary binary -> lowerBinary(binary, plan);
          case CoreExpression.Switch switched -> lowerSwitch(switched, plan);
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
          case CoreExpression.Closure closure -> lowerClosure(closure, plan);
          case CoreExpression.Invoke invoke ->
              new ExpressionNodes.Invoke(
                  lowerExpression(invoke.callee(), plan),
                  lowerArguments(invoke.arguments(), plan),
                  parameterIndices(invoke.arguments()));
          case CoreExpression.Call call -> lowerCall(call, plan);
          case CoreExpression.InterfaceCall call -> lowerInterfaceCall(call, plan);
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

  private ExpressionNode lowerSwitch(CoreExpression.Switch switched, FunctionPlan plan) {
    return new ExpressionNodes.Switch(
        lowerExpression(switched.value(), plan),
        switched.cases().stream()
            .map(value -> lowerPattern(value.pattern(), switched.value().type(), plan))
            .toArray(PatternNode[]::new),
        switched.cases().stream()
            .map(value -> lowerBlock(value.body(), plan))
            .toArray(StatementNode[]::new));
  }

  private PatternNode lowerPattern(CorePattern pattern, CoreType expected, FunctionPlan plan) {
    return switch (pattern) {
      case CorePattern.Variant variant -> {
        List<CoreType> payloadTypes = variantPayloadTypes(expected, variant.variantKey(), plan);
        PatternNode[] arguments = new PatternNode[variant.arguments().size()];
        for (int index = 0; index < arguments.length; index++) {
          arguments[index] =
              lowerPattern(variant.arguments().get(index), payloadTypes.get(index), plan);
        }
        yield new PatternNodes.Variant(variant.variantKey(), arguments);
      }
      case CorePattern.Binding binding ->
          new PatternNodes.Binding(plan.binding(binding.localIndex()));
      case CorePattern.Wildcard ignored -> new PatternNodes.Wildcard();
      case CorePattern.Literal literal -> {
        CoreType actual =
            CoreTypes.absolute(nonNullable(expected), plan.id.representative(), program);
        Object value =
            actual.equals(CoreType.CODE_POINT)
                ? new RuntimeValues.CodePointValue(((Number) literal.value()).intValue())
                : literal.value();
        yield new PatternNodes.Literal(value);
      }
      case CorePattern.Null ignored -> new PatternNodes.Null();
    };
  }

  private List<CoreType> variantPayloadTypes(
      CoreType expected, String variantKey, FunctionPlan plan) {
    CoreType absolute =
        CoreTypes.absolute(nonNullable(expected), plan.id.representative(), program);
    if (!(absolute instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference reference)) {
      throw new IllegalStateException("verified variant pattern type is not an enum");
    }
    DefinitionId definition = program.resolve(plan.id.representative(), reference);
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Enum enumDeclaration)) {
      throw new IllegalStateException("verified variant pattern type is not an enum");
    }
    CoreEnumVariant variant =
        enumDeclaration.variants().stream()
            .filter(value -> value.key().equals(variantKey))
            .findFirst()
            .orElseThrow();
    return variant.fields().stream()
        .map(
            field ->
                CoreTypes.absolute(field.type(), definition, program)
                    .substitute(declared.arguments()::get))
        .toList();
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          new CoreType.Declared(
              declared.constructor(),
              declared.arguments(),
              declared.category(),
              dev.w0fv1.norm.core.CoreNullability.NON_NULL);
      case CoreType.Function function ->
          new CoreType.Function(
              function.returnType(),
              function.parameterTypes(),
              dev.w0fv1.norm.core.CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          new CoreType.Parameter(parameter.index(), dev.w0fv1.norm.core.CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }

  private ExpressionNode lowerEnumConstruct(
      CoreExpression.EnumConstruct construct, FunctionPlan plan) {
    DefinitionOccurrenceId target = resolve(plan, construct.nodeIndex(), construct.target());
    return new ExpressionNodes.EnumConstruct(
        target.representative(),
        artifact.displayName(target),
        construct.variantKey(),
        lowerRuntimeType(construct.runtimeType(), plan),
        lowerArguments(construct.arguments(), plan),
        parameterIndices(construct.arguments()));
  }

  private ExpressionNode lowerConstruct(CoreExpression.Construct construct, FunctionPlan plan) {
    DefinitionOccurrenceId target = resolve(plan, construct.nodeIndex(), construct.target());
    RuntimeValues.AggregateInfo info = aggregateInfo.get(target);
    if (info == null) throw new IllegalStateException("core aggregate target is absent: " + target);
    DefinitionId initializerId = resolve(plan.id.representative(), construct.initializer());
    com.oracle.truffle.api.CallTarget initializer =
        callables.values().stream()
            .filter(value -> value.id.representative().equals(initializerId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("constructor target is absent"))
            .target;
    return new ExpressionNodes.Construct(
        info,
        initializer,
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
      if (call.virtual()) {
        return new ExpressionNodes.DispatchedCall(
            targetId.representative(),
            lowerExpression(call.receiver().orElseThrow(), plan),
            lowerArguments(call.arguments(), plan),
            parameterIndices(call.arguments()),
            call.reifiedArguments().stream()
                .map(value -> lowerRuntimeType(value, plan))
                .toArray(ExpressionNode[]::new),
            call.nullSafe(),
            builtinDispatch);
      }
      return new ExpressionNodes.MethodCall(
          target.target,
          lowerExpression(call.receiver().orElseThrow(), plan),
          lowerArguments(call.arguments(), plan),
          parameterIndices(call.arguments()),
          call.reifiedArguments().stream()
              .map(value -> lowerRuntimeType(value, plan))
              .toArray(ExpressionNode[]::new),
          call.receiverTypeArguments().stream()
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

  private ExpressionNode lowerClosure(CoreExpression.Closure closure, FunctionPlan plan) {
    DefinitionOccurrenceId targetId = resolve(plan, closure.nodeIndex(), closure.target());
    FunctionPlan target = callables.get(targetId);
    if (target == null)
      throw new IllegalStateException("core closure target is absent: " + targetId);
    return new ExpressionNodes.Closure(
        target.target,
        closure.virtual() ? targetId.representative() : null,
        closure.receiver().map(value -> lowerExpression(value, plan)).orElse(null),
        closure.captures().stream()
            .map(value -> lowerExpression(value, plan))
            .toArray(ExpressionNode[]::new),
        closure.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new),
        closure.receiverTypeArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new));
  }

  private ExpressionNode lowerInterfaceCall(CoreExpression.InterfaceCall call, FunctionPlan plan) {
    DefinitionOccurrenceId requirement = resolve(plan, call.nodeIndex(), call.requirement());
    return new ExpressionNodes.DispatchedCall(
        requirement.representative(),
        lowerExpression(call.receiver(), plan),
        lowerArguments(call.arguments(), plan),
        parameterIndices(call.arguments()),
        call.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new),
        call.nullSafe(),
        builtinDispatch);
  }

  private DefinitionOccurrenceId resolve(
      FunctionPlan plan, int nodeIndex, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalStateException("pending definition reached the backend");
    }
    DefinitionOccurrenceId target = artifact.authoring().target(plan.id, nodeIndex);
    DefinitionId resolved = program.resolve(plan.id.representative(), reference);
    if (!artifact
        .authoring()
        .occurrence(target)
        .orElseThrow()
        .representedDefinitions()
        .contains(resolved)) {
      throw new IllegalStateException("authoring target does not represent the core reference");
    }
    return target;
  }

  private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalStateException("pending definition reached the backend");
    }
    return program.resolve(owner, reference);
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
    return artifact.authoring().span(occurrence, nodeIndex).map(this::section).orElse(null);
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
        case "std.core.Integer" -> FrameSlotKind.Int;
        case "std.core.Long" -> FrameSlotKind.Long;
        case "std.core.Float" -> FrameSlotKind.Float;
        case "std.core.Double" -> FrameSlotKind.Double;
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
