package dev.w0fv1.norm.packaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RuntimePayloadArchive {
  private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 1, 1, 0, 0, 2);

  private RuntimePayloadArchive() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 3)
      throw new IllegalArgumentException(
          "Usage: RuntimePayloadArchive <runtime directory> <zip output> <digest output>");
    write(Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]));
  }

  public static void write(Path directory, Path archive, Path digestFile) throws IOException {
    Path root = directory.toAbsolutePath().normalize();
    Path destination = archive.toAbsolutePath().normalize();
    Path digestDestination = digestFile.toAbsolutePath().normalize();
    if (!Files.isDirectory(root))
      throw new IOException("Runtime directory is unavailable: " + root);
    if (destination.startsWith(root) || digestDestination.startsWith(root))
      throw new IOException("Runtime payload outputs must be outside the runtime directory");

    List<Path> files;
    try (var contents = Files.walk(root)) {
      files =
          contents
              .filter(Files::isRegularFile)
              .sorted(
                  Comparator.comparing(file -> root.relativize(file).toString().replace('\\', '/')))
              .toList();
    }
    if (files.isEmpty()) throw new IOException("Runtime directory has no files: " + root);

    Files.createDirectories(destination.getParent());
    Files.createDirectories(digestDestination.getParent());
    Path stagedArchive = Files.createTempFile(destination.getParent(), "norm-runtime-", ".zip");
    Path stagedDigest =
        Files.createTempFile(digestDestination.getParent(), "norm-runtime-", ".sha256");
    try {
      try (var output = new ZipOutputStream(Files.newOutputStream(stagedArchive))) {
        for (Path file : files) {
          ZipEntry entry = new ZipEntry(root.relativize(file).toString().replace('\\', '/'));
          entry.setTimeLocal(ENTRY_TIME);
          output.putNextEntry(entry);
          Files.copy(file, output);
          output.closeEntry();
        }
      }
      String digest;
      try {
        MessageDigest hasher = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(stagedArchive), hasher)) {
          input.transferTo(OutputStream.nullOutputStream());
        }
        digest = HexFormat.of().formatHex(hasher.digest());
      } catch (NoSuchAlgorithmException error) {
        throw new IllegalStateException(error);
      }
      Files.writeString(stagedDigest, digest, StandardCharsets.US_ASCII);
      Files.move(
          stagedArchive,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      Files.move(
          stagedDigest,
          digestDestination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(stagedArchive);
      Files.deleteIfExists(stagedDigest);
    }
  }
}
