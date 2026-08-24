package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreAuthoringMapTest {
  @Test
  void rejectsNonCanonicalOccurrenceOrdinals() {
    DefinitionId definition = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    SourceFile firstSource = SourceFile.of(Path.of("first.norm"), "Void first() {}");
    SourceFile secondSource = SourceFile.of(Path.of("second.norm"), "Void second() {}");
    CoreDefinitionOrigin first =
        new CoreDefinitionOrigin(
            "first", new SourceSpan(firstSource, 0, firstSource.length()), Map.of());
    CoreDefinitionOrigin second =
        new CoreDefinitionOrigin(
            "second", new SourceSpan(secondSource, 0, secondSource.length()), Map.of());
    CoreDefinitionOccurrence wrongFirst =
        new CoreDefinitionOccurrence(
            new DefinitionOccurrenceId(definition, 1), Set.of(definition), first, Map.of());
    CoreDefinitionOccurrence wrongSecond =
        new CoreDefinitionOccurrence(
            new DefinitionOccurrenceId(definition, 0), Set.of(definition), second, Map.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreAuthoringMap(List.of(wrongFirst, wrongSecond), wrongFirst.id()));
  }
}
