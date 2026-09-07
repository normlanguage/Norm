package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SemanticProbeTest {
  @Test
  void diamondInferenceConsidersEveryConstructorRegardlessOfDeclarationOrder() {
    var constructors =
        java.util.List.of(
            "Box(String tag, T value) { this.value = value }",
            "Box(T value) { this.value = value }");
    for (var order : java.util.List.of(constructors, constructors.reversed())) {
      var source =
          SourceFile.of(
              Path.of("diamond-overloads.norm"),
              "class Box<T> { T value "
                  + String.join(" ", order)
                  + " } Void main() { printLine(Box<>(42).value) }");
      try (var compiler = new CompilerSession()) {
        var result = compiler.compile(source);
        assertTrue(result.isSuccess(), result.diagnostics().toString());
      }
    }
  }

  @Test
  void invariantGenericArgumentsDoNotIntroduceInapplicableOverloads() {
    var source =
        SourceFile.of(
            Path.of("invariant-overloads.norm"),
            """
        String accept(List<Any> values) { return "list" }
        String accept(Any values) { return "any" }
        Void main() {
          List<Integer> values = [42]
          printLine(accept(values))
        }
        """);
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
  }

  @Test
  void constructorOverloadsUseIsolatedContextualLambdaProbes() {
    var source =
        SourceFile.of(
            Path.of("constructor-probe.norm"),
            """
      class Box {
        Box(Function<Integer(Integer)> action) {}
        Box(Function<String(String)> action) {}
      }
      Void main() { Box value = Box((candidate) { candidate + 1 }) }
      """);
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(source);
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      assertEquals(
          1,
          analysis.semanticModel().symbols().stream()
              .filter(
                  symbol ->
                      symbol.kind() == SymbolKind.PARAMETER && symbol.name().equals("candidate"))
              .count());
    }
  }

  @Test
  void interfaceOverloadsUseExpectedResultsAndIsolatedLambdaProbes() {
    var source =
        SourceFile.of(
            Path.of("interface-probe.norm"),
            """
      interface Transformer {
        T apply<T>(Function<T(Integer)> action)
        String apply(Function<String(String)> action)
      }
      Integer transform(Transformer value) { return value.apply((candidate) { candidate + 1 }) }
      Void main() {}
      """);
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(source);
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      assertEquals(
          1,
          analysis.semanticModel().symbols().stream()
              .filter(
                  symbol ->
                      symbol.kind() == SymbolKind.PARAMETER && symbol.name().equals("candidate"))
              .count());
    }
  }

  @Test
  void speculativeTypingDoesNotSuppressCaptureDiagnostics() {
    var source =
        SourceFile.of(
            Path.of("capture-probe.norm"),
            """
        Integer use(Function<Integer()> action) { return action() }
        Void main() { Integer value = 1 value = 2 Integer result = use(() { value }) }
        """);
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(source);
      assertTrue(
          analysis.diagnostics().stream()
              .anyMatch(diagnostic -> diagnostic.message().contains("must be effectively final")),
          analysis.diagnostics().toString());
    }
  }

  @Test
  void speculativeLambdaTypingDoesNotRetainCandidateSymbols() {
    var source =
        SourceFile.of(
            Path.of("probe.norm"),
            """
        Integer apply(Function<Integer(Integer)> action) { return action(1) }
        String apply(Function<String(String)> action) { return action("value") }
        Void main() { Integer result = apply((candidate) { candidate + 1 }) }
        """);
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(source);
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      assertEquals(
          1,
          analysis.semanticModel().symbols().stream()
              .filter(
                  symbol ->
                      symbol.kind() == SymbolKind.PARAMETER && symbol.name().equals("candidate"))
              .count());
    }
  }
}
