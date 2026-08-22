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
        "package app\nimport std.math.|integer as math\nvoid main() {}",
        CompletionContext.Import.class);
    assertContext(
        "void main() { List<i|nt> values = List<int>() }", CompletionContext.TypeArgument.class);
    assertContext(
        "void main() { List<int> values = List<int>() values.|add(1) }",
        CompletionContext.Member.class);
    assertContext("void main() { print(\"text |\") }", CompletionContext.None.class);
    assertContext("void main() { // comment |\n print(1) }", CompletionContext.None.class);
  }

  private void assertContext(String marked, Class<? extends CompletionContext> expected) {
    int offset = marked.indexOf('|');
    String text = marked.replace("|", "");
    var snapshot = compiler.snapshot(SourceFile.of(DocumentId.of("untitled:context"), text));
    assertInstanceOf(expected, resolver.resolve(snapshot.entryDocument(), offset));
  }
}
