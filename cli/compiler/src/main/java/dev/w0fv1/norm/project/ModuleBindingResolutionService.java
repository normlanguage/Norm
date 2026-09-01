package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.ModuleBindingSourceEditor;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.Sha256Digest;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ModuleBindingResolutionService {
  private final ProjectLoader projects;
  private final ModuleBindingSourceEditor editor;

  public ModuleBindingResolutionService(ProjectLoader projects) {
    this.projects = Objects.requireNonNull(projects, "projects");
    editor = new ModuleBindingSourceEditor();
  }

  public Resolution resolve(Path modulePath) throws IOException {
    Path path = Objects.requireNonNull(modulePath, "modulePath").toAbsolutePath().normalize();
    SourceFile source = SourceFile.read(path);
    ModuleDescriptor descriptor = projects.evaluateModule(source);
    if (descriptor.binding().isEmpty())
      throw new IOException("module does not declare a JAR binding");
    ResolvedJarGraph graph = projects.resolveJarBinding(source);
    Sha256Digest digest = graph.contentId();
    String updated = editor.withDigest(source, descriptor.binding().orElseThrow().target(), digest);
    boolean changed = !source.text().equals(updated);
    if (changed) replace(path, updated);
    return new Resolution(descriptor.coordinate(), digest, changed);
  }

  private static void replace(Path target, String content) throws IOException {
    Path parent = target.getParent();
    if (parent == null) throw new IOException("module configuration path has no parent");
    Path temporary = Files.createTempFile(parent, ".module.norm-", ".tmp");
    try {
      Files.writeString(temporary, content, StandardCharsets.UTF_8);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public record Resolution(ModuleCoordinate module, Sha256Digest digest, boolean changed) {
    public Resolution {
      Objects.requireNonNull(module, "module");
      Objects.requireNonNull(digest, "digest");
    }
  }
}
