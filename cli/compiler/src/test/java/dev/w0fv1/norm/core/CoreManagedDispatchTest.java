package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class CoreManagedDispatchTest {
  @Test
  void acceptsAnExplicitlyUnimplementedClassSlot() {
    assertDoesNotThrow(() -> program(CoreAggregateKind.CLASS, 2, CallShape.VALID));
  }

  @Test
  void rejectsConstructorsAsDispatchTargets() {
    assertThrows(
        IllegalArgumentException.class, () -> program(CoreAggregateKind.CLASS, 1, CallShape.VALID));
  }

  @Test
  void rejectsManagedDispatchOnValues() {
    assertThrows(
        IllegalArgumentException.class, () -> program(CoreAggregateKind.VALUE, 2, CallShape.VALID));
  }

  @ParameterizedTest
  @EnumSource(value = CallShape.class, names = "VALID", mode = EnumSource.Mode.EXCLUDE)
  void rejectsCallsThatDoNotMatchTheManagedSignature(CallShape shape) {
    assertThrows(IllegalArgumentException.class, () -> program(CoreAggregateKind.CLASS, 2, shape));
  }

  private enum CallShape {
    VALID,
    NONVIRTUAL,
    MISSING_ARGUMENT,
    WRONG_ARGUMENT,
    WRONG_RESULT,
    CONSTRUCT
  }

  private CoreProgram program(CoreAggregateKind kind, int target, CallShape shape) {
    CoreType receiver =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new PendingDefinitionReference(0)),
            List.of(),
            kind == CoreAggregateKind.CLASS ? CoreValueCategory.IDENTITY : CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    var aggregate =
        new CoreDefinition.Aggregate(
            new CoreNominalTypeKey(
                ModuleCoordinate.localApplication(),
                "",
                "Repository",
                CoreVisibility.PUBLIC,
                Optional.empty()),
            kind,
            kind == CoreAggregateKind.CLASS ? CoreValueCategory.IDENTITY : CoreValueCategory.VALUE,
            List.of(),
            Optional.empty(),
            0,
            List.of(),
            List.of(
                new CoreMethodDispatch(
                    new PendingDefinitionReference(2),
                    new PendingDefinitionReference(target),
                    receiver)),
            List.of(new PendingDefinitionReference(1)),
            List.of());
    var constructor =
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
            new CoreBlock(0, List.of()));
    CoreDefinition method =
        new CoreDefinition.MethodSignature(
            "find", receiver, List.of(), List.of(CoreType.LONG), CoreType.LONG);
    var call =
        new CoreExpression.Call(
            2,
            new PendingDefinitionReference(2),
            Optional.of(new CoreExpression.LocalRead(3, 0, receiver)),
            shape == CallShape.MISSING_ARGUMENT
                ? List.of()
                : List.of(
                    new CoreArgument(
                        shape == CallShape.WRONG_ARGUMENT
                            ? new CoreExpression.Literal(4, "wrong", CoreType.STRING)
                            : new CoreExpression.Literal(4, 1L, CoreType.LONG),
                        0)),
            List.of(),
            List.of(),
            shape != CallShape.NONVIRTUAL,
            false,
            shape == CallShape.WRONG_RESULT ? CoreType.STRING : CoreType.LONG);
    CoreExpression expression =
        shape == CallShape.CONSTRUCT
            ? new CoreExpression.Construct(
                2,
                new PendingDefinitionReference(0),
                new PendingDefinitionReference(1),
                new CoreRuntimeType(receiver, List.of()),
                List.of(),
                receiver)
            : call;
    var caller =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CoreCallableParameter("repository", receiver, 0, List.of())),
            List.of(),
            List.of(),
            expression.type(),
            List.of(new CoreLocal(0, receiver, CoreLocal.Kind.PARAMETER)),
            new CoreBlock(
                0, List.of(new CoreStatement.ReturnStatement(1, Optional.of(expression)))));
    return new CoreProgram(
        new CoreCanonicalizer()
            .canonicalize(List.of(aggregate, constructor, method, caller))
            .groups());
  }
}
