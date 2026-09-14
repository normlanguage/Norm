package dev.w0fv1.norm.syntax;

import dev.w0fv1.norm.source.SourceLocation;
import dev.w0fv1.norm.source.SourceSpan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TokenSpanMapping {
  public static TokenSpanMapping relocate(
      dev.w0fv1.norm.source.SourceFile previous, dev.w0fv1.norm.source.SourceFile current) {
    if (!previous.text().equals(current.text()))
      throw new IllegalArgumentException("relocated sources must have identical content");
    return new TokenSpanMapping(
        new SourceSpan(previous, 0, previous.length()),
        new SourceSpan(current, 0, current.length()),
        List.of(),
        List.of());
  }

  private final SourceSpan previousRoot;
  private final SourceSpan currentRoot;
  private final java.util.NavigableMap<Integer, Integer> anchors = new java.util.TreeMap<>();
  private final Map<Integer, Integer> starts = new LinkedHashMap<>();
  private final Map<Integer, Integer> ends = new LinkedHashMap<>();

  public TokenSpanMapping(
      SourceSpan previousRoot,
      SourceSpan currentRoot,
      List<Token> previousTokens,
      List<Token> currentTokens) {
    this.previousRoot = previousRoot;
    this.currentRoot = currentRoot;
    if (previousTokens.size() != currentTokens.size()) {
      throw new IllegalArgumentException("token mapping tokens must have equal shape");
    }
    anchor(starts, previousRoot.startOffset(), currentRoot.startOffset());
    for (int index = 0; index < previousTokens.size(); index++) {
      Token previous = previousTokens.get(index);
      Token current = currentTokens.get(index);
      if (previous.kind() != current.kind() || !previous.lexeme().equals(current.lexeme())) {
        throw new IllegalArgumentException("token mapping tokens must have equal shape");
      }
      anchor(starts, previous.span().startOffset(), current.span().startOffset());
      anchor(ends, previous.span().endOffset(), current.span().endOffset());
    }
    anchor(ends, previousRoot.endOffset(), currentRoot.endOffset());
  }

  public SourceSpan previousRoot() {
    return previousRoot;
  }

  public SourceSpan currentRoot() {
    return currentRoot;
  }

  private void anchor(Map<Integer, Integer> boundaries, int previous, int current) {
    anchors.putIfAbsent(previous, current);
    Integer existing = boundaries.putIfAbsent(previous, current);
    if (existing != null && existing != current) {
      throw new IllegalArgumentException("token mapping has inconsistent token anchors");
    }
  }

  public SourceSpan rebase(SourceSpan span) {
    return new SourceSpan(
        currentRoot.source(),
        map(span.startOffset(), false),
        map(span.endOffset(), !span.isEmpty()),
        span.expansion());
  }

  public SourceLocation rebase(SourceLocation location) {
    return new SourceLocation(
        currentRoot.source().id(),
        map(location.startOffset(), false),
        map(location.endOffset(), location.endOffset() != location.startOffset()));
  }

  private int map(int offset, boolean end) {
    if (offset < previousRoot.startOffset() || offset > previousRoot.endOffset()) {
      throw new IllegalArgumentException("mapped span is outside its declaration");
    }
    Integer exact = (end ? ends : starts).get(offset);
    if (exact == null) exact = (end ? starts : ends).get(offset);
    if (exact != null) return exact;
    Map.Entry<Integer, Integer> lower = anchors.floorEntry(offset);
    Map.Entry<Integer, Integer> upper = anchors.ceilingEntry(offset);
    if (lower == null || upper == null) {
      throw new IllegalStateException("token mapping has incomplete token anchors");
    }
    int relative = offset - lower.getKey();
    int lowerValue = starts.getOrDefault(lower.getKey(), lower.getValue());
    int upperValue = ends.getOrDefault(upper.getKey(), upper.getValue());
    return lowerValue + Math.min(relative, upperValue - lowerValue);
  }
}
