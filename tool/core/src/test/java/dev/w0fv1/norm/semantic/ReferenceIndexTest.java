package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ReferenceIndexTest {
  @Test
  void groupsReferencesByCanonicalSymbolAndStableDocumentOrder() {
    SourceFile second = SourceFile.of(DocumentId.of("file:///b.norm"), "use");
    SourceFile first = SourceFile.of(DocumentId.of("file:///a.norm"), "decl use");
    SymbolId symbol = new SymbolId("function/value");
    Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
    bindings.put(new SourceSpan(second, 0, 3), symbol);
    bindings.put(new SourceSpan(first, 5, 8), symbol);
    bindings.put(new SourceSpan(first, 0, 4), symbol);

    ReferenceIndex index = ReferenceIndex.from(bindings, Map.of());

    assertEquals(
        List.of(
            new SourceSpan(first, 0, 4), new SourceSpan(first, 5, 8), new SourceSpan(second, 0, 3)),
        index.references(symbol));
  }
}
