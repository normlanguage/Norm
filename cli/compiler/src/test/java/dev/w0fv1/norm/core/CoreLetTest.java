package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreLetTest {
  @Test
  void verifiesNestedBindingsAndRejectsReadsOutsideTheirScope() {
    var value =
        new CoreExpression.Let(
            2,
            0,
            new CoreExpression.Literal(3, 7, CoreType.INTEGER),
            new CoreExpression.LocalRead(4, 0, CoreType.INTEGER));
    var locals = List.of(new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.VARIABLE));
    assertDoesNotThrow(
        () ->
            program(
                CoreType.INTEGER,
                locals,
                List.of(),
                List.of(new CoreStatement.ReturnStatement(1, Optional.of(value)))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            program(
                CoreType.INTEGER,
                locals,
                List.of(),
                List.of(
                    new CoreStatement.ExpressionStatement(1, value),
                    new CoreStatement.ReturnStatement(
                        5, Optional.of(new CoreExpression.LocalRead(6, 0, CoreType.INTEGER))))));
  }

  @Test
  void rejectsEscapingLetStorageAndBorrowedReferenceParameters() {
    var reference = new CoreType.Reference(CoreType.INTEGER);
    var escaped =
        new CoreExpression.Let(
            2,
            0,
            new CoreExpression.Literal(3, 7, CoreType.INTEGER),
            new CoreExpression.AddressLocal(4, 0, reference));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            program(
                reference,
                List.of(new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.VARIABLE)),
                List.of(),
                List.of(new CoreStatement.ReturnStatement(1, Optional.of(escaped)))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            program(
                reference,
                List.of(new CoreLocal(0, reference, CoreLocal.Kind.PARAMETER)),
                List.of(new CoreCallableParameter("borrowed", reference, 0, List.of())),
                List.of(
                    new CoreStatement.ReturnStatement(
                        1, Optional.of(new CoreExpression.LocalRead(2, 0, reference))))));
  }

  @Test
  void rejectsBindingExpiredReferencesIntoLongerLivedStorage() {
    var reference = new CoreType.Reference(CoreType.INTEGER);
    var escaped =
        new CoreExpression.Let(
            2,
            0,
            new CoreExpression.Literal(3, 7, CoreType.INTEGER),
            new CoreExpression.AddressLocal(4, 0, reference));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            program(
                CoreType.VOID,
                List.of(
                    new CoreLocal(0, CoreType.INTEGER, CoreLocal.Kind.VARIABLE),
                    new CoreLocal(1, reference, CoreLocal.Kind.VARIABLE)),
                List.of(),
                List.of(new CoreStatement.LocalDeclaration(1, 1, escaped))));
  }

  private static CoreProgram program(
      CoreType result,
      List<CoreLocal> locals,
      List<CoreCallableParameter> parameters,
      List<CoreStatement> statements) {
    var callable =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            parameters,
            List.of(),
            List.of(),
            result,
            locals,
            new CoreBlock(0, statements));
    return new CoreProgram(List.of(CoreDefinitionGroup.create(List.of(callable))));
  }
}
