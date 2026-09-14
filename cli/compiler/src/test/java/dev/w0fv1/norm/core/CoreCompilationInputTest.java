package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreCompilationInputTest {
  @Test
  void linksCompiledDeclarationsWithoutCanonicalizingTheirGroupsAgain() {
    var compiler = new CoreCanonicalizer();
    var original = compiler.canonicalize(List.of(callable(), callable(0)));
    var dependency = new CoreProgram(original.groups());
    var leaf = original.definitionIds().get(0);
    var result =
        compiler.canonicalize(
            new CoreCompilationInput(
                List.of(
                    new CoreCompilationInput.Compiled(leaf, Set.of(leaf)),
                    new CoreCompilationInput.Source(callable(0))),
                dependency));

    assertEquals(original.definitionIds(), result.definitionIds());
    assertEquals(1, result.metrics().components());
    assertEquals(original.groups(), result.groups());
    assertSame(
        dependency.group(leaf.group()).orElseThrow(),
        result.groups().stream()
            .filter(group -> group.id().equals(leaf.group()))
            .findFirst()
            .orElseThrow());
    assertDoesNotThrow(() -> new CoreProgram(result.groups()));
  }

  @Test
  void includesTransitiveCompiledDependenciesAndDropsUnreferencedGroups() {
    var compiler = new CoreCanonicalizer();
    var previous = compiler.canonicalize(List.of(callable(), callable(0), callable(2)));
    var middle = previous.definitionIds().get(1);
    var unused = previous.definitionIds().get(2);
    var result =
        compiler.canonicalize(
            new CoreCompilationInput(
                List.of(
                    new CoreCompilationInput.Compiled(middle, Set.of(middle)),
                    new CoreCompilationInput.Source(callable(0))),
                new CoreProgram(previous.groups())));
    var linked = new CoreProgram(result.groups());

    assertTrue(linked.definition(previous.definitionIds().get(0)).isPresent());
    assertTrue(linked.definition(middle).isPresent());
    assertTrue(linked.definition(unused).isEmpty());
    assertEquals(1, result.metrics().components());
  }

  @Test
  void preservesEquivalentMembersOfCompiledRecursiveGroups() {
    var compiler = new CoreCanonicalizer();
    var expected = compiler.canonicalize(List.of(callable(1), callable(0), callable(0)));
    var recursive = expected.definitionIds().get(0);
    var orbit = expected.definitionOrbits().get(0);
    assertEquals(2, orbit.size());
    var result =
        compiler.canonicalize(
            new CoreCompilationInput(
                List.of(
                    new CoreCompilationInput.Compiled(recursive, orbit),
                    new CoreCompilationInput.Source(callable(0))),
                new CoreProgram(expected.groups())));

    assertEquals(orbit, result.definitionOrbits().get(0));
    assertEquals(expected.definitionIds().get(2), result.definitionIds().get(1));
    assertEquals(1, result.metrics().components());
    assertDoesNotThrow(() -> new CoreProgram(result.groups()));
  }

  @Test
  void rejectsMissingCompiledGroupsAndInvalidEquivalentMembers() {
    var result = new CoreCanonicalizer().canonicalize(List.of(callable()));
    var id = result.definitionIds().get(0);
    assertThrows(
        IllegalArgumentException.class, () -> new CoreCompilationInput.Compiled(id, Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreCompilationInput(
                List.of(new CoreCompilationInput.Compiled(id, Set.of(id))),
                new CoreProgram(List.of())));
  }

  private static CoreDefinition.Callable callable(int... targets) {
    var statements = new java.util.ArrayList<CoreStatement>();
    for (int index = 0; index < targets.length; index++)
      statements.add(
          new CoreStatement.ExpressionStatement(
              index * 2 + 1,
              new CoreExpression.Call(
                  index * 2 + 2,
                  new PendingDefinitionReference(targets[index]),
                  Optional.empty(),
                  List.of(),
                  List.of(),
                  false,
                  CoreType.VOID)));
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
        new CoreBlock(0, statements));
  }
}
