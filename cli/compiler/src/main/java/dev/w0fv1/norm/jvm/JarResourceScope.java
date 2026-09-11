package dev.w0fv1.norm.jvm;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

final class JarResourceScope implements AutoCloseable {
  private static final Map<JarFile, Integer> owners = new IdentityHashMap<>();
  private final Set<JarFile> archives = Collections.newSetFromMap(new IdentityHashMap<>());
  private boolean closed;

  void retain(URL resource) throws IOException {
    synchronized (owners) {
      if (closed) throw new IOException("application resource scope is closed");
      var connection = (JarURLConnection) resource.openConnection();
      var archive = connection.getJarFile();
      if (archives.add(archive)) owners.merge(archive, 1, Integer::sum);
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (owners) {
      if (closed) return;
      closed = true;
      IOException failure = null;
      for (var archive : archives) {
        int remaining = owners.get(archive) - 1;
        if (remaining > 0) owners.put(archive, remaining);
        else {
          owners.remove(archive);
          try {
            archive.close();
          } catch (IOException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
          }
        }
      }
      archives.clear();
      if (failure != null) throw failure;
    }
  }
}
