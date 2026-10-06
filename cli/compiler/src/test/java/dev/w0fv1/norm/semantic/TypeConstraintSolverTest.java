package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TypeConstraintSolverTest {
  @Test
  void terminatesWhenSelfBoundsExpandActualGenericArguments() {
    var variable = SemanticType.parameter("test.T", "T");
    var relations =
        new TypeRelations.DeclarationGraph(
            type -> {
              if (type.equals(variable))
                return List.of(
                    SemanticType.declared(
                        "test.Carrier", "Carrier", List.of(variable), ValueCategory.POLYMORPHIC));
              if (!type.identity().equals("test.Expanding")) return List.of();
              var nested =
                  SemanticType.declared(
                      "test.Carrier", "Carrier", type.arguments(), ValueCategory.POLYMORPHIC);
              var expanded =
                  SemanticType.declared(
                      "test.Expanding", "Expanding", List.of(nested), ValueCategory.IDENTITY);
              return List.of(
                  SemanticType.declared(
                      "test.Carrier", "Carrier", List.of(expanded), ValueCategory.POLYMORPHIC));
            });
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(2),
        () -> {
          var solver = new TypeConstraintSolver(List.of(variable), relations);
          solver.constrain(
              variable,
              SemanticType.declared(
                  "test.Expanding",
                  "Expanding",
                  List.of(SemanticType.STRING),
                  ValueCategory.IDENTITY));
          assertTrue(!solver.solve().conflicts().isEmpty());
        });
  }

  @Test
  void usesGenericBoundsToSelectAmongUnrelatedCommonInterfaces() {
    var value = SemanticType.parameter("test.Value", "Value");
    var printable =
        SemanticType.declared("test.Printable", "Printable", List.of(), ValueCategory.POLYMORPHIC);
    var serializable =
        SemanticType.declared(
            "test.Serializable", "Serializable", List.of(), ValueCategory.POLYMORPHIC);
    var relations =
        new TypeRelations.DeclarationGraph(
            type ->
                type.equals(value)
                    ? List.of(printable)
                    : type.equals(SemanticType.STRING) || type.equals(SemanticType.INTEGER)
                        ? List.of(printable, serializable)
                        : List.of());
    var solver = new TypeConstraintSolver(List.of(value), relations);
    solver.constrain(value, SemanticType.STRING);
    solver.constrain(value, SemanticType.INTEGER);
    var solution = solver.solve();
    assertTrue(solution.conflicts().isEmpty());
    assertEquals(printable, solution.substitutions().get(value.identity()));
    var homogeneous = new TypeConstraintSolver(List.of(value), relations);
    homogeneous.constrain(value, SemanticType.STRING);
    homogeneous.constrain(value, SemanticType.STRING);
    assertEquals(SemanticType.STRING, homogeneous.solve().substitutions().get(value.identity()));
    assertTrue(relations.commonType(SemanticType.STRING, SemanticType.INTEGER).isEmpty());
  }

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
