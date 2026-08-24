package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.DefinitionHasher;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public final class FileDefinitionStore implements DefinitionStore {
  private static final int DEFAULT_MAXIMUM_GROUPS = 50_000;
  private static final long DEFAULT_MAXIMUM_BYTES = 512L * 1024 * 1024;
  private static final int LOCK_STRIPE_COUNT = 256;
  private static final int MAINTENANCE_LOCK_STRIPE_COUNT = 64;
  private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
  private static final ReentrantLock[] PROCESS_LOCKS = new ReentrantLock[LOCK_STRIPE_COUNT];
  private static final ReentrantLock[] MAINTENANCE_PROCESS_LOCKS =
      new ReentrantLock[MAINTENANCE_LOCK_STRIPE_COUNT];

  static {
    Arrays.setAll(PROCESS_LOCKS, ignored -> new ReentrantLock());
    Arrays.setAll(MAINTENANCE_PROCESS_LOCKS, ignored -> new ReentrantLock());
  }

  private final Path root;
  private final StorePolicy policy;
  private final Map<Path, Long> accessHints = new HashMap<>();

  public FileDefinitionStore(Path root) throws IOException {
    this(root, DEFAULT_MAXIMUM_GROUPS, DEFAULT_MAXIMUM_BYTES);
  }

  public FileDefinitionStore(Path root, int maximumGroups, long maximumBytes) throws IOException {
    Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    this.policy = new StorePolicy(maximumGroups, maximumBytes);
    Files.createDirectories(normalizedRoot);
    this.root = normalizedRoot.toRealPath();
    withMaintenanceLock(
        () -> {
          requirePolicy();
          enforceCapacity(null);
          return null;
        });
  }

  @Override
  public PutResult put(byte[] canonicalGroup) throws IOException {
    Objects.requireNonNull(canonicalGroup, "canonicalGroup");
    byte[] ownedGroup = canonicalGroup.clone();
    DefinitionGroupId id = DefinitionHasher.hashGroup(ownedGroup);
    if (ownedGroup.length > policy.maximumBytes()) {
      return new PutResult(id, PutResult.Status.NOT_ADMITTED);
    }
    Path target = path(id);
    return withMaintenanceLock(
        () -> {
          Files.createDirectories(target.getParent());
          PutResult result =
              withShardLock(
                  id,
                  () -> {
                    PutResult.Status status;
                    Optional<byte[]> existing;
                    try {
                      existing = read(target, id);
                    } catch (CorruptDefinitionException exception) {
                      existing = Optional.empty();
                    }
                    if (existing.isPresent()) {
                      requireSameContent(id, ownedGroup, existing.orElseThrow());
                      status = PutResult.Status.REUSED;
                    } else {
                      Path temporary =
                          Files.createTempFile(
                              target.getParent(), "." + target.getFileName() + "-", ".tmp");
                      try {
                        try (FileChannel channel =
                            FileChannel.open(
                                temporary,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING)) {
                          ByteBuffer content = ByteBuffer.wrap(ownedGroup);
                          while (content.hasRemaining()) {
                            channel.write(content);
                          }
                          channel.force(true);
                        }
                        Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                      } finally {
                        Files.deleteIfExists(temporary);
                      }
                      status = PutResult.Status.STORED;
                    }
                    requireSameContent(
                        id,
                        ownedGroup,
                        read(target, id)
                            .orElseThrow(
                                () ->
                                    new IOException("definition disappeared during write: " + id)));
                    recordAccess(target);
                    return new PutResult(id, status);
                  });
          enforceCapacity(target);
          return result;
        });
  }

  @Override
  public Optional<byte[]> get(DefinitionGroupId id) throws IOException {
    Objects.requireNonNull(id, "id");
    Path path = path(id);
    Optional<byte[]> content = read(path, id);
    if (content.isPresent()) recordAccess(path);
    return content;
  }

  private <T> T withMaintenanceLock(IoOperation<T> operation) throws IOException {
    Path lockPath = root.resolve(".locks").resolve("maintenance");
    Files.createDirectories(lockPath.getParent());
    ReentrantLock processLock =
        MAINTENANCE_PROCESS_LOCKS[Math.floorMod(root.hashCode(), MAINTENANCE_PROCESS_LOCKS.length)];
    processLock.lock();
    try {
      try (FileChannel lockChannel =
          FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        FileLock maintenanceLock = lockChannel.lock();
        try {
          return operation.run();
        } finally {
          maintenanceLock.release();
        }
      }
    } finally {
      processLock.unlock();
    }
  }

  private <T> T withShardLock(DefinitionGroupId id, IoOperation<T> operation) throws IOException {
    String prefix = id.toString().substring(0, 2);
    int lockStripe = Integer.parseInt(prefix, 16);
    Path lockPath = root.resolve(".locks").resolve(prefix);
    Files.createDirectories(lockPath.getParent());
    ReentrantLock processLock = PROCESS_LOCKS[lockStripe];
    processLock.lock();
    try {
      try (FileChannel lockChannel =
          FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        FileLock definitionLock = lockChannel.lock();
        try {
          return operation.run();
        } finally {
          definitionLock.release();
        }
      }
    } finally {
      processLock.unlock();
    }
  }

  private Path path(DefinitionGroupId id) {
    String hash = id.toString();
    return root.resolve(hash.substring(0, 2)).resolve(hash.substring(2));
  }

  private Optional<byte[]> read(Path path, DefinitionGroupId expected) throws IOException {
    byte[] canonicalGroup;
    try {
      canonicalGroup = Files.readAllBytes(path);
    } catch (NoSuchFileException exception) {
      return Optional.empty();
    }
    DefinitionGroupId actual = DefinitionHasher.hashGroup(canonicalGroup);
    if (!expected.equals(actual)) {
      throw new CorruptDefinitionException(expected, actual);
    }
    return Optional.of(canonicalGroup);
  }

  private static void requireSameContent(DefinitionGroupId id, byte[] expected, byte[] actual)
      throws IOException {
    if (!Arrays.equals(expected, actual)) {
      throw new IOException("distinct definition groups have the same content hash: " + id);
    }
  }

  private boolean isGroupPath(Path path) {
    Path relative = root.relativize(path);
    if (relative.getNameCount() != 2) return false;
    String prefix = relative.getName(0).toString();
    String suffix = relative.getName(1).toString();
    return prefix.length() == 2 && suffix.length() == 62 && HASH.matcher(prefix + suffix).matches();
  }

  private void requirePolicy() throws IOException {
    Path policyPath = root.resolve(".locks").resolve("policy");
    StorePolicy requested = policy;
    if (Files.isRegularFile(policyPath)) {
      StorePolicy configured = StorePolicy.decode(Files.readAllBytes(policyPath));
      if (!configured.equals(requested)) {
        throw new DefinitionStorePolicyMismatchException(
            configured.maximumGroups(),
            configured.maximumBytes(),
            requested.maximumGroups(),
            requested.maximumBytes());
      }
      return;
    }
    if (Files.exists(policyPath)) {
      throw new IOException("definition store policy path is not a regular file: " + policyPath);
    }
    Path temporary = Files.createTempFile(policyPath.getParent(), "policy-", ".tmp");
    try {
      try (FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer content = ByteBuffer.wrap(requested.encode());
        while (content.hasRemaining()) {
          channel.write(content);
        }
        channel.force(true);
      }
      Files.move(temporary, policyPath, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void enforceCapacity(Path protectedPath) throws IOException {
    while (true) {
      StoreSnapshot snapshot = scan();
      if (snapshot.entries().size() <= policy.maximumGroups()
          && snapshot.bytes() <= policy.maximumBytes()) {
        return;
      }
      var oldest = new ArrayList<>(snapshot.entries());
      oldest.sort(
          Comparator.comparing((CacheEntry entry) -> entry.path().equals(protectedPath))
              .thenComparingLong(CacheEntry::lastAccessed)
              .thenComparing(entry -> entry.path().toString()));
      int remainingGroups = snapshot.entries().size();
      long remainingBytes = snapshot.bytes();
      boolean attempted = false;
      for (CacheEntry entry : oldest) {
        if (remainingGroups <= policy.maximumGroups() && remainingBytes <= policy.maximumBytes())
          break;
        if (entry.path().equals(protectedPath)) continue;
        withShardLock(
            entry.id(),
            () -> {
              if (Files.isRegularFile(entry.path())) Files.delete(entry.path());
              else if (Files.exists(entry.path())) {
                throw new IOException("definition path is not a regular file: " + entry.path());
              }
              forgetAccess(entry.path());
              return null;
            });
        attempted = true;
        remainingGroups--;
        remainingBytes -= entry.size();
      }
      if (!attempted) {
        throw new IOException("definition store capacity cannot retain the admitted group");
      }
    }
  }

  private StoreSnapshot scan() throws IOException {
    List<CacheEntry> entries = new ArrayList<>();
    Set<Path> livePaths = new HashSet<>();
    long bytes = 0;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).filter(this::isGroupPath).toList()) {
        long size = Files.size(path);
        try {
          bytes = Math.addExact(bytes, size);
        } catch (ArithmeticException exception) {
          throw new IOException("definition store size exceeds the supported range", exception);
        }
        Path relative = root.relativize(path);
        DefinitionGroupId id =
            DefinitionGroupId.parse(relative.getName(0).toString() + relative.getName(1));
        entries.add(
            new CacheEntry(
                id, path, size, lastAccessed(path, Files.getLastModifiedTime(path).toMillis())));
        livePaths.add(path);
      }
    }
    retainAccessHints(livePaths);
    return new StoreSnapshot(entries, bytes);
  }

  private synchronized void recordAccess(Path path) {
    accessHints.put(path, System.currentTimeMillis());
  }

  private synchronized long lastAccessed(Path path, long fallback) {
    return accessHints.getOrDefault(path, fallback);
  }

  private synchronized void retainAccessHints(Set<Path> livePaths) {
    accessHints.keySet().retainAll(livePaths);
  }

  private synchronized void forgetAccess(Path path) {
    accessHints.remove(path);
  }

  @FunctionalInterface
  private interface IoOperation<T> {
    T run() throws IOException;
  }

  private record StorePolicy(int maximumGroups, long maximumBytes) {
    private static final long MAGIC = 0x4e4f524d44533031L;
    private static final int ENCODED_BYTES = Long.BYTES + Integer.BYTES + Long.BYTES;

    private StorePolicy {
      if (maximumGroups < 1) throw new IllegalArgumentException("maximum groups must be positive");
      if (maximumBytes < 1) throw new IllegalArgumentException("maximum bytes must be positive");
    }

    private byte[] encode() {
      return ByteBuffer.allocate(ENCODED_BYTES)
          .putLong(MAGIC)
          .putInt(maximumGroups)
          .putLong(maximumBytes)
          .array();
    }

    private static StorePolicy decode(byte[] encoded) throws IOException {
      if (encoded.length != ENCODED_BYTES) {
        throw new IOException("definition store policy has an invalid length");
      }
      ByteBuffer reader = ByteBuffer.wrap(encoded);
      if (reader.getLong() != MAGIC) {
        throw new IOException("definition store policy has an invalid identity");
      }
      int maximumGroups = reader.getInt();
      long maximumBytes = reader.getLong();
      if (maximumGroups < 1 || maximumBytes < 1) {
        throw new IOException("definition store policy contains an invalid capacity");
      }
      return new StorePolicy(maximumGroups, maximumBytes);
    }
  }

  private record StoreSnapshot(List<CacheEntry> entries, long bytes) {}

  private record CacheEntry(DefinitionGroupId id, Path path, long size, long lastAccessed) {}
}
