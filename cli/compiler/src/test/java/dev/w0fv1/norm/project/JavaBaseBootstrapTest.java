package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import org.junit.jupiter.api.Test;

final class JavaBaseBootstrapTest {
  @Test
  void exposesTheCanonicalSecurityPrincipal() throws Exception {
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var source = SourceFile.of(DocumentId.of("memory:/Security.norm"), """
          import java.base.security.Principal
          String principalName(Principal principal) { principal.getName()!! }
          Void main() {}
          """);
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }

  @Test
  void infersBoundedMixedArraysWithoutLosingHomogeneousElementPrecision() throws Exception {
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var source =
          SourceFile.of(
              DocumentId.of("memory:/Inference.norm"),
              """
          import std.io.printLines
          import std.core.Stringable
          Array<T> retain<T extends Stringable>(Array<T> values) { return values }
          T choose<T extends Stringable>(T first, T second) { return first }
          Void main() {
            printLines(["x", 1])
            printLines([true, "x"])
            printLines([true, 1])
            String value = retain(["x", "y"])[0]
            printLine(choose(first: "x", second: 1))
            printLine(value)
          }
          """);
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }

  @Test
  void loadsJavaBaseWithoutDependingOnStandardLibrary() throws Exception {
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    var descriptor = environment.javaBaseDescriptor();

    assertEquals("java.base", descriptor.name());
    assertTrue(descriptor.dependencies().isEmpty());
    assertEquals(1, environment.javaBindings().size());
    assertTrue(descriptor.exports().contains("util.JavaMap"));
    assertTrue(
        environment.javaBindings().getFirst().generated().sources().stream()
            .noneMatch(source -> source.text().contains("import std.")));
  }

  @Test
  void authorizesGeneratedJavaBaseCallsInApplicationPrelude() throws Exception {
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    var source =
        SourceFile.of(
            DocumentId.of("memory:/Main.norm"),
            "import java.base.util.linkedHashMapNew\nVoid main() { var values = linkedHashMapNew<String, Integer>() values.put(arg0: \"entry\", arg1: 7) printLine(values.size()) }\n");
    try (var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }
}
