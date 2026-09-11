package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.testing.NormTestKit;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MethodDeclarationCompilerTest {
  @Test
  void compilesProviderOwnedMethodsAsDeclarations() {
    var result =
        NormTestKit.compile(
            """
            import std.annotation.ManagedImplementation
            import std.annotation.RuntimeRetention
            annotation Generated implements ManagedImplementation, RuntimeRetention {}
            @Generated() class Service { Long count() }
            Void main() {}
            """);
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void preventsDirectConstructionUntilAllInheritedMethodsAreImplemented() {
    String declarations =
        """
        import std.annotation.ManagedImplementation
        import std.annotation.RuntimeRetention
        annotation Generated implements ManagedImplementation, RuntimeRetention {}
        @Generated() class Repository<T> { T find(Long id) }
        class Pending extends Repository<String> {}
        class Complete extends Repository<String> { String find(Long id) { "found" } }
        """;
    for (String expression : java.util.List.of("Repository<String>()", "Pending()")) {
      var result = NormTestKit.compile(declarations + "Void main() { " + expression + " }");
      assertFalse(result.isSuccess());
      assertTrue(
          result.diagnostics().stream()
              .anyMatch(
                  value ->
                      value
                          .message()
                          .contains(
                              "has unimplemented methods and cannot be constructed directly")),
          () -> result.diagnostics().toString());
    }
    org.junit.jupiter.api.Assertions.assertEquals(
        "found" + System.lineSeparator(),
        NormTestKit.run(declarations + "Void main() { printLine(Complete().find(1)) }"));
  }

  @Test
  void rejectsPrivateManagedMethodDeclarations() {
    var result =
        NormTestKit.compile(
            """
            import std.annotation.ManagedImplementation
            import std.annotation.RuntimeRetention
            annotation Generated implements ManagedImplementation, RuntimeRetention {}
            @Generated() class Repository { private Void clear() }
            Void main() {}
            """);
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                value -> value.message().equals("managed method declarations must be public")));
  }

  @Test
  void rechecksConstructionWhenAnInheritedImplementationChanges() throws Exception {
    String declarations =
        """
        import std.annotation.ManagedImplementation
        import std.annotation.RuntimeRetention
        annotation Generated implements ManagedImplementation, RuntimeRetention {}
        @Generated() class Repository<T> { T find(Long id) }
        class Complete extends Repository<String> { String find(Long id) { "found" } }
        Void main() { var complete = Complete() }
        """;
    var path = Path.of("managed-construction.norm");
    try (var compiler =
        dev.w0fv1.norm.project.ProjectEnvironment.bootstrap(
                new dev.w0fv1.norm.truffle.TruffleExecutionBackend())
            .compilerSession()) {
      var initial = compiler.compile(SourceFile.of(path, declarations));
      assertTrue(initial.isSuccess(), () -> initial.diagnostics().toString());
      var removed =
          compiler.compile(
              SourceFile.of(path, declarations.replace("String find(Long id) { \"found\" }", "")));
      assertFalse(removed.isSuccess());
      assertTrue(
          removed.diagnostics().stream()
              .anyMatch(
                  value ->
                      value
                          .message()
                          .contains(
                              "has unimplemented methods and cannot be constructed directly")));
      assertTrue(compiler.compile(SourceFile.of(path, declarations)).isSuccess());
    }
  }

  @Test
  void keepsImplementationOwnershipInTheAnnotationContract() {
    assertTrue(
        NormTestKit.compile(
                """
                import std.annotation.ManagedImplementation
                import std.annotation.RuntimeRetention
                annotation Generated implements ManagedImplementation, RuntimeRetention {}
                @Generated() class Service {}
                Void main() {}
                """)
            .isSuccess());
    var invalid =
        NormTestKit.compile(
            """
            import std.annotation.ManagedImplementation
            class Service implements ManagedImplementation {}
            Void main() {}
            """);
    assertFalse(invalid.isSuccess());
    assertTrue(
        invalid.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic
                        .message()
                        .equals(
                            "annotation policy interfaces can only be implemented by annotation"
                                + " types")));
  }

  @Test
  void rechecksWhenAnImplementationIsRemovedAndRestored() {
    var path = Path.of("method-implementation.norm");
    String implemented = "class Repository { Void clear() {} } Void main() {}";
    try (var compiler = new CompilerSession()) {
      assertTrue(compiler.compile(SourceFile.of(path, implemented)).isSuccess());
      assertFalse(
          compiler
              .compile(SourceFile.of(path, "class Repository { Void clear() } Void main() {}"))
              .isSuccess());
      assertTrue(compiler.compile(SourceFile.of(path, implemented)).isSuccess());
    }
  }

  @Test
  void requiresClassOwnershipAndAnExplicitReturnType() {
    var value = NormTestKit.compile("value Repository { Void clear() } Void main() {}");
    var inferred = NormTestKit.compile("class Repository { clear() } Void main() {}");
    assertTrue(
        value.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.message().equals("method declarations require a class")));
    assertTrue(
        inferred.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic
                        .message()
                        .equals("method declarations require an explicit return type")));
  }

  @Test
  void doesNotCompileDeclarationsAsEmptyVoidImplementations() {
    var result = NormTestKit.compile("class Repository { Void clear() } Void main() {}");
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic
                        .message()
                        .equals("method declaration has no implementation provider")));
  }

  @Test
  void checksSignaturesWithoutReportingMissingReturns() {
    var result = NormTestKit.compile("class Repository<T> { T? find(Long id) } Void main() {}");
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic
                        .message()
                        .equals("method declaration has no implementation provider")));
    assertTrue(
        result.diagnostics().stream()
            .noneMatch(diagnostic -> diagnostic.message().contains("must return")));
  }
}
