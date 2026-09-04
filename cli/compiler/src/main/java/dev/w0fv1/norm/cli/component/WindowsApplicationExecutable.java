package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Objects;

public final class WindowsApplicationExecutable {
  public static final byte[] MAGIC =
      "NORMAPP1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  public Path write(Path launcher, Path bundle, Path destination) throws IOException {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
      throw new IOException("Windows application executables can only be built on Windows");
    }
    Path template = normalize(launcher);
    Path payload = normalize(bundle);
    Path output = normalize(destination);
    if (!Files.isRegularFile(template)) throw new IOException("Norm launcher is unavailable");
    if (!Files.isRegularFile(payload)) throw new IOException("application bundle is unavailable");
    Path parent = output.getParent();
    if (parent == null) throw new IOException("application executable has no parent directory");
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, output.getFileName().toString(), ".part");
    try {
      Files.copy(template, temporary, StandardCopyOption.REPLACE_EXISTING);
      long length = Files.size(payload);
      byte[] digest = HexFormat.of().parseHex(Sha256Digest.compute(payload).value());
      try (OutputStream stream =
          Files.newOutputStream(temporary, java.nio.file.StandardOpenOption.APPEND)) {
        Files.copy(payload, stream);
        stream.write(ByteBuffer.allocate(Long.BYTES).putLong(length).array());
        stream.write(digest);
        stream.write(MAGIC);
      }
      move(temporary, output);
      return output;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void move(Path source, Path destination) throws IOException {
    try {
      Files.move(
          source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path normalize(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }
}
