package dev.w0fv1.norm.build;

import dev.w0fv1.norm.application.TemporaryDirectory;
import dev.w0fv1.norm.platform.jdk.FilePublication;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Objects;

final class WindowsApplicationExecutable {
  public static final byte[] MAGIC =
      "NORMAPP1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  Path write(Path launcher, Path bundle, Path destination) throws IOException {
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
    try (var workspace = new TemporaryDirectory()) {
      Path temporary = workspace.path().resolve("application.part");
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
      FilePublication.publish(temporary, output);
      return output;
    }
  }

  private static Path normalize(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }
}
