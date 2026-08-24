package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

final class CoreCompilationBoundaryTest {
  @Test
  void rejectsBindingKindsThatDoNotMatchCoreDefinitions() {
    CoreCompilation compilation =
        compile("Integer value() { return 1 } Void main() { printLine(value()) }");

    assertRejected(
        compilation,
        "value",
        binding -> copy(binding, binding.ownerName(), new CoreBindingShape.Class(0, List.of())));
  }

  @Test
  void rejectsCallableShapesThatDoNotMatchCoreAbi() {
    CoreCompilation compilation =
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
                    shape.typeParameterCount() + 1, shape.parameters(), shape.returnType())));
    assertRejected(
        compilation,
        "identity",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Callable(
                    shape.typeParameterCount(), List.of(), shape.returnType())));
    assertRejected(
        compilation,
        "identity",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Callable(
                    shape.typeParameterCount(), shape.parameters(), CoreType.STRING)));
  }

  @Test
  void rejectsClassShapesThatDoNotMatchCoreAbi() {
    CoreCompilation compilation = compile("class Box { Integer value } Void main() {}");
    CoreBinding binding = binding(compilation, "Box");
    CoreBindingShape.Class shape = (CoreBindingShape.Class) binding.shape();

    assertRejected(
        compilation,
        "Box",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Class(shape.typeParameterCount() + 1, shape.fields())));
    assertRejected(
        compilation,
        "Box",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Class(shape.typeParameterCount(), List.of())));
    assertRejected(
        compilation,
        "Box",
        value ->
            copy(
                value,
                value.ownerName(),
                new CoreBindingShape.Class(
                    shape.typeParameterCount(),
                    List.of(
                        new CoreBindingShape.Field(
                            shape.fields().getFirst().name(),
                            shape.fields().getFirst().visibility(),
                            CoreType.STRING)))));
  }

  @Test
  void rejectsEnumShapesThatDoNotMatchCoreAbi() {
    CoreCompilation compilation = compile("enum State { Ready, Waiting } Void main() {}");

    assertRejected(
        compilation,
        "State",
        binding ->
            copy(
                binding,
                binding.ownerName(),
                new CoreBindingShape.Enum(List.of("Ready", "Stopped"))));
  }

  @Test
  void rejectsCallableKindsThatDisagreeWithReceiverPresence() {
    CoreCompilation compilation =
        compile("class Box { Integer value() { return 1 } } Void main() {}");

    assertRejected(
        compilation, "value", binding -> copy(binding, Optional.empty(), binding.shape()));
  }

  @Test
  void rejectsMethodBindingsForADifferentOwner() {
    CoreCompilation compilation =
        compile("class Box { Integer value() { return 1 } } Void main() {}");

    assertRejected(
        compilation, "value", binding -> copy(binding, Optional.of("Other"), binding.shape()));
  }

  private static CoreCompilation compile(String text) {
    return new Compiler()
        .compile(SourceFile.of(Path.of("boundary.norm"), text))
        .program()
        .orElseThrow()
        .coreCompilation();
  }

  private static CoreBinding binding(CoreCompilation compilation, String name) {
    return compilation.namespace().bindings().stream()
        .filter(binding -> binding.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static void assertRejected(
      CoreCompilation compilation, String name, UnaryOperator<CoreBinding> mutation) {
    List<CoreBinding> bindings =
        compilation.namespace().bindings().stream()
            .map(binding -> binding.name().equals(name) ? mutation.apply(binding) : binding)
            .toList();
    CoreNamespace namespace = CoreNamespace.create(bindings);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreCompilation(
                compilation.program(),
                namespace,
                compilation.authoring(),
                compilation.buildReport(),
                compilation.dependencies(),
                compilation.delta()));
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
