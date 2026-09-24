package dev.w0fv1.norm.jvm;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public record JavaModulePath(List<Path> paths, List<String> names) {
  public JavaModulePath {
    paths = List.copyOf(paths);
    names = List.copyOf(names);
  }

  public List<String> nativeImageReadOptions() throws IOException {
    var featureModules = new java.util.TreeSet<String>();
    for (var module : ModuleFinder.of(paths.toArray(Path[]::new)).findAll()) {
      try (var reader = module.open();
          var resources = reader.list()) {
        for (String resource : resources.filter(name -> name.endsWith(".class")).toList()) {
          try (var input = reader.open(resource).orElseThrow()) {
            var type = new org.objectweb.asm.ClassReader(input);
            if (java.util.Arrays.asList(type.getInterfaces())
                .contains("org/graalvm/nativeimage/hosted/Feature")) {
              featureModules.add(module.descriptor().name());
              break;
            }
          }
        }
      }
    }
    return featureModules.stream()
        .map(name -> "--add-reads=" + name + "=org.graalvm.nativeimage")
        .toList();
  }

  public static JavaModulePath inspect(Collection<Path> entries) throws IOException {
    var paths = new ArrayList<Path>();
    for (Path path : entries) {
      if (Files.isDirectory(path)) {
        if (Files.isRegularFile(path.resolve("module-info.class"))) paths.add(path);
      } else if (Files.isRegularFile(path)) {
        try (var jar = new JarFile(path.toFile(), false, ZipFile.OPEN_READ, Runtime.version())) {
          if (jar.getJarEntry("module-info.class") != null) paths.add(path);
        }
      }
    }
    var names =
        ModuleFinder.of(paths.toArray(Path[]::new)).findAll().stream()
            .map(reference -> reference.descriptor().name())
            .sorted()
            .toList();
    return new JavaModulePath(paths, names);
  }

  public static JavaModulePath nativeImage(Collection<Path> entries, Collection<String> userRoots)
      throws IOException {
    var roots = new java.util.TreeSet<>(userRoots);
    var forcedPaths = new java.util.LinkedHashSet<Path>();
    for (Path path : entries) {
      if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) continue;
      try (var jar = new JarFile(path.toFile(), false, ZipFile.OPEN_READ, Runtime.version())) {
        for (var resource :
            jar.stream()
                .filter(entry -> entry.getName().startsWith("META-INF/native-image/"))
                .filter(entry -> entry.getName().endsWith("/native-image.properties"))
                .toList()) {
          var properties = new Properties();
          try (var input = jar.getInputStream(resource)) {
            properties.load(input);
          }
          String name = properties.getProperty("ForceOnModulePath");
          if (name == null) continue;
          if (ModuleFinder.of(path).find(name).isEmpty()) {
            throw new java.lang.module.FindException(
                "ForceOnModulePath module " + name + " not found in " + path);
          }
          roots.add(name);
          forcedPaths.add(path);
        }
      }
    }
    return select(entries, roots, forcedPaths);
  }

  public static JavaModulePath select(Collection<Path> entries, Collection<String> roots)
      throws IOException {
    return select(entries, roots, List.of());
  }

  private static JavaModulePath select(
      Collection<Path> entries, Collection<String> roots, Collection<Path> forcedPaths)
      throws IOException {
    var available = inspect(entries);
    var candidatePaths = new java.util.LinkedHashSet<>(available.paths());
    candidatePaths.addAll(forcedPaths);
    var references =
        ModuleFinder.of(candidatePaths.toArray(Path[]::new)).findAll().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    reference -> reference.descriptor().name(), reference -> reference));
    var selected = new java.util.TreeSet<String>();
    for (String root : roots) {
      if (!references.containsKey(root))
        throw new java.lang.module.FindException("Application module root not found: " + root);
    }
    var pending = new java.util.ArrayDeque<String>(roots);
    while (!pending.isEmpty()) {
      String name = pending.removeFirst();
      var reference = references.get(name);
      if (reference == null || !selected.add(name)) continue;
      reference.descriptor().requires().stream()
          .filter(
              requirement ->
                  !requirement
                      .modifiers()
                      .contains(java.lang.module.ModuleDescriptor.Requires.Modifier.STATIC))
          .map(java.lang.module.ModuleDescriptor.Requires::name)
          .forEach(pending::addLast);
    }
    var paths =
        selected.stream()
            .map(name -> Path.of(references.get(name).location().orElseThrow()))
            .toList();
    return new JavaModulePath(paths, List.copyOf(selected));
  }
}
