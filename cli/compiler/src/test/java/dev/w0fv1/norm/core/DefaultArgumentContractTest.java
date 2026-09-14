package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DefaultArgumentContractTest {
  @Test
  void preservesDefaultContractsWithoutExecutingUnusedImplementations() {
    var library =
        compile(
            """
        Integer defaultNumber() { printLine("unused"); 42 }
        Integer number(Integer value = defaultNumber()) { value }
        Void main() { printLine(number(7)) }
        """);
    var artifact = library.withEntryPoint(library.namespace().occurrence("", "main").orElseThrow());
    var analysis = CoreReachability.analyze(artifact, Set.of());
    var reduced = analysis.artifact();
    var number =
        reduced.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("number"))
            .findFirst()
            .orElseThrow();
    var factory =
        ((CoreDefaultArgument.Resolved)
                number.shape().parameters().getFirst().defaultValue().orElseThrow())
            .occurrence();
    assertTrue(
        CoreDependencyIndex.create(reduced).dependenciesOf(number.occurrence()).contains(factory));
    assertEquals(
        CoreReachability.RetentionKind.DEFAULT_ARGUMENT,
        analysis.causes().get(factory.representative().group()).kind());
    assertFalse(
        CoreExecutionPlan.forArtifact(reduced).callables().contains(factory.representative()));
    var output = new java.io.StringWriter();
    new dev.w0fv1.norm.runtime.NormRuntime().run(reduced, new java.io.PrintWriter(output));
    assertEquals("7" + System.lineSeparator(), output.toString());
  }

  @Test
  void preservesGenericBoundsAndSeparatesDefaultCodeFromThePublicSignature() {
    var first = compile("public Integer number(Integer value = 41) { value }");
    var second = compile("public Integer number(Integer value = 42) { value }");
    assertEquals(PublicAbiId.forArtifact(first), PublicAbiId.forArtifact(second));
    assertNotEquals(CoreCodeId.forArtifact(first), CoreCodeId.forArtifact(second));
    assertDoesNotThrow(
        () ->
            compile(
                """
        public interface Value {}
        public class Empty implements Value {}
        public T? optional<T extends Value = Empty>(T? value = null) { value }
        public class Box<T extends Value = Empty> { T? item = null T? read(T? value = item) { value } }
        """));
  }

  @Test
  void publishesDefaultsForAllParameterOwnersWithoutCallSites() throws Exception {
    var artifact =
        compile(
            """
        public Integer number(Integer value = 42) { value }
        public class Box<T> { T? value = null T? read(T? input = value) { input } }
        public enum Choice<T> { Value(T? value = null) }
        public interface Reader<T> { T? read(T? value = null) T? other(T? value = null) { value } }
        """);
    artifact = PortableObjectCodec.decode(PortableObjectCodec.encode(artifact), CoreArtifact.class);
    int defaults = 0;
    for (var binding : artifact.namespace().bindings()) {
      for (var parameter : binding.shape().parameters()) {
        if (!parameter.policy().hasDefault()) continue;
        var target =
            ((CoreDefaultArgument.Resolved) parameter.defaultValue().orElseThrow()).occurrence();
        assertEquals(
            CoreDefinitionRole.DEFAULT_ARGUMENT,
            artifact.authoring().occurrence(target).orElseThrow().role());
        assertInstanceOf(
            CoreDefinition.Callable.class,
            artifact.program().definition(target.representative()).orElseThrow());
        defaults++;
      }
    }
    assertEquals(7, defaults);
  }

  @Test
  void distinguishesDefaultRoutesEvenWhenImplementationsHaveEqualCode() {
    var artifact =
        compile(
            "public Integer first(Integer value = 42) { value } public Integer second(Integer value = 42) { value }");
    var first =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("first"))
            .findFirst()
            .orElseThrow();
    var second =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("second"))
            .findFirst()
            .orElseThrow();
    var firstDefault =
        ((CoreDefaultArgument.Resolved)
                first.shape().parameters().getFirst().defaultValue().orElseThrow())
            .occurrence();
    var secondDefault =
        ((CoreDefaultArgument.Resolved)
                second.shape().parameters().getFirst().defaultValue().orElseThrow())
            .occurrence();
    assertEquals(firstDefault.representative(), secondDefault.representative());
    assertNotEquals(firstDefault, secondDefault);
    var changed = replaceDefault(artifact, first, new CoreDefaultArgument.Resolved(secondDefault));
    assertEquals(PublicAbiId.forArtifact(artifact), PublicAbiId.forArtifact(changed));
    assertEquals(CoreCodeId.forArtifact(artifact), CoreCodeId.forArtifact(changed));
    assertFalse(
        java.util.Arrays.equals(
            CoreArtifactIdentity.linkage(artifact), CoreArtifactIdentity.linkage(changed)));
  }

  @Test
  void rejectsUnresolvedOrIncompatibleDefaultImplementations() {
    var artifact =
        compile(
            "public Integer first(Integer value = 42) { value } public String second(String value = \"bad\") { value }");
    var first =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("first"))
            .findFirst()
            .orElseThrow();
    var second =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("second"))
            .findFirst()
            .orElseThrow();
    assertThrows(
        IllegalArgumentException.class,
        () -> replaceDefault(artifact, first, new CoreDefaultArgument.Pending(0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            replaceDefault(
                artifact,
                first,
                second.shape().parameters().getFirst().defaultValue().orElseThrow()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            replaceDefault(artifact, first, new CoreDefaultArgument.Resolved(first.occurrence())));
  }

  @Test
  void rebindsDefaultOccurrencesWhenPersistentDefinitionsAreReordered(@TempDir Path directory)
      throws Exception {
    String original = "public Integer value(Integer input = 42) { input }";
    var file = directory.resolve("module.norm");
    var cache = directory.resolve("cache");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(request(file, original)).isSuccess());
    }
    String edited = "public Integer earlier(Integer input = 42) { input }\n" + original;
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var result = compiler.compile(request(file, edited));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var expected = fresh.compile(request(file, edited)).output().orElseThrow().artifact();
      var actual = result.output().orElseThrow().artifact();
      assertEquals(
          ArtifactId.forArtifact(expected, "test"), ArtifactId.forArtifact(actual, "test"));
      assertEquals(2, result.output().orElseThrow().state().buildReport().convertedDefinitions());
      for (var binding : actual.namespace().bindings()) {
        var target =
            ((CoreDefaultArgument.Resolved)
                    binding.shape().parameters().getFirst().defaultValue().orElseThrow())
                .occurrence();
        assertEquals(
            binding.name() + "/argument/0", actual.authoring().origin(target).definitionName());
      }
    }
  }

  private static CoreArtifact replaceDefault(
      CoreArtifact artifact, CoreBinding binding, CoreDefaultArgument target) {
    var callable = (CoreBindingShape.Callable) binding.shape();
    var parameter = callable.parameters().getFirst();
    var shape =
        new CoreBindingShape.Callable(
            callable.kind(),
            callable.typeParameters(),
            List.of(
                new CoreBindingShape.Parameter(
                    parameter.label(), parameter.type(), parameter.policy(), Optional.of(target))),
            callable.returnType());
    var replacement =
        new CoreBinding(
            binding.packageName(),
            binding.ownerName(),
            binding.name(),
            binding.visibility(),
            shape,
            binding.occurrence(),
            binding.exported());
    return new CoreArtifact(
        artifact.program(),
        CoreNamespace.create(
            artifact.namespace().bindings().stream()
                .map(value -> value.equals(binding) ? replacement : value)
                .toList()),
        artifact.authoring(),
        artifact.metadata());
  }

  private static CoreArtifact compile(String text) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(request(Path.of("defaults.norm"), text));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }

  private static CompilationRequest request(Path file, String text) {
    var source = SourceFile.of(file, text);
    return new CompilationRequest(source.id(), List.of(source), Set.of(source.id())).asLibrary();
  }
}
