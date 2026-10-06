package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModuleJavaBindingEvaluationTest {
  @TempDir Path directory;

  @Test
  void recordsAndReplaysJavaCallsMadeByModuleConfiguration() throws Exception {
    Path moduleRoot = Files.createDirectory(directory.resolve("sample"));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(entry, "package sample Void main() {}\n");
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        import std.collections.mutableMap

        Module module() {
          var settings = mutableMap<String, String>()
          settings.put(arg0: "name", arg1: "sample")
          String? name = settings.get("name")
          if name != null {
            return module(name: name, version: 1, exports: ["Main"])
          }
          return module(name: "missing", version: 1, exports: ["Main"])
        }
        """);
    var runtime = new NormRuntime();
    try (var environment = ProjectEnvironment.bootstrap(runtime);
        var projects = environment.projectLoader()) {
      var loaded = projects.load(entry);
      var evaluation = loaded.moduleEvaluations().getFirst();
      assertEquals("sample", evaluation.declaration().name().orElseThrow());
      assertFalse(evaluation.bindings().isEmpty());
      var restored =
          PortableObjectCodec.decode(
              PortableObjectCodec.encode(evaluation), ModuleEvaluation.class);
      assertEquals(evaluation.declaration(), restored.evaluate(runtime).declaration());
    }
  }
}
