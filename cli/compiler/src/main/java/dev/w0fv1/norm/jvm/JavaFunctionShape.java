package dev.w0fv1.norm.jvm;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public enum JavaFunctionShape {
  ACTION(0, true, Runnable.class),
  SUPPLIER(0, false, Supplier.class),
  CONSUMER(1, true, Consumer.class),
  FUNCTION(1, false, Function.class),
  BI_CONSUMER(2, true, BiConsumer.class),
  BI_FUNCTION(2, false, BiFunction.class);

  private final int arity;
  private final boolean returnsVoid;
  private final Class<?> type;

  JavaFunctionShape(int arity, boolean returnsVoid, Class<?> type) {
    this.arity = arity;
    this.returnsVoid = returnsVoid;
    this.type = type;
  }

  public static Optional<JavaFunctionShape> of(int arity, boolean returnsVoid) {
    return Arrays.stream(values())
        .filter(shape -> shape.arity == arity && shape.returnsVoid == returnsVoid)
        .findFirst();
  }

  public String binaryName() {
    return type.getName();
  }

  public Class<?> interfaceType() {
    return type;
  }

  public Object adapt(Object state, Function<Object[], Object> invocation) {
    var sidecar = new java.util.concurrent.atomic.AtomicReference<>(state);
    return java.lang.reflect.Proxy.newProxyInstance(
        dev.w0fv1.norm.bridge.JavaApplicationObject.class.getClassLoader(),
        new Class<?>[] {type, dev.w0fv1.norm.bridge.JavaApplicationObject.class},
        new dev.w0fv1.norm.bridge.JavaApplicationObject.StateHandler() {
          @Override
          public Object state() {
            return sidecar.get();
          }

          @Override
          public void state(Object value) {
            sidecar.set(value);
          }

          @Override
          public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
              throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
              return switch (method.getName()) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" ->
                    type.getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
                default -> throw new IllegalStateException("Unsupported function object method");
              };
            }
            if (method.isDefault())
              return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, arguments);
            return invocation.apply(arguments == null ? new Object[0] : arguments);
          }
        });
  }

  public boolean accepts(Object value) {
    return type.isInstance(value);
  }

  @SuppressWarnings("unchecked")
  public Object invoke(Object function, Object[] arguments) {
    if (arguments.length != arity)
      throw new IllegalArgumentException("Java function argument count does not match");
    return switch (this) {
      case ACTION -> {
        ((Runnable) function).run();
        yield null;
      }
      case SUPPLIER -> ((Supplier<?>) function).get();
      case CONSUMER -> {
        ((Consumer<Object>) function).accept(arguments[0]);
        yield null;
      }
      case FUNCTION -> ((Function<Object, ?>) function).apply(arguments[0]);
      case BI_CONSUMER -> {
        ((BiConsumer<Object, Object>) function).accept(arguments[0], arguments[1]);
        yield null;
      }
      case BI_FUNCTION ->
          ((BiFunction<Object, Object, ?>) function).apply(arguments[0], arguments[1]);
    };
  }
}
