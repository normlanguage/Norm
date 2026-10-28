package dev.w0fv1.norm.pyrun;

import dev.w0fv1.norm.platform.jdk.EnvironmentProxySelector;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public final class PythonRuntimeInstaller {
  private static final String COMPLETE = ".complete";
  private final PythonRuntimeDistribution distribution;
  private final Path root;
  private final ArchiveSource archiveSource;

  public PythonRuntimeInstaller(PythonRuntimeDistribution distribution) {
    this(distribution, configuredRoot(), PythonRuntimeInstaller::downloadHttp);
  }

  PythonRuntimeInstaller(
      PythonRuntimeDistribution distribution, Path root, ArchiveSource archiveSource) {
    this.distribution = distribution;
    this.root = root.toAbsolutePath().normalize();
    this.archiveSource = archiveSource;
  }

  public Path install(Consumer<String> progress) throws IOException {
    Path destination = root.resolve(distribution.version()).resolve(distribution.platform().key());
    Path installed = installed(destination);
    if (installed != null) return installed;
    Files.createDirectories(destination.getParent());
    Path lockPath = destination.resolveSibling(destination.getFileName() + ".lock");
    try (FileChannel channel =
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock()) {
      if (!lock.isValid()) throw new IOException("cannot lock the Python runtime");
      installed = installed(destination);
      if (installed != null) return installed;
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) deleteTree(destination);
      return downloadAndInstall(destination, progress);
    }
  }

  private Path downloadAndInstall(Path destination, Consumer<String> progress) throws IOException {
    Path archive = Files.createTempFile(destination.getParent(), "python-", ".download");
    Path staging = Files.createTempDirectory(destination.getParent(), "python-installing-");
    try {
      progress.accept("Downloading Python " + distribution.version());
      archiveSource.download(distribution.archive(), archive, progress);
      Sha256Digest actual = Sha256Digest.compute(archive);
      if (!distribution.integrity().equals(actual)) {
        throw new IOException(
            "Python download integrity mismatch: expected "
                + distribution.integrity()
                + ", actual "
                + actual);
      }
      extract(archive, staging);
      Path executable = staging.resolve(distribution.executable()).normalize();
      if (!executable.startsWith(staging) || !Files.isRegularFile(executable)) {
        throw new IOException(
            "Python runtime archive has no expected executable: " + distribution.executable());
      }
      permissions(executable, 0755);
      Files.writeString(
          staging.resolve(COMPLETE),
          distribution.identity()
              + System.lineSeparator()
              + distribution.executable()
              + System.lineSeparator());
      move(staging, destination);
      progress.accept("Python is ready at " + destination.resolve(distribution.executable()));
      return destination.resolve(distribution.executable());
    } finally {
      Files.deleteIfExists(archive);
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) deleteTree(staging);
    }
  }

  private void extract(Path archive, Path destination) throws IOException {
    if (archive.getFileName().toString().endsWith(".zip")) extractZip(archive, destination);
    else extractTarGzip(archive, destination);
  }

  private static void extractZip(Path archive, Path destination) throws IOException {
    try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
      for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
        Path target = safeTarget(destination, entry.getName());
        if (entry.isDirectory()) Files.createDirectories(target);
        else {
          Files.createDirectories(target.getParent());
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        input.closeEntry();
      }
    }
  }

  private static void extractTarGzip(Path archive, Path destination) throws IOException {
    try (InputStream file = Files.newInputStream(archive);
        GzipCompressorInputStream gzip = new GzipCompressorInputStream(file);
        TarArchiveInputStream input = new TarArchiveInputStream(gzip)) {
      for (TarArchiveEntry entry; (entry = input.getNextEntry()) != null; ) {
        Path target = safeTarget(destination, entry.getName());
        if (entry.isDirectory()) Files.createDirectories(target);
        else if (entry.isSymbolicLink()) {
          Path linkTarget = safeLinkTarget(target, entry.getLinkName(), destination);
          Files.createDirectories(target.getParent());
          Files.createSymbolicLink(target, target.getParent().relativize(linkTarget));
        } else if (entry.isLink()) {
          Path linkTarget = safeTarget(destination, entry.getLinkName());
          Files.createDirectories(target.getParent());
          Files.createLink(target, linkTarget);
        } else if (entry.isFile()) {
          Files.createDirectories(target.getParent());
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
          permissions(target, entry.getMode());
        }
      }
    }
  }

  private static Path safeTarget(Path destination, String name) throws IOException {
    Path target = destination.resolve(name.replace('\\', '/')).normalize();
    if (!target.startsWith(destination))
      throw new IOException("invalid Python archive entry: " + name);
    return target;
  }

  private static Path safeLinkTarget(Path link, String name, Path destination) throws IOException {
    Path target = link.getParent().resolve(name.replace('\\', '/')).normalize();
    if (!target.startsWith(destination))
      throw new IOException("invalid Python archive link: " + name);
    return target;
  }

  private static void permissions(Path target, int mode) throws IOException {
    if (Files.getFileAttributeView(target, java.nio.file.attribute.PosixFileAttributeView.class)
        == null) return;
    Set<PosixFilePermission> values = EnumSet.noneOf(PosixFilePermission.class);
    int[] bits = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
    PosixFilePermission[] permissions = PosixFilePermission.values();
    for (int index = 0; index < bits.length; index++)
      if ((mode & bits[index]) != 0) values.add(permissions[index]);
    Files.setPosixFilePermissions(target, values);
  }

  private Path installed(Path destination) throws IOException {
    Path complete = destination.resolve(COMPLETE);
    if (!Files.isRegularFile(complete)) return null;
    var values = Files.readAllLines(complete);
    if (values.size() != 2 || !values.getFirst().equals(distribution.identity())) return null;
    Path executable = destination.resolve(values.get(1)).normalize();
    return executable.startsWith(destination) && Files.isRegularFile(executable)
        ? executable
        : null;
  }

  private static void move(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, destination);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }

  private static Path configuredRoot() {
    String configured = System.getProperty("norm.pyrun.home");
    if (configured == null || configured.isBlank()) configured = System.getenv("NORM_PYRUN_HOME");
    return configured == null || configured.isBlank()
        ? Path.of(System.getProperty("user.home"), ".norm", "pyrun", "runtimes")
        : Path.of(configured);
  }

  static void downloadHttp(URI source, Path target, Consumer<String> progress) throws IOException {
    try (HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(EnvironmentProxySelector.system())
            .build()) {
      HttpResponse<InputStream> response;
      try {
        response =
            client.send(
                HttpRequest.newBuilder(source).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Python download was interrupted", exception);
      }
      if (response.statusCode() != 200) {
        response.body().close();
        throw new IOException("cannot download Python: HTTP " + response.statusCode());
      }
      try (InputStream input = response.body();
          var output = Files.newOutputStream(target)) {
        input.transferTo(output);
      }
    }
  }

  @FunctionalInterface
  interface ArchiveSource {
    void download(URI source, Path destination, Consumer<String> progress) throws IOException;
  }
}
