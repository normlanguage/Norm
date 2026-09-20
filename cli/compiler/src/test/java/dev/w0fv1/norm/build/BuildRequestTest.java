package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BuildRequestTest {
  @Test
  void normalizesInputWithoutDiscoveringTheProject() {
    Path input = Path.of("sample", "..", "web.norm");
    var request =
        new BuildRequest(
            input,
            ApplicationBuildTarget.NATIVE,
            true,
            dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE);
    assertEquals(input.toAbsolutePath().normalize(), request.input());
    assertTrue(request.diagnostics());
  }

  @Test
  void rejectsNativeOnlyOptionsForJvmBuilds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BuildRequest(
                Path.of("web.norm"),
                ApplicationBuildTarget.JVM,
                true,
                dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE));
    assertDoesNotThrow(
        () ->
            new BuildRequest(
                Path.of("web.norm"),
                ApplicationBuildTarget.JVM,
                false,
                dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE));
  }

  @Test
  void rejectsMissingRequestFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new BuildRequest(
                null,
                ApplicationBuildTarget.NATIVE,
                false,
                dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE));
    assertThrows(
        NullPointerException.class,
        () -> new BuildRequest(Path.of("web.norm"), null, false, WindowsSubsystem.CONSOLE));
  }
}
