package dev.w0fv1.norm.value;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class SourceFile {
  private final DocumentId id;
  private final Path path;
  private final String text;
  private final int[] lineStarts;

  private SourceFile(DocumentId id, Path path, String text) {
    this.id = Objects.requireNonNull(id, "id");
    this.path = path == null ? null : path.normalize();
    this.text = Objects.requireNonNull(text, "text");
    this.lineStarts = indexLines(text);
  }

  public static SourceFile of(Path path, String text) {
    Objects.requireNonNull(path, "path");
    Path normalized = path.normalize();
    return new SourceFile(new DocumentId(normalized.toUri()), normalized, text);
  }

  public static SourceFile of(DocumentId id, String text) {
    Objects.requireNonNull(id, "id");
    URI uri = id.uri();
    Path path =
        uri.getScheme() == null
            ? Path.of(uri.toString())
            : uri.getScheme().equalsIgnoreCase("file") ? Path.of(uri) : null;
    return new SourceFile(id, path, text);
  }

  public static SourceFile read(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    Path normalized = path.normalize();
    return new SourceFile(
        new DocumentId(normalized.toUri()),
        normalized,
        Files.readString(normalized, StandardCharsets.UTF_8));
  }

  public DocumentId id() {
    return id;
  }

  public Path path() {
    if (path == null) {
      throw new IllegalStateException("source document is not backed by a file");
    }
    return path;
  }

  public String displayName() {
    return path == null ? id.uri().toString() : path.toString();
  }

  public String text() {
    return text;
  }

  public int length() {
    return text.length();
  }

  public int lineCount() {
    return lineStarts.length;
  }

  public String lineSeparator() {
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '\r') {
        return index + 1 < text.length() && text.charAt(index + 1) == '\n' ? "\r\n" : "\r";
      }
      if (character == '\n') return "\n";
    }
    return "\n";
  }

  public SourcePosition positionAt(int offset) {
    requireOffset(offset);
    int lineIndex = Arrays.binarySearch(lineStarts, offset);
    if (lineIndex < 0) {
      lineIndex = -lineIndex - 2;
    }
    return new SourcePosition(offset, lineIndex + 1, offset - lineStarts[lineIndex] + 1);
  }

  public int offsetAt(int zeroBasedLine, int zeroBasedCharacter) {
    if (zeroBasedLine < 0 || zeroBasedLine >= lineStarts.length || zeroBasedCharacter < 0) {
      throw new IllegalArgumentException("position is outside the source file");
    }
    int start = lineStarts[zeroBasedLine];
    int end =
        zeroBasedLine + 1 == lineStarts.length ? text.length() : lineStarts[zeroBasedLine + 1];
    while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) end--;
    if (zeroBasedCharacter > end - start) {
      throw new IllegalArgumentException("position is outside the source line");
    }
    return start + zeroBasedCharacter;
  }

  public String lineText(int oneBasedLine) {
    if (oneBasedLine < 1 || oneBasedLine > lineStarts.length) {
      throw new IllegalArgumentException("line is outside the source file: " + oneBasedLine);
    }

    int start = lineStarts[oneBasedLine - 1];
    int end = oneBasedLine == lineStarts.length ? text.length() : lineStarts[oneBasedLine];
    while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
      end--;
    }
    return text.substring(start, end);
  }

  private void requireOffset(int offset) {
    if (offset < 0 || offset > text.length()) {
      throw new IllegalArgumentException(
          "offset " + offset + " is outside source length " + text.length());
    }
  }

  private static int[] indexLines(String text) {
    int lineCount = 1;
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '\r') {
        lineCount++;
        if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
          index++;
        }
      } else if (character == '\n') {
        lineCount++;
      }
    }

    int[] starts = new int[lineCount];
    int line = 1;
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
        starts[line++] = ++index + 1;
      } else if (character == '\r' || character == '\n') {
        starts[line++] = index + 1;
      }
    }
    return starts;
  }
}
