package dev.w0fv1.norm.core;

import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.value.TestAbi;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record CoreTestIndex(List<Test> tests) {
  public CoreTestIndex {
    tests = List.copyOf(tests);
  }

  public static CoreTestIndex from(CoreArtifact artifact) {
    var annotation = artifact.namespace().definition(TestAbi.PACKAGE, TestAbi.NAME);
    if (annotation.isEmpty()) return new CoreTestIndex(List.of());
    Set<DefinitionOccurrenceId> marked =
        artifact.metadata().annotations().stream()
            .filter(application -> application.annotation().equals(annotation.orElseThrow()))
            .map(CoreAnnotationApplication::target)
            .filter(CoreAnnotationTarget.Definition.class::isInstance)
            .map(CoreAnnotationTarget.Definition.class::cast)
            .map(CoreAnnotationTarget.Definition::occurrence)
            .collect(java.util.stream.Collectors.toSet());
    return new CoreTestIndex(
        artifact.namespace().bindings().stream()
            .filter(binding -> marked.contains(binding.occurrence()))
            .map(
                binding ->
                    new Test(
                        binding.occurrence(),
                        binding.packageName().isEmpty()
                            ? binding.name()
                            : binding.packageName() + "." + binding.name(),
                        artifact.authoring().origin(binding.occurrence()).rootSpan()))
            .sorted(
                Comparator.comparing(Test::name)
                    .thenComparing(test -> test.source().source().id().uri().toString()))
            .toList());
  }

  public record Test(DefinitionOccurrenceId occurrence, String name, SourceSpan source) {}
}
