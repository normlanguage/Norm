package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.value.BuildMetadata;
import dev.w0fv1.norm.value.Sha256Digest;
import java.net.URI;
import java.util.Locale;

record NativeImageDistribution(
    String platform, String version, URI archive, Sha256Digest integrity, ArchiveFormat format) {
  private static final String VERSION = BuildMetadata.GRAALVM_VERSION;
  private static final String RELEASE =
      "https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-" + VERSION + "/";

  static NativeImageDistribution current() {
    String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (operatingSystem.startsWith("windows") && isX64(architecture)) {
      return distribution(
          "windows-x64",
          "graalvm-community-jdk-25i1-25.0.3_windows-x64_bin.zip",
          "96f1e7a9674b5b5751224104ff0c4624f15f9522b190d960a041cab72c381393",
          ArchiveFormat.ZIP);
    }
    if (operatingSystem.startsWith("linux") && isX64(architecture)) {
      return distribution(
          "linux-x64",
          "graalvm-community-jdk-25i1-25.0.3_linux-x64_bin.tar.gz",
          "e9cd1637be853e105f8b09125b4b19fbce385696465d782cbca8bb80e1df8f0d",
          ArchiveFormat.TAR_GZIP);
    }
    if ((operatingSystem.startsWith("mac") || operatingSystem.startsWith("darwin"))
        && isArm64(architecture)) {
      return distribution(
          "macos-arm64",
          "graalvm-community-jdk-25i1-25.0.3_macos-aarch64_bin.tar.gz",
          "b41cdde27691a0e04a1f2b0660624bc37e59c738e536888860c4c9f65a4a9a3b",
          ArchiveFormat.TAR_GZIP);
    }
    throw new IllegalStateException(
        "Native builds are unsupported on " + operatingSystem + " " + architecture);
  }

  String identity() {
    return version + ":" + platform + ":" + integrity.value();
  }

  private static NativeImageDistribution distribution(
      String platform, String name, String integrity, ArchiveFormat format) {
    return new NativeImageDistribution(
        platform, VERSION, URI.create(RELEASE + name), Sha256Digest.parse(integrity), format);
  }

  private static boolean isX64(String architecture) {
    return architecture.equals("amd64") || architecture.equals("x86_64");
  }

  private static boolean isArm64(String architecture) {
    return architecture.equals("aarch64") || architecture.equals("arm64");
  }

  enum ArchiveFormat {
    ZIP,
    TAR_GZIP
  }
}
