package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class JavaAnnotationProcessorPipeline {
  public JavaAnnotationProcessingOutput process(
      CoreArtifact artifact,
      List<ResolvedJarBinding> bindings,
      Path projectRoot,
      CompilationScope scope,
      DocumentId entryDocument,
      Set<DocumentId> bindingDocuments)
      throws JavaAnnotationProcessingException {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(bindings, "bindings");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(entryDocument, "entryDocument");
    bindingDocuments = Set.copyOf(bindingDocuments);
    Path root = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
    List<JavaAnnotationStub> stubs;
    try {
      stubs =
          new JavaAnnotationStubGenerator()
              .generate(artifact, bindings, scope, entryDocument, bindingDocuments);
    } catch (IllegalArgumentException exception) {
      throw new JavaAnnotationProcessingException(exception.getMessage(), exception);
    }
    Path build = root.resolve("build/norm");
    Path output = build.resolve("java");
    if (stubs.isEmpty()) {
      try {
        delete(output);
      } catch (IOException exception) {
        throw new JavaAnnotationProcessingException(
            "cannot clear Java annotation output " + output, exception);
      }
      return new JavaAnnotationProcessingOutput(output, stubs);
    }
    Path staging = null;
    try {
      Files.createDirectories(build);
      staging = Files.createTempDirectory(build, "java-");
      Path sources = Files.createDirectories(staging.resolve("sources"));
      Path generated = Files.createDirectories(staging.resolve("generated-sources"));
      Path classes = Files.createDirectories(staging.resolve("classes"));
      List<Path> sourceFiles = writeSources(sources, stubs);
      List<Path> classpath = classpath(bindings);
      Path arguments = staging.resolve("javac.args");
      Files.writeString(
          arguments, arguments(classpath, generated, classes, sourceFiles), StandardCharsets.UTF_8);
      Process process =
          new ProcessBuilder(javac().toString(), "@" + arguments.toAbsolutePath())
              .redirectErrorStream(true)
              .start();
      String diagnostics =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int status;
      try {
        status = process.waitFor();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new JavaAnnotationProcessingException(
            "Java annotation processing was interrupted", exception);
      }
      if (status != 0) {
        throw new JavaAnnotationProcessingException(
            diagnostics.isBlank()
                ? "Java annotation processing failed with exit code " + status
                : diagnostics.strip());
      }
      replace(staging, output);
      staging = null;
      return new JavaAnnotationProcessingOutput(output, stubs);
    } catch (JavaAnnotationProcessingException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw new JavaAnnotationProcessingException(exception.getMessage(), exception);
    } catch (IOException exception) {
      throw new JavaAnnotationProcessingException(
          "cannot run Java annotation processing", exception);
    } finally {
      if (staging != null) {
        try {
          delete(staging);
        } catch (IOException ignored) {
        }
      }
    }
  }

  private static List<Path> writeSources(Path root, List<JavaAnnotationStub> stubs)
      throws IOException {
    List<Path> result = new ArrayList<>();
    for (JavaAnnotationStub stub : stubs) {
      Path source = root.resolve(stub.binaryName().replace('.', '/') + ".java");
      Files.createDirectories(source.getParent());
      Files.writeString(source, stub.source(), StandardCharsets.UTF_8);
      result.add(source);
    }
    return List.copyOf(result);
  }

  private static List<Path> classpath(List<ResolvedJarBinding> bindings) {
    Set<Path> paths = new LinkedHashSet<>();
    try {
      paths.add(
          Path.of(
                  JavaApplicationBridge.class
                      .getProtectionDomain()
                      .getCodeSource()
                      .getLocation()
                      .toURI())
              .toAbsolutePath()
              .normalize());
    } catch (java.net.URISyntaxException exception) {
      throw new IllegalStateException("invalid Norm runtime classpath", exception);
    }
    ResolvedJarClasspath.resolve(bindings.stream().map(ResolvedJarBinding::graph).toList())
        .forEach(paths::add);
    return List.copyOf(paths);
  }

  private static String arguments(
      List<Path> classpath, Path generated, Path classes, List<Path> sources) {
    String joinedClasspath =
        classpath.stream()
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    List<String> values = new ArrayList<>();
    values.add("--release");
    values.add("17");
    values.add("-encoding");
    values.add("UTF-8");
    values.add("-parameters");
    values.add("-proc:full");
    values.add("-classpath");
    values.add(joinedClasspath);
    values.add("-processorpath");
    values.add(joinedClasspath);
    values.add("-s");
    values.add(generated.toAbsolutePath().toString());
    values.add("-d");
    values.add(classes.toAbsolutePath().toString());
    sources.stream().map(Path::toAbsolutePath).map(Path::toString).forEach(values::add);
    return values.stream()
        .map(JavaAnnotationProcessorPipeline::argument)
        .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
  }

  private static String argument(String value) {
    return '"' + value.replace("\\", "/").replace("\"", "\\\"") + '"';
  }

  private static Path javac() throws JavaAnnotationProcessingException {
    String executable =
        System.getProperty("os.name", "").startsWith("Windows") ? "javac.exe" : "javac";
    List<Path> candidates = new ArrayList<>();
    String javaHome = System.getProperty("java.home");
    if (javaHome != null && !javaHome.isBlank()) {
      candidates.add(Path.of(javaHome).resolve("bin").resolve(executable));
    }
    String environmentHome = System.getenv("JAVA_HOME");
    if (environmentHome != null && !environmentHome.isBlank()) {
      candidates.add(Path.of(environmentHome).resolve("bin").resolve(executable));
    }
    return candidates.stream()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .filter(Files::isRegularFile)
        .findFirst()
        .orElseThrow(
            () ->
                new JavaAnnotationProcessingException(
                    "a JDK with javac is required for Java annotation processing"));
  }

  private static void replace(Path staging, Path output) throws IOException {
    delete(output);
    try {
      Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(staging, output);
    }
  }

  private static void delete(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
    }
  }
}
