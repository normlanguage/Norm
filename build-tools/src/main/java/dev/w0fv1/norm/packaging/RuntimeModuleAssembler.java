package dev.w0fv1.norm.packaging;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

public final class RuntimeModuleAssembler {
  private static final List<String> MAVEN_PROVIDER_COMPONENTS =
      List.of(
          "maven.resolver.provider",
          "maven.model.builder",
          "maven.model",
          "maven.repository.metadata",
          "maven.artifact",
          "maven.builder.support");
  private static final long ZIP_TIMESTAMP = 315532800000L;

  private RuntimeModuleAssembler() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 3)
      throw new IllegalArgumentException(
          "Expected compiler JAR or --dependencies-only, runtime classpath and output directory");
    List<Path> dependencies =
        Arrays.stream(arguments[1].split(Pattern.quote(File.pathSeparator)))
            .filter(value -> !value.isBlank())
            .map(Path::of)
            .filter(
                path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
            .toList();
    if (arguments[0].equals("--dependencies-only"))
      assembleDependencies(dependencies, Path.of(arguments[2]));
    else assemble(Path.of(arguments[0]), dependencies, Path.of(arguments[2]));
  }

  public static void assemble(Path compilerJar, List<Path> dependencies, Path output)
      throws IOException {
    if (!Files.isRegularFile(compilerJar))
      throw new IllegalArgumentException("Compiler JAR is missing: " + compilerJar);
    assemble(dependencies, Optional.of(compilerJar), output);
  }

  public static Map<Path, List<Path>> assembleDependencies(List<Path> dependencies, Path output)
      throws IOException {
    return assemble(dependencies, Optional.empty(), output);
  }

  private static Map<Path, List<Path>> assemble(
      List<Path> dependencies, Optional<Path> compilerJar, Path output) throws IOException {
    Map<String, Path> inputs = new TreeMap<>();
    for (Path dependency : dependencies) {
      if (!Files.isRegularFile(dependency))
        throw new IllegalArgumentException("Runtime dependency is missing: " + dependency);
      if (!dependency.getFileName().toString().endsWith(".jar"))
        throw new IllegalArgumentException("Invalid runtime JAR: " + dependency);
      var modules = ModuleFinder.of(dependency).findAll();
      if (modules.size() != 1)
        throw new IllegalArgumentException("Expected one module in runtime JAR: " + dependency);
      String moduleName = modules.iterator().next().descriptor().name();
      if (inputs.putIfAbsent(moduleName, dependency) != null)
        throw new IllegalArgumentException("Duplicate runtime module: " + moduleName);
    }
    Map<String, Path> components = new LinkedHashMap<>();
    String version = null;
    for (String moduleName : MAVEN_PROVIDER_COMPONENTS) {
      Path component = inputs.remove(moduleName);
      if (component == null)
        throw new IllegalArgumentException("Missing Maven provider module: " + moduleName);
      String componentVersion;
      try (var archive = new JarFile(component.toFile())) {
        var manifest = archive.getManifest();
        componentVersion =
            manifest == null
                ? null
                : manifest.getMainAttributes().getValue("Implementation-Version");
      }
      if (componentVersion == null || componentVersion.isBlank())
        throw new IllegalArgumentException("Missing Maven provider version: " + moduleName);
      if (version != null && !version.equals(componentVersion))
        throw new IllegalArgumentException("Maven provider components have different versions");
      version = componentVersion;
      components.put(moduleName, component);
    }

    Path destination = output.toAbsolutePath();
    Files.createDirectories(destination.getParent());
    Path staged = Files.createTempDirectory(destination.getParent(), ".norm-runtime-");
    Map<Path, List<Path>> ownership = new LinkedHashMap<>();
    try {
      Path lib = Files.createDirectory(staged.resolve("lib"));
      if (compilerJar.isPresent()) {
        Files.copy(compilerJar.get(), lib.resolve(compilerJar.get().getFileName()));
        try (var archive = new JarFile(compilerJar.get().toFile())) {
          for (String name : List.of("LICENSE", "LICENSING.md")) {
            String entryName = "META-INF/" + name;
            JarEntry entry = archive.getJarEntry(entryName);
            if (entry == null) throw new IOException("Compiler JAR is missing " + entryName);
            try (var input = archive.getInputStream(entry)) {
              Files.copy(input, staged.resolve(name));
            }
          }
        }
      }
      for (var input : inputs.entrySet()) {
        Files.copy(input.getValue(), lib.resolve(input.getKey() + ".jar"));
        ownership.put(
            destination.resolve("lib").resolve(input.getKey() + ".jar"), List.of(input.getValue()));
      }
      merge(components, lib.resolve("maven.resolver.provider.jar"), version);
      ownership.put(
          destination.resolve("lib/maven.resolver.provider.jar"), List.copyOf(components.values()));
      if (Files.exists(destination)) deleteTree(destination);
      Files.move(staged, destination);
    } finally {
      if (Files.exists(staged)) deleteTree(staged);
    }
    return Map.copyOf(ownership);
  }

  private static void merge(Map<String, Path> components, Path destination, String version)
      throws IOException {
    Map<String, byte[]> entries = new TreeMap<>();
    Map<String, List<String>> combined = new TreeMap<>();
    boolean multiRelease = false;
    for (var component : components.entrySet()) {
      try (var input = new JarFile(component.getValue().toFile())) {
        if (input.getManifest() != null)
          multiRelease |=
              Boolean.parseBoolean(
                  input.getManifest().getMainAttributes().getValue("Multi-Release"));
        var members = input.entries();
        while (members.hasMoreElements()) {
          JarEntry member = members.nextElement();
          String name = member.getName();
          if (member.isDirectory()
              || name.equalsIgnoreCase("META-INF/MANIFEST.MF")
              || signature(name)) continue;
          byte[] data;
          try (var stream = input.getInputStream(member)) {
            data = stream.readAllBytes();
          }
          if (name.equals("META-INF/NOTICE")
              || name.equals("META-INF/DEPENDENCIES")
              || name.equals("META-INF/LICENSE")) {
            combined
                .computeIfAbsent(name, ignored -> new ArrayList<>())
                .add(component.getKey() + ":\n" + new String(data, StandardCharsets.UTF_8).strip());
          } else if (name.startsWith("META-INF/services/")
              || name.equals("META-INF/sisu/javax.inject.Named")) {
            combined
                .computeIfAbsent(name, ignored -> new ArrayList<>())
                .add(new String(data, StandardCharsets.UTF_8));
          } else {
            byte[] previous = entries.putIfAbsent(name, data);
            if (previous != null && !Arrays.equals(previous, data))
              throw new IllegalArgumentException("Conflicting Maven provider entry: " + name);
          }
        }
      }
    }
    for (var entry : combined.entrySet()) {
      String name = entry.getKey();
      String text;
      if (name.startsWith("META-INF/services/")
          || name.equals("META-INF/sisu/javax.inject.Named")) {
        var lines = new LinkedHashSet<String>();
        entry
            .getValue()
            .forEach(
                value ->
                    value.lines().map(String::strip).filter(s -> !s.isEmpty()).forEach(lines::add));
        text = String.join("\n", lines) + "\n";
      } else {
        text = String.join("\n\n", entry.getValue()) + "\n";
      }
      entries.put(name, text.getBytes(StandardCharsets.UTF_8));
    }
    String manifest =
        "Manifest-Version: 1.0\r\nAutomatic-Module-Name: maven.resolver.provider\r\n"
            + "Implementation-Version: "
            + version
            + "\r\n"
            + (multiRelease ? "Multi-Release: true\r\n" : "")
            + "\r\n";
    try (var output = new JarOutputStream(Files.newOutputStream(destination))) {
      write(output, "META-INF/MANIFEST.MF", manifest.getBytes(StandardCharsets.UTF_8));
      for (var entry : entries.entrySet()) write(output, entry.getKey(), entry.getValue());
    }
  }

  private static boolean signature(String name) {
    String upper = name.toUpperCase(java.util.Locale.ROOT);
    return upper.startsWith("META-INF/")
        && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
  }

  private static void write(JarOutputStream output, String name, byte[] data) throws IOException {
    var entry = new JarEntry(name);
    entry.setTime(ZIP_TIMESTAMP);
    output.putNextEntry(entry);
    output.write(data);
    output.closeEntry();
  }

  private static void deleteTree(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
    }
  }
}
