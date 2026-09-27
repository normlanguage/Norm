package dev.w0fv1.norm.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseTargetTest {
  @TempDir Path directory;

  @Test
  void selectsManifestAssetForHost() throws Exception {
    Path manifest = directory.resolve("release-targets.json");
    Files.writeString(
        manifest,
        """
        [{"target":"linux-x64","asset":"norm-v{version}-linux-x64.tar.gz"},
         {"target":"win32-x64","asset":"norm.exe","standalone":"build/distributions/norm.exe"}]
        """);
    var linux = ReleaseTarget.select(manifest, "Linux", "amd64");
    assertEquals("norm-v0.24.0-linux-x64.tar.gz", linux.asset("0.24.0"));
    assertFalse(linux.standalone());
    var windows = ReleaseTarget.select(manifest, "Windows 11", "x86_64");
    assertEquals("norm.exe", windows.asset("0.24.0"));
    assertTrue(windows.standalone());
    assertThrows(IOException.class, () -> ReleaseTarget.select(manifest, "Linux", "arm64"));
  }
}
