package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.bridge.NormApplicationMethod;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.DefinitionId;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.objenesis.ObjenesisStd;

final class JavaApplicationDispatch implements JavaApplicationBridge.Handler {
  private final CoreProgram program;
  private final Map<DefinitionId, CallTarget> targets;
  private final GuestValueFactory values;
  private final ExecutionState execution;
  private final ClassLoader applicationLoader;
  private final IdentityHashMap<Object, RuntimeValues.ObjectValue> guests = new IdentityHashMap<>();
  private final IdentityHashMap<RuntimeValues.ObjectValue, Object> proxies =
      new IdentityHashMap<>();
  private final ObjenesisStd objenesis = new ObjenesisStd();

  JavaApplicationDispatch(
      CoreProgram program,
      Map<DefinitionId, CallTarget> targets,
      GuestValueFactory values,
      ExecutionState execution,
      ClassLoader applicationLoader) {
    this.program = Objects.requireNonNull(program, "program");
    this.targets = Map.copyOf(targets);
    this.values = Objects.requireNonNull(values, "values");
    this.execution = Objects.requireNonNull(execution, "execution");
    this.applicationLoader = Objects.requireNonNull(applicationLoader, "applicationLoader");
  }

  @Override
  public void allocate(String definition, Object receiver) {
    execution
        .callbacks()
        .invoke(
            () -> {
              DefinitionId id = DefinitionId.parse(definition);
              CoreDefinition declaration = program.definition(id).orElseThrow();
              if (!(declaration instanceof CoreDefinition.Aggregate aggregate)) {
                throw new IllegalArgumentException(
                    "Java application allocation is not an aggregate");
              }
              CoreType owner =
                  new CoreType.Declared(
                      new CoreTypeConstructor.User(
                          new dev.w0fv1.norm.core.DefinitionReference.External(id)),
                      List.of(),
                      aggregate.valueCategory(),
                      dev.w0fv1.norm.core.CoreNullability.NON_NULL);
              RuntimeValues.ObjectValue guest = values.allocate(owner);
              attach(receiver, guest);
              return null;
            });
  }

  @Override
  public void construct(String callable, Object receiver, Object[] arguments) {
    execution
        .callbacks()
        .invoke(
            () -> {
              DefinitionId id = DefinitionId.parse(callable);
              CoreDefinition.Callable constructor = callable(id);
              if (constructor.receiverType().isEmpty()) {
                throw new IllegalArgumentException("Java application constructor is not a method");
              }
              requireSimpleCallable(constructor, arguments);
              CoreType owner =
                  CoreTypes.absolute(constructor.receiverType().orElseThrow(), id, program);
              RuntimeValues.ObjectValue guest =
                  values.construct(owner, execution, javaArguments(constructor, id, arguments));
              attach(receiver, guest);
              synchronizeToHost(guest, receiver);
              return null;
            });
  }

  @Override
  public Object invoke(String callable, Object receiver, Object[] arguments) {
    try {
      return execution.callbacks().invoke(() -> invokeOnOwner(callable, receiver, arguments));
    } catch (NormThrownException failure) {
      Object materialized = values.javaArgument(failure.value);
      if (materialized instanceof RuntimeException runtime) throw runtime;
      throw failure;
    }
  }

  @Override
  public Object invokeHost(String callable, Object receiver, Object[] arguments) {
    return execution.callbacks().invoke(() -> invokeHostMethod(callable, receiver, arguments));
  }

  @Override
  public Object toJava(Object value) {
    return execution.callbacks().invoke(() -> javaResult(value));
  }

  @Override
  public Object fromJava(Object value) {
    return execution
        .callbacks()
        .invoke(
            () -> {
              RuntimeValues.ObjectValue guest = guests.get(value);
              if (guest != null) {
                synchronizeFromHost(value, guest);
                guest.dispatchToHost = hostDispatchRequired(guest, value);
              }
              return guest;
            });
  }

  @Override
  public void writeField(Object receiver, String name, Object value) {
    RuntimeValues.ObjectValue guest = guests.get(receiver);
    if (guest == null) {
      throw new IllegalStateException("Java application receiver has no Norm object");
    }
    Field field = publicField(receiver.getClass(), name);
    if (field == null) {
      throw new IllegalStateException("Java application field is absent: " + name);
    }
    try {
      field.set(receiver, javaResult(value));
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Norm application field cannot be written", exception);
    }
  }

