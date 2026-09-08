package dev.w0fv1.norm.value;

import java.nio.file.Path;
import java.util.List;

public record ModuleSourceLayout(List<String> sources, List<String> tests) {
  public ModuleSourceLayout {
    sources = List.copyOf(sources);
    tests = List.copyOf(tests);
    var roots = new java.util.HashSet<String>();
    for (String value : java.util.stream.Stream.concat(sources.stream(), tests.stream()).toList()) {
      Path path = Path.of(value).normalize();
      if (value.isBlank() || path.getRoot() != null || path.startsWith("..")) {
        throw new IllegalArgumentException("source root must be relative to its module: " + value);
      }
      if (!roots.add(path.toString())) {
        throw new IllegalArgumentException("duplicate source root: " + value);
      }
    }
  }

  public static ModuleSourceLayout defaults() {
    return new ModuleSourceLayout(List.of("."), List.of("tests"));
  }

  public java.util.Optional<Root> locate(Path moduleDirectory, Path source) {
    return java.util.stream.Stream.concat(
            sources.stream()
                .map(path -> new Root(moduleDirectory.resolve(path).normalize(), Kind.PRODUCTION)),
            tests.stream()
                .map(path -> new Root(moduleDirectory.resolve(path).normalize(), Kind.TEST)))
        .filter(root -> source.startsWith(root.directory()))
        .max(java.util.Comparator.comparingInt(root -> root.directory().getNameCount()));
  }

  public enum Kind {
    PRODUCTION,
    TEST
  }

  public record Root(Path directory, Kind kind) {}
}
