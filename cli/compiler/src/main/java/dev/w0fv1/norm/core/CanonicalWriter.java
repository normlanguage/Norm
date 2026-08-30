package dev.w0fv1.norm.core;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class CanonicalWriter {
  private static final int TAG_KIND = 1;
  private static final int BOOLEAN_KIND = 2;
  private static final int INT_KIND = 3;
  private static final int LONG_KIND = 4;
  private static final int STRING_KIND = 5;
  private static final int BYTES_KIND = 6;

  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  public CanonicalWriter() {}

  public CanonicalWriter writeTag(String value) {
    return writeLengthPrefixed(TAG_KIND, encodeUtf8(value));
  }

  public CanonicalWriter writeBoolean(boolean value) {
    output.write(BOOLEAN_KIND);
    output.write(value ? 1 : 0);
    return this;
  }

  public CanonicalWriter writeInt(int value) {
    output.write(INT_KIND);
    writeIntPayload(value);
    return this;
  }

  public CanonicalWriter writeLong(long value) {
    output.write(LONG_KIND);
    output.write((int) (value >>> 56) & 0xff);
    output.write((int) (value >>> 48) & 0xff);
    output.write((int) (value >>> 40) & 0xff);
    output.write((int) (value >>> 32) & 0xff);
    output.write((int) (value >>> 24) & 0xff);
    output.write((int) (value >>> 16) & 0xff);
    output.write((int) (value >>> 8) & 0xff);
    output.write((int) value & 0xff);
    return this;
  }

  public CanonicalWriter writeString(String value) {
    return writeLengthPrefixed(STRING_KIND, encodeUtf8(value));
  }

  public CanonicalWriter writeBytes(byte[] value) {
    Objects.requireNonNull(value, "value");
    return writeLengthPrefixed(BYTES_KIND, value);
  }

  public byte[] toByteArray() {
    return output.toByteArray();
  }

  private CanonicalWriter writeLengthPrefixed(int kind, byte[] value) {
    output.write(kind);
    writeIntPayload(value.length);
    output.writeBytes(value);
    return this;
  }

  private void writeIntPayload(int value) {
    output.write(value >>> 24 & 0xff);
    output.write(value >>> 16 & 0xff);
    output.write(value >>> 8 & 0xff);
    output.write(value & 0xff);
  }

  private static byte[] encodeUtf8(String value) {
    Objects.requireNonNull(value, "value");
    try {
      ByteBuffer encoded =
          StandardCharsets.UTF_8
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(value));
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return bytes;
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("value is not valid Unicode", exception);
    }
  }
}
