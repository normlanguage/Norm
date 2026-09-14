package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AnnotationProcessorResourcesTest {
  @TempDir Path directory;

  @Test
  void processesCurrentModuleResourcesWhenNormSourcesAreReused() throws Exception {
    Path source = directory.resolve("TemplateProcessor.java");
    Files.writeString(
        source,
        """
        import java.util.Set;
        import javax.annotation.processing.*;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.element.TypeElement;
        import javax.tools.StandardLocation;

        @SupportedAnnotationTypes("*")
        @SupportedSourceVersion(SourceVersion.RELEASE_17)
        public final class TemplateProcessor extends AbstractProcessor {
          private boolean written;

          @Override
          public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (written || round.processingOver()) return false;
            written = true;
            try {
              var filer = processingEnv.getFiler();
              var template = filer.getResource(StandardLocation.CLASS_PATH, "", "templates/page.mustache");
              var outputTemplate = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "templates/page.mustache");
              if (!template.getCharContent(true).toString().equals(outputTemplate.getCharContent(true).toString())) {
                throw new java.io.IOException("Resource locations disagree");
              }
              try (var output = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "rendered.txt").openWriter()) {
                output.write(template.getCharContent(true).toString());
              }
            } catch (java.io.IOException exception) {
              processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR,
                  "Cannot read templates/page.mustache: " + exception.getMessage());
            }
            return false;
          }
        }
        """);
    Path classes = Files.createDirectories(directory.resolve("classes"));
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString()));
    Path service = classes.resolve("META-INF/services/javax.annotation.processing.Processor");
    Files.createDirectories(service.getParent());
    Files.writeString(service, "TemplateProcessor\n");
    Path jar = directory.resolve("processor.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar));
        var files = Files.walk(classes)) {
      for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
        output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
        output.write(Files.readAllBytes(file));
        output.closeEntry();
      }
    }
    var artifact =
        new ResolvedJarArtifact(
            new MavenJarIdentity(new MavenArtifactCoordinate("sample", "processor", "1")),
            jar,
            Sha256Digest.compute(jar));
    var processors = List.of(new ResolvedJarGraph(artifact, List.of(artifact), List.of()));
    Path root = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(
        root.resolve("module.norm"), "Module module() { module(name: \"sample\", version: 1) }");
    Path entry = root.resolve("main.norm");
    Files.writeString(
        entry,
        """
        package sample
        import std.annotation.RuntimeRetention
        import std.annotation.ManagedImplementation
        public annotation Template implements ManagedImplementation, RuntimeRetention {}
        @Template() public class Page { String title() }
        Void main() {}
        """);
    Path template = root.resolve("resources/templates/page.mustache");
    Files.createDirectories(template.getParent());
    Files.writeString(template, "<h1>first</h1>");
    var timestamp = Files.getLastModifiedTime(template);
    var environment = ProjectEnvironment.persistent(new NormRuntime());
    for (int attempt = 0; attempt < 3; attempt++) {
      if (attempt == 1) {
        Files.writeString(template, "<h1>second</h1>");
        Files.setLastModifiedTime(template, timestamp);
      } else if (attempt == 2) Files.delete(template);
      var progress = new ArrayList<String>();
      try (var runner = ApplicationRunner.persistent(environment);
          var compilation = runner.compileApplication(entry, progress::add, processors)) {
        if (attempt == 2) {
          assertFalse(compilation.result().isSuccess());
          assertTrue(
              compilation.result().diagnostics().toString().contains("templates/page.mustache"));
        } else {
          assertTrue(
              compilation.result().isSuccess(), compilation.result().diagnostics().toString());
          Path output = compilation.application().orElseThrow().annotations().classes();
          String expected = attempt == 0 ? "<h1>first</h1>" : "<h1>second</h1>";
          assertEquals(expected, Files.readString(output.resolve("rendered.txt")));
          assertEquals(expected, Files.readString(output.resolve("templates/page.mustache")));
        }
        if (attempt > 0) assertTrue(progress.contains("Reused compiled Norm sources"));
      }
    }
  }
}
