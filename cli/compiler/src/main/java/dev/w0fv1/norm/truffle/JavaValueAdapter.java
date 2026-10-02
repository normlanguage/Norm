package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.execution.JarBindingCallback;
import dev.w0fv1.norm.execution.JarBindingCallbackException;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import java.util.List;

final class JavaValueAdapter {
  private JavaValueAdapter() {}

  static Object jarArgument(Object value, ExecutionState execution) {
    return jarArgument(value, execution, null);
  }

  static Object jarArgument(Object value, ExecutionState execution, AnnotationRuntime annotations) {
    if (value == RuntimeValues.NullValue.INSTANCE) return null;
    if (value instanceof RuntimeValues.Closure closure) {
      if (execution == null) {
        throw new IllegalStateException("JAR callback execution is unavailable");
      }
      CoreType concrete = nonNullable(closure.functionType());
      if (!(concrete instanceof CoreType.Function function)) {
        throw new IllegalStateException("JAR callback value has no function type");
      }
      return new JarBindingCallback() {
        @Override
        public Object identity() {
          return closure;
        }

        @Override
        public Object invoke(List<JarBindingResult> arguments) {
          return execution
              .callbacks()
              .invoke(
                  () -> {
                    try {
                      if (arguments.size() != function.parameterTypes().size()) {
                        throw new IllegalStateException(
                            "JAR callback expected "
                                + function.parameterTypes().size()
                                + " arguments but received "
                                + arguments.size());
                      }
                      Object[] values = new Object[arguments.size()];
                      for (int index = 0; index < values.length; index++) {
                        values[index] =
                            jarBindingValue(
                                function.parameterTypes().get(index),
                                arguments.get(index),
                                annotations,
                                execution,
                                null);
                      }
                      return jarArgument(
                          RuntimeInvocation.invoke(execution, closure, values),
                          execution,
                          annotations);
                    } catch (JarBindingCallbackException exception) {
                      throw exception;
                    } catch (RuntimeException exception) {
                      throw new JarBindingCallbackException(exception);
                    }
                  });
        }
      };
    }
    if (value instanceof RuntimeValues.ClassValue reflected) {
      return reflected.annotations().jarClassReference(reflected.reflectedType());
    }
    if (value instanceof RuntimeValues.CodePointValue codePoint) return codePoint.value();
    if (value instanceof RuntimeValues.EnumValue enumValue) {
      if (execution == null) {
        throw new IllegalStateException("JAR enum argument execution is unavailable");
      }
      return execution.values().javaEnumArgument(enumValue);
    }
    if (value instanceof RuntimeValues.OpaqueValue opaque) return opaque.value;
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.hostValue();
    if (value instanceof RuntimeValues.ObjectValue object && execution != null) {
      Object argument = execution.values().javaArgument(object);
      if (argument != object) return argument;
      if (execution.context().jarBindingRuntime() instanceof JavaApplicationRuntime runtime) {
        return JavaApplicationBridge.toJava(runtime.applicationClassLoader(), object);
      }
      return object;
    }
    return value;
  }

  static Object jarValue(
      CoreType type, Object value, ExecutionState execution, AnnotationRuntime annotations) {
    if (value == null) return RuntimeValues.NullValue.INSTANCE;
    Object guest = mappedGuest(value, execution);
    if (guest != null) return guest;
    CoreType concrete = nonNullable(type);
    if (concrete instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
      return switch (builtin.id().value()) {
        case "std.core.Any" -> jarDynamicValue(value, execution);
        case "std.core.Integer" -> ((Number) value).intValue();
        case "std.core.Long" -> ((Number) value).longValue();
        case "std.core.Float" -> ((Number) value).floatValue();
        case "std.core.Double" -> ((Number) value).doubleValue();
        case "std.core.CodePoint" ->
            new RuntimeValues.CodePointValue(
                value instanceof Character character
                    ? character.charValue()
                    : ((Number) value).intValue());
        case "std.core.Boolean", "std.core.Number", "std.core.String" -> value;
        default -> throw new IllegalStateException("unsupported JAR value type " + concrete);
      };
    }
    if (value instanceof Enum<?> enumValue) {
      return execution.values().javaEnumValue(concrete, enumValue.name());
    }
    if (value instanceof Throwable throwable) {
      return execution.values().javaExceptionValue(concrete, throwable, execution);
    }
    if (value instanceof java.nio.file.Path path) {
      return execution.values().javaPathValue(concrete, path.toString(), execution);
    }
    if (value instanceof java.io.File file) {
      return execution.values().javaPathValue(concrete, file.getPath(), execution);
    }
    return jarBindingValue(
        concrete,
        new JarBindingResult.Reference(
            value,
            value.getClass().getName(),
            execution.context().jarBindingRuntime().referenceCandidates(value)),
        annotations,
        execution,
        null);
  }

