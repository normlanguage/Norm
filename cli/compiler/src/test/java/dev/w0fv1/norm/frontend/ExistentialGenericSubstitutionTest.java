package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ExistentialGenericSubstitutionTest {
  @Test
  void specializesNullableGenericInterfaceResultsForExistentialReceivers() throws Exception {
    var source =
        SourceFile.of(
            Path.of("existential-results.norm"),
            """
        interface Producer<T> { T? next() }
        Void consume(Producer<?> producer) { Any? value = producer.next() printLine(value) }
        interface Named { String name() }
        interface BoundedProducer<T extends Named> { T? next() List<T> many() }
        class BoundedBox<T extends Named> {
          T? next() { null }
          List<T> many() { [] }
        }
        Void consumeBounded(BoundedProducer<?> producer) {
          Named? value = producer.next()
          List<?> items = producer.many()
          if value != null { printLine(value.name()) }
          printLine(items.size())
        }
        Void consumeBox(BoundedBox<?> box) {
          Named? value = box.next()
          var next = box.next
          Named? referenced = next()
          List<?> items = box.many()
          if value != null { printLine(value.name()) }
          printLine(items.size())
        }
        Void main() {}
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }

  @Test
  void rejectsConcreteArgumentsThatDoNotSatisfyTheCapturedBound() throws Exception {
    var source =
        SourceFile.of(
            Path.of("invalid-captured-bound.norm"),
            """
        interface Named { String name() }
        interface Producer<T extends Named> { T? next() }
        Void consume(Producer<Integer> producer) {}
        Void main() {}
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertFalse(result.isSuccess());
      assertTrue(
          result.diagnostics().toString().contains("does not satisfy bound"),
          () -> result.diagnostics().toString());
    }
  }
}
