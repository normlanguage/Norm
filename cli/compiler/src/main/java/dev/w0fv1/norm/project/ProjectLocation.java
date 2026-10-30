package dev.w0fv1.norm.project;

import dev.w0fv1.norm.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record ProjectLocation(Path standaloneRoot, Optional<SourceFile> module, List<Path> candidates) {
  ProjectLocation {
    candidates = List.copyOf(candidates);
  }

  static Map<Path, SourceFile> overlays(SourceFile entry, Collection<SourceFile> overlays) {
    var sources = new LinkedHashMap<Path, SourceFile>();
    for (var source : overlays) sources.put(ProjectPaths.normalize(source.path()), source);
    sources.put(ProjectPaths.normalize(entry.path()), entry);
    return Map.copyOf(sources);
  }

  static ProjectLocation discover(Path entry, Map<Path, SourceFile> overlays) throws IOException {
    Path fallback = entry.getParent();
    if (fallback == null) throw new IllegalArgumentException("source path has no parent");
    var candidates = new ArrayList<Path>();
    for (Path current = fallback; current != null; current = current.getParent()) {
      for (String name : ModuleInput.ENTRY_NAMES) {
        Path candidate = ProjectPaths.normalize(current.resolve(name));
        candidates.add(candidate);
        SourceFile source = overlays.get(candidate);
        if (source == null && Files.isRegularFile(candidate)) source = SourceFile.read(candidate);
        if (source != null && ModuleInput.isModuleEntry(source))
          return new ProjectLocation(current, Optional.of(source), candidates);
      }
    }
    return new ProjectLocation(fallback, Optional.empty(), candidates);
  }
}
