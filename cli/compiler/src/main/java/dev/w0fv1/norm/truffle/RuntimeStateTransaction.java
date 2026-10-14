package dev.w0fv1.norm.truffle;

import static dev.w0fv1.norm.truffle.RuntimeValues.*;

import com.oracle.truffle.api.CallTarget;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class RuntimeStateTransaction implements AutoCloseable {
  private static final ThreadLocal<RuntimeStateTransaction> CURRENT = new ThreadLocal<>();

  static RuntimeStateTransaction current() {
    return CURRENT.get();
  }

  static RuntimeStateTransaction open(ExecutionState execution) {
    var transaction = new RuntimeStateTransaction(execution, CURRENT.get());
    CURRENT.set(transaction);
    return transaction;
  }

  static boolean requiresMigration(Object value, ExecutionState execution) {
    return new RuntimeStateTransaction(execution, CURRENT.get())
        .requiresMigration(value, new java.util.IdentityHashMap<>());
  }

  private boolean requiresMigration(
      Object value, java.util.IdentityHashMap<Object, Boolean> checked) {
    if (value == null || checked.put(value, Boolean.TRUE) != null) return false;
    if (value instanceof Closure) return false;
    if (value instanceof EnumValue enumeration
        && enumeration.annotations() != null
        && enumeration.annotations() != execution.runtime()) return true;
    if (value instanceof ObjectValue object
        && !object.state().schemaMatches(object.info(execution), object.type())
        && !object.state().hasView(object.info(execution), object.type())) return true;
    for (Object child : children(value)) if (requiresMigration(child, checked)) return true;
    return false;
  }

  ExecutionState execution() {
    return execution;
  }

  void afterCommit(Runnable effect) {
    effects.add(effect);
  }

  void writeCell(RuntimeObjectState.Cell cell, RuntimeObjectState.CellValue value) {
    cells.put(cell, value);
  }

  private final ExecutionState execution;
  private final RuntimeStateTransaction parent;
  private final java.util.IdentityHashMap<Map<FieldKey, Object>, Map<FieldKey, Object>> staged =
      new java.util.IdentityHashMap<>();
  private final List<Object> roots = new ArrayList<>();
  private final java.util.IdentityHashMap<Object, Object> replacements =
      new java.util.IdentityHashMap<>();
  private final java.util.IdentityHashMap<Map<FieldKey, Object>, java.util.Set<FieldKey>>
      initializing = new java.util.IdentityHashMap<>();
  private final java.util.IdentityHashMap<RuntimeCollections.Storage<?>, Object> containers =
      new java.util.IdentityHashMap<>();
  private final java.util.IdentityHashMap<RuntimeObjectState.Cell, RuntimeObjectState.CellValue>
      cells = new java.util.IdentityHashMap<>();
  private final java.util.IdentityHashMap<RuntimeObjectState, RuntimeObjectState.MigrationContext>
      objects = new java.util.IdentityHashMap<>();
  private final List<RuntimeObjectState.MigrationContext> transitions = new ArrayList<>();
  private LiveBindings.StateTransition transition;
  private final List<Runnable> effects = new ArrayList<>();
  private boolean closed;
  private boolean committed;

  RuntimeStateTransaction(ExecutionState execution, RuntimeStateTransaction parent) {
    this.execution = execution;
    this.parent = parent;
  }

  Map<FieldKey, Object> storage(Map<FieldKey, Object> cells) {
    return staged.computeIfAbsent(
        cells, key -> new JournalMap(parent == null ? key : parent.storage(key)));
  }

  RuntimeObjectState.CellValue cellView(RuntimeObjectState.Cell cell) {
    var value = cells.get(cell);
    return value != null ? value : parent == null ? cell.value : parent.cellView(cell);
  }

  private static final class JournalMap extends LinkedHashMap<FieldKey, Object> {
    private static final long serialVersionUID = 1L;
    boolean dirty;

    JournalMap(Map<FieldKey, Object> source) {
      super(source);
    }

    @Override
    public Object put(FieldKey key, Object value) {
      dirty = true;
      return super.put(key, value);
    }

    @Override
    public Object remove(Object key) {
      dirty = true;
      return super.remove(key);
    }

    @Override
    public void clear() {
      dirty = true;
      super.clear();
    }

    @Override
    public void putAll(Map<? extends FieldKey, ?> values) {
      dirty = true;
      super.putAll(values);
    }
  }

  @SuppressWarnings("unchecked")
  <T> T view(RuntimeCollections.Storage<T> storage) {
    if (containers.containsKey(storage)) return (T) containers.get(storage);
    return parent == null ? storage.original() : parent.view(storage);
  }

  @SuppressWarnings("unchecked")
  <T> T write(RuntimeCollections.Storage<T> storage) {
    return (T) containers.computeIfAbsent(storage, key -> storage.snapshot(view(storage)));
  }

  AggregateInfo objectInfo(ObjectValue object) {
    var context = objects.get(object.state());
    return context != null
        ? context.targetInfo()
        : parent == null ? object.state().currentInfo() : parent.objectInfo(object);
  }

  RuntimeObjectState.ViewMap objectView(RuntimeObjectState state) {
    var context = objects.get(state);
    return context != null
        ? context.target()
        : parent == null ? state.current() : parent.objectView(state);
  }

  RuntimeObjectState.MigrationContext migrate(ObjectValue object, AggregateInfo view) {
    return migrate(object, view, object.type());
  }

  RuntimeObjectState.MigrationContext migrate(
      ObjectValue object, AggregateInfo view, CoreType desiredType) {
    var existing = objects.get(object.state());
    if (existing != null && existing.targetInfo() == view) return existing;
    if (object.state().shapeChanges(view, object.type())
        && execution != null
        && execution.liveBindings() != null
        && transition == null) {
      transition = execution.liveBindings().stateTransition();
    }
    if (transition != null) {
      var checked = new java.util.IdentityHashMap<Object, Boolean>();
      transition.roots().forEach(root -> checkWritableHandles(root, checked));
    }
    var context = object.state().prepare(object, view, desiredType);
    if (context.shapeChanged()
        && execution != null
        && execution.liveBindings() != null
        && transition == null) {
      transition = execution.liveBindings().stateTransition();
      context = object.state().prepare(object, view, desiredType);
    }
    objects.put(object.state(), context);
    transitions.add(context);
    return context;
  }

  void include(Iterable<Object> roots) {
    var retained = new ArrayList<Object>();
    roots.forEach(retained::add);
    if (execution != null && execution.liveBindings() != null)
      retained.addAll(execution.liveBindings().stateRoots());
    var types = new java.util.HashSet<CoreTypeConstructor>();
    var iterators = new ArrayList<NativeIteratorValue>();
    var scanned = new java.util.IdentityHashMap<Object, Boolean>();
    retained.forEach(value -> scanTypeEvolution(value, scanned, types, iterators));
    for (var iterator : iterators)
      if (iterator.active() && typeUses(iterator.type, types))
        throw new StateMigrationPending(
            "State update awaits an active iterator with an evolving element contract");
    var checked = new java.util.IdentityHashMap<Object, Boolean>();
    retained.forEach(value -> checkWritableHandles(value, checked));
    this.roots.addAll(retained);
  }

  private void scanTypeEvolution(
      Object value,
      java.util.IdentityHashMap<Object, Boolean> checked,
      java.util.Set<CoreTypeConstructor> types,
      List<NativeIteratorValue> iterators) {
    if (value == null || checked.put(value, Boolean.TRUE) != null) return;
    if (value instanceof NativeIteratorValue iterator) iterators.add(iterator);
    if (value instanceof ObjectValue object
        && isValueObject(object)
        && object.state().shapeChanges(object.info(execution), object.type())
        && object.type() instanceof CoreType.Declared declared) types.add(declared.constructor());
    if (value instanceof EnumValue enumeration
        && enumeration.annotations() != null
        && execution != null
        && !enumeration
            .annotations()
            .typeFingerprint(enumeration.type())
            .equals(execution.runtime().typeFingerprint(enumeration.type()))
        && enumeration.type() instanceof CoreType.Declared declared)
      types.add(declared.constructor());
    for (Object child : children(value)) scanTypeEvolution(child, checked, types, iterators);
  }

  private boolean typeUses(CoreType type, java.util.Set<CoreTypeConstructor> constructors) {
    return switch (type) {
      case CoreType.Declared declared ->
          constructors.contains(declared.constructor())
              || declared.arguments().stream()
                  .anyMatch(argument -> typeUses(argument, constructors));
      case CoreType.Reference reference -> typeUses(reference.target(), constructors);
      case CoreType.Function function ->
          typeUses(function.returnType(), constructors)
              || function.parameterTypes().stream()
                  .anyMatch(argument -> typeUses(argument, constructors));
      default -> false;
    };
  }

  private void checkWritableHandles(
      Object value, java.util.IdentityHashMap<Object, Boolean> checked) {
    if (value == null || checked.put(value, Boolean.TRUE) != null) return;
    if (value instanceof FieldReference reference) {
      CoreType declared =
          RuntimeObjectState.Schema.of(reference.view(), reference.receiver().type())
              .fields()
              .get(reference.field());
      requireWritableHandle(reference.receiver(), reference.field(), declared);
    } else if (value instanceof OpaqueValue opaque
        && opaque.value instanceof AnnotationRuntime.FieldHandle handle) {
      requireWritableHandle(handle.receiver(), handle.field().key(), handle.field().fieldType());
    }
    if (value instanceof Closure closure && closure.snapshotVersion() != null) {
      var retained = new java.util.IdentityHashMap<Object, Boolean>();
      requireSnapshotGraphStable(closure.receiver(), retained);
      for (Object capture : closure.captures()) requireSnapshotGraphStable(capture, retained);
    }
    for (Object child : children(value)) checkWritableHandles(child, checked);
  }

  private void requireSnapshotGraphStable(
      Object value, java.util.IdentityHashMap<Object, Boolean> checked) {
    if (value == null || checked.put(value, Boolean.TRUE) != null) return;
    if (value instanceof ObjectValue object
        && isValueObject(object)
        && object.state().shapeChanges(object.info(execution), object.type()))
      throw new StateMigrationPending(
          "State update awaits a retained function snapshot with value keys");
    for (Object child : children(value)) requireSnapshotGraphStable(child, checked);
  }

  private void requireWritableHandle(ObjectValue object, FieldKey key, CoreType type) {
    AggregateInfo target = execution == null ? objectInfo(object) : object.info(execution);
    var candidate = RuntimeObjectState.Schema.of(target, object.type()).fields().get(key);
    if (!Objects.equals(type, candidate))
      throw new StateMigrationPending(
          "State update awaits a retained writable field handle: " + key);
  }

  private void visit(Object value) {
    transform(value, CoreType.DYNAMIC);
  }

  Object transform(Object value, CoreType expected) {
    if (value == null) return null;
    if (replacements.containsKey(value)) {
      Object retained = replacements.get(value);
      validateType(expected, retained);
      return retained;
    }
    Object transformed =
        switch (value) {
          case ObjectValue object -> {
            ObjectValue target = isValueObject(object) ? copyObject(object) : object;
            replacements.put(value, target);
            CoreType desiredType =
                expected instanceof CoreType.Declared declared
                        && target.type() instanceof CoreType.Declared original
                        && declared.constructor().equals(original.constructor())
                    ? instanceType(declared)
                    : target.type();
            prepare(target, target.info(execution), desiredType);
            var context = objects.get(target.state());
            var fields =
                context == null
                    ? RuntimeObjectState.Schema.of(target.info(execution), target.type()).fields()
                    : context.targetFields();
            for (var field : fields.entrySet()) {
              if (!target.cells().containsKey(field.getKey())) continue;
              Object previous = target.cells().get(field.getKey());
              Object adopted = transform(previous, field.getValue());
              if (adopted != previous) {
                if (context == null) target.writeRawField(field.getKey(), adopted);
                else context.stageTransform(field.getKey(), adopted);
              }
            }
            if (context != null) context.validateCoverage();
            yield target;
          }
          case EnumValue enumeration -> {
            var adopted = (EnumValue) MigrationPolicy.migrateValue(enumeration, execution);
            replacements.put(value, adopted);
            var declaration =
                (dev.w0fv1.norm.core.CoreDefinition.Enum)
                    execution.runtime().program().structure(adopted.definition()).orElseThrow();
            var variant =
                declaration.variants().stream()
                    .filter(item -> item.key().equals(adopted.variantKey()))
                    .findFirst()
                    .orElseThrow();
            var arguments = ((CoreType.Declared) adopted.type()).arguments();
            var payload = new ArrayList<Object>();
            for (int index = 0; index < adopted.fieldCount(); index++) {
              CoreType declared =
                  execution
                      .runtime()
                      .program()
                      .type(variant.fields().get(index).type())
                      .substitute(arguments::get);
              payload.add(transform(adopted.payloadValues().get(index), declared));
            }
            yield adopted.rebuildPayload(payload);
          }
          case ArrayValue array -> {
            var type = containerType(array.type, expected);
            var target = new ArrayValue(type, List.of());
            replacements.put(value, target);
            for (Object item : array.values)
              target.values.add(transform(item, type.arguments().getFirst()));
            yield target;
          }
          case ListValue list -> {
            var type = containerType(list.type, expected);
            var target = new ListValue(type);
            replacements.put(value, target);
            for (Object item : list.values)
              target.values.add(transform(item, type.arguments().getFirst()));
            yield target;
          }
          case MapValue map -> {
            var type = containerType(map.type, expected);
            var target = new MapValue(type);
            replacements.put(value, target);
            for (var entry : RuntimeCollections.entries(map.values)) {
              var key =
                  new RuntimeKey(transform(entry.getKey().value, type.arguments().getFirst()));
              if (target.values.containsKey(key))
                throw new StateMigrationFailure("State migration produces duplicate map keys");
              target.values.put(key, transform(entry.getValue(), type.arguments().get(1)));
            }
            yield target;
          }
          case SetValue set -> {
            var type = containerType(set.type, expected);
            var target = new SetValue(type);
            replacements.put(value, target);
            for (var key : set.values)
              if (!target.values.add(
                  new RuntimeKey(transform(key.value, type.arguments().getFirst()))))
                throw new StateMigrationFailure("State migration produces duplicate set keys");
            yield target;
          }
          case StackValue stack -> {
            var type = containerType(stack.type, expected);
            var target = new StackValue(type);
            replacements.put(value, target);
            for (Object item : stack.values)
              target.values.addLast(transform(item, type.arguments().getFirst()));
            yield target;
          }
          case QueueValue queue -> {
            var type = containerType(queue.type, expected);
            var target = new QueueValue(type);
            replacements.put(value, target);
            for (Object item : queue.values)
              target.values.addLast(transform(item, type.arguments().getFirst()));
            yield target;
          }
          case DequeValue deque -> {
            var type = containerType(deque.type, expected);
            var target = new DequeValue(type);
            replacements.put(value, target);
            for (Object item : deque.values)
              target.values.addLast(transform(item, type.arguments().getFirst()));
            yield target;
          }
          case PairValue pair -> {
            var type = containerType(pair.type, expected);
            var target = new PairValue(type, null, null);
            replacements.put(value, target);
            target.first(transform(pair.first(), type.arguments().getFirst()));
            target.second(transform(pair.second(), type.arguments().get(1)));
            yield target;
          }
          case Closure function -> function;
          default -> {
            replacements.put(value, value);
            for (Object child : children(value)) visit(child);
            yield value;
          }
        };
    replacements.put(value, transformed);
    replacements.put(transformed, transformed);
    validateType(expected, transformed);
    return transformed;
  }

  private CoreType.Declared containerType(CoreType actual, CoreType expected) {
    var concrete = (CoreType.Declared) actual;
    return instanceType(
        expected instanceof CoreType.Declared declared
                && declared.constructor().equals(concrete.constructor())
            ? declared
            : concrete);
  }

  private CoreType.Declared instanceType(CoreType.Declared declared) {
    return declared.nullability() == dev.w0fv1.norm.core.CoreNullability.NON_NULL
        ? declared
        : new CoreType.Declared(
            declared.constructor(),
            declared.arguments(),
            declared.category(),
            dev.w0fv1.norm.core.CoreNullability.NON_NULL);
  }

  private void validateType(CoreType expected, Object value) {
    if (expected.equals(CoreType.DYNAMIC) || expected.equals(CoreType.EXISTENTIAL)) return;
    if (value == NullValue.INSTANCE
        ? !expected.isNullable()
        : execution != null && !execution.runtime().isAssignable(expected, runtimeType(value)))
      throw new StateMigrationPending(
          "State update cannot bind retained value to target type: " + expected);
  }

  private Iterable<Object> children(Object value) {
    var children = new ArrayList<Object>();
    switch (value) {
      case RuntimeStateRoots roots -> children.addAll(roots.retainedValues());
      case ObjectValue object -> {
        children.addAll(object.cells().values());
        children.addAll(storage(object.configuration).values());
      }
      case Closure closure -> {
        children.add(closure.receiver());
        children.addAll(java.util.Arrays.asList(closure.captures()));
      }
      case FieldReference reference -> {
        children.add(reference.receiver());
        children.add(reference.read());
      }
      case ReferenceValue reference -> children.add(reference.read());
      case OpaqueValue opaque -> {
        children.add(opaque.owner);
        if (opaque.value instanceof AnnotationRuntime.FieldHandle handle)
          children.add(handle.receiver());
      }
      case OpaqueResource resource -> children.add(resource.borrowingOwner);
      case ArrayValue array -> children.addAll(array.values);
      case ListValue list -> children.addAll(list.values);
      case MapValue map -> {
        for (var entry : RuntimeCollections.entries(map.values)) {
          children.add(entry.getKey().value);
          children.add(entry.getValue());
        }
      }
      case SetValue set -> set.values.forEach(key -> children.add(key.value));
      case StackValue stack -> children.addAll(stack.values);
      case QueueValue queue -> children.addAll(queue.values);
      case DequeValue deque -> children.addAll(deque.values);
      case EnumValue enumeration -> children.addAll(enumeration.payloadValues());
      case PairValue pair -> {
        children.add(pair.first());
        children.add(pair.second());
      }
      default -> {}
    }
    return children;
  }

  void prepare(ObjectValue object, AggregateInfo view) {
    prepare(object, view, object.type());
  }

  void prepare(ObjectValue object, AggregateInfo view, CoreType desiredType) {
    RuntimeObjectState.MigrationContext context = null;
    if (object.state().hasView(view, desiredType)
        && !object.state().schemaMatches(view, desiredType)) return;
    if (!object.state().schemaMatches(view, desiredType)) {
      context = migrate(object, view, desiredType);
      boolean converted = context.hasExplicitChanges();
      if (!converted && context.requiresConverter() && execution != null)
        converted = MigrationPolicy.migrate(context, execution);
      if (context.destructive() && !converted)
        throw new StateMigrationFailure(
            "Destructive runtime state migration requires a converter for "
                + object.objectInfo.name()
                + ": "
                + context.objectType()
                + " -> "
                + context.targetType());
      var fields = RuntimeObjectState.Schema.of(view, object.type()).fields();
      for (var configured : object.configurationFor(view).entrySet())
        context.write(configured.getKey(), fields.get(configured.getKey()), configured.getValue());
    }
    for (FieldPlan field : view.fields()) {
      if (!isInitializing(object.cells(), field.key())) prepareField(object, field);
    }
  }

  void prepareField(ObjectValue object, FieldPlan field) {
    Map<FieldKey, Object> target = object.cells();
    if (target.containsKey(field.key())) return;
    if (object.configurationFor(objectInfo(object)).containsKey(field.key())) {
      target.put(field.key(), storage(object.configuration).get(field.key()));
      return;
    }
    if (isInitializing(object.cells(), field.key()))
      throw new StateMigrationFailure("cyclic runtime field initializer: " + field.key());
    var active = initializing.computeIfAbsent(object.cells(), ignored -> new java.util.HashSet<>());
    active.add(field.key());
    try {
      CallTarget initializer =
          field
              .initializer()
              .orElseThrow(
                  () ->
                      new StateMigrationFailure(
                          "New field requires an explicit initializer or migration: "
                              + field.key()));
      var arguments = new ArrayList<Object>();
      arguments.add(execution);
      arguments.add(object);
      if (object.type() instanceof CoreType.Declared declared)
        arguments.addAll(declared.arguments());
      target.put(field.key(), copy(initializer.call(arguments.toArray())));
    } finally {
      active.remove(field.key());
    }
  }

  private boolean isInitializing(Map<FieldKey, Object> cells, FieldKey key) {
    return initializing.getOrDefault(cells, java.util.Set.of()).contains(key)
        || parent != null && parent.isInitializing(cells, key);
  }

  void commit() {
    if (transition != null) include(transition.roots());
    for (int index = 0; index < roots.size(); index++) visit(roots.get(index));
    transitions.forEach(RuntimeObjectState.MigrationContext::validateCoverage);
    staged.forEach(
        (cells, values) -> {
          if (!((JournalMap) values).dirty) return;
          Map<FieldKey, Object> target = parent == null ? cells : parent.storage(cells);
          target.clear();
          target.putAll(values);
        });
    cells.forEach(
        (cell, value) -> {
          if (parent == null) cell.value = value;
          else parent.cells.put(cell, value);
        });
    containers.forEach(
        (storage, value) -> {
          if (parent == null) storage.commit(value);
          else parent.containers.put(storage, value);
        });
    if (parent == null) {
      objects.forEach((state, context) -> state.publish(context.target()));
      if (transition != null) transition.commit();
    } else {
      parent.objects.putAll(objects);
      parent.transitions.addAll(transitions);
      if (parent.transition == null) {
        parent.transition = transition;
        transition = null;
      }
    }
    committed = true;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    if (parent == null) CURRENT.remove();
    else CURRENT.set(parent);
    try {
      if (committed) {
        if (parent == null) effects.forEach(Runnable::run);
        else parent.effects.addAll(effects);
      }
    } finally {
      if (!committed || parent == null)
        transitions.forEach(RuntimeObjectState.MigrationContext::close);
      if (transition != null) transition.close();
    }
  }
}
