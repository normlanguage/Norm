package dev.w0fv1.norm.pyrun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("external")
final class PythonRuntimeProjectSmokeTest {
  @TempDir Path temporaryDirectory;

  @Test
  void compilesPyxelSourceThroughManagedPython() throws Exception {
    Path project = cloneProject("https://github.com/kitao/pyxel.git", "pyxel");
    runPython(project, List.of("-m", "compileall", "-q", "python/pyxel"), project);
  }

  @Test
  void compilesManimGallerySourceThroughManagedPython() throws Exception {
    Path project = cloneProject("https://github.com/flipdot/manim-gallery.git", "manim-gallery");
    runPython(project, List.of("-m", "compileall", "-q", "."), project);
  }

  @Test
  void compilesAppQuickLauncherSourceThroughManagedPython() throws Exception {
    Path project =
        cloneProject("https://github.com/yinkaisheng/AppQuickLauncher.git", "AppQuickLauncher");
    runPython(project, List.of("-m", "py_compile", "AppQuickLauncher.pyw"), project);
  }

  private void runPython(Path project, List<String> arguments, Path workingDirectory)
      throws Exception {
    Path requirements = project.resolveSibling(project.getFileName() + "-smoke-requirements.txt");
    Files.writeString(requirements, "");
    Path interpreter =
        new PythonRuntimeService().prepare(project, requirements, System.out::println);
    List<String> command = new java.util.ArrayList<>();
    command.add(interpreter.toString());
    command.addAll(arguments);
    Process process =
        new ProcessBuilder(command).directory(workingDirectory.toFile()).inheritIO().start();
    assertEquals(0, process.waitFor(), () -> "Python smoke command failed: " + command);
  }

  private Path cloneProject(String url, String name) throws IOException, InterruptedException {
    Path project = temporaryDirectory.resolve(name);
    Process process =
        new ProcessBuilder("git", "clone", "--depth", "1", url, project.toString())
            .directory(temporaryDirectory.toFile())
            .inheritIO()
            .start();
    assertEquals(0, process.waitFor(), () -> "Git clone failed: " + url);
    return project;
  }
}
