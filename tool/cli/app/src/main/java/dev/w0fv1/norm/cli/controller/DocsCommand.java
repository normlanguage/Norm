package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.component.ApiDocumentationWriter;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.documentation.DocumentationGenerator;
import dev.w0fv1.norm.documentation.MissingDocumentationException;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DocsCommand implements Command {
  @Override
  public String name() {
    return "docs";
  }

  @Override
  public String summary() {
    return "Export structured module API documentation";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    Optional<Options> parsed = options(arguments, err);
    if (parsed.isEmpty()) return ExitCode.USAGE_ERROR;
    Options options = parsed.orElseThrow();
    Path moduleRoot = options.moduleRoot().toAbsolutePath().normalize();
    Path modulePath = moduleRoot.resolve("module.norm");
    if (!Files.isDirectory(moduleRoot) || !Files.isRegularFile(modulePath)) {
      err.printf("error[NORM-DOC-0001]: documentation root must contain module.norm: %s%n", moduleRoot);
      return ExitCode.INPUT_ERROR;
    }

    try {
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
      try (ProjectLoader projects = environment.projectLoader();
          CompilerSession compiler = environment.compilerSession()) {
        SourceFile moduleSource = SourceFile.read(modulePath);
        ModuleDescriptor descriptor = projects.evaluateModule(moduleSource);
        LoadedModule loaded =
            descriptor.name().equals("std")
                ? standardLibrary(moduleRoot, descriptor, compiler)
                : projectModule(moduleRoot, modulePath, descriptor, projects, compiler);
        if (loaded.snapshot().analysis().hasErrors()) {
          loaded.snapshot().diagnostics().forEach(diagnostic -> err.println(DiagnosticRenderer.render(diagnostic)));
          return ExitCode.COMPILATION_ERROR;
        }
        var documentation =
            new DocumentationGenerator()
                .generate(
                    descriptor.coordinate(),
                    loaded.sourcePaths(),
                    loaded.exportedSources(),
                    loaded.snapshot(),
                    options.strict());
        new ApiDocumentationWriter().write(documentation, options.output());
        out.printf("Generated %s%n", options.output().toAbsolutePath().normalize());
        return ExitCode.SUCCESS;
      }
    } catch (MissingDocumentationException exception) {
      exception
          .declarations()
          .forEach(
              declaration ->
                  err.printf(
                      "error[NORM-DOC-0002]: exported declaration '%s' is missing @Document%n",
                      declaration));
      return ExitCode.COMPILATION_ERROR;
    } catch (IOException exception) {
      err.printf("error[NORM-DOC-0001]: cannot export module documentation: %s%n", exception.getMessage());
      return ExitCode.INPUT_ERROR;
    } catch (dev.w0fv1.norm.frontend.CompilationInfrastructureException exception) {
      err.printf("error[NORM-CLI-0005]: compiler storage unavailable: %s%n", exception.getMessage());
      return ExitCode.INTERNAL_ERROR;
    }
  }

  private static LoadedModule projectModule(
      Path moduleRoot,
      Path modulePath,
      ModuleDescriptor descriptor,
      ProjectLoader projects,
      CompilerSession compiler)
      throws IOException {
    ProjectSourceSet sourceSet = loadSourceSet(moduleRoot, modulePath, projects);
    if (sourceSet.rootModulePath().isEmpty()
        || !sourceSet.rootModulePath().orElseThrow().equals(modulePath)) {
      throw new IOException("documentation root does not identify the loaded module");
    }
    CompilationSnapshot snapshot = compiler.snapshot(sourceSet.compilationRequest());
    Map<DocumentId, String> sourcePaths = new LinkedHashMap<>();
    Set<DocumentId> exported = new LinkedHashSet<>();
    for (SourceFile source : sourceSet.sources()) {
      if (!sourceSet.scope().coordinate(source.id()).module().equals(descriptor.coordinate())) continue;
      Path path = source.path().toAbsolutePath().normalize();
      if (!path.startsWith(moduleRoot)) continue;
      sourcePaths.put(source.id(), relative(moduleRoot, path));
      if (sourceSet.exportedSourcePaths().contains(path)) exported.add(source.id());
    }
    return new LoadedModule(sourcePaths, exported, snapshot);
  }

  private static LoadedModule standardLibrary(
      Path moduleRoot, ModuleDescriptor descriptor, CompilerSession compiler) throws IOException {
    List<Path> paths;
    try (var files = Files.walk(moduleRoot)) {
      paths =
          files.filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".norm"))
              .filter(path -> !path.equals(moduleRoot.resolve("module.norm")))
              .sorted(Comparator.comparing(Path::toString))
              .toList();
    }
    if (paths.isEmpty()) throw new IOException("module does not contain any Norm source files");
    List<SourceFile> overlays = new ArrayList<>();
    Map<DocumentId, String> sourcePaths = new LinkedHashMap<>();
    for (Path path : paths) {
      String relative = relative(moduleRoot, path);
      DocumentId document = DocumentId.of("stdlib:/" + descriptor.name().replace('.', '/') + "/" + relative);
      if (compiler.preludeSource(document).isEmpty()) {
        throw new IOException("standard library source is not part of this Norm build: " + relative);
      }
      overlays.add(SourceFile.of(document, Files.readString(path)));
      sourcePaths.put(document, relative);
    }
    Set<DocumentId> exported =
        descriptor.exports().stream()
            .map(descriptor::sourcePath)
            .map(path -> DocumentId.of("stdlib:/" + path))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    CompilationSnapshot snapshot = compiler.preludeSnapshot(overlays, overlays.getFirst().id());
    return new LoadedModule(sourcePaths, exported, snapshot);
  }

  private static ProjectSourceSet loadSourceSet(
      Path moduleRoot, Path modulePath, ProjectLoader projects) throws IOException {
    List<Path> candidates;
    try (var paths = Files.walk(moduleRoot)) {
      candidates =
          paths.filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".norm"))
              .filter(path -> !path.equals(modulePath))
              .sorted(Comparator.comparing(Path::toString))
              .toList();
    }
    List<Path> moduleSources = new ArrayList<>();
    for (Path candidate : candidates) {
      if (!insideNestedModule(moduleRoot, candidate)) moduleSources.add(candidate);
    }
    if (moduleSources.isEmpty()) {
      throw new IOException("module does not contain any loadable Norm source files");
    }
    return projects.load(moduleSources.getFirst());
  }

  private static boolean insideNestedModule(Path moduleRoot, Path source) throws IOException {
    Path directory = source.getParent();
    while (directory != null && !directory.equals(moduleRoot)) {
      Path candidate = directory.resolve("module.norm");
      if (Files.isRegularFile(candidate)
          && ProjectLoader.isModuleSource(SourceFile.read(candidate))) return true;
      directory = directory.getParent();
    }
    return false;
  }

  private static Optional<Options> options(List<String> arguments, PrintWriter err) {
    if (arguments.isEmpty()) return usage(err);
    Path moduleRoot;
    try {
      moduleRoot = Path.of(arguments.getFirst());
    } catch (InvalidPathException exception) {
      err.printf("error[NORM-DOC-0001]: invalid module path '%s'%n", arguments.getFirst());
      return Optional.empty();
    }
    Path output = null;
    boolean strict = false;
    for (int index = 1; index < arguments.size(); index++) {
      String argument = arguments.get(index);
      if (argument.equals("--strict")) {
        strict = true;
      } else if (argument.equals("--output") && index + 1 < arguments.size()) {
        try {
          output = Path.of(arguments.get(++index));
        } catch (InvalidPathException exception) {
          err.printf("error[NORM-DOC-0001]: invalid output path '%s'%n", arguments.get(index));
          return Optional.empty();
        }
      } else {
        return usage(err);
      }
    }
    return output == null ? usage(err) : Optional.of(new Options(moduleRoot, output, strict));
  }

  private static Optional<Options> usage(PrintWriter err) {
    err.println("Usage: norm docs <module-directory> --output <api-directory> [--strict]");
    return Optional.empty();
  }

  private static String relative(Path root, Path path) {
    return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }

  private record Options(Path moduleRoot, Path output, boolean strict) {}

  private record LoadedModule(
      Map<DocumentId, String> sourcePaths,
      Set<DocumentId> exportedSources,
      CompilationSnapshot snapshot) {
    private LoadedModule {
      sourcePaths = Map.copyOf(sourcePaths);
      exportedSources = Set.copyOf(exportedSources);
    }
  }
}
