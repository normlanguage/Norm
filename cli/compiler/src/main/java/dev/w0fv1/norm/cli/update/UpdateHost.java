package dev.w0fv1.norm.cli.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

interface UpdateHost {
  enum PackageManager {
    SCOOP("Scoop", List.of("scoop", "update", "normlang")),
    BREW("Homebrew", List.of("brew", "upgrade", "norm")),
    APT("apt", List.of("apt-get", "install", "--only-upgrade", "norm"));

    final String label;
    final List<String> command;

    PackageManager(String label, List<String> command) {
      this.label = label;
      this.command = command;
    }
  }

  Optional<PackageManager> packageManager() throws IOException;

  int updatePackage(PackageManager manager, Consumer<String> output) throws IOException;

  String installedVersion() throws IOException;

  Path launcherPath() throws IOException;

  void install(Path staged, Path target) throws IOException;
}
