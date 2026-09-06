package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.testing.NormTestKit;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreDependencyTest {
  @Test
  void readingAnIndexDoesNotDemandItsOptionalWriteOperation() {
    var compiled =
        NormTestKit.compile(
            """
        Void main() { List<Integer> items = [1] printLine(items[0]) }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.program().orElseThrow().compilation().artifact();
    var entry = artifact.program().definition(artifact.entryDefinition()).orElseThrow();
    var facts = CoreReachability.intrinsics(CoreDefinitionGroup.create(java.util.List.of(entry)));
    var indexes = new java.util.ArrayList<CoreExpression.Index>();
    new CoreWalker() {
      @Override
      protected void visitExpression(CoreExpression expression) {
        if (expression instanceof CoreExpression.Index index) indexes.add(index);
      }
    }.walkExecution(entry);
    assertEquals(1, indexes.size());
    var index = indexes.getFirst();
    assertTrue(facts.contains(index.readIntrinsic()));
    assertFalse(facts.contains(index.writeIntrinsic().orElseThrow()));
  }

  @Test
  void distinguishesVirtualCallsFromDirectCalls() {
    var compiled =
        NormTestKit.compile(
            """
        class Item { String name() { return "item" } }
        String helper() { return "hello" }
        Void main() { Item item = Item() printLine(item.name()) printLine(helper()) }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.program().orElseThrow().compilation().artifact();
    var entry = artifact.program().definition(artifact.entryDefinition()).orElseThrow();
    var calls = new java.util.ArrayList<CoreExpression.Call>();
    new CoreWalker() {
      @Override
      protected void visitExpression(CoreExpression expression) {
        if (expression instanceof CoreExpression.Call call) calls.add(call);
      }
    }.walk(entry);
    assertTrue(calls.stream().anyMatch(CoreExpression.Call::virtual));
    assertTrue(calls.stream().anyMatch(call -> !call.virtual()));
    var dependencies = CoreTree.executionDependencies(entry);
    for (var call : calls) {
      assertTrue(
          dependencies.stream()
              .anyMatch(
                  dependency ->
                      dependency.target().equals(call.target())
                          && dependency.kind()
                              == (call.virtual()
                                  ? CoreDependency.Kind.VIRTUAL_CALL
                                  : CoreDependency.Kind.CALL)));
    }
  }

  @Test
  void includesIntrinsicAssignmentsAndIterationInRetentionFacts() {
    var compiled =
        NormTestKit.compile(
            """
        Void main() {
          List<Integer> items = [1, 2]
          items[0] = 3
          printLine(items[0])
          for Integer item : items { printLine(item) }
        }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.program().orElseThrow().compilation().artifact();
    var entry =
        (CoreDefinition.Callable)
            artifact.program().definition(artifact.entryDefinition()).orElseThrow();
    var facts = CoreReachability.intrinsics(CoreDefinitionGroup.create(java.util.List.of(entry)));
    var assignment =
        entry.body().statements().stream()
            .filter(CoreStatement.IntrinsicAssignment.class::isInstance)
            .map(CoreStatement.IntrinsicAssignment.class::cast)
            .findFirst()
            .orElseThrow();
    var loop =
        entry.body().statements().stream()
            .filter(CoreStatement.ForStatement.class::isInstance)
            .map(CoreStatement.ForStatement.class::cast)
            .findFirst()
            .orElseThrow();
    var iteration = assertInstanceOf(CoreIteration.Builtin.class, loop.iteration());
    assertTrue(facts.contains(assignment.intrinsic()), facts.toString());
    assertTrue(facts.contains(iteration.intrinsic()), facts.toString());
    var expressionIntrinsics = java.util.EnumSet.noneOf(dev.w0fv1.norm.abi.IntrinsicId.class);
    new CoreWalker() {
      @Override
      protected void visitExpression(CoreExpression expression) {
        if (expression instanceof CoreExpression.CollectionLiteral collection)
          expressionIntrinsics.add(collection.materializer());
        if (expression instanceof CoreExpression.Index index)
          expressionIntrinsics.add(index.readIntrinsic());
      }
    }.walkExecution(entry);
    assertEquals(2, expressionIntrinsics.size());
    assertTrue(facts.containsAll(expressionIntrinsics), facts.toString());
    assertThrows(UnsupportedOperationException.class, facts::clear);
    var conformances =
        artifact.program().definitions().stream()
            .map(CoreDefinitionRecord::definition)
            .filter(CoreDefinition.BuiltinConformance.class::isInstance)
            .map(CoreDefinition.BuiltinConformance.class::cast)
            .toList();
    int witnesses = 0;
    for (var conformance : conformances) {
      var declared = java.util.EnumSet.noneOf(dev.w0fv1.norm.abi.IntrinsicId.class);
      var executed = java.util.EnumSet.noneOf(dev.w0fv1.norm.abi.IntrinsicId.class);
      new CoreWalker() {
        @Override
        protected void visitIntrinsic(dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
          declared.add(intrinsic);
        }
      }.walkDeclaration(conformance);
      new CoreWalker() {
        @Override
        protected void visitIntrinsic(dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
          executed.add(intrinsic);
        }
      }.walkExecution(conformance);
      assertTrue(executed.isEmpty());
      for (var witness : conformance.witnesses()) {
        if (witness.implementation() instanceof CoreWitnessTarget.Intrinsic intrinsic) {
          witnesses++;
          assertTrue(declared.contains(intrinsic.intrinsic()));
        }
      }
    }
    assertTrue(witnesses > 0);
  }

  @Test
  void distinguishesTypeReferencesFromConstructionAndCalls() {
    var compiled =
        NormTestKit.compile(
            """
        interface Named { String name() }
        class Item implements Named { String name() { return "item" } }
        String helper() { return "hello" }
        Void main() {
          Named? absent = null
          Named present = Item()
          printLine(present.name())
          printLine(helper())
        }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.program().orElseThrow().compilation().artifact();
    var entry = artifact.program().definition(artifact.entryDefinition()).orElseThrow();
    var declaration = CoreTree.declarationDependencies(entry);
    var execution = CoreTree.executionDependencies(entry);
    assertFalse(
        declaration.stream()
            .anyMatch(
                dependency ->
                    dependency.kind() == CoreDependency.Kind.CONSTRUCTION
                        || dependency.kind() == CoreDependency.Kind.CALL
                        || dependency.kind() == CoreDependency.Kind.INTERFACE_CALL));
    assertTrue(
        execution.stream().anyMatch(dependency -> dependency.kind() == CoreDependency.Kind.CALL));
    var combined = new java.util.ArrayList<>(declaration);
    combined.addAll(execution);
    assertEquals(CoreTree.dependencies(entry), combined);
    var dependencies = CoreTree.dependencies(entry);
    var rawLinks = new java.util.ArrayList<CoreDefinitionLink>();
    new CoreWalker() {
      @Override
      protected void visitLink(CoreDefinitionLink link) {
        rawLinks.add(link);
      }
    }.walk(entry);
    assertEquals(rawLinks, dependencies.stream().map(CoreDependency::target).toList());
    var analysis = CoreReachability.analyze(artifact, Set.of());
    assertTrue(
        analysis.causes().values().stream().anyMatch(cause -> cause.dependency().isPresent()));
    for (var cause : analysis.causes().values()) {
      assertEquals(
          cause.kind() == CoreReachability.RetentionKind.DEFINITION_LINK,
          cause.dependency().isPresent());
    }
    var kinds =
        dependencies.stream()
            .map(CoreDependency::kind)
            .collect(java.util.stream.Collectors.toSet());
    assertTrue(
        kinds.containsAll(
            Set.of(
                CoreDependency.Kind.TYPE,
                CoreDependency.Kind.CONSTRUCTION,
                CoreDependency.Kind.CALL,
                CoreDependency.Kind.INTERFACE_CALL)),
        kinds.toString());
    assertEquals(CoreTree.links(entry), dependencies.stream().map(CoreDependency::target).toList());
    assertThrows(UnsupportedOperationException.class, dependencies::clear);
    var item =
        artifact.program().definitions().stream()
            .filter(
                record ->
                    record.definition() instanceof CoreDefinition.Aggregate aggregate
                        && aggregate.nominalType().name().equals("Item"))
            .findFirst()
            .orElseThrow();
    var declarations = CoreTree.dependencies(item.definition());
    assertEquals(declarations, CoreTree.declarationDependencies(item.definition()));
    assertTrue(CoreTree.executionDependencies(item.definition()).isEmpty());
    assertTrue(
        declarations.stream().anyMatch(link -> link.kind() == CoreDependency.Kind.IMPLEMENTATION));
    assertFalse(declarations.stream().anyMatch(link -> link.kind() == CoreDependency.Kind.CALL));
  }
}
