package dev.w0fv1.norm.jvm;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import java.util.List;
import java.util.Map;

final class PublishedBindingAbi2 {
  private PublishedBindingAbi2() {}

  static Map<Class<?>, Serializer<?>> serializers() {
    return Map.of(
        JavaBindingCallable.class,
        new CallableSerializer(),
        JarBindingType.class,
        new TypeSerializer());
  }

  private static final class CallableSerializer extends Serializer<JavaBindingCallable> {
    @Override
    public void write(Kryo kryo, Output output, JavaBindingCallable value) {
      throw new UnsupportedOperationException("Java binding ABI 2 is read-only");
    }

    @Override
    public JavaBindingCallable read(
        Kryo kryo, Input input, Class<? extends JavaBindingCallable> type) {
      String descriptor = kryo.readObjectOrNull(input, String.class);
      JavaCallableKind kind = kryo.readObjectOrNull(input, JavaCallableKind.class);
      String name = kryo.readObjectOrNull(input, String.class);
      String owner = kryo.readObjectOrNull(input, String.class);
      List<JavaBindingType> parameters =
          list(kryo.readClassAndObject(input), JavaBindingType.class);
      JavaNullability nullability = kryo.readObjectOrNull(input, JavaNullability.class);
      JavaBindingType result = (JavaBindingType) kryo.readClassAndObject(input);
      List<JavaBindingTypeParameter> variables =
          list(kryo.readClassAndObject(input), JavaBindingTypeParameter.class);
      return new JavaBindingCallable(
          owner, name, descriptor, kind, variables, parameters, result, nullability);
    }
  }

  private static final class TypeSerializer extends Serializer<JarBindingType> {
    @Override
    public void write(Kryo kryo, Output output, JarBindingType value) {
      throw new UnsupportedOperationException("Java binding ABI 2 is read-only");
    }

    @Override
    public JarBindingType read(Kryo kryo, Input input, Class<? extends JarBindingType> type) {
      List<String> members = list(kryo.readClassAndObject(input), String.class);
      String name = kryo.readObjectOrNull(input, String.class);
      List<JarBindingOverload> overloads =
          list(kryo.readClassAndObject(input), JarBindingOverload.class);
      return new JarBindingType(name, members, overloads);
    }
  }

  private static <T> List<T> list(Object value, Class<T> type) {
    return ((List<?>) value).stream().map(type::cast).toList();
  }
}
