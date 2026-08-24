package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreCanonicalizerTest {
  @Test
  void canonicalizesStructurallyIndistinguishableRecursiveMembers() {
    List<List<Integer>> graph = List.of(List.of(1, 2), List.of(0, 2), List.of(0, 3), List.of(0, 1));

    CoreCanonicalizer.Result first = canonicalize(graph, List.of(0, 1, 2, 3));
    CoreCanonicalizer.Result reordered = canonicalize(graph, List.of(2, 0, 3, 1));

    assertEquals(first.groups().getFirst().id(), reordered.groups().getFirst().id());
    assertEquals(4, Set.copyOf(first.definitionIds().values()).size());
  }

  @Test
  void givesExternalCallersStableIdentityForSymmetricRecursiveMembers() {
    List<List<Integer>> graph = List.of(List.of(1), List.of(2), List.of(0), List.of(0));

    List<Integer> firstOrder = List.of(0, 1, 2, 3);
    List<Integer> secondOrder = List.of(2, 3, 0, 1);
    CoreCanonicalizer.Result first = canonicalize(graph, firstOrder);
    CoreCanonicalizer.Result reordered = canonicalize(graph, secondOrder);

    assertEquals(
        first.definitionIds().get(firstOrder.indexOf(3)),
        reordered.definitionIds().get(secondOrder.indexOf(3)));
    assertEquals(
        first.definitionIds().get(firstOrder.indexOf(0)),
        first.definitionIds().get(firstOrder.indexOf(1)));
    assertEquals(
        first.definitionIds().get(firstOrder.indexOf(0)),
        first.definitionIds().get(firstOrder.indexOf(2)));
  }

  private static CoreCanonicalizer.Result canonicalize(
      List<List<Integer>> graph, List<Integer> declarationOrder) {
    int[] declarationByVertex = new int[graph.size()];
    for (int declaration = 0; declaration < declarationOrder.size(); declaration++) {
      declarationByVertex[declarationOrder.get(declaration)] = declaration;
    }
    List<CoreDefinition> definitions = new ArrayList<>();
    for (int vertex : declarationOrder) {
      List<CoreStatement> calls = new ArrayList<>();
      for (int index = 0; index < graph.get(vertex).size(); index++) {
        int statementIndex = index * 2 + 1;
        int expressionIndex = statementIndex + 1;
        calls.add(
            new CoreStatement.ExpressionStatement(
                statementIndex,
                new CoreExpression.Call(
                    expressionIndex,
                    new PendingDefinitionReference(
                        declarationByVertex[graph.get(vertex).get(index)]),
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    false,
                    CoreType.VOID)));
      }
      definitions.add(
          new CoreDefinition.Callable(
              Optional.empty(),
              List.of(),
              List.of(),
              List.of(),
              CoreType.VOID,
              List.of(),
              new CoreBlock(0, calls)));
    }
    return new CoreCanonicalizer().canonicalize(definitions);
  }
}
