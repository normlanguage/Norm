package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class SpanIndexTest {
  @Test
  void matchesShortestContainingSpanReferenceImplementation() {
    SourceFile source = SourceFile.of(DocumentId.of("untitled:index"), "x".repeat(256));
    Random random = new Random(7);
    List<SpanIndex.Entry<Integer>> entries = new ArrayList<>();
    for (int value = 0; value < 500; value++) {
      int start = random.nextInt(source.length() + 1);
      int end = start + random.nextInt(source.length() - start + 1);
      entries.add(new SpanIndex.Entry<>(new SourceSpan(source, start, end), value));
    }
    SpanIndex<Integer> index = SpanIndex.of(entries);

    for (int offset = 0; offset <= source.length(); offset++) {
      int requested = offset;
      Optional<SpanIndex.Entry<Integer>> expected =
          entries.stream()
              .filter(entry -> contains(entry.span(), requested))
              .min(
                  java.util.Comparator.comparingInt(
                          (SpanIndex.Entry<Integer> entry) -> entry.span().length())
                      .thenComparing(
                          entry -> entry.span().startOffset(),
                          java.util.Comparator.reverseOrder()));
      assertEquals(expected, index.at(source.id(), offset));
    }
  }

  private static boolean contains(SourceSpan span, int offset) {
    return span.isEmpty()
        ? span.startOffset() == offset
        : span.startOffset() <= offset && offset < span.endOffset();
  }
}
