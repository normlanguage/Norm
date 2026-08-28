package dev.w0fv1.norm.truffle;

import java.util.Arrays;
import java.util.Objects;

final class ByteSequence {
  private final byte[] storage;
  private final int offset;
  private final int length;

  ByteSequence(byte[] storage) {
    this(storage, 0, storage.length);
  }

  ByteSequence(byte[] storage, int offset, int length) {
    this.storage = Objects.requireNonNull(storage, "storage");
    Objects.checkFromIndexSize(offset, length, storage.length);
    this.offset = offset;
    this.length = length;
  }

  int size() {
    return length;
  }

  byte[] storage() {
    return storage;
  }

  int offset() {
    return offset;
  }

  int at(int index) {
    Objects.checkIndex(index, length);
    return Byte.toUnsignedInt(storage[offset + index]);
  }

  ByteSequence slice(int start, int size) {
    Objects.checkFromIndexSize(start, size, length);
    return new ByteSequence(storage, offset + start, size);
  }

  byte[] toArray() {
    return Arrays.copyOfRange(storage, offset, offset + length);
  }

  void copyTo(byte[] target, int targetOffset) {
    System.arraycopy(storage, offset, target, targetOffset, length);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ByteSequence sequence
        && Arrays.equals(
            storage,
            offset,
            offset + length,
            sequence.storage,
            sequence.offset,
            sequence.offset + sequence.length);
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int index = offset; index < offset + length; index++) {
      result = 31 * result + storage[index];
    }
    return result;
  }
}
