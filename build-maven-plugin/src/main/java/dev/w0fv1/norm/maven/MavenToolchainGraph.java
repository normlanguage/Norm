package dev.w0fv1.norm.maven;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;

final class MavenToolchainGraph {
  record Graph(
      List<String> roots,
      Map<String, List<String>> dependencies,
      Map<String, List<String>> purposes,
      Map<String, Artifact> artifacts) {}

  private MavenToolchainGraph() {}

  static Graph from(
      DependencyNode root, List<String> executionModules, List<String> hostedModules) {
    var dependencies = new TreeMap<String, Set<String>>();
    var artifacts = new HashMap<String, Artifact>();
    var pending = new ArrayDeque<DependencyNode>();
    pending.add(root);
    while (!pending.isEmpty()) {
      var node = pending.removeFirst();
      var artifact = node.getArtifact();
      String coordinate = coordinate(artifact);
      if (node != root) artifacts.putIfAbsent(coordinate, artifact);
      var edges = dependencies.computeIfAbsent(coordinate, ignored -> new TreeSet<>());
      for (var child : node.getChildren()) {
        edges.add(coordinate(child.getArtifact()));
        pending.add(child);
      }
    }
    String rootCoordinate = coordinate(root.getArtifact());
    List<String> roots = List.copyOf(dependencies.remove(rootCoordinate));
    Map<String, List<String>> graph = new TreeMap<>();
    dependencies.forEach((coordinate, edges) -> graph.put(coordinate, List.copyOf(edges)));
    Set<String> execution = new HashSet<>(executionModules);
    Set<String> hosted = new HashSet<>(hostedModules);
    if (!java.util.Collections.disjoint(execution, hosted))
      throw new IllegalArgumentException("Toolchain purpose modules overlap");
    Map<String, List<String>> purposes = new TreeMap<>();
    for (String purpose : List.of("execution", "hosted", "tooling"))
      purposes.put(purpose, new ArrayList<>());
    for (String coordinate : roots) {
      String module = coordinate.substring(0, coordinate.lastIndexOf(':'));
      String purpose =
          execution.contains(module) ? "execution" : hosted.contains(module) ? "hosted" : "tooling";
      purposes.get(purpose).add(coordinate);
    }
    return new Graph(roots, graph, purposes, artifacts);
  }

  private static String coordinate(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
  }
}
