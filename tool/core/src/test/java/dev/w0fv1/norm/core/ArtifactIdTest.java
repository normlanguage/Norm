package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArtifactIdTest {
  @Test
  void includesAnnotationMetadataValues() {
    CoreArtifact first =
        compile(
            "package std.annotation public interface AnnotationTarget {} "
                + "public interface TypeTarget extends AnnotationTarget {} "
                + "public interface AnnotationRetention {} "
                + "public interface BinaryRetention extends AnnotationRetention {} "
                + "annotation Label implements TypeTarget, BinaryRetention { String text } "
                + "@Label(text: \"first\") class Point {} Void main() {}");
    CoreArtifact second =
        compile(
            "package std.annotation public interface AnnotationTarget {} "
                + "public interface TypeTarget extends AnnotationTarget {} "
                + "public interface AnnotationRetention {} "
                + "public interface BinaryRetention extends AnnotationRetention {} "
                + "annotation Label implements TypeTarget, BinaryRetention { String text } "
                + "@Label(text: \"second\") class Point {} Void main() {}");

    assertNotEquals(ArtifactId.forArtifact(first, "test"), ArtifactId.forArtifact(second, "test"));
  }

  @Test
  void canonicalizesEveryAnnotationTargetVariantIndependentlyOfInputOrder() {
    CoreArtifact original =
        compile(
            "@Marker() package std.annotation "
                + "public interface AnnotationTarget {} "
                + "public interface PackageTarget extends AnnotationTarget {} "
                + "public interface TypeTarget extends AnnotationTarget {} "
                + "public interface FieldTarget<T> extends AnnotationTarget { "
                + "T before(FieldContext context, T value) { return value } "
                + "Void after(FieldContext context, FunctionCompletion completion) {} } "
                + "public interface ConstructorTarget extends AnnotationTarget {} "
                + "public interface ParameterTarget<T> extends AnnotationTarget { "
                + "T before(ParameterContext context, T value) { return value } "
                + "Void after(ParameterContext context, FunctionCompletion completion) {} } "
                + "public interface LocalTarget extends AnnotationTarget {} "
                + "public interface AnnotationRetention {} "
                + "public interface BinaryRetention extends AnnotationRetention {} "
                + "public interface FunctionTarget extends AnnotationTarget { "
                + "Void before(FunctionContext context) {} "
                + "R around<R>(FunctionInvocation<R> invocation) { return invocation.proceed() } "
                + "Void after(FunctionContext context, FunctionCompletion completion) {} } "
                + "annotation Marker implements PackageTarget, TypeTarget, FieldTarget<Integer>, "
                + "ConstructorTarget, FunctionTarget, ParameterTarget<Integer>, LocalTarget, BinaryRetention {} "
                + "@Marker() class Box { @Marker() Integer value @Marker() Box(@Marker() Integer value) { this.value = value } } "
                + "@Marker() Integer read(@Marker() Integer input) { @Marker() Integer copy = input return copy } "
                + "Void main() {}");
    List<CoreAnnotationApplication> reversed =
        new java.util.ArrayList<>(original.metadata().annotations());
    java.util.Collections.reverse(reversed);
    CoreArtifact reordered =
        new CoreArtifact(
            original.program(),
            original.namespace(),
            original.authoring(),
            new CoreMetadata(reversed));

    assertEquals(
        java.util.Set.of(
            CoreAnnotationTarget.Package.class,
            CoreAnnotationTarget.Definition.class,
            CoreAnnotationTarget.Field.class,
            CoreAnnotationTarget.Parameter.class,
            CoreAnnotationTarget.Local.class),
        original.metadata().annotations().stream()
            .map(value -> value.target().getClass())
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals(
        ArtifactId.forArtifact(original, "test"), ArtifactId.forArtifact(reordered, "test"));
  }

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
        new CoreArtifact(
            original.program(), swappedNamespace, original.authoring(), original.metadata());

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
                      occurrence.role(),
                      occurrence.origin(),
                      references);
                })
            .toList();
    CoreArtifact rerouted =
        new CoreArtifact(
            original.program(),
            original.namespace(),
            new CoreAuthoringMap(reroutedOccurrences, original.entryPoint()),
            original.metadata());

    assertNotEquals(
        ArtifactId.forArtifact(original, "test"), ArtifactId.forArtifact(rerouted, "test"));
  }

  private static CoreArtifact compile(String source) {
    SourceFile file = SourceFile.of(Path.of("artifact-annotations.norm"), source);
    return new CompilerSession()
        .compile(
            new CompilationRequest(
                new CompilationUnitId(file.id().uri()),
                CompilationScope.module(
                    new ModuleCoordinate("std", 1), Map.of(file.id(), "artifact-annotations.norm")),
                file.id(),
                List.of(file),
                Set.of()))
        .program()
        .orElseThrow()
        .compilation()
        .artifact();
  }
}
