package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ParameterContractTest {
  @Test
  void retainsExecutableDefaultsWithoutAnyCallSite() {
    var first = compile("public Integer value(Integer optional = 41) { optional }");
    var second = compile("public Integer value(Integer optional = 42) { optional }");
    assertNotEquals(CoreCodeId.forArtifact(first), CoreCodeId.forArtifact(second));
  }

  @Test
  void defaultParameterPresenceChangesThePublicContract() {
    for (String declaration :
        java.util.List.of(
            "public Integer value(Integer required, Integer optional%s) { required }",
            "public class Value { Integer required Integer optional%s }",
            "public interface Value { Integer value(Integer required, Integer optional%s) }",
            "public enum Value { Item(Integer required, Integer optional%s) }")) {
      var required = compile(declaration.formatted(""));
      var optional = compile(declaration.formatted(" = 42"));
      assertNotEquals(required.namespace().id(), optional.namespace().id(), declaration);
      assertNotEquals(
          PublicAbiId.forArtifact(required), PublicAbiId.forArtifact(optional), declaration);
    }
  }

  @Test
  void retainsCallbackNamesWithoutChangingExecutableCode() throws Exception {
    var first = compile("public Void visit(Void callback(String title)) {}");
    var renamed = compile("public Void visit(Void callback(String name)) {}");
    assertEquals(CoreCodeId.forArtifact(first), CoreCodeId.forArtifact(renamed));
    assertNotEquals(PublicAbiId.forArtifact(first), PublicAbiId.forArtifact(renamed));
    var restored =
        PortableObjectCodec.decode(PortableObjectCodec.encode(first), CoreArtifact.class);
    var binding = (CoreBindingShape.Callable) restored.namespace().bindings().getFirst().shape();
    var policy = binding.parameters().getFirst().policy();
    assertEquals(java.util.List.of("title"), policy.callbackParameterNames());
    assertFalse(policy.hasDefault());
    assertEquals(dev.w0fv1.norm.value.ParameterPolicy.LabelPolicy.NAMED, policy.labelPolicy());
    assertEquals(PublicAbiId.forArtifact(first), PublicAbiId.forArtifact(restored));
  }

  @Test
  void retainsPoliciesThroughPersistentCoreReuse(@org.junit.jupiter.api.io.TempDir Path directory)
      throws Exception {
    String text = "public T visit<T>(T item, Void callback(T value), Integer count = 1) { item }";
    var source = SourceFile.of(directory.resolve("library.norm"), text);
    var request =
        new CompilationRequest(
                source.id(), java.util.List.of(source), java.util.Set.of(source.id()))
            .asLibrary();
    CoreArtifact first;
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      var result = compiler.compile(request);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      first = result.output().orElseThrow().artifact();
    }
    var moved = SourceFile.of(source.path(), "\n\n" + text);
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      var result =
          compiler.compile(
              new CompilationRequest(
                      moved.id(), java.util.List.of(moved), java.util.Set.of(moved.id()))
                  .asLibrary());
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(0, output.state().buildReport().convertedDefinitions());
      assertEquals(first.namespace().id(), output.artifact().namespace().id());
      var parameters =
          ((CoreBindingShape.Callable) output.artifact().namespace().bindings().getFirst().shape())
              .parameters();
      assertEquals(java.util.List.of("value"), parameters.get(1).policy().callbackParameterNames());
      assertTrue(parameters.get(2).policy().hasDefault());
    }
  }

  private static CoreArtifact compile(String text) {
    try (var compiler = new CompilerSession()) {
      var source = SourceFile.of(Path.of("parameter-contract.norm"), text);
      var result =
          compiler.compile(
              new CompilationRequest(
                      source.id(), java.util.List.of(source), java.util.Set.of(source.id()))
                  .asLibrary());
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }
}
