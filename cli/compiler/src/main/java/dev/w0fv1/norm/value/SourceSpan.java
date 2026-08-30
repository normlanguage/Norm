package dev.w0fv1.norm.value;

import java.util.Objects;

public record SourceSpan(SourceFile source, int startOffset, int endOffset) {
  public SourceSpan {
    Objects.requireNonNull(source, "source");
    if (startOffset < 0 || endOffset < startOffset || endOffset > source.length()) {
      throw new IllegalArgumentException(
          "invalid source span ["
              + startOffset
              + ", "
              + endOffset
              + ") for source length "
              + source.length());
    }
  }

  public static SourceSpan at(SourceFile source, int offset) {
    return new SourceSpan(source, offset, offset);
  }

  public int length() {
    return endOffset - startOffset;
  }

  public boolean isEmpty() {
    return startOffset == endOffset;
  }

  public SourcePosition start() {
    return source.positionAt(startOffset);
  }

  public SourcePosition end() {
    return source.positionAt(endOffset);
  }

  public String text() {
    return source.text().substring(startOffset, endOffset);
  }

  public SourceLocation location() {
    return new SourceLocation(source.id(), startOffset, endOffset);
  }

  public SourceSpan cover(SourceSpan other) {
    Objects.requireNonNull(other, "other");
    if (source != other.source) {
      throw new IllegalArgumentException("cannot combine spans from different source files");
    }
    return new SourceSpan(
        source, Math.min(startOffset, other.startOffset), Math.max(endOffset, other.endOffset));
  }
}
