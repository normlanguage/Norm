package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SpanIndex<T> {
  private static final Comparator<Entry<?>> ORDER =
      Comparator.<Entry<?>, Integer>comparing(entry -> entry.span().length())
          .thenComparing(entry -> entry.span().startOffset(), Comparator.reverseOrder())
          .thenComparingInt(entry -> entry.span().endOffset());
  private final Map<DocumentId, Node<T>> roots;

  private SpanIndex(Map<DocumentId, Node<T>> roots) {
    this.roots = roots;
  }

  public static <T> SpanIndex<T> of(List<Entry<T>> values) {
    Objects.requireNonNull(values, "values");
    Map<DocumentId, List<Entry<T>>> grouped = new LinkedHashMap<>();
    values.forEach(
        entry ->
            grouped
                .computeIfAbsent(entry.span().source().id(), ignored -> new ArrayList<>())
                .add(entry));
    return new SpanIndex<>(
        grouped.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Node.build(entry.getValue()))));
  }

  public static <T> SpanIndex<T> from(Map<SourceSpan, T> values) {
    return of(
        values.entrySet().stream()
            .map(entry -> new Entry<>(entry.getKey(), entry.getValue()))
            .toList());
  }

  public Optional<Entry<T>> at(DocumentId document, int offset) {
    Objects.requireNonNull(document, "document");
    Node<T> root = roots.get(document);
    return root == null ? Optional.empty() : root.at(offset, Optional.empty());
  }

  private static boolean contains(SourceSpan span, int offset) {
    return span.isEmpty()
        ? offset == span.startOffset()
        : span.startOffset() <= offset && offset < span.endOffset();
  }

  public record Entry<T>(SourceSpan span, T value) {
    public Entry {
      Objects.requireNonNull(span, "span");
      Objects.requireNonNull(value, "value");
    }
  }

  private static final class Node<T> {
    private final int center;
    private final List<Entry<T>> byStart;
    private final List<Entry<T>> byEnd;
    private final Node<T> left;
    private final Node<T> right;

    private Node(
        int center, List<Entry<T>> byStart, List<Entry<T>> byEnd, Node<T> left, Node<T> right) {
      this.center = center;
      this.byStart = byStart;
      this.byEnd = byEnd;
      this.left = left;
      this.right = right;
    }

    private static <T> Node<T> build(List<Entry<T>> entries) {
      if (entries.isEmpty()) return null;
      List<Integer> points =
          entries.stream()
              .flatMap(
                  entry ->
                      java.util.stream.Stream.of(
                          entry.span().startOffset(), entry.span().endOffset()))
              .sorted()
              .toList();
      int center = points.get(points.size() / 2);
      List<Entry<T>> left = new ArrayList<>();
      List<Entry<T>> right = new ArrayList<>();
      List<Entry<T>> crossing = new ArrayList<>();
      for (Entry<T> entry : entries) {
        if (entry.span().endOffset() < center) {
          left.add(entry);
        } else if (entry.span().startOffset() > center) {
          right.add(entry);
        } else {
          crossing.add(entry);
        }
      }
      List<Entry<T>> byStart =
          crossing.stream()
              .sorted(
                  Comparator.comparingInt((Entry<T> entry) -> entry.span().startOffset())
                      .thenComparingInt(entry -> entry.span().endOffset()))
              .toList();
      List<Entry<T>> byEnd =
          crossing.stream()
              .sorted(
                  Comparator.comparingInt((Entry<T> entry) -> entry.span().endOffset())
                      .reversed()
                      .thenComparingInt(entry -> entry.span().startOffset()))
              .toList();
      return new Node<>(center, byStart, byEnd, build(left), build(right));
    }

    private Optional<Entry<T>> at(int offset, Optional<Entry<T>> current) {
      Optional<Entry<T>> best = current;
      List<Entry<T>> candidates = offset <= center ? byStart : byEnd;
      for (Entry<T> entry : candidates) {
        if (offset < center && entry.span().startOffset() > offset) break;
        if (offset > center && entry.span().endOffset() < offset) break;
        if (contains(entry.span(), offset)
            && (best.isEmpty() || ORDER.compare(entry, best.orElseThrow()) < 0)) {
          best = Optional.of(entry);
        }
      }
      if (offset < center && left != null) return left.at(offset, best);
      if (offset > center && right != null) return right.at(offset, best);
      return best;
    }
  }
}
