package dev.w0fv1.norm.runtime;

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

public final class NativeApplicationArchive {
  private NativeApplicationArchive() {}

  public static void write(NativeApplicationData application, Path destination) throws IOException {
    try {
      try (Output output = new Output(Files.newOutputStream(destination))) {
        kryo().writeObject(output, application);
      }
    } catch (RuntimeException exception) {
      throw new IOException("cannot encode native application", exception);
    }
  }

  public static NativeApplicationData read(Path source) throws IOException {
    try {
      try (Input input = new Input(Files.newInputStream(source))) {
        return kryo().readObject(input, NativeApplicationData.class);
      }
    } catch (RuntimeException exception) {
      throw new IOException("cannot decode native application", exception);
    }
  }

  private static Kryo kryo() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(false);
    kryo.setReferences(true);
    kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    kryo.addDefaultSerializer(Collection.class, PortableCollectionSerializer.class);
    kryo.addDefaultSerializer(Map.class, PortableMapSerializer.class);
    kryo.addDefaultSerializer(Path.class, PathSerializer.class);
    kryo.addDefaultSerializer(URI.class, UriSerializer.class);
    kryo.addDefaultSerializer(Optional.class, OptionalSerializer.class);
    return kryo;
  }

  public static final class PortableCollectionSerializer
      extends CollectionSerializer<Collection<Object>> {
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
    @Override
    protected Map<Object, Object> create(
        Kryo kryo, Input input, Class<? extends Map<Object, Object>> type, int size) {
      return new LinkedHashMap<>(Math.max(16, (int) Math.ceil(size / 0.75d)));
    }
  }

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
