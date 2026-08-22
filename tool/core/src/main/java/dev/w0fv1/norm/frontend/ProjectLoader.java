package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectLoader {
  public ProjectLoader() {}

  public CompilationRequest load(Path entryPath) throws IOException {
    Path entry = entryPath.toAbsolutePath().normalize();
    SourceFile entrySource = SourceFile.read(entry);
    Syntax.Program header = parse(entrySource);
    if (header.packageName().isEmpty()) return CompilationRequest.single(entrySource);

    Path sourceRoot = entry.getParent();
    List<String> segments = List.of(header.packageName().split("\\."));
    for (int index = segments.size() - 1; index >= 0; index--) {
      if (sourceRoot == null
          || sourceRoot.getFileName() == null
          || !sourceRoot.getFileName().toString().equals(segments.get(index))) {
        throw new IOException(
            "package '" + header.packageName() + "' does not match the entry directory");
      }
      sourceRoot = sourceRoot.getParent();
    }
    if (sourceRoot == null) throw new IOException("cannot determine source root");

    ModuleLoader moduleLoader = new ModuleLoader();
    Map<Path, SourceFile> sourcesByPath = new LinkedHashMap<>();
    Set<dev.w0fv1.norm.value.DocumentId> exportedSources = new LinkedHashSet<>();
    try (FileResolver resolver = new FileResolver(sourceRoot)) {
      for (SourceFile source : moduleLoader.loadPackage(resolver, header.packageName())) {
        sourcesByPath.put(source.path(), source.path().equals(entry) ? entrySource : source);
      }
      Path manifestPath = sourceRoot.resolve("module.norm");
      if (Files.isRegularFile(manifestPath)) {
        ModuleLoader.LoadedModule loaded = moduleLoader.load(resolver);
        ModuleManifest manifest = loaded.manifest();
        if (!header.packageName().equals(manifest.name())
            && !header.packageName().startsWith(manifest.name() + ".")) {
          throw new IOException(
              "entry package '"
                  + header.packageName()
                  + "' is outside module package '"
                  + manifest.name()
                  + "'");
        }
        for (SourceFile source : loaded.sources()) sourcesByPath.put(source.path(), source);
        exportedSources.addAll(loaded.exportedSources());
      }
    }
    if (!sourcesByPath.containsKey(entry)) {
      throw new IOException("entry source is not part of package '" + header.packageName() + "'");
    }
    return new CompilationRequest(
        entrySource.id(), List.copyOf(sourcesByPath.values()), exportedSources);
  }

  private static Syntax.Program parse(SourceFile source) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    return new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics).parse();
  }

  private static final class FileResolver implements ModuleSourceResolver {
    private final Path root;

    private FileResolver(Path root) {
      this.root = root;
    }

    @Override
    public SourceFile read(String relativePath) throws IOException {
      Path path = resolve(relativePath);
      if (!Files.isRegularFile(path))
        throw new IOException("source '" + relativePath + "' does not exist");
      return SourceFile.read(path);
    }

    @Override
    public List<String> list(String relativeDirectory) throws IOException {
      Path directory = resolve(relativeDirectory);
      if (!Files.isDirectory(directory)) return List.of();
      try (var files = Files.list(directory)) {
        return files
            .filter(Files::isRegularFile)
            .map(root::relativize)
            .map(path -> path.toString().replace('\\', '/'))
            .toList();
      }
    }

    private Path resolve(String relativePath) throws IOException {
      Path path =
          root.resolve(relativePath.replace('/', root.getFileSystem().getSeparator().charAt(0)))
              .normalize();
      if (!path.startsWith(root)) throw new IOException("source path escapes the module root");
      return path;
    }
  }
}
