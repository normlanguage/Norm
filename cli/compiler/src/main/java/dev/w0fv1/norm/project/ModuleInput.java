package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.SourceStructure;
import dev.w0fv1.norm.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModuleInput {
  static final java.util.List<String> ENTRY_NAMES =
      java.util.List.of("module.norm", "application.norm", "main.norm");

  private ModuleInput() {}

  public static Path resolve(Path input) throws IOException {
    Path path = input.toAbsolutePath().normalize();
    if (!Files.isDirectory(path)) return path;
    Path module = path.resolve("module.norm");
    return Files.isRegularFile(module) ? module : ApplicationEntry.resolve(path);
  }

  static boolean isEntryName(Path path) {
    return ENTRY_NAMES.contains(path.getFileName().toString());
  }

  static boolean isModuleEntry(SourceFile source) {
    return ModuleSourceFiles.isModuleSource(source)
        || isEntryName(source.path())
            && SourceStructure.inspect(source).moduleConfiguration().isPresent();
  }
}
