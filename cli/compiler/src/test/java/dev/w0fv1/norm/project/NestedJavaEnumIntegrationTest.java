package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NestedJavaEnumIntegrationTest {
  @TempDir Path directory;

  @Test
  void preservesNestedJavaEnumIdentityAcrossAliasedSwitchExpressions() throws Exception {
    var module = Files.createDirectories(directory.resolve("binding"));
    var source =
        Files.writeString(
            directory.resolve("Config.java"),
            """
        package sample;
        public final class Config {
          public enum Density { COMPACT, DEFAULT }
          private final Density density;
          public Config(Density density) { this.density = density; }
          public String label() { return density.name(); }
        }
        """);
    var classes = Files.createDirectories(directory.resolve("classes"));
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    var jar = module.resolve("config.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      for (var name : java.util.List.of("Config", "Config$Density")) {
        output.putNextEntry(new JarEntry("sample/" + name + ".class"));
        output.write(Files.readAllBytes(classes.resolve("sample/" + name + ".class")));
        output.closeEntry();
      }
    }
    Files.writeString(
        module.resolve("module.norm"),
        """
        Module module() { module(name: "binding", version: 1, exports: ["Config", "Density"],
          dependencies: [dependency(repository: "norm", name: "java.base", version: 1)],
          binding: jarBinding(target: localJar(path: "config.jar", integrity: sha256("%s")),
            api: [jarType(name: "sample.Config", members: ["new", "label"]),
              jarType(name: "sample.Config.Density", alias: "Density", members: [])])) }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    var consumer = Files.createDirectories(module.resolve("consumer"));
    Files.writeString(
        consumer.resolve("main.norm"),
        """
        package binding.consumer
        import binding.Density as FxDensity
        import binding.configNew
        enum Density { Compact, Default }
        public Void run() {
          Density mode = Density.Compact
          FxDensity explicit = switch mode {
            case Compact { break FxDensity.COMPACT }
            case Default { break FxDensity.DEFAULT }
          }
          var inferred = switch mode {
            case Compact { break FxDensity.COMPACT }
            case Default { break FxDensity.DEFAULT }
          }
          printLine(configNew(explicit).label()!!)
          printLine(configNew(inferred).label()!!)
        }
        """);
    var entry =
        Files.writeString(
            module.resolve("main.norm"),
            "package binding import binding.consumer.run Void main() { run() }");
    var runtime = new NormRuntime();
    var output = new StringWriter();
    try (var environment = ProjectEnvironment.bootstrap(runtime);
        var application =
            new ApplicationRunner(
                environment.projectLoader(directory.resolve("cache")),
                environment.compilerSession(),
                runtime)) {
      var result = application.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
    assertEquals(
        "COMPACT" + System.lineSeparator() + "COMPACT" + System.lineSeparator(), output.toString());
  }
}
