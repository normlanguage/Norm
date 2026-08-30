package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SourceLocationTest {
  @Test
  void preservesNonFileDocumentIdentity() {
    DocumentId id = DocumentId.of("untitled:Untitled-1");
    SourceFile source = SourceFile.of(id, "Void main() {}");
    SourceSpan span = new SourceSpan(source, 5, 9);

    assertEquals(new SourceLocation(id, 5, 9), span.location());
    assertEquals("untitled:Untitled-1", source.displayName());
    assertThrows(IllegalStateException.class, source::path);
  }

  @Test
  void usesUtf16OffsetsForPositions() {
    SourceFile source = SourceFile.of(DocumentId.of("untitled:unicode"), "a😀b\r\nc");

    assertEquals(new SourcePosition(3, 1, 4), source.positionAt(3));
    assertEquals(new SourcePosition(6, 2, 1), source.positionAt(6));
    assertEquals(3, source.offsetAt(0, 3));
    assertEquals(6, source.offsetAt(1, 0));
  }
}
