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
  void preservesNestedNullableListTypesInJavaMethodSignatures() {
    var parent =
        plan().types().stream()
            .filter(type -> type.name().equals("Parent"))
            .findFirst()
            .orElseThrow();
    var echo =
        parent.callables().stream()
            .filter(call -> call.name().equals("echo"))
            .findFirst()
            .orElseThrow();
    String expected =
        "java.util.List<java.util.List<java.lang.@org.jspecify.annotations.Nullable String>>";
    assertEquals(expected, echo.returnType());
    assertEquals(expected, echo.parameters().getFirst().type());
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

  @Test
  void retainsNativeAnnotationMetadataWithoutInventingJavaAnnotationDefaults() throws Exception {
    var nativeSource =
        SourceFile.of(
            Path.of("native-annotation.norm"),
            """
        package annotation.shape
        import std.annotation.TypeTarget
        import std.annotation.RuntimeRetention
        annotation NativeOnly implements TypeTarget, RuntimeRetention {
          String? note
        }
        annotation JavaVisible implements TypeTarget, RuntimeRetention {
          String text
          List<String> tags
        }
        @NativeOnly()
        @JavaVisible(text: "visible", tags: ["a", "b"])
        class Example { NativeOnly? nativeNote }
        Void main() {}
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(nativeSource);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var nativeArtifact = result.output().orElseThrow().artifact();
      var nativeId =
          nativeArtifact.namespace().bindings().stream()
              .filter(binding -> binding.name().equals("NativeOnly"))
              .findFirst()
              .orElseThrow()
              .definition();
      assertTrue(
          JavaAnnotationShape.elementType(
                  nativeArtifact.program(),
                  nativeId,
                  dev.w0fv1.norm.core.CoreType.VOID,
                  java.util.Map.of())
              .isEmpty());
      assertTrue(
          nativeArtifact.metadata().annotations().stream()
              .filter(application -> application.annotation().equals(nativeId))
              .anyMatch(
                  application ->
                      application.values().getFirst().value()
                          == dev.w0fv1.norm.core.CoreAnnotationValue.Null.INSTANCE));
      var plan =
          new JavaStubPlanner()
              .plan(
                  nativeArtifact,
                  environment.javaBindings(),
                  CompilationScope.anonymous(List.of(nativeSource)),
                  nativeSource.id(),
                  Set.of());
      var nativeType =
          plan.types().stream()
              .filter(type -> type.name().equals("NativeOnly"))
              .findFirst()
              .orElseThrow();
      assertEquals(JavaStubPlan.TypeKind.VALUE, nativeType.kind());
      var example =
          plan.types().stream()
              .filter(type -> type.name().equals("Example"))
              .findFirst()
              .orElseThrow();
      assertFalse(
          example.annotations().stream()
              .anyMatch(annotation -> annotation.binaryName().endsWith("NativeOnly")));
      assertTrue(
          example.annotations().stream()
              .anyMatch(annotation -> annotation.binaryName().endsWith("JavaVisible")));
      var stubs = new JavaStubRenderer().render(plan);
      assertTrue(stubs.stream().anyMatch(stub -> stub.source().contains("@interface JavaVisible")));
      assertTrue(
          stubs.stream().anyMatch(stub -> stub.source().contains("java.lang.String[] tags();")));
      assertTrue(stubs.stream().anyMatch(stub -> stub.source().contains("class NativeOnly")));
    }
  }

  @Test
  void plansOnlyJavaContractsAndTheirRequiredNominalSignatures() throws Exception {
    var contractSource =
        SourceFile.of(
            Path.of("java-contract-roots.norm"),
            """
        package contract.roots
        import std.concurrent.Task
        import java.base.util.function.Supplier
        import java.base.util.optionalOf
        import java.base.util.JavaList
        import java.base.util.arrayListNew
        interface Unused<T> { Boolean equals(T other) }
        class IntegerSupplier implements Supplier<Integer> {
          Task<Integer>? work = null
          Integer get() { 42 }
        }
        class Reflected { String name }
        class NativeReflection { String name }
        class InterfaceReflected { String name }
        Void inspect<T>(Class<T> type) { var value = optionalOf(arg0: type) }
        Void main() {
          inspect(type: Reflected.class)
          JavaList<Class<InterfaceReflected>> tokens = arrayListNew<Class<InterfaceReflected>>()
          tokens.add(arg0: InterfaceReflected.class)
          var nativeType = NativeReflection.class
        }
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(contractSource);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var plan =
          new JavaStubPlanner()
              .plan(
                  result.output().orElseThrow().artifact(),
                  environment.javaBindings(),
                  CompilationScope.anonymous(List.of(contractSource)),
                  contractSource.id(),
                  Set.of());
      assertFalse(plan.types().stream().anyMatch(type -> type.name().equals("Unused")));
      assertTrue(plan.types().stream().anyMatch(type -> type.name().equals("Reflected")));
      assertFalse(plan.types().stream().anyMatch(type -> type.name().equals("NativeReflection")));
      assertTrue(plan.types().stream().anyMatch(type -> type.name().equals("InterfaceReflected")));
      var supplier =
          plan.types().stream()
              .filter(type -> type.name().equals("IntegerSupplier"))
              .findFirst()
              .orElseThrow();
      assertFalse(
          plan.types().stream().anyMatch(type -> type.binaryName().equals("std.concurrent.Task")));
      assertEquals(
          "java.lang.@org.jspecify.annotations.Nullable Object",
          supplier.fields().stream()
              .filter(field -> field.name().equals("work"))
              .findFirst()
              .orElseThrow()
              .type());
      assertTrue(supplier.interfaces().contains("java.util.function.Supplier<java.lang.Integer>"));
      assertEquals(
          "java.lang.Integer",
          supplier.callables().stream()
              .filter(call -> call.name().equals("get"))
              .findFirst()
              .orElseThrow()
              .returnType());
    }
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
