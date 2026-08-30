package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TypeConstraintSolverTest {
  @Test
  void solvesVariablesInDeclarationOrder() {
    SemanticType first = SemanticType.parameter("test.First", "First");
    SemanticType second = SemanticType.parameter("test.Second", "Second");
    TypeConstraintSolver.Solution solution =
        new TypeConstraintSolver(List.of(first, second)).solve();

    assertEquals(List.of(first.identity(), second.identity()), solution.missing());
  }

  @Test
  void joinsConcreteNumericLeavesAtNumber() {
    SemanticType value = SemanticType.parameter("test.Value", "Value");
    TypeConstraintSolver solver = new TypeConstraintSolver(List.of(value));
    solver.constrain(value, SemanticType.INTEGER);
    solver.constrain(value, SemanticType.DOUBLE);
    solver.constrain(value, SemanticType.LONG);

    TypeConstraintSolver.Solution solution = solver.solve();

    assertEquals(SemanticType.NUMBER, solution.substitutions().get(value.identity()));
    assertTrue(solution.conflicts().isEmpty());
  }

  @Test
  void preservesNullableInformationWhileJoiningTheSameType() {
    SemanticType value = SemanticType.parameter("test.Value", "Value");
    TypeConstraintSolver solver = new TypeConstraintSolver(List.of(value));
    solver.constrain(value, SemanticType.STRING);
    solver.constrain(value, SemanticType.STRING.nullable());

    TypeConstraintSolver.Solution solution = solver.solve();

    assertEquals(SemanticType.STRING.nullable(), solution.substitutions().get(value.identity()));
    assertTrue(solution.conflicts().isEmpty());
  }

  @Test
  void reportsUnrelatedExactConstraintsAsAConflict() {
    SemanticType value = SemanticType.parameter("test.Value", "Value");
    TypeConstraintSolver solver = new TypeConstraintSolver(List.of(value));
    solver.constrain(value, SemanticType.STRING);
    solver.constrain(value, SemanticType.INTEGER);

    TypeConstraintSolver.Solution solution = solver.solve();

    assertEquals(1, solution.conflicts().size());
    assertEquals(SemanticType.STRING, solution.conflicts().getFirst().first());
    assertEquals(SemanticType.INTEGER, solution.conflicts().getFirst().second());
  }
}
