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
    private final Map<RuntimeValues.FieldKey, CoreType> fields;

    private Schema(RuntimeValues.AggregateInfo info, Map<RuntimeValues.FieldKey, CoreType> fields) {
      revision = new java.lang.ref.WeakReference<>(info);
      this.fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
      hash = 31 * System.identityHashCode(info) + fields.hashCode();
    }

    static Schema of(RuntimeValues.AggregateInfo info, CoreType type) {
      List<CoreType> arguments =
          type instanceof CoreType.Declared declared ? declared.arguments() : List.of();
      var fields = new LinkedHashMap<RuntimeValues.FieldKey, CoreType>();
      info.fields()
          .forEach(field -> fields.put(field.key(), field.type().substitute(arguments::get)));
      return new Schema(info, fields);
    }

    Map<RuntimeValues.FieldKey, CoreType> fields() {
      return fields;
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
              && fields.equals(schema.fields);
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

    Object read() {
      return snapshot().value();
    }

    void write(Object value) {
      RuntimeValues.stateCellWrite(this, new CellValue(true, value));
    }
  }

  private record Published(ViewMap view, RuntimeValues.AggregateInfo info) {}

  private final Map<Schema, ViewMap> views = new java.util.concurrent.ConcurrentHashMap<>();
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
    views.put(schema, current);
    published = new Published(current, info);
  }

  static RuntimeObjectState shared(
      RuntimeValues.AggregateInfo info, CoreType type, Map<RuntimeValues.FieldKey, Object> values) {
    return values instanceof ViewMap view ? view.state : new RuntimeObjectState(info, type, values);
  }

  Map<RuntimeValues.FieldKey, Object> view(RuntimeValues.AggregateInfo info, CoreType type) {
    Schema schema = Schema.of(info, type);
    var active = RuntimeValues.stateView(this);
    if (active.schema.equals(schema)) return active;
    var selected = views.get(schema);
    if (selected == null)
      throw new RuntimeValues.StateMigrationFailure(
          "runtime state schema has not been prepared: " + schema);
    return selected;
  }

  boolean sameFieldType(
      RuntimeValues.AggregateInfo before,
      RuntimeValues.AggregateInfo after,
      CoreType type,
      RuntimeValues.FieldKey key) {
    return Objects.equals(
            Schema.of(before, type).fields().get(key), Schema.of(after, type).fields().get(key))
        && Schema.of(before, type).fields().containsKey(key);
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
    return !RuntimeValues.stateView(this).schema.fields().equals(Schema.of(target, type).fields());
  }

  boolean schemaMatches(RuntimeValues.AggregateInfo info, CoreType type) {
    return RuntimeValues.stateView(this).schema.equals(Schema.of(info, type));
  }

  boolean hasView(RuntimeValues.AggregateInfo info, CoreType type) {
    return views.containsKey(Schema.of(info, type));
  }

  ViewMap current() {
    return published.view();
  }

  RuntimeValues.AggregateInfo currentInfo() {
    return published.info();
  }

  MigrationContext prepare(
      RuntimeValues.ObjectValue object, RuntimeValues.AggregateInfo targetInfo) {
    var source = RuntimeValues.stateView(this);
    Schema target = Schema.of(targetInfo, object.type);
    var cells = new LinkedHashMap<RuntimeValues.FieldKey, Cell>();
    for (var entry : target.fields().entrySet()) {
      Cell compatible =
          Objects.equals(source.schema.fields().get(entry.getKey()), entry.getValue())
              ? source.cells.get(entry.getKey())
              : null;
      cells.put(
          entry.getKey(),
          compatible == null ? new Cell(entry.getValue(), false, null) : compatible);
    }
    return new MigrationContext(object, targetInfo, source, new ViewMap(this, target, cells));
  }

  void publish(ViewMap view) {
    views.keySet().removeIf(schema -> !schema.alive());
    views.put(view.schema, view);
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

  static final class MigrationContext {
    private final RuntimeValues.ObjectValue object;
    private final RuntimeValues.AggregateInfo targetInfo;
    private final ViewMap source;
    private final ViewMap target;
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
      this.target = target;
      var values = new LinkedHashMap<RuntimeValues.FieldKey, Object>(source);
      object.configuration.forEach(
          (key, value) -> {
            if (stateCompatible(key)) values.put(key, value);
          });
      snapshot = java.util.Collections.unmodifiableMap(values);
    }

    private boolean stateCompatible(RuntimeValues.FieldKey key) {
      var declared =
          Schema.of((RuntimeValues.AggregateInfo) object.objectInfo, object.type).fields().get(key);
      return declared != null && declared.equals(source.schema.fields().get(key));
    }

    RuntimeValues.ObjectValue object() {
      return object;
    }

    CoreType objectType() {
      return object.type;
    }

    RuntimeValues.AggregateInfo targetInfo() {
      return targetInfo;
    }

    ViewMap target() {
      return target;
    }

    record StateField(RuntimeValues.FieldKey key, CoreType type) {}

    Optional<StateField> sourceField(String name) {
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

    Optional<StateField> targetField(String name) {
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

    Object read(RuntimeValues.FieldKey key, CoreType expected) {
      requireActive();
      var declared = source.schema.fields().get(key);
      if (declared == null || !assignable(expected, declared))
        throw new RuntimeValues.StateMigrationFailure(
            "migration source field type does not match: " + key);
      if (!snapshot.containsKey(key))
        throw new RuntimeValues.StateMigrationFailure(
            "migration source field is uninitialized: " + key);
      return RuntimeValues.copy(snapshot.get(key));
    }

    void write(RuntimeValues.FieldKey key, CoreType declared, Object value) {
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

    void remove(RuntimeValues.FieldKey key) {
      requireActive();
      if (!source.schema.fields().containsKey(key))
        throw new RuntimeValues.StateMigrationFailure("migration source field is absent: " + key);
      if (Objects.equals(source.schema.fields().get(key), target.schema.fields().get(key)))
        throw new RuntimeValues.StateMigrationFailure(
            "migration cannot remove a retained target field: " + key);
      removed.add(key);
    }

    boolean shapeChanged() {
      return !source.schema.fields().equals(target.schema.fields());
    }

    boolean destructive() {
      return source.schema.fields().entrySet().stream()
          .anyMatch(
              entry ->
                  !Objects.equals(entry.getValue(), target.schema.fields().get(entry.getKey())));
    }

    boolean hasExplicitChanges() {
      return !written.isEmpty() || !removed.isEmpty();
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
      for (var key : target.schema.fields().keySet()) {
        if (!target.containsKey(key))
          throw new RuntimeValues.StateMigrationFailure(
              "migration has not initialized target field: " + key);
      }
    }

    void close() {
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
