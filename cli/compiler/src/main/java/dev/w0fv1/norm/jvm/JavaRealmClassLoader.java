package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JavaRealmClassLoader extends URLClassLoader {
  static {
    registerAsParallelCapable();
  }

  private final Object localDefinitionLock = new Object();
  private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
  private final java.lang.ref.Cleaner.Cleanable archiveCleanup;
  private final JarResourceScope resourceArchives = new JarResourceScope();
  private boolean resourcesClosed;
  private final Set<java.io.InputStream> resourceStreams =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  final Map<String, java.lang.module.ModuleReference> modules = new LinkedHashMap<>();
  private static final Set<String> PLATFORM_PACKAGES =
      java.lang.module.ModuleFinder.ofSystem().findAll().stream()
          .flatMap(reference -> reference.descriptor().packages().stream())
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
  private static final List<String> SHARED_PACKAGES =
      List.of("dev.w0fv1.norm.bridge.", "org.junit.", "org.opentest4j.", "org.apiguardian.");

  JavaRealmClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
    var archives = resourceArchives;
    archiveCleanup =
        CLEANER.register(
            this,
            () -> {
              try {
                archives.close();
              } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
              }
            });
  }

  @Override
  protected URL findResource(String moduleName, String name) throws IOException {
    if (moduleName == null) return findResource(name);
    var reference = modules.get(moduleName);
    if (reference == null) return null;
    try (var reader = reference.open()) {
      var resource = reader.find(name);
      if (resource.isEmpty()) return null;
      return ownedResource(resource.orElseThrow().toURL());
    }
  }

  @Override
  protected Class<?> findClass(String moduleName, String name) {
    try {
      Class<?> type = loadClass(name);
      return java.util.Objects.equals(moduleName, type.getModule().getName()) ? type : null;
    } catch (ClassNotFoundException absent) {
      return null;
    }
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    Class<?> type = parentFirst(name) ? getParent().loadClass(name) : loadLocalClass(name);
    if (resolve) resolveClass(type);
    return type;
  }

  final Class<?> loadLocalClass(String name) throws ClassNotFoundException {
    synchronized (localDefinitionLock) {
      Class<?> type = findLoadedClass(name);
      if (type != null) return type;
      try {
        return findClass(name);
      } catch (LinkageError failure) {
        type = findLoadedClass(name);
        if (type == null) throw failure;
        return type;
      }
    }
  }

  @Override
  public URL getResource(String name) {
    URL resource = findResource(name);
    if (resource != null) return resource;
    var parent = parentResourceLoader(name);
    return parent == null ? null : parent.getResource(name);
  }

  @Override
  public URL findResource(String name) {
    URL moduleResource = findModuleResource(name);
    if (moduleResource != null) return moduleResource;
    var resource = super.findResource(name);
    return resource == null ? null : ownedResource(resource);
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    var resources = new LinkedHashSet<URL>(findModuleResources(name));
    resources.addAll(
        Collections.list(super.findResources(name)).stream().map(this::ownedResource).toList());
    return Collections.enumeration(resources);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    LinkedHashSet<URL> resources = new LinkedHashSet<>();
    resources.addAll(Collections.list(findResources(name)));
    var parent = parentResourceLoader(name);
    if (parent != null) resources.addAll(Collections.list(parent.getResources(name)));
    return Collections.enumeration(resources);
  }

  private URL findModuleResource(String name) {
    for (var reference : modules.values()) {
      try (var reader = reference.open()) {
        var resource = reader.find(name);
        if (resource.isPresent()) return ownedResource(resource.orElseThrow().toURL());
      } catch (IOException failure) {
        throw new JarBindingRuntimeException("cannot read module resource " + name, failure);
      }
    }
    return null;
  }

  private List<URL> findModuleResources(String name) throws IOException {
    var resources = new LinkedHashSet<URL>();
    for (var reference : modules.values()) {
      try (var reader = reference.open()) {
        var resource = reader.find(name);
        if (resource.isPresent()) resources.add(ownedResource(resource.orElseThrow().toURL()));
      }
    }
    return List.copyOf(resources);
  }

  private ClassLoader parentResourceLoader(String name) {
    boolean service = name.startsWith("META-INF/services/");
    String type = service ? name.substring("META-INF/services/".length()) : name.replace('/', '.');
    if (sharedParent(type)) return getParent();
    int separator = service ? type.lastIndexOf('.') : name.lastIndexOf('/');
    String packageName = separator < 0 ? "" : type.substring(0, separator);
    return PLATFORM_PACKAGES.contains(packageName) ? ClassLoader.getPlatformClassLoader() : null;
  }

  private URL ownedResource(URL resource) {
    if (!resource.getProtocol().equals("jar")) return resource;
    try {
      resourceArchives.retain(resource);
      return URL.of(
          java.net.URI.create(resource.toExternalForm()),
          new java.net.URLStreamHandler() {
            @Override
            protected java.net.URLConnection openConnection(URL url) throws IOException {
              resourceArchives.retain(resource);
              var connection = java.net.URI.create(url.toExternalForm()).toURL().openConnection();
              return new java.net.URLConnection(url) {
                @Override
                public void connect() throws IOException {
                  connection.connect();
                  connected = true;
                }

                @Override
                public java.io.InputStream getInputStream() throws IOException {
                  synchronized (resourceStreams) {
                    if (resourcesClosed)
                      throw new IOException("application resource scope is closed");
                    var stream =
                        new java.io.FilterInputStream(connection.getInputStream()) {
                          @Override
                          public void close() throws IOException {
                            try {
                              super.close();
                            } finally {
                              synchronized (resourceStreams) {
                                resourceStreams.remove(this);
                              }
                            }
                          }
                        };
                    resourceStreams.add(stream);
                    return stream;
                  }
                }

                @Override
                public int getContentLength() {
                  return connection.getContentLength();
                }

                @Override
                public long getContentLengthLong() {
                  return connection.getContentLengthLong();
                }

                @Override
                public String getHeaderField(String name) {
                  return connection.getHeaderField(name);
                }
              };
            }
          });
    } catch (IOException failure) {
      throw new JarBindingRuntimeException("cannot own application resource " + resource, failure);
    }
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    synchronized (resourceStreams) {
      resourcesClosed = true;
      for (var stream : List.copyOf(resourceStreams)) {
        try {
          stream.close();
        } catch (IOException exception) {
          if (failure == null) failure = exception;
          else failure.addSuppressed(exception);
        }
      }
    }
    try {
      super.close();
    } catch (IOException exception) {
      if (failure == null) failure = exception;
      else failure.addSuppressed(exception);
    }
    try {
      resourceArchives.close();
      archiveCleanup.clean();
    } catch (IOException exception) {
      if (failure == null) failure = exception;
      else failure.addSuppressed(exception);
    }
    if (failure != null) throw failure;
  }

  static boolean parentFirst(String name) {
    int separator = name.lastIndexOf('.');
    return sharedParent(name)
        || (separator >= 0 && PLATFORM_PACKAGES.contains(name.substring(0, separator)));
  }

  private static boolean sharedParent(String name) {
    return SHARED_PACKAGES.stream().anyMatch(name::startsWith);
  }
}
