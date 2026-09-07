package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import org.junit.jupiter.api.Test;

final class CompletionContextResolverTest {
  private final CompilerSession compiler = new CompilerSession();
  private final CompletionContextResolver resolver = new CompletionContextResolver();

  @Test
  void followsLiteralRecoveryAndNestedInterpolationTokens() {
    assertContext("Void main() { printLine(\"|", CompletionContext.None.class);
    assertContext("Void main() { printLine(\"escaped \\\"|", CompletionContext.None.class);
    assertContext("Void main() { CodePoint value = '|", CompletionContext.None.class);
    assertContext(
        "Void main() { printLine(\"${\"${val|ue}\"}\") }", CompletionContext.Expression.class);
    assertContext(
        "Void main() { printLine(\"${value} tail\n printLine(val|ue) }",
        CompletionContext.Expression.class);
  }

  @Test
  void distinguishesMemberTypeArgumentImportAndExcludedText() {
    assertContext(
        "package app\nimport std.math.|integer as math\nVoid main() {}",
        CompletionContext.Import.class);
    assertContext(
        "Void main() { List<i|nt> values = List<>() }", CompletionContext.TypeArgument.class);
    assertContext(
        "Void main() { List<Integer> values = List<>() values.|add(1) }",
        CompletionContext.Member.class);
    assertContext(
        "Void main() { String? value = null value?.|codePointSize() }",
        CompletionContext.Member.class);
    assertContext(
        "enum Result<T, E> { Ok(T value), Err(E error) } Void main() { Result<Integer, String>.| }",
        CompletionContext.Member.class);
    assertContext(
        "interface Named {} class User implements |Named {}",
        CompletionContext.InterfaceType.class);
    assertContext(
        "interface Named {} interface Labeled extends |Named {}",
        CompletionContext.InterfaceType.class);
    assertContext(
        "interface Named {} T read<T extends |Named>(T value) { return value }",
        CompletionContext.InterfaceType.class);
    assertContext(
        "interface Named {} interface Sized {} class User implements Named, |Sized {}",
        CompletionContext.InterfaceType.class);
    assertContext(
        "interface Named {} T read<T extends Named, |U>(T value) { return value }",
        CompletionContext.TypeArgument.class);
    assertContext("annotation Marker {} @Mar| value Point {}", CompletionContext.Annotation.class);
    assertContext("Void main() { printLine(\"text |\") }", CompletionContext.None.class);
    assertContext("Void main() { printLine(\"text |", CompletionContext.None.class);
    assertContext("Void main() { printLine(\"${val|ue}\") }", CompletionContext.Expression.class);
    assertContext(
        "Void main() { CodePoint quote = '\"' printLine(val|ue) }",
        CompletionContext.Expression.class);
    assertContext(
        "Void main() { // comment |\n printLine(1) }", CompletionContext.Expression.class);
  }

  private void assertContext(String marked, Class<? extends CompletionContext> expected) {
    int offset = marked.indexOf('|');
    String text = marked.replace("|", "");
    var snapshot = compiler.snapshot(SourceFile.of(DocumentId.of("untitled:context"), text));
    assertInstanceOf(expected, resolver.resolve(snapshot.entryDocument(), offset));
  }
}
