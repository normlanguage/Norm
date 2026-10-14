package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.BindingId;
import dev.w0fv1.norm.core.CoreAnnotationReferenceSite;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreBindingKind;
import dev.w0fv1.norm.core.CoreCodeId;
import dev.w0fv1.norm.core.MetadataId;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class AnnotationReferenceLinkageExecutionTest {
  private static final String SOURCE =
      """
        import std.annotation.FunctionInterceptor
        import std.annotation.RuntimeRetention
        annotation Execute implements FunctionInterceptor, RuntimeRetention {
          Function<String()> action
          Void before(FunctionContext context) { printLine(action.name()); printLine(action()) }
        }
        String first() { "same" }
        String second() { "same" }
        @Execute(action: first.function) Void one() {}
        @Execute(action: second.function) Void two() {}
        Void main() { one(); two() }
        """;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void resolvesEqualContentFunctionsToTheirAuthoredAnnotationReferences(boolean portable)
      throws Exception {
    var result = NormTestKit.compile(SOURCE);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var artifact = result.output().orElseThrow().artifact();
    var first =
        artifact.namespace().bindings().stream()
            .filter(
                binding ->
                    binding.kind() == CoreBindingKind.FUNCTION && binding.name().equals("first"))
            .findFirst()
            .orElseThrow();
    var second =
        artifact.namespace().bindings().stream()
            .filter(
                binding ->
                    binding.kind() == CoreBindingKind.FUNCTION && binding.name().equals("second"))
            .findFirst()
            .orElseThrow();
    assertEquals(first.definition(), second.definition());
    assertNotEquals(first.occurrence(), second.occurrence());
    if (portable)
      artifact =
          PortableObjectCodec.decode(PortableObjectCodec.encode(artifact), CoreArtifact.class);
    var output = new StringWriter();
    new NormRuntime().run(artifact, new PrintWriter(output));
    assertEquals(
        java.util.List.of("first", "same", "second", "same"), output.toString().lines().toList());
  }

  @Test
  void standaloneExecutableCacheSeparatesExchangedAuthoredReferencesWithEqualContent() {
    var first = NormTestKit.compile(SOURCE);
    var second =
        NormTestKit.compile(
            SOURCE
                .replace("action: first.function", "action: temporary.function")
                .replace("action: second.function", "action: first.function")
                .replace("action: temporary.function", "action: second.function"));
    assertTrue(first.isSuccess(), first.diagnostics().toString());
    assertTrue(second.isSuccess(), second.diagnostics().toString());
    var original = first.output().orElseThrow().artifact();
    var exchanged = second.output().orElseThrow().artifact();
    assertEquals(CoreCodeId.forArtifact(original), CoreCodeId.forArtifact(exchanged));
    assertEquals(MetadataId.forArtifact(original), MetadataId.forArtifact(exchanged));
    assertNotEquals(BindingId.forArtifact(original), BindingId.forArtifact(exchanged));
    var backend = new TruffleExecutionBackend();
    var before = new StringWriter();
    var after = new StringWriter();
    backend.prepare(original).execute(ExecutionContext.of(new PrintWriter(before)));
    backend.prepare(exchanged).execute(ExecutionContext.of(new PrintWriter(after)));
    assertEquals(
        java.util.List.of("first", "same", "second", "same"), before.toString().lines().toList());
    assertEquals(
        java.util.List.of("second", "same", "first", "same"), after.toString().lines().toList());
    assertEquals(2, backend.cachedArtifacts());
  }

  @ParameterizedTest
  @ValueSource(strings = {"RuntimeRetention", "SourceRetention"})
  void preservesDefaultedReferencesWithinNestedListsAcrossMetadataAndInterceptors(
      String retention) {
    var result =
        NormTestKit.compile(
            """
        import std.annotation.FunctionInterceptor
        import std.annotation.%s
        annotation Execute implements FunctionInterceptor, %s {
          List<List<Function<String()>>> actions = [[first.function, second.function]]
          Void before(FunctionContext context) {
            for row : actions { for action : row { printLine(action.name()); printLine(action()) } }
          }
        }
        String first() { "same" }
        String second() { "same" }
        @Execute() Void run() {}
        Void main() { run() }
        """
                .formatted(retention, retention));
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var output = new StringWriter();
    new NormRuntime().run(result.output().orElseThrow().artifact(), new PrintWriter(output));
    assertEquals(
        java.util.List.of("first", "same", "second", "same"), output.toString().lines().toList());
  }

  @Test
  void standaloneExecutableCachePreservesChangedAuthoredNamesWithoutChangingCodeContent() {
    var original = NormTestKit.compile(SOURCE);
    var renamed = NormTestKit.compile(SOURCE.replace("first", "renamed"));
    assertTrue(original.isSuccess(), original.diagnostics().toString());
    assertTrue(renamed.isSuccess(), renamed.diagnostics().toString());
    var beforeArtifact = original.output().orElseThrow().artifact();
    var afterArtifact = renamed.output().orElseThrow().artifact();
    assertEquals(CoreCodeId.forArtifact(beforeArtifact), CoreCodeId.forArtifact(afterArtifact));
    assertEquals(MetadataId.forArtifact(beforeArtifact), MetadataId.forArtifact(afterArtifact));
    var backend = new TruffleExecutionBackend();
    var before = new StringWriter();
    var after = new StringWriter();
    backend.prepare(beforeArtifact).execute(ExecutionContext.of(new PrintWriter(before)));
    backend.prepare(afterArtifact).execute(ExecutionContext.of(new PrintWriter(after)));
    assertEquals(
        java.util.List.of("renamed", "same", "second", "same"), after.toString().lines().toList());
    assertEquals(2, backend.cachedArtifacts());
  }

  @Test
  void rejectsMetadataAndInterceptorBindingsThatDisagreeDespiteEqualCanonicalTargets() {
    var result = NormTestKit.compile(SOURCE);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var artifact = result.output().orElseThrow().artifact();
    var first =
        artifact.namespace().bindings().stream()
            .filter(
                binding ->
                    binding.kind() == CoreBindingKind.FUNCTION && binding.name().equals("first"))
            .findFirst()
            .orElseThrow();
    var second =
        artifact.namespace().bindings().stream()
            .filter(
                binding ->
                    binding.kind() == CoreBindingKind.FUNCTION && binding.name().equals("second"))
            .findFirst()
            .orElseThrow();
    assertEquals(first.definition(), second.definition());
    var references = new java.util.HashMap<>(artifact.authoring().annotationReferences());
    var site =
        references.entrySet().stream()
            .filter(
                entry ->
                    entry.getKey().owner() instanceof CoreAnnotationReferenceSite.Metadata
                        && entry.getValue().equals(first.occurrence()))
            .findFirst()
            .orElseThrow()
            .getKey();
    references.put(site, second.occurrence());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring().withAnnotationReferences(references),
                artifact.metadata()));
  }

  @Test
  void sourceFieldInterceptorsKeepTheirConfiguredFunctionDeclarationBindings() {
    var result =
        NormTestKit.compile(
            """
        import std.annotation.FieldInterceptor
        import std.annotation.SourceRetention
        annotation Observe implements FieldInterceptor<Integer>, SourceRetention {
          Function<String()> action
          Integer before(FieldContext context, Integer value) { printLine(action.name()); value }
        }
        String first() { "same" }
        String second() { "same" }
        class Box {
          @Observe(action: first.function) Integer one
          @Observe(action: second.function) Integer two
        }
        Void main() { var box = Box(one: 1, two: 2) box.one = 3 box.two = 4 }
        """);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var output = new StringWriter();
    new NormRuntime().run(result.output().orElseThrow().artifact(), new PrintWriter(output));
    assertEquals(
        java.util.List.of("first", "second", "first", "second"),
        output.toString().lines().toList());
  }

  @Test
  void resolvesDefaultClassFieldAndCallableReferencesFromReflectedMetadata() {
    var result =
        NormTestKit.compile(
            """
        import std.annotation.TypeTarget
        import std.annotation.RuntimeRetention
        annotation Link implements TypeTarget, RuntimeRetention {
          Function<String()> action = first.function
          Class<?> model = Model.class
          Field<Model, Integer> property = Model.value.field
        }
        String first() { "same" }
        String second() { "same" }
        class Model { Integer value = 19 }
        @Link() class Original {}
        @Link(action: second.function) class Other {}
        Void main() {
          var original = Original.class.annotation<Link>()!!
          var other = Other.class.annotation<Link>()!!
          printLine(original.action.name())
          printLine(other.action.name())
          printLine(original.model.name())
          printLine(original.property.name())
        }
        """);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var output = new StringWriter();
    new NormRuntime().run(result.output().orElseThrow().artifact(), new PrintWriter(output));
    assertEquals(
        java.util.List.of("first", "second", "Model", "value"), output.toString().lines().toList());
  }

  @Test
  void retainedAnnotationReferencesUpdateOnlyTheirOwnAuthoredFunction(@TempDir Path directory)
      throws Exception {
    var source =
        SOURCE.replace(
            "Void main() { one(); two() }",
            "Void main() { var count = 0 for (count < 2) { one(); two(); count = count + 1 } }");
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var original = compiler.compile(SourceFile.of(directory.resolve("references.norm"), source));
      var updated =
          compiler.compile(
              SourceFile.of(
                  directory.resolve("references.norm"),
                  source.replace("String first() { \"same\" }", "String first() { \"updated\" }")));
      assertTrue(original.isSuccess(), original.diagnostics().toString());
      assertTrue(updated.isSuccess(), updated.diagnostics().toString());
      var before = original.output().orElseThrow().artifact();
      var after = updated.output().orElseThrow().artifact();
      LiveExecution live = new TruffleExecutionBackend().prepareLive(before);
      var output = new StringWriter();
      var writer =
          new Writer() {
            int lines;

            @Override
            public void write(char[] characters, int offset, int length) {
              output.write(characters, offset, length);
              for (int index = offset; index < offset + length; index++) {
                if (characters[index] == '\n' && ++lines == 4) live.update(after);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      live.execute(ExecutionContext.of(new PrintWriter(writer)));
      assertEquals(
          java.util.List.of(
              "first", "same", "second", "same", "first", "updated", "second", "same"),
          output.toString().lines().toList());
    }
  }
}
