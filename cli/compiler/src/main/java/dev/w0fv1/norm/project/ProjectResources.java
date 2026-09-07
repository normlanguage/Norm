package dev.w0fv1.norm.project;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectResources {
  private final Map<ModuleCoordinate, Map<String, ModuleResource>> modules;
  private final Map<String, ModuleResource> classpath;

  public ProjectResources(Map<ModuleCoordinate, Map<String, ModuleResource>> modules)
      throws IOException {
    Map<ModuleCoordinate, Map<String, ModuleResource>> captured = new LinkedHashMap<>();
    Map<String, ModuleResource> resources = new LinkedHashMap<>();
    for (var entry : modules.entrySet()) {
      captured.put(entry.getKey(), Map.copyOf(entry.getValue()));
      for (var resource : entry.getValue().entrySet()) {
        if (!resource.getKey().equals(resource.getValue().path()))
          throw new IllegalArgumentException("resource key must match its path");
        if (resources.putIfAbsent(resource.getKey(), resource.getValue()) != null)
          throw new IOException("duplicate module resource " + resource.getKey());
      }
    }
    this.modules = Map.copyOf(captured);
    this.classpath = Map.copyOf(resources);
  }

  public Map<String, ModuleResource> forModule(ModuleCoordinate coordinate) {
    return modules.getOrDefault(coordinate, Map.of());
  }

  public Map<String, ModuleResource> classpath() {
    return classpath;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof ProjectResources resources && modules.equals(resources.modules);
  }

  @Override
  public int hashCode() {
    return modules.hashCode();
  }
}
