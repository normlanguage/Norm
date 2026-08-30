package dev.w0fv1.norm.value;

import java.util.Objects;

public record SourceLocation(DocumentId document, int startOffset, int endOffset) {
  public SourceLocation {
    Objects.requireNonNull(document, "document");
    if (startOffset < 0 || endOffset < startOffset) {
      throw new IllegalArgumentException(
          "invalid source location [" + startOffset + ", " + endOffset + ")");
    }
  }

  public boolean contains(int offset) {
    return startOffset == endOffset
        ? offset == startOffset
        : startOffset <= offset && offset < endOffset;
  }

  public int length() {
    return endOffset - startOffset;
  }
}
