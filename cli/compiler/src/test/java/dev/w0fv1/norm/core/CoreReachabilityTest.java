package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class CoreReachabilityTest {
  @Test
  void separatesRetainedInterfaceDeclarationsFromUnusedImplementationExecution() {
    var compiled =
        NormTestKit.compile(
            """
        interface Named { String name() }
        String dormantService() { return "not requested" }
        class Unconstructed implements Named {
          String name() { return dormantService() }
        }
        Void main() { Named? value = null; printLine("hello") }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var original = compiled.program().orElseThrow().compilation().artifact();
    var analysis = CoreReachability.analyze(original, java.util.Set.of());
    var reduced = analysis.artifact();
    assertEquals(reduced.program().groups().size(), analysis.causes().size());
    assertThrows(UnsupportedOperationException.class, () -> analysis.causes().clear());
    assertTrue(
        analysis.causes().values().stream()
            .anyMatch(cause -> cause.kind() == CoreReachability.RetentionKind.SUBTYPE));
    for (var group : analysis.causes().keySet()) {
      var visited = new java.util.HashSet<DefinitionGroupId>();
      var current = group;
      while (true) {
        assertTrue(visited.add(current), "retention predecessors must not cycle");
        var cause = analysis.causes().get(current);
        assertNotNull(cause);
        if (cause.source().isEmpty()) {
          assertEquals(CoreReachability.RetentionKind.APPLICATION_ENTRY, cause.kind());
          break;
        }
        current = cause.source().orElseThrow();
      }
    }
    assertTrue(
        reduced.namespace().bindings().stream()
            .anyMatch(binding -> binding.name().equals("dormantService")));
    var execution = CoreExecutionPlan.forArtifact(reduced);
    reduced.namespace().bindings().stream()
        .filter(binding -> binding.name().equals("dormantService") || binding.name().equals("name"))
        .forEach(
            binding -> {
              assertFalse(execution.callables().contains(binding.definition()));
              assertFalse(execution.dispatchSlots().contains(binding.definition()));
            });
    var output = new StringWriter();
    new NormRuntime()
        .execute(
            reduced, ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("hello" + System.lineSeparator(), output.toString());
  }

  @Test
  void annotationsFollowReachableTargetsAndExplicitHostEntries() {
    var compiled =
        NormTestKit.compile(
            """
        import std.annotation.RuntimeRetention
        import std.annotation.TypeTarget
        annotation Label implements TypeTarget, RuntimeRetention { String text }
        @Label(text: "host")
        class Host { String value() { return "external" } }
        Void main() { printLine("hello") }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var original = compiled.program().orElseThrow().compilation().artifact();
    var host =
        original.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("Host") && binding.ownerName().isEmpty())
            .findFirst()
            .orElseThrow()
            .definition();
    var reduced = CoreReachability.retainApplication(original);
    assertTrue(reduced.program().definition(host).isEmpty());
    var rooted = CoreReachability.retainApplication(original, java.util.Set.of(host));
    assertTrue(rooted.program().definition(host).isPresent());
    assertTrue(rooted.metadata().annotations().size() > reduced.metadata().annotations().size());
    assertTrue(
        rooted.namespace().bindings().stream()
            .anyMatch(
                binding ->
                    binding.ownerName().orElse("").equals("Host")
                        && binding.name().equals("value")));
    var repeated = CoreReachability.retainApplication(rooted, java.util.Set.of(host));
    assertEquals(rooted.program().groups(), repeated.program().groups());
    assertEquals(rooted.metadata().annotations(), repeated.metadata().annotations());
    assertEquals(rooted.namespace().bindings(), repeated.namespace().bindings());
  }

  @Test
  void minimalProgramDoesNotRetainNetworkOrSerializationIntrinsics() {
    var compiled = NormTestKit.compile("Void main() { printLine(\"hello\") }");
    var artifact =
        CoreReachability.retainApplication(
            compiled.program().orElseThrow().compilation().artifact());
    var ids = java.util.EnumSet.noneOf(dev.w0fv1.norm.abi.IntrinsicId.class);
    var walker =
        new CoreWalker() {
          @Override
          protected void visitExpression(CoreExpression expression) {
            if (expression instanceof CoreExpression.Intrinsic intrinsic)
              ids.add(intrinsic.intrinsic());
          }
        };
    artifact.program().definitions().forEach(record -> walker.walk(record.definition()));
    assertFalse(
        ids.stream()
            .anyMatch(
                id ->
                    id.name().startsWith("HTTP_")
                        || id.name().startsWith("XML_")
                        || id.name().startsWith("JSON_")
                        || id.name().startsWith("YAML_")),
        ids.toString());
  }

  @Test
  void inactiveDynamicJavaCallsDoNotWidenTheSelectedCallTable() throws Exception {
    var compiled =
        compileBinding(
            """
        Void dormant(String name) { __jarInvokeVoid0(name) }
        Void main() { __jarInvokeVoid0("sample.call") }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var artifact = compiled.program().orElseThrow().compilation().artifact();
    var dormant =
        artifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("dormant"))
            .findFirst()
            .orElseThrow()
            .definition();
    assertEquals(
        java.util.Set.of("sample.call"), CoreReachability.jarCalls(artifact).orElseThrow());
    var external = CoreExecutionPlan.forArtifact(artifact, java.util.Set.of(dormant));
    assertTrue(CoreReachability.jarCalls(artifact, external).isEmpty());
  }

  @Test
  void collectsLiteralJavaCallsAndKeepsDynamicLookupConservative() throws Exception {
    var direct = compileBinding("Void main() { __jarInvokeVoid0(\"sample.call\") }");
    assertTrue(direct.isSuccess(), () -> direct.diagnostics().toString());
    assertEquals(
        java.util.Set.of("sample.call"),
        CoreReachability.jarCalls(
                CoreReachability.retainApplication(
                    direct.program().orElseThrow().compilation().artifact()))
            .orElseThrow());
    var dynamic =
        compileBinding(
            """
        Void invoke(String name) { __jarInvokeVoid0(name) }
        Void main() { invoke("sample.call") }
        """);
    assertTrue(dynamic.isSuccess(), () -> dynamic.diagnostics().toString());
    assertTrue(
        CoreReachability.jarCalls(
                CoreReachability.retainApplication(
                    dynamic.program().orElseThrow().compilation().artifact()))
            .isEmpty());
  }

  private static dev.w0fv1.norm.value.CompilationResult compileBinding(String text)
      throws Exception {
    var source = dev.w0fv1.norm.value.SourceFile.of(java.nio.file.Path.of("binding.norm"), text);
    var request = dev.w0fv1.norm.value.CompilationRequest.single(source);
    try (var compiler =
        dev.w0fv1.norm.project.ProjectEnvironment.bootstrap(new NormRuntime()).compilerSession()) {
      return compiler.compile(
          new dev.w0fv1.norm.value.CompilationRequest(
              request.unit(),
              request.scope(),
              request.entryDocument(),
              request.sources(),
              request.exportedSources(),
              java.util.Set.of(source.id())));
    }
  }

  @Test
  void retainsSubtypesThatCanBeReturnedByHostFactories() {
    var compiled =
        NormTestKit.compile(
            """
        class Parent { String name() { return "parent" } }
        class Child extends Parent { Child() { super() } }
        Void main() { printLine(Parent().name()) }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var original = compiled.program().orElseThrow().compilation().artifact();
    var reduced = CoreReachability.retainApplication(original);
    assertTrue(
        reduced.namespace().bindings().stream()
            .anyMatch(binding -> binding.name().equals("Child")));
  }

  @Test
  void retainsUnconstructedImplementationsOfReachableInterfaces() {
    var compiled =
        NormTestKit.compile(
            """
        interface Named { String name() }
        class HostBindingValue implements Named { String name() { return "host" } }
        Void main() { Named? value = null }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var reduced =
        CoreReachability.retainApplication(
            compiled.program().orElseThrow().compilation().artifact());
    assertTrue(
        reduced.namespace().bindings().stream()
            .anyMatch(binding -> binding.name().equals("HostBindingValue")));
  }

  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> preservesLanguageBehaviors() {
    return java.util.stream.Stream.of(
            "interfaces/05_custom_iterable.norm",
            "annotations/dynamic_dispatch_interception.norm",
            "reflection/reflect_class_annotation.norm",
            "reflection/reflect_fields.norm",
            "annotations/field_inheritance.norm")
        .map(
            path ->
                org.junit.jupiter.api.DynamicTest.dynamicTest(
                    path,
                    () -> {
                      String source =
                          java.nio.file.Files.readString(
                              java.nio.file.Path.of(
                                  java.util.Objects.requireNonNull(
                                          CoreReachabilityTest.class.getResource("/" + path))
                                      .toURI()));
                      var compiled = NormTestKit.compile(source);
                      assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
                      var original = compiled.program().orElseThrow().compilation().artifact();
                      var expected = new StringWriter();
                      new NormRuntime()
                          .execute(
                              original,
                              ExecutionContext.of(
                                  new PrintWriter(expected), JdkSystemPlatform.standard()));
                      var actual = new StringWriter();
                      new NormRuntime()
                          .execute(
                              CoreReachability.retainApplication(original),
                              ExecutionContext.of(
                                  new PrintWriter(actual), JdkSystemPlatform.standard()));
                      assertEquals(expected.toString(), actual.toString());
                    }));
  }

  @Test
  void removesUnusedFunctionsAndPreservesExecutionAndIdentity() {
    var compiled =
        NormTestKit.compile(
            """
        String unused() { return "unused" }
        String greeting() { return "hello" }
        Void main() { printLine(greeting()) }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var original = compiled.program().orElseThrow().compilation().artifact();
    var reduced = CoreReachability.retainApplication(original);
    assertTrue(reduced.program().definitions().size() < original.program().definitions().size());
    assertFalse(
        reduced.authoring().occurrences().stream()
            .anyMatch(value -> value.origin().definitionName().equals("unused")));
    assertEquals(original.entryPoint(), reduced.entryPoint());
    assertEquals(
        reduced.program().groups(), CoreReachability.retainApplication(reduced).program().groups());
    var output = new StringWriter();
    new NormRuntime()
        .execute(
            reduced, ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("hello" + System.lineSeparator(), output.toString());
  }

  @Test
  void retainsRecursiveGroupsAndVirtualMethods() {
    var compiled =
        NormTestKit.compile(
            """
        class Greeter {
          String greet() { return "hello" }
        }
        Integer count(Integer n) {
          if n == 0 { return 0 }
          return count(n - 1) + 1
        }
        Void main() {
          printLine(Greeter().greet())
          printLine(count(3))
        }
        """);
    assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    var reduced =
        CoreReachability.retainApplication(
            compiled.program().orElseThrow().compilation().artifact());
    var output = new StringWriter();
    new NormRuntime()
        .execute(
            reduced, ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals(
        "hello" + System.lineSeparator() + "3" + System.lineSeparator(), output.toString());
  }
}
