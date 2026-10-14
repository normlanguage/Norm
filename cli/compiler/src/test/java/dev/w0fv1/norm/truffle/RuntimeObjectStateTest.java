package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class RuntimeObjectStateTest {
  private static final DefinitionId OWNER =
      new DefinitionId(DefinitionHasher.hashGroup(new byte[] {81}), 0);
  private static final DefinitionOccurrenceId OCCURRENCE = new DefinitionOccurrenceId(OWNER, 0);

  @Test
  void oldSchemaKeepsTypedValuesWhileIdentityAndAliasesRemain() {
    var old = info(Map.of("value", CoreType.INTEGER, "shared", CoreType.INTEGER));
    var next = info(Map.of("value", CoreType.STRING, "shared", CoreType.INTEGER));
    var object = new RuntimeValues.ObjectValue(old, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    object.writeRawField(key("shared"), 1);
    var alias = object;
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      assertEquals(7, migration.read(key("value"), CoreType.INTEGER));
      migration.write(key("value"), CoreType.STRING, "seven");
      migration.validateCoverage();
      transaction.commit();
    }
    assertSame(object, alias);
    assertEquals(7, object.state().view(old, object.type).get(key("value")));
    assertEquals("seven", object.state().view(next, object.type).get(key("value")));
    object.state().view(next, object.type).put(key("shared"), 2);
    assertEquals(2, object.state().view(old, object.type).get(key("shared")));
    assertThrows(
        RuntimeValues.StateMigrationFailure.class,
        () -> object.state().view(old, object.type).put(key("value"), 8));
  }

  @Test
  void renameAndDeletionRequireExplicitCoverageAndRollbackTogetherWithContainers() {
    var old = info(Map.of("value", CoreType.INTEGER, "obsolete", CoreType.INTEGER));
    var next = info(Map.of("renamed", CoreType.INTEGER));
    var object = new RuntimeValues.ObjectValue(old, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    object.writeRawField(key("obsolete"), 8);
    var list = new RuntimeValues.ListValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1)));
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      migration.write(
          key("renamed"), CoreType.INTEGER, migration.read(key("value"), CoreType.INTEGER));
      migration.remove(key("value"));
      list.values.add(2);
      assertThrows(RuntimeValues.StateMigrationFailure.class, migration::validateCoverage);
    }
    assertEquals(List.of(1), list.values);
    assertEquals(7, object.rawField(key("value")));
    assertFalse(object.state().hasView(next, object.type));
  }

  @Test
  void cyclicGraphPreservesOriginalIdentitiesAndCanAdvanceMultipleSchemas() {
    var old = info(Map.of("self", CoreType.DYNAMIC, "value", CoreType.INTEGER));
    var next = info(Map.of("self", CoreType.DYNAMIC, "renamed", CoreType.INTEGER));
    var last = info(Map.of("self", CoreType.DYNAMIC, "text", CoreType.STRING));
    var object = new RuntimeValues.ObjectValue(old, CoreType.DYNAMIC);
    object.writeRawField(key("self"), object);
    object.writeRawField(key("value"), 7);
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      migration.write(
          key("renamed"), CoreType.INTEGER, migration.read(key("value"), CoreType.INTEGER));
      migration.remove(key("value"));
      migration.validateCoverage();
      transaction.commit();
    }
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, last);
      assertEquals(7, migration.read(key("renamed"), CoreType.INTEGER));
      migration.write(key("text"), CoreType.STRING, "seven");
      migration.remove(key("renamed"));
      migration.validateCoverage();
      transaction.commit();
    }
    assertSame(object, object.state().view(old, object.type).get(key("self")));
    assertSame(object, object.state().view(last, object.type).get(key("self")));
    assertEquals(7, object.state().view(next, object.type).get(key("renamed")));
    assertEquals("seven", object.state().view(last, object.type).get(key("text")));
  }

  @Test
  void typedReadsAndWritesRejectInvalidValuesBeforeCommit() {
    var old = info(Map.of("value", CoreType.INTEGER));
    var next = info(Map.of("value", CoreType.STRING));
    var object = new RuntimeValues.ObjectValue(old, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      assertThrows(
          RuntimeValues.StateMigrationFailure.class,
          () -> migration.read(key("value"), CoreType.STRING));
      assertThrows(
          RuntimeValues.StateMigrationFailure.class,
          () -> migration.write(key("value"), CoreType.STRING, 8));
    }
    assertEquals(7, object.rawField(key("value")));
  }

  @Test
  void returningToAnEarlierShapeDoesNotRestoreItsRetiredState() {
    var first = info(Map.of("value", CoreType.INTEGER));
    var middle = info(Map.of("value", CoreType.STRING));
    var last = info(Map.of("value", CoreType.INTEGER));
    var object = new RuntimeValues.ObjectValue(first, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, middle);
      migration.write(key("value"), CoreType.STRING, "eleven");
      transaction.commit();
    }
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, last);
      assertEquals("eleven", migration.read(key("value"), CoreType.STRING));
      migration.write(key("value"), CoreType.INTEGER, 11);
      transaction.commit();
    }
    assertEquals(7, object.state().view(first, object.type).get(key("value")));
    assertEquals(11, object.state().view(last, object.type).get(key("value")));
  }

  @Test
  void explicitConversionReusesNormalDefaultsForUnwrittenNewFields() {
    var old = info(Map.of("value", CoreType.INTEGER));
    var base = info(Map.of("renamed", CoreType.INTEGER, "extra", CoreType.INTEGER));
    var initializer =
        new com.oracle.truffle.api.nodes.RootNode(null) {
          @Override
          public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
            return 9;
          }
        }.getCallTarget();
    var fields =
        base.fields().stream()
            .map(
                field ->
                    field.name().equals("extra")
                        ? new RuntimeValues.FieldPlan(
                            field.owner(),
                            field.name(),
                            field.index(),
                            true,
                            List.of(),
                            field.type(),
                            Optional.of(initializer))
                        : field)
            .toList();
    var next =
        new RuntimeValues.AggregateInfo(
            OWNER, "Counter", fields.size(), fields, Map.of(), Set.of(OWNER), null);
    var object = new RuntimeValues.ObjectValue(old, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      migration.write(key("renamed"), CoreType.INTEGER, 7);
      migration.remove(key("value"));
      transaction.prepare(object, next);
      transaction.commit();
    }
    assertEquals(7, object.rawField(key("renamed")));
    assertEquals(9, object.rawField(key("extra")));
  }

  @Test
  void retainedWritableReferenceKeepsDestructiveMigrationPending() {
    var old = info(Map.of("value", CoreType.INTEGER));
    var next = info(Map.of("value", CoreType.STRING));
    var object = new RuntimeValues.ObjectValue(old, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    var reference = new RuntimeValues.FieldReference(object, key("value"));
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      transaction.migrate(object, next);
      assertThrows(
          RuntimeValues.StateMigrationPending.class, () -> transaction.include(List.of(reference)));
    }
    assertEquals(7, reference.read());
    assertFalse(object.state().hasView(next, object.type));
  }

  @Test
  void changedOverlayCannotReplaceValuesInAnotherTypedSchema() {
    var old = info(Map.of("value", CoreType.INTEGER));
    var next = info(Map.of("value", CoreType.STRING));
    var object =
        new RuntimeValues.ObjectValue(
            old,
            CoreType.DYNAMIC,
            new LinkedHashMap<>(Map.of(key("value"), 7)),
            new LinkedHashMap<>(Map.of(key("value"), 8)));
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      assertEquals(8, migration.read(key("value"), CoreType.INTEGER));
      migration.write(key("value"), CoreType.STRING, "eight");
      transaction.commit();
    }
    assertEquals(8, object.rawField(key("value"), old));
    assertEquals("eight", object.rawField(key("value"), next));
    assertThrows(
        RuntimeValues.StateMigrationFailure.class,
        () -> object.writeRawField(key("value"), 9, old));
  }

  @Test
  void preparingAnUnchangedShapeDoesNotOverwriteConcurrentCellOrOverlayWrites() throws Exception {
    var old = info(Map.of("value", CoreType.INTEGER, "configuration", CoreType.INTEGER));
    var next = info(Map.of("value", CoreType.INTEGER, "configuration", CoreType.INTEGER));
    var object =
        new RuntimeValues.ObjectValue(
            old,
            CoreType.DYNAMIC,
            new LinkedHashMap<>(Map.of(key("value"), 1, key("configuration"), 9)),
            new LinkedHashMap<>(Map.of(key("configuration"), 9)));
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, next);
      assertEquals(1, migration.read(key("value"), CoreType.INTEGER));
      assertEquals(9, migration.read(key("configuration"), CoreType.INTEGER));
      var writer =
          new Thread(
              () -> {
                object.writeRawField(key("value"), 2);
                object.writeRawField(key("configuration"), 10);
              });
      writer.start();
      writer.join();
      transaction.commit();
    }
    assertEquals(2, object.rawField(key("value"), next));
    assertEquals(10, object.rawField(key("configuration"), next));
  }

  @Test
  void inheritedFieldTypesUseTheParentProjectionInsteadOfChildParameterOrder() {
    var result =
        dev.w0fv1.norm.testing.NormTestKit.compile(
            """
        class Base<T> {
          T value
          Base(T value) { this.value = value }
        }
        class Derived<A, B> extends Base<B> {
          Derived(B value) { super(value: value) }
        }
        Void main() {
          var object = Derived<Integer, String>(value: "x")
          printLine(object.value)
        }
        """);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var output = new java.io.StringWriter();
    new dev.w0fv1.norm.runtime.NormRuntime()
        .run(result.output().orElseThrow().artifact(), new java.io.PrintWriter(output));
    assertEquals(List.of("x"), output.toString().lines().toList());
  }

  private static RuntimeValues.FieldKey key(String name) {
    return new RuntimeValues.FieldKey(OWNER, name);
  }

  private static RuntimeValues.AggregateInfo info(Map<String, CoreType> fields) {
    var plans = new ArrayList<RuntimeValues.FieldPlan>();
    fields.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            field ->
                plans.add(
                    new RuntimeValues.FieldPlan(
                        OCCURRENCE,
                        field.getKey(),
                        plans.size(),
                        true,
                        List.of(),
                        field.getValue(),
                        Optional.empty())));
    return new RuntimeValues.AggregateInfo(
        OWNER, "Counter", plans.size(), plans, Map.of(), Set.of(OWNER), null);
  }
}
