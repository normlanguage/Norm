package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.execution.RevisionResources;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class JavaRealmPool implements AutoCloseable {
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  private final ReferenceQueue<Component> retiredComponents = new ReferenceQueue<>();

  private boolean closed;

  private record Component(
      String identity,
      ComponentLoader loader,
      RevisionResources.Source resources,
      Set<String> classes,
      ModuleLayer layer) {}

  private static final class Entry extends WeakReference<Component> {
    private final String identity;

    private Entry(Component component, ReferenceQueue<Component> queue) {
      super(component, queue);
      identity = component.identity();
    }
  }

  synchronized ViewLoader open(
      List<Path> paths, List<String> moduleRoots, RevisionResources.Lease assets)
      throws IOException {
    if (closed) throw new IllegalStateException("Java realm is closed");
    JavaLinkageGraph graph = JavaLinkageGraph.scan(paths, moduleRoots);
    var components = new LinkedHashMap<Integer, Component>();
    var ownership = new ArrayList<RevisionResources.Lease>();
    var selected = new ArrayList<Component>();
    try {
      for (List<Integer> members : graph.components()) {
        var memberSet = Set.copyOf(members);
        var dependencies = new LinkedHashMap<String, Component>();
        for (int member : members)
          for (int target : graph.edges().get(member)) {
            if (!memberSet.contains(target)) {
              Component dependency = components.get(target);
              if (dependency == null)
                throw new IllegalStateException("Java component dependency is unresolved");
              dependencies.put(dependency.identity(), dependency);
            }
          }
        var classes = new TreeMap<String, JavaLinkageGraph.Type>();
        var sources = new LinkedHashSet<Path>();
        var modules = new ArrayList<java.lang.module.ModuleReference>();
        var writer =
            new CanonicalWriter().writeTag("java-linkage-component-1").writeInt(members.size());
        for (int member :
            members.stream()
                .sorted(java.util.Comparator.comparing(index -> graph.nodes().get(index).content()))
                .toList()) {
          var node = graph.nodes().get(member);
          writer.writeString(node.content());
          sources.add(node.path());
          classes.putAll(node.classes());
          modules.addAll(node.modules());
        }
        writer.writeInt(classes.size());
        classes.keySet().forEach(writer::writeString);
        writer.writeInt(dependencies.size());
        dependencies.keySet().stream().sorted().forEach(writer::writeString);
        String identity = Sha256Digest.compute(writer.toByteArray()).value();
        Component component;
        Entry retired;
        while ((retired = (Entry) retiredComponents.poll()) != null)
          entries.remove(retired.identity, retired);
        Entry existing = entries.get(identity);
        component = existing == null ? null : existing.get();
        var retained =
            component == null
                ? java.util.Optional.<RevisionResources.Lease>empty()
                : component.resources().acquire();
        if (retained.isPresent()) ownership.add(retained.orElseThrow());
        else {
          var linkedClasses = new LinkedHashMap<String, ComponentLoader>();
          dependencies
              .values()
              .forEach(
                  dependency ->
                      dependency
                          .classes()
                          .forEach(name -> linkedClasses.put(name, dependency.loader())));
          var loader =
              new ComponentLoader(
                  urls(paths.stream().filter(sources::contains).toList()),
                  Set.copyOf(classes.keySet()),
                  Map.copyOf(linkedClasses));
          ModuleLayer layer;
          try {
            var parents = new LinkedHashSet<ModuleLayer>();
            parents.add(ModuleLayer.boot());
            dependencies.values().stream()
                .map(Component::layer)
                .filter(value -> value != null)
                .forEach(parents::add);
            if (modules.isEmpty()) layer = null;
            else {
              Path[] modulePaths =
                  modules.stream()
                      .map(module -> Path.of(module.location().orElseThrow()))
                      .toArray(Path[]::new);
              var finder = java.lang.module.ModuleFinder.of(modulePaths);
              var configuration =
                  java.lang.module.Configuration.resolve(
                      finder,
                      parents.stream().map(ModuleLayer::configuration).toList(),
                      java.lang.module.ModuleFinder.of(),
                      modules.stream().map(module -> module.descriptor().name()).toList());
              modules.forEach(module -> loader.modules.put(module.descriptor().name(), module));
              layer =
                  ModuleLayer.defineModules(configuration, List.copyOf(parents), ignored -> loader)
                      .layer();
            }
          } catch (RuntimeException | Error exception) {
            loader.close();
            throw exception;
          }
          var resources =
              RevisionResources.own(
                  identity,
                  new ComponentResources(
                      new WeakReference<>(loader),
                      assets.retain(),
                      dependencies.values().stream()
                          .map(dependency -> dependency.resources().acquire().orElseThrow())
                          .toList()));
          ownership.add(resources);
          component =
              new Component(
                  identity, loader, resources.source(), Set.copyOf(classes.keySet()), layer);
          loader.component = component;
          entries.put(identity, new Entry(component, retiredComponents));
        }
        selected.add(component);
        for (int member : members) components.put(member, component);
      }
      var owners = new LinkedHashMap<String, ComponentLoader>();
      graph.owners().forEach((name, node) -> owners.put(name, components.get(node).loader()));
      return new ViewLoader(
          urls(paths), Map.copyOf(owners), List.copyOf(selected), List.copyOf(ownership));
    } catch (IOException | RuntimeException | Error exception) {
      for (var resource : ownership.reversed()) resource.close();
      throw exception;
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    entries.clear();
  }

  private static URL[] urls(java.util.Collection<Path> paths) throws IOException {
    var urls = new ArrayList<URL>();
    for (Path path : paths) urls.add(path.toUri().toURL());
    return urls.toArray(URL[]::new);
  }

  private record ComponentResources(
      WeakReference<ComponentLoader> loader,
      RevisionResources.Lease assets,
      List<RevisionResources.Lease> dependencies)
      implements AutoCloseable {
    @Override
    public void close() throws IOException {
      try {
        ComponentLoader retained = loader.get();
        if (retained != null) retained.close();
      } finally {
        try {
          for (var dependency : dependencies.reversed()) dependency.close();
        } finally {
          assets.close();
        }
      }
    }
  }

  static final class ViewLoader extends JavaRealmClassLoader {
    private final Map<String, ComponentLoader> owners;
    private final List<Component> components;
    private final List<RevisionResources.Lease> ownership;

    private ViewLoader(
        URL[] paths,
        Map<String, ComponentLoader> owners,
        List<Component> components,
        List<RevisionResources.Lease> ownership) {
      super(paths, JvmJarBindingRuntime.class.getClassLoader());
      this.owners = new LinkedHashMap<>(owners);
      this.components = new ArrayList<>(components);
      this.ownership = new ArrayList<>(ownership);
    }

    List<ClassLoader> loaders(Set<String> applicationTypes) {
      var loaders = new LinkedHashSet<ClassLoader>();
      loaders.add(this);
      applicationTypes.stream()
          .sorted()
          .map(owners::get)
          .filter(value -> value != null)
          .forEach(loaders::add);
      return List.copyOf(loaders);
    }

    void retain(Object value) {
      if (value == null) return;
      ClassLoader loader =
          value instanceof Class<?> type
              ? type.getClassLoader()
              : value.getClass().getClassLoader();
      if (loader instanceof ComponentLoader component) {
        try (var lease =
            component
                .component
                .resources()
                .acquire()
                .orElseThrow(
                    () ->
                        new dev.w0fv1.norm.execution.JarBindingRuntimeException(
                            dev.w0fv1.norm.execution.RuntimeErrorCode.JAVA_MIGRATION_REQUIRED,
                            "MIGRATION_REQUIRED: Java value belongs to a retired linkage component"))) {
          lease.retain(value);
        }
      }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (parentFirst(name)) return super.loadClass(name, resolve);
      ComponentLoader owner = owners.get(name);
      if (owner == null) throw new ClassNotFoundException(name);
      return owner.loadClass(name);
    }

    @Override
    public void close() throws IOException {
      try {
        super.close();
      } finally {
        try {
          for (var resource : ownership.reversed()) resource.close();
        } finally {
          ownership.clear();
          components.clear();
          owners.clear();
        }
      }
    }
  }

  private static final class ComponentLoader extends JavaRealmClassLoader {
    private Component component;
    private final Set<String> classes;
    private final Map<String, ComponentLoader> dependencies;

    private ComponentLoader(
        URL[] paths, Set<String> classes, Map<String, ComponentLoader> dependencies) {
      super(paths, JvmJarBindingRuntime.class.getClassLoader());
      this.classes = classes;
      this.dependencies = dependencies;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (parentFirst(name) || classes.contains(name)) return super.loadClass(name, resolve);
      ComponentLoader dependency = dependencies.get(name);
      if (dependency != null) return dependency.loadClass(name);
      ClassLoader context = Thread.currentThread().getContextClassLoader();
      if (context instanceof ViewLoader view && view.owners.containsKey(name))
        return view.loadClass(name);
      throw new ClassNotFoundException(name);
    }

    @Override
    public URL getResource(String name) {
      ClassLoader context = Thread.currentThread().getContextClassLoader();
      if (context instanceof ViewLoader view) return view.getResource(name);
      URL resource = super.getResource(name);
      if (resource != null) return resource;
      for (var dependency : new LinkedHashSet<>(dependencies.values())) {
        resource = dependency.getResource(name);
        if (resource != null) return resource;
      }
      return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      ClassLoader context = Thread.currentThread().getContextClassLoader();
      if (context instanceof ViewLoader view) return view.getResources(name);
      var resources = new LinkedHashSet<>(Collections.list(super.getResources(name)));
      for (var dependency : new LinkedHashSet<>(dependencies.values()))
        resources.addAll(Collections.list(dependency.getResources(name)));
      return Collections.enumeration(resources);
    }
  }
}
