package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationScope;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class JavaStubPlannerTest {
  private static SourceFile source;
  private static CoreArtifact artifact;

  @BeforeAll
  static void compileFixture() throws Exception {
    try (var input = JavaStubPlannerTest.class.getResourceAsStream("/java-stubs/planning.norm")) {
      assertNotNull(input);
      source =
          SourceFile.of(
              Path.of("java-stub-planning.norm"),
              new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
    try (var compiler = ProjectEnvironment.bootstrap(new NormRuntime()).compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      artifact = result.output().orElseThrow().artifact();
    }
  }

  @Test
  void preservesGeneratedBytesAndBridgeIdentities() throws Exception {
    var stubs = new JavaStubRenderer().render(plan());
    try (var input = getClass().getResourceAsStream("/java-stubs/planning.json")) {
      assertNotNull(input);
      var expected =
          JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
              .getAsJsonObject();
      assertEquals(expected.size(), stubs.size());
      for (var stub : stubs) {
        assertTrue(expected.has(stub.binaryName()), stub.binaryName());
        assertEquals(
            expected.get(stub.binaryName()).getAsString(), stub.source(), stub.binaryName());
      }
    }
  }

  @Test
  void fixesManagedSignaturesInheritanceAndDefaultsBeforeRendering() {
    var plan = plan();
    var repository =
        plan.types().stream()
            .filter(type -> type.name().equals("Repository"))
            .findFirst()
            .orElseThrow();
    assertTrue(repository.abstractType());
    assertEquals("T0", repository.typeParameters().getFirst().name());
    var find =
        repository.callables().stream()
            .filter(call -> call.name().equals("find"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaStubPlan.CallableKind.ABSTRACT_METHOD, find.kind());
    assertTrue(find.returnType().contains("Nullable"));
    assertEquals("long", find.parameters().getFirst().type());
    assertFalse(find.parameters().getFirst().annotations().isEmpty());
    var child =
        plan.types().stream().filter(type -> type.name().equals("Child")).findFirst().orElseThrow();
    assertTrue(child.generatedParent());
    assertTrue(child.parentType().orElseThrow().endsWith("Parent"));
    var named =
        plan.types().stream().filter(type -> type.name().equals("Named")).findFirst().orElseThrow();
    assertEquals(
        JavaStubPlan.CallableKind.DEFAULT_METHOD,
        named.callables().stream()
            .filter(call -> call.name().equals("greet"))
            .findFirst()
            .orElseThrow()
            .kind());
  }

  @Test
  void freezesCollectionsAndCanRenderWithoutTheCompilationSession() {
    var plan = plan();
    assertThrows(UnsupportedOperationException.class, () -> plan.types().clear());
    for (var type : plan.types()) {
      assertThrows(UnsupportedOperationException.class, () -> type.callables().clear());
      assertThrows(UnsupportedOperationException.class, () -> type.annotations().clear());
      for (var callable : type.callables()) {
        assertThrows(UnsupportedOperationException.class, () -> callable.parameters().clear());
      }
    }
    var expected = new JavaStubRenderer().render(plan);
    var first = CompletableFuture.supplyAsync(() -> new JavaStubRenderer().render(plan));
    var second = CompletableFuture.supplyAsync(() -> new JavaStubRenderer().render(plan));
    assertEquals(expected, first.join());
    assertEquals(expected, second.join());
    assertEquals(plan, plan());
  }

  @Test
  void excludesBindingDocumentsFromTheApplicationSurface() {
    var excluded =
        new JavaStubPlanner()
            .plan(
                artifact,
                List.of(),
                CompilationScope.anonymous(List.of(source)),
                source.id(),
                Set.of(source.id()));
    assertTrue(excluded.types().isEmpty());
  }

  private static JavaStubPlan plan() {
    return new JavaStubPlanner()
        .plan(
            artifact,
            List.of(),
            CompilationScope.anonymous(List.of(source)),
            source.id(),
            Set.of());
  }
}
