package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ResultBuilderExecutionTest {
  private static final String API =
      """
      import std.build.BuildWith
      import std.build.ResultBuilder
      import std.core.Exception
      class Words implements ResultBuilder<String, String> {
        private String text = ""
        Void add(String value) { text = text + value }
        String finish() { text }
      }
      String words(@BuildWith(Words.class) Function<String()> content) { content() }
      """;

  @Test
  void collectsExpressionsBranchesAndLoopsWithFreshBuilders() {
    assertEquals(
        "abcde\n\nz\n",
        NormTestKit.run(
                API
                    + """
                    Void main() {
                      printLine(words {
                        var first = "a"
                        first
                        if true { "b" } else { "wrong" }
                        if false { "wrong" }
                        for letter : ["c", "d"] { letter }
                        "e"
                      })
                      printLine(words {})
                      printLine(words { "z" })
                    }
                    """)
            .replace("\r\n", "\n"));
  }

  @Test
  void leavesNestedEventsAndOrdinaryFunctionValuesUnchanged() {
    assertEquals(
        "ab\nevent\nmanual\n",
        NormTestKit.run(
                API
                    + """
                    class Actions { Function<Void()> clicked = () {} }
                    String button(String text, Function<Void()> action, Actions actions) {
                      actions.clicked = action
                      text
                    }
                    Void main() {
                      var actions = Actions()
                      printLine(words {
                        button(text: "a", actions: actions) { printLine("event") }
                        "b"
                      })
                      actions.clicked()
                      Function<String()> content = () { "manual" }
                      printLine(words(content))
                    }
                    """)
            .replace("\r\n", "\n"));
  }

  @Test
  void rejectsWrongElementsAndExplicitBuilderReturns() {
    var invalid = NormTestKit.compile(API + "Void main() { words { 42 } }");
    assertFalse(invalid.isSuccess());
    assertTrue(
        invalid.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.primarySpan().text().equals("42")));
    assertFalse(NormTestKit.compile(API + "Void main() { words { return \"x\" } }").isSuccess());
  }

  @Test
  void reusesBuilderAnalysisAfterFormattingWithoutLosingGeneratedBindings() throws Exception {
    var runtime = new dev.w0fv1.norm.runtime.NormRuntime(new TruffleExecutionBackend(8));
    var environment = dev.w0fv1.norm.project.ProjectEnvironment.bootstrap(runtime);
    String text = API + "Void main(){printLine(words{\"a\" if true{\"b\"}})}";
    var path = java.nio.file.Path.of("builder-incremental.norm");
    var first = dev.w0fv1.norm.source.SourceFile.of(path, text);
    String formatted = new dev.w0fv1.norm.frontend.SourceFormatter().format(first).orElseThrow();
    try (var compiler = environment.compilerSession()) {
      assertTrue(compiler.compile(first).isSuccess());
      var result = compiler.compile(dev.w0fv1.norm.source.SourceFile.of(path, formatted));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = new java.io.StringWriter();
      runtime.run(
          result.output().orElseThrow().artifact(),
          dev.w0fv1.norm.execution.ExecutionContext.of(
              new java.io.PrintWriter(output),
              dev.w0fv1.norm.platform.jdk.JdkSystemPlatform.standard()));
      assertEquals("ab\n", output.toString().replace("\r\n", "\n"));
    }
  }

  @Test
  void infersGenericCallbackResultsAndPreservesExceptionOrder() {
    assertEquals(
        "ab\nfailed\n",
        NormTestKit.run(
                API
                    + """
                    R build<R>(@BuildWith(Words.class) Function<R()> content) { content() }
                    String fail() { throw Exception(message: "failed") }
                    Void main() {
                      var result = build { "a" "b" }
                      printLine(result)
                      try { words { "first" fail() "last" } }
                      catch Exception failure { printLine(failure.message) }
                    }
                    """)
            .replace("\r\n", "\n"));
  }

  @Test
  void usesAppliedGenericBuilderClasses() {
    assertEquals(
        "1\n2\n",
        NormTestKit.run(
                API
                    + """
                    class Items<T> implements ResultBuilder<T, List<T>> {
                      private List<T> values = []
                      Void add(T value) { values.add(value) }
                      List<T> finish() { values }
                    }
                    List<Integer> numbers(@BuildWith(Items<Integer>.class) Function<List<Integer>()> content) { content() }
                    Void main() { var values = numbers { 1 2 }; for number : values { printLine(number) } }
                    """)
            .replace("\r\n", "\n"));
  }
}