  private Object invokeHostMethod(String callable, Object receiver, Object[] arguments) {
    DefinitionId id = DefinitionId.parse(callable);
    CoreDefinition definition = program.definition(id).orElseThrow();
    List<CoreType> parameterTypes;
    CoreType returnType;
    if (definition instanceof CoreDefinition.InterfaceMethod method) {
      requireSimpleInterfaceMethod(method, arguments);
      parameterTypes = method.parameterTypes();
      returnType = CoreTypes.absolute(method.returnType(), id, program);
    } else if (definition instanceof CoreDefinition.Callable method && method.hasReceiver()) {
      requireSimpleCallable(method, arguments);
      parameterTypes = method.parameterTypes();
      returnType = CoreTypes.absolute(method.returnType(), id, program);
    } else {
      throw new IllegalArgumentException("Java host target is not an instance method");
    }
    Method javaMethod = javaMethod(receiver.getClass(), id);
    Object[] parameters = new Object[arguments.length];
    for (int index = 0; index < arguments.length; index++) {
      Object parameter = javaResult(arguments[index]);
      if (parameter == null
          && !CoreTypes.absolute(parameterTypes.get(index), id, program).isNullable()) {
        throw new IllegalArgumentException("Java application value is unexpectedly null");
      }
      parameters[index] = parameter;
    }
    try {
      Object result = javaMethod.invoke(receiver, parameters);
      return returnType == CoreType.VOID ? null : javaValue(returnType, result);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Java host method cannot be invoked", exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtime) throw runtime;
      if (cause instanceof Error error) throw error;
      throw new IllegalStateException("Java host method failed", cause);
    }
  }

  private static Method javaMethod(Class<?> owner, DefinitionId id) {
    Class<?> current = owner;
    List<Class<?>> interfaces = new ArrayList<>();
    while (current != null) {
      interfaces.addAll(List.of(current.getInterfaces()));
      Optional<Method> method =
          java.util.Arrays.stream(current.getDeclaredMethods())
              .filter(
                  candidate -> {
                    NormApplicationMethod annotation =
                        candidate.getAnnotation(NormApplicationMethod.class);
                    return annotation != null && annotation.value().equals(id.toString());
                  })
              .findFirst();
      if (method.isPresent()) return method.orElseThrow();
      current = current.getSuperclass();
    }
    for (int index = 0; index < interfaces.size(); index++) {
      Class<?> implemented = interfaces.get(index);
      Optional<Method> method =
          java.util.Arrays.stream(implemented.getDeclaredMethods())
              .filter(
                  candidate -> {
                    NormApplicationMethod annotation =
                        candidate.getAnnotation(NormApplicationMethod.class);
                    return annotation != null && annotation.value().equals(id.toString());
                  })
              .findFirst();
      if (method.isPresent()) return method.orElseThrow();
      interfaces.addAll(List.of(implemented.getInterfaces()));
    }
    return java.util.Arrays.stream(owner.getMethods())
        .filter(
            candidate -> {
              NormApplicationMethod annotation =
                  candidate.getAnnotation(NormApplicationMethod.class);
              return annotation != null && annotation.value().equals(id.toString());
            })
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Java host method is absent: " + id + " on " + owner.getName()));
  }

  private Object invokeOnOwner(String callable, Object receiver, Object[] arguments) {
    DefinitionId id = DefinitionId.parse(callable);
    CoreDefinition.Callable declaration = callable(id);
    requireSimpleCallable(declaration, arguments);
    RuntimeValues.ObjectValue guest = null;
    if (declaration.hasReceiver()) {
      guest = guests.get(receiver);
      if (guest == null) {
        throw new IllegalStateException("Java application receiver has no Norm object");
      }
      synchronizeFromHost(receiver, guest);
    }
    Object[] parameters = javaArguments(declaration, id, arguments);
    List<CoreType> receiverArguments =
        guest != null && guest.type instanceof CoreType.Declared declared
            ? declared.arguments()
            : List.of();
    Object[] call =
        new Object[1 + (guest == null ? 0 : 1) + parameters.length + receiverArguments.size()];
    int offset = 0;
    call[offset++] = execution;
    if (guest != null) call[offset++] = guest;
    System.arraycopy(parameters, 0, call, offset, parameters.length);
    offset += parameters.length;
    for (CoreType argument : receiverArguments) call[offset++] = argument;
    CallTarget target = targets.get(id);
    if (target == null) throw new IllegalStateException("Norm application callable is absent");
    return javaResult(target.call(call));
  }

  private Object[] javaArguments(
      CoreDefinition.Callable callable, DefinitionId owner, Object[] arguments) {
    Object[] result = new Object[arguments.length];
    for (int index = 0; index < arguments.length; index++) {
      CoreType expected = CoreTypes.absolute(callable.parameterTypes().get(index), owner, program);
      result[index] = javaValue(expected, arguments[index]);
    }
    return result;
  }

  private Object javaResult(Object value) {
    if (value == null || value == RuntimeValues.NullValue.INSTANCE) return null;
    if (value instanceof RuntimeValues.CodePointValue codePoint) return codePoint.value();
    if (value instanceof RuntimeValues.ObjectValue object) {
      Object proxy = proxies.get(object);
      if (proxy == null) proxy = materialize(object);
      synchronizeToHost(object, proxy);
      return proxy;
    }
    if (value instanceof RuntimeValues.OpaqueValue opaque) return opaque.value;
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.hostValue();
    return value;
  }

