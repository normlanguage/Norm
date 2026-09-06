package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ApplicationBuildOptionsTest {
  @Test
  void buildsNativeApplicationsByDefault() {
    ApplicationBuildOptions options = ApplicationBuildOptions.parse(List.of("web.norm"));

    assertEquals(ApplicationBuildTarget.NATIVE, options.target());
    assertEquals("web.norm", options.input());
  }

  @Test
  void requiresAnExplicitOptionForJvmApplications() {
    ApplicationBuildOptions options = ApplicationBuildOptions.parse(List.of("--jvm", "web.norm"));

    assertEquals(ApplicationBuildTarget.JVM, options.target());
    assertEquals("web.norm", options.input());
  }

  @Test
  void rejectsUnknownBuildOptions() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ApplicationBuildOptions.parse(List.of("--unknown", "web.norm")));
  }
}
