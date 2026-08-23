package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import org.junit.jupiter.api.Test;

final class CompletionContextResolverTest {
  private final Compiler compiler = new Compiler();
  private final CompletionContextResolver resolver = new CompletionContextResolver();

  @Test
  void distinguishesMemberTypeArgumentImportAndExcludedText() {
    assertContext(
        "package app\nimport std.math.|integer as math\nVoid main() {}",
        CompletionContext.Import.class);
    assertContext(
        "Void main() { List<i|nt> values = List<Integer>() }",
        CompletionContext.TypeArgument.class);
    assertContext(
        "Void main() { List<Integer> values = List<Integer>() values.|add(1) }",
        CompletionContext.Member.class);
    assertContext(
        "Void main() { String? value = null value?.|codePointSize() }",
        CompletionContext.Member.class);
    assertContext("Void main() { printLine(\"text |\") }", CompletionContext.None.class);
    assertContext("Void main() { // comment |\n printLine(1) }", CompletionContext.None.class);
  }

  private void assertContext(String marked, Class<? extends CompletionContext> expected) {
    int offset = marked.indexOf('|');
    String text = marked.replace("|", "");
    var snapshot = compiler.snapshot(SourceFile.of(DocumentId.of("untitled:context"), text));
    assertInstanceOf(expected, resolver.resolve(snapshot.entryDocument(), offset));
  }
}
