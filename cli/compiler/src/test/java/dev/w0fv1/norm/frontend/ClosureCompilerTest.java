package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClosureCompilerTest {
  @Test
  void capturesCollectionVariablesInNestedConditionalCallbacks() {
    var source =
        SourceFile.of(
            Path.of("closures.norm"),
            """
        value Column<T> {
          Function<String(T)> display
          Function<Integer(T, T)>? compare = null
        }
        value Projection<T> {
          Function<String(T)> display
          Function<Integer(T, T)>? compare
        }
        Function<List<Projection<Integer>>()> project(List<Column<Integer>> columns) {
          () { [for (column : columns) Projection<Integer>(
            display: (Integer row) { column.display(row) },
            compare: if column.compare == null { null } else {
              (Integer left, Integer right) { column.compare!!(left, right) }
            })] }
        }
        Void main() {
          List<Column<Integer>> columns = [Column<Integer>(display: (Integer row) { row.toString() })]
          var load = project(columns)
          var result = load()
        }
        """);
    var result = new CompilerSession().compile(source);
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsMultipleParametersInAConditionalFunctionValue() {
    var result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("conditional.norm"),
                    """
        Function<Integer(Integer, Integer)>? comparator(Boolean enabled) {
          if enabled { (Integer left, Integer right) { left - right } } else { null }
        }
        Void main() {
          var compare = comparator(true)
          Integer result = compare!!(10, 2)
        }
        """));
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsParametersAndOuterCapturesInConditionalVoidCallbacks() {
    var result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("void-callback.norm"),
                    """
        Function<Void(Integer)>? callback(Boolean enabled, List<Integer> values) {
          if enabled { (Integer value) { values.add(value) } } else { null }
        }
        Void main() {
          List<Integer> values = []
          var action = callback(enabled: true, values: values)
          action!!(42)
        }
        """));
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }
}
