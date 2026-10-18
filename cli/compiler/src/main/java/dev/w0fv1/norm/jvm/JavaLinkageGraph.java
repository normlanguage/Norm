package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.value.DirectedGraphComponents;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

record JavaLinkageGraph(
    List<Node> nodes,
    Map<String, Integer> owners,
    List<List<Integer>> components,
    List<Set<Integer>> edges) {
  record Node(
      Path path, Map<String, Type> classes, String content, List<ModuleReference> modules) {}

  record Member(String owner, String name, String descriptor, boolean method) {}

  record Type(
      String name,
      String family,
      int access,
      Set<String> references,
      Set<Member> memberReferences,
      Map<Member, Integer> members,
      List<String> parents,
      String content) {}

  static JavaLinkageGraph scan(List<Path> paths, List<String> moduleRoots) throws IOException {
    var selected = JavaModulePath.select(paths, moduleRoots);
    var modulePaths = new LinkedHashMap<Path, List<ModuleReference>>();
    for (Path path : selected.paths())
      modulePaths.put(
          path.toAbsolutePath().normalize(),
          java.lang.module.ModuleFinder.of(path).findAll().stream()
              .sorted(java.util.Comparator.comparing(reference -> reference.descriptor().name()))
              .toList());
    var nodes = new ArrayList<Node>();
    var owners = new LinkedHashMap<String, Integer>();
    for (Path entry : paths) {
      Path path = entry.toAbsolutePath().normalize();
      var classes = new TreeMap<String, Type>();
      var resources = new TreeMap<String, String>();
      if (Files.isDirectory(path)) {
        try (var files = Files.walk(path)) {
          for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
            byte[] bytes = Files.readAllBytes(file);
            String name = path.relativize(file).toString().replace('\\', '/');
            if (name.endsWith(".class") && !name.equals("module-info.class")) {
              Type type = read(bytes);
              classes.put(type.name(), type);
            } else resources.put(name, Sha256Digest.compute(bytes).value());
          }
        }
      } else {
        try (var jar =
            new JarFile(path.toFile(), false, java.util.zip.ZipFile.OPEN_READ, Runtime.version())) {
          for (var file : jar.versionedStream().filter(value -> !value.isDirectory()).toList()) {
            if (file.getName().endsWith(".class") && !file.getName().equals("module-info.class")) {
              try (var input = jar.getInputStream(file)) {
                Type type = read(input.readAllBytes());
                classes.put(type.name(), type);
              }
            }
          }
        }
      }
      List<ModuleReference> modules = modulePaths.getOrDefault(path, List.of());
      if (!Files.isDirectory(path) || !modules.isEmpty()) {
        String content =
            Files.isDirectory(path)
                ? content(classes, resources)
                : Sha256Digest.compute(path).value();
        int identity = nodes.size();
        classes.entrySet().removeIf(value -> owners.containsKey(value.getKey()));
        classes.keySet().forEach(name -> owners.put(name, identity));
        nodes.add(new Node(path, Map.copyOf(classes), content, modules));
      } else {
        var families = new TreeMap<String, Map<String, Type>>();
        for (Type type : classes.values())
          if (!owners.containsKey(type.name()))
            families
                .computeIfAbsent(type.family(), ignored -> new TreeMap<>())
                .put(type.name(), type);
        for (var family : families.values()) {
          int identity = nodes.size();
          family.keySet().forEach(name -> owners.put(name, identity));
          nodes.add(new Node(path, Map.copyOf(family), content(family, Map.of()), List.of()));
        }
        if (!resources.isEmpty())
          nodes.add(new Node(path, Map.of(), content(Map.of(), resources), List.of()));
      }
    }
    var edges = new ArrayList<Set<Integer>>();
    nodes.forEach(node -> edges.add(new LinkedHashSet<>()));
    var moduleOwners = new LinkedHashMap<String, Integer>();
    for (int index = 0; index < nodes.size(); index++) {
      int owner = index;
      nodes
          .get(index)
          .modules()
          .forEach(module -> moduleOwners.put(module.descriptor().name(), owner));
    }
    for (int index = 0; index < nodes.size(); index++) {
      Node node = nodes.get(index);
      for (Type type : node.classes().values()) {
        for (String reference : type.references()) {
          Integer target = owners.get(reference);
          if (target == null || target == index) continue;
          edges.get(index).add(target);
          Type targetType = nodes.get(target).classes().get(reference);
          if (samePackage(type.name(), reference)
              && (targetType.access() & Opcodes.ACC_PUBLIC) == 0) edges.get(target).add(index);
        }
        for (Member member : type.memberReferences()) {
          var queue = new java.util.ArrayDeque<String>();
          var visited = new LinkedHashSet<String>();
          queue.add(member.owner());
          while (!queue.isEmpty()) {
            String owner = queue.removeFirst();
            Integer target = owners.get(owner);
            if (target == null || !visited.add(owner)) continue;
            Type targetType = nodes.get(target).classes().get(owner);
            Integer access =
                targetType
                    .members()
                    .get(new Member(owner, member.name(), member.descriptor(), member.method()));
            if (access == null) queue.addAll(targetType.parents());
            else {
              if (target != index
                  && samePackage(type.name(), owner)
                  && (access & Opcodes.ACC_PUBLIC) == 0) {
                edges.get(index).add(target);
                edges.get(target).add(index);
              }
              break;
            }
          }
        }
      }
      for (var module : node.modules())
        for (var required : module.descriptor().requires()) {
          Integer target = moduleOwners.get(required.name());
          if (target != null && target != index) edges.get(index).add(target);
        }
    }
    var dependencies =
        edges.stream()
            .map(targets -> targets.stream().mapToInt(Integer::intValue).sorted().toArray())
            .toList();
    return new JavaLinkageGraph(
        List.copyOf(nodes),
        Map.copyOf(owners),
        DirectedGraphComponents.compute(dependencies, ignored -> true, () -> {}),
        edges.stream().map(Set::copyOf).toList());
  }

  private static String content(Map<String, Type> classes, Map<String, String> resources) {
    var writer = new CanonicalWriter().writeTag("java-linkage-source-1").writeInt(classes.size());
    new TreeMap<>(classes)
        .forEach((name, type) -> writer.writeString(name).writeString(type.content()));
    writer.writeInt(resources.size());
    new TreeMap<>(resources)
        .forEach((name, digest) -> writer.writeString(name).writeString(digest));
    return Sha256Digest.compute(writer.toByteArray()).value();
  }

  private static boolean samePackage(String left, String right) {
    return left.substring(0, Math.max(0, left.lastIndexOf('.')))
        .equals(right.substring(0, Math.max(0, right.lastIndexOf('.'))));
  }

  private static Type read(byte[] bytes) {
    var references = new LinkedHashSet<String>();
    var memberReferences = new LinkedHashSet<Member>();
    var node = new ClassNode(Opcodes.ASM9);
    new ClassReader(bytes)
        .accept(
            new ClassRemapper(
                node,
                new Remapper(Opcodes.ASM9) {
                  @Override
                  public String map(String name) {
                    references.add(name.replace('/', '.'));
                    return name;
                  }

                  @Override
                  public String mapMethodName(String owner, String name, String descriptor) {
                    memberReferences.add(
                        new Member(owner.replace('/', '.'), name, descriptor, true));
                    return name;
                  }

                  @Override
                  public String mapFieldName(String owner, String name, String descriptor) {
                    memberReferences.add(
                        new Member(owner.replace('/', '.'), name, descriptor, false));
                    return name;
                  }
                }),
            0);
    String name = node.name.replace('/', '.');
    String family =
        node.nestHostClass == null ? name.split("\\$", 2)[0] : node.nestHostClass.replace('/', '.');
    var members = new LinkedHashMap<Member, Integer>();
    node.methods.forEach(
        method -> members.put(new Member(name, method.name, method.desc, true), method.access));
    node.fields.forEach(
        field -> members.put(new Member(name, field.name, field.desc, false), field.access));
    var parents = new ArrayList<String>();
    if (node.superName != null) parents.add(node.superName.replace('/', '.'));
    node.interfaces.forEach(parent -> parents.add(parent.replace('/', '.')));
    return new Type(
        name,
        family,
        node.access,
        Set.copyOf(references),
        Set.copyOf(memberReferences),
        Map.copyOf(members),
        List.copyOf(parents),
        Sha256Digest.compute(bytes).value());
  }
}