  static Object jarBindingValue(
      CoreType type,
      JarBindingResult result,
      AnnotationRuntime annotations,
      ExecutionState execution,
      Object receiver) {
    return switch (result) {
      case JarBindingResult.Scalar scalar -> scalar.value();
      case JarBindingResult.ClassReference reference -> {
        if (annotations == null || type == null) {
          throw new IllegalStateException("JAR class result type is unavailable");
        }
        yield annotations.jarClassValue(type, reference.candidates());
      }
      case JarBindingResult.DurationValue duration -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR duration result type is unavailable");
        }
        yield execution
            .values()
            .javaDurationValue(type, duration.seconds(), duration.nanoseconds(), execution);
      }
      case JarBindingResult.EnumReference reference -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR enum result type is unavailable");
        }
        yield execution.values().javaEnumValue(type, reference.value().variant());
      }
      case JarBindingResult.ExceptionReference reference -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR exception result type is unavailable");
        }
        yield execution.values().javaExceptionValue(type, reference.value(), execution);
      }
      case JarBindingResult.PathValue path -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR path result type is unavailable");
        }
        yield execution.values().javaPathValue(type, path.value(), execution);
      }
      case JarBindingResult.UriValue uri -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR URI result type is unavailable");
        }
        yield execution.values().javaUriValue(type, uri.value(), execution);
      }
      case JarBindingResult.Null ignored -> RuntimeValues.NullValue.INSTANCE;
      case JarBindingResult.Void ignored -> null;
      case JarBindingResult.Reference reference -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR reference result type is unavailable");
        }
        Object guest = mappedGuest(reference.value(), execution);
        if (guest != null) yield guest;
        CoreType runtimeType =
            reference.candidates().isEmpty()
                ? type
                : annotations.jarReferenceType(type, reference.candidates());
        if (reference.value() instanceof AutoCloseable closeable)
          yield execution
              .values()
              .resource(runtimeType, closeable, reference.displayName(), execution);
        yield execution.values().opaque(runtimeType, reference.value(), reference.displayName());
      }
      case JarBindingResult.ReceiverAlias alias -> {
        JarBindingResult value = alias.value();
        boolean borrowed =
            receiver instanceof RuntimeValues.OpaqueValue opaque && opaque.owner != null
                || receiver instanceof RuntimeValues.OpaqueResource resource
                    && resource.borrowingOwner != null;
        if (borrowed) {
          if (value instanceof JarBindingResult.ResourceReference reference)
            value =
                new JarBindingResult.BorrowedReference(
                    reference.value(), reference.displayName(), reference.candidates());
          else if (value instanceof JarBindingResult.Reference reference)
            value =
                new JarBindingResult.BorrowedReference(
                    reference.value(), reference.displayName(), reference.candidates());
        }
        yield jarBindingValue(type, value, annotations, execution, receiver);
      }
      case JarBindingResult.BorrowedReference reference -> {
        if (execution == null || type == null || receiver == null)
          throw new IllegalStateException("borrowed JAR result requires its owning receiver");
        CoreType runtimeType =
            reference.candidates().isEmpty()
                ? type
                : annotations.jarReferenceType(type, reference.candidates());
        if (reference.value() instanceof AutoCloseable closeable)
          yield execution
              .values()
              .borrowedResource(
                  runtimeType, closeable, reference.displayName(), receiver, execution);
        yield execution
            .values()
            .borrowedOpaque(runtimeType, reference.value(), reference.displayName(), receiver);
      }
      case JarBindingResult.ResourceReference reference -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("JAR resource result type is unavailable");
        }
        CoreType runtimeType =
            reference.candidates().isEmpty()
                ? type
                : annotations.jarReferenceType(type, reference.candidates());
        yield execution
            .values()
            .resource(runtimeType, reference.value(), reference.displayName(), execution);
      }
      case JarBindingResult.ResourceClosed ignored -> {
        if (!(receiver instanceof RuntimeValues.OpaqueResource resource)) {
          throw new IllegalStateException("JAR resource close receiver is unavailable");
        }
        resource.closedExternally();
        yield null;
      }
    };
  }

  private static Object jarDynamicValue(Object value, ExecutionState execution) {
    if (value instanceof Byte number) return number.intValue();
    if (value instanceof Short number) return number.intValue();
    if (value instanceof Character character) {
      return new RuntimeValues.CodePointValue(character.charValue());
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean)
      return value;
    return execution.values().opaque(CoreType.ANY, value, value.getClass().getName());
  }

  private static Object mappedGuest(Object value, ExecutionState execution) {
    if (execution != null
        && execution.context().jarBindingRuntime() instanceof JavaApplicationRuntime runtime) {
      return JavaApplicationBridge.fromJava(runtime.applicationClassLoader(), value);
    }
    return null;
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          new CoreType.Declared(
              declared.constructor(),
              declared.arguments(),
              declared.category(),
              CoreNullability.NON_NULL);
      case CoreType.Function function ->
          new CoreType.Function(
              function.returnType(), function.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }
}
