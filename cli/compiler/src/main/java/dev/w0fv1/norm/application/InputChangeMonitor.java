package dev.w0fv1.norm.application;

import dev.w0fv1.norm.project.ProjectInputSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class InputChangeMonitor implements AutoCloseable {
  private final WatchService watcher = java.nio.file.FileSystems.getDefault().newWatchService();
  private final Map<Path, WatchKey> registered = new LinkedHashMap<>();

  InputChangeMonitor() throws IOException {}

  void update(ProjectInputSnapshot inputs) throws IOException {
    Set<Path> directories = new LinkedHashSet<>();
    for (var watch : inputs.watches()) {
      Path existing = watch.root();
      while (existing != null && !Files.isDirectory(existing)) existing = existing.getParent();
      if (existing != null) directories.add(existing);
    }
    for (var directory : inputs.directories()) {
      if (!Files.isDirectory(directory.root())) continue;
      try (var paths = Files.walk(directory.root())) {
        paths.filter(Files::isDirectory).forEach(directories::add);
      }
    }
    for (Path path : Set.copyOf(registered.keySet())) {
      if (!directories.contains(path) || !registered.get(path).isValid()) {
        registered.remove(path).cancel();
      }
    }
    for (Path path : directories) {
      if (!registered.containsKey(path)) {
        registered.put(
            path,
            path.register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY));
      }
    }
  }

  boolean awaitChange(Duration timeout) throws InterruptedException {
    WatchKey key = watcher.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (key == null) return false;
    do {
      key.pollEvents();
      key.reset();
      key = watcher.poll();
    } while (key != null);
    return true;
  }

  @Override
  public void close() throws IOException {
    watcher.close();
  }
}
