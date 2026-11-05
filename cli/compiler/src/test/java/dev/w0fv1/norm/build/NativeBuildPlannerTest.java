package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.ApplicationProgramArchive;
import dev.w0fv1.norm.runtime.ApplicationProgramData;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeBuildPlannerTest {
  @Test
  void retainedTypedJsonCanRejectMalformedInputAndContinue(@TempDir Path root) throws Exception {
    var source =
        Files.writeString(
            root.resolve("app.norm"),
            """
        import std.serialization.Serializable
        import std.json.fromJson
        import std.json.JsonException
        @Serializable() value Header { String name String value }
        Void main() {
          var rejected = false
          try { fromJson<Header>(value: "[]") }
          catch JsonException error { rejected = true }
          require(condition: rejected, message: "invalid header")
          printLine("recovered")
        }
        """);
    var backend = new NormRuntime();
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(backend));
        var compilation = runner.compileApplication(source)) {
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      var plan = NativeBuildPlan.from(compilation.application().orElseThrow());
      var output = new StringWriter();
      backend.execute(
          plan.retention().artifact(),
          plan.execution(),
          ExecutionContext.of(new PrintWriter(output)));
      assertEquals("recovered" + System.lineSeparator(), output.toString());
    }
  }

  @Test
  void plansAndExecutesTheRetainedArtifactWithoutReplacingTheBasePlan(@TempDir Path root)
      throws Exception {
    Path source =
        Files.writeString(
            root.resolve("app.norm"),
            "Void unused() { printLine(\"unused\") } Void main() { printLine(\"retained\") }");
    var backend = new NormRuntime();
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(backend));
        var compilation = runner.compileApplication(source)) {
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      var app = compilation.application().orElseThrow();
      var original = app.result().output().orElseThrow().artifact();
      var base = app.executionPlan();
      var plan = NativeBuildPlan.from(app);
      var retained = plan.retention().artifact();
      assertSame(app, plan.application());
      assertTrue(retained.program().definitions().size() < original.program().definitions().size());
      assertEquals(
          CoreExecutionPlan.forArtifact(retained, app.methods().entryPoints()), plan.execution());
      assertEquals(base, app.executionPlan());
      assertSame(original, app.result().output().orElseThrow().artifact());
      assertThrows(UnsupportedOperationException.class, () -> plan.bindings().clear());
      Path archive = root.resolve("application.bin");
      ApplicationProgramArchive.write(
          new ApplicationProgramData(
              retained, plan.execution(), plan.bindings(), plan.packageName()),
          archive);
      var loaded = ApplicationProgramArchive.read(archive);
      var output = new StringWriter();
      backend.execute(
          loaded.artifact(), loaded.execution(), ExecutionContext.of(new PrintWriter(output)));
      assertEquals("retained" + System.lineSeparator(), output.toString());
      for (var group : retained.program().groups()) {
        assertArrayEquals(
            original.program().group(group.id()).orElseThrow().canonicalBytes(),
            group.canonicalBytes());
      }
    }
  }
}
