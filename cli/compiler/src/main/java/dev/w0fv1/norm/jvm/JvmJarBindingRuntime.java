package dev.w0fv1.norm.jvm;

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
import java.util.ArrayList;
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
  private final List<URLClassLoader> loaders;
  private final Map<String, BoundCall> calls;
  private URLClassLoader applicationLoader;

  public JvmJarBindingRuntime(List<ResolvedJarBinding> bindings) {
    this(bindings, List.of());
  }

  public JvmJarBindingRuntime(List<ResolvedJarBinding> bindings, List<Path> applicationClasspath) {
    loaders = new ArrayList<>();
    calls = new LinkedHashMap<>();
    try {
      Set<Path> paths = new LinkedHashSet<>();
      applicationClasspath.stream()
          .map(Path::toAbsolutePath)
          .map(Path::normalize)
          .forEach(paths::add);
      ResolvedJarClasspath.resolve(bindings.stream().map(ResolvedJarBinding::graph).toList())
          .forEach(paths::add);
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
      URLClassLoader loader = new URLClassLoader(urls, JvmJarBindingRuntime.class.getClassLoader());
      applicationLoader = loader;
      loaders.add(loader);
      Map<JarBindingClassReference.Nominal, String> classDescriptors = new LinkedHashMap<>();
      Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants =
          new LinkedHashMap<>();
      for (ResolvedJarBinding binding : bindings) {
        binding
            .generated()
            .classDescriptors()
            .forEach(
                (reference, descriptor) -> {
                  if (classDescriptors.putIfAbsent(reference, descriptor) != null) {
                    throw new JarBindingRuntimeException(
                        "duplicate Norm class mapping " + reference);
                  }
                });
        binding
            .generated()
            .enumConstants()
            .forEach(
                (reference, constants) -> {
                  if (enumConstants.putIfAbsent(reference, constants) != null) {
                    throw new JarBindingRuntimeException(
                        "duplicate Norm enum mapping " + reference);
                  }
                });
      }
      ClassCatalog classes = new ClassCatalog(loader, classDescriptors, enumConstants);
      for (ResolvedJarBinding binding : bindings) add(binding, loader, classes);
    } catch (IllegalArgumentException exception) {
      close();
      throw new JarBindingRuntimeException(exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      close();
      throw exception;
    }
  }

  @Override
  public ClassLoader applicationClassLoader() {
    if (applicationLoader == null) {
      throw new JarBindingRuntimeException("JAR binding runtime is closed");
    }
    return applicationLoader;
  }

  @Override
  public JarBindingResult invoke(String callId, List<Object> arguments) {
    BoundCall call = calls.get(callId);
    if (call == null) throw new JarBindingRuntimeException("unknown JAR binding call " + callId);
    int receiverCount = call.callable().kind().requiresReceiver() ? 1 : 0;
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
      List<Object> adapted = new ArrayList<>(arguments.size());
      if (receiverCount == 1) {
        Object receiver = arguments.getFirst();
        adapted.add(
            receiver instanceof JarBindingEnumValue enumValue
                ? call.classes().resolve(enumValue)
                : receiver);
      }
      for (int index = 0; index < call.callable().parameters().size(); index++) {
        adapted.add(
            adaptArgument(
                call.classes(),
                call.callable().parameters().get(index),
                arguments.get(index + receiverCount)));
      }
      Object value = call.handle().invokeWithArguments(adapted);
      if (resourceClose(call.callable(), adapted)) return JarBindingResult.ResourceClosed.INSTANCE;
      return adaptResult(call.classes(), call.callable().returnType(), value);
    } catch (JarBindingCallbackException exception) {
      throw exception.failure();
    } catch (JarBindingRuntimeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new JarBindingInvocationException(
          "JAR binding call failed: "
              + call.callable().owner()
              + "."
              + call.callable().name()
              + call.callable().descriptor(),
          throwable);
    } finally {
      thread.setContextClassLoader(previousContextLoader);
    }
  }

  private static Object adaptArgument(ClassCatalog classes, JavaBindingType type, Object value) {
    if (type instanceof JavaReferenceType reference) {
      switch (reference.kind()) {
        case OPTIONAL -> {
          return value == null
              ? java.util.Optional.empty()
              : java.util.Optional.of(adaptArgument(classes, optionalElement(reference), value));
        }
        case OPTIONAL_INT -> {
          return value == null
              ? java.util.OptionalInt.empty()
              : java.util.OptionalInt.of(((Number) value).intValue());
        }
        case OPTIONAL_LONG -> {
          return value == null
              ? java.util.OptionalLong.empty()
              : java.util.OptionalLong.of(((Number) value).longValue());
        }
        case OPTIONAL_DOUBLE -> {
          return value == null
              ? java.util.OptionalDouble.empty()
              : java.util.OptionalDouble.of(((Number) value).doubleValue());
        }
        default -> {}
      }
    }
    if (value == null) return null;
    return switch (type) {
      case JavaArrayType ignored -> value;
      case JavaPrimitiveType primitive -> adaptPrimitive(primitive, value);
      case JavaBoxedType boxed -> value == null ? null : adaptPrimitive(boxed.primitive(), value);
      case JavaBindingTypeVariable ignored -> value;
      case JavaCallbackType callback -> adaptCallback(classes, callback, value);
      case JavaReferenceType reference ->
          switch (reference.kind()) {
            case PATH -> java.nio.file.Path.of(((JarBindingPath) value).value());
            case FILE -> new java.io.File(((JarBindingPath) value).value());
            case CHARSET -> java.nio.charset.Charset.forName((String) value);
            case CLASS -> classes.resolve((JarBindingClassReference) value);
            case ENUM -> classes.resolve((JarBindingEnumValue) value);
            case URI -> uriArgument(reference, (JarBindingUri) value);
            case DURATION -> {
              JarBindingDuration duration = (JarBindingDuration) value;
              yield java.time.Duration.ofSeconds(duration.seconds(), duration.nanoseconds());
            }
            case TASK -> {
              if (!(value instanceof JarBindingTask task)) {
                throw new JarBindingRuntimeException("JAR task argument is not a Norm Task");
              }
              yield task.hostValue();
            }
            case PUBLISHER -> value;
            case UNIT -> null;
            case ITERABLE, ITERATOR, COLLECTION, LIST, SET, MAP -> value;
            case OPTIONAL, OPTIONAL_INT, OPTIONAL_LONG, OPTIONAL_DOUBLE ->
                throw new IllegalStateException("Optional argument was not adapted");
            case EXCEPTION,
                INPUT_STREAM,
                NUMBER,
                OBJECT,
                OPAQUE,
                OUTPUT_STREAM,
                RESOURCE,
                STRING,
                CHAR_SEQUENCE ->
                value;
          };
    };
  }

  private static Object adaptCallback(ClassCatalog classes, JavaCallbackType type, Object value) {
    if (!(value instanceof JarBindingCallback callback)) {
      throw new JarBindingRuntimeException("JAR callback argument is not a Norm function");
    }
    Class<?> callbackInterface = classes.load(type.binaryName());
    if (!callbackInterface.isInterface()) {
      throw new JarBindingRuntimeException(
          "Java callback type is not an interface: " + type.binaryName());
    }
    return Proxy.newProxyInstance(
        classes.loader(),
        new Class<?>[] {callbackInterface},
        (proxy, method, arguments) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "equals" -> proxy == arguments[0];
              case "hashCode" -> System.identityHashCode(proxy);
              case "toString" -> "Norm function as " + type.binaryName();
              default -> throw new IllegalStateException("unexpected Object method " + method);
            };
          }
          if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
          if (!method.getName().equals(type.methodName())) {
            throw new JarBindingRuntimeException(
                "unexpected Java callback method " + type.binaryName() + "." + method.getName());
          }
          Object[] values = arguments == null ? new Object[0] : arguments;
          if (values.length != type.parameters().size()) {
            throw new JarBindingRuntimeException(
                "Java callback expected "
                    + type.parameters().size()
                    + " arguments but received "
                    + values.length);
          }
          List<JarBindingResult> adapted = new ArrayList<>(values.length);
          for (int index = 0; index < values.length; index++) {
            adapted.add(adaptResult(classes, type.parameters().get(index), values[index]));
          }
          Object result = callback.invoke(adapted);
          return type.returnType() == JavaPrimitiveType.VOID
              ? null
              : adaptArgument(classes, type.returnType(), result);
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

  private static JarBindingResult adaptResult(
      ClassCatalog classes, JavaBindingType type, Object value) {
    if (value == null) {
      return type == JavaPrimitiveType.VOID
          ? JarBindingResult.Void.INSTANCE
          : JarBindingResult.Null.INSTANCE;
    }
    return switch (type) {
      case JavaArrayType array -> new JarBindingResult.Reference(value, array.displayName());
      case JavaPrimitiveType primitive ->
          switch (primitive) {
            case BYTE -> new JarBindingResult.Scalar(((Byte) value).intValue());
            case SHORT -> new JarBindingResult.Scalar(((Short) value).intValue());
            case CHAR -> new JarBindingResult.Scalar((int) ((Character) value).charValue());
            case VOID -> JarBindingResult.Void.INSTANCE;
            default -> new JarBindingResult.Scalar(value);
          };
      case JavaBoxedType boxed -> boxedResult(boxed, value);
      case JavaBindingTypeVariable variable -> {
        if (value instanceof AutoCloseable resource) {
          yield new JarBindingResult.ResourceReference(
              resource, variable.displayName(), classes.nominalReferences(value.getClass()));
        }
        yield dynamicResult(value);
      }
      case JavaCallbackType ignored ->
          throw new JarBindingRuntimeException("Java callback return values are unsupported");
      case JavaReferenceType reference ->
          switch (reference.kind()) {
            case EXCEPTION -> new JarBindingResult.ExceptionReference((Throwable) value);
            case RESOURCE ->
                new JarBindingResult.ResourceReference(
                    (AutoCloseable) value,
                    reference.displayName(),
                    classes.nominalReferences(value.getClass()));
            case INPUT_STREAM, OUTPUT_STREAM ->
                new JarBindingResult.ResourceReference(
                    (AutoCloseable) value, reference.displayName());
            case TASK ->
                new JarBindingResult.ResourceReference(
                    task(classes, reference, value), reference.displayName());
            case PUBLISHER -> new JarBindingResult.Reference(value, reference.displayName());
            case PATH -> new JarBindingResult.PathValue(((java.nio.file.Path) value).toString());
            case FILE -> new JarBindingResult.PathValue(((java.io.File) value).getPath());
            case URI -> new JarBindingResult.UriValue(value.toString());
            case DURATION -> {
              java.time.Duration duration = (java.time.Duration) value;
              yield new JarBindingResult.DurationValue(duration.getSeconds(), duration.getNano());
            }
            case CLASS -> new JarBindingResult.ClassReference(classes.references((Class<?>) value));
            case ENUM -> new JarBindingResult.EnumReference(classes.reference((Enum<?>) value));
            case OPTIONAL -> {
              java.util.Optional<?> optional = (java.util.Optional<?>) value;
              yield optional.isEmpty()
                  ? JarBindingResult.Null.INSTANCE
                  : adaptResult(classes, optionalElement(reference), optional.get());
            }
            case OPTIONAL_INT -> {
              java.util.OptionalInt optional = (java.util.OptionalInt) value;
              yield optional.isEmpty()
                  ? JarBindingResult.Null.INSTANCE
                  : new JarBindingResult.Scalar(optional.getAsInt());
            }
            case OPTIONAL_LONG -> {
              java.util.OptionalLong optional = (java.util.OptionalLong) value;
              yield optional.isEmpty()
                  ? JarBindingResult.Null.INSTANCE
                  : new JarBindingResult.Scalar(optional.getAsLong());
            }
            case OPTIONAL_DOUBLE -> {
              java.util.OptionalDouble optional = (java.util.OptionalDouble) value;
              yield optional.isEmpty()
                  ? JarBindingResult.Null.INSTANCE
                  : new JarBindingResult.Scalar(optional.getAsDouble());
            }
            case ITERABLE, ITERATOR, COLLECTION, LIST, SET, MAP ->
                new JarBindingResult.Reference(
                    value,
                    reference.displayName(),
                    classes.nominalReferences(reference.binaryName()));
            case OPAQUE ->
                new JarBindingResult.Reference(
                    value,
                    reference.displayName(),
                    classes.nominalReferences(reference.binaryName()));
            case OBJECT -> dynamicResult(value);
            case CHAR_SEQUENCE -> new JarBindingResult.Scalar(value.toString());
            case CHARSET -> new JarBindingResult.Scalar(((java.nio.charset.Charset) value).name());
            case NUMBER, STRING -> new JarBindingResult.Scalar(value);
            case UNIT -> JarBindingResult.Null.INSTANCE;
          };
    };
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

  private static Object uriArgument(JavaReferenceType reference, JarBindingUri value) {
    java.net.URI uri = java.net.URI.create(value.value());
    if (reference.binaryName().equals("java.net.URI")) return uri;
    try {
      return uri.toURL();
    } catch (java.net.MalformedURLException failure) {
      throw new JarBindingRuntimeException("invalid Java URL " + value.value(), failure);
    }
  }

  private static JarBindingTask task(
      ClassCatalog classes, JavaReferenceType reference, Object value) {
    Future<?> future =
        value instanceof Future<?> candidate
            ? candidate
            : ((CompletionStage<?>) value).toCompletableFuture();
    JavaBindingType element = optionalElement(reference);
    return new JarBindingTask() {
      @Override
      public JarBindingResult await() {
        try {
          return adaptResult(classes, element, future.get());
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

  private static boolean resourceClose(JavaBindingCallable callable, List<Object> arguments) {
    return callable.kind() == JavaCallableKind.INSTANCE_METHOD
        && callable.name().equals("close")
        && callable.descriptor().equals("()V")
        && !arguments.isEmpty()
        && arguments.getFirst() instanceof AutoCloseable;
  }

  private void add(ResolvedJarBinding binding, URLClassLoader loader, ClassCatalog classes) {
    for (Map.Entry<String, JavaBindingCallable> entry : binding.generated().calls().entrySet()) {
      BoundCall call = bind(loader, classes, entry.getValue());
      if (calls.putIfAbsent(entry.getKey(), call) != null) {
        throw new JarBindingRuntimeException("duplicate JAR binding call " + entry.getKey());
      }
    }
  }

  private static BoundCall bind(
      ClassLoader loader, ClassCatalog classes, JavaBindingCallable callable) {
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
        return new BoundCall(callable, handle.asFixedArity(), classes);
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
      return new BoundCall(callable, handle.asFixedArity(), classes);
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
    JarBindingRuntimeException failure = null;
    for (URLClassLoader loader : loaders) {
      try {
        loader.close();
      } catch (IOException exception) {
        if (failure == null) {
          failure = new JarBindingRuntimeException("cannot close JAR binding runtime", exception);
        } else {
          failure.addSuppressed(exception);
        }
      }
    }
    loaders.clear();
    applicationLoader = null;
    calls.clear();
    if (failure != null) throw failure;
  }

  private static final class ClassCatalog {
    private final ClassLoader loader;
    private final Map<JarBindingClassReference, Class<?>> classes;
    private final Map<Class<?>, List<JarBindingClassReference>> references;
    private final Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants;

    private ClassCatalog(
        ClassLoader loader,
        Map<JarBindingClassReference.Nominal, String> generatedClassDescriptors,
        Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants) {
      this.loader = loader;
      Map<JarBindingClassReference, Class<?>> indexedClasses = new LinkedHashMap<>();
      for (Map.Entry<JarBindingClassReference, String> entry :
          JavaPlatformTypes.classDescriptors().entrySet()) {
        try {
          indexedClasses.put(entry.getKey(), descriptorClass(entry.getValue(), loader));
        } catch (TypeNotPresentException ignored) {
          continue;
        }
      }
      generatedClassDescriptors.forEach(
          (reference, descriptor) -> {
            if (indexedClasses.putIfAbsent(reference, descriptorClass(descriptor, loader))
                != null) {
              throw new JarBindingRuntimeException("duplicate Norm class mapping " + reference);
            }
          });
      classes = new LinkedHashMap<>(indexedClasses);
      Map<Class<?>, List<JarBindingClassReference>> indexedReferences = new LinkedHashMap<>();
      indexedClasses.forEach(
          (reference, type) ->
              indexedReferences.computeIfAbsent(type, ignored -> new ArrayList<>()).add(reference));
      Map<Class<?>, List<JarBindingClassReference>> stableReferences = new LinkedHashMap<>();
      indexedReferences.forEach((type, values) -> stableReferences.put(type, List.copyOf(values)));
      references = new LinkedHashMap<>(stableReferences);
      this.enumConstants = enumConstants;
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
            nominal.packageName().isEmpty()
                ? nominal.name()
                : nominal.packageName() + "." + nominal.name();
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

    private static Class<?> descriptorClass(String descriptor, ClassLoader loader) {
      try {
        return MethodType.fromMethodDescriptorString("()" + descriptor, loader).returnType();
      } catch (IllegalArgumentException exception) {
        throw new JarBindingRuntimeException(
            "invalid Java class descriptor " + descriptor, exception);
      }
    }
  }

  private record BoundCall(
      JavaBindingCallable callable, MethodHandle handle, ClassCatalog classes) {}
}
