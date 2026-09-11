package dev.w0fv1.norm.core;

import dev.w0fv1.norm.core.CoreVerificationTypes.CallableSignature;
import dev.w0fv1.norm.core.CoreVerificationTypes.InterfaceInstance;
import dev.w0fv1.norm.pattern.PatternCoverage;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CoreCallableVerifier {
  private final CoreProgram program;
  private final CoreVerificationTypes validationTypes;
  private final CoreIntrinsicVerifier intrinsicVerifier;
  private final CoreFunctionInterceptorProtocol functionInterceptor;
  private final CoreParameterInterceptorProtocol parameterInterceptor;
  private final Map<String, CoreType> patternTypes = new java.util.HashMap<>();
  private final Deque<Control> controls = new ArrayDeque<>();
  private final CoreReferenceFlow referenceFlow = new CoreReferenceFlow();

  CoreCallableVerifier(
      CoreProgram program,
      CoreVerificationTypes validationTypes,
      CoreIntrinsicVerifier intrinsicVerifier,
      CoreFunctionInterceptorProtocol functionInterceptor,
      CoreParameterInterceptorProtocol parameterInterceptor) {
    this.program = program;
    this.validationTypes = validationTypes;
    this.intrinsicVerifier = intrinsicVerifier;
    this.functionInterceptor = functionInterceptor;
    this.parameterInterceptor = parameterInterceptor;
  }

  void verifyCallable(DefinitionId id, CoreDefinition.Callable callable) {
    int parameterCount = callable.reifiedTypeLocals().size();
    validationTypes.verifyTypeParameters(id, callable.typeParameters(), parameterCount);
    callable
        .receiverType()
        .ifPresent(type -> validationTypes.verifyStoredType(id, type, parameterCount));
    callable
        .captureTypes()
        .forEach(type -> validationTypes.verifyStoredType(id, type, parameterCount));
    callable
        .parameterTypes()
        .forEach(type -> validationTypes.verifyParameterType(id, type, parameterCount));
    if (callable.parameters().stream().map(CoreCallableParameter::name).distinct().count()
        != callable.parameters().size()) {
      throw new IllegalArgumentException("callable parameter names must be unique");
    }
    Set<DefinitionId> interceptorTypes = new HashSet<>();
    for (CoreInterceptor interceptor : callable.interceptors()) {
      if (!interceptorTypes.add(validationTypes.resolve(id, interceptor.annotation()))) {
        throw new IllegalArgumentException("interceptor annotation must be unique per callable");
      }
    }
    callable
        .interceptors()
        .forEach(
            interceptor ->
                CoreAnnotationVerifier.verifyInterceptor(
                    program, id, interceptor, functionInterceptor));
    for (CoreCallableParameter parameter : callable.parameters()) {
      Set<DefinitionId> parameterInterceptorTypes = new HashSet<>();
      for (CoreInterceptor interceptor : parameter.interceptors()) {
        if (!parameterInterceptorTypes.add(validationTypes.resolve(id, interceptor.annotation()))) {
          throw new IllegalArgumentException("interceptor annotation must be unique per parameter");
        }
        CoreAnnotationVerifier.verifyParameterInterceptor(
            program, id, parameter, interceptor, parameterInterceptor);
      }
    }
    validationTypes.verifyReturnType(id, callable.returnType(), parameterCount);
    callable.locals().forEach(local -> validationTypes.verifyLocalType(id, local, parameterCount));
    if (!controls.isEmpty()) throw new IllegalStateException("core control stack is not empty");
    List<Integer> entryLocals = new ArrayList<>();
    if (callable.receiverType().isPresent()) entryLocals.add(0);
    entryLocals.addAll(callable.captureLocals());
    entryLocals.addAll(callable.parameterLocals());
    entryLocals.addAll(callable.reifiedTypeLocals());
    verifyBlock(id, callable, callable.body(), entryLocals, true);
  }

  private void verifyBlock(DefinitionId owner, CoreDefinition.Callable callable, CoreBlock block) {
    verifyBlock(owner, callable, block, List.of(), false);
  }

  private void verifyBlock(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CoreBlock block,
      List<Integer> implicitLocals,
      boolean externalReferences) {
    referenceFlow.push();
    for (int localIndex : implicitLocals) {
      CoreLocal local = CoreVerificationTypes.local(callable, localIndex);
      referenceFlow.declare(localIndex);
      if (local.type() instanceof CoreType.Reference) {
        referenceFlow.update(
            localIndex,
            externalReferences ? LexicalLifetime.longLived() : LexicalLifetime.unusable());
      }
    }
    for (CoreStatement statement : block.statements()) {
      switch (statement) {
        case CoreStatement.LocalDeclaration local -> {
          CoreLocal target = CoreVerificationTypes.local(callable, local.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("local declaration must bind a variable local");
          }
          verifyExpression(owner, callable, local.initializer());
          validationTypes.requireAssignable(
              owner, target.type(), owner, local.initializer().type(), "local initializer");
          referenceFlow.declare(local.localIndex());
          if (target.type() instanceof CoreType.Reference) {
            updateReferenceLifetime(local.localIndex(), local.initializer());
          }
        }
        case CoreStatement.LocalAssignment assignment -> {
          CoreLocal target = CoreVerificationTypes.local(callable, assignment.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE
              && target.kind() != CoreLocal.Kind.PARAMETER) {
            throw new IllegalArgumentException("local assignment target is not mutable storage");
          }
          referenceFlow.requireDeclared(assignment.localIndex());
          verifyExpression(owner, callable, assignment.value());
          validationTypes.requireAssignable(
              owner, target.type(), owner, assignment.value().type(), "local assignment");
          if (target.type() instanceof CoreType.Reference) {
            updateReferenceLifetime(assignment.localIndex(), assignment.value());
          }
        }
        case CoreStatement.FieldAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          verifyExpression(owner, callable, assignment.value());
          validationTypes.requireNonNullableReceiver(
              owner, assignment.receiver().type(), "field assignment");
          CoreType fieldType =
              validationTypes.instantiatedFieldType(
                  owner, assignment.receiver().type(), assignment.field());
          validationTypes.requireAssignable(
              owner, fieldType, owner, assignment.value().type(), "field assignment");
        }
        case CoreStatement.IntrinsicAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          assignment.index().ifPresent(value -> verifyExpression(owner, callable, value));
          verifyExpression(owner, callable, assignment.value());
          intrinsicVerifier.verifyIntrinsicAssignment(owner, assignment);
        }
        case CoreStatement.ReferenceAssignment assignment -> {
          verifyExpression(owner, callable, assignment.reference());
          verifyExpression(owner, callable, assignment.value());
          CoreType target = referenceTarget(owner, assignment.reference().type(), "assignment");
          validationTypes.requireAssignable(
              owner, target, owner, assignment.value().type(), "reference assignment");
        }
        case CoreStatement.ExpressionStatement expression ->
            verifyExpression(owner, callable, expression.expression());
        case CoreStatement.IfStatement conditional -> {
          verifyExpression(owner, callable, conditional.condition());
          validationTypes.requireSameType(
              owner, CoreType.BOOLEAN, owner, conditional.condition().type(), "if condition");
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          verifyBlock(owner, callable, conditional.thenBlock());
          CoreReferenceFlow.State thenFlow = referenceFlow.snapshot();
          referenceFlow.replace(incoming);
          verifyBlock(owner, callable, conditional.elseBlock());
          CoreReferenceFlow.State elseFlow = referenceFlow.snapshot();
          referenceFlow.replace(CoreReferenceFlow.merge(incoming, thenFlow, elseFlow));
        }
        case CoreStatement.ConditionalForStatement loop -> {
          verifyExpression(owner, callable, loop.condition());
          validationTypes.requireSameType(
              owner, CoreType.BOOLEAN, owner, loop.condition().type(), "loop condition");
          controls.addFirst(Control.loop());
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          verifyBlock(owner, callable, loop.body());
          controls.removeFirst();
          referenceFlow.replace(
              CoreReferenceFlow.merge(incoming, incoming, referenceFlow.snapshot()));
        }
        case CoreStatement.ForStatement loop -> {
          if (CoreVerificationTypes.local(callable, loop.iteratorLocal()).kind()
                  != CoreLocal.Kind.ITERATOR
              || CoreVerificationTypes.local(callable, loop.variableLocal()).kind()
                  != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("for loop local ABI is invalid");
          }
          loop.indexLocal()
              .ifPresent(
                  index -> {
                    if (index == loop.iteratorLocal() || index == loop.variableLocal()) {
                      throw new IllegalArgumentException("for loop index local ABI is invalid");
                    }
                    CoreLocal local = CoreVerificationTypes.local(callable, index);
                    if (local.kind() != CoreLocal.Kind.VARIABLE
                        || !validationTypes
                            .absolute(owner, local.type())
                            .equals(CoreType.INTEGER)) {
                      throw new IllegalArgumentException("for loop index local ABI is invalid");
                    }
                  });
          verifyExpression(owner, callable, loop.iterable());
          intrinsicVerifier.verifyIteration(
              owner, callable, loop.variableLocal(), loop.iterable(), loop.iteration());
          controls.addFirst(Control.loop());
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          List<Integer> loopLocals = new ArrayList<>();
          loopLocals.add(loop.variableLocal());
          loop.indexLocal().ifPresent(loopLocals::add);
          verifyBlock(owner, callable, loop.body(), loopLocals, false);
          controls.removeFirst();
          referenceFlow.replace(
              CoreReferenceFlow.merge(incoming, incoming, referenceFlow.snapshot()));
        }
        case CoreStatement.TryStatement tried -> {
          if (tried.catches().isEmpty() && tried.finallyBlock().isEmpty()) {
            throw new IllegalArgumentException("core try requires catch or finally");
          }
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          List<CoreReferenceFlow.State> completing = new ArrayList<>();
          verifyBlock(owner, callable, tried.body());
          if (!definitelyExits(tried.body())) completing.add(referenceFlow.snapshot());
          List<CoreType> preceding = new ArrayList<>();
          for (CoreCatchClause clause : tried.catches()) {
            validationTypes.requireExceptionType(owner, clause.type(), "catch");
            CoreLocal target = CoreVerificationTypes.local(callable, clause.localIndex());
            if (target.kind() != CoreLocal.Kind.VARIABLE) {
              throw new IllegalArgumentException("catch binding must be a variable local");
            }
            validationTypes.requireSameType(
                owner, target.type(), owner, clause.type(), "catch binding");
            CoreType absoluteType = validationTypes.absolute(owner, clause.type());
            if (preceding.stream()
                .anyMatch(previous -> validationTypes.isAssignable(previous, absoluteType))) {
              throw new IllegalArgumentException("core catch type is already covered");
            }
            preceding.add(absoluteType);
            referenceFlow.replace(incoming);
            verifyBlock(owner, callable, clause.body(), List.of(clause.localIndex()), false);
            if (!definitelyExits(clause.body())) completing.add(referenceFlow.snapshot());
          }
          CoreReferenceFlow.State normal = mergeCompletingReferenceFlows(incoming, completing);
          referenceFlow.replace(normal);
          if (tried.finallyBlock().isPresent()) {
            referenceFlow.replace(incoming);
            CoreReferenceFlow.Writes writes = referenceFlow.trackWrites();
            try (writes) {
              verifyBlock(owner, callable, tried.finallyBlock().orElseThrow());
            }
            CoreReferenceFlow.State finalFlow = referenceFlow.snapshot();
            referenceFlow.replace(CoreReferenceFlow.overlay(normal, finalFlow, writes.locals()));
          }
        }
        case CoreStatement.ThrowStatement thrown -> {
          verifyExpression(owner, callable, thrown.exception());
          validationTypes.requireExceptionType(owner, thrown.exception().type(), "throw");
        }
        case CoreStatement.ReturnStatement returned -> {
          if (returned.value().isEmpty()) {
            validationTypes.requireSameType(
                owner, CoreType.VOID, owner, callable.returnType(), "return");
          } else {
            CoreExpression value = returned.value().orElseThrow();
            verifyExpression(owner, callable, value);
            validationTypes.requireAssignable(
                owner, callable.returnType(), owner, value.type(), "return");
          }
        }
        case CoreStatement.YieldStatement yielded -> {
          if (controls.isEmpty() || controls.getFirst().kind() != ControlKind.SWITCH) {
            throw new IllegalArgumentException("yield is only valid inside switch");
          }
          verifyExpression(owner, callable, yielded.value());
          validationTypes.requireAssignable(
              owner,
              controls.getFirst().yieldType(),
              owner,
              yielded.value().type(),
              "switch yield");
          if (yielded.value().type() instanceof CoreType.Reference) {
            controls.getFirst().mergeReferenceLifetime(referenceLifetime(yielded.value()));
          }
        }
        case CoreStatement.BreakStatement ignored -> {
          if (controls.isEmpty() || controls.getFirst().kind() != ControlKind.LOOP) {
            throw new IllegalArgumentException("break is only valid inside loop");
          }
        }
        case CoreStatement.ContinueStatement ignored -> {
          if (controls.stream().noneMatch(value -> value.kind() == ControlKind.LOOP)) {
            throw new IllegalArgumentException("continue is only valid inside loop");
          }
        }
      }
    }
    referenceFlow.pop();
  }

  private void updateReferenceLifetime(int destination, CoreExpression value) {
    LexicalLifetime source = referenceLifetime(value);
    if (!source.outlives(referenceFlow.storageLifetime(destination))) {
      throw new IllegalArgumentException(
          "core reference cannot outlive the addressed storage location");
    }
    referenceFlow.update(destination, source);
  }

  private static CoreReferenceFlow.State mergeCompletingReferenceFlows(
      CoreReferenceFlow.State incoming, List<CoreReferenceFlow.State> flows) {
    if (flows.isEmpty()) return incoming;
    CoreReferenceFlow.State result = flows.getFirst();
    for (int index = 1; index < flows.size(); index++) {
      result = CoreReferenceFlow.merge(incoming, result, flows.get(index));
    }
    return result;
  }

  private LexicalLifetime referenceLifetime(CoreExpression expression) {
    return switch (expression) {
      case CoreExpression.AddressLocal address ->
          referenceFlow.storageLifetime(address.localIndex());
      case CoreExpression.AddressField ignored -> LexicalLifetime.longLived();
      case CoreExpression.LocalRead read -> referenceFlow.referenceLifetime(read.localIndex());
      case CoreExpression.Switch switched -> {
        LexicalLifetime lifetime = referenceFlow.expressionLifetime(switched);
        yield lifetime == null ? LexicalLifetime.unusable() : lifetime;
      }
      default -> LexicalLifetime.unusable();
    };
  }

  private void verifyExpression(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression expression) {
    if (expression instanceof CoreExpression.Call
        || expression instanceof CoreExpression.InterfaceCall
        || expression instanceof CoreExpression.Intrinsic
        || expression.type().equals(CoreType.VOID)) {
      validationTypes.verifyReturnType(
          owner, expression.type(), callable.reifiedTypeLocals().size());
    } else if (!(expression instanceof CoreExpression.NullLiteral)
        || !expression.type().equals(CoreType.NULL)) {
      validationTypes.verifyValueType(
          owner, expression.type(), callable.reifiedTypeLocals().size());
    }
    switch (expression) {
      case CoreExpression.Literal literal -> verifyLiteral(owner, literal);
      case CoreExpression.NullLiteral literal -> {
        if (!literal.type().equals(CoreType.NULL) && !literal.type().isNullable()) {
          throw new IllegalArgumentException("null literal requires a nullable core type");
        }
      }
      case CoreExpression.CollectionLiteral collection -> {
        verifyRuntimeType(owner, callable, collection.runtimeType());
        validationTypes.requireSameType(
            owner,
            collection.runtimeType().template(),
            owner,
            collection.type(),
            "collection runtime type");
        if (!intrinsicVerifier.matchesCollectionLiteral(owner, collection)) {
          throw new IllegalArgumentException("collection literal does not match its builtin ABI");
        }
        CoreType absoluteType = validationTypes.absolute(owner, collection.type());
        if (!(absoluteType instanceof CoreType.Declared declared)
            || declared.arguments().size() != 1) {
          throw new IllegalArgumentException("collection literal requires one element type");
        }
        CoreType elementType = declared.arguments().getFirst();
        collection
            .elements()
            .forEach(value -> verifyCollectionElement(owner, callable, value, elementType));
      }
      case CoreExpression.LocalRead read -> {
        CoreLocal local = CoreVerificationTypes.local(callable, read.localIndex());
        referenceFlow.requireDeclared(read.localIndex());
        validationTypes.requireAssignable(owner, local.type(), owner, read.type(), "local read");
        if (local.type() instanceof CoreType.Reference) {
          referenceFlow.referenceLifetime(read.localIndex());
        }
      }
      case CoreExpression.FieldRead read -> {
        verifyExpression(owner, callable, read.receiver());
        verifyReceiverSafety(owner, read.receiver().type(), read.nullSafe(), "field read");
        CoreType fieldType =
            validationTypes.instantiatedFieldType(owner, read.receiver().type(), read.field());
        validationTypes.requireSameType(
            owner,
            CoreVerificationTypes.safeResult(fieldType, read.nullSafe(), read.receiver().type()),
            owner,
            read.type(),
            "field read");
      }
      case CoreExpression.AddressLocal address -> {
        CoreLocal local = CoreVerificationTypes.local(callable, address.localIndex());
        if (local.kind() != CoreLocal.Kind.VARIABLE && local.kind() != CoreLocal.Kind.PARAMETER) {
          throw new IllegalArgumentException("address target is not mutable local storage");
        }
        CoreType target = referenceTarget(owner, address.type(), "local address");
        validationTypes.requireSameType(owner, local.type(), owner, target, "local address");
        referenceFlow.storageLifetime(address.localIndex());
      }
      case CoreExpression.AddressField address -> {
        verifyExpression(owner, callable, address.receiver());
        validationTypes.requireNonNullableReceiver(
            owner, address.receiver().type(), "field address");
        CoreType receiver =
            CoreVerificationTypes.nonNullable(
                validationTypes.absolute(owner, address.receiver().type()));
        if (!(receiver instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.IDENTITY) {
          throw new IllegalArgumentException("field address requires an identity receiver");
        }
        CoreType fieldType =
            validationTypes.instantiatedFieldType(
                owner, address.receiver().type(), address.field());
        CoreType target = referenceTarget(owner, address.type(), "field address");
        validationTypes.requireSameType(owner, fieldType, owner, target, "field address");
      }
      case CoreExpression.Dereference dereference -> {
        verifyExpression(owner, callable, dereference.reference());
        CoreType target = referenceTarget(owner, dereference.reference().type(), "dereference");
        validationTypes.requireSameType(owner, target, owner, dereference.type(), "dereference");
      }
      case CoreExpression.EnumConstruct construct ->
          verifyEnumConstruct(owner, callable, construct);
      case CoreExpression.Unary unary -> {
        verifyExpression(owner, callable, unary.operand());
        verifyUnary(owner, unary);
      }
      case CoreExpression.Binary binary -> {
        verifyExpression(owner, callable, binary.left());
        verifyExpression(owner, callable, binary.right());
        verifyBinary(owner, binary);
      }
      case CoreExpression.Switch switched -> verifySwitch(owner, callable, switched);
      case CoreExpression.Index index -> {
        verifyExpression(owner, callable, index.receiver());
        verifyExpression(owner, callable, index.index());
        intrinsicVerifier.verifyIndex(owner, index);
      }
      case CoreExpression.CopyObject copied -> {
        verifyExpression(owner, callable, copied.receiver());
        verifyReceiverSafety(owner, copied.receiver().type(), copied.nullSafe(), "copy");
        CoreType receiver =
            CoreVerificationTypes.nonNullable(
                validationTypes.absolute(owner, copied.receiver().type()));
        if (!(receiver instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.IDENTITY) {
          throw new IllegalArgumentException("copy requires an identity receiver");
        }
        validationTypes.requireSameType(
            owner,
            CoreVerificationTypes.safeResult(
                copied.receiver().type(), copied.nullSafe(), copied.receiver().type()),
            owner,
            copied.type(),
            "copy result");
      }
      case CoreExpression.Closure closure -> verifyClosure(owner, callable, closure);
      case CoreExpression.Invoke invoke -> verifyInvoke(owner, callable, invoke);
      case CoreExpression.Call call -> verifyCall(owner, callable, call);
      case CoreExpression.InterfaceCall call -> verifyInterfaceCall(owner, callable, call);
      case CoreExpression.Construct construct -> verifyConstruct(owner, callable, construct);
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(value -> verifyExpression(owner, callable, value));
        intrinsic
            .arguments()
            .forEach(argument -> verifyExpression(owner, callable, argument.value()));
        intrinsic.runtimeType().ifPresent(type -> verifyRuntimeType(owner, callable, type));
        intrinsic
            .runtimeDependencies()
            .forEach(
                type ->
                    validationTypes.verifyValueType(
                        owner, type, callable.reifiedTypeLocals().size()));
        intrinsicVerifier.verifyIntrinsic(owner, intrinsic);
      }
    }
  }

  private void verifyLiteral(DefinitionId owner, CoreExpression.Literal literal) {
    CoreType expected =
        switch (literal.value()) {
          case Integer ignored ->
              validationTypes.absolute(owner, literal.type()).equals(CoreType.CODE_POINT)
                  ? CoreType.CODE_POINT
                  : CoreType.INTEGER;
          case Long ignored -> CoreType.LONG;
          case Float ignored -> CoreType.FLOAT;
          case Double ignored -> CoreType.DOUBLE;
          case Boolean ignored -> CoreType.BOOLEAN;
          case String ignored -> CoreType.STRING;
          default -> throw new IllegalArgumentException("unsupported core literal value");
        };
    validationTypes.requireSameType(owner, expected, owner, literal.type(), "literal");
  }

  private CoreType referenceTarget(DefinitionId owner, CoreType type, String subject) {
    CoreType absolute = validationTypes.absolute(owner, type);
    if (!(absolute instanceof CoreType.Reference reference)) {
      throw new IllegalArgumentException(subject + " requires a reference type");
    }
    return reference.target();
  }

  private void verifyUnary(DefinitionId owner, CoreExpression.Unary unary) {
    if (unary.operator() == CoreUnaryOperator.THROW) {
      validationTypes.requireExceptionType(owner, unary.operand().type(), "throw");
      return;
    }
    CoreType expected = validationTypes.absolute(owner, unary.operand().type());
    if (unary.operator() == CoreUnaryOperator.NON_NULL) {
      if (expected.equals(CoreType.NULL) || expected.equals(CoreType.VOID)) {
        throw new IllegalArgumentException(
            "non-null assertion requires an inhabitable operand type");
      }
      validationTypes.requireSameType(
          owner,
          CoreVerificationTypes.nonNullable(expected),
          owner,
          unary.type(),
          "non-null assertion result");
      return;
    }
    if (unary.operator() == CoreUnaryOperator.NOT) expected = CoreType.BOOLEAN;
    else if (!CoreVerificationTypes.isNumericLeaf(expected)) {
      throw new IllegalArgumentException("numeric unary operand requires a concrete leaf type");
    }
    validationTypes.requireSameType(
        owner, expected, owner, unary.operand().type(), "unary operand");
    validationTypes.requireSameType(owner, expected, owner, unary.type(), "unary result");
  }

  private void verifyBinary(DefinitionId owner, CoreExpression.Binary binary) {
    CoreType left = validationTypes.absolute(owner, binary.left().type());
    CoreType right = validationTypes.absolute(owner, binary.right().type());
    CoreType result = validationTypes.absolute(owner, binary.type());
    switch (binary.operator()) {
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER -> {
        if (!CoreVerificationTypes.isNumericLeaf(left)) {
          throw new IllegalArgumentException("binary left operand requires a numeric leaf type");
        }
        CoreVerificationTypes.requireSameAbsoluteType(left, right, "binary right operand");
        CoreVerificationTypes.requireSameAbsoluteType(left, result, "binary result");
      }
      case STRING_CONCAT -> {
        CoreVerificationTypes.requireSameAbsoluteType(CoreType.STRING, left, "binary left operand");
        CoreVerificationTypes.requireSameAbsoluteType(
            CoreType.STRING, right, "binary right operand");
        CoreVerificationTypes.requireSameAbsoluteType(CoreType.STRING, result, "binary result");
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        if (!CoreVerificationTypes.isNumericLeaf(left)) {
          throw new IllegalArgumentException(
              "comparison left operand requires a numeric leaf type");
        }
        CoreVerificationTypes.requireSameAbsoluteType(left, right, "comparison right operand");
        CoreVerificationTypes.requireSameAbsoluteType(
            CoreType.BOOLEAN, result, "comparison result");
      }
      case AND, OR -> {
        CoreVerificationTypes.requireSameAbsoluteType(
            CoreType.BOOLEAN, left, "logical left operand");
        CoreVerificationTypes.requireSameAbsoluteType(
            CoreType.BOOLEAN, right, "logical right operand");
        CoreVerificationTypes.requireSameAbsoluteType(CoreType.BOOLEAN, result, "logical result");
      }
      case EQUAL, NOT_EQUAL -> {
        if (!validationTypes.isAssignable(left, right)
            && !validationTypes.isAssignable(right, left)) {
          throw new IllegalArgumentException("equality operands have incompatible types");
        }
        CoreVerificationTypes.requireSameAbsoluteType(CoreType.BOOLEAN, result, "equality result");
      }
      case COALESCE -> {
        if (!CoreVerificationTypes.mayContainNull(left)) {
          throw new IllegalArgumentException("coalesce left operand cannot contain null");
        }
        if (left.equals(CoreType.NULL)) {
          validationTypes.requireAssignable(result, right, "coalesce right operand");
        } else {
          CoreType expected = CoreVerificationTypes.nonNullable(left);
          validationTypes.requireAssignable(expected, right, "coalesce right operand");
          CoreVerificationTypes.requireSameAbsoluteType(expected, result, "coalesce result");
        }
      }
    }
  }

  private void verifyCollectionElement(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CoreCollectionElement element,
      CoreType elementType) {
    switch (element) {
      case CoreExpression expression -> {
        verifyExpression(owner, callable, expression);
        validationTypes.requireAssignable(
            owner, elementType, owner, expression.type(), "collection element");
      }
      case CoreCollectionElement.Conditional conditional -> {
        verifyExpression(owner, callable, conditional.condition());
        CoreVerificationTypes.requireSameAbsoluteType(
            CoreType.BOOLEAN,
            validationTypes.absolute(owner, conditional.condition().type()),
            "collection condition");
        CoreReferenceFlow.State incoming = referenceFlow.snapshot();
        referenceFlow.push();
        verifyCollectionElement(owner, callable, conditional.thenElement(), elementType);
        referenceFlow.pop();
        CoreReferenceFlow.State thenFlow = referenceFlow.snapshot();
        referenceFlow.replace(incoming);
        referenceFlow.push();
        conditional
            .elseElement()
            .ifPresent(value -> verifyCollectionElement(owner, callable, value, elementType));
        referenceFlow.pop();
        referenceFlow.replace(
            CoreReferenceFlow.merge(incoming, thenFlow, referenceFlow.snapshot()));
      }
      case CoreCollectionElement.Repeated repeated -> {
        if (CoreVerificationTypes.local(callable, repeated.iteratorLocal()).kind()
                != CoreLocal.Kind.ITERATOR
            || CoreVerificationTypes.local(callable, repeated.variableLocal()).kind()
                != CoreLocal.Kind.VARIABLE) {
          throw new IllegalArgumentException("collection loop local ABI is invalid");
        }
        repeated
            .indexLocal()
            .ifPresent(
                index -> {
                  if (index == repeated.iteratorLocal()
                      || index == repeated.variableLocal()
                      || CoreVerificationTypes.local(callable, index).kind()
                          != CoreLocal.Kind.VARIABLE
                      || !validationTypes
                          .absolute(owner, CoreVerificationTypes.local(callable, index).type())
                          .equals(CoreType.INTEGER)) {
                    throw new IllegalArgumentException("collection loop index ABI is invalid");
                  }
                });
        verifyExpression(owner, callable, repeated.iterable());
        intrinsicVerifier.verifyIteration(
            owner, callable, repeated.variableLocal(), repeated.iterable(), repeated.iteration());
        CoreReferenceFlow.State incoming = referenceFlow.snapshot();
        referenceFlow.push();
        referenceFlow.declare(repeated.variableLocal());
        repeated.indexLocal().ifPresent(referenceFlow::declare);
        controls.addFirst(Control.loop());
        verifyCollectionElement(owner, callable, repeated.element(), elementType);
        controls.removeFirst();
        referenceFlow.pop();
        referenceFlow.replace(
            CoreReferenceFlow.merge(incoming, incoming, referenceFlow.snapshot()));
      }
    }
  }

  private void verifyReceiverSafety(
      DefinitionId owner, CoreType receiver, boolean nullSafe, String subject) {
    CoreType actual = validationTypes.absolute(owner, receiver);
    if (!nullSafe && actual.isNullable()) {
      throw new IllegalArgumentException(subject + " requires null-safe access");
    }
  }

  private void verifyCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Call call) {
    DefinitionId targetId = validationTypes.resolve(owner, call.target());
    CallableSignature target = validationTypes.callableSignature(targetId, "call target");
    if (!target.implemented() && !call.virtual()) {
      throw new IllegalArgumentException("method signature calls require virtual dispatch");
    }
    call.receiver().ifPresent(value -> verifyExpression(owner, caller, value));
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    call.receiverTypeArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    if (target.hasReceiver() != call.receiver().isPresent()) {
      throw new IllegalArgumentException("call receiver does not match the target ABI");
    }
    if (call.nullSafe() && call.receiver().isEmpty()) {
      throw new IllegalArgumentException("null-safe call requires a receiver");
    }
    List<CoreType> substitutions = new ArrayList<>();
    if (target.hasReceiver()) {
      CoreType actualReceiver =
          validationTypes.absolute(owner, call.receiver().orElseThrow().type());
      CoreType nonNullableReceiver = CoreVerificationTypes.nonNullable(actualReceiver);
      CoreType dispatchReceiver =
          validationTypes.effectiveClassReceiver(owner, nonNullableReceiver);
      if (!(dispatchReceiver instanceof CoreType.Declared declaredReceiver)) {
        throw new IllegalArgumentException("method receiver is not a declared type");
      }
      verifyMethodDispatch(dispatchReceiver, targetId, call.virtual(), "call");
      if (call.receiverTypeArguments().isEmpty()) {
        substitutions.addAll(validationTypes.receiverArguments(targetId, target, declaredReceiver));
      } else {
        call.receiverTypeArguments().stream()
            .map(CoreRuntimeType::template)
            .map(type -> validationTypes.absolute(owner, type))
            .forEach(substitutions::add);
      }
      CoreType expectedReceiver =
          validationTypes
              .absolute(targetId, target.receiverType().orElseThrow())
              .substitute(substitutions::get);
      if (!validationTypes.isAssignable(expectedReceiver, nonNullableReceiver)
          && !validationTypes.isAssignable(expectedReceiver, dispatchReceiver)) {
        throw new IllegalArgumentException("method receiver type does not match the target ABI");
      }
      if (!call.nullSafe() && actualReceiver.isNullable()) {
        throw new IllegalArgumentException("nullable receiver requires a null-safe call");
      }
    }
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> validationTypes.absolute(owner, type))
        .forEach(substitutions::add);
    if (substitutions.size() != target.reifiedParameterCount()) {
      throw new IllegalArgumentException("call reified arguments do not match the target ABI");
    }
    validationTypes.verifyTypeArgumentBounds(
        targetId, target.typeParameters(), substitutions, owner);
    CoreVerificationTypes.verifyDenseArguments(call.arguments(), target.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          validationTypes
              .absolute(targetId, target.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      validationTypes.requireAssignable(
          expected, validationTypes.absolute(owner, argument.value().type()), "call argument");
    }
    CoreType result =
        validationTypes.absolute(targetId, target.returnType()).substitute(substitutions::get);
    CoreType receiverType = call.receiver().map(CoreExpression::type).orElse(CoreType.DYNAMIC);
    result = CoreVerificationTypes.safeResult(result, call.nullSafe(), receiverType);
    CoreVerificationTypes.requireSameAbsoluteType(
        result, validationTypes.absolute(owner, call.type()), "call result");
  }

  private void verifyClosure(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Closure closure) {
    DefinitionId targetId = validationTypes.resolve(owner, closure.target());
    CallableSignature target = validationTypes.callableSignature(targetId, "closure target");
    if (!target.implemented() && !closure.virtual()) {
      throw new IllegalArgumentException("method signature references require virtual dispatch");
    }
    closure.receiver().ifPresent(value -> verifyExpression(owner, caller, value));
    closure.captures().forEach(value -> verifyExpression(owner, caller, value));
    closure.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    closure.receiverTypeArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    if (!target.hasReceiver() && closure.receiver().isPresent()) {
      throw new IllegalArgumentException("closure receiver does not match the target ABI");
    }
    if (target.captureTypes().size() != closure.captures().size()) {
      throw new IllegalArgumentException("closure captures do not match the target ABI");
    }
    List<CoreType> substitutions = new ArrayList<>();
    CoreType unboundReceiver = null;
    if (target.hasReceiver()) {
      if (closure.receiver().isPresent()) {
        CoreType receiver =
            CoreVerificationTypes.nonNullable(
                validationTypes.absolute(owner, closure.receiver().orElseThrow().type()));
        CoreType dispatchReceiver = validationTypes.effectiveClassReceiver(owner, receiver);
        if (!(dispatchReceiver instanceof CoreType.Declared declared)) {
          throw new IllegalArgumentException("bound method receiver is not declared");
        }
        verifyMethodDispatch(dispatchReceiver, targetId, closure.virtual(), "closure");
        if (closure.receiverTypeArguments().isEmpty()) {
          substitutions.addAll(validationTypes.receiverArguments(targetId, target, declared));
        } else {
          closure.receiverTypeArguments().stream()
              .map(CoreRuntimeType::template)
              .map(type -> validationTypes.absolute(owner, type))
              .forEach(substitutions::add);
        }
        CoreType expectedReceiver =
            validationTypes
                .absolute(targetId, target.receiverType().orElseThrow())
                .substitute(substitutions::get);
        if (!validationTypes.isAssignable(expectedReceiver, receiver)
            && !validationTypes.isAssignable(expectedReceiver, dispatchReceiver)) {
          throw new IllegalArgumentException("bound method receiver does not match the target ABI");
        }
      } else {
        closure.receiverTypeArguments().stream()
            .map(CoreRuntimeType::template)
            .map(type -> validationTypes.absolute(owner, type))
            .forEach(substitutions::add);
        unboundReceiver = validationTypes.absolute(targetId, target.receiverType().orElseThrow());
        if (!substitutions.isEmpty()) {
          unboundReceiver = unboundReceiver.substitute(substitutions::get);
        }
        verifyMethodDispatch(unboundReceiver, targetId, closure.virtual(), "closure");
      }
    }
    closure.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> validationTypes.absolute(owner, type))
        .forEach(substitutions::add);
    if (substitutions.size() != target.reifiedParameterCount()) {
      throw new IllegalArgumentException("closure reified arguments do not match the target ABI");
    }
    validationTypes.verifyTypeArgumentBounds(
        targetId, target.typeParameters(), substitutions, owner);
    for (int index = 0; index < closure.captures().size(); index++) {
      CoreType expected = validationTypes.absolute(targetId, target.captureTypes().get(index));
      if (!substitutions.isEmpty()) expected = expected.substitute(substitutions::get);
      validationTypes.requireAssignable(
          expected,
          validationTypes.absolute(owner, closure.captures().get(index).type()),
          "closure capture");
    }
    List<CoreType> parameters = new ArrayList<>();
    if (unboundReceiver != null) parameters.add(unboundReceiver);
    target.parameterTypes().stream()
        .map(value -> validationTypes.absolute(targetId, value))
        .map(value -> substitutions.isEmpty() ? value : value.substitute(substitutions::get))
        .forEach(parameters::add);
    CoreType result = validationTypes.absolute(targetId, target.returnType());
    if (!substitutions.isEmpty()) result = result.substitute(substitutions::get);
    CoreType expected = new CoreType.Function(result, parameters, CoreNullability.NON_NULL);
    CoreVerificationTypes.requireSameAbsoluteType(
        expected, validationTypes.absolute(owner, closure.type()), "closure type");
  }

  private void verifyMethodDispatch(
      CoreType receiver, DefinitionId slot, boolean virtual, String subject) {
    boolean dispatched = false;
    if (receiver instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user) {
      DefinitionId aggregateId = CoreVerificationTypes.resolveExternal(user.definition());
      CoreDefinition definition = program.definition(aggregateId).orElseThrow();
      if (definition instanceof CoreDefinition.Aggregate aggregate) {
        dispatched =
            aggregate.dispatch().stream()
                .anyMatch(
                    dispatch -> validationTypes.resolve(aggregateId, dispatch.slot()).equals(slot));
      }
    }
    if (dispatched != virtual) {
      throw new IllegalArgumentException(subject + " dispatch mode does not match its method slot");
    }
  }

  private void verifyInvoke(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Invoke invoke) {
    verifyExpression(owner, caller, invoke.callee());
    invoke.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType callee =
        CoreVerificationTypes.nonNullable(validationTypes.absolute(owner, invoke.callee().type()));
    if (!(callee instanceof CoreType.Function function)) {
      throw new IllegalArgumentException("invoked expression is not a function");
    }
    CoreVerificationTypes.verifyDenseArguments(
        invoke.arguments(), function.parameterTypes().size());
    for (CoreArgument argument : invoke.arguments()) {
      validationTypes.requireAssignable(
          function.parameterTypes().get(argument.parameterIndex()),
          validationTypes.absolute(owner, argument.value().type()),
          "function argument");
    }
    CoreVerificationTypes.requireSameAbsoluteType(
        function.returnType(), validationTypes.absolute(owner, invoke.type()), "function result");
  }

  private void verifyInterfaceCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.InterfaceCall call) {
    DefinitionId requirementId = validationTypes.resolve(owner, call.requirement());
    CoreDefinition target = program.definition(requirementId).orElseThrow();
    if (!(target instanceof CoreDefinition.MethodSignature requirement)) {
      throw new IllegalArgumentException("interface call target is not an interface method");
    }
    verifyExpression(owner, caller, call.receiver());
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    verifyReceiverSafety(owner, call.receiver().type(), call.nullSafe(), "interface call");
    CoreType actualReceiver =
        CoreVerificationTypes.nonNullable(validationTypes.absolute(owner, call.receiver().type()));
    InterfaceInstance required =
        validationTypes.interfaceInstance(requirementId, requirement.receiverType());
    CoreType realized = validationTypes.realizedInterface(actualReceiver, required.definition());
    if (realized == null && actualReceiver instanceof CoreType.Parameter parameter) {
      CoreTypeParameter declaration =
          caller.typeParameters().stream()
              .filter(candidate -> candidate.index() == parameter.index())
              .findFirst()
              .orElse(null);
      if (declaration != null && declaration.upperBound().isPresent()) {
        CoreType upperBound =
            validationTypes.absolute(owner, declaration.upperBound().orElseThrow());
        realized =
            validationTypes.realizedInterface(
                CoreVerificationTypes.nonNullable(upperBound), required.definition());
      }
    }
    if (realized == null) {
      throw new IllegalArgumentException(
          "interface call receiver does not satisfy the requirement");
    }
    InterfaceInstance instance = validationTypes.interfaceInstance(owner, realized);
    List<CoreType> substitutions =
        validationTypes.interfaceSubstitutions(instance, required.definition());
    if (substitutions == null) {
      throw new IllegalArgumentException(
          "interface call receiver does not satisfy the requirement");
    }
    substitutions = new ArrayList<>(substitutions);
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> validationTypes.absolute(owner, type))
        .forEach(substitutions::add);
    if (call.reifiedArguments().size() != requirement.typeParameters().size()) {
      throw new IllegalArgumentException(
          "interface call type arguments do not match the requirement ABI");
    }
    validationTypes.verifyTypeArgumentBounds(
        requirementId, requirement.typeParameters(), substitutions, owner);
    CoreVerificationTypes.verifyDenseArguments(
        call.arguments(), requirement.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          validationTypes
              .absolute(requirementId, requirement.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      validationTypes.requireAssignable(
          expected,
          validationTypes.absolute(owner, argument.value().type()),
          "interface call argument");
    }
    CoreType result =
        validationTypes
            .absolute(requirementId, requirement.returnType())
            .substitute(substitutions::get);
    result = CoreVerificationTypes.safeResult(result, call.nullSafe(), call.receiver().type());
    CoreVerificationTypes.requireSameAbsoluteType(
        result, validationTypes.absolute(owner, call.type()), "interface call result");
  }

  private void verifyConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Construct construct) {
    DefinitionId targetId = validationTypes.resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Aggregate target)) {
      throw new IllegalArgumentException("construct target is not an aggregate");
    }
    if (target.dispatch().stream()
        .anyMatch(
            method ->
                program.definition(validationTypes.resolve(targetId, method.target())).orElseThrow()
                    instanceof CoreDefinition.MethodSignature)) {
      throw new IllegalArgumentException("construct target has unimplemented methods");
    }
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType constructedType = validationTypes.absolute(owner, construct.type());
    CoreVerificationTypes.requireSameAbsoluteType(
        constructedType,
        validationTypes.absolute(owner, construct.runtimeType().template()),
        "construct runtime type");
    if (!(CoreVerificationTypes.nonNullable(constructedType) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !CoreVerificationTypes.resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("constructed type does not match the aggregate ABI");
    }
    DefinitionId initializerId = validationTypes.resolve(owner, construct.initializer());
    if (target.constructors().stream()
        .map(constructor -> validationTypes.resolve(targetId, constructor))
        .noneMatch(initializerId::equals)) {
      throw new IllegalArgumentException("construct initializer does not match the aggregate");
    }
    CoreDefinition definition = program.definition(initializerId).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable initializer)
        || initializer.receiverType().isEmpty()) {
      throw new IllegalArgumentException("construct initializer is not a constructor");
    }
    CoreVerificationTypes.verifyDenseArguments(
        construct.arguments(), initializer.parameterTypes().size());
    List<CoreType> substitutions = new ArrayList<>(declared.arguments());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          validationTypes
              .absolute(initializerId, initializer.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      validationTypes.requireAssignable(
          expected,
          validationTypes.absolute(owner, argument.value().type()),
          "constructor argument");
    }
  }

  private void verifyEnumConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.EnumConstruct construct) {
    DefinitionId targetId = validationTypes.resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Enum target)) {
      throw new IllegalArgumentException("enum construct target is not an enum");
    }
    CoreEnumVariant variant =
        target.variants().stream()
            .filter(candidate -> candidate.key().equals(construct.variantKey()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("enum construct variant is absent"));
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType type = validationTypes.absolute(owner, construct.type());
    CoreVerificationTypes.requireSameAbsoluteType(
        type,
        validationTypes.absolute(owner, construct.runtimeType().template()),
        "enum construct runtime type");
    if (type.isNullable()
        || !(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !CoreVerificationTypes.resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("enum construct type does not match its target");
    }
    CoreVerificationTypes.verifyDenseArguments(construct.arguments(), variant.fields().size());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          validationTypes
              .absolute(targetId, variant.fields().get(argument.parameterIndex()).type())
              .substitute(declared.arguments()::get);
      validationTypes.requireAssignable(
          expected,
          validationTypes.absolute(owner, argument.value().type()),
          "enum construct argument");
    }
  }

  private void verifySwitch(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression.Switch switched) {
    verifyExpression(owner, callable, switched.value());
    CoreType valueType = validationTypes.absolute(owner, switched.value().type());
    PatternCoverage<CoreType> coverage = new PatternCoverage<>(new CorePatternDomain(owner));
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    Control switchControl = Control.switched(switched.type());
    CoreReferenceFlow.State incoming = referenceFlow.snapshot();
    List<CoreReferenceFlow.State> caseFlows = new ArrayList<>();
    for (CoreSwitchCase switchCase : switched.cases()) {
      referenceFlow.replace(incoming);
      PatternCoverage.Pattern pattern =
          verifyPattern(owner, callable, switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        throw new IllegalArgumentException("core switch case is unreachable");
      }
      previous.add(pattern);
      controls.addFirst(switchControl);
      verifyBlock(owner, callable, switchCase.body(), patternLocals(switchCase.pattern()), false);
      controls.removeFirst();
      caseFlows.add(referenceFlow.snapshot());
      if (!switched.type().equals(CoreType.VOID) && !definitelyYields(switchCase.body())) {
        throw new IllegalArgumentException("core switch expression case does not yield");
      }
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      throw new IllegalArgumentException("core switch is not exhaustive");
    }
    if (!caseFlows.isEmpty()) {
      CoreReferenceFlow.State merged = caseFlows.getFirst();
      for (int index = 1; index < caseFlows.size(); index++) {
        merged = CoreReferenceFlow.merge(incoming, merged, caseFlows.get(index));
      }
      referenceFlow.replace(merged);
    }
    if (switched.type() instanceof CoreType.Reference) {
      LexicalLifetime lifetime = switchControl.referenceLifetime();
      if (lifetime == null || !lifetime.outlives(referenceFlow.currentLifetime())) {
        throw new IllegalArgumentException(
            "core reference cannot outlive the addressed storage location");
      }
      referenceFlow.recordExpressionLifetime(switched, lifetime);
    }
  }

  private static List<Integer> patternLocals(CorePattern pattern) {
    List<Integer> result = new ArrayList<>();
    collectPatternLocals(pattern, result);
    return List.copyOf(result);
  }

  private static void collectPatternLocals(CorePattern pattern, List<Integer> result) {
    switch (pattern) {
      case CorePattern.Binding binding -> result.add(binding.localIndex());
      case CorePattern.Variant variant ->
          variant.arguments().forEach(argument -> collectPatternLocals(argument, result));
      case CorePattern.Wildcard ignored -> {}
      case CorePattern.Literal ignored -> {}
      case CorePattern.Null ignored -> {}
    }
  }

  private PatternCoverage.Pattern verifyPattern(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CorePattern pattern,
      CoreType expected) {
    if (expected.isNullable() && !(pattern instanceof CorePattern.Null)) {
      if (pattern instanceof CorePattern.Wildcard) return PatternCoverage.Pattern.any();
      PatternCoverage.Pattern value =
          PatternCoverage.Pattern.constructor(
              "$value",
              List.of(
                  verifyNonNullPattern(
                      owner, callable, pattern, CoreVerificationTypes.nonNullable(expected))));
      if (pattern instanceof CorePattern.Binding binding && binding.type().isNullable()) {
        return PatternCoverage.Pattern.alternatives(
            List.of(PatternCoverage.Pattern.constructor("$null", List.of()), value));
      }
      return value;
    }
    if (pattern instanceof CorePattern.Null) {
      if (!expected.isNullable()) {
        throw new IllegalArgumentException("core null pattern requires nullable type");
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return verifyNonNullPattern(
        owner, callable, pattern, CoreVerificationTypes.nonNullable(expected));
  }

  private PatternCoverage.Pattern verifyNonNullPattern(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CorePattern pattern,
      CoreType expected) {
    return switch (pattern) {
      case CorePattern.Wildcard ignored -> PatternCoverage.Pattern.any();
      case CorePattern.Binding binding -> {
        CoreLocal local = CoreVerificationTypes.local(callable, binding.localIndex());
        if (local.kind() != CoreLocal.Kind.VARIABLE) {
          throw new IllegalArgumentException("core pattern binding requires variable local");
        }
        validationTypes.verifyValueType(owner, binding.type(), callable.reifiedTypeLocals().size());
        verifyRuntimeType(owner, callable, binding.runtimeType());
        validationTypes.requireSameType(
            owner, binding.type(), owner, local.type(), "pattern binding local");
        CoreType type =
            CoreVerificationTypes.nonNullable(validationTypes.absolute(owner, binding.type()));
        if (!validationTypes.isAssignable(expected, type)
            && !validationTypes.isAssignable(type, expected)) {
          throw new IllegalArgumentException("core pattern binding type is unrelated to input");
        }
        patternTypes.put("type:" + type, type);
        yield validationTypes.isAssignable(type, expected)
            ? PatternCoverage.Pattern.any()
            : PatternCoverage.Pattern.constructor("type:" + type, List.of());
      }
      case CorePattern.Variant variant -> {
        EnumInstance instance = enumInstance(expected);
        CoreEnumVariant declaration =
            instance.declaration().variants().stream()
                .filter(candidate -> candidate.key().equals(variant.variantKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("core pattern variant is absent"));
        if (variant.arguments().size() != declaration.fields().size()) {
          throw new IllegalArgumentException("core pattern variant has wrong payload arity");
        }
        List<PatternCoverage.Pattern> arguments = new ArrayList<>();
        for (int index = 0; index < variant.arguments().size(); index++) {
          CoreType payloadType =
              validationTypes
                  .absolute(instance.definition(), declaration.fields().get(index).type())
                  .substitute(instance.type().arguments()::get);
          arguments.add(
              verifyPattern(owner, callable, variant.arguments().get(index), payloadType));
        }
        yield PatternCoverage.Pattern.constructor("variant:" + variant.variantKey(), arguments);
      }
      case CorePattern.Literal literal -> {
        CoreType literalType =
            switch (literal.value()) {
              case Integer ignored ->
                  literal.type().equals(CoreType.CODE_POINT)
                      ? CoreType.CODE_POINT
                      : CoreType.INTEGER;
              case Long ignored -> CoreType.LONG;
              case Float ignored -> CoreType.FLOAT;
              case Double ignored -> CoreType.DOUBLE;
              case Boolean ignored -> CoreType.BOOLEAN;
              case String ignored -> CoreType.STRING;
              default -> throw new IllegalArgumentException("unsupported core pattern literal");
            };
        CoreVerificationTypes.requireSameAbsoluteType(
            literal.type(), literalType, "pattern literal representation");
        CoreVerificationTypes.requireSameAbsoluteType(expected, literal.type(), "pattern literal");
        yield PatternCoverage.Pattern.constructor(literalKey(expected, literal.value()), List.of());
      }
      case CorePattern.Null ignored ->
          throw new IllegalArgumentException("core null pattern requires nullable type");
    };
  }

  private EnumInstance enumInstance(CoreType type) {
    if (!(CoreVerificationTypes.nonNullable(type) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("core variant pattern requires enum type");
    }
    DefinitionId definition = CoreVerificationTypes.resolveExternal(user.definition());
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Enum enumDeclaration)
        || declared.arguments().size() != enumDeclaration.typeParameters().size()) {
      throw new IllegalArgumentException("core variant pattern requires enum type");
    }
    return new EnumInstance(definition, enumDeclaration, declared);
  }

  private static String literalKey(CoreType expected, Object value) {
    if (CoreVerificationTypes.isNumericLeaf(expected)) {
      return "numeric:" + expected + ":" + value;
    }
    if (expected.equals(CoreType.CODE_POINT)) return "codepoint:" + value;
    if (expected.equals(CoreType.BOOLEAN)) return "boolean:" + value;
    return "string:" + value;
  }

  private static boolean definitelyYields(CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      if (statement instanceof CoreStatement.ReturnStatement
          || statement instanceof CoreStatement.YieldStatement
          || statement instanceof CoreStatement.ThrowStatement) {
        return true;
      }
      if (statement instanceof CoreStatement.IfStatement conditional
          && definitelyYields(conditional.thenBlock())
          && definitelyYields(conditional.elseBlock())) {
        return true;
      }
      if (statement instanceof CoreStatement.TryStatement tried) {
        if (tried.finallyBlock().isPresent()
            && definitelyYields(tried.finallyBlock().orElseThrow())) {
          return true;
        }
        if (definitelyYields(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyYields(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean definitelyExits(CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      if (statement instanceof CoreStatement.ReturnStatement
          || statement instanceof CoreStatement.ThrowStatement
          || statement instanceof CoreStatement.YieldStatement) {
        return true;
      }
      if (statement instanceof CoreStatement.IfStatement conditional
          && definitelyExits(conditional.thenBlock())
          && definitelyExits(conditional.elseBlock())) {
        return true;
      }
      if (statement instanceof CoreStatement.TryStatement tried) {
        if (tried.finallyBlock().isPresent()
            && definitelyExits(tried.finallyBlock().orElseThrow())) {
          return true;
        }
        if (definitelyExits(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyExits(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  private final class CorePatternDomain implements PatternCoverage.Domain<CoreType> {
    private final DefinitionId owner;

    private CorePatternDomain(DefinitionId owner) {
      this.owner = owner;
    }

    @Override
    public List<PatternCoverage.Constructor<CoreType>> constructors(CoreType type) {
      if (type.isNullable()) {
        return List.of(
            new PatternCoverage.Constructor<>("$null", List.of()),
            new PatternCoverage.Constructor<>(
                "$value", List.of(CoreVerificationTypes.nonNullable(type))));
      }
      if (type.equals(CoreType.BOOLEAN)) {
        return List.of(
            new PatternCoverage.Constructor<>("boolean:false", List.of()),
            new PatternCoverage.Constructor<>("boolean:true", List.of()));
      }
      CoreType absoluteType = validationTypes.absolute(owner, type);
      if (!(absoluteType instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
        return List.of();
      }
      DefinitionId definition = CoreVerificationTypes.resolveExternal(user.definition());
      CoreDefinition target = program.definition(definition).orElseThrow();
      if (!(target instanceof CoreDefinition.Enum enumDeclaration)) return List.of();
      return enumDeclaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.key(),
                      variant.fields().stream()
                          .map(
                              field ->
                                  validationTypes
                                      .absolute(definition, field.type())
                                      .substitute(declared.arguments()::get))
                          .toList()))
          .toList();
    }

    @Override
    public boolean covers(CoreType input, String previous, String candidate) {
      if (previous.equals(candidate)) return true;
      var previousType = patternTypes.get(previous);
      var candidateType = patternTypes.get(candidate);
      return previousType != null
          && candidateType != null
          && validationTypes.isAssignable(previousType, candidateType);
    }

    @Override
    public PatternCoverage.Constructor<CoreType> openConstructor(CoreType type, String key) {
      return constructors(type).isEmpty()
          ? new PatternCoverage.Constructor<>(key, List.of())
          : null;
    }
  }

  private record EnumInstance(
      DefinitionId definition, CoreDefinition.Enum declaration, CoreType.Declared type) {}

  private enum ControlKind {
    LOOP,
    SWITCH
  }

  private static final class Control {
    private final ControlKind kind;
    private final CoreType yieldType;
    private LexicalLifetime referenceLifetime;

    private Control(ControlKind kind, CoreType yieldType) {
      this.kind = kind;
      this.yieldType = yieldType;
    }

    static Control loop() {
      return new Control(ControlKind.LOOP, CoreType.VOID);
    }

    static Control switched(CoreType type) {
      return new Control(ControlKind.SWITCH, type);
    }

    ControlKind kind() {
      return kind;
    }

    CoreType yieldType() {
      return yieldType;
    }

    LexicalLifetime referenceLifetime() {
      return referenceLifetime;
    }

    void mergeReferenceLifetime(LexicalLifetime lifetime) {
      referenceLifetime =
          referenceLifetime == null ? lifetime : referenceLifetime.narrowest(lifetime);
    }
  }

  private void verifyRuntimeType(
      DefinitionId owner, CoreDefinition.Callable callable, CoreRuntimeType runtimeType) {
    validationTypes.verifyRuntimeTypeTemplate(
        owner, runtimeType.template(), callable.reifiedTypeLocals().size());
    Set<Integer> parameters = new HashSet<>();
    CoreVerificationTypes.collectTypeParameters(runtimeType.template(), parameters);
    Set<Integer> captures = new HashSet<>();
    for (CoreTypeCapture capture : runtimeType.captures()) {
      if (!captures.add(capture.typeParameterIndex())) {
        throw new IllegalArgumentException("runtime type captures must be unique");
      }
      if (capture.typeParameterIndex() >= callable.reifiedTypeLocals().size()
          || capture.localIndex() != callable.reifiedTypeLocals().get(capture.typeParameterIndex())
          || CoreVerificationTypes.local(callable, capture.localIndex()).kind()
              != CoreLocal.Kind.REIFIED_TYPE) {
        throw new IllegalArgumentException("runtime type capture does not match a reified local");
      }
      referenceFlow.requireDeclared(capture.localIndex());
    }
    if (!captures.equals(parameters)) {
      throw new IllegalArgumentException("runtime type captures do not cover the template");
    }
  }
}
