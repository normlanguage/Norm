package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationPolicy;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RuntimeProgramTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "Integer doubleIt(Integer value) { return value * 2 } Void main() { printLine(doubleIt(3)) }",
        "Function<List<T>(T)> singleton<T>() { return (T value) { List<T> result = List<>() result.add(value) return result } } Void main() { Function<List<Integer>(Integer)> make = singleton<Integer>() List<Integer> values = make(7) printLine(values[0]) }"
      })
  void projectsCallableSignaturesWithoutChangingCanonicalDefinitions(String source) {
    var core = NormTestKit.compile(source).output().orElseThrow().artifact().program();
    var runtime = RuntimeProgram.from(core);
    for (var record : core.definitions()) {
      if (record.definition() instanceof CoreDefinition.Callable callable) {
        var signature = runtime.callable(record.id()).orElseThrow();
        assertEquals(callable.receiverType(), signature.receiverType());
        assertEquals(callable.parameterTypes(), signature.parameterTypes());
        assertEquals(callable.returnType(), signature.returnType());
        assertEquals(callable.captureTypes().size(), signature.captureCount());
        assertEquals(callable.typeParameters().size(), signature.typeParameterCount());
        assertEquals(callable.receiverTypeParameterCount(), signature.receiverTypeParameterCount());
        assertTrue(runtime.structure(record.id()).isEmpty());
        assertSame(callable, core.definition(record.id()).orElseThrow());
      } else {
        assertSame(record.definition(), runtime.structure(record.id()).orElseThrow());
        assertTrue(runtime.callable(record.id()).isEmpty());
        if (record.definition() instanceof CoreDefinition.Aggregate aggregate
            && aggregate.kind() == CoreAggregateKind.ANNOTATION) {
          assertEquals(
              CoreAnnotationPolicy.resolve(core, record.id(), aggregate),
              runtime.annotationPolicy(record.id()));
        } else {
          assertThrows(IllegalArgumentException.class, () -> runtime.annotationPolicy(record.id()));
        }
      }
      assertEquals(
          record.id(),
          runtime.resolve(
              record.id(), new DefinitionReference.RecursiveMember(record.id().memberIndex())));
      var external = new DefinitionReference.External(record.id());
      assertEquals(core.resolve(record.id(), external), runtime.resolve(record.id(), external));
      var absent =
          new DefinitionReference.External(
              new DefinitionId(record.id().group(), Integer.MAX_VALUE));
      assertEquals(core.resolve(record.id(), absent), runtime.resolve(record.id(), absent));
      assertThrows(NullPointerException.class, () -> runtime.resolve(null, external));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              runtime.resolve(
                  record.id(), new DefinitionReference.RecursiveMember(Integer.MAX_VALUE)));
    }
    assertTrue(
        runtime.structures().stream()
            .noneMatch(record -> record.definition() instanceof CoreDefinition.Callable));
    assertThrows(UnsupportedOperationException.class, () -> runtime.structures().clear());
  }

  @Test
  void executionHoldersUseProjectedProgramRatherThanCanonicalBodies() {
    for (var holder :
        java.util.List.of(
            ExecutableProgram.class, AnnotationRuntime.class, JavaApplicationDispatch.class)) {
      assertTrue(
          java.util.Arrays.stream(holder.getDeclaredFields())
              .noneMatch(field -> field.getType() == CoreProgram.class));
      assertTrue(
          java.util.Arrays.stream(holder.getDeclaredFields())
              .anyMatch(field -> field.getType() == RuntimeProgram.class));
    }
  }
}
