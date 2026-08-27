package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreInterfaceProgramTest {
  @Test
  void verifiesCompleteNominalInterfaceDispatch() {
    assertDoesNotThrow(() -> program(true));
  }

  @Test
  void rejectsIncompleteNominalInterfaceDispatch() {
    assertThrows(IllegalArgumentException.class, () -> program(false));
  }

  @Test
  void verifiesGenericInterfaceCallReification() {
    assertDoesNotThrow(CoreInterfaceProgramTest::genericProgram);
  }

  @Test
  void verifiesBuiltinIntrinsicWitnesses() {
    CoreType sized = userType(new PendingDefinitionReference(0), List.of());
    CoreDefinition.Interface declaration =
        new CoreDefinition.Interface(
            nominal("Sized"), List.of(), List.of(), List.of(new PendingDefinitionReference(1)));
    CoreDefinition.InterfaceMethod requirement =
        new CoreDefinition.InterfaceMethod("size", sized, List.of(), List.of(), CoreType.INTEGER);
    CoreType list =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.List")),
            List.of(new CoreType.Parameter(0, CoreNullability.NON_NULL)),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    CoreDefinition.BuiltinConformance conformance =
        new CoreDefinition.BuiltinConformance(
            List.of(new CoreTypeParameter(0, Optional.empty())),
            list,
            sized,
            List.of(
                new CoreWitness(
                    new PendingDefinitionReference(1),
                    new CoreWitnessTarget.Intrinsic(IntrinsicId.SIZE))));
    CoreCanonicalizer.Result result =
        new CoreCanonicalizer().canonicalize(List.of(declaration, requirement, conformance));

    assertDoesNotThrow(() -> new CoreProgram(result.groups()));
  }

  @Test
  void verifiesInterfaceIterationRequirements() {
    CoreType parameter = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    CoreType iterableTemplate = userType(new PendingDefinitionReference(0), List.of(parameter));
    CoreType iteratorTemplate = userType(new PendingDefinitionReference(1), List.of(parameter));
    CoreType iterableInteger =
        userType(new PendingDefinitionReference(0), List.of(CoreType.INTEGER));
    CoreDefinition.Interface iterable =
        new CoreDefinition.Interface(
            nominal("Iterable"),
            List.of(new CoreTypeParameter(0, Optional.empty())),
            List.of(),
            List.of(new PendingDefinitionReference(2)));
    CoreDefinition.Interface iterator =
        new CoreDefinition.Interface(
            nominal("Iterator"),
            List.of(new CoreTypeParameter(0, Optional.empty())),
            List.of(),
            List.of(new PendingDefinitionReference(3), new PendingDefinitionReference(4)));
    CoreDefinition.InterfaceMethod iteratorMethod =
        new CoreDefinition.InterfaceMethod(
            "iterator", iterableTemplate, List.of(), List.of(), iteratorTemplate);
    CoreDefinition.InterfaceMethod hasNext =
        new CoreDefinition.InterfaceMethod(
            "hasNext", iteratorTemplate, List.of(), List.of(), CoreType.BOOLEAN);
    CoreDefinition.InterfaceMethod next =
        new CoreDefinition.InterfaceMethod(
            "next", iteratorTemplate, List.of(), List.of(), parameter);
    CoreDefinition.Callable consume =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CoreCallableParameter("argument0", iterableInteger, 0, List.of())),
            List.of(),
            List.of(),
            CoreType.VOID,
            List.of(
                new CoreLocal(0, iterableInteger, CoreLocal.Kind.PARAMETER),
                new CoreLocal(1, CoreType.DYNAMIC, CoreLocal.Kind.ITERATOR),
                new CoreLocal(2, CoreType.INTEGER, CoreLocal.Kind.VARIABLE)),
            new CoreBlock(
                0,
                List.of(
                    new CoreStatement.ForStatement(
                        1,
                        1,
                        2,
                        java.util.OptionalInt.empty(),
                        new CoreExpression.LocalRead(2, 0, iterableInteger),
                        new CoreBlock(3, List.of()),
                        new CoreIteration.Interface(
                            new PendingDefinitionReference(2),
                            new PendingDefinitionReference(3),
                            new PendingDefinitionReference(4))))));
    CoreCanonicalizer.Result result =
        new CoreCanonicalizer()
            .canonicalize(List.of(iterable, iterator, iteratorMethod, hasNext, next, consume));

    assertDoesNotThrow(() -> new CoreProgram(result.groups()));
  }

  @Test
  void genericBoundsParticipateInDefinitionIdentity() {
    CoreDefinition.Interface base =
        new CoreDefinition.Interface(nominal("Base"), List.of(), List.of(), List.of());
    CoreType bound = userType(new PendingDefinitionReference(0), List.of());
    CoreDefinition.Interface unbounded =
        new CoreDefinition.Interface(
            nominal("Value"),
            List.of(new CoreTypeParameter(0, Optional.empty())),
            List.of(),
            List.of());
    CoreDefinition.Interface bounded =
        new CoreDefinition.Interface(
            nominal("Value"),
            List.of(new CoreTypeParameter(0, Optional.of(bound))),
            List.of(),
            List.of());

    DefinitionId first =
        new CoreCanonicalizer().canonicalize(List.of(base, unbounded)).definitionIds().get(1);
    DefinitionId second =
        new CoreCanonicalizer().canonicalize(List.of(base, bounded)).definitionIds().get(1);

    assertNotEquals(first, second);
  }

  private static CoreProgram program(boolean complete) {
    CoreType named = userType(new PendingDefinitionReference(0), List.of());
    CoreType item = identityType(new PendingDefinitionReference(2), List.of());
    CoreDefinition.Interface declaration =
        new CoreDefinition.Interface(
            nominal("Named"), List.of(), List.of(), List.of(new PendingDefinitionReference(1)));
    CoreDefinition.InterfaceMethod requirement =
        new CoreDefinition.InterfaceMethod("name", named, List.of(), List.of(), CoreType.STRING);
    List<CoreWitness> witnesses =
        complete
            ? List.of(
                new CoreWitness(
                    new PendingDefinitionReference(1),
                    new CoreWitnessTarget.Callable(new PendingDefinitionReference(3))))
            : List.of();
    CoreDefinition.Aggregate itemDefinition =
        new CoreDefinition.Aggregate(
            nominal("Item"),
            CoreAggregateKind.CLASS,
            CoreValueCategory.IDENTITY,
            List.of(),
            Optional.empty(),
            0,
            List.of(),
            List.of(),
            new PendingDefinitionReference(5),
            List.of(new CoreConformance(named, witnesses)));
    CoreDefinition.Callable constructor = constructor(item);
    CoreDefinition.Callable implementation =
        new CoreDefinition.Callable(
            Optional.of(item),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            CoreType.STRING,
            List.of(new CoreLocal(0, item, CoreLocal.Kind.RECEIVER)),
            new CoreBlock(
                0,
                List.of(
                    new CoreStatement.ReturnStatement(
                        1, Optional.of(new CoreExpression.Literal(2, "Norm", CoreType.STRING))))));
    CoreDefinition.Callable invoke =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CoreCallableParameter("argument0", named, 0, List.of())),
            List.of(),
            List.of(),
            CoreType.STRING,
            List.of(new CoreLocal(0, named, CoreLocal.Kind.PARAMETER)),
            new CoreBlock(
                0,
                List.of(
                    new CoreStatement.ReturnStatement(
                        1,
                        Optional.of(
                            new CoreExpression.InterfaceCall(
                                2,
                                new PendingDefinitionReference(1),
                                new CoreExpression.LocalRead(3, 0, named),
                                List.of(),
                                List.of(),
                                false,
                                CoreType.STRING))))));
    CoreCanonicalizer.Result result =
        new CoreCanonicalizer()
            .canonicalize(
                List.of(
                    declaration, requirement, itemDefinition, implementation, invoke, constructor));
    return new CoreProgram(result.groups());
  }

  private static CoreProgram genericProgram() {
    CoreType identity = userType(new PendingDefinitionReference(0), List.of());
    CoreType service = identityType(new PendingDefinitionReference(2), List.of());
    CoreType parameter = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    CoreDefinition.Interface declaration =
        new CoreDefinition.Interface(
            nominal("Identity"), List.of(), List.of(), List.of(new PendingDefinitionReference(1)));
    CoreDefinition.InterfaceMethod requirement =
        new CoreDefinition.InterfaceMethod(
            "same",
            identity,
            List.of(new CoreTypeParameter(0, Optional.empty())),
            List.of(parameter),
            parameter);
    CoreDefinition.Aggregate serviceDefinition =
        new CoreDefinition.Aggregate(
            nominal("IdentityService"),
            CoreAggregateKind.CLASS,
            CoreValueCategory.IDENTITY,
            List.of(),
            Optional.empty(),
            0,
            List.of(),
            List.of(),
            new PendingDefinitionReference(5),
            List.of(
                new CoreConformance(
                    identity,
                    List.of(
                        new CoreWitness(
                            new PendingDefinitionReference(1),
                            new CoreWitnessTarget.Callable(new PendingDefinitionReference(3)))))));
    CoreDefinition.Callable constructor = constructor(service);
    CoreDefinition.Callable implementation =
        new CoreDefinition.Callable(
            Optional.of(service),
            List.of(new CoreTypeParameter(0, Optional.empty())),
            List.of(),
            List.of(),
            List.of(new CoreCallableParameter("argument0", parameter, 1, List.of())),
            List.of(2),
            List.of(),
            parameter,
            List.of(
                new CoreLocal(0, service, CoreLocal.Kind.RECEIVER),
                new CoreLocal(1, parameter, CoreLocal.Kind.PARAMETER),
                new CoreLocal(2, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE)),
            new CoreBlock(
                0,
                List.of(
                    new CoreStatement.ReturnStatement(
                        1, Optional.of(new CoreExpression.LocalRead(2, 1, parameter))))));
    CoreDefinition.Callable invoke =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CoreCallableParameter("argument0", identity, 0, List.of())),
            List.of(),
            List.of(),
            CoreType.STRING,
            List.of(new CoreLocal(0, identity, CoreLocal.Kind.PARAMETER)),
            new CoreBlock(
                0,
                List.of(
                    new CoreStatement.ReturnStatement(
                        1,
                        Optional.of(
                            new CoreExpression.InterfaceCall(
                                2,
                                new PendingDefinitionReference(1),
                                new CoreExpression.LocalRead(3, 0, identity),
                                List.of(
                                    new CoreArgument(
                                        new CoreExpression.Literal(4, "Norm", CoreType.STRING), 0)),
                                List.of(new CoreRuntimeType(CoreType.STRING, List.of())),
                                false,
                                CoreType.STRING))))));
    CoreCanonicalizer.Result result =
        new CoreCanonicalizer()
            .canonicalize(
                List.of(
                    declaration,
                    requirement,
                    serviceDefinition,
                    implementation,
                    invoke,
                    constructor));
    return new CoreProgram(result.groups());
  }

  private static CoreType userType(CoreDefinitionLink definition, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(definition),
        arguments,
        CoreValueCategory.POLYMORPHIC,
        CoreNullability.NON_NULL);
  }

  private static CoreDefinition.Callable constructor(CoreType receiver) {
    return new CoreDefinition.Callable(
        Optional.of(receiver),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        CoreType.VOID,
        List.of(new CoreLocal(0, receiver, CoreLocal.Kind.RECEIVER)),
        new CoreBlock(0, List.of()));
  }

  private static CoreType identityType(CoreDefinitionLink definition, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(definition),
        arguments,
        CoreValueCategory.IDENTITY,
        CoreNullability.NON_NULL);
  }

  private static CoreNominalTypeKey nominal(String name) {
    return new CoreNominalTypeKey(
        new ModuleCoordinate("sample", 1), "sample", name, CoreVisibility.PUBLIC, Optional.empty());
  }
}
