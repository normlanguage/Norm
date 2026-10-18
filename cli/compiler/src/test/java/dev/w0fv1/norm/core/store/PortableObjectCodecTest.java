package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreBlock;
import dev.w0fv1.norm.core.CoreCompilationInput;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PortableObjectCodecTest {
  @Test
  void retainsContentIdentityAndExactLinkageAcrossPortableRoundTrips() throws Exception {
    var statements = new java.util.ArrayList<CoreStatement>();
    var targets = List.of(7, 9, 7);
    for (int index = 0; index < targets.size(); index++) {
      statements.add(
          new CoreStatement.ExpressionStatement(
              index * 2 + 1,
              new CoreExpression.Call(
                  index * 2 + 2,
                  new PendingDefinitionReference(targets.get(index)),
                  Optional.empty(),
                  List.of(),
                  List.of(),
                  false,
                  CoreType.VOID)));
    }
    var definition =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            CoreType.VOID,
            List.of(),
            new CoreBlock(0, statements));
    var original = new CoreCompilationInput.Source(definition);
    var relocated = original.relocate(index -> index + 100);
    var merged = original.relocate(index -> 17);
    assertEquals(original.content().id(), relocated.content().id());
    assertEquals(original.content().id(), merged.content().id());
    assertEquals(List.of(7, 9), original.linkage().declarations());
    assertEquals(List.of(107, 109), relocated.linkage().declarations());
    assertEquals(List.of(17, 17), merged.linkage().declarations());
    assertNotEquals(original.definition(), relocated.definition());
    var payload = new Sources(List.of(original, relocated, merged));
    byte[] encoded = PortableObjectCodec.encodeDeterministic(payload);
    var decoded = PortableObjectCodec.decodeDeterministic(encoded, Sources.class);
    assertEquals(original.content().id(), decoded.values().getFirst().content().id());
    assertEquals(relocated.content().id(), decoded.values().get(1).content().id());
    assertEquals(merged.content().id(), decoded.values().getLast().content().id());
    assertEquals(original.linkage(), decoded.values().getFirst().linkage());
    assertEquals(relocated.linkage(), decoded.values().get(1).linkage());
    assertEquals(merged.linkage(), decoded.values().getLast().linkage());
    assertEquals(original.definition(), decoded.values().getFirst().definition());
    assertEquals(relocated.definition(), decoded.values().get(1).definition());
    assertEquals(merged.definition(), decoded.values().getLast().definition());
    assertArrayEquals(encoded, PortableObjectCodec.encodeDeterministic(decoded));
    var ordinary = PortableObjectCodec.decode(PortableObjectCodec.encode(payload), Sources.class);
    assertEquals(original.definition(), ordinary.values().getFirst().definition());
    assertEquals(relocated.definition(), ordinary.values().get(1).definition());
    assertEquals(merged.content().id(), ordinary.values().getLast().content().id());
    assertEquals(merged.linkage(), ordinary.values().getLast().linkage());
    assertEquals(merged.definition(), ordinary.values().getLast().definition());
  }

  record Sources(List<CoreCompilationInput.Source> values) {
    Sources {
      values = List.copyOf(values);
    }
  }

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
