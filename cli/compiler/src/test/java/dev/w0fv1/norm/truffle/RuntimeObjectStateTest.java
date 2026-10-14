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
  void nullableAliasesDoNotChangeTheConcreteObjectTypeInEitherTraversalOrder() {
    var constructor = new CoreTypeConstructor.User(new DefinitionReference.External(OWNER));
    var concrete =
        new CoreType.Declared(
            constructor, List.of(), CoreValueCategory.IDENTITY, CoreNullability.NON_NULL);
    var nullable =
        new CoreType.Declared(
            constructor, List.of(), CoreValueCategory.IDENTITY, CoreNullability.NULLABLE);
    for (boolean nullableFirst : List.of(false, true)) {
      var object = new RuntimeValues.ObjectValue(info(Map.of()), concrete);
      try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
        assertSame(object, transaction.transform(object, nullableFirst ? nullable : concrete));
        assertSame(object, transaction.transform(object, nullableFirst ? concrete : nullable));
        assertEquals(concrete, object.type());
        transaction.commit();
      }
      assertEquals(concrete, object.type());
    }
  }

  @Test
  void intermediateRetiredSchemasCanBeCollectedDespiteMetadataValueCycles() throws Exception {
    var history = metadataHistory(-1);
    for (int attempt = 0;
        attempt < 100
            && history.schemas().subList(1, history.schemas().size() - 1).stream()
                .anyMatch(reference -> reference.get() != null);
        attempt++) {
      System.gc();
      Thread.sleep(10);
    }
    assertTrue(
        history.schemas().subList(1, history.schemas().size() - 1).stream()
            .allMatch(reference -> reference.get() == null));
    assertNotNull(history.object().state().currentInfo());
    java.lang.ref.Reference.reachabilityFence(history.object());
  }

  @Test
  void aRetainedSourceDescriptorKeepsItsRetiredViewAlive() throws Exception {
    var history = metadataHistory(3);
    var retained = history.retained();
    assertNotNull(retained);
    for (int attempt = 0; attempt < 5; attempt++) {
      System.gc();
      Thread.sleep(10);
    }
    var value =
        (RuntimeValues.OpaqueValue)
            history.object().state().view(retained, history.object().type()).get(key("value"));
    assertSame(retained, value.aggregateInfo);
  }

  private record MetadataHistory(
      RuntimeValues.ObjectValue object,
      List<java.lang.ref.WeakReference<RuntimeValues.AggregateInfo>> schemas,
      RuntimeValues.AggregateInfo retained) {}

  private static MetadataHistory metadataHistory(int retainedIndex) {
    var first = info(Map.of("value", CoreType.DYNAMIC));
    var object = new RuntimeValues.ObjectValue(first, CoreType.DYNAMIC);
    object.writeRawField(
        key("value"), new RuntimeValues.OpaqueValue(CoreType.DYNAMIC, "value", "value", first));
    var schemas = new ArrayList<java.lang.ref.WeakReference<RuntimeValues.AggregateInfo>>();
    schemas.add(new java.lang.ref.WeakReference<>(first));
    RuntimeValues.AggregateInfo retained = retainedIndex == 0 ? first : null;
    for (int index = 0; index < 16; index++) {
      var next = info(Map.of("value", CoreType.DYNAMIC));
      schemas.add(new java.lang.ref.WeakReference<>(next));
      if (index + 1 == retainedIndex) retained = next;
      try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
        var context = transaction.migrate(object, next);
        context.stageTransform(
            key("value"), new RuntimeValues.OpaqueValue(CoreType.DYNAMIC, "value", "value", next));
        context.validate();
        transaction.commit();
      }
    }
    return new MetadataHistory(object, schemas, retained);
  }

  @Test
  void copyingUsesMaterializedOwnershipAndPreservesPreparedHistoricalViews() {
    var before = info(Map.of("value", CoreType.INTEGER));
    var after = info(Map.of("value", CoreType.STRING));
    var object = new RuntimeValues.ObjectValue(before, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    var initial = object.state().copySource(after, CoreType.DYNAMIC);
    assertSame(before, initial.info());
    assertEquals(7, initial.values().get(key("value")));
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var context = transaction.migrate(object, after);
      context.write(key("value"), CoreType.STRING, "seven");
      context.validate();
      transaction.commit();
    }
    var historical = object.state().copySource(before, CoreType.DYNAMIC);
    assertSame(before, historical.info());
    assertEquals(7, historical.values().get(key("value")));
    var current = object.state().copySource(after, CoreType.DYNAMIC);
    assertSame(after, current.info());
    assertEquals("seven", current.values().get(key("value")));
  }

  @Test
  void laterMigrationReadsAdoptedGenericStateInsteadOfBirthConfiguration() {
    var field = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    var before = info(Map.of("value", field));
    var after = info(Map.of("value", field));
    var last = info(Map.of("value", field));
    var constructor = new CoreTypeConstructor.User(new DefinitionReference.External(OWNER));
    var sourceType =
        new CoreType.Declared(
            constructor,
            List.of(CoreType.INTEGER),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var targetType =
        new CoreType.Declared(
            constructor,
            List.of(CoreType.STRING),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var object = new RuntimeValues.ObjectValue(before, sourceType);
    object.writeRawField(key("value"), 7);
    object.configuration.put(key("value"), 7);
    var replacement = new RuntimeValues.ObjectValue(after, targetType);
    replacement.writeRawField(key("value"), "seven");
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, after);
      migration.replace(replacement);
      migration.validate();
      transaction.commit();
    }
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var migration = transaction.migrate(object, last);
      assertEquals("seven", migration.read(key("value"), CoreType.STRING));
    }
  }

  @Test
  void requestedSpecializationIsComparedExactlyWithinOneRevision() {
    var info = info(Map.of());
    var constructor = new CoreTypeConstructor.User(new DefinitionReference.External(OWNER));
    var sourceType =
        new CoreType.Declared(
            constructor,
            List.of(CoreType.INTEGER),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var targetType =
        new CoreType.Declared(
            constructor,
            List.of(CoreType.STRING),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var object = new RuntimeValues.ObjectValue(info, sourceType);
    assertTrue(object.state().schemaMatches(info, sourceType));
    assertTrue(object.state().hasView(info, sourceType));
    assertFalse(object.state().schemaMatches(info, targetType));
    assertFalse(object.state().hasView(info, targetType));
    assertEquals(sourceType, object.type(info));
    try (var migration = object.state().prepare(object, info, targetType)) {
      assertEquals(targetType, migration.targetType());
      assertTrue(migration.requiresConverter());
    }
  }

  @Test
  void nestedGenericChangesPreserveInitializedStateForTheChildMigration() {
    var constructor = new CoreTypeConstructor.User(new DefinitionReference.External(OWNER));
    var sourceType =
        new CoreType.Declared(
            constructor,
            List.of(CoreType.INTEGER),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var targetType =
        new CoreType.Declared(
            constructor,
            List.of(CoreType.STRING),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var before = info(Map.of("value", sourceType));
    var after = info(Map.of("value", targetType));
    var object = new RuntimeValues.ObjectValue(before, CoreType.DYNAMIC);
    var child = new RuntimeValues.OpaqueValue(sourceType, "value", "value", before);
    object.writeRawField(key("value"), child);
    try (var context = object.state().prepare(object, after)) {
      assertTrue(context.target().containsKey(key("value")));
      assertEquals(sourceType, RuntimeValues.runtimeType(context.target().get(key("value"))));
      assertTrue(RuntimeValues.equal(child, context.target().get(key("value"))));
      assertFalse(context.requiresConverter());
    }
  }

  @Test
  void replacementChangesGenericArgumentsWhileRetainingTheSourceViewAndIdentity() {
    var before = info(Map.of("value", new CoreType.Parameter(0, CoreNullability.NON_NULL)));
    var after = info(Map.of("value", new CoreType.Parameter(1, CoreNullability.NON_NULL)));
    var firstType =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(OWNER)),
            List.of(CoreType.INTEGER),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var secondType =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(OWNER)),
            List.of(CoreType.INTEGER, CoreType.STRING),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var object = new RuntimeValues.ObjectValue(before, firstType);
    object.writeRawField(key("value"), 7);
    var candidate = new RuntimeValues.ObjectValue(after, secondType);
    candidate.writeRawField(key("value"), "seven");
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var context = transaction.migrate(object, after);
      context.replace(candidate);
      context.validate();
      assertSame(object, context.replacement());
      assertEquals(firstType, context.objectType());
      assertEquals(secondType, context.targetType());
      transaction.commit();
    }
    assertEquals(secondType, object.type());
    assertEquals(firstType, object.state().type(before, object.type()));
    assertEquals(7, object.state().view(before, object.type()).get(key("value")));
    assertEquals("seven", object.state().view(after, object.type()).get(key("value")));
  }

  @Test
  void graphTransformationForksCompatiblePolymorphicCellsWithoutChangingRetainedViews() {
    var before = info(Map.of("value", CoreType.DYNAMIC));
    var after = info(Map.of("value", CoreType.DYNAMIC));
    var object = new RuntimeValues.ObjectValue(before, CoreType.DYNAMIC);
    object.writeRawField(key("value"), 7);
    try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
      var context = transaction.migrate(object, after);
      context.stageTransform(key("value"), "seven");
      context.validate();
      transaction.commit();
    }
    assertEquals(7, object.state().view(before, object.type()).get(key("value")));
    assertEquals("seven", object.state().view(after, object.type()).get(key("value")));
  }

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
    assertEquals(7, object.state().view(old, object.type()).get(key("value")));
    assertEquals("seven", object.state().view(next, object.type()).get(key("value")));
    object.state().view(next, object.type()).put(key("shared"), 2);
    assertEquals(2, object.state().view(old, object.type()).get(key("shared")));
    assertThrows(
        RuntimeValues.StateMigrationFailure.class,
        () -> object.state().view(old, object.type()).put(key("value"), 8));
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
    assertFalse(object.state().hasView(next, object.type()));
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
    assertSame(object, object.state().view(old, object.type()).get(key("self")));
    assertSame(object, object.state().view(last, object.type()).get(key("self")));
    assertEquals(7, object.state().view(next, object.type()).get(key("renamed")));
    assertEquals("seven", object.state().view(last, object.type()).get(key("text")));
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
    assertEquals(7, object.state().view(first, object.type()).get(key("value")));
    assertEquals(11, object.state().view(last, object.type()).get(key("value")));
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
    assertFalse(object.state().hasView(next, object.type()));
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
