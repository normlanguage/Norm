package dev.w0fv1.norm.pyrun;

import java.io.IOException;

public enum PythonRuntimePlatform {
  WINDOWS_X64("windows-x64"),
  LINUX_X64("linux-x64"),
  MACOS_X64("macos-x64"),
  MACOS_ARM64("macos-arm64");

  private final String key;

  PythonRuntimePlatform(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static PythonRuntimePlatform current() throws IOException {
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("win") && (arch.equals("amd64") || arch.equals("x86_64"))) return WINDOWS_X64;
    if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64"))) return LINUX_X64;
    if (os.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"))) return MACOS_ARM64;
    if (os.contains("mac") && (arch.equals("amd64") || arch.equals("x86_64"))) return MACOS_X64;
    throw new IOException("Unsupported Python runtime platform: " + os + "/" + arch);
  }
}
