package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import dev.w0fv1.norm.execution.JarBindingCallback;
import dev.w0fv1.norm.execution.JarBindingCallbackException;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.execution.JarBindingDuration;
import dev.w0fv1.norm.execution.JarBindingEnumValue;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingPath;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingRuntime;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.execution.JarBindingTask;
import dev.w0fv1.norm.execution.JarBindingUri;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class JvmJarBindingRuntime
    implements JarBindingRuntime, JavaApplicationRuntime, AutoCloseable {
  private static final Duration CLASS_LOADER_RETIREMENT_TIMEOUT = Duration.ofSeconds(5);
  private final Map<String, BoundCall> calls;
  private final boolean ownsApplicationLoader;
  private ClassLoader applicationLoader;
  private Map<String, JavaDirectCall> applicationCalls = Map.of();

  public JvmJarBindingRuntime(List<ResolvedJarBinding> bindings) {
    this(bindings, List.of());
  }

  public JvmJarBindingRuntime(List<ResolvedJarBinding> bindings, List<Path> applicationClasspath) {
    this(link(bindings), applicationClassLoader(bindings, applicationClasspath), true);
  }

  public static JvmJarBindingRuntime closedWorld(List<LinkedJarBinding> bindings) {
    return new JvmJarBindingRuntime(bindings, JvmJarBindingRuntime.class.getClassLoader(), false);
  }

  public static JvmJarBindingRuntime closedWorld(
      List<LinkedJarBinding> bindings, Map<String, JavaDirectCall> directCalls) {
    return closedWorld(
        LinkedJarBinding.linkCalls(bindings),
        directCalls,
        LinkedJavaClasses.resolve(bindings, JvmJarBindingRuntime.class.getClassLoader()),
        JavaApplicationCallLinker.link(JvmJarBindingRuntime.class.getClassLoader()));
  }

  public static JvmJarBindingRuntime closedWorld(
      Map<String, JavaBindingCallable> callables,
      Map<String, JavaDirectCall> directCalls,
      LinkedJavaClasses linkedClasses,
      Map<String, JavaDirectCall> applicationCalls) {
    return closedWorld(prepareCalls(callables, directCalls), linkedClasses, applicationCalls);
  }

  public static LinkedCalls prepareCalls(
      Map<String, JavaBindingCallable> callables, Map<String, JavaDirectCall> directCalls) {
    if (!callables.keySet().equals(directCalls.keySet()))
      throw new IllegalArgumentException("direct Java calls must match the linked call set");
    var prepared = new LinkedHashMap<String, LinkedCall>();
    callables.forEach(
        (id, callable) ->
            prepared.put(
                id,
                prepareCall(
                    callable,
                    java.util.Objects.requireNonNull(
                        directCalls.get(id), "direct Java call " + id))));
    return new LinkedCalls(prepared);
  }

  public static JvmJarBindingRuntime closedWorld(
      LinkedCalls prepared,
      LinkedJavaClasses linkedClasses,
      Map<String, JavaDirectCall> applicationCalls) {
    return new JvmJarBindingRuntime(
        () -> prepared,
        JvmJarBindingRuntime.class.getClassLoader(),
        false,
        () -> linkedClasses,
        () -> applicationCalls);
  }

  private JvmJarBindingRuntime(
      List<LinkedJarBinding> bindings,
      ClassLoader applicationLoader,
      boolean ownsApplicationLoader) {
    this(
        () -> {
          var prepared = new LinkedHashMap<String, LinkedCall>();
          LinkedJarBinding.linkCalls(bindings)
              .forEach(
                  (id, callable) ->
                      prepared.put(id, prepareCall(callable, bind(applicationLoader, callable))));
          return new LinkedCalls(prepared);
        },
        applicationLoader,
        ownsApplicationLoader,
        () -> LinkedJavaClasses.resolve(bindings, applicationLoader),
        () -> JavaApplicationCallLinker.link(applicationLoader));
  }

  private JvmJarBindingRuntime(
      java.util.function.Supplier<LinkedCalls> callables,
      ClassLoader applicationLoader,
      boolean ownsApplicationLoader,
      java.util.function.Supplier<LinkedJavaClasses> classLinker,
      java.util.function.Supplier<Map<String, JavaDirectCall>> applicationLinker) {
    calls = new LinkedHashMap<>();
    this.applicationLoader = applicationLoader;
    this.ownsApplicationLoader = ownsApplicationLoader;
    try {
      ClassCatalog classes = new ClassCatalog(applicationLoader, classLinker.get());
      callables
          .get()
          .calls
          .forEach((name, callable) -> calls.put(name, new BoundCall(callable, classes)));
      applicationCalls = Map.copyOf(applicationLinker.get());
    } catch (IllegalArgumentException exception) {
      close();
      throw new JarBindingRuntimeException(exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      close();
      throw exception;
    }
  }

  private static List<LinkedJarBinding> link(List<ResolvedJarBinding> bindings) {
    return bindings.stream().map(LinkedJarBinding::from).toList();
  }

  private static ClassLoader applicationClassLoader(
      List<ResolvedJarBinding> bindings, List<Path> applicationClasspath) {
    Set<Path> paths = new LinkedHashSet<>();
    applicationClasspath.stream()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .forEach(paths::add);
    JarBindingClasspath.resolve(bindings).forEach(paths::add);
    URL[] urls =
        paths.stream()
            .map(
                path -> {
                  try {
                    return path.toUri().toURL();
                  } catch (java.net.MalformedURLException exception) {
                    throw new JarBindingRuntimeException(
                        "invalid classpath entry " + path, exception);
                  }
                })
            .toArray(URL[]::new);
    return new ApplicationClassLoader(urls, JvmJarBindingRuntime.class.getClassLoader());
  }

  @Override
  public ClassLoader applicationClassLoader() {
    if (applicationLoader == null) {
      throw new JarBindingRuntimeException("JAR binding runtime is closed");
    }
    return applicationLoader;
  }

  @Override
  public Map<String, JavaDirectCall> applicationCalls() {
    if (applicationLoader == null) {
      throw new JarBindingRuntimeException("JAR binding runtime is closed");
    }
    return applicationCalls;
  }

  @Override
  public JarBindingResult invoke(String callId, List<Object> arguments) {
    BoundCall call = calls.get(callId);
    if (call == null) throw new JarBindingRuntimeException("unknown JAR binding call " + callId);
    int receiverCount = call.callable().receiver() ? 1 : 0;
    int expectedArguments = call.callable().parameters().size() + receiverCount;
    if (arguments.size() != expectedArguments) {
      throw new JarBindingRuntimeException(
          "JAR binding call expected "
              + expectedArguments
              + " arguments but received "
              + arguments.size());
    }
    Thread thread = Thread.currentThread();
    ClassLoader previousContextLoader = thread.getContextClassLoader();
    thread.setContextClassLoader(applicationClassLoader());
    try {
      Object[] adapted = new Object[expectedArguments];
      if (receiverCount == 1) {
        Object receiver = arguments.getFirst();
        adapted[0] =
            receiver instanceof JarBindingEnumValue enumValue
                ? call.classes().resolve(enumValue)
                : receiver;
      }
      for (int index = 0; index < call.callable().parameters().size(); index++) {
        adapted[index + receiverCount] =
            call.callable()
                .parameters()
                .get(index)
                .apply(call.classes(), arguments.get(index + receiverCount));
      }
      boolean closesResource =
          call.callable().closesResource() && adapted[0] instanceof AutoCloseable;
      Object value = call.callable().target().invoke(adapted);
      if (closesResource) return JarBindingResult.ResourceClosed.INSTANCE;
      return call.callable().result().apply(call.classes(), value);
    } catch (JarBindingCallbackException exception) {
      throw exception.failure();
    } catch (JarBindingRuntimeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new JarBindingInvocationException(
          "JAR binding call failed: " + call.callable().description(), throwable);
    } finally {
      thread.setContextClassLoader(previousContextLoader);
    }
  }

  private static Conversion<Object> argumentAdapter(JavaBindingType type) {
    if (type instanceof JavaReferenceType reference) {
      switch (reference.kind()) {
        case OPTIONAL -> {
          var element = argumentAdapter(optionalElement(reference));
          return (classes, value) ->
              value == null
                  ? java.util.Optional.empty()
                  : java.util.Optional.of(element.apply(classes, value));
        }
        case OPTIONAL_INT -> {
          return (classes, value) ->
              value == null
                  ? java.util.OptionalInt.empty()
                  : java.util.OptionalInt.of(((Number) value).intValue());
        }
        case OPTIONAL_LONG -> {
          return (classes, value) ->
              value == null
                  ? java.util.OptionalLong.empty()
                  : java.util.OptionalLong.of(((Number) value).longValue());
        }
        case OPTIONAL_DOUBLE -> {
          return (classes, value) ->
              value == null
                  ? java.util.OptionalDouble.empty()
                  : java.util.OptionalDouble.of(((Number) value).doubleValue());
        }
        default -> {}
      }
    }
    Conversion<Object> conversion =
        switch (type) {
          case JavaArrayType ignored -> (classes, value) -> value;
          case JavaPrimitiveType primitive -> (classes, value) -> adaptPrimitive(primitive, value);
          case JavaBoxedType boxed -> {
            var primitive = boxed.primitive();
            yield (classes, value) -> adaptPrimitive(primitive, value);
          }
          case JavaBindingTypeVariable ignored -> (classes, value) -> value;
          case JavaCallbackType callback -> {
            var parameters =
                callback.parameters().stream().map(JvmJarBindingRuntime::resultAdapter).toList();
            var result = argumentAdapter(callback.returnType());
            var binaryName = callback.binaryName();
            var methodName = callback.methodName();
            boolean returnsVoid = callback.returnType() == JavaPrimitiveType.VOID;
            yield (classes, value) ->
                adaptCallback(
                    classes, binaryName, methodName, parameters, result, returnsVoid, value);
          }
          case JavaReferenceType reference ->
              switch (reference.kind()) {
                case PATH ->
                    (classes, value) -> java.nio.file.Path.of(((JarBindingPath) value).value());
                case FILE -> (classes, value) -> new java.io.File(((JarBindingPath) value).value());
                case CHARSET ->
                    (classes, value) -> java.nio.charset.Charset.forName((String) value);
                case CLASS -> (classes, value) -> classes.resolve((JarBindingClassReference) value);
                case ENUM -> (classes, value) -> classes.resolve((JarBindingEnumValue) value);
                case URI -> {
                  var binaryName = reference.binaryName();
                  yield (classes, value) -> uriArgument(binaryName, (JarBindingUri) value);
                }
                case DURATION ->
                    (classes, value) -> {
                      JarBindingDuration duration = (JarBindingDuration) value;
                      return java.time.Duration.ofSeconds(
                          duration.seconds(), duration.nanoseconds());
                    };
                case TASK ->
                    (classes, value) -> {
                      if (!(value instanceof JarBindingTask task))
                        throw new JarBindingRuntimeException(
                            "JAR task argument is not a Norm Task");
                      return task.hostValue();
                    };
                case UNIT -> (classes, value) -> null;
                case OPTIONAL, OPTIONAL_INT, OPTIONAL_LONG, OPTIONAL_DOUBLE ->
                    throw new IllegalStateException("Optional argument was not adapted");
                case PUBLISHER,
                    ITERABLE,
                    ITERATOR,
                    COLLECTION,
                    LIST,
                    SET,
                    MAP,
                    EXCEPTION,
                    INPUT_STREAM,
                    NUMBER,
                    OBJECT,
                    OPAQUE,
                    OUTPUT_STREAM,
                    RESOURCE,
                    STRING,
                    CHAR_SEQUENCE ->
                    (classes, value) -> value;
              };
        };
    return (classes, value) -> value == null ? null : conversion.apply(classes, value);
  }

  private static Object adaptCallback(
      ClassCatalog classes,
      String binaryName,
      String methodName,
      List<Conversion<JarBindingResult>> parameters,
      Conversion<Object> resultConversion,
      boolean returnsVoid,
      Object value) {
    if (!(value instanceof JarBindingCallback callback)) {
      throw new JarBindingRuntimeException("JAR callback argument is not a Norm function");
    }
    Class<?> callbackInterface = classes.load(binaryName);
    if (!callbackInterface.isInterface()) {
      throw new JarBindingRuntimeException("Java callback type is not an interface: " + binaryName);
    }
    return Proxy.newProxyInstance(
        classes.loader(),
        new Class<?>[] {callbackInterface},
        (proxy, method, arguments) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "equals" -> proxy == arguments[0];
              case "hashCode" -> System.identityHashCode(proxy);
              case "toString" -> "Norm function as " + binaryName;
              default -> throw new IllegalStateException("unexpected Object method " + method);
            };
          }
          if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
          if (!method.getName().equals(methodName)) {
            throw new JarBindingRuntimeException(
                "unexpected Java callback method " + binaryName + "." + method.getName());
          }
          Object[] values = arguments == null ? new Object[0] : arguments;
          if (values.length != parameters.size()) {
            throw new JarBindingRuntimeException(
                "Java callback expected "
                    + parameters.size()
                    + " arguments but received "
                    + values.length);
          }
          List<JarBindingResult> adapted = new ArrayList<>(values.length);
          for (int index = 0; index < values.length; index++) {
            adapted.add(parameters.get(index).apply(classes, values[index]));
          }
          Object result = callback.invoke(adapted);
          return returnsVoid ? null : resultConversion.apply(classes, result);
        });
  }

  private static Object adaptPrimitive(JavaPrimitiveType primitive, Object value) {
    return switch (primitive) {
      case BYTE -> {
        int integer = ((Number) value).intValue();
        if (integer < Byte.MIN_VALUE || integer > Byte.MAX_VALUE) {
          throw new JarBindingRuntimeException("JAR byte argument is out of range");
        }
        yield (byte) integer;
      }
      case SHORT -> {
        int integer = ((Number) value).intValue();
        if (integer < Short.MIN_VALUE || integer > Short.MAX_VALUE) {
          throw new JarBindingRuntimeException("JAR short argument is out of range");
        }
        yield (short) integer;
      }
      case CHAR -> {
        int codePoint = ((Number) value).intValue();
        if (codePoint < Character.MIN_VALUE || codePoint > Character.MAX_VALUE) {
          throw new JarBindingRuntimeException("JAR char argument is out of range");
        }
        yield (char) codePoint;
      }
      case INT -> ((Number) value).intValue();
      case LONG -> ((Number) value).longValue();
      case FLOAT -> ((Number) value).floatValue();
      case DOUBLE -> ((Number) value).doubleValue();
      case BOOLEAN, VOID -> value;
    };
  }

  private static Conversion<JarBindingResult> resultAdapter(JavaBindingType type) {
    boolean returnsVoid = type == JavaPrimitiveType.VOID;
    Conversion<JarBindingResult> conversion =
        switch (type) {
          case JavaArrayType array -> {
            var name = array.displayName();
            yield (classes, value) -> new JarBindingResult.Reference(value, name);
          }
          case JavaPrimitiveType primitive ->
              switch (primitive) {
                case BYTE ->
                    (classes, value) -> new JarBindingResult.Scalar(((Byte) value).intValue());
                case SHORT ->
                    (classes, value) -> new JarBindingResult.Scalar(((Short) value).intValue());
                case CHAR ->
                    (classes, value) ->
                        new JarBindingResult.Scalar((int) ((Character) value).charValue());
                case VOID -> (classes, value) -> JarBindingResult.Void.INSTANCE;
                default -> (classes, value) -> new JarBindingResult.Scalar(value);
              };
          case JavaBoxedType boxed -> (classes, value) -> boxedResult(boxed, value);
          case JavaBindingTypeVariable variable -> {
            var name = variable.displayName();
            yield (classes, value) ->
                value instanceof AutoCloseable resource
                    ? new JarBindingResult.ResourceReference(
                        resource, name, classes.nominalReferences(value.getClass()))
                    : dynamicResult(value);
          }
          case JavaCallbackType ignored ->
              (classes, value) -> {
                throw new JarBindingRuntimeException("Java callback return values are unsupported");
              };
          case JavaReferenceType reference -> {
            var name = reference.displayName();
            var binaryName = reference.binaryName();
            yield switch (reference.kind()) {
              case EXCEPTION ->
                  (classes, value) -> new JarBindingResult.ExceptionReference((Throwable) value);
              case RESOURCE ->
                  (classes, value) ->
                      new JarBindingResult.ResourceReference(
                          (AutoCloseable) value, name, classes.nominalReferences(value.getClass()));
              case INPUT_STREAM, OUTPUT_STREAM ->
                  (classes, value) ->
                      new JarBindingResult.ResourceReference((AutoCloseable) value, name);
              case TASK -> {
                var element = resultAdapter(optionalElement(reference));
                yield (classes, value) ->
                    new JarBindingResult.ResourceReference(task(classes, element, value), name);
              }
              case PUBLISHER -> (classes, value) -> new JarBindingResult.Reference(value, name);
              case PATH ->
                  (classes, value) ->
                      new JarBindingResult.PathValue(((java.nio.file.Path) value).toString());
              case FILE ->
                  (classes, value) ->
                      new JarBindingResult.PathValue(((java.io.File) value).getPath());
              case URI -> (classes, value) -> new JarBindingResult.UriValue(value.toString());
              case DURATION ->
                  (classes, value) -> {
                    java.time.Duration duration = (java.time.Duration) value;
                    return new JarBindingResult.DurationValue(
                        duration.getSeconds(), duration.getNano());
                  };
              case CLASS ->
                  (classes, value) ->
                      new JarBindingResult.ClassReference(classes.references((Class<?>) value));
              case ENUM ->
                  (classes, value) ->
                      new JarBindingResult.EnumReference(classes.reference((Enum<?>) value));
              case OPTIONAL -> {
                var element = resultAdapter(optionalElement(reference));
                yield (classes, value) -> {
                  java.util.Optional<?> optional = (java.util.Optional<?>) value;
                  return optional.isEmpty()
                      ? JarBindingResult.Null.INSTANCE
                      : element.apply(classes, optional.get());
                };
              }
              case OPTIONAL_INT ->
                  (classes, value) -> {
                    java.util.OptionalInt optional = (java.util.OptionalInt) value;
                    return optional.isEmpty()
                        ? JarBindingResult.Null.INSTANCE
                        : new JarBindingResult.Scalar(optional.getAsInt());
                  };
              case OPTIONAL_LONG ->
                  (classes, value) -> {
                    java.util.OptionalLong optional = (java.util.OptionalLong) value;
                    return optional.isEmpty()
                        ? JarBindingResult.Null.INSTANCE
                        : new JarBindingResult.Scalar(optional.getAsLong());
                  };
              case OPTIONAL_DOUBLE ->
                  (classes, value) -> {
                    java.util.OptionalDouble optional = (java.util.OptionalDouble) value;
                    return optional.isEmpty()
                        ? JarBindingResult.Null.INSTANCE
                        : new JarBindingResult.Scalar(optional.getAsDouble());
                  };
              case ITERABLE, ITERATOR, COLLECTION, LIST, SET, MAP, OPAQUE ->
                  (classes, value) ->
                      new JarBindingResult.Reference(
                          value, name, classes.nominalReferences(binaryName));
              case OBJECT -> (classes, value) -> dynamicResult(value);
              case CHAR_SEQUENCE ->
                  (classes, value) -> new JarBindingResult.Scalar(value.toString());
              case CHARSET ->
                  (classes, value) ->
                      new JarBindingResult.Scalar(((java.nio.charset.Charset) value).name());
              case NUMBER, STRING -> (classes, value) -> new JarBindingResult.Scalar(value);
              case UNIT -> (classes, value) -> JarBindingResult.Null.INSTANCE;
            };
          }
        };
    return (classes, value) ->
        value == null
            ? (returnsVoid ? JarBindingResult.Void.INSTANCE : JarBindingResult.Null.INSTANCE)
            : conversion.apply(classes, value);
  }

  private static JarBindingResult boxedResult(JavaBoxedType boxed, Object value) {
    return switch (boxed.primitive()) {
      case BYTE -> new JarBindingResult.Scalar(((Byte) value).intValue());
      case SHORT -> new JarBindingResult.Scalar(((Short) value).intValue());
      case CHAR -> new JarBindingResult.Scalar((int) ((Character) value).charValue());
      case BOOLEAN, INT, LONG, FLOAT, DOUBLE -> new JarBindingResult.Scalar(value);
      case VOID -> throw new IllegalStateException("Void cannot be boxed");
    };
  }

  private static JavaBindingType optionalElement(JavaReferenceType optional) {
    if (optional.arguments().isEmpty()
        || optional.arguments().getFirst().variance() == JavaTypeVariance.UNBOUNDED) {
      return new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    }
    return optional.arguments().getFirst().type().orElseThrow();
  }

  private static Object uriArgument(String binaryName, JarBindingUri value) {
    java.net.URI uri = java.net.URI.create(value.value());
    if (binaryName.equals("java.net.URI")) return uri;
    try {
      return uri.toURL();
    } catch (java.net.MalformedURLException failure) {
      throw new JarBindingRuntimeException("invalid Java URL " + value.value(), failure);
    }
  }

  private static JarBindingTask task(
      ClassCatalog classes, Conversion<JarBindingResult> element, Object value) {
    Future<?> future =
        value instanceof Future<?> candidate
            ? candidate
            : ((CompletionStage<?>) value).toCompletableFuture();
    return new JarBindingTask() {
      @Override
      public JarBindingResult await() {
        try {
          return element.apply(classes, future.get());
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new JarBindingInvocationException("Java task await was interrupted", failure);
        } catch (ExecutionException failure) {
          Throwable cause = failure.getCause() == null ? failure : failure.getCause();
          while (cause instanceof java.util.concurrent.CompletionException completion
              && completion.getCause() != null) {
            cause = completion.getCause();
          }
          if (cause instanceof JarBindingCallbackException callback) throw callback.failure();
          throw new JarBindingInvocationException("Java task completed exceptionally", cause);
        } catch (CancellationException failure) {
          throw new JarBindingInvocationException("Java task was cancelled", failure);
        }
      }

      @Override
      public boolean cancel() {
        return future.cancel(true);
      }

      @Override
      public boolean completed() {
        return future.isDone();
      }

      @Override
      public Object hostValue() {
        return value;
      }

      @Override
      public void close() {
        if (!future.isDone()) future.cancel(true);
      }
    };
  }

  private static JarBindingResult dynamicResult(Object value) {
    if (value instanceof Byte number) return new JarBindingResult.Scalar(number.intValue());
    if (value instanceof Short number) return new JarBindingResult.Scalar(number.intValue());
    if (value instanceof Character character) {
      return new JarBindingResult.Scalar((int) character.charValue());
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return new JarBindingResult.Scalar(value);
    }
    return new JarBindingResult.Reference(value, value.getClass().getName());
  }

  private static JavaDirectCall bind(ClassLoader loader, JavaBindingCallable callable) {
    try {
      if (callable.kind() == JavaCallableKind.ARRAY_CONSTRUCTOR
          || callable.kind() == JavaCallableKind.ARRAY_LENGTH
          || callable.kind() == JavaCallableKind.ARRAY_GET
          || callable.kind() == JavaCallableKind.ARRAY_SET) {
        Class<?> array = arrayClass(callable, loader);
        MethodHandle handle =
            switch (callable.kind()) {
              case ARRAY_CONSTRUCTOR -> MethodHandles.arrayConstructor(array);
              case ARRAY_LENGTH -> MethodHandles.arrayLength(array);
              case ARRAY_GET -> MethodHandles.arrayElementGetter(array);
              case ARRAY_SET -> MethodHandles.arrayElementSetter(array);
              default -> throw new IllegalStateException("not an array binding");
            };
        return handle.asFixedArity()::invokeWithArguments;
      }
      Class<?> owner = Class.forName(callable.owner(), false, loader);
      MethodHandle handle =
          switch (callable.kind()) {
            case ARRAY_CONSTRUCTOR, ARRAY_LENGTH, ARRAY_GET, ARRAY_SET ->
                throw new IllegalStateException("array binding was not linked");
            case CONSTRUCTOR ->
                MethodHandles.publicLookup()
                    .findConstructor(
                        owner,
                        MethodType.fromMethodDescriptorString(callable.descriptor(), loader)
                            .changeReturnType(void.class));
            case STATIC_METHOD ->
                MethodHandles.publicLookup()
                    .findStatic(
                        owner,
                        callable.name(),
                        MethodType.fromMethodDescriptorString(callable.descriptor(), loader));
            case INSTANCE_METHOD ->
                MethodHandles.publicLookup()
                    .findVirtual(
                        owner,
                        callable.name(),
                        MethodType.fromMethodDescriptorString(callable.descriptor(), loader));
            case STATIC_FIELD_GET ->
                MethodHandles.publicLookup()
                    .findStaticGetter(owner, callable.name(), fieldType(callable, loader));
            case STATIC_FIELD_SET ->
                MethodHandles.publicLookup()
                    .findStaticSetter(owner, callable.name(), fieldType(callable, loader));
            case INSTANCE_FIELD_GET ->
                MethodHandles.publicLookup()
                    .findGetter(owner, callable.name(), fieldType(callable, loader));
            case INSTANCE_FIELD_SET ->
                MethodHandles.publicLookup()
                    .findSetter(owner, callable.name(), fieldType(callable, loader));
          };
      return handle.asFixedArity()::invokeWithArguments;
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | NoSuchFieldException
        | IllegalAccessException exception) {
      throw new JarBindingRuntimeException(
          "cannot link JAR binding call "
              + callable.owner()
              + "."
              + callable.name()
              + callable.descriptor(),
          exception);
    }
  }

  private static Class<?> fieldType(JavaBindingCallable callable, ClassLoader loader) {
    return MethodType.fromMethodDescriptorString("()" + callable.descriptor(), loader).returnType();
  }

  private static Class<?> arrayClass(JavaBindingCallable callable, ClassLoader loader) {
    return MethodType.fromMethodDescriptorString("()" + callable.owner(), loader).returnType();
  }

  @Override
  public void close() {
    ClassLoader loader = applicationLoader;
    applicationLoader = null;
    applicationCalls = Map.of();
    calls.clear();
    if (ownsApplicationLoader) retire((URLClassLoader) loader);
  }

  private static void retire(URLClassLoader loader) {
    if (loader == null) return;
    long deadline = System.nanoTime() + CLASS_LOADER_RETIREMENT_TIMEOUT.toNanos();
    boolean interrupted = false;
    List<Thread> remaining = applicationThreads(loader);
    while (!remaining.isEmpty() && System.nanoTime() < deadline) {
      for (Thread thread : remaining) {
        long wait = deadline - System.nanoTime();
        if (wait <= 0) break;
        try {
          thread.join(Duration.ofNanos(wait));
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
      remaining = applicationThreads(loader);
    }
    close(loader);
    if (interrupted) Thread.currentThread().interrupt();
  }

  private static List<Thread> applicationThreads(ClassLoader loader) {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(Thread::isAlive)
        .filter(thread -> thread != Thread.currentThread())
        .filter(thread -> thread.getContextClassLoader() == loader)
        .toList();
  }

  private static void close(URLClassLoader loader) {
    try {
      loader.close();
    } catch (IOException exception) {
      throw new JarBindingRuntimeException("cannot close JAR binding runtime", exception);
    }
  }

  private static final class ApplicationClassLoader extends URLClassLoader {
    private static final List<String> PARENT_PACKAGES =
        List.of(
            "java.",
            "jdk.",
            "sun.",
            "com.sun.",
            "dev.w0fv1.norm.bridge.",
            "org.junit.",
            "org.opentest4j.",
            "org.apiguardian.");

    private ApplicationClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> type = findLoadedClass(name);
        if (type == null) {
          if (parentFirst(name)) {
            type = super.loadClass(name, false);
          } else {
            try {
              type = findClass(name);
            } catch (ClassNotFoundException ignored) {
              type = super.loadClass(name, false);
            }
          }
        }
        if (resolve) resolveClass(type);
        return type;
      }
    }

    @Override
    public URL getResource(String name) {
      URL resource = findResource(name);
      return resource == null ? super.getResource(name) : resource;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      LinkedHashSet<URL> resources = new LinkedHashSet<>();
      resources.addAll(Collections.list(findResources(name)));
      resources.addAll(Collections.list(getParent().getResources(name)));
      return Collections.enumeration(resources);
    }

    private static boolean parentFirst(String name) {
      return PARENT_PACKAGES.stream().anyMatch(name::startsWith);
    }
  }

  private static final class ClassCatalog {
    private final ClassLoader loader;
    private final Map<JarBindingClassReference, Class<?>> classes;
    private final Map<Class<?>, List<JarBindingClassReference>> references;
    private final Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants;

    private ClassCatalog(ClassLoader loader, LinkedJavaClasses linkedClasses) {
      this.loader = loader;
      var indexedClasses = linkedClasses.classes();
      classes = new LinkedHashMap<>(indexedClasses);
      Map<Class<?>, List<JarBindingClassReference>> indexedReferences = new LinkedHashMap<>();
      indexedClasses.forEach(
          (reference, type) ->
              indexedReferences.computeIfAbsent(type, ignored -> new ArrayList<>()).add(reference));
      Map<Class<?>, List<JarBindingClassReference>> stableReferences = new LinkedHashMap<>();
      indexedReferences.forEach((type, values) -> stableReferences.put(type, List.copyOf(values)));
      references = new LinkedHashMap<>(stableReferences);
      this.enumConstants = linkedClasses.enumConstants();
    }

    private ClassLoader loader() {
      return loader;
    }

    private Class<?> load(String binaryName) {
      try {
        return Class.forName(binaryName, false, loader);
      } catch (ClassNotFoundException exception) {
        throw new JarBindingRuntimeException("Java class is unavailable: " + binaryName, exception);
      }
    }

    private synchronized Class<?> resolve(JarBindingClassReference reference) {
      Class<?> type = classes.get(reference);
      if (type == null && reference instanceof JarBindingClassReference.Nominal nominal) {
        String binaryName =
            JavaApplicationTypeName.packageName(nominal.packageName()) + "." + nominal.name();
        try {
          type = Class.forName(binaryName, false, loader);
          classes.put(reference, type);
          List<JarBindingClassReference> candidates =
              new ArrayList<>(references.getOrDefault(type, List.of()));
          candidates.add(reference);
          references.put(type, List.copyOf(candidates));
        } catch (ClassNotFoundException ignored) {
          type = null;
        }
      }
      if (type == null) {
        throw new JarBindingRuntimeException("Norm class has no Java mapping: " + reference);
      }
      return type;
    }

    private synchronized List<JarBindingClassReference> references(Class<?> type) {
      List<JarBindingClassReference> candidates = references.get(type);
      if (candidates == null) {
        throw new JarBindingRuntimeException(
            "Java class has no Norm mapping: " + type.getTypeName());
      }
      return candidates;
    }

    private List<JarBindingClassReference.Nominal> nominalReferences(String binaryName) {
      return references(load(binaryName)).stream()
          .filter(JarBindingClassReference.Nominal.class::isInstance)
          .map(JarBindingClassReference.Nominal.class::cast)
          .toList();
    }

    private synchronized List<JarBindingClassReference.Nominal> nominalReferences(
        Class<?> runtimeType) {
      return references.entrySet().stream()
          .filter(entry -> entry.getKey().isAssignableFrom(runtimeType))
          .flatMap(entry -> entry.getValue().stream())
          .filter(JarBindingClassReference.Nominal.class::isInstance)
          .map(JarBindingClassReference.Nominal.class::cast)
          .distinct()
          .toList();
    }

    private Object resolve(JarBindingEnumValue value) {
      Class<?> type = resolve(value.type());
      if (!type.isEnum()) {
        throw new JarBindingRuntimeException(
            "Norm enum maps to a non-enum Java class: " + value.type());
      }
      String constant = enumConstants.getOrDefault(value.type(), Map.of()).get(value.variant());
      if (constant == null) {
        throw new JarBindingRuntimeException(
            "Norm enum variant has no Java mapping: " + value.type() + "." + value.variant());
      }
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object result = Enum.valueOf((Class) type, constant);
      return result;
    }

    private JarBindingEnumValue reference(Enum<?> value) {
      List<JarBindingClassReference.Nominal> candidates =
          references(value.getDeclaringClass()).stream()
              .filter(JarBindingClassReference.Nominal.class::isInstance)
              .map(JarBindingClassReference.Nominal.class::cast)
              .filter(enumConstants::containsKey)
              .toList();
      if (candidates.size() != 1) {
        throw new JarBindingRuntimeException(
            "Java enum does not map to exactly one Norm declaration: "
                + value.getDeclaringClass().getTypeName());
      }
      JarBindingClassReference.Nominal type = candidates.getFirst();
      String variant =
          enumConstants.get(type).entrySet().stream()
              .filter(entry -> entry.getValue().equals(value.name()))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElseThrow(
                  () ->
                      new JarBindingRuntimeException(
                          "Java enum constant has no Norm variant: "
                              + value.getDeclaringClass().getTypeName()
                              + "."
                              + value.name()));
      return new JarBindingEnumValue(type, variant);
    }
  }

  public static final class LinkedCalls {
    private final Map<String, LinkedCall> calls;

    private LinkedCalls(Map<String, LinkedCall> calls) {
      this.calls = Map.copyOf(calls);
    }
  }

  private static LinkedCall prepareCall(JavaBindingCallable callable, JavaDirectCall target) {
    return new LinkedCall(
        callable.owner() + "." + callable.name() + callable.descriptor(),
        callable.kind().requiresReceiver(),
        callable.kind() == JavaCallableKind.INSTANCE_METHOD
            && callable.name().equals("close")
            && callable.descriptor().equals("()V"),
        callable.parameters().stream().map(JvmJarBindingRuntime::argumentAdapter).toList(),
        resultAdapter(callable.returnType()),
        target);
  }

  @FunctionalInterface
  private interface Conversion<T> {
    T apply(ClassCatalog classes, Object value);
  }

  private record LinkedCall(
      String description,
      boolean receiver,
      boolean closesResource,
      List<Conversion<Object>> parameters,
      Conversion<JarBindingResult> result,
      JavaDirectCall target) {}

  private record BoundCall(LinkedCall callable, ClassCatalog classes) {}
}