  private Object materialize(RuntimeValues.ObjectValue guest) {
    CoreDefinition definition = program.definition(guest.objectInfo.definition()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)) {
      throw new IllegalStateException("Norm application result is not an aggregate");
    }
    CoreNominalTypeKey nominal = aggregate.nominalType();
    String binaryName =
        nominal.packageName().isEmpty()
            ? nominal.name()
            : nominal.packageName() + "." + nominal.name();
    try {
      Class<?> type = applicationLoader.loadClass(binaryName);
      Object proxy = objenesis.newInstance(type);
      attach(proxy, guest);
      return proxy;
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException("Norm application result cannot be materialized", exception);
    }
  }

  private void attach(Object receiver, RuntimeValues.ObjectValue guest) {
    RuntimeValues.ObjectValue replaced = guests.put(receiver, guest);
    if (replaced != null && replaced != guest) proxies.remove(replaced);
    proxies.putIfAbsent(guest, receiver);
    if (guest.hostValue == null) guest.attachHost(receiver);
  }

  private void synchronizeFromHost(Object receiver, RuntimeValues.ObjectValue guest) {
    RuntimeValues.AggregateInfo info = (RuntimeValues.AggregateInfo) guest.objectInfo;
    for (RuntimeValues.FieldPlan plan : info.fields()) {
      Field field = publicField(receiver.getClass(), plan.name());
      if (field == null) continue;
      try {
        guest.fields[plan.index()] = javaValue(fieldType(plan), field.get(receiver));
      } catch (IllegalAccessException exception) {
        throw new IllegalStateException("Norm application field cannot be read", exception);
      }
    }
  }

  private void synchronizeToHost(RuntimeValues.ObjectValue guest, Object receiver) {
    RuntimeValues.AggregateInfo info = (RuntimeValues.AggregateInfo) guest.objectInfo;
    for (RuntimeValues.FieldPlan plan : info.fields()) {
      Field field = publicField(receiver.getClass(), plan.name());
      if (field == null) continue;
      try {
        field.set(receiver, javaResult(guest.fields[plan.index()]));
      } catch (IllegalAccessException exception) {
        throw new IllegalStateException("Norm application field cannot be written", exception);
      }
    }
  }

  private CoreType fieldType(RuntimeValues.FieldPlan plan) {
    CoreDefinition definition = program.definition(plan.owner().representative()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)) {
      throw new IllegalStateException("Norm application field owner is not an aggregate");
    }
    CoreField field =
        aggregate.fields().stream()
            .filter(candidate -> candidate.ordinal() == plan.index())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Norm application field is absent"));
    return CoreTypes.absolute(field.type(), plan.owner().representative(), program);
  }

  private Object javaValue(CoreType expected, Object value) {
    if (value == null) {
      if (!expected.isNullable()) {
        throw new IllegalArgumentException("Java application value is unexpectedly null");
      }
      return RuntimeValues.NullValue.INSTANCE;
    }
    if (value instanceof RuntimeValues.EnumValue) return value;
    RuntimeValues.ObjectValue guest = guests.get(value);
    if (guest != null) {
      synchronizeFromHost(value, guest);
      guest.dispatchToHost = hostDispatchRequired(guest, value);
      return guest;
    }
    if (value instanceof Enum<?> enumeration
        && expected instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User) {
      return values.javaEnumValue(expected, enumeration.name());
    }
    if (expected instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User) {
      return values.opaque(expected, value, value.getClass().getName());
    }
    return value;
  }

  private boolean hostDispatchRequired(RuntimeValues.ObjectValue guest, Object host) {
    CoreDefinition definition = program.definition(guest.objectInfo.definition()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)) return false;
    CoreNominalTypeKey nominal = aggregate.nominalType();
    String binaryName =
        nominal.packageName().isEmpty()
            ? nominal.name()
            : nominal.packageName() + "." + nominal.name();
    try {
      return host.getClass() != applicationLoader.loadClass(binaryName);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException("Norm application host type is absent", exception);
    }
  }

  private static Field publicField(Class<?> owner, String name) {
    try {
      return owner.getField(name);
    } catch (NoSuchFieldException exception) {
      return null;
    }
  }

  private CoreDefinition.Callable callable(DefinitionId id) {
    CoreDefinition definition = program.definition(id).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable callable)) {
      throw new IllegalArgumentException("Java application target is not callable");
    }
    return callable;
  }

  private static void requireSimpleCallable(CoreDefinition.Callable callable, Object[] arguments) {
    if (callable.parameters().size() != arguments.length) {
      throw new IllegalArgumentException("Java application argument count does not match");
    }
    if (!callable.captureTypes().isEmpty()
        || !callable.typeParameters().isEmpty()
        || callable.receiverTypeParameterCount() != 0) {
      throw new IllegalArgumentException(
          "generic or captured Java application callables are not supported");
    }
  }

  private static void requireSimpleInterfaceMethod(
      CoreDefinition.InterfaceMethod method, Object[] arguments) {
    if (method.parameterTypes().size() != arguments.length) {
      throw new IllegalArgumentException("Java host interface argument count does not match");
    }
    if (!method.typeParameters().isEmpty()) {
      throw new IllegalArgumentException("generic Java host interface methods are not supported");
    }
  }
}
