package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.jvm.EnvironmentProxySelector;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

final class NativeImageToolchainInstaller {
  private static final String COMPLETE = ".complete";
  private final NativeImageDistribution distribution;
  private final Path root;
  private final ArchiveSource archiveSource;

  NativeImageToolchainInstaller() {
    this(
        NativeImageDistribution.current(),
        configuredRoot(),
        NativeImageToolchainInstaller::downloadHttp);
  }

  NativeImageToolchainInstaller(
      NativeImageDistribution distribution, Path root, ArchiveSource archiveSource) {
    this.distribution = distribution;
    this.root = root.toAbsolutePath().normalize();
    this.archiveSource = archiveSource;
  }

  NativeImageToolchain install(Consumer<String> output) throws IOException {
    Path destination = root.resolve(distribution.version()).resolve(distribution.platform());
    NativeImageToolchain installed = installed(destination);
    if (installed != null) return installed;
    Files.createDirectories(destination.getParent());
    Path lockPath = destination.resolveSibling(destination.getFileName() + ".lock");
    try (FileChannel channel =
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock()) {
      if (!lock.isValid()) throw new IOException("cannot lock the Native Image toolchain");
      installed = installed(destination);
      if (installed != null) return installed;
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) deleteTree(destination);
      return downloadAndInstall(destination, output);
    }
  }

  private NativeImageToolchain downloadAndInstall(Path destination, Consumer<String> output)
      throws IOException {
    Path archive =
        Files.createTempFile(destination.getParent(), distribution.platform() + "-", ".download");
    Path staging =
        Files.createTempDirectory(
            destination.getParent(), distribution.platform() + "-installing-");
    try {
      output.accept(
          "Installing Native Image " + distribution.version() + " for " + distribution.platform());
      archiveSource.download(distribution.archive(), archive, output);
      Sha256Digest actual = Sha256Digest.compute(archive);
      if (!distribution.integrity().equals(actual)) {
        throw new IOException(
            "Native Image download integrity mismatch: expected "
                + distribution.integrity()
                + ", actual "
                + actual);
      }
      extract(archive, staging);
      Path executable = findExecutable(staging);
      Path relativeExecutable = staging.relativize(executable);
      Files.writeString(
          staging.resolve(COMPLETE),
          distribution.identity()
              + System.lineSeparator()
              + relativeExecutable
              + System.lineSeparator());
      move(staging, destination);
      output.accept("Native Image is ready at " + destination.resolve(relativeExecutable));
      return new NativeImageToolchain(destination.resolve(relativeExecutable));
    } finally {
      Files.deleteIfExists(archive);
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) deleteTree(staging);
    }
  }

  private static void downloadHttp(URI archive, Path target, Consumer<String> output)
      throws IOException {
    try (HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(EnvironmentProxySelector.system())
            .build()) {
      HttpResponse<InputStream> response;
      try {
        response =
            client.send(
                HttpRequest.newBuilder(archive).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Native Image download was interrupted", exception);
      }
      if (response.statusCode() != 200) {
        response.body().close();
        throw new IOException("cannot download Native Image: HTTP " + response.statusCode());
      }
      long length = response.headers().firstValueAsLong("content-length").orElse(-1);
      try (InputStream input = response.body();
          var file = Files.newOutputStream(target)) {
        byte[] buffer = new byte[1024 * 1024];
        long downloaded = 0;
        int lastPercent = -1;
        for (int read; (read = input.read(buffer)) >= 0; ) {
          file.write(buffer, 0, read);
          downloaded += read;
          if (length > 0) {
            int percent = (int) Math.min(100, downloaded * 100 / length);
            if (percent / 10 > lastPercent / 10) {
              output.accept("Downloading Native Image: " + percent + "%");
              lastPercent = percent;
            }
          }
        }
      }
    }
  }

  private void extract(Path archive, Path destination) throws IOException {
    switch (distribution.format()) {
      case ZIP -> extractZip(archive, destination);
      case TAR_GZIP -> extractTarGzip(archive, destination);
    }
  }

  private static void extractZip(Path archive, Path destination) throws IOException {
    try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
      for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
        Path target = safeTarget(destination, entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        input.closeEntry();
      }
    }
  }

  private static void extractTarGzip(Path archive, Path destination) throws IOException {
    List<Link> links = new ArrayList<>();
    try (InputStream file = Files.newInputStream(archive);
        GzipCompressorInputStream gzip = new GzipCompressorInputStream(file);
        TarArchiveInputStream input = new TarArchiveInputStream(gzip)) {
      for (TarArchiveEntry entry; (entry = input.getNextEntry()) != null; ) {
        Path target = safeTarget(destination, entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else if (entry.isSymbolicLink() || entry.isLink()) {
          links.add(new Link(target, entry.getLinkName(), entry.isSymbolicLink()));
        } else if (entry.isFile()) {
          Files.createDirectories(target.getParent());
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
          permissions(target, entry.getMode());
        }
      }
    }
    for (Link link : links) link.create(destination);
  }

  private static Path safeTarget(Path destination, String name) throws IOException {
    Path target = destination.resolve(name.replace('\\', '/')).normalize();
    if (!target.startsWith(destination)) {
      throw new IOException("invalid Native Image archive entry: " + name);
    }
    return target;
  }

  private static void permissions(Path target, int mode) throws IOException {
    if (Files.getFileAttributeView(target, java.nio.file.attribute.PosixFileAttributeView.class)
        == null) return;
    Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
    PosixFilePermission[] values = PosixFilePermission.values();
    int[] bits = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
    for (int index = 0; index < bits.length; index++) {
      if ((mode & bits[index]) != 0) permissions.add(values[index]);
    }
    Files.setPosixFilePermissions(target, permissions);
  }

  private NativeImageToolchain installed(Path destination) throws IOException {
    Path complete = destination.resolve(COMPLETE);
    if (!Files.isRegularFile(complete)) return null;
    List<String> values = Files.readAllLines(complete);
    if (values.size() != 2 || !values.getFirst().equals(distribution.identity())) return null;
    Path executable = destination.resolve(values.get(1)).normalize();
    if (!executable.startsWith(destination) || !Files.isRegularFile(executable)) return null;
    return new NativeImageToolchain(executable);
  }

  private static Path findExecutable(Path root) throws IOException {
    String name = windows() ? "native-image.cmd" : "native-image";
    try (var paths =
        Files.find(
            root,
            8,
            (path, attributes) ->
                attributes.isRegularFile() && path.getFileName().toString().equals(name))) {
      List<Path> matches = paths.sorted().toList();
      if (matches.size() != 1) {
        throw new IOException("Native Image archive contains " + matches.size() + " executables");
      }
      return matches.getFirst();
    }
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
    String configured = System.getProperty("norm.toolchain.root");
    if (configured == null || configured.isBlank())
      configured = System.getenv("NORM_TOOLCHAIN_HOME");
    return configured == null || configured.isBlank()
        ? Path.of(System.getProperty("user.home"), ".norm", "toolchains", "native-image")
        : Path.of(configured);
  }

  private static boolean windows() {
    return System.getProperty("os.name", "").startsWith("Windows");
  }

  private record Link(Path target, String archiveTarget, boolean symbolic) {
    private void create(Path root) throws IOException {
      Files.createDirectories(target.getParent());
      if (symbolic) {
        Path linkTarget = Path.of(archiveTarget);
        Path resolved = target.getParent().resolve(linkTarget).normalize();
        if (!resolved.startsWith(root)) throw new IOException("unsafe Native Image symbolic link");
        Files.createSymbolicLink(target, linkTarget);
      } else {
        Path linkTarget = safeTarget(root, archiveTarget);
        Files.createLink(target, linkTarget);
      }
    }
  }

  @FunctionalInterface
  interface ArchiveSource {
    void download(URI source, Path destination, Consumer<String> output) throws IOException;
  }
}
