package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class CompactGuiSyntaxTest {
  @Test
  void evaluatesUnbracedBranchesLazilyAndPreservesNarrowing() {
    assertEquals(
        "yes\nyes\nmissing\ninner\n",
        NormTestKit.run(
                """
                import std.core.Exception
                String choose(Boolean editing, String? title) {
                  if editing "yes" else if title != null title else "missing"
                }
                Void main() {
                  printLine(choose(editing: true, title: null))
                  printLine(if true "yes" else throw Exception(message: "unreachable"))
                  printLine(choose(editing: false, title: null))
                  printLine(if true if false "wrong" else "inner" else "outer")
                }
                """)
            .replace("\r\n", "\n"));
  }

  @Test
  void requiresAValueOnEveryNormalPath() {
    assertFalse(
        NormTestKit.compile("String missing(Boolean flag) { if flag \"yes\" }").isSuccess());
  }

  @Test
  void parenthesizedConditionsSeparateUnaryAndGroupedBranches() {
    String source =
        """
        Void main() {
          printLine(if (true) -1 else -2)
          printLine(if (false) (1 + 2) else 4)
        }
        """;
    var formatted =
        new SourceFormatter()
            .format(
                dev.w0fv1.norm.source.SourceFile.of(java.nio.file.Path.of("branches.norm"), source))
            .orElseThrow();
    assertEquals("-1\n4\n", NormTestKit.run(source).replace("\r\n", "\n"));
    assertEquals("-1\n4\n", NormTestKit.run(formatted).replace("\r\n", "\n"));
  }

  @Test
  void blankPropertyUsesUnicodeWhitespace() {
    assertEquals(
        "true\ntrue\ntrue\nfalse\nfalse\n",
        NormTestKit.run(
                """
                Void main() {
                  printLine("".isBlank)
                  printLine(" \\t\\n".isBlank)
                  printLine("　".isBlank)
                  printLine("任务".isBlank)
                  printLine(" ".isBlank)
                }
                """)
            .replace("\r\n", "\n"));
  }
}
