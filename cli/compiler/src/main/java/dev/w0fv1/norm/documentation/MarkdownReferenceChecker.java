package dev.w0fv1.norm.documentation;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MarkdownReferenceChecker {
  private static final DiagnosticCode INVALID_REFERENCE = new DiagnosticCode("NORM-DOC-0003");
  private final ProjectLoader projects;
  private final CompilerSession compiler;

  public MarkdownReferenceChecker(ProjectLoader projects, CompilerSession compiler) {
    this.projects = java.util.Objects.requireNonNull(projects, "projects");
    this.compiler = java.util.Objects.requireNonNull(compiler, "compiler");
  }

  public Result check(Path directory, Optional<Path> module) throws IOException {
    Path root = directory.toAbsolutePath().normalize();
    if (!Files.isDirectory(root))
      throw new IOException("Markdown root must be a directory: " + root);
    var parser = new MarkdownReferences();
    var references = new ArrayList<MarkdownReferences.Reference>();
    var files = new ArrayList<Path>();
    Files.walkFileTree(
        root,
        new java.nio.file.SimpleFileVisitor<>() {
          @Override
          public java.nio.file.FileVisitResult preVisitDirectory(
              Path directory, java.nio.file.attribute.BasicFileAttributes attributes) {
            String name = directory.getFileName().toString();
            return !directory.equals(root) && (name.startsWith(".") || name.equals("node_modules"))
                ? java.nio.file.FileVisitResult.SKIP_SUBTREE
                : java.nio.file.FileVisitResult.CONTINUE;
          }

          @Override
          public java.nio.file.FileVisitResult visitFile(
              Path file, java.nio.file.attribute.BasicFileAttributes attributes) {
            String name = file.getFileName().toString();
            if (attributes.isRegularFile()
                && !name.startsWith(".")
                && name.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) files.add(file);
            return java.nio.file.FileVisitResult.CONTINUE;
          }
        });
    files.sort(Path::compareTo);
    for (Path file : files) references.addAll(parser.parse(SourceFile.read(file)));
    var diagnostics = new ArrayList<Diagnostic>();
    var loaded = new HashMap<ModuleRequirement, ModuleIndex>();
    var failures = new HashMap<ModuleRequirement, String>();
    ModuleIndex local = null;
    String localFailure = null;
    for (var reference : references) {
      try {
        var target = reference.target();
        ModuleIndex index;
        if (target.repository().isPresent()) {
          var requirement =
              projects.resolveReference(
                  target.repository().orElseThrow(), target.path(), target.version());
          if (failures.containsKey(requirement)) throw new IOException(failures.get(requirement));
          index = loaded.get(requirement);
          if (index == null) {
            try {
              index = index(projects.loadForAnalysis(root, requirement));
              loaded.put(requirement, index);
            } catch (IOException exception) {
              failures.put(requirement, exception.getMessage());
              throw exception;
            }
          }
        } else {
          if (module.isEmpty()) throw new IOException("local references require --module");
          if (localFailure != null) throw new IOException(localFailure);
          if (local == null) {
            try {
              local = index(projects.loadForAnalysis(module.orElseThrow()));
            } catch (IOException exception) {
              localFailure = exception.getMessage();
              throw exception;
            }
          }
          index = local;
        }
        if (index.module.version() != target.version()) {
          throw new IllegalArgumentException(
              "reference version "
                  + target.version()
                  + " does not match module "
                  + index.module.name()
                  + " version "
                  + index.module.version());
        }
        var candidates = index.declarations.getOrDefault(target.path(), List.of());
        if (candidates.isEmpty())
          throw new IllegalArgumentException(
              "exported declaration does not exist: " + target.path());
        if (candidates.size() > 1) {
          throw new IllegalArgumentException(
              "ambiguous declaration "
                  + target.path()
                  + "; specify a signature: "
                  + String.join(", ", candidates));
        }
      } catch (IllegalArgumentException | IOException exception) {
        diagnostics.add(
            Diagnostic.error(
                INVALID_REFERENCE,
                "@" + reference.text() + ": " + exception.getMessage(),
                reference.span()));
      }
    }
    return new Result(files.size(), references.size(), diagnostics);
  }

  private ModuleIndex index(ProjectSourceSet sources) throws IOException {
    var snapshot = compiler.snapshot(sources.analysisCompilationRequest());
    if (snapshot.analysis().hasErrors()) {
      throw new IOException(
          "cannot check declarations in a module with compilation errors: "
              + snapshot.diagnostics().stream()
                  .map(dev.w0fv1.norm.diagnostic.DiagnosticRenderer::render)
                  .collect(java.util.stream.Collectors.joining(System.lineSeparator())));
    }
    var coordinate = sources.scope().coordinate(sources.primarySource().id()).module();
    Map<DocumentId, String> paths = new LinkedHashMap<>();
    Set<DocumentId> exports = new LinkedHashSet<>();
    for (var source : sources.sources()) {
      var location = sources.scope().coordinate(source.id());
      if (!location.module().equals(coordinate)) continue;
      paths.put(source.id(), location.relativePath());
      if (sources.exportedSourcePaths().contains(source.path().toAbsolutePath().normalize()))
        exports.add(source.id());
    }
    var documentation =
        new DocumentationGenerator().generate(coordinate, paths, exports, snapshot, false);
    Map<String, List<String>> declarations = new LinkedHashMap<>();
    for (var file : documentation.files()) {
      String fileName =
          file.sourcePath()
              .substring(0, file.sourcePath().length() - ".norm".length())
              .replace('/', '.');
      var pending = new java.util.ArrayDeque<>(file.declarations());
      while (!pending.isEmpty()) {
        var declaration = pending.removeFirst();
        String name = declaration.id().substring(declaration.id().indexOf("::") + 2);
        String selector = fileName + "." + name;
        declarations.computeIfAbsent(selector, ignored -> new ArrayList<>()).add(selector);
        int parameters = selector.indexOf('(');
        if (parameters >= 0)
          declarations
              .computeIfAbsent(selector.substring(0, parameters), ignored -> new ArrayList<>())
              .add(selector);
        pending.addAll(declaration.members());
      }
    }
    return new ModuleIndex(coordinate, declarations);
  }

  public record Result(int files, int references, List<Diagnostic> diagnostics) {
    public Result {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  private record ModuleIndex(
      dev.w0fv1.norm.value.ModuleCoordinate module, Map<String, List<String>> declarations) {}
}
