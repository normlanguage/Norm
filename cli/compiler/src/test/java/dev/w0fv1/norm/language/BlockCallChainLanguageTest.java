package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BlockCallChainLanguageTest {
  private static final String BOX =
      """
      class Box<T> {
        T value
        Box<R> map<R>(R transform(T result)) { Box<R>(value: transform(value)) }
      }
      Box<T> produce<T>(T work()) { Box<T>(value: work()) }
      """;

  @Test
  void completesABlockMemberPrefixWithABlockRatherThanAVirtualDot() {
    String text = BOX + "Void main() { produce { 1 } ma }";
    int offset = text.lastIndexOf("ma }") + 2;
    try (var service = new LanguageService()) {
      Completion completion = mapCompletion(service, text, offset);
      assertTrue(completion.insertText().startsWith("map {"), completion.toString());
      assertFalse(completion.insertText().contains("."));
      assertTrue(completion.snippet());
      var edit = completion.textEdit().orElseThrow();
      assertEquals(offset - 2, edit.location().startOffset());
      assertEquals(offset, edit.location().endOffset());
      String completed =
          text.substring(0, edit.location().startOffset())
              + edit.newText().replace("${0}", "result + 1")
              + text.substring(edit.location().endOffset());
      var analysis = service.analyze(source(completed));
      assertFalse(analysis.hasErrors(), () -> analysis.diagnostics().toString());
    }
  }

  @Test
  void replacesTheWholeMemberNameWithoutDuplicatingAnExistingBlock() {
    String text = BOX + "Void main() { produce { 1 } mapp { result + 1 } }";
    int start = text.lastIndexOf("mapp");
    try (var service = new LanguageService()) {
      Completion completion = mapCompletion(service, text, start + 2);
      assertEquals("map", completion.insertText());
      assertFalse(completion.snippet());
      var edit = completion.textEdit().orElseThrow();
      assertEquals(start, edit.location().startOffset());
      assertEquals(start + 4, edit.location().endOffset());
    }
  }

  @Test
  void neverContinuesMembersAcrossLineOrControlStructureBoundaries() {
    try (var service = new LanguageService()) {
      for (String preceding :
          List.of(
              "produce { 1 }\n",
              "produce { 1 }\r\n",
              "produce { 1 }\r",
              "(produce { 1 }) ",
              "if true {} ",
              "() {} ",
              "produce { 1 }; ")) {
        String text = BOX + "Void main() { " + preceding + "ma }";
        var analysis = service.analyze(source(text));
        var completions = service.complete(analysis, text.lastIndexOf("ma }") + 2);
        assertTrue(
            completions.stream().noneMatch(c -> c.label().equals("map")), preceding + completions);
      }
    }
  }

  @Test
  void completesOnlyMethodsCallableWithOneTrailingBlock() {
    String text =
        """
        class Chain {
          Integer allowed(Integer action(Integer result), Integer option = 1) { action(option) }
          Integer required(Integer option, Integer action(Integer result)) { action(option) }
          Integer value = 1
        }
        Chain produce(Integer work()) { work(); Chain() }
        Void main() { produce { 1 } al }
        """;
    try (var service = new LanguageService()) {
      var completions =
          service.complete(service.analyze(source(text)), text.lastIndexOf("al }") + 2);
      assertTrue(
          completions.stream().anyMatch(c -> c.label().equals("allowed")), completions.toString());
      assertTrue(
          completions.stream()
              .noneMatch(c -> c.label().equals("required") || c.label().equals("value")),
          completions.toString());
    }
  }

  @Test
  void providesSignatureHelpForTheActualTrailingCallbackInsideOuterArguments() {
    String text =
        BOX
            + "Void consume(Box<Integer> box) {} Void main() { consume(produce { 1 } map { result + 1 }) }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(source(text));
      assertFalse(analysis.hasErrors(), () -> analysis.diagnostics().toString());
      var help = service.signatureHelp(analysis, text.lastIndexOf("result + 1")).orElseThrow();
      assertEquals(0, help.activeParameter());
      assertTrue(help.signatures().getFirst().label().contains("map"), help.toString());
      assertTrue(help.signatures().getFirst().label().contains("Integer"), help.toString());
      String explicit = text.replace("} map", "}.map");
      var dotted =
          service
              .signatureHelp(service.analyze(source(explicit)), explicit.lastIndexOf("result + 1"))
              .orElseThrow();
      assertEquals(help, dotted);
    }
  }

  @Test
  void specializesExtensionSignaturesWithoutExposingTheirReceiverAsAnArgument() {
    String text =
        BOX
            + "extension Integer finish<T>(Box<T> box, Integer action(T result)) { action(box.value) } "
            + "Void main() { produce { 1 } finish { result + 1 } }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(source(text));
      var help = service.signatureHelp(analysis, text.lastIndexOf("result + 1")).orElseThrow();
      assertEquals(0, help.activeParameter());
      assertEquals(1, help.signatures().getFirst().parameters().size());
      assertTrue(help.signatures().getFirst().label().contains("finish"), help.toString());
    }
  }

  @Test
  void preservesCallbackScopesAndUsesRealMemberNamesForNavigation() {
    String text =
        BOX + "Void main() { produce { 1 } map { result.toString() } map { result.trim() } }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(source(text));
      assertFalse(analysis.hasErrors(), () -> analysis.diagnostics().toString());
      assertEquals(
          "`Integer result`",
          service.hover(analysis, text.indexOf("result.toString")).orElseThrow().markdown());
      assertEquals(
          "`String result`",
          service.hover(analysis, text.indexOf("result.trim")).orElseThrow().markdown());
      int member = text.lastIndexOf("map {");
      var definition = service.definition(analysis, member).orElseThrow();
      assertEquals("map", text.substring(definition.startOffset(), definition.endOffset()));
      var references = service.references(analysis, member, true);
      assertEquals(3, references.size());
      assertTrue(
          references.stream()
              .allMatch(ref -> text.substring(ref.startOffset(), ref.endOffset()).equals("map")));
      assertTrue(service.prepareRename(analysis, member).isPresent());
    }
  }

  @Test
  void handlesIncompleteCallbacksWithoutLosingTheirParameterScope() {
    try (var service = new LanguageService()) {
      for (String ending : List.of("map { res", "map { item in ite")) {
        String text = BOX + "Void main() { produce { 1 } " + ending;
        var analysis = service.analyze(source(text));
        String expected = ending.contains(" in ") ? "item" : "result";
        assertTrue(
            service.complete(analysis, text.length()).stream()
                .anyMatch(c -> c.label().equals(expected)),
            () -> analysis.diagnostics().toString());
      }
    }
  }

  @Test
  void preservesExplicitMemberCompletionsAfterCallsAndParentheses() {
    try (var service = new LanguageService()) {
      for (String receiver :
          List.of("produce { 1 }", "(produce { 1 })", "produce { 1 } map { result + 1 }")) {
        String text = BOX + "Void main() { " + receiver + ".ma }";
        var completions =
            service.complete(service.analyze(source(text)), text.lastIndexOf("ma }") + 2);
        Completion completion =
            completions.stream()
                .filter(c -> c.label().equals("map"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(receiver + " : " + completions));
        assertTrue(completion.insertText().startsWith("map("), receiver + completion);
      }
    }
  }

  @Test
  void doesNotTurnExplicitSuccessorArgumentsIntoBlockCompletion() {
    try (var service = new LanguageService()) {
      for (String suffix : List.of("ma(1)", "ma<Integer> {}", "ma: {}", "ma\n{}", "ma\r{}")) {
        String text = BOX + "Void main() { produce { 1 } " + suffix + " }";
        var completions =
            service.complete(service.analyze(source(text)), text.lastIndexOf(suffix) + 2);
        assertTrue(
            completions.stream().noneMatch(c -> c.label().equals("map")), suffix + completions);
      }
    }
  }

  @Test
  void retainsAnInnerCallSignatureAndMapsADefaultedOuterParameter() {
    String text =
        """
        class Chain {
          Integer choose(Integer option = 5, Integer action(Integer result) = (Integer item) { item }) { action(option) }
        }
        Chain produce(Integer work()) { work(); Chain() }
        Void main() { produce { 1 } choose { printLine(result); result + 1 } }
        """;
    try (var service = new LanguageService()) {
      var analysis = service.analyze(source(text));
      assertFalse(analysis.hasErrors(), () -> analysis.diagnostics().toString());
      var nested =
          service
              .signatureHelp(analysis, text.lastIndexOf("printLine(result") + "printLine(".length())
              .orElseThrow();
      assertTrue(nested.signatures().getFirst().label().contains("printLine"), nested.toString());
      var outer = service.signatureHelp(analysis, text.lastIndexOf("result + 1")).orElseThrow();
      assertEquals(1, outer.activeParameter());
      assertTrue(outer.signatures().getFirst().label().contains("choose"), outer.toString());
    }
  }

  @Test
  void completesMethodsReturningFunctionsWithoutReplacingTheirOwnParameters() {
    String text =
        """
        class Chain {
          Function<Integer()> make(Integer action(Integer result)) {
            var saved = action(1)
            () { saved }
          }
        }
        Chain produce(Integer work()) { work(); Chain() }
        Void main() { produce { 1 } ma }
        """;
    try (var service = new LanguageService()) {
      var completions =
          service.complete(service.analyze(source(text)), text.lastIndexOf("ma }") + 2);
      var completion =
          completions.stream().filter(c -> c.label().equals("make")).findFirst().orElseThrow();
      assertEquals("make {\n  ${0}\n}", completion.insertText());
    }
  }

  @Test
  void suppliesExplicitCallbackNamesWhenTheApiSignatureHasNoNames() {
    String text =
        """
        class Chain { Integer map(Function<Integer(Integer)> action) { action(1) } }
        Chain produce(Integer work()) { work(); Chain() }
        Void main() { produce { 1 } ma }
        """;
    try (var service = new LanguageService()) {
      Completion completion = mapCompletion(service, text, text.lastIndexOf("ma }") + 2);
      assertEquals("map { ${1:arg1} in\n  ${0}\n}", completion.insertText());
      var edit = completion.textEdit().orElseThrow();
      String completed =
          text.substring(0, edit.location().startOffset())
              + edit.newText().replace("${1:arg1}", "number").replace("${0}", "number + 1")
              + text.substring(edit.location().endOffset());
      var analysis = service.analyze(source(completed));
      assertFalse(analysis.hasErrors(), () -> analysis.diagnostics().toString());
    }
  }

  @Test
  void rejectsTokensFromAnotherSourceSnapshot() {
    String text = BOX + "Void main() { produce { 1 } ma }";
    try (var compiler = new dev.w0fv1.norm.frontend.CompilerSession()) {
      var before = compiler.snapshot(source(text)).entryDocument();
      var changed = source(text.replace("{ 1 }", "{ 2 }"));
      var mismatch =
          new dev.w0fv1.norm.semantic.DocumentSemanticModel(
              changed, before.syntax(), before.tokens(), before.projectModel());
      int offset = text.lastIndexOf("ma }") + 2;
      assertTrue(
          new CompletionContextResolver().resolve(mismatch, offset)
              instanceof CompletionContext.None);
      assertTrue(new CompletionEngine().complete(mismatch, offset).isEmpty());
    }
  }

  private static Completion mapCompletion(LanguageService service, String text, int offset) {
    var completions = service.complete(service.analyze(source(text)), offset);
    return completions.stream()
        .filter(c -> c.label().equals("map"))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    completions.stream().map(Completion::label).toList().toString()));
  }

  private static SourceFile source(String text) {
    return SourceFile.of(DocumentId.of("untitled:block-chain"), text);
  }
}
