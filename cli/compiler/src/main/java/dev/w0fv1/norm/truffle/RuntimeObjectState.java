package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreType;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class RuntimeObjectState {
  static final class Schema {
    private final java.lang.ref.WeakReference<RuntimeValues.AggregateInfo> revision;
    private final int hash;
    private final CoreType objectType;
    private final dev.w0fv1.norm.core.CoreRuntimeSchema.TypeContract owner;
    private final Map<RuntimeValues.FieldKey, dev.w0fv1.norm.core.CoreRuntimeSchema.TypeFingerprint>
        contracts;
    private final Map<RuntimeValues.FieldKey, CoreType> fields;

    private Schema(
        RuntimeValues.AggregateInfo info,
        CoreType objectType,
        Map<RuntimeValues.FieldKey, CoreType> fields) {
      revision = new java.lang.ref.WeakReference<>(info);
      this.objectType = objectType;
      owner =
          info.annotations() == null
              ? null
              : info.annotations().typeFingerprint(objectType).contracts().get(info.definition());
      this.fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
      var fingerprints =
          new LinkedHashMap<
              RuntimeValues.FieldKey, dev.w0fv1.norm.core.CoreRuntimeSchema.TypeFingerprint>();
      fields.forEach(
          (key, type) ->
              fingerprints.put(
                  key,
                  info.annotations() == null
                      ? new dev.w0fv1.norm.core.CoreRuntimeSchema.TypeFingerprint(type, Map.of())
                      : info.annotations().typeFingerprint(type)));
      contracts = Map.copyOf(fingerprints);
      hash = Objects.hash(System.identityHashCode(info), objectType, contracts);
    }

    static Schema of(RuntimeValues.AggregateInfo info, CoreType type) {
      List<CoreType> arguments =
          type instanceof CoreType.Declared declared ? declared.arguments() : List.of();
      var fields = new LinkedHashMap<RuntimeValues.FieldKey, CoreType>();
      info.fields()
          .forEach(
              field ->
                  fields.put(
                      field.key(),
                      field
                          .type()
                          .substitute(
                              index ->
                                  index < arguments.size()
                                      ? arguments.get(index)
                                      : new CoreType.Parameter(
                                          index, dev.w0fv1.norm.core.CoreNullability.NON_NULL))));
      return new Schema(info, type, fields);
    }

    Map<RuntimeValues.FieldKey, CoreType> fields() {
      return fields;
    }

    boolean requiresTypePlan(Schema target) {
      if (!objectType.equals(target.objectType)) return true;
      if (owner == null || target.owner == null) return false;
      return dev.w0fv1.norm.core.CoreRuntimeSchema.requiresTypeParameterMigration(
              owner.parameters(), target.owner.parameters())
          || !target.owner.parents().containsAll(owner.parents())
          || !target.owner.conformances().containsAll(owner.conformances());
    }

    boolean alive() {
      return revision.get() != null;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || other instanceof Schema schema
              && revision.get() != null
              && revision.get() == schema.revision.get()
              && objectType.equals(schema.objectType)
              && contracts.equals(schema.contracts);
    }
  }

  record CellValue(boolean present, Object value) {}

  static final class Cell {
    final CoreType type;
    volatile CellValue value;

    Cell(CoreType type, boolean present, Object value) {
      this.type = type;
      this.value = new CellValue(present, value);
    }

    CellValue snapshot() {
      return RuntimeValues.stateCellView(this);
    }

    public Object read() {
      return snapshot().value();
    }

    public void write(Object value) {
      RuntimeValues.stateCellWrite(this, new CellValue(true, value));
    }
  }

  private record Published(ViewMap view, RuntimeValues.AggregateInfo info) {}

  private final Map<Schema, java.lang.ref.WeakReference<ViewMap>> views =
      new java.util.concurrent.ConcurrentHashMap<>();
  private volatile Published published;

  RuntimeObjectState(
      RuntimeValues.AggregateInfo info, CoreType type, Map<RuntimeValues.FieldKey, Object> values) {
    Schema schema = Schema.of(info, type);
    var cells = new LinkedHashMap<RuntimeValues.FieldKey, Cell>();
    schema
        .fields()
        .forEach(
            (key, declared) ->
                cells.put(key, new Cell(declared, values.containsKey(key), values.get(key))));
    var current = new ViewMap(this, schema, cells);
    views.put(schema, new java.lang.ref.WeakReference<>(current));
    published = new Published(current, info);
  }

  static RuntimeObjectState shared(
      RuntimeValues.AggregateInfo info, CoreType type, Map<RuntimeValues.FieldKey, Object> values) {
    return values instanceof ViewMap view ? view.state : new RuntimeObjectState(info, type, values);
  }

  Map<RuntimeValues.FieldKey, Object> view(RuntimeValues.AggregateInfo info, CoreType type) {
    Schema schema = schema(info, type);
    var active = RuntimeValues.stateView(this);
    if (active.schema.equals(schema)) return active;
    var reference = views.get(schema);
    var selected = reference == null ? null : reference.get();
    if (selected == null)
      throw new RuntimeValues.StateMigrationFailure(
          "runtime state schema has not been prepared: " + schema);
    return selected;
  }

  private Schema schema(RuntimeValues.AggregateInfo info, CoreType fallback) {
    var current = RuntimeValues.stateView(this);
    if (current.schema.revision.get() == info) return current.schema;
    return views.keySet().stream()
        .filter(value -> value.revision.get() == info)
        .findFirst()
        .orElseGet(() -> Schema.of(info, fallback));
  }

  CoreType type(RuntimeValues.AggregateInfo info, CoreType fallback) {
    return schema(info, fallback).objectType;
  }

  CoreType currentType() {
    return RuntimeValues.stateView(this).schema.objectType;
  }

  record CopySource(
      RuntimeValues.AggregateInfo info,
      CoreType type,
      Map<RuntimeValues.FieldKey, Object> values) {}

  CopySource copySource(RuntimeValues.AggregateInfo preferred, CoreType type) {
    var active = RuntimeValues.stateView(this);
    var requested = schema(preferred, type);
    var reference = views.get(requested);
    var historical = reference == null ? null : reference.get();
    var selected =
        active.schema.equals(requested) ? active : historical == null ? active : historical;
    return new CopySource(
        Objects.requireNonNull(selected.schema.revision.get()),
        selected.schema.objectType,
        selected);
  }

  boolean sameFieldType(
      RuntimeValues.AggregateInfo before,
      RuntimeValues.AggregateInfo after,
      CoreType type,
      RuntimeValues.FieldKey key) {
    return Objects.equals(
            schema(before, type).contracts.get(key), schema(after, type).contracts.get(key))
        && schema(before, type).fields().containsKey(key);
  }

  int locationHash(RuntimeValues.AggregateInfo view, CoreType type, RuntimeValues.FieldKey key) {
    return System.identityHashCode(((ViewMap) view(view, type)).cells.get(key));
  }

  boolean sameLocation(
      RuntimeValues.AggregateInfo before,
      RuntimeValues.AggregateInfo after,
      CoreType type,
      RuntimeValues.FieldKey key) {
    var first = (ViewMap) view(before, type);
    var second = (ViewMap) view(after, type);
    return first.cells.containsKey(key) && first.cells.get(key) == second.cells.get(key);
  }

  boolean shapeChanges(RuntimeValues.AggregateInfo target, CoreType type) {
    return !RuntimeValues.stateView(this).schema.contracts.equals(schema(target, type).contracts)
        || RuntimeValues.stateView(this).schema.requiresTypePlan(schema(target, type));
  }

  boolean schemaMatches(RuntimeValues.AggregateInfo info, CoreType type) {
    return RuntimeValues.stateView(this).schema.equals(Schema.of(info, type));
  }

  boolean hasView(RuntimeValues.AggregateInfo info, CoreType type) {
    var view = views.get(Schema.of(info, type));
    return view != null && view.get() != null;
  }

  ViewMap current() {
    return published.view();
  }

  RuntimeValues.AggregateInfo currentInfo() {
    return published.info();
  }

  MigrationContext prepare(
      RuntimeValues.ObjectValue object, RuntimeValues.AggregateInfo targetInfo) {
    return prepare(object, targetInfo, object.type());
  }

  MigrationContext prepare(
      RuntimeValues.ObjectValue object,
      RuntimeValues.AggregateInfo targetInfo,
      CoreType desiredType) {
    var source = RuntimeValues.stateView(this);
    Schema target = Schema.of(targetInfo, desiredType);
    var cells = new LinkedHashMap<RuntimeValues.FieldKey, Cell>();
    for (var entry : target.fields().entrySet()) {
      Cell compatible =
          Objects.equals(
                  source.schema.contracts.get(entry.getKey()), target.contracts.get(entry.getKey()))
              ? source.cells.get(entry.getKey())
              : null;
      cells.put(
          entry.getKey(),
          compatible == null
              ? new Cell(
                  entry.getValue(),
                  source.schema.fields().containsKey(entry.getKey())
                      && dev.w0fv1.norm.core.CoreRuntimeSchema.migratableFieldType(
                          source.schema.fields().get(entry.getKey()), entry.getValue())
                      && source.containsKey(entry.getKey()),
                  source.schema.fields().containsKey(entry.getKey())
                          && dev.w0fv1.norm.core.CoreRuntimeSchema.migratableFieldType(
                              source.schema.fields().get(entry.getKey()), entry.getValue())
                      ? RuntimeValues.copy(source.get(entry.getKey()))
                      : null)
              : compatible);
    }
    return new MigrationContext(object, targetInfo, source, new ViewMap(this, target, cells));
  }

  void publish(ViewMap view) {
    var before = published;
    if (before.view() != view) before.info().stateViews().retain(before.view());
    views.entrySet().removeIf(entry -> !entry.getKey().alive() || entry.getValue().get() == null);
    views.put(view.schema, new java.lang.ref.WeakReference<>(view));
    published =
        new Published(
            view,
            Objects.requireNonNull(
                view.schema.revision.get(), "current runtime state schema owner"));
  }

  static final class ViewMap extends AbstractMap<RuntimeValues.FieldKey, Object> {
    private final RuntimeObjectState state;
    private final Schema schema;
    private final Map<RuntimeValues.FieldKey, Cell> cells;

    ViewMap(RuntimeObjectState state, Schema schema, Map<RuntimeValues.FieldKey, Cell> cells) {
      this.state = state;
      this.schema = schema;
      this.cells = cells;
    }

    @Override
    public boolean containsKey(Object key) {
      Cell cell = cells.get(key);
      return cell != null && cell.snapshot().present();
    }

    @Override
    public Object get(Object key) {
      Cell cell = cells.get(key);
      return cell == null ? null : cell.read();
    }

    void checkWrite(RuntimeValues.FieldKey key, Object value) {
      Cell cell = cells.get(key);
      if (cell == null)
        throw new RuntimeValues.StateMigrationFailure(
            "field is outside runtime state schema: " + key);
      var writable = RuntimeValues.stateView(state);
      if (writable.cells.get(key) != cell)
        throw new RuntimeValues.StateMigrationFailure(
            "retired runtime state field is read only: " + key);
      var declared = cell.type;
      if (value != null) {
        var info = schema.revision.get();
        boolean valid =
            value == RuntimeValues.NullValue.INSTANCE
                ? declared.isNullable()
                : declared.equals(CoreType.DYNAMIC)
                    || (info == null || info.annotations() == null
                        ? declared.equals(RuntimeValues.runtimeType(value))
                        : info.annotations()
                            .isAssignable(declared, RuntimeValues.runtimeType(value)));
        if (!valid)
          throw new RuntimeValues.StateMigrationFailure(
              "runtime state field value does not match its schema: " + key);
      }
    }

    @Override
    public Object put(RuntimeValues.FieldKey key, Object value) {
      checkWrite(key, value);
      Cell cell = cells.get(key);
      Object previous = cell.read();
      cell.write(value);
      return previous;
    }

    @Override
    public Set<Entry<RuntimeValues.FieldKey, Object>> entrySet() {
      var entries = new LinkedHashSet<Entry<RuntimeValues.FieldKey, Object>>();
      cells.forEach(
          (key, cell) -> {
            if (containsKey(key)) entries.add(new SimpleImmutableEntry<>(key, cell.read()));
          });
      return java.util.Collections.unmodifiableSet(entries);
    }
  }

  static final class MigrationContext implements dev.w0fv1.norm.truffle.MigrationContext {
    private final RuntimeValues.ObjectValue object;
    private final RuntimeValues.AggregateInfo targetInfo;
    private final ViewMap source;
    private ViewMap target;
    private final AnnotationRuntime sourceAnnotations;
    private boolean replaced;
    private final Map<RuntimeValues.FieldKey, Object> snapshot;
    private final Set<RuntimeValues.FieldKey> removed = new LinkedHashSet<>();
    private final Set<RuntimeValues.FieldKey> written = new LinkedHashSet<>();
    private boolean active = true;

    MigrationContext(
        RuntimeValues.ObjectValue object,
        RuntimeValues.AggregateInfo targetInfo,
        ViewMap source,
        ViewMap target) {
      this.object = object;
      this.targetInfo = targetInfo;
      this.source = source;
      sourceAnnotations = source.schema.revision.get().annotations();
      this.target = target;
      var values = new LinkedHashMap<RuntimeValues.FieldKey, Object>(source);
      object.configuration.forEach(
          (key, value) -> {
            if (stateCompatible(key)) values.put(key, value);
          });
      snapshot = java.util.Collections.unmodifiableMap(values);
    }

    private boolean stateCompatible(RuntimeValues.FieldKey key) {
      return object
          .state()
          .sameFieldType(
              (RuntimeValues.AggregateInfo) object.objectInfo,
              source.schema.revision.get(),
              object.type(),
              key);
    }

    RuntimeValues.ObjectValue object() {
      return object;
    }

    public CoreType objectType() {
      return source.schema.objectType;
    }

    public CoreType targetType() {
      return target.schema.objectType;
    }

    public AnnotationRuntime sourceAnnotations() {
      return sourceAnnotations;
    }

    public AnnotationRuntime targetAnnotations() {
      return targetInfo.annotations();
    }

    RuntimeValues.AggregateInfo targetInfo() {
      return targetInfo;
    }

    Map<RuntimeValues.FieldKey, CoreType> targetFields() {
      return target.schema.fields();
    }

    ViewMap target() {
      return target;
    }

    public Optional<StateField> sourceField(String name) {
      requireActive();
      var fields =
          source.schema.fields().entrySet().stream()
              .filter(entry -> entry.getKey().name().equals(name))
              .toList();
      if (fields.isEmpty()) return Optional.empty();
      if (fields.size() != 1)
        throw new RuntimeValues.StateMigrationFailure(
            "migration source field name is ambiguous: " + name);
      var field = fields.getFirst();
      return Optional.of(new StateField(field.getKey(), field.getValue()));
    }

    public Optional<StateField> targetField(String name) {
      requireActive();
      var fields =
          target.schema.fields().entrySet().stream()
              .filter(entry -> entry.getKey().name().equals(name))
              .toList();
      if (fields.isEmpty()) return Optional.empty();
      if (fields.size() != 1)
        throw new RuntimeValues.StateMigrationFailure(
            "migration target field name is ambiguous: " + name);
      var field = fields.getFirst();
      return Optional.of(new StateField(field.getKey(), field.getValue()));
    }

    public Object read(RuntimeValues.FieldKey key, CoreType expected) {
      requireActive();
      var declared = source.schema.fields().get(key);
      if (declared == null
          || !(sourceAnnotations == null
              ? expected.equals(declared) || expected.equals(CoreType.DYNAMIC)
              : sourceAnnotations.isAssignable(expected, declared)))
        throw new RuntimeValues.StateMigrationFailure(
            "migration source field type does not match: " + key);
      if (!snapshot.containsKey(key))
        throw new RuntimeValues.StateMigrationFailure(
            "migration source field is uninitialized: " + key);
      return RuntimeValues.copy(snapshot.get(key));
    }

    public void write(RuntimeValues.FieldKey key, CoreType declared, Object value) {
      requireActive();
      if (!Objects.equals(target.schema.fields().get(key), declared))
        throw new RuntimeValues.StateMigrationFailure(
            "migration target field descriptor does not match: " + key);
      if (value == RuntimeValues.NullValue.INSTANCE
          ? !declared.isNullable()
          : value == null || !assignable(declared, RuntimeValues.runtimeType(value)))
        throw new RuntimeValues.StateMigrationFailure(
            "migration target field value does not match: " + key);
      target.put(key, RuntimeValues.copy(value));
      if (object.configurationFor(targetInfo).containsKey(key))
        RuntimeValues.stateStorage(object.configuration).remove(key);
      written.add(key);
    }

    void stageTransform(RuntimeValues.FieldKey key, Object value) {
      requireActive();
      var existing = target.cells.get(key);
      if (existing == null)
        throw new RuntimeValues.StateMigrationFailure(
            "Transformed field is outside the target schema: " + key);
      if (existing == source.cells.get(key))
        target.cells.put(
            key, new Cell(existing.type, existing.snapshot().present(), existing.read()));
      target.put(key, value);
      if (!Objects.equals(source.schema.contracts.get(key), target.schema.contracts.get(key)))
        written.add(key);
    }

    public void remove(RuntimeValues.FieldKey key) {
      requireActive();
      if (!source.schema.fields().containsKey(key))
        throw new RuntimeValues.StateMigrationFailure("migration source field is absent: " + key);
      if (Objects.equals(source.schema.fields().get(key), target.schema.fields().get(key)))
        throw new RuntimeValues.StateMigrationFailure(
            "migration cannot remove a retained target field: " + key);
      removed.add(key);
    }

    boolean shapeChanged() {
      return !source.schema.contracts.equals(target.schema.contracts)
          || source.schema.requiresTypePlan(target.schema);
    }

    boolean destructive() {
      return source.schema.requiresTypePlan(target.schema)
          || source.schema.fields().entrySet().stream()
              .anyMatch(
                  entry ->
                      target.schema.fields().get(entry.getKey()) == null
                          || !dev.w0fv1.norm.core.CoreRuntimeSchema.migratableFieldType(
                              entry.getValue(), target.schema.fields().get(entry.getKey())));
    }

    boolean hasExplicitChanges() {
      return replaced || !written.isEmpty() || !removed.isEmpty();
    }

    boolean requiresConverter() {
      return destructive()
          || targetInfo.fields().stream()
              .anyMatch(
                  field ->
                      !target.containsKey(field.key())
                          && field.initializer().isEmpty()
                          && !object.configurationFor(targetInfo).containsKey(field.key()));
    }

    void validateCoverage() {
      requireActive();
      for (var entry : source.schema.fields().entrySet()) {
        var key = entry.getKey();
        if (!Objects.equals(entry.getValue(), target.schema.fields().get(key))
            && !removed.contains(key)
            && !written.contains(key))
          throw new RuntimeValues.StateMigrationFailure(
              "migration has not consumed source field: " + key);
      }
      if (target.schema.owner != null
          && target.schema.objectType instanceof CoreType.Declared declared) {
        var parameters = target.schema.owner.parameters();
        if (declared.arguments().size() != parameters.size())
          throw new RuntimeValues.StateMigrationFailure(
              "Migration must provide the candidate generic arguments through replacement");
        for (int index = 0; index < parameters.size(); index++) {
          var bound = parameters.get(index).upperBound();
          if (bound.isPresent()
              && !targetAnnotations()
                  .isAssignable(
                      bound.orElseThrow().substitute(declared.arguments()::get),
                      declared.arguments().get(index)))
            throw new RuntimeValues.StateMigrationFailure(
                "Migration candidate generic argument violates its bound");
        }
      }
      for (var key : target.schema.fields().keySet()) {
        if (!target.containsKey(key))
          throw new RuntimeValues.StateMigrationFailure(
              "migration has not initialized target field: " + key);
      }
    }

    public String variant() {
      requireActive();
      return null;
    }

    public Object readPayload(int index, CoreType expected) {
      throw new RuntimeValues.StateMigrationFailure("Object migration has no enum payload");
    }

    public void replace(Object value) {
      requireActive();
      if (!(value instanceof RuntimeValues.ObjectValue candidate)
          || !candidate.objectInfo.definition().equals(targetInfo.definition()))
        throw new RuntimeValues.StateMigrationFailure(
            "Object replacement must preserve its nominal declaration");
      var candidateType = candidate.type();
      var schema = Schema.of(targetInfo, candidateType);
      var cells = new LinkedHashMap<RuntimeValues.FieldKey, Cell>();
      for (var entry : schema.fields().entrySet()) {
        var previous = target.cells.get(entry.getKey());
        var cell =
            previous != null
                    && Objects.equals(
                        target.schema.contracts.get(entry.getKey()),
                        schema.contracts.get(entry.getKey()))
                ? previous
                : new Cell(entry.getValue(), false, null);
        cells.put(entry.getKey(), cell);
      }
      target = new ViewMap(object.state(), schema, cells);
      for (var entry : schema.fields().entrySet()) {
        Object replacementValue = candidate.rawField(entry.getKey(), targetInfo);
        Cell cell = cells.get(entry.getKey());
        cell.write(RuntimeValues.copy(replacementValue));
        written.add(entry.getKey());
      }
      removed.addAll(source.schema.fields().keySet());
      replaced = true;
    }

    public Object replacement() {
      return object;
    }

    public void validate() {
      validateCoverage();
    }

    public void close() {
      active = false;
    }

    private void requireActive() {
      if (!active)
        throw new RuntimeValues.StateMigrationFailure("migration state is no longer active");
    }

    private boolean assignable(CoreType expected, CoreType actual) {
      return targetInfo.annotations() == null
          ? expected.equals(actual) || expected.equals(CoreType.DYNAMIC)
          : targetInfo.annotations().isAssignable(expected, actual);
    }
  }
}
