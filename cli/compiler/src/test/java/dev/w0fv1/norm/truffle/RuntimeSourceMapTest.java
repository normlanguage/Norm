package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class RuntimeSourceMapTest {
  @Test
  void preservesEveryAuthoredPositionAndRootFallback() {
    var authoring =
        NormTestKit.compile("Void main() { printLine(\"position\") }")
            .program()
            .orElseThrow()
            .compilation()
            .artifact()
            .authoring();
    var locations = RuntimeSourceMap.from(authoring);
    var declarations = RuntimeDeclarationIndex.from(authoring);
    for (var occurrence : authoring.occurrences()) {
      assertEquals(occurrence.origin().definitionName(), declarations.name(occurrence.id()));
      for (var definition : occurrence.representedDefinitions()) {
        assertEquals(
            authoring.occurrences(definition).stream().map(value -> value.id()).toList(),
            declarations.occurrences(definition));
      }
      for (var node : occurrence.origin().nodeSpans().entrySet()) {
        var expected = node.getValue();
        var actual = locations.location(occurrence.id(), node.getKey());
        assertEquals(expected.source().id().uri(), actual.uri());
        assertEquals(expected.start().line(), actual.line());
        assertEquals(expected.start().column(), actual.column());
      }
      var root = occurrence.origin().rootSpan();
      var fallback = locations.location(occurrence.id(), Integer.MAX_VALUE);
      assertEquals(root.source().id().uri(), fallback.uri());
      assertEquals(root.start().line(), fallback.line());
      assertEquals(root.start().column(), fallback.column());
      assertThrows(IllegalArgumentException.class, () -> locations.location(occurrence.id(), -1));
    }
  }
}
