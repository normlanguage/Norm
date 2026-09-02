package dev.w0fv1.norm.bridge;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JavaApplicationBridge {
  private static final ConcurrentMap<ClassLoader, Handler> HANDLERS = new ConcurrentHashMap<>();

  private JavaApplicationBridge() {}

  public static Registration install(ClassLoader loader, Handler handler) {
    Objects.requireNonNull(loader, "loader");
    Objects.requireNonNull(handler, "handler");
    if (HANDLERS.putIfAbsent(loader, handler) != null) {
      throw new IllegalStateException("Norm Java application bridge is already installed");
    }
    return () -> HANDLERS.remove(loader, handler);
  }

  public static void construct(
      Class<?> owner, Object receiver, String callable, Object[] arguments) {
    handler(owner).construct(callable, receiver, arguments.clone());
  }

  public static void allocate(Class<?> owner, Object receiver, String definition) {
    handler(owner).allocate(definition, receiver);
  }

  public static Object invoke(
      Class<?> owner, Object receiver, String callable, Object[] arguments) {
    return handler(owner).invoke(callable, receiver, arguments.clone());
  }

  public static Object invokeHost(Object receiver, String callable, Object[] arguments) {
    Objects.requireNonNull(receiver, "receiver");
    return handler(receiver.getClass()).invokeHost(callable, receiver, arguments.clone());
  }

  public static Object toJava(ClassLoader loader, Object value) {
    Objects.requireNonNull(value, "value");
    return handler(loader).toJava(value);
  }

  public static Object fromJava(ClassLoader loader, Object value) {
    Objects.requireNonNull(value, "value");
    return handler(loader).fromJava(value);
  }

  public static void writeField(Object receiver, String field, Object value) {
    Objects.requireNonNull(receiver, "receiver");
    handler(receiver.getClass()).writeField(receiver, field, value);
  }

  private static Handler handler(Class<?> owner) {
    Objects.requireNonNull(owner, "owner");
    return handler(owner.getClassLoader());
  }

  private static Handler handler(ClassLoader loader) {
    Objects.requireNonNull(loader, "loader");
    Handler handler = HANDLERS.get(loader);
    if (handler == null) {
      throw new IllegalStateException("Norm Java application bridge is not installed");
    }
    return handler;
  }

  public interface Handler {
    void allocate(String definition, Object receiver);

    void construct(String callable, Object receiver, Object[] arguments);

    Object invoke(String callable, Object receiver, Object[] arguments);

    Object invokeHost(String callable, Object receiver, Object[] arguments);

    Object toJava(Object value);

    Object fromJava(Object value);

    void writeField(Object receiver, String field, Object value);
  }

  @FunctionalInterface
  public interface Registration extends AutoCloseable {
    @Override
    void close();
  }
}
