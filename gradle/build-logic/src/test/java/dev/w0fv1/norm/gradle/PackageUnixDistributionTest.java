package dev.w0fv1.norm.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageUnixDistributionTest {
  @TempDir Path directory;

  @Test
  void archiveRootIsTraversableWhenRuntimeSourceIsPrivate() throws Exception {
    Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
    Path runtime = directory.resolve("runtime-source");
    Files.createDirectories(runtime.resolve("bin"));
    Files.writeString(runtime.resolve("bin/norm"), "#!/bin/sh\n");
    Files.setPosixFilePermissions(runtime, PosixFilePermissions.fromString("rwx------"));
    var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    var task = project.getTasks().create("packageUnix", PackageUnixDistribution.class);
    task.getRuntimeDirectory().set(runtime.toFile());
    task.getStagingDirectory().set(directory.resolve("stage").toFile());
    Path archive = directory.resolve("norm.tar.gz");
    task.getArchive().set(archive.toFile());
    task.packageAsset();
    Path extracted = directory.resolve("extracted");
    Files.createDirectories(extracted);
    var unpack =
        new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", extracted.toString()).start();
    assertEquals(0, unpack.waitFor(), new String(unpack.getErrorStream().readAllBytes()));
    assertEquals(
        PosixFilePermissions.fromString("rwxr-xr-x"),
        Files.getPosixFilePermissions(extracted.resolve("norm")));
  }
}
