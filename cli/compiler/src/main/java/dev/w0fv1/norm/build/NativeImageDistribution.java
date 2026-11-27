package dev.w0fv1.norm.build;

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
          "graalvm-community-jdk-25i6-25.0.5.1_windows-x64_bin.zip",
          "1f534c46337f838880553c47f973a3d769b4058036288afcec930e2abddf06a1",
          ArchiveFormat.ZIP);
    }
    if (operatingSystem.startsWith("linux") && isX64(architecture)) {
      return distribution(
          "linux-x64",
          "graalvm-community-jdk-25i6-25.0.5.1_linux-x64_bin.tar.gz",
          "d792848db26f1602f0a5cf2cd9279d9a652d32ab450d891ce616c9ca67e359d0",
          ArchiveFormat.TAR_GZIP);
    }
    if ((operatingSystem.startsWith("mac") || operatingSystem.startsWith("darwin"))
        && isArm64(architecture)) {
      return distribution(
          "macos-arm64",
          "graalvm-community-jdk-25i6-25.0.5.1_macos-aarch64_bin.tar.gz",
          "19ef5175f18fe2a957a5ec4cc3dd263a164e88ae621cf80ee1c715ede72469f0",
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
