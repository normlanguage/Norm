package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.bridge.JavaDirectCall;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.JavaFunctionShape;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.objenesis.ObjenesisStd;

final class JavaApplicationDispatch implements JavaApplicationBridge.Handler {
  private final RuntimeProgram program;
  private final dev.w0fv1.norm.core.CoreTypeRelations typeRelations;
  private final Map<DefinitionId, CallTarget> targets;
  private final GuestValueFactory values;
  private final ExecutionState execution;
  private final ClassLoader applicationLoader;
  private final IdentityHashMap<Object, RuntimeValues.ObjectValue> guests = new IdentityHashMap<>();
  private final IdentityHashMap<RuntimeValues.ObjectValue, Object> proxies =
      new IdentityHashMap<>();
  private final IdentityHashMap<RuntimeValues.Closure, Object> functionProxies =
      new IdentityHashMap<>();
  private final IdentityHashMap<Object, Map<CoreType, RuntimeValues.Closure>> functions =
      new IdentityHashMap<>();
  private final ObjenesisStd objenesis = new ObjenesisStd();
  private final Map<String, JavaDirectCall> hostCalls;

  JavaApplicationDispatch(
      RuntimeProgram program,
      Map<DefinitionId, CallTarget> targets,
      GuestValueFactory values,
      ExecutionState execution,
      JavaApplicationRuntime runtime) {
    this.program = Objects.requireNonNull(program, "program");
    this.typeRelations = new dev.w0fv1.norm.core.CoreTypeRelations(program.structures());
    this.targets = Map.copyOf(targets);
    this.values = Objects.requireNonNull(values, "values");
    this.execution = Objects.requireNonNull(execution, "execution");
    this.applicationLoader =
        Objects.requireNonNull(runtime.applicationClassLoader(), "applicationLoader");
    this.hostCalls = runtime.applicationCalls();
  }

