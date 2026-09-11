package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NativeApplicationArchive;
import dev.w0fv1.norm.runtime.NativeApplicationData;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeBuildPlannerTest {
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
      var plan = NativeBuildPlanner.plan(app);
      var retained = plan.retention().artifact();
      assertSame(app, plan.application());
      assertTrue(retained.program().definitions().size() < original.program().definitions().size());
      assertEquals(
          CoreExecutionPlan.forArtifact(retained, app.methods().entryPoints()), plan.execution());
      assertEquals(base, app.executionPlan());
      assertSame(original, app.result().output().orElseThrow().artifact());
      assertThrows(UnsupportedOperationException.class, () -> plan.bindings().clear());
      Path archive = root.resolve("application.bin");
      NativeApplicationArchive.write(
          new NativeApplicationData(
              retained, plan.execution(), plan.bindings(), plan.packageName()),
          archive);
      var loaded = NativeApplicationArchive.read(archive);
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
