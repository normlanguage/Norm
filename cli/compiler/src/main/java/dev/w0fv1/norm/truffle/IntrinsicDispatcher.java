package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.JarBindingCallback;
import dev.w0fv1.norm.execution.JarBindingCallbackException;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleDependency;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class IntrinsicDispatcher {
  private static final Set<IntrinsicId> SUPPORTED =
      Collections.unmodifiableSet(EnumSet.allOf(IntrinsicId.class));

  private IntrinsicDispatcher() {}

  public static Set<IntrinsicId> supportedIntrinsics() {
    return SUPPORTED;
  }

  private static Object jarArgument(Object value, ExecutionState execution) {
    return jarArgument(value, execution, null);
  }

  private static Object jarArgument(
      Object value, ExecutionState execution, AnnotationRuntime annotations) {
    if (value == RuntimeValues.NullValue.INSTANCE) return null;
    if (value instanceof RuntimeValues.Closure closure) {
      if (execution == null) {
        throw new IllegalStateException("JAR callback execution is unavailable");
      }
      CoreType concrete = nonNullable(closure.functionType());
      if (!(concrete instanceof CoreType.Function function)) {
        throw new IllegalStateException("JAR callback value has no function type");
      }
      return (JarBindingCallback)
          arguments ->
              execution
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
                              RuntimeValues.invoke(execution, closure, values),
                              execution,
                              annotations);
                        } catch (JarBindingCallbackException exception) {
                          throw exception;
                        } catch (RuntimeException exception) {
                          throw new JarBindingCallbackException(exception);
                        }
                      });
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

  static Object execute(
      IntrinsicId intrinsic,
      Object receiver,
      Object[] arguments,
      CoreType type,
      ExecutionContext context,
      Node location) {
    return execute(intrinsic, receiver, arguments, type, context, location, null, null);
  }

  static Object execute(
      IntrinsicId intrinsic,
      Object receiver,
      Object[] arguments,
      CoreType type,
      ExecutionContext context,
      Node location,
      AnnotationRuntime annotations,
      ExecutionState execution) {
    Object first = arguments.length == 0 ? null : arguments[0];
    Object second = arguments.length < 2 ? null : arguments[1];
    Object third = arguments.length < 3 ? null : arguments[2];
    Object fourth = arguments.length < 4 ? null : arguments[3];
    Object fifth = arguments.length < 5 ? null : arguments[4];
    return switch (intrinsic) {
      case CLASS_LITERAL -> {
        if (annotations == null
            || !(type instanceof CoreType.Declared declared)
            || declared.arguments().size() != 1) {
          throw new IllegalStateException("class literal runtime type is unavailable");
        }
        yield new RuntimeValues.ClassValue(type, declared.arguments().getFirst(), annotations);
      }
      case CLASS_NAME ->
          ((RuntimeValues.ClassValue) receiver)
              .annotations()
              .name(((RuntimeValues.ClassValue) receiver).reflectedType());
      case CLASS_ANNOTATION -> {
        if (execution == null) {
          throw new IllegalStateException("annotation execution is unavailable");
        }
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
        yield reflected.annotations().annotation(reflected.reflectedType(), type, execution);
      }
      case CLASS_FIELDS -> {
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
        yield reflected.annotations().fields(reflected.reflectedType(), type);
      }
      case CLASS_FUNCTIONS -> {
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
        yield reflected.annotations().functions(reflected.reflectedType(), type);
      }
      case CLASS_CONSTRUCTORS -> {
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
        yield reflected.annotations().constructors(reflected.reflectedType(), type);
      }
      case FIELD_LITERAL -> {
        if (annotations == null || type == null) {
          throw new IllegalStateException("field literal runtime type is unavailable");
        }
        yield annotations.field(type, (Integer) first);
      }
      case FIELD_NAME -> ((RuntimeValues.FieldValue) receiver).name();
      case FIELD_TYPE -> {
        RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
        yield new RuntimeValues.ClassValue(type, field.fieldType(), field.annotations());
      }
      case FIELD_OWNER -> {
        RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
        yield new RuntimeValues.ClassValue(type, field.ownerType(), field.annotations());
      }
      case FIELD_ANNOTATION -> {
        if (execution == null) {
          throw new IllegalStateException("annotation execution is unavailable");
        }
        RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
        yield field.annotations().fieldAnnotation(field, type, execution);
      }
      case FIELD_READ -> {
        RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
        yield field.annotations().readField(field, first);
      }
      case FUNCTION_NAME -> {
        RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
        yield annotations.functionName(function);
      }
      case FUNCTION_OWNER -> {
        RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
        yield annotations.functionOwner(function, type);
      }
      case FUNCTION_PARAMETERS -> {
        RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
        yield annotations.parameters(function, type);
      }
      case PARAMETER_NAME -> ((RuntimeValues.ParameterValue) receiver).name();
      case PARAMETER_TYPE -> {
        RuntimeValues.ParameterValue parameter = (RuntimeValues.ParameterValue) receiver;
        yield new RuntimeValues.ClassValue(type, parameter.valueType(), parameter.annotations());
      }
      case PARAMETER_FUNCTION -> ((RuntimeValues.ParameterValue) receiver).function();
      case CONSTRUCTOR_OWNER -> {
        RuntimeValues.ConstructorValue constructor = (RuntimeValues.ConstructorValue) receiver;
        yield new RuntimeValues.ClassValue(
            type, constructor.ownerType(), constructor.annotations());
      }
      case JSON_ENCODE -> {
        if (annotations == null || execution == null) {
          throw new IllegalStateException("serialization runtime is unavailable");
        }
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
        yield annotations
            .mapper()
            .write(JsonDataFormat.INSTANCE, reflected.reflectedType(), first, execution, location);
      }
      case JSON_DECODE -> {
        if (annotations == null || execution == null || type == null) {
          throw new IllegalStateException("serialization runtime is unavailable");
        }
        yield annotations
            .mapper()
            .read(JsonDataFormat.INSTANCE, type, (String) first, execution, location);
      }
      case JSON_PARSE -> {
        if (annotations == null || execution == null || type == null) {
          throw new IllegalStateException("JSON runtime is unavailable");
        }
        yield JsonRuntime.parseValue((String) first, type, annotations, execution, location);
      }
      case JSON_WRITE -> {
        if (annotations == null || execution == null) {
          throw new IllegalStateException("JSON runtime is unavailable");
        }
        yield JsonRuntime.writeValue(first, annotations, execution, location);
      }
      case XML_ENCODE -> {
        if (annotations == null || execution == null) {
          throw new IllegalStateException("serialization runtime is unavailable");
        }
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
        yield annotations
            .mapper()
            .write(annotations.xml(), reflected.reflectedType(), first, execution, location);
      }
      case XML_DECODE -> {
        if (annotations == null || execution == null || type == null) {
          throw new IllegalStateException("serialization runtime is unavailable");
        }
        yield annotations
            .mapper()
            .read(annotations.xml(), type, (String) first, execution, location);
      }
      case YAML_ENCODE -> {
        if (annotations == null || execution == null) {
          throw new IllegalStateException("serialization runtime is unavailable");
        }
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
        yield annotations
            .mapper()
            .write(YamlDataFormat.INSTANCE, reflected.reflectedType(), first, execution, location);
      }
      case YAML_DECODE -> {
        if (annotations == null || execution == null || type == null) {
          throw new IllegalStateException("serialization runtime is unavailable");
        }
        yield annotations
            .mapper()
            .read(YamlDataFormat.INSTANCE, type, (String) first, execution, location);
      }
      case CONFIGURATION_PROPERTIES -> {
        if (annotations == null || execution == null || type == null) {
          throw new IllegalStateException("configuration runtime is unavailable");
        }
        RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
        try {
          Map<String, Object> properties =
              annotations.configuration().properties(reflected.reflectedType(), first);
          yield execution.values().opaque(type, properties, "MutableMap");
        } catch (SerializationRuntime.ShapeException | IllegalArgumentException failure) {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, failure.getMessage(), location);
        }
      }
      case FUNCTION_CONTEXT_FUNCTION -> ((RuntimeValues.FunctionContextValue) receiver).function();
      case PARAMETER_CONTEXT_PARAMETER ->
          ((RuntimeValues.ParameterContextValue) receiver).parameter();
      case FIELD_CONTEXT_FIELD -> ((RuntimeValues.FieldContextValue) receiver).field();
      case FUNCTION_INVOCATION_PROCEED ->
          ((RuntimeValues.FunctionInvocationValue) receiver).proceed(location);
      case FUNCTION_COMPLETION_SUCCEEDED ->
          ((RuntimeValues.FunctionCompletionValue) receiver).succeeded();
      case PRINT_LINE -> {
        context.output().println(RuntimeValues.stringify(first));
        yield null;
      }
      case EXPECTED_OUTPUT_LINE -> {
        context.expectedOutput().println(RuntimeValues.stringify(first));
        yield null;
      }
      case AWAIT_CANCELLATION -> {
        if (execution == null) throw new IllegalStateException("execution runtime is unavailable");
        execution.callbacks().runUntilCancellation();
        yield null;
      }
      case REQUIRE_ARGUMENT -> {
        if (!(Boolean) first) {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, (String) second, location);
        }
        yield null;
      }
      case PUBLISH_MODULE -> {
        RuntimeValues.ListValue exportedValues = (RuntimeValues.ListValue) third;
        List<String> exports = exportedValues.values.stream().map(String.class::cast).toList();
        List<Object> dependencyRepositories = ((RuntimeValues.ListValue) fourth).values;
        List<Object> dependencyNames = ((RuntimeValues.ListValue) fifth).values;
        List<Object> dependencyVersions = ((RuntimeValues.ListValue) arguments[5]).values;
        List<Object> dependencyExports = ((RuntimeValues.ListValue) arguments[6]).values;
        if (dependencyRepositories.size() != dependencyNames.size()
            || dependencyNames.size() != dependencyVersions.size()
            || dependencyNames.size() != dependencyExports.size()) {
          throw new IllegalStateException("module dependency coordinates are inconsistent");
        }
        List<ModuleDependency> dependencies = new ArrayList<>(dependencyNames.size());
        for (int index = 0; index < dependencyNames.size(); index++) {
          dependencies.add(
              new ModuleDependency(
                  (String) dependencyRepositories.get(index),
                  (String) dependencyNames.get(index),
                  dependencyVersions.get(index) == RuntimeValues.NullValue.INSTANCE
                      ? null
                      : (Integer) dependencyVersions.get(index),
                  (Boolean) dependencyExports.get(index)));
        }
        String bindingSource = (String) arguments[7];
        Optional<Sha256Digest> digest =
            ((String) arguments[12]).isEmpty()
                ? Optional.empty()
                : Optional.of(Sha256Digest.parse((String) arguments[12]));
        List<Object> bindingApiTypes = ((RuntimeValues.ListValue) arguments[13]).values;
        List<Object> bindingApiMembers = ((RuntimeValues.ListValue) arguments[14]).values;
        List<Object> bindingApiOverloadNames = ((RuntimeValues.ListValue) arguments[15]).values;
        List<Object> bindingApiOverloadParameterTypes =
            ((RuntimeValues.ListValue) arguments[16]).values;
        if (bindingApiTypes.size() != bindingApiMembers.size()
            || bindingApiTypes.size() != bindingApiOverloadNames.size()
            || bindingApiTypes.size() != bindingApiOverloadParameterTypes.size()) {
          throw new IllegalStateException("JAR binding API declarations are inconsistent");
        }
        List<JarBindingType> api = new ArrayList<>(bindingApiTypes.size());
        for (int index = 0; index < bindingApiTypes.size(); index++) {
          RuntimeValues.ListValue members = (RuntimeValues.ListValue) bindingApiMembers.get(index);
          RuntimeValues.ListValue overloadNames =
              (RuntimeValues.ListValue) bindingApiOverloadNames.get(index);
          RuntimeValues.ListValue overloadParameterTypes =
              (RuntimeValues.ListValue) bindingApiOverloadParameterTypes.get(index);
          if (overloadNames.values.size() != overloadParameterTypes.values.size()) {
            throw new IllegalStateException("JAR binding overload declarations are inconsistent");
          }
          List<JarBindingOverload> overloads = new ArrayList<>(overloadNames.values.size());
          for (int overloadIndex = 0;
              overloadIndex < overloadNames.values.size();
              overloadIndex++) {
            RuntimeValues.ListValue parameterTypes =
                (RuntimeValues.ListValue) overloadParameterTypes.values.get(overloadIndex);
            overloads.add(
                new JarBindingOverload(
                    (String) overloadNames.values.get(overloadIndex),
                    parameterTypes.values.stream().map(String.class::cast).toList()));
          }
          api.add(
              new JarBindingType(
                  (String) bindingApiTypes.get(index),
                  members.values.stream().map(String.class::cast).toList(),
                  overloads));
        }
        Optional<JarBinding> binding =
            switch (bindingSource) {
              case "" -> Optional.empty();
              case "local" ->
                  Optional.of(
                      new JarBinding(new LocalJarTarget((String) arguments[8], digest), api));
              case "maven" ->
                  Optional.of(
                      new JarBinding(
                          new MavenJarTarget(
                              new MavenArtifactCoordinate(
                                  (String) arguments[9],
                                  (String) arguments[10],
                                  (String) arguments[11]),
                              digest),
                          api));
              default ->
                  throw new IllegalStateException("unknown JAR binding source " + bindingSource);
            };
        context
            .modulePublisher()
            .orElseThrow(
                () -> new IllegalStateException("module publication capability is unavailable"))
            .publish(
                new ModuleDeclaration(
                    first == RuntimeValues.NullValue.INSTANCE ? null : (String) first,
                    second == RuntimeValues.NullValue.INSTANCE ? null : (Integer) second,
                    exports,
                    dependencies,
                    binding));
        yield null;
      }
      case JAVA_COLLECTION_SIZE -> javaCollection(first).size();
      case JAVA_LIST_GET -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java list element type is unavailable");
        }
        List<Object> values = javaList(first);
        yield jarValue(type, values.get(index(second, values.size(), location)), execution);
      }
      case JAVA_LIST_SET -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java list element type is unavailable");
        }
        List<Object> values = javaList(first);
        Object previous =
            values.set(index(second, values.size(), location), jarArgument(third, execution));
        yield jarValue(type, previous, execution);
      }
      case JAVA_LIST_REMOVE -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java list element type is unavailable");
        }
        List<Object> values = javaList(first);
        Object removed = values.remove(index(second, values.size(), location));
        yield jarValue(type, removed, execution);
      }
      case JAVA_COLLECTION_CONTAINS ->
          javaCollection(first).contains(jarArgument(second, execution));
      case JAVA_COLLECTION_ADD -> javaCollection(first).add(jarArgument(second, execution));
      case JAVA_COLLECTION_REMOVE -> javaCollection(first).remove(jarArgument(second, execution));
      case JAVA_ITERABLE_ITERATOR -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java iterator result type is unavailable");
        }
        Iterator<?> iterator = javaIterable(first).iterator();
        yield execution.values().opaque(type, iterator, iterator.getClass().getName());
      }
      case JAVA_ITERATOR_HAS_NEXT -> javaIterator(first).hasNext();
      case JAVA_ITERATOR_NEXT -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java iterator element type is unavailable");
        }
        yield jarValue(type, javaIterator(first).next(), execution);
      }
      case JAVA_MAP_NEW -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java map result type is unavailable");
        }
        yield execution.values().opaque(type, new java.util.LinkedHashMap<>(), "MutableMap");
      }
      case JAVA_MAP_SIZE -> javaMap(first).size();
      case JAVA_MAP_CONTAINS_KEY -> javaMap(first).containsKey(jarArgument(second, execution));
      case JAVA_MAP_GET -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java map value type is unavailable");
        }
        yield jarValue(type, javaMap(first).get(jarArgument(second, execution)), execution);
      }
      case JAVA_MAP_PUT -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java map value type is unavailable");
        }
        Object previous =
            javaMap(first).put(jarArgument(second, execution), jarArgument(third, execution));
        yield jarValue(type, previous, execution);
      }
      case JAVA_MAP_REMOVE -> {
        if (execution == null || type == null) {
          throw new IllegalStateException("Java map value type is unavailable");
        }
        yield jarValue(type, javaMap(first).remove(jarArgument(second, execution)), execution);
      }
      case JAR_INVOKE, JAR_INVOKE_VOID -> {
        try {
          List<Object> jarArguments =
              java.util.Arrays.stream(arguments, 1, arguments.length)
                  .map(value -> jarArgument(value, execution, annotations))
                  .toList();
          JarBindingResult result;
          try {
            result = invokeJar(context, (String) first, jarArguments, execution, location);
          } finally {
            synchronizeJarArguments(context, jarArguments);
          }
          yield jarBindingValue(type, result, annotations, execution, second);
        } catch (JarBindingInvocationException exception) {
          if (execution == null) {
            throw new IllegalStateException("JAR invocation exception execution is unavailable");
          }
          throw execution.values().javaException(exception.failure(), execution, location);
        } catch (JarBindingRuntimeException exception) {
          throw new NormGuestException(
              RuntimeErrorCode.JAR_BINDING, exception.getMessage(), location);
        }
      }
      case IO_BYTES_CREATE,
          IO_BYTES_SIZE,
          IO_BYTES_AT,
          IO_BYTES_SLICE,
          IO_BYTES_TO_ARRAY,
          IO_BYTES_JOIN,
          IO_TEXT_ENCODE_UTF8,
          IO_TEXT_DECODE_UTF8,
          IO_USE ->
          IoIntrinsicDispatcher.execute(intrinsic, first, second, third, type, execution, location);
      case JAR_INPUT_STREAM_READ,
          JAR_OUTPUT_STREAM_WRITE,
          JAR_OUTPUT_STREAM_FLUSH,
          JAR_STREAM_CLOSE ->
          JarStreamIntrinsicDispatcher.execute(intrinsic, first, second, type, execution, location);
      case JAR_TASK_AWAIT, JAR_TASK_CANCEL, JAR_TASK_COMPLETED, JAR_TASK_CLOSE ->
          JarTaskIntrinsicDispatcher.execute(
              intrinsic, first, type, annotations, execution, location);
      case FILE_OPEN_READ,
          FILE_READER_READ,
          FILE_OPEN_WRITE,
          FILE_WRITER_WRITE,
          FILE_WRITER_FLUSH,
          FILE_WRITER_SYNC,
          FILE_CLOSE ->
          FileIntrinsicDispatcher.execute(
              intrinsic, first, second, type, context, execution, location);
      case HTTP_SEND,
          HTTP_RESPONSE_STATUS,
          HTTP_RESPONSE_HEADERS,
          HTTP_RESPONSE_READ,
          HTTP_RESPONSE_CLOSE ->
          HttpIntrinsicDispatcher.execute(intrinsic, arguments, type, context, execution, location);
      case TIME_SYSTEM_CLOCK, TIME_CLOCK_NOW ->
          SystemIntrinsicDispatcher.execute(
              intrinsic, first, second, type, context, execution, location);
      case TO_STRING -> RuntimeValues.stringify(receiver);
      case RANGE_CONSTRUCT -> {
        int step = third == null ? 1 : (Integer) third;
        if (step == 0) {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, "range step must not be zero", location);
        }
        yield new RuntimeValues.RangeValue(type, (Integer) first, (Integer) second, step);
      }
      case ARRAY_CONSTRUCT -> new RuntimeValues.ArrayValue(type, new ArrayList<>());
      case LIST_CONSTRUCT -> new RuntimeValues.ListValue(type);
      case MAP_CONSTRUCT -> new RuntimeValues.MapValue(type);
      case SET_CONSTRUCT -> new RuntimeValues.SetValue(type);
      case STACK_CONSTRUCT -> new RuntimeValues.StackValue(type);
      case QUEUE_CONSTRUCT -> new RuntimeValues.QueueValue(type);
      case DEQUE_CONSTRUCT -> new RuntimeValues.DequeValue(type);
      case PAIR_CONSTRUCT -> new RuntimeValues.PairValue(type, first, second);
      case STRING_BUILDER_CONSTRUCT -> new RuntimeValues.BuilderValue(type);
      case SIZE -> {
        try {
          yield RuntimeValues.size(receiver);
        } catch (ArithmeticException exception) {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, "range size exceeds Integer", location);
        }
      }
      case IS_EMPTY -> isEmpty(receiver);
      case LIST_ADD -> {
        ((RuntimeValues.ListValue) receiver).values.add(RuntimeValues.copy(first));
        yield null;
      }
      case LIST_GET ->
          RuntimeValues.copy(
              ((RuntimeValues.ListValue) receiver)
                  .values.get(
                      index(first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
      case LIST_REMOVE_AT ->
          RuntimeValues.copy(
              ((RuntimeValues.ListValue) receiver)
                  .values.remove(
                      index(first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
      case ARRAY_FILLED ->
          new RuntimeValues.ArrayValue(type, filledValues(first, second, location));
      case ARRAY_LAST ->
          RuntimeValues.copy(last(((RuntimeValues.ArrayValue) receiver).values, "Array", location));
      case ARRAY_REVERSED -> {
        RuntimeValues.ArrayValue result = (RuntimeValues.ArrayValue) RuntimeValues.copy(receiver);
        Collections.reverse(result.values);
        yield result;
      }
      case LIST_FILLED -> new RuntimeValues.ListValue(type, filledValues(first, second, location));
      case LIST_LAST ->
          RuntimeValues.copy(last(((RuntimeValues.ListValue) receiver).values, "List", location));
      case LIST_REMOVE_LAST ->
          RuntimeValues.copy(
              removeLast(((RuntimeValues.ListValue) receiver).values, "List", location));
      case LIST_REVERSED -> {
        RuntimeValues.ListValue result = (RuntimeValues.ListValue) RuntimeValues.copy(receiver);
        Collections.reverse(result.values);
        yield result;
      }
      case MAP_PUT -> {
        RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
        yield null;
      }
      case MAP_GET ->
          RuntimeValues.copy(RuntimeValues.mapGetOrNull((RuntimeValues.MapValue) receiver, first));
      case MAP_CONTAINS_KEY -> RuntimeValues.mapContains((RuntimeValues.MapValue) receiver, first);
      case MAP_REMOVE -> RuntimeValues.mapRemove((RuntimeValues.MapValue) receiver, first);
      case SET_ADD -> RuntimeValues.setAdd((RuntimeValues.SetValue) receiver, first);
      case SET_CONTAINS -> RuntimeValues.setContains((RuntimeValues.SetValue) receiver, first);
      case SET_REMOVE -> RuntimeValues.setRemove((RuntimeValues.SetValue) receiver, first);
      case STACK_PUSH -> {
        ((RuntimeValues.StackValue) receiver).values.push(RuntimeValues.copy(first));
        yield null;
      }
      case STACK_POP ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.StackValue) receiver).values.pollFirst(), "Stack", location));
      case STACK_PEEK ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.StackValue) receiver).values.peekFirst(), "Stack", location));
      case QUEUE_ADD -> {
        ((RuntimeValues.QueueValue) receiver).values.addLast(RuntimeValues.copy(first));
        yield null;
      }
      case QUEUE_REMOVE ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.QueueValue) receiver).values.pollFirst(), "Queue", location));
      case QUEUE_PEEK ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.QueueValue) receiver).values.peekFirst(), "Queue", location));
      case DEQUE_ADD_FIRST -> {
        ((RuntimeValues.DequeValue) receiver).values.addFirst(RuntimeValues.copy(first));
        yield null;
      }
      case DEQUE_ADD_LAST -> {
        ((RuntimeValues.DequeValue) receiver).values.addLast(RuntimeValues.copy(first));
        yield null;
      }
      case DEQUE_REMOVE_FIRST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.pollFirst(), "Deque", location));
      case DEQUE_REMOVE_LAST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.pollLast(), "Deque", location));
      case DEQUE_PEEK_FIRST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.peekFirst(), "Deque", location));
      case DEQUE_PEEK_LAST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.peekLast(), "Deque", location));
      case BUILDER_APPEND -> {
        RuntimeValues.BuilderValue builder = (RuntimeValues.BuilderValue) receiver;
        builder.value.append(RuntimeValues.stringify(first));
        yield builder;
      }
      case BUILDER_TO_STRING -> ((RuntimeValues.BuilderValue) receiver).value.toString();
      case STRING_BYTE_SIZE -> RuntimeValues.byteSize((String) receiver);
      case STRING_CODE_POINT_SIZE -> RuntimeValues.codePointSize((String) receiver);
      case STRING_GRAPHEME_SIZE -> RuntimeValues.graphemeSize((String) receiver);
      case STRING_CODE_POINTS -> RuntimeValues.codePoints((String) receiver);
      case STRING_GRAPHEMES -> RuntimeValues.graphemes((String) receiver);
      case STRING_SLICE_CODE_POINTS ->
          RuntimeValues.sliceCodePoints(
              (String) receiver, (Integer) first, (Integer) second, location);
      case STRING_SPLIT -> RuntimeValues.split((String) receiver, (String) first, location);
      case STRING_IS_EMPTY -> ((String) receiver).isEmpty();
      case STRING_CONTAINS -> ((String) receiver).contains((String) first);
      case STRING_STARTS_WITH -> ((String) receiver).startsWith((String) first);
      case STRING_ENDS_WITH -> ((String) receiver).endsWith((String) first);
      case STRING_SLICE_GRAPHEMES ->
          RuntimeValues.sliceGraphemes(
              (String) receiver, (Integer) first, (Integer) second, location);
      case STRING_REPLACE ->
          RuntimeValues.replace((String) receiver, (String) first, (String) second, location);
      case STRING_REPLACE_FIRST ->
          RuntimeValues.replaceFirst((String) receiver, (String) first, (String) second, location);
      case STRING_TRIM -> RuntimeValues.trim((String) receiver);
      case STRING_TRIM_START -> RuntimeValues.trimStart((String) receiver);
      case STRING_TRIM_END -> RuntimeValues.trimEnd((String) receiver);
      case STRING_TO_LOWERCASE -> RuntimeValues.toLowercase((String) receiver);
      case STRING_TO_UPPERCASE -> RuntimeValues.toUppercase((String) receiver);
      case STRING_EQUALS_IGNORE_CASE_ASCII ->
          RuntimeValues.equalsIgnoreCaseAscii((String) receiver, (String) first);
      case STRING_COMPARE_CODE_POINTS ->
          RuntimeValues.compareCodePoints((String) receiver, (String) first);
      case STRING_NORMALIZE_NFC -> RuntimeValues.normalizeNfc((String) receiver);
      case STRING_NORMALIZE_NFD -> RuntimeValues.normalizeNfd((String) receiver);
      case STRING_NORMALIZE_NFKC -> RuntimeValues.normalizeNfkc((String) receiver);
      case STRING_NORMALIZE_NFKD -> RuntimeValues.normalizeNfkd((String) receiver);
      case STRING_IS_NORMALIZED_NFC -> RuntimeValues.isNormalizedNfc((String) receiver);
      case STRING_IS_NORMALIZED_NFD -> RuntimeValues.isNormalizedNfd((String) receiver);
      case STRING_IS_NORMALIZED_NFKC -> RuntimeValues.isNormalizedNfkc((String) receiver);
      case STRING_IS_NORMALIZED_NFKD -> RuntimeValues.isNormalizedNfkd((String) receiver);
      case CODE_POINT_SCALAR_VALUE -> ((RuntimeValues.CodePointValue) receiver).value();
      case CODE_POINT_IS_DECIMAL_DIGIT ->
          Character.isDigit(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_LETTER ->
          Character.isLetter(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_WHITESPACE ->
          RuntimeValues.isWhitespace(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_UPPERCASE ->
          Character.isUpperCase(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_LOWERCASE ->
          Character.isLowerCase(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_ASCII_DIGIT -> {
        int value = ((RuntimeValues.CodePointValue) receiver).value();
        yield value >= '0' && value <= '9';
      }
      case CODE_POINT_ASCII_DIGIT_VALUE -> {
        int value = ((RuntimeValues.CodePointValue) receiver).value();
        if (value < '0' || value > '9') {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, "code point is not an ASCII digit", location);
        }
        yield value - '0';
      }
      case PAIR_FIRST_READ -> RuntimeValues.copy(((RuntimeValues.PairValue) receiver).first);
      case PAIR_SECOND_READ -> RuntimeValues.copy(((RuntimeValues.PairValue) receiver).second);
      case PAIR_FIRST_WRITE -> {
        ((RuntimeValues.PairValue) receiver).first = RuntimeValues.copy(first);
        yield null;
      }
      case PAIR_SECOND_WRITE -> {
        ((RuntimeValues.PairValue) receiver).second = RuntimeValues.copy(first);
        yield null;
      }
      case ARRAY_INDEX_READ ->
          RuntimeValues.copy(
              ((RuntimeValues.ArrayValue) receiver)
                  .values.get(
                      index(first, ((RuntimeValues.ArrayValue) receiver).values.size(), location)));
      case LIST_INDEX_READ ->
          RuntimeValues.copy(
              ((RuntimeValues.ListValue) receiver)
                  .values.get(
                      index(first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
      case MAP_INDEX_READ ->
          RuntimeValues.copy(mapGet((RuntimeValues.MapValue) receiver, first, location));
      case ARRAY_INDEX_WRITE -> {
        ((RuntimeValues.ArrayValue) receiver)
            .values.set(
                index(first, ((RuntimeValues.ArrayValue) receiver).values.size(), location),
                RuntimeValues.copy(second));
        yield null;
      }
      case LIST_INDEX_WRITE -> {
        ((RuntimeValues.ListValue) receiver)
            .values.set(
                index(first, ((RuntimeValues.ListValue) receiver).values.size(), location),
                RuntimeValues.copy(second));
        yield null;
      }
      case MAP_INDEX_WRITE -> {
        RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
        yield null;
      }
      case ARRAY_ITERATOR ->
          nativeIterator(
              ((RuntimeValues.ArrayValue) receiver).type,
              ((RuntimeValues.ArrayValue) receiver).values.iterator());
      case LIST_ITERATOR ->
          nativeIterator(
              ((RuntimeValues.ListValue) receiver).type,
              ((RuntimeValues.ListValue) receiver).values.iterator());
      case MAP_ITERATOR ->
          new RuntimeValues.NativeIteratorValue(
              mapElementType(((RuntimeValues.MapValue) receiver).type),
              mapIterator((RuntimeValues.MapValue) receiver));
      case SET_ITERATOR ->
          nativeIterator(
              ((RuntimeValues.SetValue) receiver).type,
              ((RuntimeValues.SetValue) receiver).values.stream().map(key -> key.value).iterator());
      case STACK_ITERATOR ->
          nativeIterator(
              ((RuntimeValues.StackValue) receiver).type,
              ((RuntimeValues.StackValue) receiver).values.iterator());
      case QUEUE_ITERATOR ->
          nativeIterator(
              ((RuntimeValues.QueueValue) receiver).type,
              ((RuntimeValues.QueueValue) receiver).values.iterator());
      case DEQUE_ITERATOR ->
          nativeIterator(
              ((RuntimeValues.DequeValue) receiver).type,
              ((RuntimeValues.DequeValue) receiver).values.iterator());
      case RANGE_ITERATOR ->
          new RuntimeValues.NativeIteratorValue(
              CoreType.INTEGER, ((RuntimeValues.RangeValue) receiver).iterator());
      case ITERATOR_HAS_NEXT -> ((RuntimeValues.NativeIteratorValue) receiver).iterator.hasNext();
      case ITERATOR_NEXT -> {
        Iterator<Object> iterator = ((RuntimeValues.NativeIteratorValue) receiver).iterator;
        if (!iterator.hasNext()) {
          throw new NormGuestException(
              RuntimeErrorCode.EMPTY_COLLECTION, "iterator is exhausted", location);
        }
        yield RuntimeValues.copy(iterator.next());
      }
    };
  }

  private static Object jarValue(CoreType type, Object value, ExecutionState execution) {
    if (value == null) return RuntimeValues.NullValue.INSTANCE;
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
    return execution.values().opaque(concrete, value, value.getClass().getName());
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
        if (execution.context().jarBindingRuntime() instanceof JavaApplicationRuntime runtime) {
          Object guest =
              JavaApplicationBridge.fromJava(runtime.applicationClassLoader(), reference.value());
          if (guest != null) yield guest;
        }
        CoreType runtimeType =
            reference.candidates().isEmpty()
                ? type
                : annotations.jarReferenceType(type, reference.candidates());
        yield execution.values().opaque(runtimeType, reference.value(), reference.displayName());
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

  @TruffleBoundary
  private static JarBindingResult invokeJar(
      ExecutionContext context,
      String call,
      List<Object> arguments,
      ExecutionState execution,
      Node location) {
    boolean callbackPumpRequired =
        arguments.stream().anyMatch(JarBindingCallback.class::isInstance)
            || context.jarBindingRuntime() instanceof JavaApplicationRuntime;
    if (execution == null || !callbackPumpRequired || execution.callbacks().isBorrowedExecution()) {
      return context.jarBindingRuntime().invoke(call, arguments);
    }
    CompletableFuture<JarBindingResult> result = new CompletableFuture<>();
    Thread worker =
        Thread.ofVirtual()
            .name("norm-java-binding")
            .start(
                () -> {
                  try {
                    result.complete(context.jarBindingRuntime().invoke(call, arguments));
                  } catch (RuntimeException | Error failure) {
                    result.completeExceptionally(failure);
                  }
                });
    try {
      execution.runCallbacksUntil(result::isDone, location);
      return result.join();
    } catch (CompletionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException exception) throw exception;
      if (cause instanceof Error error) throw error;
      throw new IllegalStateException("JAR binding callback invocation failed", cause);
    } catch (RuntimeException | Error failure) {
      worker.interrupt();
      throw failure;
    }
  }

  private static void synchronizeJarArguments(ExecutionContext context, List<Object> arguments) {
    if (!(context.jarBindingRuntime() instanceof JavaApplicationRuntime runtime)) return;
    for (Object argument : arguments) {
      if (argument != null) {
        JavaApplicationBridge.fromJava(runtime.applicationClassLoader(), argument);
      }
    }
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

  @SuppressWarnings("unchecked")
  private static List<Object> javaList(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof List<?> list)) {
      throw new IllegalStateException("Java mutable list host value is unavailable");
    }
    return (List<Object>) list;
  }

  @SuppressWarnings("unchecked")
  private static java.util.Collection<Object> javaCollection(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof java.util.Collection<?> collection)) {
      throw new IllegalStateException("Java mutable collection host value is unavailable");
    }
    return (java.util.Collection<Object>) collection;
  }

  private static Iterable<?> javaIterable(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof Iterable<?> iterable)) {
      throw new IllegalStateException("Java iterable host value is unavailable");
    }
    return iterable;
  }

  private static Iterator<?> javaIterator(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof Iterator<?> iterator)) {
      throw new IllegalStateException("Java iterator host value is unavailable");
    }
    return iterator;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> javaMap(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof Map<?, ?> map)) {
      throw new IllegalStateException("Java mutable map host value is unavailable");
    }
    return (Map<Object, Object>) map;
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

  private static RuntimeValues.NativeIteratorValue nativeIterator(
      CoreType collectionType, Iterator<Object> iterator) {
    CoreType.Declared declared = (CoreType.Declared) collectionType;
    return new RuntimeValues.NativeIteratorValue(declared.arguments().getFirst(), iterator);
  }

  private static CoreType mapElementType(CoreType mapType) {
    CoreType.Declared map = (CoreType.Declared) mapType;
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Pair")),
        map.arguments(),
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  private static int index(Object value, int size, Node location) {
    int index = (Integer) value;
    if (index < 0 || index >= size) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS,
          "index " + index + " is outside collection size " + size,
          location);
    }
    return index;
  }

  private static List<Object> filledValues(Object sizeValue, Object value, Node location) {
    int size = (Integer) sizeValue;
    if (size < 0) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "collection size is outside 0..2147483647", location);
    }
    List<Object> result = new ArrayList<>(size);
    for (int index = 0; index < size; index++) result.add(RuntimeValues.copy(value));
    return result;
  }

  private static Object last(List<Object> values, String collection, Node location) {
    return requireElement(values.isEmpty() ? null : values.getLast(), collection, location);
  }

  private static Object removeLast(List<Object> values, String collection, Node location) {
    return requireElement(values.isEmpty() ? null : values.removeLast(), collection, location);
  }

  private static Object mapGet(RuntimeValues.MapValue map, Object key, Node location) {
    if (!RuntimeValues.mapContains(map, key)) {
      throw new NormGuestException(
          RuntimeErrorCode.MISSING_MAP_KEY, "map key does not exist", location);
    }
    return RuntimeValues.mapGet(map, key);
  }

  private static Object requireElement(Object value, String collection, Node location) {
    if (value == null) {
      throw new NormGuestException(
          RuntimeErrorCode.EMPTY_COLLECTION, collection + " is empty", location);
    }
    return value;
  }

  private static boolean isEmpty(Object value) {
    return switch (value) {
      case RuntimeValues.ListValue list -> list.values.isEmpty();
      case RuntimeValues.MapValue map -> map.values.isEmpty();
      case RuntimeValues.SetValue set -> set.values.isEmpty();
      case RuntimeValues.StackValue stack -> stack.values.isEmpty();
      case RuntimeValues.QueueValue queue -> queue.values.isEmpty();
      case RuntimeValues.DequeValue deque -> deque.values.isEmpty();
      default -> throw new IllegalStateException("invalid isEmpty receiver");
    };
  }

  private static Iterator<Object> mapIterator(RuntimeValues.MapValue map) {
    Iterator<Map.Entry<RuntimeValues.RuntimeKey, Object>> entries =
        map.values.entrySet().iterator();
    if (!(map.type instanceof CoreType.Declared mapType)) {
      throw new IllegalStateException("map runtime type is not declared");
    }
    CoreType pairType =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Pair")),
            mapType.arguments(),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return entries.hasNext();
      }

      @Override
      public Object next() {
        Map.Entry<RuntimeValues.RuntimeKey, Object> entry = entries.next();
        return new RuntimeValues.PairValue(pairType, entry.getKey().value, entry.getValue());
      }
    };
  }
}
