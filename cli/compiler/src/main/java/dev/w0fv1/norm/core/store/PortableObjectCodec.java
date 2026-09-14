package dev.w0fv1.norm.core.store;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import com.esotericsoftware.kryo.serializers.MapSerializer;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objenesis.strategy.StdInstantiatorStrategy;

public final class PortableObjectCodec {
  private PortableObjectCodec() {}

  public static <T> byte[] encode(T value) throws IOException {
    return encode(value, false);
  }

  public static <T> byte[] encodeDeterministic(T value) throws IOException {
    return encode(value, true);
  }

  private static <T> byte[] encode(T value, boolean deterministic) throws IOException {
    try (Output output = new Output(4096, -1)) {
      kryo(deterministic).writeObject(output, value);
      return output.toBytes();
    } catch (RuntimeException exception) {
      throw new IOException("cannot encode artifact", exception);
    }
  }

  public static <T> T decode(byte[] bytes, Class<T> type) throws IOException {
    return decode(bytes, type, false);
  }

  public static <T> T decodeDeterministic(byte[] bytes, Class<T> type) throws IOException {
    return decode(bytes, type, true);
  }

  private static <T> T decode(byte[] bytes, Class<T> type, boolean deterministic)
      throws IOException {
    try (Input input = new Input(bytes)) {
      T value = kryo(deterministic).readObject(input, type);
      if (input.position() != bytes.length) throw new IOException("artifact has trailing content");
      return value;
    } catch (RuntimeException exception) {
      throw new IOException("cannot decode artifact", exception);
    }
  }

  public static <T> void write(T application, Path destination) throws IOException {
    try {
      try (Output output = new Output(Files.newOutputStream(destination))) {
        kryo(false).writeObject(output, application);
      }
    } catch (RuntimeException exception) {
      throw new IOException("cannot encode artifact", exception);
    }
  }

  public static <T> T read(Path source, Class<T> type) throws IOException {
    try {
      try (Input input = new Input(Files.newInputStream(source))) {
        return kryo(false).readObject(input, type);
      }
    } catch (RuntimeException exception) {
      throw new IOException("cannot decode artifact", exception);
    }
  }

  private static Kryo kryo(boolean deterministic) {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(false);
    kryo.setReferences(!deterministic);
    kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    kryo.addDefaultSerializer(Collection.class, new PortableCollectionSerializer(deterministic));
    kryo.addDefaultSerializer(Map.class, new PortableMapSerializer(deterministic));
    kryo.addDefaultSerializer(Path.class, new PathSerializer());
    kryo.addDefaultSerializer(URI.class, new UriSerializer());
    kryo.addDefaultSerializer(Optional.class, new OptionalSerializer());
    kryo.addDefaultSerializer(dev.w0fv1.norm.source.SourceFile.class, new SourceFileSerializer());
    return kryo;
  }

  public static final class SourceFileSerializer
      extends Serializer<dev.w0fv1.norm.source.SourceFile> {
    private final Map<dev.w0fv1.norm.source.SourceFile, Integer> written = new LinkedHashMap<>();
    private final java.util.List<dev.w0fv1.norm.source.SourceFile> read = new ArrayList<>();

    @Override
    public void write(Kryo kryo, Output output, dev.w0fv1.norm.source.SourceFile source) {
      Integer index = written.get(source);
      if (index != null) {
        output.writeInt(index, true);
        return;
      }
      output.writeInt(written.size(), true);
      written.put(source, written.size());
      output.writeString(source.id().uri().toString());
      output.writeString(source.text());
    }

    @Override
    public dev.w0fv1.norm.source.SourceFile read(
        Kryo kryo, Input input, Class<? extends dev.w0fv1.norm.source.SourceFile> type) {
      int index = input.readInt(true);
      if (index < 0 || index > read.size())
        throw new IllegalArgumentException("invalid source table reference");
      if (index < read.size()) return read.get(index);
      var source =
          dev.w0fv1.norm.source.SourceFile.of(
              dev.w0fv1.norm.source.DocumentId.of(input.readString()), input.readString());
      read.add(source);
      return source;
    }
  }

  public static final class PortableCollectionSerializer
      extends CollectionSerializer<Collection<Object>> {
    private final boolean deterministic;

    PortableCollectionSerializer(boolean deterministic) {
      this.deterministic = deterministic;
    }

    @Override
    public void write(Kryo kryo, Output output, Collection<Object> values) {
      super.write(
          kryo, output, deterministic && values instanceof Set<?> ? ordered(values) : values);
    }

    @Override
    protected Collection<Object> create(
        Kryo kryo, Input input, Class<? extends Collection<Object>> type, int size) {
      return Set.class.isAssignableFrom(type)
          ? new LinkedHashSet<>(capacity(size))
          : new ArrayList<>(size);
    }

    private static int capacity(int size) {
      return Math.max(16, (int) Math.ceil(size / 0.75d));
    }
  }

  public static final class PortableMapSerializer extends MapSerializer<Map<Object, Object>> {
    private final boolean deterministic;

    PortableMapSerializer(boolean deterministic) {
      this.deterministic = deterministic;
    }

    @Override
    public void write(Kryo kryo, Output output, Map<Object, Object> values) {
      if (!deterministic) {
        super.write(kryo, output, values);
        return;
      }
      var sorted = new LinkedHashMap<Object, Object>();
      for (Object key : ordered(values.keySet())) sorted.put(key, values.get(key));
      super.write(kryo, output, sorted);
    }

    @Override
    protected Map<Object, Object> create(
        Kryo kryo, Input input, Class<? extends Map<Object, Object>> type, int size) {
      return new LinkedHashMap<>(Math.max(16, (int) Math.ceil(size / 0.75d)));
    }
  }

  private static java.util.List<Object> ordered(Collection<Object> values) {
    var entries = new ArrayList<OrderedValue>();
    for (Object value : values) {
      try {
        entries.add(
            new OrderedValue(value, value == null ? new byte[0] : encodeDeterministic(value)));
      } catch (IOException exception) {
        throw new java.io.UncheckedIOException(exception);
      }
    }
    entries.sort(
        java.util.Comparator.comparing(
                (OrderedValue entry) ->
                    entry.value() == null ? "" : entry.value().getClass().getName())
            .thenComparing(OrderedValue::bytes, java.util.Arrays::compareUnsigned));
    return entries.stream().map(OrderedValue::value).toList();
  }

  private record OrderedValue(Object value, byte[] bytes) {}

  public static final class PathSerializer extends Serializer<Path> {
    @Override
    public void write(Kryo kryo, Output output, Path value) {
      output.writeString(value.toString());
    }

    @Override
    public Path read(Kryo kryo, Input input, Class<? extends Path> type) {
      return Path.of(input.readString());
    }
  }

  public static final class UriSerializer extends Serializer<URI> {
    @Override
    public void write(Kryo kryo, Output output, URI value) {
      output.writeString(value.toString());
    }

    @Override
    public URI read(Kryo kryo, Input input, Class<? extends URI> type) {
      return URI.create(input.readString());
    }
  }

  public static final class OptionalSerializer extends Serializer<Optional<?>> {
    @Override
    public void write(Kryo kryo, Output output, Optional<?> value) {
      output.writeBoolean(value.isPresent());
      value.ifPresent(item -> kryo.writeClassAndObject(output, item));
    }

    @Override
    public Optional<?> read(Kryo kryo, Input input, Class<? extends Optional<?>> type) {
      return input.readBoolean()
          ? Optional.ofNullable(kryo.readClassAndObject(input))
          : Optional.empty();
    }
  }
}
