package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class CollectionCompilerTest {
  @Test
  void rejectsInvalidConditionsElementsSpreadsAndEscapingVariables() {
    for (String expression :
        java.util.List.of(
            "[if (1) 2]",
            "[if (true) 1 else \"wrong\"]",
            "[...1]",
            "[...[\"wrong\"]]",
            "[for (value : [1]) \"wrong\"]",
            "[for (value : [1]) value, value]",
            "[for (value : []) value]")) {
      var result = NormTestKit.compile("Void main() { List<Integer> values = " + expression + " }");
      assertFalse(result.isSuccess(), expression);
    }
  }

  @Test
  void acceptsTypedIterationIndexesAndNestedConditions() {
    assertTrue(
        NormTestKit.compile(
                "Void main() { List<Integer> values = [for (Integer value, index : [3, 4]) if"
                    + " (index == 0) value else index] }")
            .isSuccess());
  }
}
