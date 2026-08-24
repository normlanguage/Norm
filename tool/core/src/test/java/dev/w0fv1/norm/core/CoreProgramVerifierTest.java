package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreProgramVerifierTest {
  @Test
  void rejectsSpecialTypesInValueAbis() {
    for (CoreType special : List.of(CoreType.VOID, CoreType.NULL, CoreType.DYNAMIC)) {
      CoreDefinitionGroup field =
          group(classDefinition("Box", 0, List.of(new CoreField(0, special))));
      CoreDefinitionGroup parameter =
          group(
              new CoreDefinition.Callable(
                  Optional.empty(),
                  List.of(special),
                  List.of(0),
                  List.of(),
                  CoreType.VOID,
                  List.of(new CoreLocal(0, special, CoreLocal.Kind.PARAMETER)),
                  new CoreBlock(0, List.of())));
      CoreDefinitionGroup local =
          group(
              new CoreDefinition.Callable(
                  Optional.empty(),
                  List.of(),
                  List.of(),
                  List.of(),
                  CoreType.VOID,
                  List.of(new CoreLocal(0, special, CoreLocal.Kind.VARIABLE)),
                  new CoreBlock(0, List.of())));

      assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(field)));
      assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(parameter)));
      assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(local)));
    }
  }

  @Test
  void permitsDynamicOnlyForInternalRuntimeLocals() {
    CoreDefinitionGroup valid =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(0),
                CoreType.VOID,
                List.of(
                    new CoreLocal(0, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE),
                    new CoreLocal(1, CoreType.DYNAMIC, CoreLocal.Kind.ITERATOR)),
                new CoreBlock(0, List.of())));

    assertDoesNotThrow(() -> new CoreProgram(List.of(valid)));
  }

  @Test
  void restrictsSpecialCallableReturnTypesToVoid() {
    assertDoesNotThrow(() -> new CoreProgram(List.of(group(emptyFunction(CoreType.VOID)))));
    assertDoesNotThrow(() -> new CoreProgram(List.of(group(emptyFunction(CoreType.INTEGER)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(emptyFunction(CoreType.NULL)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(emptyFunction(CoreType.DYNAMIC)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(emptyFunction(arrayType(CoreType.VOID))))));
  }

  @Test
  void rejectsSpecialRuntimeTypeTemplatesAtEveryDepth() {
    for (CoreType special : List.of(CoreType.VOID, CoreType.NULL, CoreType.DYNAMIC)) {
      assertThrows(IllegalArgumentException.class, () -> programWithReifiedArgument(special));
      assertThrows(
          IllegalArgumentException.class, () -> programWithReifiedArgument(arrayType(special)));
    }
  }

  @Test
  void rejectsCallsWhoseArgumentsDoNotMatchTheTargetAbi() {
    CoreDefinitionGroup target =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(CoreType.INTEGER),
                List.of(0),
                List.of(),
                CoreType.VOID,
                List.of(new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.PARAMETER)),
                new CoreBlock(0, List.of())));
    CoreExpression.Call call =
        new CoreExpression.Call(
            2,
            new DefinitionReference.External(target.definitionId(0)),
            Optional.empty(),
            List.of(),
            List.of(),
            false,
            CoreType.VOID);
    CoreDefinitionGroup caller = group(function(call));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(target, caller)));
  }

  @Test
  void rejectsMethodCallsWithoutTheDeclaredReceiver() {
    CoreDefinitionGroup owner = group(classDefinition("Box", 0, List.of()));
    CoreType receiver = userType(owner.definitionId(0), List.of());
    CoreDefinitionGroup method =
        group(
            new CoreDefinition.Callable(
                Optional.of(receiver),
                List.of(),
                List.of(),
                List.of(),
                CoreType.VOID,
                List.of(new CoreLocal(0, receiver, CoreLocal.Kind.RECEIVER)),
                new CoreBlock(0, List.of())));
    CoreExpression.Call call =
        new CoreExpression.Call(
            2,
            new DefinitionReference.External(method.definitionId(0)),
            Optional.empty(),
            List.of(),
            List.of(),
            false,
            CoreType.VOID);
    CoreDefinitionGroup caller = group(function(call));

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(owner, method, caller)));
  }

  @Test
  void rejectsIncompleteRuntimeTypeCaptures() {
    CoreType element = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    CoreType array =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Array")),
            List.of(element),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    CoreExpression.ArrayLiteral literal =
        new CoreExpression.ArrayLiteral(2, List.of(), new CoreRuntimeType(array, List.of()), array);
    CoreDefinitionGroup group =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(0),
                CoreType.VOID,
                List.of(new CoreLocal(0, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE)),
                new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, literal)))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group)));
  }

  @Test
  void rejectsConstructorArgumentsWithTheWrongType() {
    CoreDefinitionGroup target =
        group(classDefinition("Box", 0, List.of(new CoreField(0, CoreType.INTEGER))));
    CoreType box = userType(target.definitionId(0), List.of());
    CoreExpression.Construct construct =
        new CoreExpression.Construct(
            2,
            new DefinitionReference.External(target.definitionId(0)),
            new CoreRuntimeType(box, List.of()),
            List.of(new CoreArgument(new CoreExpression.Literal(3, "wrong", CoreType.STRING), 0)),
            box);
    CoreDefinitionGroup caller = group(function(construct));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(target, caller)));
  }

  @Test
  void rejectsInvalidFieldAndEnumTargets() {
    CoreDefinitionGroup owner =
        group(classDefinition("Box", 0, List.of(new CoreField(0, CoreType.INTEGER))));
    CoreType box = userType(owner.definitionId(0), List.of());
    CoreExpression.FieldRead read =
        new CoreExpression.FieldRead(
            2,
            new CoreExpression.LocalRead(3, 0, box),
            new CoreFieldReference(new DefinitionReference.External(owner.definitionId(0)), 1),
            false,
            CoreType.INTEGER);
    CoreDefinitionGroup fieldCaller =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                CoreType.VOID,
                List.of(new CoreLocal(0, box, CoreLocal.Kind.VARIABLE)),
                new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, read)))));
    CoreDefinitionGroup enumGroup =
        group(new CoreDefinition.Enum(nominal("Choice"), List.of("ONLY")));
    CoreType choice = userType(enumGroup.definitionId(0), List.of());
    CoreExpression.EnumMember member =
        new CoreExpression.EnumMember(
            2, new DefinitionReference.External(enumGroup.definitionId(0)), 1, choice);
    CoreDefinitionGroup enumCaller = group(function(member));

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(owner, fieldCaller)));
    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(enumGroup, enumCaller)));
  }

  @Test
  void rejectsTypeParametersOutsideTheirDefinitionAbi() {
    CoreDefinitionGroup invalid =
        group(
            classDefinition(
                "Box",
                1,
                List.of(new CoreField(0, new CoreType.Parameter(1, CoreNullability.NON_NULL)))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(invalid)));
  }

  @Test
  void rejectsLiteralValuesWhoseTypeDoesNotMatch() {
    CoreExpression literal = new CoreExpression.Literal(2, "not an integer", CoreType.INTEGER);

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(group(function(literal)))));
  }

  @Test
  void rejectsUnsafeNullableFieldReadsAndCopies() {
    CoreDefinitionGroup owner =
        group(classDefinition("Box", 0, List.of(new CoreField(0, CoreType.INTEGER))));
    CoreType box = userType(owner.definitionId(0), List.of());
    CoreType nullableBox = box.asNullable();
    CoreExpression receiver = new CoreExpression.LocalRead(3, 0, nullableBox);
    CoreExpression.FieldRead field =
        new CoreExpression.FieldRead(
            2,
            receiver,
            new CoreFieldReference(new DefinitionReference.External(owner.definitionId(0)), 0),
            false,
            CoreType.INTEGER);
    CoreExpression.CopyObject copy = new CoreExpression.CopyObject(2, receiver, false, box);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(owner, group(functionWithLocal(field, nullableBox)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(owner, group(functionWithLocal(copy, nullableBox)))));
  }

  @Test
  void rejectsUnaryAndBinaryExpressionsWithInvalidAbis() {
    CoreExpression integer = new CoreExpression.Literal(3, 1L, CoreType.INTEGER);
    CoreExpression wrongUnary =
        new CoreExpression.Unary(2, CoreUnaryOperator.NOT, integer, CoreType.BOOLEAN);
    CoreExpression wrongBinary =
        new CoreExpression.Binary(
            2,
            integer,
            CoreBinaryOperator.ADD,
            new CoreExpression.Literal(4, "one", CoreType.STRING),
            CoreType.INTEGER);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(function(wrongUnary)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(function(wrongBinary)))));
  }

  @Test
  void rejectsIndexAndIntrinsicExpressionsOutsideBuiltinContracts() {
    CoreType list = builtinType("List", List.of(CoreType.INTEGER));
    CoreExpression receiver = new CoreExpression.LocalRead(3, 0, list);
    CoreExpression wrongIndex =
        new CoreExpression.Index(
            2,
            receiver,
            new CoreExpression.Literal(4, "zero", CoreType.STRING),
            IntrinsicId.LIST_INDEX_READ,
            Optional.of(IntrinsicId.LIST_INDEX_WRITE),
            CoreType.INTEGER);
    CoreExpression wrongIntrinsic =
        new CoreExpression.Intrinsic(
            2,
            IntrinsicId.SIZE,
            Optional.empty(),
            List.of(),
            Optional.empty(),
            false,
            CoreType.INTEGER);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(functionWithLocal(wrongIndex, list)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(function(wrongIntrinsic)))));
  }

  @Test
  void rejectsIntrinsicWritesAndIterationOutsideBuiltinContracts() {
    CoreType list = builtinType("List", List.of(CoreType.INTEGER));
    CoreExpression receiver = new CoreExpression.LocalRead(2, 0, list);
    CoreStatement.IntrinsicAssignment write =
        new CoreStatement.IntrinsicAssignment(
            1,
            IntrinsicId.LIST_INDEX_WRITE,
            receiver,
            Optional.of(new CoreExpression.Literal(3, "zero", CoreType.STRING)),
            new CoreExpression.Literal(4, 1L, CoreType.INTEGER));
    CoreStatement.ForStatement iteration =
        new CoreStatement.ForStatement(
            1, 1, 2, receiver, new CoreBlock(5, List.of()), IntrinsicId.ARRAY_ITERATOR);
    List<CoreLocal> locals =
        List.of(
            new CoreLocal(0, list, CoreLocal.Kind.VARIABLE),
            new CoreLocal(1, CoreType.DYNAMIC, CoreLocal.Kind.ITERATOR),
            new CoreLocal(2, CoreType.INTEGER, CoreLocal.Kind.VARIABLE));

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(functionWithStatements(locals, List.of(write))))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(group(functionWithStatements(locals, List.of(iteration))))));
  }

  private static CoreDefinition.Callable function(CoreExpression expression) {
    return new CoreDefinition.Callable(
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        CoreType.VOID,
        List.of(),
        new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, expression))));
  }

  private static CoreDefinition.Callable emptyFunction(CoreType returnType) {
    return new CoreDefinition.Callable(
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        returnType,
        List.of(),
        new CoreBlock(0, List.of()));
  }

  private static CoreDefinition.Callable functionWithLocal(
      CoreExpression expression, CoreType localType) {
    return new CoreDefinition.Callable(
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        CoreType.VOID,
        List.of(new CoreLocal(0, localType, CoreLocal.Kind.VARIABLE)),
        new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, expression))));
  }

  private static CoreDefinition.Callable functionWithStatements(
      List<CoreLocal> locals, List<CoreStatement> statements) {
    return new CoreDefinition.Callable(
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        CoreType.VOID,
        locals,
        new CoreBlock(0, statements));
  }

  private static CoreProgram programWithReifiedArgument(CoreType template) {
    CoreDefinitionGroup target =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(0),
                CoreType.VOID,
                List.of(new CoreLocal(0, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE)),
                new CoreBlock(0, List.of())));
    CoreExpression.Call call =
        new CoreExpression.Call(
            2,
            new DefinitionReference.External(target.definitionId(0)),
            Optional.empty(),
            List.of(),
            List.of(new CoreRuntimeType(template, List.of())),
            false,
            CoreType.VOID);
    return new CoreProgram(List.of(target, group(function(call))));
  }

  private static CoreType arrayType(CoreType elementType) {
    return builtinType("Array", List.of(elementType));
  }

  private static CoreType builtinType(String name, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core." + name)),
        arguments,
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  private static CoreDefinition.Class classDefinition(
      String name, int typeParameters, List<CoreField> fields) {
    return new CoreDefinition.Class(nominal(name), typeParameters, fields);
  }

  private static CoreNominalTypeKey nominal(String name) {
    return new CoreNominalTypeKey(
        new ModuleCoordinate("verification", 1),
        "verification",
        name,
        CoreVisibility.PUBLIC,
        Optional.empty());
  }

  private static CoreType userType(DefinitionId definition, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
        arguments,
        CoreValueCategory.IDENTITY,
        CoreNullability.NON_NULL);
  }

  private static CoreDefinitionGroup group(CoreDefinition definition) {
    return CoreDefinitionGroup.create(List.of(definition));
  }
}
