package dev.w0fv1.norm.documentation;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MarkdownReferencesTest {
  @Test
  void readsVersionedReferencesWithExactMarkdownLocations() {
    String text =
        "说明 **@github.h2.database.api.open#1**。\r\n> @{std.math.integer.clamp(Integer,Integer,Integer)#1}\n";
    var references = new MarkdownReferences().parse(SourceFile.of(Path.of("guide.md"), text));
    assertEquals(2, references.size());
    assertEquals("github.h2.database.api.open#1", references.getFirst().text());
    assertEquals(text.indexOf('@'), references.getFirst().span().startOffset());
    assertEquals(2, references.getLast().span().start().line());
    assertEquals("std.math.integer.clamp(Integer,Integer,Integer)#1", references.getLast().text());
  }

  @Test
  void ignoresCodeEscapesEmailsAndHtmlWhileKeepingLinkLabels() {
    String text =
        """
        `@github.sample.api.Type#1`
        \\@github.sample.api.Type#1
        user@github.sample.api.Type#1
        &#64;github.sample.api.Type#1
        <!-- @github.sample.api.Type#1 -->

        ```norm
        @github.sample.api.Type#1
        ```

            @github.sample.api.Type#1

        [@github.sample.api.Type#2](https://example.com/@github.sample.api.Type#3)
        """;
    var references = new MarkdownReferences().parse(SourceFile.of(Path.of("guide.md"), text));
    assertEquals(1, references.size());
    assertEquals("github.sample.api.Type#2", references.getFirst().text());
  }

  @Test
  void rejectsUnclosedBracedReferencesAndInvalidVersionSuffixes() {
    var references =
        new MarkdownReferences()
            .parse(
                SourceFile.of(
                    Path.of("guide.md"),
                    "@{github.sample.api.Type#1\n@github.sample.api.Type#1.2\n@github.sample.api.Type#2147483648\n"));
    assertEquals(3, references.size());
    for (var reference : references)
      assertThrows(IllegalArgumentException.class, reference::target);
  }

  @Test
  void retainsMalformedReferencesForDiagnostics() {
    var references =
        new MarkdownReferences()
            .parse(
                SourceFile.of(
                    Path.of("guide.md"),
                    "@github.sample.api.Type @github.sample.api.Type#0 @{github.sample.api.run(Integer)#abc}"));
    assertEquals(3, references.size());
    for (var reference : references) {
      assertThrows(IllegalArgumentException.class, reference::target);
    }
  }
}
