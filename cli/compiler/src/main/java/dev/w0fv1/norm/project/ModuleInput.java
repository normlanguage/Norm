package dev.w0fv1.norm.project;

import dev.w0fv1.norm.application.ApplicationEntry;
import dev.w0fv1.norm.frontend.SourceStructure;
import dev.w0fv1.norm.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModuleInput {
  private ModuleInput() {}

  public static Path resolve(Path input) throws IOException {
    Path path = input.toAbsolutePath().normalize();
    if (!Files.isDirectory(path)) return path;
    Path module = path.resolve("module.norm");
    return Files.isRegularFile(module) ? module : ApplicationEntry.resolve(path);
  }

  public static SourceFile configuration(SourceFile source) throws IOException {
    if (ModuleSourceFiles.isModuleSource(source)) return source;
    return SourceStructure.inspect(source)
        .moduleConfiguration()
        .orElseThrow(
            () ->
                new IOException("source does not contain a module declaration: " + source.path()));
  }
}