  @Override
  public void allocate(String definition, Object receiver) {
    execution
        .callbacks()
        .invoke(
            () -> {
              DefinitionId id = DefinitionId.parse(definition);
              CoreDefinition declaration = program.structure(id).orElseThrow();
              if (!(declaration instanceof CoreDefinition.Aggregate aggregate)) {
                throw new IllegalArgumentException(
                    "Java application allocation is not an aggregate");
              }
              CoreType owner =
                  new CoreType.Declared(
                      new CoreTypeConstructor.User(new DefinitionReference.External(id)),
                      List.of(),
                      aggregate.valueCategory(),
                      CoreNullability.NON_NULL);
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
              RuntimeProgram.Callable constructor = callable(id);
              if (constructor.receiverType().isEmpty()) {
                throw new IllegalArgumentException("Java application constructor is not a method");
              }
              requireJavaCallable(constructor, arguments);
              if (constructor.receiverTypeParameterCount() != 0) {
                throw new IllegalArgumentException(
                    "Java constructors require concrete receiver types");
              }
              CoreType owner =
                  CoreTypes.absolute(constructor.receiverType().orElseThrow(), id, program);
              RuntimeValues.ObjectValue guest = values.allocate(owner);
              invokeGuest(id, constructor, guest, arguments);
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
  public Object invokeHost(
      String callable,
      Object receiver,
      Object[] arguments,
      Object receiverType,
      Object[] methodTypes) {
    return execution
        .callbacks()
        .invoke(
            () ->
                invokeHostMethod(
                    callable,
                    receiver,
                    arguments,
                    (CoreType) receiverType,
                    java.util.Arrays.stream(methodTypes).map(CoreType.class::cast).toList()));
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
    if (field == null) return;
    try {
      field.set(receiver, javaResult(value));
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Norm application field cannot be written", exception);
    }
  }

  private Object invokeHostMethod(
      String callable,
      Object receiver,
      Object[] arguments,
      CoreType receiverType,
      List<CoreType> methodTypes) {
    DefinitionId id = DefinitionId.parse(callable);
    CoreDefinition definition = program.structure(id).orElse(null);
    List<CoreType> parameterTypes;
    CoreType returnType;
    CoreType ownerType;
    int methodTypeCount;
    if (definition instanceof CoreDefinition.MethodSignature method) {
      ownerType = method.receiverType();
      methodTypeCount = method.typeParameters().size();
      parameterTypes = method.parameterTypes();
      returnType = CoreTypes.absolute(method.returnType(), id, program);
    } else if (program.callable(id).isPresent()) {
      RuntimeProgram.Callable method = callable(id);
      if (!method.hasReceiver())
        throw new IllegalArgumentException("Java host target is not an instance method");
      if (method.captureCount() != 0)
        throw new IllegalArgumentException("Java host methods cannot capture locals");
      ownerType = method.receiverType().orElseThrow();
      methodTypeCount = method.typeParameterCount();
      parameterTypes = method.parameterTypes();
      returnType = CoreTypes.absolute(method.returnType(), id, program);
    } else {
      throw new IllegalArgumentException("Java host target is not an instance method");
    }
    if (parameterTypes.size() != arguments.length || methodTypeCount != methodTypes.size()) {
      throw new IllegalArgumentException("Java host call does not match its signature");
    }
    var typeArguments =
        new java.util.ArrayList<>(receiverTypeArguments(ownerType, receiverType, id));
    typeArguments.addAll(methodTypes);
    parameterTypes =
        parameterTypes.stream()
            .map(type -> CoreTypes.absolute(type, id, program).substitute(typeArguments::get))
            .toList();
    returnType = returnType.substitute(typeArguments::get);
    var javaMethod = hostCalls.get(id.toString());
    if (javaMethod == null) throw new IllegalStateException("Java host method is absent: " + id);
    Object[] parameters = new Object[arguments.length + 1];
    parameters[0] = receiver;
    for (int index = 0; index < arguments.length; index++) {
      Object parameter = javaResult(arguments[index]);
      if (parameter == null && !parameterTypes.get(index).isNullable()) {
        throw new IllegalArgumentException("Java application value is unexpectedly null");
      }
      parameters[index + 1] = parameter;
    }
    try {
      Object result = execution.callbacks().hostCall(() -> javaMethod.invoke(parameters));
      return returnType.equals(CoreType.VOID) ? null : javaValue(returnType, result);
    } catch (Throwable cause) {
      if (cause instanceof NormGuestException guest) throw guest;
      if (cause instanceof Error error) throw error;
      throw values.javaException(cause, execution, null);
    }
  }

  private Object invokeOnOwner(String callable, Object receiver, Object[] arguments) {
    DefinitionId id = DefinitionId.parse(callable);
    RuntimeProgram.Callable declaration = callable(id);
    requireJavaCallable(declaration, arguments);
    RuntimeValues.ObjectValue guest = null;
    if (declaration.hasReceiver()) {
      guest = guests.get(receiver);
      if (guest == null) {
        throw new IllegalStateException("Java application receiver has no Norm object");
      }
      synchronizeFromHost(receiver, guest);
    }
    return javaResult(invokeGuest(id, declaration, guest, arguments));
  }

  private Object invokeGuest(
      DefinitionId id,
      RuntimeProgram.Callable declaration,
      RuntimeValues.ObjectValue guest,
      Object[] arguments) {
    List<CoreType> receiverArguments =
        guest == null
            ? List.of()
            : receiverTypeArguments(declaration.receiverType().orElseThrow(), guest.type, id);
    Object[] parameters = new Object[arguments.length];
    for (int index = 0; index < arguments.length; index++) {
      CoreType expected =
          CoreTypes.absolute(declaration.parameterTypes().get(index), id, program)
              .substitute(receiverArguments::get);
      parameters[index] = javaValue(expected, arguments[index]);
    }
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
    return target.call(call);
  }

  private List<CoreType> receiverTypeArguments(
      CoreType ownerType, CoreType receiverType, DefinitionId id) {
    var owner = (CoreType.Declared) CoreTypes.absolute(ownerType, id, program);
    var ownerReference =
        (DefinitionReference.External)
            ((CoreTypeConstructor.User) owner.constructor()).definition();
    var receiverView = typeRelations.view(receiverType, ownerReference.definition());
    if (receiverView == null)
      throw new IllegalArgumentException("Java receiver does not implement the method owner");
    return receiverView.arguments();
  }

  private Object javaResult(Object value) {
    if (value == null || value == RuntimeValues.NullValue.INSTANCE) return null;
    if (value instanceof RuntimeValues.ListValue list) {
      return list.values.stream().map(this::javaResult).toList();
    }
    if (value instanceof RuntimeValues.Closure closure) {
      Object existing = functionProxies.get(closure);
      if (existing != null) return existing;
      var function = (CoreType.Function) closure.functionType();
      var shape =
          JavaFunctionShape.of(
                  function.parameterTypes().size(), function.returnType().equals(CoreType.VOID))
              .orElseThrow(
                  () -> new IllegalArgumentException("Java function arity is unsupported"));
      Object proxy =
          shape.adapt(
              arguments ->
                  execution
                      .callbacks()
                      .invoke(
                          () -> {
                            try {
                              Object[] parameters = new Object[arguments.length];
                              for (int index = 0; index < arguments.length; index++) {
                                parameters[index] =
                                    javaValue(
                                        function.parameterTypes().get(index), arguments[index]);
                              }
                              return javaResult(
                                  RuntimeInvocation.invoke(execution, closure, parameters));
                            } catch (NormThrownException failure) {
                              Object materialized = values.javaArgument(failure.value);
                              if (materialized instanceof RuntimeException runtime) throw runtime;
                              throw failure;
                            }
                          }));
      functionProxies.put(closure, proxy);
      functions
          .computeIfAbsent(proxy, ignored -> new java.util.HashMap<>())
          .put(closure.functionType(), closure);
      return proxy;
    }
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
    CoreDefinition definition = program.structure(guest.objectInfo.definition()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)) {
      throw new IllegalStateException("Norm application result is not an aggregate");
    }
    CoreNominalTypeKey nominal = aggregate.nominalType();
    String binaryName = JavaApplicationTypeName.binaryName(nominal);
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
        Object previous = guest.fields[plan.index()];
        guest.fields[plan.index()] = javaValue(fieldType(plan), field.get(receiver));
        guest.fieldChanged(plan.index(), previous);
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
    CoreDefinition definition = program.structure(plan.owner().representative()).orElseThrow();
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
    if (expected instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
        && builtin.id().value().equals("std.core.List")) {
      if (!(value instanceof List<?> items)) {
        throw new IllegalArgumentException("Java application value is not a List");
      }
      var listType =
          new CoreType.Declared(
              declared.constructor(),
              declared.arguments(),
              declared.category(),
              CoreNullability.NON_NULL);
      return new RuntimeValues.ListValue(
          listType,
          items.stream().map(item -> javaValue(declared.arguments().getFirst(), item)).toList());
    }
    RuntimeValues.Closure function = functions.getOrDefault(value, Map.of()).get(expected);
    if (function != null) return function;
    if (expected instanceof CoreType.Function signature) {
      boolean returnsVoid = signature.returnType().equals(CoreType.VOID);
      var shape =
          JavaFunctionShape.of(signature.parameterTypes().size(), returnsVoid)
              .orElseThrow(
                  () -> new IllegalArgumentException("Java function arity is unsupported"));
      if (!shape.accepts(value))
        throw new IllegalArgumentException("Java callback does not match the function signature");
      var root =
          new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
              Object[] arguments = new Object[signature.parameterTypes().size()];
              for (int index = 0; index < arguments.length; index++) {
                arguments[index] = javaResult(frame.getArguments()[index + 1]);
              }
              Object result;
              try {
                result = execution.callbacks().hostCall(() -> shape.invoke(value, arguments));
              } catch (RuntimeException failure) {
                throw values.javaException(failure, execution, null);
              }
              return returnsVoid
                  ? RuntimeValues.NullValue.INSTANCE
                  : javaValue(signature.returnType(), result);
            }
          };
      var closure =
          new RuntimeValues.Closure(
              root.getCallTarget(),
              Optional.empty(),
              null,
              false,
              null,
              new Object[0],
              new Object[0],
              new Object[0],
              signature);
      functions
          .computeIfAbsent(value, ignored -> new java.util.HashMap<>())
          .put(signature, closure);
      functionProxies.put(closure, value);
      return closure;
    }
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
    CoreDefinition definition = program.structure(guest.objectInfo.definition()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)) return false;
    CoreNominalTypeKey nominal = aggregate.nominalType();
    String binaryName = JavaApplicationTypeName.binaryName(nominal);
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

  private RuntimeProgram.Callable callable(DefinitionId id) {
    return program
        .callable(id)
        .orElseThrow(() -> new IllegalArgumentException("Java application target is not callable"));
  }

  private static void requireJavaCallable(RuntimeProgram.Callable callable, Object[] arguments) {
    if (callable.parameters().size() != arguments.length) {
      throw new IllegalArgumentException("Java application argument count does not match");
    }
    if (callable.captureCount() != 0 || callable.typeParameterCount() != 0) {
      throw new IllegalArgumentException(
          "generic or captured Java application callables are not supported");
    }
  }
}
