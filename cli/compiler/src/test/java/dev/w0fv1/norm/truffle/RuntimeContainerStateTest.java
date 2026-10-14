package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RuntimeContainerStateTest {
  @Test
  void discardedStateMigrationPreservesContainerAliasesAndIterator() {
    var list = new RuntimeValues.ListValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1, 2)));
    var alias = list.values;
    var iterator = list.values.iterator();
    assertEquals(1, iterator.next());
    try (var migration = RuntimeValues.prepareState(List.of(list), null)) {
      list.values.add(9);
      list.values.set(0, 8);
      assertEquals(List.of(8, 2, 9), list.values);
    }
    assertSame(alias, list.values);
    assertEquals(List.of(1, 2), list.values);
    assertEquals(2, iterator.next());
  }

  @Test
  void childCommitRemainsDiscardableByItsParentMigration() {
    var list = new RuntimeValues.ListValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1)));
    try (var outer = RuntimeValues.prepareState(List.of(list), null)) {
      list.values.add(2);
      try (var inner = RuntimeValues.prepareState(List.of(list), null)) {
        list.values.add(3);
        inner.commit();
      }
      assertEquals(List.of(1, 2, 3), list.values);
    }
    assertEquals(List.of(1), list.values);
  }

  @Test
  void allMutableContainerViewsUseTheSameDiscardableState() {
    var array = new RuntimeValues.ArrayValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1)));
    var map = new RuntimeValues.MapValue(CoreType.DYNAMIC);
    var key = new RuntimeValues.RuntimeKey(1);
    map.values.put(key, "old");
    var set = new RuntimeValues.SetValue(CoreType.DYNAMIC);
    set.values.add(key);
    var stack = new RuntimeValues.StackValue(CoreType.DYNAMIC);
    stack.values.push(1);
    var queue = new RuntimeValues.QueueValue(CoreType.DYNAMIC);
    queue.values.add(1);
    var deque = new RuntimeValues.DequeValue(CoreType.DYNAMIC);
    deque.values.add(1);
    var builder = new RuntimeValues.BuilderValue(CoreType.DYNAMIC, "a");
    var pair = new RuntimeValues.PairValue(CoreType.DYNAMIC, 1, 2);
    try (var migration =
        RuntimeValues.prepareState(
            List.of(array, map, set, stack, queue, deque, builder, pair), null)) {
      array.values.set(0, 9);
      map.values.entrySet().iterator().next().setValue("new");
      set.values.removeIf(value -> true);
      stack.values.push(2);
      queue.values.add(2);
      deque.values.addFirst(2);
      builder.writable().append("b");
      pair.first(9);
    }
    assertEquals(List.of(1), array.values);
    assertEquals("old", map.values.get(key));
    assertTrue(set.values.contains(key));
    assertEquals(List.of(1), new ArrayList<>(stack.values));
    assertEquals(List.of(1), new ArrayList<>(queue.values));
    assertEquals(List.of(1), new ArrayList<>(deque.values));
    assertEquals("a", builder.value().toString());
    assertEquals(1, pair.first());
    assertEquals(2, pair.second());
  }

  @Test
  void iteratorMutationsRemainTransactionalAfterEarlierWrites() {
    var list = new RuntimeValues.ListValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1, 2, 3, 4)));
    try (var migration = RuntimeValues.prepareState(List.of(list), null)) {
      list.values.removeIf(value -> ((Integer) value) % 2 == 0);
      assertEquals(List.of(1, 3), list.values);
      list.values.add(5);
      list.values.removeIf(value -> true);
      assertTrue(list.values.isEmpty());
    }
    assertEquals(List.of(1, 2, 3, 4), list.values);
  }

  @Test
  void committingNonstructuralChangesPreservesTheRunningIterator() {
    var list = new RuntimeValues.ListValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1, 2)));
    var iterator = list.values.iterator();
    assertEquals(1, iterator.next());
    try (var migration = RuntimeValues.prepareState(List.of(list), null)) {
      list.values.set(1, 9);
      migration.commit();
    }
    assertEquals(9, iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
  void dequeIteratorRemovesTheSelectedOccurrenceInsideMigration() {
    var deque = new RuntimeValues.DequeValue(CoreType.DYNAMIC);
    deque.values.addAll(List.of(1, 2, 1));
    try (var migration = RuntimeValues.prepareState(List.of(deque), null)) {
      var iterator = deque.values.iterator();
      assertEquals(1, iterator.next());
      assertEquals(2, iterator.next());
      assertEquals(1, iterator.next());
      iterator.remove();
      assertEquals(List.of(1, 2), new ArrayList<>(deque.values));
    }
    assertEquals(List.of(1, 2, 1), new ArrayList<>(deque.values));
  }

  @Test
  void missingInitializerIsAnExplicitStateMigrationFailure() {
    var definition =
        new dev.w0fv1.norm.core.DefinitionId(
            dev.w0fv1.norm.core.DefinitionHasher.hashGroup(new byte[] {1}), 0);
    var owner = new dev.w0fv1.norm.core.DefinitionOccurrenceId(definition, 0);
    var initial =
        new RuntimeValues.AggregateInfo(
            definition,
            "Counter",
            0,
            List.of(),
            java.util.Map.of(),
            java.util.Set.of(definition),
            null);
    var candidate =
        new RuntimeValues.AggregateInfo(
            definition,
            "Counter",
            1,
            List.of(
                new RuntimeValues.FieldPlan(
                    owner,
                    "extra",
                    0,
                    true,
                    List.of(),
                    CoreType.INTEGER,
                    java.util.Optional.empty())),
            java.util.Map.of(),
            java.util.Set.of(definition),
            null);
    var object = new RuntimeValues.ObjectValue(initial, CoreType.DYNAMIC);
    try (var migration = RuntimeValues.prepareState(List.of(), null)) {
      var failure =
          assertThrows(
              RuntimeValues.StateMigrationFailure.class,
              () -> migration.prepare(object, candidate));
      assertTrue(failure.getMessage().contains("explicit initializer"));
    }
    assertTrue(object.stateCells().isEmpty());
  }
}
