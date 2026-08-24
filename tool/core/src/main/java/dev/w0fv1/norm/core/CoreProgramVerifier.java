package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.ValueCategory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CoreProgramVerifier {
  private final CoreProgram program;
  private final BuiltinCatalog builtins = BuiltinCatalog.standard();

  private CoreProgramVerifier(CoreProgram program) {
    this.program = Objects.requireNonNull(program, "program");
  }

  static void verify(CoreProgram program) {
    new CoreProgramVerifier(program).verify();
  }

  private void verify() {
    for (CoreDefinitionRecord record : program.definitions()) {
      switch (record.definition()) {
        case CoreDefinition.Callable callable -> verifyCallable(record.id(), callable);
        case CoreDefinition.Class declaration -> verifyClass(record.id(), declaration);
        case CoreDefinition.Enum declaration -> verifyEnum(declaration);
      }
    }
  }

  private void verifyCallable(DefinitionId id, CoreDefinition.Callable callable) {
    int parameterCount = callable.reifiedTypeLocals().size();
    callable.receiverType().ifPresent(type -> verifyValueType(id, type, parameterCount));
    callable.parameterTypes().forEach(type -> verifyValueType(id, type, parameterCount));
    verifyReturnType(id, callable.returnType(), parameterCount);
    callable.locals().forEach(local -> verifyLocalType(id, local, parameterCount));
    verifyBlock(id, callable, callable.body());
  }

  private void verifyClass(DefinitionId id, CoreDefinition.Class declaration) {
    declaration
        .fields()
        .forEach(field -> verifyValueType(id, field.type(), declaration.typeParameterCount()));
  }

  private static void verifyEnum(CoreDefinition.Enum declaration) {
    if (new HashSet<>(declaration.members()).size() != declaration.members().size()) {
      throw new IllegalArgumentException("enum members must be unique");
    }
  }

  private void verifyBlock(DefinitionId owner, CoreDefinition.Callable callable, CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      switch (statement) {
        case CoreStatement.LocalDeclaration local -> {
          CoreLocal target = local(callable, local.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("local declaration must bind a variable local");
          }
          verifyExpression(owner, callable, local.initializer());
          requireAssignable(
              owner, target.type(), owner, local.initializer().type(), "local initializer");
        }
        case CoreStatement.LocalAssignment assignment -> {
          CoreLocal target = local(callable, assignment.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE
              && target.kind() != CoreLocal.Kind.PARAMETER) {
            throw new IllegalArgumentException("local assignment target is not mutable storage");
          }
          verifyExpression(owner, callable, assignment.value());
          requireAssignable(
              owner, target.type(), owner, assignment.value().type(), "local assignment");
        }
        case CoreStatement.FieldAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          verifyExpression(owner, callable, assignment.value());
          requireNonNullableReceiver(owner, assignment.receiver().type(), "field assignment");
          CoreType fieldType =
              instantiatedFieldType(owner, assignment.receiver().type(), assignment.field());
          requireAssignable(owner, fieldType, owner, assignment.value().type(), "field assignment");
        }
        case CoreStatement.IntrinsicAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          assignment.index().ifPresent(value -> verifyExpression(owner, callable, value));
          verifyExpression(owner, callable, assignment.value());
          verifyIntrinsicAssignment(owner, assignment);
        }
        case CoreStatement.ExpressionStatement expression ->
            verifyExpression(owner, callable, expression.expression());
        case CoreStatement.IfStatement conditional -> {
          verifyExpression(owner, callable, conditional.condition());
          requireSameType(
              owner, CoreType.BOOLEAN, owner, conditional.condition().type(), "if condition");
          verifyBlock(owner, callable, conditional.thenBlock());
          verifyBlock(owner, callable, conditional.elseBlock());
        }
        case CoreStatement.ConditionalForStatement loop -> {
          verifyExpression(owner, callable, loop.condition());
          requireSameType(
              owner, CoreType.BOOLEAN, owner, loop.condition().type(), "loop condition");
          verifyBlock(owner, callable, loop.body());
        }
        case CoreStatement.ForStatement loop -> {
          if (local(callable, loop.iteratorLocal()).kind() != CoreLocal.Kind.ITERATOR
              || local(callable, loop.variableLocal()).kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("for loop local ABI is invalid");
          }
          verifyExpression(owner, callable, loop.iterable());
          verifyIteration(owner, callable, loop);
          verifyBlock(owner, callable, loop.body());
        }
        case CoreStatement.ReturnStatement returned -> {
          if (returned.value().isEmpty()) {
            requireSameType(owner, CoreType.VOID, owner, callable.returnType(), "return");
          } else {
            CoreExpression value = returned.value().orElseThrow();
            verifyExpression(owner, callable, value);
            requireAssignable(owner, callable.returnType(), owner, value.type(), "return");
          }
        }
        case CoreStatement.BreakStatement ignored -> {}
        case CoreStatement.ContinueStatement ignored -> {}
      }
    }
  }

  private void verifyExpression(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression expression) {
    if (expression instanceof CoreExpression.Call
        || expression instanceof CoreExpression.Intrinsic) {
      verifyReturnType(owner, expression.type(), callable.reifiedTypeLocals().size());
    } else {
      verifyValueType(owner, expression.type(), callable.reifiedTypeLocals().size());
    }
    switch (expression) {
      case CoreExpression.Literal literal -> verifyLiteral(owner, literal);
      case CoreExpression.NullLiteral literal -> {
        if (!literal.type().isNullable()) {
          throw new IllegalArgumentException("null literal requires a nullable core type");
        }
      }
      case CoreExpression.ArrayLiteral array -> {
        verifyRuntimeType(owner, callable, array.runtimeType());
        requireSameType(
            owner, array.runtimeType().template(), owner, array.type(), "array runtime type");
        array.elements().forEach(value -> verifyExpression(owner, callable, value));
        CoreType elementType = declaredArgument(owner, array.type(), "std.core.Array", 0);
        array
            .elements()
            .forEach(
                value ->
                    requireAssignable(owner, elementType, owner, value.type(), "array element"));
      }
      case CoreExpression.LocalRead read -> {
        CoreLocal local = local(callable, read.localIndex());
        requireAssignable(owner, local.type(), owner, read.type(), "local read");
      }
      case CoreExpression.FieldRead read -> {
        verifyExpression(owner, callable, read.receiver());
        verifyReceiverSafety(owner, read.receiver().type(), read.nullSafe(), "field read");
        CoreType fieldType = instantiatedFieldType(owner, read.receiver().type(), read.field());
        requireSameType(
            owner,
            safeResult(fieldType, read.nullSafe(), read.receiver().type()),
            owner,
            read.type(),
            "field read");
      }
      case CoreExpression.EnumMember member -> verifyEnumMember(owner, member);
      case CoreExpression.Unary unary -> {
        verifyExpression(owner, callable, unary.operand());
        verifyUnary(owner, unary);
      }
      case CoreExpression.Binary binary -> {
        verifyExpression(owner, callable, binary.left());
        verifyExpression(owner, callable, binary.right());
        verifyBinary(owner, binary);
      }
      case CoreExpression.Index index -> {
        verifyExpression(owner, callable, index.receiver());
        verifyExpression(owner, callable, index.index());
        verifyIndex(owner, index);
      }
      case CoreExpression.CopyObject copied -> {
        verifyExpression(owner, callable, copied.receiver());
        verifyReceiverSafety(owner, copied.receiver().type(), copied.nullSafe(), "copy");
        CoreType receiver = nonNullable(absolute(owner, copied.receiver().type()));
        if (!(receiver instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.IDENTITY) {
          throw new IllegalArgumentException("copy requires an identity receiver");
        }
        requireSameType(
            owner,
            safeResult(copied.receiver().type(), copied.nullSafe(), copied.receiver().type()),
            owner,
            copied.type(),
            "copy result");
      }
      case CoreExpression.Call call -> verifyCall(owner, callable, call);
      case CoreExpression.Construct construct -> verifyConstruct(owner, callable, construct);
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(value -> verifyExpression(owner, callable, value));
        intrinsic
            .arguments()
            .forEach(argument -> verifyExpression(owner, callable, argument.value()));
        intrinsic.runtimeType().ifPresent(type -> verifyRuntimeType(owner, callable, type));
        verifyIntrinsic(owner, intrinsic);
      }
    }
  }

  private void verifyLiteral(DefinitionId owner, CoreExpression.Literal literal) {
    CoreType expected =
        switch (literal.value()) {
          case Long ignored -> CoreType.INTEGER;
          case Integer ignored -> CoreType.CODE_POINT;
          case Boolean ignored -> CoreType.BOOLEAN;
          case String ignored -> CoreType.STRING;
          default -> throw new IllegalArgumentException("unsupported core literal value");
        };
    requireSameType(owner, expected, owner, literal.type(), "literal");
  }

  private void verifyUnary(DefinitionId owner, CoreExpression.Unary unary) {
    CoreType expected =
        unary.operator() == CoreUnaryOperator.NOT ? CoreType.BOOLEAN : CoreType.INTEGER;
    requireSameType(owner, expected, owner, unary.operand().type(), "unary operand");
    requireSameType(owner, expected, owner, unary.type(), "unary result");
  }

  private void verifyBinary(DefinitionId owner, CoreExpression.Binary binary) {
    CoreType left = absolute(owner, binary.left().type());
    CoreType right = absolute(owner, binary.right().type());
    CoreType result = absolute(owner, binary.type());
    switch (binary.operator()) {
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER -> {
        requireSameType(CoreType.INTEGER, left, "binary left operand");
        requireSameType(CoreType.INTEGER, right, "binary right operand");
        requireSameType(CoreType.INTEGER, result, "binary result");
      }
      case STRING_CONCAT -> {
        requireSameType(CoreType.STRING, left, "binary left operand");
        requireSameType(CoreType.STRING, right, "binary right operand");
        requireSameType(CoreType.STRING, result, "binary result");
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        requireSameType(CoreType.INTEGER, left, "comparison left operand");
        requireSameType(CoreType.INTEGER, right, "comparison right operand");
        requireSameType(CoreType.BOOLEAN, result, "comparison result");
      }
      case AND, OR -> {
        requireSameType(CoreType.BOOLEAN, left, "logical left operand");
        requireSameType(CoreType.BOOLEAN, right, "logical right operand");
        requireSameType(CoreType.BOOLEAN, result, "logical result");
      }
      case EQUAL, NOT_EQUAL -> {
        if (!isAssignable(left, right) && !isAssignable(right, left)) {
          throw new IllegalArgumentException("equality operands have incompatible types");
        }
        requireSameType(CoreType.BOOLEAN, result, "equality result");
      }
      case COALESCE -> {
        if (!mayContainNull(left)) {
          throw new IllegalArgumentException("coalesce left operand cannot contain null");
        }
        CoreType expected = nonNullable(left);
        requireAssignable(expected, right, "coalesce right operand");
        requireSameType(expected, result, "coalesce result");
      }
    }
  }

  private void verifyIndex(DefinitionId owner, CoreExpression.Index index) {
    requireNonNullableReceiver(owner, index.receiver().type(), "index read");
    boolean valid =
        builtins.indexCandidates(index.readIntrinsic()).stream()
            .anyMatch(candidate -> matchesIndex(owner, index, candidate));
    if (!valid)
      throw new IllegalArgumentException("index expression does not match its builtin ABI");
  }

  private boolean matchesIndex(
      DefinitionId owner, CoreExpression.Index index, BuiltinCatalog.IndexCandidate candidate) {
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        nonNullable(absolute(owner, index.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (!candidate.writeIntrinsic().equals(index.writeIntrinsic())) return false;
    CoreType expectedIndex = instantiate(candidate.index(), substitutions);
    CoreType expectedResult = instantiate(candidate.result(), substitutions);
    return isAssignable(expectedIndex, absolute(owner, index.index().type()))
        && expectedResult.equals(absolute(owner, index.type()));
  }

  private void verifyIntrinsic(DefinitionId owner, CoreExpression.Intrinsic intrinsic) {
    boolean valid =
        builtins.intrinsicCandidates(intrinsic.intrinsic()).stream()
            .anyMatch(candidate -> matchesIntrinsic(owner, intrinsic, candidate));
    if (!valid) {
      throw new IllegalArgumentException("intrinsic expression does not match its builtin ABI");
    }
  }

  private boolean matchesIntrinsic(
      DefinitionId owner,
      CoreExpression.Intrinsic intrinsic,
      BuiltinCatalog.IntrinsicCandidate candidate) {
    if (candidate.receiver().isPresent() != intrinsic.receiver().isPresent()
        || candidate.runtimeType() != intrinsic.runtimeType().isPresent()
        || intrinsic.nullSafe() && intrinsic.receiver().isEmpty()
        || !denseArguments(intrinsic.arguments(), candidate.parameters().size())) {
      return false;
    }
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (candidate.receiver().isPresent()) {
      CoreType actualReceiver = absolute(owner, intrinsic.receiver().orElseThrow().type());
      if (!intrinsic.nullSafe() && actualReceiver.isNullable()) return false;
      if (!bindPattern(
          nonNullable(actualReceiver), candidate.receiver().orElseThrow(), substitutions)) {
        return false;
      }
    }
    if (candidate.runtimeType()) {
      CoreType runtimeTemplate = absolute(owner, intrinsic.runtimeType().orElseThrow().template());
      if (!bindPattern(runtimeTemplate, candidate.result(), substitutions)) return false;
    }
    for (CoreArgument argument : intrinsic.arguments()) {
      SemanticType parameter = candidate.parameters().get(argument.parameterIndex()).type();
      CoreType expected = instantiate(parameter, substitutions);
      if (!expected.equals(CoreType.DYNAMIC)
          && !isAssignable(expected, absolute(owner, argument.value().type()))) {
        return false;
      }
    }
    CoreType result = instantiate(candidate.result(), substitutions);
    CoreType receiver =
        intrinsic
            .receiver()
            .map(CoreExpression::type)
            .map(type -> absolute(owner, type))
            .orElse(CoreType.DYNAMIC);
    result = safeResult(result, intrinsic.nullSafe(), receiver);
    return result.equals(absolute(owner, intrinsic.type()));
  }

  private void verifyIntrinsicAssignment(
      DefinitionId owner, CoreStatement.IntrinsicAssignment assignment) {
    requireNonNullableReceiver(owner, assignment.receiver().type(), "intrinsic assignment");
    boolean valid =
        builtins.writeCandidates(assignment.intrinsic()).stream()
            .anyMatch(candidate -> matchesWrite(owner, assignment, candidate));
    if (!valid) {
      throw new IllegalArgumentException("intrinsic assignment does not match its builtin ABI");
    }
  }

  private boolean matchesWrite(
      DefinitionId owner,
      CoreStatement.IntrinsicAssignment assignment,
      BuiltinCatalog.WriteCandidate candidate) {
    if (candidate.index().isPresent() != assignment.index().isPresent()) return false;
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        nonNullable(absolute(owner, assignment.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (candidate.index().isPresent()) {
      CoreType expectedIndex = instantiate(candidate.index().orElseThrow(), substitutions);
      if (!isAssignable(expectedIndex, absolute(owner, assignment.index().orElseThrow().type()))) {
        return false;
      }
    }
    CoreType expectedValue = instantiate(candidate.value(), substitutions);
    return isAssignable(expectedValue, absolute(owner, assignment.value().type()));
  }

  private void verifyIteration(
      DefinitionId owner, CoreDefinition.Callable callable, CoreStatement.ForStatement loop) {
    requireNonNullableReceiver(owner, loop.iterable().type(), "iteration");
    CoreType variable = absolute(owner, local(callable, loop.variableLocal()).type());
    boolean valid =
        builtins.iterationCandidates(loop.iterationIntrinsic()).stream()
            .anyMatch(
                candidate -> {
                  Map<String, CoreType> substitutions = new LinkedHashMap<>();
                  return bindPattern(
                          nonNullable(absolute(owner, loop.iterable().type())),
                          candidate.receiver(),
                          substitutions)
                      && instantiate(candidate.element(), substitutions).equals(variable);
                });
    if (!valid) throw new IllegalArgumentException("iteration does not match its builtin ABI");
  }

  private boolean bindPattern(
      CoreType actual, SemanticType pattern, Map<String, CoreType> substitutions) {
    if (pattern.kind() == SemanticType.Kind.ERROR) return true;
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      CoreType captured = pattern.isNullable() ? nonNullable(actual) : actual;
      CoreType previous = substitutions.putIfAbsent(pattern.identity(), captured);
      return previous == null || previous.equals(captured);
    }
    if (pattern.kind() == SemanticType.Kind.VOID) return actual.equals(CoreType.VOID);
    if (pattern.kind() == SemanticType.Kind.NULL) return actual.equals(CoreType.NULL);
    if (!(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)
        || !builtin.id().value().equals(pattern.identity())
        || declared.arguments().size() != pattern.arguments().size()
        || declared.category() != category(pattern.category())
        || declared.isNullable() != pattern.isNullable()) {
      return false;
    }
    for (int index = 0; index < pattern.arguments().size(); index++) {
      if (!bindPattern(
          declared.arguments().get(index), pattern.arguments().get(index), substitutions)) {
        return false;
      }
    }
    return true;
  }

  private CoreType instantiate(SemanticType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType type = substitutions.get(pattern.identity());
        if (type == null)
          throw new IllegalArgumentException("builtin type parameter is unresolved");
        yield pattern.isNullable() ? type.asNullable() : type;
      }
      case DECLARED ->
          new CoreType.Declared(
              new CoreTypeConstructor.Builtin(new BuiltinTypeId(pattern.identity())),
              pattern.arguments().stream()
                  .map(argument -> instantiate(argument, substitutions))
                  .toList(),
              category(pattern.category()),
              pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL);
      case VOID -> CoreType.VOID;
      case NULL -> CoreType.NULL;
      case ERROR -> CoreType.DYNAMIC;
    };
  }

  private static CoreValueCategory category(ValueCategory category) {
    return switch (category) {
      case VALUE -> CoreValueCategory.VALUE;
      case IDENTITY -> CoreValueCategory.IDENTITY;
      case DYNAMIC -> CoreValueCategory.DYNAMIC;
      case VOID -> CoreValueCategory.VOID;
    };
  }

  private void verifyReceiverSafety(
      DefinitionId owner, CoreType receiver, boolean nullSafe, String subject) {
    CoreType actual = absolute(owner, receiver);
    if (!nullSafe && actual.isNullable()) {
      throw new IllegalArgumentException(subject + " requires null-safe access");
    }
  }

  private void requireNonNullableReceiver(DefinitionId owner, CoreType receiver, String subject) {
    if (absolute(owner, receiver).isNullable()) {
      throw new IllegalArgumentException(subject + " requires a non-null receiver");
    }
  }

  private void verifyCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Call call) {
    DefinitionId targetId = resolve(owner, call.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Callable target)) {
      throw new IllegalArgumentException("call target is not callable");
    }
    call.receiver().ifPresent(value -> verifyExpression(owner, caller, value));
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    if (target.hasReceiver() != call.receiver().isPresent()) {
      throw new IllegalArgumentException("call receiver does not match the target ABI");
    }
    if (call.nullSafe() && call.receiver().isEmpty()) {
      throw new IllegalArgumentException("null-safe call requires a receiver");
    }
    List<CoreType> substitutions = new ArrayList<>();
    if (target.hasReceiver()) {
      CoreType actualReceiver = absolute(owner, call.receiver().orElseThrow().type());
      CoreType nonNullableReceiver = nonNullable(actualReceiver);
      if (!(nonNullableReceiver instanceof CoreType.Declared declared)) {
        throw new IllegalArgumentException("method receiver is not a declared type");
      }
      substitutions.addAll(declared.arguments());
      CoreType expectedReceiver =
          absolute(targetId, target.receiverType().orElseThrow()).substitute(substitutions::get);
      if (!expectedReceiver.equals(nonNullableReceiver)) {
        throw new IllegalArgumentException("method receiver type does not match the target ABI");
      }
      if (!call.nullSafe() && actualReceiver.isNullable()) {
        throw new IllegalArgumentException("nullable receiver requires a null-safe call");
      }
    }
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> absolute(owner, type))
        .forEach(substitutions::add);
    if (substitutions.size() != target.reifiedTypeLocals().size()) {
      throw new IllegalArgumentException("call reified arguments do not match the target ABI");
    }
    verifyDenseArguments(call.arguments(), target.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          absolute(targetId, target.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      requireAssignable(expected, absolute(owner, argument.value().type()), "call argument");
    }
    CoreType result = absolute(targetId, target.returnType()).substitute(substitutions::get);
    CoreType receiverType = call.receiver().map(CoreExpression::type).orElse(CoreType.DYNAMIC);
    result = safeResult(result, call.nullSafe(), receiverType);
    requireSameType(result, absolute(owner, call.type()), "call result");
  }

  private void verifyConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Construct construct) {
    DefinitionId targetId = resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Class target)) {
      throw new IllegalArgumentException("construct target is not a class");
    }
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType constructedType = absolute(owner, construct.type());
    requireSameType(
        constructedType,
        absolute(owner, construct.runtimeType().template()),
        "construct runtime type");
    if (!(nonNullable(constructedType) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameterCount()) {
      throw new IllegalArgumentException("constructed type does not match the class ABI");
    }
    verifyDenseArguments(construct.arguments(), target.fields().size());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          absolute(targetId, target.fields().get(argument.parameterIndex()).type())
              .substitute(declared.arguments()::get);
      requireAssignable(expected, absolute(owner, argument.value().type()), "constructor argument");
    }
  }

  private void verifyEnumMember(DefinitionId owner, CoreExpression.EnumMember member) {
    DefinitionId targetId = resolve(owner, member.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Enum target)
        || member.memberOrdinal() >= target.members().size()) {
      throw new IllegalArgumentException("enum member target or ordinal is invalid");
    }
    CoreType type = absolute(owner, member.type());
    if (!(nonNullable(type) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || !declared.arguments().isEmpty()) {
      throw new IllegalArgumentException("enum member type does not match its target");
    }
  }

  private CoreType instantiatedFieldType(
      DefinitionId owner, CoreType receiverType, CoreFieldReference reference) {
    DefinitionId targetId = resolve(owner, reference.owner());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Class target)
        || reference.ordinal() >= target.fields().size()) {
      throw new IllegalArgumentException("field owner or ordinal is invalid");
    }
    CoreType receiver = nonNullable(absolute(owner, receiverType));
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameterCount()) {
      throw new IllegalArgumentException("field receiver does not match its owner");
    }
    return absolute(targetId, target.fields().get(reference.ordinal()).type())
        .substitute(declared.arguments()::get);
  }

  private void verifyRuntimeType(
      DefinitionId owner, CoreDefinition.Callable callable, CoreRuntimeType runtimeType) {
    verifyRuntimeTypeTemplate(owner, runtimeType.template(), callable.reifiedTypeLocals().size());
    Set<Integer> parameters = new HashSet<>();
    collectTypeParameters(runtimeType.template(), parameters);
    Set<Integer> captures = new HashSet<>();
    for (CoreTypeCapture capture : runtimeType.captures()) {
      if (!captures.add(capture.typeParameterIndex())) {
        throw new IllegalArgumentException("runtime type captures must be unique");
      }
      if (capture.typeParameterIndex() >= callable.reifiedTypeLocals().size()
          || capture.localIndex() != callable.reifiedTypeLocals().get(capture.typeParameterIndex())
          || local(callable, capture.localIndex()).kind() != CoreLocal.Kind.REIFIED_TYPE) {
        throw new IllegalArgumentException("runtime type capture does not match a reified local");
      }
    }
    if (!captures.equals(parameters)) {
      throw new IllegalArgumentException("runtime type captures do not cover the template");
    }
  }

  private void verifyValueType(DefinitionId owner, CoreType type, int parameterCount) {
    verifyInhabitedType(owner, type, parameterCount, "core value ABI");
  }

  private void verifyReturnType(DefinitionId owner, CoreType type, int parameterCount) {
    if (type.equals(CoreType.VOID)) return;
    verifyInhabitedType(owner, type, parameterCount, "core return ABI");
  }

  private void verifyRuntimeTypeTemplate(DefinitionId owner, CoreType type, int parameterCount) {
    verifyInhabitedType(owner, type, parameterCount, "runtime type template");
  }

  private void verifyLocalType(DefinitionId owner, CoreLocal local, int parameterCount) {
    if (local.kind() == CoreLocal.Kind.REIFIED_TYPE || local.kind() == CoreLocal.Kind.ITERATOR) {
      if (!local.type().equals(CoreType.DYNAMIC)) {
        throw new IllegalArgumentException("internal runtime locals require dynamic type");
      }
      return;
    }
    verifyValueType(owner, local.type(), parameterCount);
  }

  private void verifyInhabitedType(
      DefinitionId owner, CoreType type, int parameterCount, String subject) {
    switch (type) {
      case CoreType.Parameter parameter -> {
        if (parameter.index() >= parameterCount) {
          throw new IllegalArgumentException("core type parameter is outside its ABI");
        }
      }
      case CoreType.Declared declared -> {
        declared
            .arguments()
            .forEach(argument -> verifyInhabitedType(owner, argument, parameterCount, subject));
        switch (declared.constructor()) {
          case CoreTypeConstructor.Builtin builtin -> verifyBuiltinType(builtin, declared);
          case CoreTypeConstructor.User user -> verifyUserType(owner, user, declared);
        }
      }
      case CoreType.Special ignored ->
          throw new IllegalArgumentException(subject + " requires an inhabitable type");
    }
  }

  private void verifyBuiltinType(
      CoreTypeConstructor.Builtin constructor, CoreType.Declared declared) {
    String identity = constructor.id().value();
    String prefix = "std.core.";
    if (!identity.startsWith(prefix)) {
      throw new IllegalArgumentException("unknown builtin core type " + identity);
    }
    var definition =
        builtins
            .type(identity.substring(prefix.length()))
            .orElseThrow(
                () -> new IllegalArgumentException("unknown builtin core type " + identity));
    if (definition.arity() != declared.arguments().size()) {
      throw new IllegalArgumentException("builtin core type has the wrong arity");
    }
    CoreValueCategory expected =
        definition.symbol().type().category() == ValueCategory.IDENTITY
            ? CoreValueCategory.IDENTITY
            : CoreValueCategory.VALUE;
    if (declared.category() != expected) {
      throw new IllegalArgumentException("builtin core type has the wrong value category");
    }
  }

  private void verifyUserType(
      DefinitionId owner, CoreTypeConstructor.User constructor, CoreType.Declared declared) {
    DefinitionId targetId = resolve(owner, constructor.definition());
    CoreDefinition target = program.definition(targetId).orElseThrow();
    int arity;
    CoreValueCategory category;
    if (target instanceof CoreDefinition.Class declaration) {
      arity = declaration.typeParameterCount();
      category = CoreValueCategory.IDENTITY;
    } else if (target instanceof CoreDefinition.Enum) {
      arity = 0;
      category = CoreValueCategory.VALUE;
    } else {
      throw new IllegalArgumentException("declared core type target is not nominal");
    }
    if (declared.arguments().size() != arity || declared.category() != category) {
      throw new IllegalArgumentException(
          "declared core type does not match its nominal ABI: target="
              + targetId
              + ", expectedArity="
              + arity
              + ", actualArity="
              + declared.arguments().size()
              + ", expectedCategory="
              + category
              + ", actualCategory="
              + declared.category());
    }
  }

  private CoreType declaredArgument(
      DefinitionId owner, CoreType type, String builtinIdentity, int index) {
    CoreType absolute = nonNullable(absolute(owner, type));
    if (!(absolute instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)
        || !builtin.id().value().equals(builtinIdentity)
        || index >= declared.arguments().size()) {
      throw new IllegalArgumentException("core expression has an incompatible declared type");
    }
    return declared.arguments().get(index);
  }

  private static void collectTypeParameters(CoreType type, Set<Integer> result) {
    switch (type) {
      case CoreType.Parameter parameter -> result.add(parameter.index());
      case CoreType.Declared declared ->
          declared.arguments().forEach(argument -> collectTypeParameters(argument, result));
      case CoreType.Special ignored -> {}
    }
  }

  private static void verifyDenseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (!denseArguments(arguments, parameterCount)) {
      throw new IllegalArgumentException("core arguments do not match the target arity");
    }
  }

  private static boolean denseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (arguments.size() != parameterCount) return false;
    boolean[] supplied = new boolean[parameterCount];
    for (CoreArgument argument : arguments) {
      if (argument.parameterIndex() >= parameterCount || supplied[argument.parameterIndex()]) {
        return false;
      }
      supplied[argument.parameterIndex()] = true;
    }
    return true;
  }

  private static CoreLocal local(CoreDefinition.Callable callable, int index) {
    if (index < 0 || index >= callable.locals().size()) {
      throw new IllegalArgumentException("core local use is outside the local table");
    }
    return callable.locals().get(index);
  }

  private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("core program contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  private static DefinitionId resolveExternal(CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference.External external)) {
      throw new IllegalArgumentException("absolute core type contains a relative reference");
    }
    return external.definition();
  }

  private CoreType absolute(DefinitionId owner, CoreType type) {
    return CoreTypes.absolute(type, owner, program);
  }

  private void requireAssignable(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    requireAssignable(absolute(expectedOwner, expected), absolute(actualOwner, actual), subject);
  }

  private static void requireAssignable(CoreType expected, CoreType actual, String subject) {
    if (!isAssignable(expected, actual)) {
      throw new IllegalArgumentException(subject + " type does not match its ABI");
    }
  }

  private void requireSameType(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    requireSameType(absolute(expectedOwner, expected), absolute(actualOwner, actual), subject);
  }

  private static void requireSameType(CoreType expected, CoreType actual, String subject) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(subject + " type does not match its ABI");
    }
  }

  private static boolean isAssignable(CoreType expected, CoreType actual) {
    if (expected.equals(CoreType.DYNAMIC) || actual.equals(CoreType.DYNAMIC)) return true;
    if (actual.equals(CoreType.NULL)) return expected.isNullable();
    if (expected.equals(CoreType.NULL)) return actual.equals(CoreType.NULL);
    if (!nonNullable(expected).equals(nonNullable(actual))) return false;
    return expected.isNullable() || !actual.isNullable();
  }

  private static CoreType safeResult(CoreType result, boolean nullSafe, CoreType receiver) {
    return nullSafe && receiver.isNullable() ? result.asNullable() : result;
  }

  private static boolean mayContainNull(CoreType type) {
    return type.isNullable() || type instanceof CoreType.Parameter;
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          declared.nullability() == CoreNullability.NON_NULL
              ? declared
              : new CoreType.Declared(
                  declared.constructor(),
                  declared.arguments(),
                  declared.category(),
                  CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          parameter.nullability() == CoreNullability.NON_NULL
              ? parameter
              : new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Special special -> special;
    };
  }
}
