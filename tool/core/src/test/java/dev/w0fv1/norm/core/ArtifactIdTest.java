package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ArtifactIdTest {
  @Test
  void includesTheNamespaceDefinitionMapping() {
    CoreArtifact original =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("artifact.norm"),
                    "Integer first() { return 1 } Integer second() { return 2 } "
                        + "Void main() { printLine(first()) }"))
            .program()
            .orElseThrow()
            .compilation()
            .artifact();
    DefinitionId first = original.namespace().definition("", "first").orElseThrow();
    DefinitionId second = original.namespace().definition("", "second").orElseThrow();
    List<CoreBinding> swappedBindings =
        original.namespace().bindings().stream()
            .map(
                binding ->
                    new CoreBinding(
                        binding.packageName(),
                        binding.ownerName(),
                        binding.name(),
                        binding.visibility(),
                        binding.shape(),
                        binding.definition().equals(first)
                            ? original.namespace().occurrence("", "second").orElseThrow()
                            : binding.definition().equals(second)
                                ? original.namespace().occurrence("", "first").orElseThrow()
                                : binding.occurrence(),
                        binding.exported()))
            .toList();
    CoreNamespace swappedNamespace = CoreNamespace.create(swappedBindings);
    CoreArtifact swapped =
        new CoreArtifact(original.program(), swappedNamespace, original.authoring());

    assertEquals(original.namespace().id(), swappedNamespace.id());
    assertNotEquals(
        ArtifactId.forArtifact(original, "test"), ArtifactId.forArtifact(swapped, "test"));
  }

  @Test
  void includesAuthoringRoutesBetweenSharedDefinitions() {
    SourceFile first = SourceFile.of(Path.of("route/a.norm"), "Integer alpha() { return 1 / 0 }");
    SourceFile second = SourceFile.of(Path.of("route/z.norm"), "Integer beta() { return 1 / 0 }");
    SourceFile entry =
        SourceFile.of(Path.of("route/main.norm"), "Void main() { printLine(alpha()) }");
    CoreArtifact original =
        new CompilerSession()
            .compile(new CompilationRequest(entry.id(), List.of(first, second, entry)))
            .program()
            .orElseThrow()
            .compilation()
            .artifact();
    DefinitionOccurrenceId alpha = original.namespace().occurrence("", "alpha").orElseThrow();
    DefinitionOccurrenceId beta = original.namespace().occurrence("", "beta").orElseThrow();
    assertEquals(alpha.representative(), beta.representative());
    List<CoreDefinitionOccurrence> reroutedOccurrences =
        original.authoring().occurrences().stream()
            .map(
                occurrence -> {
                  if (!occurrence.id().equals(original.entryPoint())) return occurrence;
                  Map<Integer, DefinitionOccurrenceId> references =
                      occurrence.references().entrySet().stream()
                          .collect(
                              java.util.stream.Collectors.toMap(
                                  Map.Entry::getKey,
                                  reference ->
                                      reference.getValue().equals(alpha)
                                          ? beta
                                          : reference.getValue()));
                  return new CoreDefinitionOccurrence(
                      occurrence.id(),
                      occurrence.representedDefinitions(),
                      occurrence.origin(),
                      references);
                })
            .toList();
    CoreArtifact rerouted =
        new CoreArtifact(
            original.program(),
            original.namespace(),
            new CoreAuthoringMap(reroutedOccurrences, original.entryPoint()));

    assertNotEquals(
        ArtifactId.forArtifact(original, "test"), ArtifactId.forArtifact(rerouted, "test"));
  }
}
