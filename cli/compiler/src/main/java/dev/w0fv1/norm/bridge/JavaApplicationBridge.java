package dev.w0fv1.norm.bridge;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class JavaApplicationBridge {
  public static Registration install(java.util.List<ClassLoader> loaders, Handler handler) {
    return register(loaders, handler, false);
  }

  public static Registration replace(java.util.List<ClassLoader> loaders, Handler handler) {
    return register(loaders, handler, true);
  }

  private static Registration register(
      java.util.List<ClassLoader> loaders, Handler handler, boolean replace) {
    var registrations = new java.util.ArrayList<Registration>();
    try {
      for (ClassLoader loader : new java.util.LinkedHashSet<>(loaders))
        registrations.add(register(loader, handler, replace));
      return () -> registrations.reversed().forEach(Registration::close);
    } catch (RuntimeException | Error exception) {
      registrations.reversed().forEach(Registration::close);
      throw exception;
    }
  }

  private static final Map<ClassLoader, WeakReference<Handler>> HANDLERS = new WeakHashMap<>();

  private JavaApplicationBridge() {}

  public enum SuperCall {
    INSTANCE
  }

  public static Registration install(ClassLoader loader, Handler handler) {
    return register(loader, handler, false);
  }

  public static Registration replace(ClassLoader loader, Handler handler) {
    return register(loader, handler, true);
  }

  private static Registration register(ClassLoader loader, Handler handler, boolean replace) {
    Objects.requireNonNull(loader, "loader");
    Objects.requireNonNull(handler, "handler");
    synchronized (HANDLERS) {
      var previous = HANDLERS.get(loader);
      if (!replace && previous != null && previous.get() != null)
        throw new IllegalStateException("Norm Java application bridge is already installed");
      HANDLERS.put(loader, new WeakReference<>(handler));
    }
    return () -> {
      synchronized (HANDLERS) {
        var current = HANDLERS.get(loader);
        if (current != null && current.get() == handler) HANDLERS.remove(loader);
      }
    };
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

  public static Object invokeHost(
      ClassLoader loader,
      Object receiver,
      String callable,
      Object[] arguments,
      Object receiverType,
      Object[] methodTypes) {
    Objects.requireNonNull(receiver, "receiver");
    return handler(loader)
        .invokeHost(callable, receiver, arguments.clone(), receiverType, methodTypes.clone());
  }

  public static Object toJava(ClassLoader loader, Object value) {
    Objects.requireNonNull(value, "value");
    return handler(loader).toJava(value);
  }

  public static Object fromJava(ClassLoader loader, Object value) {
    Objects.requireNonNull(value, "value");
    return handler(loader).fromJava(value);
  }

  public static void writeField(ClassLoader loader, Object receiver, String field, Object value) {
    Objects.requireNonNull(receiver, "receiver");
    handler(loader).writeField(receiver, field, value);
  }

  private static Handler handler(Class<?> owner) {
    Objects.requireNonNull(owner, "owner");
    return handler(owner.getClassLoader());
  }

  private static Handler handler(ClassLoader loader) {
    Objects.requireNonNull(loader, "loader");
    synchronized (HANDLERS) {
      var reference = HANDLERS.get(loader);
      Handler handler = reference == null ? null : reference.get();
      if (handler == null)
        throw new IllegalStateException("Norm Java application bridge is not installed");
      return handler;
    }
  }

  public interface Handler {
    void allocate(String definition, Object receiver);

    void construct(String callable, Object receiver, Object[] arguments);

    Object invoke(String callable, Object receiver, Object[] arguments);

    Object invokeHost(
        String callable,
        Object receiver,
        Object[] arguments,
        Object receiverType,
        Object[] methodTypes);

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
