package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreInterceptor;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class RuntimeValues {

  private RuntimeValues() {}

  record Closure(
      CallTarget target,
      Optional<DefinitionOccurrenceId> sourceDeclaration,
      boolean unbound,
      Object receiver,
      Object[] captures,
      Object[] receiverTypeArguments,
      Object[] reifiedArguments,
      CoreType functionType,
      AnnotationRuntime annotations,
      HostAdapters adapters) {
    Closure {
      Objects.requireNonNull(target, "target");
      sourceDeclaration = Objects.requireNonNull(sourceDeclaration, "sourceDeclaration");
      captures = captures.clone();
      receiverTypeArguments = receiverTypeArguments.clone();
      reifiedArguments = reifiedArguments.clone();
      Objects.requireNonNull(functionType, "functionType");
      if (sourceDeclaration.isPresent()) Objects.requireNonNull(annotations, "annotations");
    }

    Closure(
        CallTarget target,
        Optional<DefinitionOccurrenceId> sourceDeclaration,
        boolean unbound,
        Object receiver,
        Object[] captures,
        Object[] receiverTypeArguments,
        Object[] reifiedArguments,
        CoreType functionType,
        AnnotationRuntime annotations) {
      this(
          target,
          sourceDeclaration,
          unbound,
          receiver,
          captures,
          receiverTypeArguments,
          reifiedArguments,
          functionType,
          annotations,
          new HostAdapters());
      if (annotations != null) annotations.retain(this);
    }

    Object hostAdapter(Class<?> type, java.util.function.Supplier<Object> factory) {
      return adapters.get(type, factory);
    }

    Closure snapshot(ExecutionState execution) {
      return execution.liveBindings() == null
          ? snapshot()
          : execution.liveBindings().snapshot(this);
    }

    Closure snapshot() {
      if (sourceDeclaration.isEmpty()) return this;
      return new Closure(
          new RevisionFunctionRootNode(target, annotations().version()).getCallTarget(),
          sourceDeclaration,
          unbound,
          receiver,
          captures,
          receiverTypeArguments,
          reifiedArguments,
          functionType,
          annotations());
    }

    ReflectionBinding reflectionBinding() {
      boolean frozen =
          target instanceof com.oracle.truffle.api.RootCallTarget root
              && root.getRootNode() instanceof RevisionFunctionRootNode;
      return new ReflectionBinding(annotations(), frozen);
    }

    public AnnotationRuntime annotations() {
      if (annotations == null)
        throw new IllegalArgumentException("host function has no Norm metadata");
      return annotations;
    }

    Closure(
        CallTarget target,
        Optional<DefinitionOccurrenceId> sourceDeclaration,
        DefinitionId virtualSlot,
        boolean unbound,
        Object receiver,
        Object[] captures,
        Object[] receiverTypeArguments,
        Object[] reifiedArguments,
        CoreType functionType,
        AnnotationRuntime annotations) {
      this(
          virtualSlot == null
              ? target
              : new MethodReferenceRootNode(
                      virtualSlot,
                      sourceDeclaration.orElseThrow(),
                      receiverTypeArguments.length,
                      reifiedArguments.length)
                  .getCallTarget(),
          sourceDeclaration,
          unbound,
          receiver,
          captures,
          receiverTypeArguments,
          reifiedArguments,
          functionType,
          annotations);
    }

    DefinitionOccurrenceId declaration() {
      return sourceDeclaration.orElseThrow(
          () -> new IllegalArgumentException("host function has no Norm declaration"));
    }

    LiveBindings.Version snapshotVersion() {
      return target instanceof com.oracle.truffle.api.RootCallTarget root
              && root.getRootNode() instanceof RevisionFunctionRootNode snapshot
          ? snapshot.version()
          : null;
    }

    Closure(
        CallTarget target,
        DefinitionOccurrenceId declaration,
        DefinitionId virtualSlot,
        boolean unbound,
        Object receiver,
        Object[] captures,
        Object[] receiverTypeArguments,
        Object[] reifiedArguments,
        CoreType functionType,
        AnnotationRuntime annotations) {
      this(
          target,
          Optional.of(declaration),
          virtualSlot,
          unbound,
          receiver,
          captures,
          receiverTypeArguments,
          reifiedArguments,
          functionType,
          annotations);
    }

    Closure(
        CallTarget target,
        DefinitionOccurrenceId declaration,
        DefinitionId virtualSlot,
        boolean unbound,
        Object receiver,
        Object[] captures,
        Object[] receiverTypeArguments,
        Object[] reifiedArguments,
        AnnotationRuntime annotations) {
      this(
          target,
          declaration,
          virtualSlot,
          unbound,
          receiver,
          captures,
          receiverTypeArguments,
          reifiedArguments,
          CoreType.DYNAMIC,
          annotations);
    }
  }

  static final class HostAdapters {
    private final Map<Class<?>, Object> values = new java.util.IdentityHashMap<>();

    synchronized Object get(Class<?> type, java.util.function.Supplier<Object> factory) {
      return values.computeIfAbsent(type, ignored -> factory.get());
    }
  }

  sealed interface ReferenceValue permits LocalReference, FieldReference {
    Object read();

    boolean sameLocation(ReferenceValue other);

    int locationHash();
  }

  record LocalReference(MaterializedFrame frame, FrameBinding binding) implements ReferenceValue {
    LocalReference {
      Objects.requireNonNull(frame, "frame");
      Objects.requireNonNull(binding, "binding");
    }

    @Override
    public Object read() {
      return binding.read(frame);
    }

    @Override
    public boolean sameLocation(ReferenceValue other) {
      return other instanceof LocalReference local
          && frame == local.frame
          && binding.sameLocation(local.binding);
    }

    @Override
    public int locationHash() {
      return 31 * System.identityHashCode(frame) + binding.locationHash();
    }
  }

  record FieldReference(ObjectValue receiver, FieldKey field, AggregateInfo view)
      implements ReferenceValue {
    FieldReference {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(view, "view");
    }

    FieldReference(ObjectValue receiver, FieldKey field) {
      this(receiver, field, receiver.selectedInfo());
    }

    @Override
    public Object read() {
      return receiver.readField(field, view, null);
    }

    @Override
    public boolean sameLocation(ReferenceValue other) {
      return other instanceof FieldReference reference
          && receiver == reference.receiver
          && field.equals(reference.field)
          && receiver.state.sameLocation(view, reference.view, receiver.type(), field);
    }

    @Override
    public int locationHash() {
      return 31 * System.identityHashCode(receiver)
          + receiver.state.locationHash(view, receiver.type(), field);
    }
  }

  @TruffleBoundary
  static Object copy(Object value) {
    return switch (value) {
      case ArrayValue array -> new ArrayValue(array.type, copyList(array.values));
      case ListValue list -> new ListValue(list.type, copyList(list.values));
      case MapValue map -> {
        MapValue result = new MapValue(map.type);
        map.values.forEach((key, item) -> mapPut(result, key.value, item));
        yield result;
      }
      case SetValue set -> {
        SetValue result = new SetValue(set.type);
        set.values.forEach(item -> setAdd(result, item.value));
        yield result;
      }
      case StackValue stack -> {
        StackValue result = new StackValue(stack.type);
        List<Object> items = new ArrayList<>(stack.values);
        for (int index = items.size() - 1; index >= 0; index--) {
          result.values.push(copy(items.get(index)));
        }
        yield result;
      }
      case QueueValue queue -> {
        QueueValue result = new QueueValue(queue.type);
        queue.values.forEach(item -> result.values.addLast(copy(item)));
        yield result;
      }
      case DequeValue deque -> {
        DequeValue result = new DequeValue(deque.type);
        deque.values.forEach(item -> result.values.addLast(copy(item)));
        yield result;
      }
      case PairValue pair -> new PairValue(pair.type, copy(pair.first()), copy(pair.second()));
      case EnumValue enumValue ->
          new EnumValue(
              enumValue.definition,
              enumValue.type,
              enumValue.enumName,
              enumValue.variantKey,
              enumValue.payload,
              enumValue.annotations);
      case BuilderValue builder -> new BuilderValue(builder.type, builder.value().toString());
      case NativeIteratorValue iterator -> iterator;
      case OpaqueValue opaque ->
          new OpaqueValue(
              opaque.type, opaque.value, opaque.displayName, opaque.aggregateInfo, opaque.owner);
      case OpaqueResource resource -> resource;
      case ObjectValue object -> isValueObject(object) ? copyObject(object) : object;
      case null, default -> value;
    };
  }

  @TruffleBoundary
  static ObjectValue copyObject(ObjectValue object) {
    var source = object.state().copySource(object.selectedInfo(), object.type());
    var cells = new LinkedHashMap<FieldKey, Object>();
    source.values().forEach((key, value) -> cells.put(key, copy(value)));
    var configuration = new LinkedHashMap<FieldKey, Object>();
    object
        .configurationFor(source.info())
        .forEach((key, value) -> configuration.put(key, copy(value)));
    return new ObjectValue(source.info(), source.type(), cells, configuration);
  }

  static boolean isValueObject(ObjectValue object) {
    return object.type() instanceof CoreType.Declared declared
        && declared.category() == CoreValueCategory.VALUE;
  }

  @TruffleBoundary
  static boolean equal(Object left, Object right) {
    if (left == right) return true;
    if (left == null || right == null || left.getClass() != right.getClass()) return false;
    return switch (left) {
      case ClassValue value -> value.reflectedType().equals(((ClassValue) right).reflectedType());
      case Integer value -> value.intValue() == (Integer) right;
      case Long value -> value.longValue() == (Long) right;
      case Float value -> value.floatValue() == (Float) right;
      case Double value -> value.doubleValue() == (Double) right;
      case ArrayValue value -> equalLists(value.values, ((ArrayValue) right).values);
      case ListValue value -> equalLists(value.values, ((ListValue) right).values);
      case MapValue value -> equalMaps(value, (MapValue) right);
      case SetValue value -> equalSets(value, (SetValue) right);
      case StackValue value ->
          equalLists(new ArrayList<>(value.values), new ArrayList<>(((StackValue) right).values));
      case QueueValue value ->
          equalLists(new ArrayList<>(value.values), new ArrayList<>(((QueueValue) right).values));
      case DequeValue value ->
          equalLists(new ArrayList<>(value.values), new ArrayList<>(((DequeValue) right).values));
      case PairValue value ->
          equal(value.first(), ((PairValue) right).first())
              && equal(value.second(), ((PairValue) right).second());
      case EnumValue value -> value.sameValue((EnumValue) right);
      case BuilderValue value ->
          value.value().toString().contentEquals(((BuilderValue) right).value());
      case RangeValue value ->
          value.start == ((RangeValue) right).start
              && value.end == ((RangeValue) right).end
              && value.step == ((RangeValue) right).step;
      case OpaqueValue value -> value.sameValue((OpaqueValue) right);
      case OpaqueResource value -> value.hostValue() == ((OpaqueResource) right).hostValue();
      case ObjectValue value -> value.sameValue((ObjectValue) right);
      case ReferenceValue value -> value.sameLocation((ReferenceValue) right);
      default -> Objects.equals(left, right);
    };
  }

  @TruffleBoundary
  static int hash(Object value) {
    return switch (value) {
      case null -> 0;
      case ClassValue item -> item.reflectedType().hashCode();
      case Integer item -> Integer.hashCode(item);
      case Long item -> Long.hashCode(item);
      case Float item -> item == 0.0f ? 0 : Float.hashCode(item);
      case Double item -> item == 0.0d ? 0 : Double.hashCode(item);
      case ArrayValue item -> orderedHash(0x41525259, item.values);
      case ListValue item -> orderedHash(0x4c495354, item.values);
      case MapValue item -> {
        int result = 0x4d415000;
        for (Map.Entry<RuntimeKey, Object> entry : item.values.entrySet()) {
          result += entry.getKey().hashCode() ^ Integer.rotateLeft(hash(entry.getValue()), 16);
        }
        yield result;
      }
      case SetValue item -> {
        int result = 0x53455400;
        for (RuntimeKey element : item.values) result += element.hashCode();
        yield result;
      }
      case StackValue item -> orderedHash(0x53544143, new ArrayList<>(item.values));
      case QueueValue item -> orderedHash(0x51554555, new ArrayList<>(item.values));
      case DequeValue item -> orderedHash(0x44455155, new ArrayList<>(item.values));
      case PairValue item -> 31 * hash(item.first()) + hash(item.second());
      case EnumValue item -> item.valueHash();
      case BuilderValue item -> item.value().toString().hashCode();
      case RangeValue item -> Objects.hash(item.start, item.end, item.step);
      case OpaqueValue item -> item.valueHash();
      case OpaqueResource item -> System.identityHashCode(item.hostValue());
      case ObjectValue item ->
          isValueObject(item)
              ? orderedHash(
                  31 * 0x56414c55 + item.type().hashCode(),
                  item.cells().keySet().stream()
                      .sorted(
                          java.util.Comparator.comparing((FieldKey key) -> key.owner().toString())
                              .thenComparing(FieldKey::name))
                      .map(item::rawField)
                      .toList())
              : System.identityHashCode(item);
      case ReferenceValue item -> item.locationHash();
      default -> value.hashCode();
    };
  }

  static void mapPut(MapValue map, Object key, Object value) {
    map.values.put(new RuntimeKey(copy(key)), copy(value));
  }

  static Object mapGet(MapValue map, Object key) {
    RuntimeKey runtimeKey = new RuntimeKey(key);
    if (!map.values.containsKey(runtimeKey)) {
      throw new IllegalStateException("map key lookup invariant violated");
    }
    return map.values.get(runtimeKey);
  }

  static Object mapGetOrNull(MapValue map, Object key) {
    return map.values.getOrDefault(new RuntimeKey(key), NullValue.INSTANCE);
  }

  static boolean mapContains(MapValue map, Object key) {
    return map.values.containsKey(new RuntimeKey(key));
  }

  static boolean mapRemove(MapValue map, Object key) {
    return map.values.remove(new RuntimeKey(key)) != null;
  }

  static boolean setAdd(SetValue set, Object value) {
    return set.values.add(new RuntimeKey(copy(value)));
  }

  static boolean setContains(SetValue set, Object value) {
    return set.values.contains(new RuntimeKey(value));
  }

  static boolean setRemove(SetValue set, Object value) {
    return set.values.remove(new RuntimeKey(value));
  }

  static int size(Object value) {
    return switch (value) {
      case String string -> string.length();
      case ArrayValue array -> array.values.size();
      case ListValue list -> list.values.size();
      case MapValue map -> map.values.size();
      case SetValue set -> set.values.size();
      case StackValue stack -> stack.values.size();
      case QueueValue queue -> queue.values.size();
      case DequeValue deque -> deque.values.size();
      case RangeValue range -> range.size();
      case BuilderValue builder -> builder.value().length();
      default -> throw new IllegalStateException("value has no size");
    };
  }

  @TruffleBoundary
  static ObjectValue object(ObjectInfo objectInfo, CoreType type) {
    return new ObjectValue(objectInfo, type);
  }

  @TruffleBoundary
  static Closure closure(
      CallTarget target,
      DefinitionOccurrenceId declaration,
      DefinitionId virtualSlot,
      boolean unbound,
      Object receiver,
      Object[] captures,
      Object[] receiverTypeArguments,
      Object[] reifiedArguments,
      CoreType functionType,
      AnnotationRuntime annotations) {
    return new Closure(
        target,
        declaration,
        virtualSlot,
        unbound,
        receiver,
        captures,
        receiverTypeArguments,
        reifiedArguments,
        functionType,
        annotations);
  }

  @TruffleBoundary
  static EnumValue enumeration(
      DefinitionId definition,
      CoreType type,
      String enumName,
      String variantKey,
      Object[] payload,
      AnnotationRuntime annotations) {
    return new EnumValue(definition, type, enumName, variantKey, List.of(payload), annotations);
  }

  static CoreType runtimeType(Object value) {
    return switch (value) {
      case Integer ignored -> CoreType.INTEGER;
      case Long ignored -> CoreType.LONG;
      case Float ignored -> CoreType.FLOAT;
      case Double ignored -> CoreType.DOUBLE;
      case Boolean ignored -> CoreType.BOOLEAN;
      case String ignored -> CoreType.STRING;
      case CodePointValue ignored -> CoreType.CODE_POINT;
      case ClassValue item -> item.type;
      case FieldValue item -> item.type;
      case ConstructorValue item -> item.type;
      case ParameterValue item -> item.type;
      case FunctionContextValue item -> item.type;
      case ParameterContextValue item -> item.type;
      case FieldContextValue item -> item.type;
      case FunctionInvocationValue item -> item.type;
      case FunctionCompletionValue item -> item.type;
      case ArrayValue item -> item.type;
      case ListValue item -> item.type;
      case MapValue item -> item.type;
      case SetValue item -> item.type;
      case StackValue item -> item.type;
      case QueueValue item -> item.type;
      case DequeValue item -> item.type;
      case PairValue item -> item.type;
      case RangeValue item -> item.type;
      case BuilderValue item -> item.type;
      case NativeIteratorValue item -> item.type;
      case OpaqueValue item -> item.type;
      case OpaqueResource item -> item.type;
      case ObjectValue item -> item.type();
      case EnumValue item -> item.type;
      case Closure item -> item.functionType();
      default -> throw new IllegalStateException("interface receiver has no builtin type");
    };
  }

  static BuiltinTypeId builtinType(Object value) {
    CoreType type = runtimeType(value);
    if (!(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)) {
      throw new IllegalStateException("interface receiver has no builtin type");
    }
    return builtin.id();
  }

  enum NullValue {
    INSTANCE;

    @Override
    public String toString() {
      return "null";
    }
  }

  record CodePointValue(int value) {
    CodePointValue {
      if (!Character.isValidCodePoint(value)
          || value >= Character.MIN_SURROGATE && value <= Character.MAX_SURROGATE) {
        throw new IllegalArgumentException("invalid Unicode code point");
      }
    }

    @Override
    public String toString() {
      return new String(Character.toChars(value));
    }
  }

  record ClassValue(CoreType type, CoreType reflectedType, ReflectionBinding binding) {
    ClassValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(reflectedType, "reflectedType");
      Objects.requireNonNull(binding, "binding");
    }

    ClassValue(CoreType type, CoreType reflectedType, AnnotationRuntime annotations) {
      this(type, reflectedType, new ReflectionBinding(annotations, false));
    }

    AnnotationRuntime annotations() {
      return binding.annotations();
    }

    ClassValue snapshot() {
      return new ClassValue(type, reflectedType, binding.snapshot());
    }

    @Override
    public String toString() {
      return annotations().name(reflectedType);
    }
  }

  record FieldValue(
      CoreType type,
      CoreType ownerType,
      DefinitionOccurrenceId owner,
      String name,
      int index,
      CoreType fieldType,
      ReflectionBinding binding) {
    FieldValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(ownerType, "ownerType");
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(fieldType, "fieldType");
      Objects.requireNonNull(binding, "binding");
      if (index < 0) throw new IllegalArgumentException("field index must not be negative");
    }

    FieldValue(
        CoreType type,
        CoreType ownerType,
        DefinitionOccurrenceId owner,
        String name,
        int index,
        CoreType fieldType,
        AnnotationRuntime annotations) {
      this(
          type,
          ownerType,
          owner,
          name,
          index,
          fieldType,
          new ReflectionBinding(annotations, false));
    }

    AnnotationRuntime annotations() {
      return binding.annotations();
    }

    FieldValue snapshot() {
      return new FieldValue(type, ownerType, owner, name, index, fieldType, binding.snapshot());
    }

    FieldKey key() {
      return new FieldKey(owner.representative(), name);
    }
  }

  record ConstructorValue(
      CoreType type,
      CoreType ownerType,
      DefinitionOccurrenceId declaration,
      ReflectionBinding binding) {
    ConstructorValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(ownerType, "ownerType");
      Objects.requireNonNull(declaration, "declaration");
      Objects.requireNonNull(binding, "binding");
    }

    ConstructorValue(
        CoreType type,
        CoreType ownerType,
        DefinitionOccurrenceId declaration,
        AnnotationRuntime annotations) {
      this(type, ownerType, declaration, new ReflectionBinding(annotations, false));
    }

    AnnotationRuntime annotations() {
      return binding.annotations();
    }

    ConstructorValue snapshot() {
      return new ConstructorValue(type, ownerType, declaration, binding.snapshot());
    }
  }

  record ParameterValue(
      CoreType type,
      Closure function,
      String name,
      CoreType valueType,
      AnnotationRuntime annotations) {
    ParameterValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(function, "function");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(valueType, "valueType");
      Objects.requireNonNull(annotations, "annotations");
    }
  }

  record FunctionContextValue(CoreType type, Closure function) {
    FunctionContextValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(function, "function");
    }
  }

  record ParameterContextValue(CoreType type, ParameterValue parameter) {
    ParameterContextValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(parameter, "parameter");
    }
  }

  record FieldContextValue(CoreType type, FieldValue field) {
    FieldContextValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(field, "field");
    }
  }

  static final class FunctionInvocationValue {
    final CoreType type;
    private final java.util.function.Supplier<Object> continuation;
    private boolean proceeded;

    FunctionInvocationValue(CoreType type, java.util.function.Supplier<Object> continuation) {
      this.type = Objects.requireNonNull(type, "type");
      this.continuation = Objects.requireNonNull(continuation, "continuation");
    }

    Object proceed(Node location) {
      if (proceeded) {
        throw new NormGuestException(
            RuntimeErrorCode.INVALID_ARGUMENT,
            "FunctionInvocation.proceed() can be called once",
            location);
      }
      proceeded = true;
      return continuation.get();
    }
  }

  record FunctionCompletionValue(CoreType type, boolean succeeded) {
    FunctionCompletionValue {
      Objects.requireNonNull(type, "type");
    }
  }

  @TruffleBoundary
  private static List<Object> copyList(List<Object> values) {
    List<Object> result = new ArrayList<>(values.size());
    values.forEach(value -> result.add(copy(value)));
    return result;
  }

  private static boolean equalLists(List<Object> left, List<Object> right) {
    if (left.size() != right.size()) return false;
    for (int index = 0; index < left.size(); index++) {
      if (!equal(left.get(index), right.get(index))) return false;
    }
    return true;
  }

  private static boolean equalMaps(MapValue left, MapValue right) {
    if (left.values.size() != right.values.size()) return false;
    for (Map.Entry<RuntimeKey, Object> entry : left.values.entrySet()) {
      if (!right.values.containsKey(entry.getKey())
          || !equal(entry.getValue(), right.values.get(entry.getKey()))) return false;
    }
    return true;
  }

  private static boolean equalSets(SetValue left, SetValue right) {
    return left.values.equals(right.values);
  }

  private static int orderedHash(int seed, List<Object> values) {
    int result = seed;
    for (Object value : values) result = 31 * result + hash(value);
    return result;
  }

  abstract static class ContainerValue {
    ValueObservations observations;
  }

  static final class ArrayValue extends ContainerValue {
    final CoreType type;
    final List<Object> values;

    ArrayValue(CoreType type, List<Object> values) {
      this.type = type;
      this.values = RuntimeCollections.list(values);
    }
  }

  static final class ListValue extends ContainerValue {
    final CoreType type;
    final List<Object> values;

    ListValue(CoreType type) {
      this(type, new ArrayList<>());
    }

    ListValue(CoreType type, List<Object> values) {
      this.type = type;
      this.values = RuntimeCollections.list(values);
    }
  }

  static final class MapValue extends ContainerValue {
    final CoreType type;
    final Map<RuntimeKey, Object> values = RuntimeCollections.map();

    MapValue(CoreType type) {
      this.type = type;
    }
  }

  static final class SetValue extends ContainerValue {
    final CoreType type;
    final java.util.Set<RuntimeKey> values = RuntimeCollections.set();

    SetValue(CoreType type) {
      this.type = type;
    }
  }

  static final class StackValue extends ContainerValue {
    final CoreType type;
    final Deque<Object> values = RuntimeCollections.deque();

    StackValue(CoreType type) {
      this.type = type;
    }
  }

  static final class QueueValue extends ContainerValue {
    final CoreType type;
    final Deque<Object> values = RuntimeCollections.deque();

    QueueValue(CoreType type) {
      this.type = type;
    }
  }

  static final class DequeValue extends ContainerValue {
    final CoreType type;
    final Deque<Object> values = RuntimeCollections.deque();

    DequeValue(CoreType type) {
      this.type = type;
    }
  }

  static final class BuilderValue extends ContainerValue {
    final CoreType type;
    private final RuntimeCollections.Storage<StringBuilder> state;

    BuilderValue(CoreType type) {
      this(type, "");
    }

    BuilderValue(CoreType type, String value) {
      this.type = Objects.requireNonNull(type, "type");
      state =
          new RuntimeCollections.Storage<>(
              new StringBuilder(value),
              builder -> new StringBuilder(builder.toString()),
              (target, source) -> {
                target.setLength(0);
                target.append(source);
              });
    }

    @Override
    public String toString() {
      return value().toString();
    }

    String value() {
      return state.read().toString();
    }

    StringBuilder writable() {
      return state.write();
    }
  }

  static final class PairValue extends ContainerValue {
    final CoreType type;
    private final RuntimeCollections.Storage<Object[]> state;

    PairValue(CoreType type, Object first, Object second) {
      this.type = type;
      state =
          new RuntimeCollections.Storage<>(
              new Object[] {first, second},
              Object[]::clone,
              (target, source) -> System.arraycopy(source, 0, target, 0, 2));
    }

    Object first() {
      return state.read()[0];
    }

    Object second() {
      return state.read()[1];
    }

    void first(Object value) {
      state.write()[0] = value;
    }

    void second(Object value) {
      state.write()[1] = value;
    }
  }

  static final class NativeIteratorValue {
    final CoreType type;
    final Iterator<Object> iterator;
    private boolean active = true;

    NativeIteratorValue(CoreType elementType, Iterator<Object> iterator) {
      type =
          new CoreType.Declared(
              new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.NativeIterator")),
              List.of(elementType),
              CoreValueCategory.IDENTITY,
              CoreNullability.NON_NULL);
      this.iterator = Objects.requireNonNull(iterator, "iterator");
    }

    boolean hasNext() {
      boolean available = iterator.hasNext();
      if (!available) active = false;
      return available;
    }

    boolean active() {
      return active;
    }
  }

  static final class OpaqueValue {
    final Object owner;
    final CoreType type;
    final Object value;
    final String displayName;
    final AggregateInfo aggregateInfo;

    OpaqueValue(CoreType type, Object value, String displayName) {
      this(type, value, displayName, null);
    }

    OpaqueValue(CoreType type, Object value, String displayName, AggregateInfo aggregateInfo) {
      this(type, value, displayName, aggregateInfo, null);
    }

    OpaqueValue(
        CoreType type,
        Object value,
        String displayName,
        AggregateInfo aggregateInfo,
        Object owner) {
      this.owner = owner;
      this.type = Objects.requireNonNull(type, "type");
      this.value = Objects.requireNonNull(value, "value");
      this.displayName = Objects.requireNonNull(displayName, "displayName");
      this.aggregateInfo = aggregateInfo;
      if (aggregateInfo != null && aggregateInfo.annotations() != null)
        aggregateInfo.annotations().retain(this);
      else if (owner instanceof AnnotationRuntime annotations) annotations.retain(this);
    }

    boolean sameValue(OpaqueValue other) {
      if (type instanceof CoreType.Declared declared
          && declared.category() == CoreValueCategory.VALUE) {
        return type.equals(other.type) && value.equals(other.value);
      }
      return !(other.type instanceof CoreType.Declared declared
              && declared.category() == CoreValueCategory.VALUE)
          && value == other.value;
    }

    int valueHash() {
      return type instanceof CoreType.Declared declared
              && declared.category() == CoreValueCategory.VALUE
          ? 31 * type.hashCode() + value.hashCode()
          : System.identityHashCode(value);
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  static final class OpaqueResource {
    final CoreType type;
    final ManagedResource resource;
    final Object borrowingOwner;
    private final Object host;
    final String displayName;
    final AggregateInfo aggregateInfo;

    OpaqueResource(CoreType type, ManagedResource resource, String displayName) {
      this(type, resource, displayName, null);
    }

    OpaqueResource(
        CoreType type, ManagedResource resource, String displayName, AggregateInfo aggregateInfo) {
      this(type, resource, displayName, aggregateInfo, resource.borrowingOwner());
    }

    OpaqueResource(
        CoreType type,
        ManagedResource resource,
        String displayName,
        AggregateInfo aggregateInfo,
        Object borrowingOwner) {
      this.borrowingOwner = borrowingOwner;
      this.type = Objects.requireNonNull(type, "type");
      this.resource = Objects.requireNonNull(resource, "resource");
      this.host = resource.hostValue();
      this.displayName = Objects.requireNonNull(displayName, "displayName");
      this.aggregateInfo = aggregateInfo;
      if (aggregateInfo != null && aggregateInfo.annotations() != null)
        aggregateInfo.annotations().retain(this);
    }

    @Override
    public String toString() {
      return displayName;
    }

    Object hostValue() {
      return host;
    }

    void closedExternally() {
      resource.closedExternally();
    }
  }

  static final class EnumValue {
    private final DefinitionId definition;
    private final CoreType type;
    private final String enumName;
    private final String variantKey;
    private final List<Object> payload;
    private final AnnotationRuntime annotations;

    EnumValue(
        DefinitionId definition,
        CoreType type,
        String enumName,
        String variantKey,
        List<Object> payload,
        AnnotationRuntime annotations) {
      this.definition = Objects.requireNonNull(definition, "definition");
      this.type = Objects.requireNonNull(type, "type");
      this.enumName = Objects.requireNonNull(enumName, "enumName");
      this.variantKey = Objects.requireNonNull(variantKey, "variantKey");
      if (variantKey.isBlank()) throw new IllegalArgumentException("variant key must not be blank");
      this.payload = List.copyOf(copyList(payload));
      this.annotations = annotations;
    }

    AnnotationRuntime annotations() {
      return annotations;
    }

    List<Object> payloadValues() {
      return payload;
    }

    EnumValue rebuildPayload(List<Object> values) {
      return new EnumValue(definition, type, enumName, variantKey, values, annotations);
    }

    DefinitionId definition() {
      return definition;
    }

    CoreType type() {
      return type;
    }

    String variantKey() {
      return variantKey;
    }

    Object field(int index) {
      return copy(payload.get(index));
    }

    int fieldCount() {
      return payload.size();
    }

    private boolean sameValue(EnumValue other) {
      return definition.equals(other.definition)
          && type.equals(other.type)
          && variantKey.equals(other.variantKey)
          && equalLists(payload, other.payload);
    }

    private int valueHash() {
      int result = Objects.hash(definition, type, variantKey);
      return 31 * result + orderedHash(0, payload);
    }

    @Override
    public boolean equals(Object other) {
      return this == other || other instanceof EnumValue value && sameValue(value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(definition, type, variantKey);
    }

    @Override
    public String toString() {
      if (payload.isEmpty()) return enumName + "." + variantKey;
      return enumName
          + "."
          + variantKey
          + "("
          + payload.stream()
              .map(RuntimeText::stringify)
              .collect(java.util.stream.Collectors.joining(", "))
          + ")";
    }
  }

  static final class RangeValue {
    final CoreType type;
    final int start;
    final int end;
    final int step;

    RangeValue(CoreType type, int start, int end) {
      this(type, start, end, 1);
    }

    RangeValue(CoreType type, int start, int end, int step) {
      this.type = Objects.requireNonNull(type, "type");
      this.start = start;
      this.end = end;
      this.step = step;
    }

    int size() {
      BigInteger distance =
          step > 0
              ? BigInteger.valueOf(end).subtract(BigInteger.valueOf(start))
              : BigInteger.valueOf(start).subtract(BigInteger.valueOf(end));
      if (distance.signum() <= 0) return 0;
      BigInteger stride = BigInteger.valueOf(step).abs();
      return distance.subtract(BigInteger.ONE).divide(stride).add(BigInteger.ONE).intValueExact();
    }

    Iterator<Object> iterator() {
      return new Iterator<>() {
        private int current = start;
        private boolean exhausted;

        @Override
        public boolean hasNext() {
          return !exhausted && (step > 0 ? current < end : current > end);
        }

        @Override
        public Object next() {
          int value = current;
          try {
            current = Math.addExact(current, step);
          } catch (ArithmeticException exception) {
            exhausted = true;
          }
          return value;
        }
      };
    }
  }

  static final class RuntimeKey {
    final Object value;

    RuntimeKey(Object value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object other) {
      return this == other || other instanceof RuntimeKey key && equal(value, key.value);
    }

    @Override
    public int hashCode() {
      return hash(value);
    }

    boolean requiresSchemaReindex() {
      return switch (value) {
        case ObjectValue object -> isValueObject(object);
        case ContainerValue _, EnumValue _ -> true;
        default -> false;
      };
    }
  }

  sealed interface DispatchTarget
      permits DispatchTarget.Callable, DispatchTarget.HostMethod, DispatchTarget.Intrinsic {
    record Callable(
        CallTarget target,
        List<CoreType> receiverTypeArguments,
        boolean specializedReceiverTypeArguments)
        implements DispatchTarget {
      public Callable {
        Objects.requireNonNull(target, "target");
        receiverTypeArguments = List.copyOf(receiverTypeArguments);
      }

      Callable(CallTarget target) {
        this(target, List.of(), false);
      }

      Callable(CallTarget target, List<CoreType> receiverTypeArguments) {
        this(target, receiverTypeArguments, true);
      }
    }

    record HostMethod(DefinitionId definition) implements DispatchTarget {
      public HostMethod {
        Objects.requireNonNull(definition, "definition");
      }
    }

    record Intrinsic(IntrinsicId intrinsic, IntrinsicOperation operation)
        implements DispatchTarget {
      Intrinsic(IntrinsicId intrinsic) {
        this(intrinsic, IntrinsicDispatcher.resolve(intrinsic));
      }

      @Override
      public boolean equals(Object other) {
        return other instanceof Intrinsic target && intrinsic == target.intrinsic;
      }

      @Override
      public int hashCode() {
        return intrinsic.hashCode();
      }
    }
  }

  sealed interface ObjectInfo permits AggregateInfo {
    DefinitionId definition();

    String name();

    int fieldCount();

    Map<DefinitionId, DispatchTarget> dispatch();

    java.util.Set<DefinitionId> ancestors();

    AnnotationRuntime annotations();
  }

  record AggregateInfo(
      DefinitionId definition,
      String name,
      int fieldCount,
      List<FieldPlan> fields,
      Map<DefinitionId, DispatchTarget> dispatch,
      java.util.Set<DefinitionId> ancestors,
      AnnotationRuntime annotations,
      StateViewLeases stateViews)
      implements ObjectInfo {
    AggregateInfo(
        DefinitionId definition,
        String name,
        int fieldCount,
        List<FieldPlan> fields,
        Map<DefinitionId, DispatchTarget> dispatch,
        java.util.Set<DefinitionId> ancestors,
        AnnotationRuntime annotations) {
      this(
          definition,
          name,
          fieldCount,
          fields,
          dispatch,
          ancestors,
          annotations,
          new StateViewLeases());
    }

    AggregateInfo {
      fields = List.copyOf(fields);
      if (fields.size() != fieldCount) {
        throw new IllegalArgumentException("aggregate field plans must be complete");
      }
      for (int index = 0; index < fields.size(); index++) {
        if (fields.get(index).index() != index) {
          throw new IllegalArgumentException("aggregate field plans must be ordered");
        }
      }
      dispatch = Map.copyOf(dispatch);
      ancestors = java.util.Set.copyOf(ancestors);
      if (!ancestors.contains(definition)) {
        throw new IllegalArgumentException("aggregate ancestors must include itself");
      }
    }
  }

  static final class StateViewLeases {
    private final java.util.Set<RuntimeObjectState.ViewMap> views =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    synchronized void retain(RuntimeObjectState.ViewMap view) {
      views.add(view);
    }
  }

  record FieldPlan(
      DefinitionOccurrenceId owner,
      String name,
      int index,
      boolean publicField,
      List<CoreInterceptor> interceptors,
      CoreType type,
      Optional<CallTarget> initializer) {
    FieldPlan {
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(name, "name");
      if (index < 0) throw new IllegalArgumentException("field index must not be negative");
      interceptors = List.copyOf(interceptors);
      Objects.requireNonNull(type, "type");
      initializer = Objects.requireNonNull(initializer, "initializer");
    }

    FieldPlan project(List<CoreType> parentArguments) {
      var projected =
          initializer.map(
              target ->
                  (CallTarget)
                      new com.oracle.truffle.api.nodes.RootNode(null) {
                        @Override
                        public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
                          var incoming = frame.getArguments();
                          var outgoing = new Object[parentArguments.size() + 2];
                          outgoing[0] = incoming[0];
                          outgoing[1] = incoming[1];
                          for (int index = 0; index < parentArguments.size(); index++)
                            outgoing[index + 2] =
                                parentArguments
                                    .get(index)
                                    .substitute(parameter -> (CoreType) incoming[parameter + 2]);
                          return target.call(outgoing);
                        }
                      }.getCallTarget());
      return new FieldPlan(
          owner,
          name,
          index,
          publicField,
          interceptors,
          type.substitute(parentArguments::get),
          projected);
    }

    FieldKey key() {
      return new FieldKey(owner.representative(), name);
    }
  }

  record FieldKey(DefinitionId owner, String name) {
    FieldKey {
      Objects.requireNonNull(owner);
      Objects.requireNonNull(name);
    }
  }

  static <T> T stateView(RuntimeCollections.Storage<T> storage) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    return migration == null ? storage.original() : migration.view(storage);
  }

  static <T> T stateWrite(RuntimeCollections.Storage<T> storage) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    return migration == null ? storage.original() : migration.write(storage);
  }

  static void afterStateCommit(Runnable effect) {
    RuntimeStateTransaction current = RuntimeStateTransaction.current();
    if (current == null) effect.run();
    else current.afterCommit(effect);
  }

  static RuntimeObjectState.CellValue stateCellView(RuntimeObjectState.Cell cell) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    return migration == null ? cell.value : migration.cellView(cell);
  }

  static void stateCellWrite(RuntimeObjectState.Cell cell, RuntimeObjectState.CellValue value) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    if (migration == null) cell.value = value;
    else migration.writeCell(cell, value);
  }

  static Map<FieldKey, Object> stateStorage(Map<FieldKey, Object> cells) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    return migration == null ? cells : migration.storage(cells);
  }

  static RuntimeObjectState.ViewMap stateView(RuntimeObjectState state) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    return migration == null ? state.current() : migration.objectView(state);
  }

  static boolean migrating(GuestCallbackScheduler scheduler) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.current();
    return migration != null
        && migration.execution() != null
        && migration.execution().callbacks() == scheduler;
  }

  static RuntimeStateTransaction prepareState(Iterable<Object> roots, ExecutionState execution) {
    RuntimeStateTransaction migration = RuntimeStateTransaction.open(execution);
    try {
      migration.include(roots);
      return migration;
    } catch (RuntimeException | Error failure) {
      migration.close();
      throw failure;
    }
  }

  static class StateMigrationFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    StateMigrationFailure(String message) {
      super(message);
    }
  }

  static final class StateMigrationPending extends StateMigrationFailure {
    private static final long serialVersionUID = 1L;

    StateMigrationPending(String message) {
      super(message);
    }
  }

  static final class ObjectValue {
    final ObjectInfo objectInfo;
    private final CoreType birthType;
    private final RuntimeObjectState state;
    final Map<FieldKey, Object> configuration;
    Object hostValue;
    AggregateInfo hostSchema;
    boolean dispatchToHost;
    FieldObservations observations;

    CoreType type(AggregateInfo view) {
      return state.type(view, birthType);
    }

    CoreType type() {
      var execution = RuntimeValueExecution.current();
      var view = execution == null ? selectedInfo() : info(execution);
      return state.type(view, birthType);
    }

    Object readField(int field) {
      return readField(((AggregateInfo) objectInfo).fields().get(field).key(), null);
    }

    Object rawField(int field) {
      return rawField(((AggregateInfo) objectInfo).fields().get(field).key());
    }

    Object rawField(FieldKey field) {
      return rawField(field, selectedInfo());
    }

    void writeRawField(int field, Object value) {
      writeRawField(((AggregateInfo) objectInfo).fields().get(field).key(), value);
    }

    void writeRawField(FieldKey field, Object value) {
      writeRawField(field, value, selectedInfo());
    }

    FieldKey fieldKey(int index) {
      return cells().keySet().stream().skip(index).findFirst().orElseThrow();
    }

    Object readField(FieldKey field, ExecutionState execution) {
      return readField(field, execution == null ? selectedInfo() : info(execution), execution);
    }

    Object readField(FieldKey field, AggregateInfo view, ExecutionState execution) {
      if (execution != null) ensureLayout(view, execution);
      Map<FieldKey, Object> selected = state.view(view, type());
      if (!selected.containsKey(field) && !configurationFor(view).containsKey(field))
        throw new IllegalArgumentException("object field is absent: " + field);
      if (observations != null) afterStateCommit(() -> observations.read(field, view));
      return configurationFor(view).containsKey(field)
          ? storage(configuration).get(field)
          : selected.get(field);
    }

    Object rawField(FieldKey field, AggregateInfo view) {
      Map<FieldKey, Object> selected = state.view(view, type());
      return configurationFor(view).containsKey(field)
          ? storage(configuration).get(field)
          : selected.get(field);
    }

    void writeRawField(FieldKey field, Object value, AggregateInfo view) {
      var selected = (RuntimeObjectState.ViewMap) state.view(view, type());
      selected.checkWrite(field, value);
      if (configurationFor(view).containsKey(field)) storage(configuration).put(field, value);
      else selected.put(field, value);
    }

    Map<FieldKey, Object> configurationFor(AggregateInfo view) {
      var values = new LinkedHashMap<FieldKey, Object>();
      storage(configuration)
          .forEach(
              (key, value) -> {
                if (state.sameFieldType((AggregateInfo) objectInfo, view, type(), key))
                  values.put(key, value);
              });
      return values;
    }

    private AggregateInfo selectedInfo() {
      RuntimeStateTransaction migration = RuntimeStateTransaction.current();
      if (migration != null) return migration.objectInfo(this);
      var execution = RuntimeValueExecution.current();
      return execution == null ? state.currentInfo() : info(execution);
    }

    AggregateInfo info(ExecutionState execution) {
      if (execution != null) {
        AggregateInfo current = execution.runtime().aggregateInfo(objectInfo.definition());
        if (current != null) return current;
      }
      return (AggregateInfo) objectInfo;
    }

    private Map<FieldKey, Object> storage(Map<FieldKey, Object> values) {
      RuntimeStateTransaction migration = RuntimeStateTransaction.current();
      return migration == null ? values : migration.storage(values);
    }

    void ensureField(FieldKey key, ExecutionState execution) {
      ensureLayout(info(execution), execution);
      if (!state.view(info(execution), type()).containsKey(key)
          && !configurationFor(info(execution)).containsKey(key))
        throw new StateMigrationFailure("object field is absent: " + key);
    }

    void ensureLayout(AggregateInfo view, ExecutionState execution) {
      RuntimeStateTransaction current = RuntimeStateTransaction.current();
      if (current != null) {
        current.prepare(this, view);
        return;
      }
      if (state.schemaMatches(view, type())
          && view.fields().stream().allMatch(field -> cells().containsKey(field.key()))) return;
      try (RuntimeStateTransaction migration = prepareState(List.of(), execution)) {
        migration.prepare(this, view);
        migration.commit();
      }
    }

    void fieldChanged(FieldKey field, Object previous, AggregateInfo view) {
      if (observations != null) {
        afterStateCommit(
            () -> {
              observations.bind(field, view);
              Object current = rawField(field, view);
              if (!equal(previous, current)) observations.changed(field, view, previous, current);
            });
      }
    }

    ObjectValue(ObjectInfo objectInfo, CoreType type) {
      this(objectInfo, type, new LinkedHashMap<>(), new LinkedHashMap<>());
      ((AggregateInfo) objectInfo)
          .fields()
          .forEach(field -> cells().putIfAbsent(field.key(), null));
    }

    ObjectValue(
        ObjectInfo objectInfo,
        CoreType type,
        Map<FieldKey, Object> cells,
        Map<FieldKey, Object> configuration) {
      this.objectInfo = objectInfo;
      this.birthType = type;
      this.state = RuntimeObjectState.shared((AggregateInfo) objectInfo, type, cells);
      this.configuration = configuration;
      if (objectInfo.annotations() != null) objectInfo.annotations().retain(this);
    }

    Map<FieldKey, Object> stateCells() {
      return cells();
    }

    RuntimeObjectState state() {
      return state;
    }

    Map<FieldKey, Object> cells() {
      return state.view(selectedInfo(), type());
    }

    void attachHost(Object value) {
      Objects.requireNonNull(value, "value");
      if (hostValue == value) return;
      if (hostValue != null)
        throw new IllegalStateException("runtime object already has a host value");
      hostValue = value;
      hostSchema = selectedInfo();
    }

    private boolean sameValue(ObjectValue other) {
      return isValueObject(this)
          && isValueObject(other)
          && type().equals(other.type())
          && cells().keySet().equals(other.cells().keySet())
          && cells().keySet().stream().allMatch(key -> equal(rawField(key), other.rawField(key)));
    }
  }
}
