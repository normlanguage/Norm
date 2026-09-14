package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PortableObjectCodecTest {
  @Test
  void storesRepeatedSourceLocationsWithoutRepeatingSourceText() throws Exception {
    var document = dev.w0fv1.norm.source.DocumentId.of("norm-module:/sample/1/main.norm");
    String text = "Integer value = 41\n".repeat(1500);
    var source = dev.w0fv1.norm.source.SourceFile.of(document, text);
    var shared =
        new Locations(
            java.util.stream.IntStream.range(0, 1000)
                .mapToObj(index -> new dev.w0fv1.norm.source.SourceSpan(source, index, index + 1))
                .toList());
    var independent =
        new Locations(
            java.util.stream.IntStream.range(0, 1000)
                .mapToObj(
                    index ->
                        new dev.w0fv1.norm.source.SourceSpan(
                            dev.w0fv1.norm.source.SourceFile.of(document, text), index, index + 1))
                .toList());
    byte[] encoded = PortableObjectCodec.encodeDeterministic(shared);
    assertTrue(encoded.length < 100_000, "encoded bytes: " + encoded.length);
    assertArrayEquals(encoded, PortableObjectCodec.encodeDeterministic(independent));
    assertEquals(shared, PortableObjectCodec.decodeDeterministic(encoded, Locations.class));
  }

  record Locations(List<dev.w0fv1.norm.source.SourceSpan> spans) {}

  @Test
  void deterministicEncodingIgnoresMapAndSetIterationAndObjectSharing() throws Exception {
    var firstMap = new LinkedHashMap<String, Set<String>>();
    var secondMap = new LinkedHashMap<String, Set<String>>();
    var shared = new LinkedHashSet<>(List.of("z", "a"));
    firstMap.put("second", shared);
    firstMap.put("first", shared);
    secondMap.put("first", new LinkedHashSet<>(List.of("a", "z")));
    secondMap.put("second", new LinkedHashSet<>(List.of("a", "z")));
    var first = new Payload(firstMap);
    var second = new Payload(secondMap);
    byte[] encoded = PortableObjectCodec.encodeDeterministic(first);
    assertArrayEquals(encoded, PortableObjectCodec.encodeDeterministic(second));
    assertEquals(first, PortableObjectCodec.decodeDeterministic(encoded, Payload.class));
  }

  record Payload(Map<String, Set<String>> values) {}
}
