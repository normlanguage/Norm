package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

final class CoreArtifactBoundaryTest {
  @Test
  void rejectsBindingKindsThatDoNotMatchCoreDefinitions() {
    CoreArtifact compilation =
        compile("Integer value() { return 1 } Void main() { printLine(value()) }");

    assertRejected(
        compilation,
        "value",
        binding ->
            copy(
                binding,
                binding.ownerName(),
                new CoreBindingShape.Aggregate(
                    CoreValueCategory.IDENTITY, List.of(), List.of(), List.of())));
  }

  @Test
  void rejectsCallableShapesThatDoNotMatchCoreAbi() {
    CoreArtifact compilation =
        compile("Integer identity(Integer value) { return value } Void main() {}");
    CoreBinding binding = binding(compilation, "identity");
    CoreBindingShape.Callable shape = (CoreBindingShape.Callable) binding.shape();

    assertRejected(
        compilation,
        "identity",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Callable(
                    List.of(new CoreTypeParameter(0, Optional.empty())),
                    shape.parameters(),
                    shape.returnType())));
    assertRejected(
        compilation,
        "identity",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Callable(
                    shape.typeParameters(), List.of(), shape.returnType())));
    assertRejected(
        compilation,
        "identity",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Callable(
                    shape.typeParameters(), shape.parameters(), CoreType.STRING)));
  }

  @Test
  void rejectsClassShapesThatDoNotMatchCoreAbi() {
    CoreArtifact compilation = compile("class Box { Integer value } Void main() {}");
    CoreBinding binding = binding(compilation, "Box");
    CoreBindingShape.Aggregate shape = (CoreBindingShape.Aggregate) binding.shape();

    assertRejected(
        compilation,
        "Box",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Aggregate(
                    CoreValueCategory.IDENTITY,
                    List.of(new CoreTypeParameter(0, Optional.empty())),
                    shape.fields(),
                    shape.conformances())));
    assertRejected(
        compilation,
        "Box",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Aggregate(
                    CoreValueCategory.IDENTITY,
                    shape.typeParameters(),
                    List.of(),
                    shape.conformances())));
    assertRejected(
        compilation,
        "Box",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Aggregate(
                    CoreValueCategory.IDENTITY,
                    shape.typeParameters(),
                    List.of(
                        new CoreBindingShape.Field(
                            shape.fields().getFirst().name(),
                            shape.fields().getFirst().visibility(),
                            CoreType.STRING)),
                    shape.conformances())));
  }

  @Test
  void rejectsEnumShapesThatDoNotMatchCoreAbi() {
    CoreArtifact compilation = compile("enum State { Ready, Waiting } Void main() {}");

    assertRejected(
        compilation,
        "State",
        binding ->
            copy(
                binding,
                binding.ownerName(),
                new CoreBindingShape.Enum(
                    List.of(),
                    List.of(
                        new CoreBindingShape.Variant("Ready", List.of()),
                        new CoreBindingShape.Variant("Stopped", List.of())))));
  }

  @Test
  void rejectsCallableKindsThatDisagreeWithReceiverPresence() {
    CoreArtifact compilation = compile("class Box { Integer value() { return 1 } } Void main() {}");

    assertRejected(
        compilation, "value", binding -> copy(binding, Optional.empty(), binding.shape()));
  }

  @Test
  void rejectsMethodBindingsForADifferentOwner() {
    CoreArtifact compilation = compile("class Box { Integer value() { return 1 } } Void main() {}");

    assertRejected(
        compilation, "value", binding -> copy(binding, Optional.of("Other"), binding.shape()));
  }

  private static CoreArtifact compile(String text) {
    return new CompilerSession()
        .compile(SourceFile.of(Path.of("boundary.norm"), text))
        .program()
        .orElseThrow()
        .compilation()
        .artifact();
  }

  private static CoreBinding binding(CoreArtifact compilation, String name) {
    return compilation.namespace().bindings().stream()
        .filter(binding -> binding.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static void assertRejected(
      CoreArtifact compilation, String name, UnaryOperator<CoreBinding> mutation) {
    List<CoreBinding> bindings =
        compilation.namespace().bindings().stream()
            .map(binding -> binding.name().equals(name) ? mutation.apply(binding) : binding)
            .toList();
    CoreNamespace namespace = CoreNamespace.create(bindings);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CoreArtifact(compilation.program(), namespace, compilation.authoring()));
  }

  private static CoreBinding copy(
      CoreBinding binding, Optional<String> ownerName, CoreBindingShape shape) {
    return new CoreBinding(
        binding.packageName(),
        ownerName,
        binding.name(),
        binding.visibility(),
        shape,
        binding.occurrence(),
        binding.exported());
  }
}
