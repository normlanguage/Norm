package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationScope;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class JavaHostSurfaceTest {
  @Test
  void exportsClassParametersForwardedThroughGenericReturnValues() throws Exception {
    var source =
        SourceFile.of(
            Path.of("class-return-flow.norm"),
            """
        package classreturnflow
        import java.base.util.optionalOf
        class HostVisible { String value }
        class ReflectionOnly { String value }
        Class<T> identity<T>(Class<T> type) { return type }
        Void sink<T, U>(Class<T> host, Class<U> reflected) {
          var returned = identity(type: host)
          var value = optionalOf(arg0: returned)
        }
        Void main() {
          sink(reflected: ReflectionOnly.class, host: HostVisible.class)
        }
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      var plan =
          new JavaStubPlanner()
              .plan(
                  result.output().orElseThrow().artifact(),
                  environment.javaBindings(),
                  CompilationScope.anonymous(List.of(source)),
                  source.id(),
                  Set.of());
      assertTrue(plan.types().stream().anyMatch(type -> type.name().equals("HostVisible")));
      assertFalse(plan.types().stream().anyMatch(type -> type.name().equals("ReflectionOnly")));
    }
  }

  @Test
  void exportsClassParametersForwardedThroughLocalAliases() throws Exception {
    var source =
        SourceFile.of(
            Path.of("class-alias-flow.norm"),
            """
        package classaliasflow
        import java.base.util.optionalOf
        class HostVisible { String value }
        class ReflectionOnly { String value }
        Void sink<T, U>(Class<T> host, Class<U> reflected) {
          var alias = host
          var secondAlias = alias
          var value = optionalOf(arg0: secondAlias)
        }
        Void main() {
          sink(reflected: ReflectionOnly.class, host: HostVisible.class)
        }
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      var plan =
          new JavaStubPlanner()
              .plan(
                  result.output().orElseThrow().artifact(),
                  environment.javaBindings(),
                  CompilationScope.anonymous(List.of(source)),
                  source.id(),
                  Set.of());
      assertTrue(plan.types().stream().anyMatch(type -> type.name().equals("HostVisible")));
      assertFalse(plan.types().stream().anyMatch(type -> type.name().equals("ReflectionOnly")));
    }
  }

  @Test
  void exportsOnlyClassParametersThatReachJavaAcrossNamedCalls() throws Exception {
    var source =
        SourceFile.of(
            Path.of("class-parameter-flow.norm"),
            """
        package classflow
        import java.base.util.optionalOf
        class HostVisible { String value }
        class ReflectionOnly { String value }
        Void sink<T, U>(Class<T> host, Class<U> reflected) {
          var value = optionalOf(arg0: host)
        }
        Void forward<T, U>(Class<T> host, Class<U> reflected) {
          sink(reflected: reflected, host: host)
        }
        Void main() {
          forward(reflected: ReflectionOnly.class, host: HostVisible.class)
        }
        """);
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession()) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      var plan =
          new JavaStubPlanner()
              .plan(
                  result.output().orElseThrow().artifact(),
                  environment.javaBindings(),
                  CompilationScope.anonymous(List.of(source)),
                  source.id(),
                  Set.of());
      assertTrue(plan.types().stream().anyMatch(type -> type.name().equals("HostVisible")));
      assertFalse(plan.types().stream().anyMatch(type -> type.name().equals("ReflectionOnly")));
    }
  }
}
