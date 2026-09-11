package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class FieldObservationExecutionTest {
  @Test
  void observesReferencesAndReflectedReads() {
    assertEquals(
        "locations" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.observeFields
            class Box { Integer value = 0 }
            class Events { Integer reads = 0 Integer writes = 0 }
            Void main() {
              var box = Box()
              var events = Events()
              var subscription = observeFields(target: box,
                read: (Integer field) { events.reads = events.reads + 1 },
                changed: (Integer field) { events.writes = events.writes + 1 })
              ref<Integer> location = &box.value
              var first = *location
              *location = 42
              var last = Box.value.field.read(receiver: box)
              require(condition: first == 0 && last == 42 && events.reads == 2 && events.writes == 1,
                message: "all field access paths")
              subscription.close()
              printLine("locations")
            }
            """));
  }

  @Test
  void observesFieldsAndReleasesSubscriptions() {
    assertEquals(
        "observed" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.observeFields
            class Counter {
              private Integer value = 0
              Integer read() { value }
              Void write(Integer next) { value = next }
            }
            class Events { Integer reads = 0 Integer writes = 0 Integer slot = -1 }
            Void main() {
              var counter = Counter()
              var events = Events()
              var subscription = observeFields(target: counter,
                read: (Integer field) { events.reads = events.reads + 1 events.slot = field },
                changed: (Integer field) {
                  require(condition: field == events.slot, message: "same field identity")
                  events.writes = events.writes + 1
                })
              var first = counter.read()
              counter.write(1)
              counter.write(1)
              require(condition: first == 0 && events.reads == 1 && events.writes == 1, message: "field observations")
              subscription.close()
              subscription.close()
              counter.write(2)
              var last = counter.read()
              require(condition: last == 2 && events.reads == 1 && events.writes == 1, message: "released observations")
              printLine("observed")
            }
            """));
  }
}
