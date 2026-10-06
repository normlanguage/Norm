package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationScope;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class JavaAnnotationShapeTest {
  @Test
  void projectsOnlyPublicFieldsAlignedWithAnnotationConstructorParameters() throws Exception {
    var source =
        SourceFile.of(
            Path.of("annotation-elements.norm"),
            """
        package annotation.elements
        import std.annotation.TypeTarget
        import std.annotation.RuntimeRetention
        annotation Stateful implements TypeTarget, RuntimeRetention {
          private Integer calls
          Stateful() { calls = 0 }
        }
        annotation Renamed implements TypeTarget, RuntimeRetention {
          Integer value
          Renamed(Integer input) { value = input }
        }
        annotation JavaVisible implements TypeTarget, RuntimeRetention {
          String text
        }
        @Stateful()
        @Renamed(input: 42)
        @JavaVisible(text: "visible")
        class Example {}
        Void main() {}
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      var artifact = result.output().orElseThrow().artifact();
      for (var expected :
          Map.of("Stateful", false, "Renamed", false, "JavaVisible", true).entrySet()) {
        var binding =
            artifact.namespace().bindings().stream()
                .filter(item -> item.ownerName().isEmpty() && item.name().equals(expected.getKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(
            expected.getValue(),
            JavaAnnotationShape.representable(artifact.program(), binding, Map.of()),
            expected.getKey());
      }
      var plan =
          new JavaStubPlanner()
              .plan(
                  artifact,
                  environment.javaBindings(),
                  CompilationScope.anonymous(List.of(source)),
                  source.id(),
                  Set.of());
      var example =
          plan.types().stream()
              .filter(type -> type.name().equals("Example"))
              .findFirst()
              .orElseThrow();
      assertEquals(1, example.annotations().size());
      assertEquals(
          "annotation.elements.JavaVisible", example.annotations().getFirst().binaryName());
    }
  }
}
