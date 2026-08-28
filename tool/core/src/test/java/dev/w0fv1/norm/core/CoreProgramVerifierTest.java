package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreProgramVerifierTest {
  @Test
  void rejectsThrowValuesOutsideTheExceptionHierarchy() {
    CoreDefinition.Callable callable =
        functionWithStatements(
            List.of(),
            List.of(
                new CoreStatement.ThrowStatement(
                    1, new CoreExpression.Literal(2, 1, CoreType.INTEGER))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group(callable))));
  }

  @Test
  void rejectsNonExceptionAndCoveredCatchTypes() {
    CoreDefinitionGroup exception = exceptionRootGroup();
    CoreDefinitionGroup box = aggregateGroup("Box", 0, List.of());
    CoreType boxType = userType(aggregateId(box), List.of());
    CoreDefinition.Callable invalidType =
        functionWithStatements(
            List.of(new CoreLocal(0, boxType, CoreLocal.Kind.VARIABLE)),
            List.of(
                new CoreStatement.TryStatement(
                    1,
                    new CoreBlock(2, List.of()),
                    List.of(new CoreCatchClause(boxType, 0, new CoreBlock(3, List.of()))),
                    Optional.empty())));

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(exception, box, group(invalidType))));

    CoreDefinitionGroup child = derivedExceptionGroup("Failure", aggregateId(exception));
    CoreType exceptionType = userType(aggregateId(exception), List.of());
    CoreType childType = userType(aggregateId(child), List.of());
    CoreDefinition.Callable covered =
        functionWithStatements(
            List.of(
                new CoreLocal(0, exceptionType, CoreLocal.Kind.VARIABLE),
                new CoreLocal(1, childType, CoreLocal.Kind.VARIABLE)),
            List.of(
                new CoreStatement.TryStatement(
                    1,
                    new CoreBlock(2, List.of()),
                    List.of(
                        new CoreCatchClause(exceptionType, 0, new CoreBlock(3, List.of())),
                        new CoreCatchClause(childType, 1, new CoreBlock(4, List.of()))),
                    Optional.empty())));

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(exception, child, group(covered))));
  }

  @Test
  void rejectsMalformedExceptionRootAbi() {
    CoreDefinitionGroup malformed = aggregateGroup(exceptionNominal(), Optional.empty(), List.of());

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(malformed)));
  }

  @Test
  void rejectsCatchLocalsUsedOutsideTheirLexicalScope() {
    CoreDefinitionGroup exception = exceptionRootGroup();
    CoreType exceptionType = userType(aggregateId(exception), List.of());
    CoreDefinition.Callable callable =
        functionWithStatements(
            List.of(new CoreLocal(0, exceptionType, CoreLocal.Kind.VARIABLE)),
            List.of(
                new CoreStatement.TryStatement(
                    1,
                    new CoreBlock(2, List.of()),
                    List.of(new CoreCatchClause(exceptionType, 0, new CoreBlock(3, List.of()))),
                    Optional.empty()),
                new CoreStatement.ExpressionStatement(
                    4, new CoreExpression.LocalRead(5, 0, exceptionType))));

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(exception, group(callable))));
  }

  @Test
  void rejectsGenericAndValueExceptionDescendants() {
    CoreDefinitionGroup exception = exceptionRootGroup();
    Optional<CoreType> parent = Optional.of(userType(aggregateId(exception), List.of()));
    CoreDefinitionGroup generic =
        aggregateGroup(nominal("GenericFailure"), CoreValueCategory.IDENTITY, 1, parent, List.of());
    CoreDefinitionGroup value =
        aggregateGroup(nominal("ValueFailure"), CoreValueCategory.VALUE, 0, parent, List.of());

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(exception, generic)));
    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(exception, value)));
  }

  @Test
  void rejectsSpecialTypesInValueAbis() {
    for (CoreType special : List.of(CoreType.VOID, CoreType.NULL, CoreType.DYNAMIC)) {
      CoreDefinitionGroup field =
          aggregateGroup("Box", 0, List.of(new CoreField("value", 0, special, List.of())));
      CoreDefinitionGroup parameter =
          group(
              new CoreDefinition.Callable(
                  Optional.empty(),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(new CoreCallableParameter("argument0", special, 0, List.of())),
                  List.of(),
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
                List.of(new CoreTypeParameter(0, Optional.empty())),
                List.of(),
                List.of(),
                List.of(),
                List.of(0),
                List.of(),
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
  void rejectsReferencesInRuntimeTypeTemplatesAtEveryDepth() {
    CoreType reference = new CoreType.Reference(CoreType.INTEGER);

    assertThrows(IllegalArgumentException.class, () -> programWithReifiedArgument(reference));
    assertThrows(
        IllegalArgumentException.class, () -> programWithReifiedArgument(arrayType(reference)));
  }

  @Test
  void rejectsAddressesOfValueAggregateFields() {
    CoreDefinitionGroup owner =
        aggregateGroup(
            "Point",
            CoreValueCategory.VALUE,
            List.of(new CoreField("value", 0, CoreType.INTEGER, List.of())));
    CoreType point = userType(aggregateId(owner), List.of(), CoreValueCategory.VALUE);
    CoreExpression address =
        new CoreExpression.AddressField(
            2,
            new CoreExpression.LocalRead(3, 0, point),
            new CoreFieldReference(new DefinitionReference.External(aggregateId(owner)), 0),
            new CoreType.Reference(CoreType.INTEGER));

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(owner, group(functionWithLocal(address, point)))));
  }

  @Test
  void rejectsLocalReferenceAssignmentsAcrossLexicalRegions() {
    CoreType reference = new CoreType.Reference(CoreType.INTEGER);
    CoreDefinition.Callable callable =
        functionWithStatements(
            List.of(
                new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.VARIABLE),
                new CoreLocal(1, reference, CoreLocal.Kind.VARIABLE),
                new CoreLocal(2, CoreType.INTEGER, CoreLocal.Kind.VARIABLE)),
            List.of(
                new CoreStatement.LocalDeclaration(
                    1, 0, new CoreExpression.Literal(2, 0, CoreType.INTEGER)),
                new CoreStatement.LocalDeclaration(
                    3, 1, new CoreExpression.AddressLocal(4, 0, reference)),
                new CoreStatement.IfStatement(
                    5,
                    new CoreExpression.Literal(6, true, CoreType.BOOLEAN),
                    new CoreBlock(
                        7,
                        List.of(
                            new CoreStatement.LocalDeclaration(
                                8, 2, new CoreExpression.Literal(9, 1, CoreType.INTEGER)),
                            new CoreStatement.LocalAssignment(
                                10, 1, new CoreExpression.AddressLocal(11, 2, reference)))),
                    new CoreBlock(12, List.of()))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group(callable))));
  }

  @Test
  void rejectsExpiredReferencesProducedBySwitchExpressions() {
    CoreType reference = new CoreType.Reference(CoreType.INTEGER);
    CoreExpression switched =
        new CoreExpression.Switch(
            3,
            new CoreExpression.Literal(4, true, CoreType.BOOLEAN),
            List.of(
                new CoreSwitchCase(
                    new CorePattern.Literal(true, CoreType.BOOLEAN),
                    new CoreBlock(
                        5,
                        List.of(
                            new CoreStatement.LocalDeclaration(
                                6, 1, new CoreExpression.Literal(7, 1, CoreType.INTEGER)),
                            new CoreStatement.YieldStatement(
                                8, new CoreExpression.AddressLocal(9, 1, reference))))),
                new CoreSwitchCase(
                    new CorePattern.Literal(false, CoreType.BOOLEAN),
                    new CoreBlock(
                        10,
                        List.of(
                            new CoreStatement.YieldStatement(
                                11, new CoreExpression.AddressLocal(12, 0, reference)))))),
            reference);
    CoreDefinition.Callable callable =
        functionWithStatements(
            List.of(
                new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.VARIABLE),
                new CoreLocal(1, CoreType.INTEGER, CoreLocal.Kind.VARIABLE)),
            List.of(
                new CoreStatement.LocalDeclaration(
                    1, 0, new CoreExpression.Literal(2, 0, CoreType.INTEGER)),
                new CoreStatement.ExpressionStatement(
                    13, new CoreExpression.Dereference(14, switched, CoreType.INTEGER))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group(callable))));
  }

  @Test
  void revalidatesReusedReferenceReadsInTheirCurrentRegion() {
    CoreType reference = new CoreType.Reference(CoreType.INTEGER);
    CoreExpression.LocalRead reused = new CoreExpression.LocalRead(9, 1, reference);
    CoreDefinition.Callable callable =
        functionWithStatements(
            List.of(
                new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.VARIABLE),
                new CoreLocal(1, reference, CoreLocal.Kind.VARIABLE),
                new CoreLocal(2, CoreType.INTEGER, CoreLocal.Kind.VARIABLE)),
            List.of(
                new CoreStatement.LocalDeclaration(
                    1, 0, new CoreExpression.Literal(2, 0, CoreType.INTEGER)),
                new CoreStatement.IfStatement(
                    3,
                    new CoreExpression.Literal(4, true, CoreType.BOOLEAN),
                    new CoreBlock(
                        5,
                        List.of(
                            new CoreStatement.LocalDeclaration(
                                6, 2, new CoreExpression.Literal(7, 1, CoreType.INTEGER)),
                            new CoreStatement.LocalDeclaration(
                                8, 1, new CoreExpression.AddressLocal(10, 2, reference)),
                            new CoreStatement.ExpressionStatement(11, reused))),
                    new CoreBlock(12, List.of())),
                new CoreStatement.ExpressionStatement(
                    13, new CoreExpression.Dereference(14, reused, CoreType.INTEGER))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group(callable))));
  }

  @Test
  void rejectsCallsWhoseArgumentsDoNotMatchTheTargetAbi() {
    CoreDefinitionGroup target =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new CoreCallableParameter("argument0", CoreType.INTEGER, 0, List.of())),
                List.of(),
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
    CoreDefinitionGroup owner = aggregateGroup("Box", 0, List.of());
    CoreType receiver = userType(aggregateId(owner), List.of());
    CoreDefinitionGroup method =
        group(
            new CoreDefinition.Callable(
                Optional.of(receiver),
                List.of(),
                List.of(),
                List.of(),
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
    CoreExpression.CollectionLiteral literal =
        new CoreExpression.CollectionLiteral(
            2,
            List.of(),
            IntrinsicId.ARRAY_CONSTRUCT,
            new CoreRuntimeType(array, List.of()),
            array);
    CoreDefinitionGroup group =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(new CoreTypeParameter(0, Optional.empty())),
                List.of(),
                List.of(),
                List.of(),
                List.of(0),
                List.of(),
                CoreType.VOID,
                List.of(new CoreLocal(0, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE)),
                new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, literal)))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(group)));
  }

  @Test
  void rejectsConstructorArgumentsWithTheWrongType() {
    CoreDefinitionGroup target =
        aggregateGroup("Box", 0, List.of(new CoreField("value", 0, CoreType.INTEGER, List.of())));
    CoreType box = userType(aggregateId(target), List.of());
    CoreExpression.Construct construct =
        new CoreExpression.Construct(
            2,
            new DefinitionReference.External(aggregateId(target)),
            new DefinitionReference.External(constructorId(target)),
            new CoreRuntimeType(box, List.of()),
            List.of(new CoreArgument(new CoreExpression.Literal(3, "wrong", CoreType.STRING), 0)),
            box);
    CoreDefinitionGroup caller = group(function(construct));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(target, caller)));
  }

  @Test
  void rejectsConstructInitializerFromAnotherAggregate() {
    CoreDefinitionGroup target = aggregateGroup("Box", 0, List.of());
    CoreDefinitionGroup other = aggregateGroup("Other", 0, List.of());
    CoreType box = userType(aggregateId(target), List.of());
    CoreExpression.Construct construct =
        new CoreExpression.Construct(
            2,
            new DefinitionReference.External(aggregateId(target)),
            new DefinitionReference.External(constructorId(other)),
            new CoreRuntimeType(box, List.of()),
            List.of(),
            box);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(target, other, group(function(construct)))));
  }

  @Test
  void rejectsInvalidFieldAndEnumTargets() {
    CoreDefinitionGroup owner =
        aggregateGroup("Box", 0, List.of(new CoreField("value", 0, CoreType.INTEGER, List.of())));
    CoreType box = userType(aggregateId(owner), List.of());
    CoreExpression.FieldRead read =
        new CoreExpression.FieldRead(
            2,
            new CoreExpression.LocalRead(3, 0, box),
            new CoreFieldReference(new DefinitionReference.External(aggregateId(owner)), 1),
            false,
            CoreType.INTEGER);
    CoreDefinitionGroup fieldCaller =
        group(
            new CoreDefinition.Callable(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                CoreType.VOID,
                List.of(new CoreLocal(0, box, CoreLocal.Kind.VARIABLE)),
                new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, read)))));
    CoreDefinitionGroup enumGroup =
        group(
            new CoreDefinition.Enum(
                nominal("Choice"), List.of(), List.of(new CoreEnumVariant("Only", List.of()))));
    CoreType choice = enumType(enumGroup.definitionId(0), List.of());
    CoreExpression.EnumConstruct construct =
        new CoreExpression.EnumConstruct(
            2,
            new DefinitionReference.External(enumGroup.definitionId(0)),
            "Missing",
            new CoreRuntimeType(choice, List.of()),
            List.of(),
            choice);
    CoreDefinitionGroup enumCaller = group(function(construct));

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(owner, fieldCaller)));
    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(enumGroup, enumCaller)));
  }

  @Test
  void verifiesGenericEnumPayloadConstruction() {
    CoreType element = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    CoreDefinitionGroup result =
        group(
            new CoreDefinition.Enum(
                nominal("Result"),
                typeParameters(1),
                List.of(
                    new CoreEnumVariant(
                        "Error", List.of(new CoreField("value", 0, CoreType.STRING, List.of()))),
                    new CoreEnumVariant(
                        "Ok", List.of(new CoreField("value", 0, element, List.of()))))));
    CoreType resultOfInteger = enumType(result.definitionId(0), List.of(CoreType.INTEGER));
    CoreExpression.EnumConstruct construct =
        new CoreExpression.EnumConstruct(
            2,
            new DefinitionReference.External(result.definitionId(0)),
            "Ok",
            new CoreRuntimeType(resultOfInteger, List.of()),
            List.of(new CoreArgument(new CoreExpression.Literal(3, 42, CoreType.INTEGER), 0)),
            resultOfInteger);

    assertDoesNotThrow(() -> new CoreProgram(List.of(result, group(function(construct)))));
  }

  @Test
  void rejectsEnumConstructsWithMismatchedRuntimeTypesAndPayloads() {
    CoreType element = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    CoreDefinitionGroup result =
        group(
            new CoreDefinition.Enum(
                nominal("Result"),
                typeParameters(1),
                List.of(
                    new CoreEnumVariant(
                        "Ok", List.of(new CoreField("value", 0, element, List.of()))))));
    CoreType resultOfInteger = enumType(result.definitionId(0), List.of(CoreType.INTEGER));
    CoreType resultOfString = enumType(result.definitionId(0), List.of(CoreType.STRING));
    CoreExpression.EnumConstruct wrongRuntime =
        new CoreExpression.EnumConstruct(
            2,
            new DefinitionReference.External(result.definitionId(0)),
            "Ok",
            new CoreRuntimeType(resultOfString, List.of()),
            List.of(new CoreArgument(new CoreExpression.Literal(3, 42, CoreType.INTEGER), 0)),
            resultOfInteger);
    CoreExpression.EnumConstruct wrongPayload =
        new CoreExpression.EnumConstruct(
            2,
            new DefinitionReference.External(result.definitionId(0)),
            "Ok",
            new CoreRuntimeType(resultOfInteger, List.of()),
            List.of(new CoreArgument(new CoreExpression.Literal(3, "wrong", CoreType.STRING), 0)),
            resultOfInteger);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(result, group(function(wrongRuntime)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreProgram(List.of(result, group(function(wrongPayload)))));
  }

  @Test
  void rejectsEnumPayloadTypesOutsideTheirGenericAbi() {
    CoreDefinitionGroup invalid =
        group(
            new CoreDefinition.Enum(
                nominal("Box"),
                typeParameters(1),
                List.of(
                    new CoreEnumVariant(
                        "Box",
                        List.of(
                            new CoreField(
                                "value",
                                0,
                                new CoreType.Parameter(1, CoreNullability.NON_NULL),
                                List.of()))))));

    assertThrows(IllegalArgumentException.class, () -> new CoreProgram(List.of(invalid)));
  }

  @Test
  void rejectsTypeParametersOutsideTheirDefinitionAbi() {
    CoreDefinitionGroup invalid =
        aggregateGroup(
            "Box",
            1,
            List.of(
                new CoreField(
                    "value", 0, new CoreType.Parameter(1, CoreNullability.NON_NULL), List.of())));

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
        aggregateGroup("Box", 0, List.of(new CoreField("value", 0, CoreType.INTEGER, List.of())));
    CoreType box = userType(aggregateId(owner), List.of());
    CoreType nullableBox = box.asNullable();
    CoreExpression receiver = new CoreExpression.LocalRead(3, 0, nullableBox);
    CoreExpression.FieldRead field =
        new CoreExpression.FieldRead(
            2,
            receiver,
            new CoreFieldReference(new DefinitionReference.External(aggregateId(owner)), 0),
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
    CoreExpression integer = new CoreExpression.Literal(3, 1, CoreType.INTEGER);
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
  void bindsGenericIntrinsicResultsWithoutRuntimeTypes() {
    CoreExpression intrinsic =
        new CoreExpression.Intrinsic(
            2,
            IntrinsicId.IO_TEXT_ENCODE_UTF8,
            Optional.empty(),
            List.of(new CoreArgument(new CoreExpression.Literal(3, "text", CoreType.STRING), 0)),
            Optional.empty(),
            false,
            CoreType.STRING.asNullable());

    assertDoesNotThrow(() -> new CoreProgram(List.of(group(function(intrinsic)))));
  }

  @Test
  void bindsGenericIntrinsicsFromArgumentsWhenTheResultIsConcrete() {
    CoreType reflectedInteger = builtinType("Type", List.of(CoreType.INTEGER));
    CoreExpression reflected =
        new CoreExpression.Intrinsic(
            4,
            IntrinsicId.REFLECT_TYPE,
            Optional.empty(),
            List.of(),
            Optional.of(new CoreRuntimeType(reflectedInteger, List.of())),
            false,
            reflectedInteger);
    CoreExpression encoded =
        new CoreExpression.Intrinsic(
            2,
            IntrinsicId.JSON_ENCODE,
            Optional.empty(),
            List.of(
                new CoreArgument(new CoreExpression.Literal(3, 1, CoreType.INTEGER), 0),
                new CoreArgument(reflected, 1)),
            Optional.empty(),
            false,
            CoreType.STRING);

    assertDoesNotThrow(() -> new CoreProgram(List.of(group(function(encoded)))));
  }

  @Test
  void rejectsTypeAnnotationIntrinsicForNonAnnotationResults() {
    CoreDefinitionGroup box = aggregateGroup("Box", 0, List.of());
    CoreType boxType = userType(aggregateId(box), List.of());
    CoreType nullableBox =
        new CoreType.Declared(
            ((CoreType.Declared) boxType).constructor(),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NULLABLE);
    CoreType reflected = builtinType("Type", List.of(boxType));
    CoreExpression.Intrinsic query =
        new CoreExpression.Intrinsic(
            2,
            IntrinsicId.TYPE_ANNOTATION,
            Optional.of(new CoreExpression.LocalRead(3, 0, reflected)),
            List.of(),
            Optional.of(new CoreRuntimeType(nullableBox, List.of())),
            false,
            nullableBox);
    CoreDefinition.Callable callable =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CoreCallableParameter("argument0", reflected, 0, List.of())),
            List.of(),
            List.of(),
            CoreType.VOID,
            List.of(new CoreLocal(0, reflected, CoreLocal.Kind.PARAMETER)),
            new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, query))));

    assertThrows(
        IllegalArgumentException.class, () -> new CoreProgram(List.of(box, group(callable))));
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
            new CoreExpression.Literal(4, 1, CoreType.INTEGER));
    CoreStatement.ForStatement iteration =
        new CoreStatement.ForStatement(
            1,
            1,
            2,
            java.util.OptionalInt.empty(),
            receiver,
            new CoreBlock(5, List.of()),
            new CoreIteration.Builtin(IntrinsicId.ARRAY_ITERATOR));
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

  @Test
  void requiresAnIndexedLoopLocalToBeAnIntegerVariable() {
    CoreType list = builtinType("List", List.of(CoreType.INTEGER));
    CoreExpression receiver = new CoreExpression.LocalRead(2, 0, list);
    CoreStatement.ForStatement iteration =
        new CoreStatement.ForStatement(
            1,
            1,
            2,
            java.util.OptionalInt.of(3),
            receiver,
            new CoreBlock(5, List.of()),
            new CoreIteration.Builtin(IntrinsicId.LIST_ITERATOR));
    List<CoreLocal> locals =
        List.of(
            new CoreLocal(0, list, CoreLocal.Kind.VARIABLE),
            new CoreLocal(1, CoreType.DYNAMIC, CoreLocal.Kind.ITERATOR),
            new CoreLocal(2, CoreType.INTEGER, CoreLocal.Kind.VARIABLE),
            new CoreLocal(3, CoreType.STRING, CoreLocal.Kind.VARIABLE));

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
                List.of(new CoreTypeParameter(0, Optional.empty())),
                List.of(),
                List.of(),
                List.of(),
                List.of(0),
                List.of(),
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

  private static CoreDefinitionGroup aggregateGroup(
      String name, int typeParameters, List<CoreField> fields) {
    return aggregateGroup(name, CoreValueCategory.IDENTITY, typeParameters, fields);
  }

  private static CoreDefinitionGroup exceptionRootGroup() {
    return aggregateGroup(
        exceptionNominal(),
        Optional.empty(),
        List.of(new CoreField("message", 0, CoreType.STRING, List.of())));
  }

  private static CoreNominalTypeKey exceptionNominal() {
    return new CoreNominalTypeKey(
        new ModuleCoordinate("std", 1),
        "std.core",
        "Exception",
        CoreVisibility.PUBLIC,
        Optional.empty());
  }

  private static CoreDefinitionGroup derivedExceptionGroup(String name, DefinitionId parent) {
    return aggregateGroup(nominal(name), Optional.of(userType(parent, List.of())), List.of());
  }

  private static CoreDefinitionGroup aggregateGroup(
      CoreNominalTypeKey nominal, Optional<CoreType> parent, List<CoreField> fields) {
    return aggregateGroup(nominal, CoreValueCategory.IDENTITY, 0, parent, fields);
  }

  private static CoreDefinitionGroup aggregateGroup(
      CoreNominalTypeKey nominal,
      CoreValueCategory category,
      int typeParameterCount,
      Optional<CoreType> parent,
      List<CoreField> fields) {
    List<CoreTypeParameter> typeParameters = typeParameters(typeParameterCount);
    CoreType receiver =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new PendingDefinitionReference(0)),
            java.util.stream.IntStream.range(0, typeParameterCount)
                .mapToObj(index -> new CoreType.Parameter(index, CoreNullability.NON_NULL))
                .map(CoreType.class::cast)
                .toList(),
            category,
            CoreNullability.NON_NULL);
    CoreDefinition.Aggregate aggregate =
        new CoreDefinition.Aggregate(
            nominal,
            category == CoreValueCategory.VALUE ? CoreAggregateKind.VALUE : CoreAggregateKind.CLASS,
            category,
            typeParameters,
            parent,
            parent.isPresent() ? 1 + fields.size() : fields.size(),
            fields,
            List.of(),
            new PendingDefinitionReference(1),
            List.of());
    List<Integer> parameters =
        java.util.stream.IntStream.range(0, fields.size()).map(index -> index + 1).boxed().toList();
    List<Integer> reifiedLocals =
        java.util.stream.IntStream.range(0, typeParameterCount)
            .map(index -> fields.size() + index + 1)
            .boxed()
            .toList();
    List<CoreLocal> locals = new ArrayList<>();
    locals.add(new CoreLocal(0, receiver, CoreLocal.Kind.RECEIVER));
    for (int index = 0; index < fields.size(); index++) {
      locals.add(new CoreLocal(index + 1, fields.get(index).type(), CoreLocal.Kind.PARAMETER));
    }
    for (int index = 0; index < typeParameterCount; index++) {
      locals.add(
          new CoreLocal(fields.size() + index + 1, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE));
    }
    CoreDefinition.Callable constructor =
        new CoreDefinition.Callable(
            Optional.of(receiver),
            List.of(),
            List.of(),
            List.of(),
            java.util.stream.IntStream.range(0, fields.size())
                .mapToObj(
                    index ->
                        new CoreCallableParameter(
                            "argument" + index,
                            fields.get(index).type(),
                            parameters.get(index),
                            List.of()))
                .toList(),
            reifiedLocals,
            List.of(),
            CoreType.VOID,
            locals,
            new CoreBlock(0, List.of()));
    return new CoreCanonicalizer()
        .canonicalize(List.of(aggregate, constructor))
        .groups()
        .getFirst();
  }

  private static CoreDefinitionGroup aggregateGroup(
      String name, CoreValueCategory category, List<CoreField> fields) {
    return aggregateGroup(name, category, 0, fields);
  }

  private static CoreDefinitionGroup aggregateGroup(
      String name, CoreValueCategory category, int typeParameters, List<CoreField> fields) {
    List<CoreTypeParameter> parameters = typeParameters(typeParameters);
    CoreType receiver =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new PendingDefinitionReference(0)),
            java.util.stream.IntStream.range(0, typeParameters)
                .mapToObj(index -> new CoreType.Parameter(index, CoreNullability.NON_NULL))
                .map(CoreType.class::cast)
                .toList(),
            category,
            CoreNullability.NON_NULL);
    List<Integer> parameterLocals =
        java.util.stream.IntStream.range(0, fields.size()).map(index -> index + 1).boxed().toList();
    List<Integer> reifiedLocals =
        java.util.stream.IntStream.range(0, typeParameters)
            .map(index -> fields.size() + index + 1)
            .boxed()
            .toList();
    List<CoreLocal> locals = new java.util.ArrayList<>();
    locals.add(new CoreLocal(0, receiver, CoreLocal.Kind.RECEIVER));
    for (int index = 0; index < fields.size(); index++) {
      locals.add(new CoreLocal(index + 1, fields.get(index).type(), CoreLocal.Kind.PARAMETER));
    }
    for (int index = 0; index < typeParameters; index++) {
      locals.add(
          new CoreLocal(fields.size() + index + 1, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE));
    }
    CoreDefinition.Aggregate aggregate =
        new CoreDefinition.Aggregate(
            nominal(name),
            category == CoreValueCategory.VALUE ? CoreAggregateKind.VALUE : CoreAggregateKind.CLASS,
            category,
            parameters,
            Optional.empty(),
            fields.size(),
            fields,
            List.of(),
            new PendingDefinitionReference(1),
            List.of());
    CoreDefinition.Callable constructor =
        new CoreDefinition.Callable(
            Optional.of(receiver),
            List.of(),
            List.of(),
            List.of(),
            java.util.stream.IntStream.range(0, fields.size())
                .mapToObj(
                    index ->
                        new CoreCallableParameter(
                            "argument" + index,
                            fields.get(index).type(),
                            parameterLocals.get(index),
                            List.of()))
                .toList(),
            reifiedLocals,
            List.of(),
            CoreType.VOID,
            locals,
            new CoreBlock(0, List.of()));
    return new CoreCanonicalizer()
        .canonicalize(List.of(aggregate, constructor))
        .groups()
        .getFirst();
  }

  private static DefinitionId aggregateId(CoreDefinitionGroup group) {
    for (int index = 0; index < group.definitions().size(); index++) {
      if (group.definitions().get(index) instanceof CoreDefinition.Aggregate) {
        return group.definitionId(index);
      }
    }
    throw new IllegalArgumentException("group has no aggregate");
  }

  private static DefinitionId constructorId(CoreDefinitionGroup group) {
    CoreDefinition.Aggregate aggregate =
        group.definitions().stream()
            .filter(CoreDefinition.Aggregate.class::isInstance)
            .map(CoreDefinition.Aggregate.class::cast)
            .findFirst()
            .orElseThrow();
    return switch (aggregate.constructor()) {
      case DefinitionReference.External external -> external.definition();
      case DefinitionReference.RecursiveMember recursive ->
          group.definitionId(recursive.memberIndex());
      case PendingDefinitionReference ignored ->
          throw new IllegalArgumentException("group constructor is unresolved");
    };
  }

  private static List<CoreTypeParameter> typeParameters(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> new CoreTypeParameter(index, Optional.empty()))
        .toList();
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
    return userType(definition, arguments, CoreValueCategory.IDENTITY);
  }

  private static CoreType userType(
      DefinitionId definition, List<CoreType> arguments, CoreValueCategory category) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
        arguments,
        category,
        CoreNullability.NON_NULL);
  }

  private static CoreType enumType(DefinitionId definition, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
        arguments,
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  private static CoreDefinitionGroup group(CoreDefinition definition) {
    return CoreDefinitionGroup.create(List.of(definition));
  }
}
