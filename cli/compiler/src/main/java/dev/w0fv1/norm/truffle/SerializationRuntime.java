package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.SerializationAbi;
import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SerializationRuntime {
  private final AnnotationRuntime reflection;
  private final Map<CoreType, Shape> shapes = new LinkedHashMap<>();
  private final Map<String, DefinitionId> annotations = new LinkedHashMap<>();

  SerializationRuntime(AnnotationRuntime reflection) {
    this.reflection = java.util.Objects.requireNonNull(reflection, "reflection");
  }

  synchronized Shape shape(CoreType type) {
    Shape cached = shapes.get(type);
    if (cached != null) return cached;
    List<CoreType> createdTypes = new ArrayList<>();
    try {
      return create(type, createdTypes);
    } catch (RuntimeException failure) {
      createdTypes.forEach(shapes::remove);
      throw failure;
    }
  }

  synchronized int cachedShapeCount() {
    return shapes.size();
  }

  private Shape create(CoreType type, List<CoreType> createdTypes) {
    Shape cached = shapes.get(type);
    if (cached != null) return cached;
    DeferredShape deferred = new DeferredShape(type);
    shapes.put(type, deferred);
    createdTypes.add(type);
    try {
      Shape created;
      if (nullable(type)) {
        created = new NullableShape(type, create(nonNullable(type), createdTypes));
      } else if (!(type instanceof CoreType.Declared declared)) {
        throw unsupported(type, "$", "unsupported serialization type");
      } else if (declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
        String identity = builtin.id().value();
        ScalarKind scalar = scalar(identity);
        if (scalar != null) {
          created = new ScalarShape(type, scalar);
        } else if (identity.equals("std.core.Array") || identity.equals("std.core.List")) {
          requireArguments(declared, 1);
          SequenceKind kind =
              identity.equals("std.core.Array") ? SequenceKind.ARRAY : SequenceKind.LIST;
          created =
              new SequenceShape(type, kind, create(declared.arguments().getFirst(), createdTypes));
        } else if (identity.equals("std.core.Map")) {
          requireArguments(declared, 2);
          created =
              new MapShape(
                  type,
                  create(declared.arguments().getFirst(), createdTypes),
                  create(declared.arguments().get(1), createdTypes));
        } else {
          throw unsupported(type, "$", "unsupported builtin serialization type");
        }
      } else {
        CoreTypeConstructor.User user = (CoreTypeConstructor.User) declared.constructor();
        DefinitionId definition = external(user.definition());
        CoreDefinition declaration = reflection.program().definition(definition).orElseThrow();
        if (declaration instanceof CoreDefinition.Enum enumeration) {
          if (enumeration.variants().stream().anyMatch(variant -> !variant.fields().isEmpty())) {
            throw unsupported(type, "$", "enum payload serialization is not supported");
          }
          created = new EnumShape(type, definition, enumeration);
        } else {
          if (!(declaration instanceof CoreDefinition.Aggregate aggregate)
              || aggregate.kind() == CoreAggregateKind.ANNOTATION
              || aggregate.valueCategory() != CoreValueCategory.VALUE) {
            throw unsupported(type, "$", "automatic serialization requires a value type");
          }
          DefinitionId serializable = annotation(SerializationAbi.SERIALIZABLE_ANNOTATION_NAME);
          if (reflection.typeAnnotationValues(serializable, definition) == null) {
            throw unsupported(type, "$", "value type is not marked @Serializable");
          }
          RuntimeValues.AggregateInfo info = reflection.aggregateInfo(definition);
          if (info == null)
            throw new IllegalStateException("serialization aggregate is not initialized");
          DefinitionId serialName = annotation(SerializationAbi.SERIAL_NAME_ANNOTATION_NAME);
          DefinitionId serialIgnore = annotation(SerializationAbi.SERIAL_IGNORE_ANNOTATION_NAME);
          List<CoreAnnotationValue> typeName =
              reflection.typeAnnotationValues(serialName, definition);
          String name =
              typeName == null
                  ? aggregate.nominalType().name()
                  : annotationString(typeName.getFirst());
          requireName(name, "$", "type");
          List<FieldShape> fields = new ArrayList<>();
          Map<String, Integer> names = new LinkedHashMap<>();
          for (RuntimeValues.FieldPlan field : info.fields()) {
            CoreType fieldType = reflection.reflectedFieldType(field, declared);
            boolean ignored =
                reflection.fieldAnnotationValues(serialIgnore, field.owner(), field.index())
                    != null;
            List<CoreAnnotationValue> renamed =
                reflection.fieldAnnotationValues(serialName, field.owner(), field.index());
            String fieldName =
                renamed == null ? field.name() : annotationString(renamed.getFirst());
            requireName(fieldName, "$", "field");
            if (!ignored && names.putIfAbsent(fieldName, field.index()) != null) {
              throw new ShapeException(
                  "NORM-SERIALIZATION-DUPLICATE-NAME",
                  "$",
                  "duplicate serialized field name '" + fieldName + "'");
            }
            if (ignored && !nullable(fieldType)) {
              throw new ShapeException(
                  "NORM-SERIALIZATION-IGNORED-FIELD",
                  "$." + fieldName,
                  "ignored field must be nullable");
            }
            fields.add(
                new FieldShape(
                    field.index(),
                    field.name(),
                    fieldName,
                    field.owner(),
                    fieldType,
                    ignored,
                    ignored ? null : create(fieldType, createdTypes)));
          }
          created = new AggregateShape(type, definition, name, info, fields, names);
        }
      }
      deferred.complete(created);
      shapes.put(type, created);
      return created;
    } catch (RuntimeException failure) {
      deferred.fail(failure);
      throw failure;
    }
  }

  private static void requireName(String name, String path, String target) {
    if (name.isBlank()) {
      throw new ShapeException(
          "NORM-SERIALIZATION-NAME", path, "serialized " + target + " name is blank");
    }
  }

  private static String annotationString(CoreAnnotationValue value) {
    if (value.value() instanceof CoreAnnotationValue.Literal literal
        && literal.value() instanceof String string) {
      return string;
    }
    throw new IllegalStateException("annotation string metadata is invalid");
  }

  private DefinitionId annotation(String name) {
    return annotation(
        SerializationAbi.MODULE_NAME,
        SerializationAbi.MODULE_VERSION,
        SerializationAbi.PACKAGE_NAME,
        name);
  }

  DefinitionId annotation(String module, int version, String packageName, String name) {
    String identity = module + "@" + version + ":" + packageName + "." + name;
    DefinitionId cached = annotations.get(identity);
    if (cached != null) return cached;
    DefinitionId found = null;
    for (var record : reflection.program().definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Aggregate aggregate)
          || aggregate.kind() != CoreAggregateKind.ANNOTATION
          || !aggregate.nominalType().module().name().equals(module)
          || aggregate.nominalType().module().version() != version
          || !aggregate.nominalType().packageName().equals(packageName)
          || !aggregate.nominalType().name().equals(name)) continue;
      if (found != null) throw new IllegalStateException("serialization annotation is ambiguous");
      found = record.id();
    }
    if (found == null) {
      throw new IllegalStateException("serialization annotation is absent: " + name);
    }
    annotations.put(identity, found);
    return found;
  }

  boolean hasFieldAnnotation(DefinitionId annotation, FieldShape field) {
    return reflection.fieldAnnotationValues(annotation, field.owner(), field.ordinal()) != null;
  }

  private static ScalarKind scalar(String identity) {
    return switch (identity) {
      case "std.core.String" -> ScalarKind.STRING;
      case "std.core.Boolean" -> ScalarKind.BOOLEAN;
      case "std.core.Integer" -> ScalarKind.INTEGER;
      case "std.core.Long" -> ScalarKind.LONG;
      case "std.core.Float" -> ScalarKind.FLOAT;
      case "std.core.Double" -> ScalarKind.DOUBLE;
      case "std.core.CodePoint" -> ScalarKind.CODE_POINT;
      default -> null;
    };
  }

  private static void requireArguments(CoreType.Declared type, int count) {
    if (type.arguments().size() != count) {
      throw new IllegalStateException("builtin serialization type has an invalid arity");
    }
  }

  private static ShapeException unsupported(CoreType type, String path, String message) {
    return new ShapeException("NORM-SERIALIZATION-UNSUPPORTED-TYPE", path, message + ": " + type);
  }

  private static DefinitionId external(dev.w0fv1.norm.core.CoreDefinitionLink link) {
    return ((DefinitionReference.External) link).definition();
  }

  private static boolean nullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared -> declared.nullability() == CoreNullability.NULLABLE;
      case CoreType.Function function -> function.nullability() == CoreNullability.NULLABLE;
      case CoreType.Parameter parameter -> parameter.nullability() == CoreNullability.NULLABLE;
      case CoreType.Reference ignored -> false;
      case CoreType.Special ignored -> false;
    };
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

  sealed interface Shape
      permits DeferredShape,
          ScalarShape,
          NullableShape,
          SequenceShape,
          MapShape,
          EnumShape,
          AggregateShape {
    CoreType type();
  }

  static final class DeferredShape implements Shape {
    private final CoreType type;
    private Shape resolved;
    private RuntimeException failure;

    DeferredShape(CoreType type) {
      this.type = type;
    }

    @Override
    public CoreType type() {
      return type;
    }

    void complete(Shape shape) {
      resolved = shape;
    }

    void fail(RuntimeException cause) {
      failure = cause;
    }

    Shape resolved() {
      if (failure != null) throw failure;
      if (resolved == null) throw new IllegalStateException("serialization shape is unresolved");
      return resolved;
    }
  }

  static Shape resolved(Shape shape) {
    Shape current = shape;
    while (current instanceof DeferredShape deferred) current = deferred.resolved();
    return current;
  }

  enum ScalarKind {
    STRING,
    BOOLEAN,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    CODE_POINT
  }

  record ScalarShape(CoreType type, ScalarKind kind) implements Shape {}

  record NullableShape(CoreType type, Shape value) implements Shape {}

  enum SequenceKind {
    ARRAY,
    LIST
  }

  record SequenceShape(CoreType type, SequenceKind kind, Shape element) implements Shape {}

  record MapShape(CoreType type, Shape key, Shape value) implements Shape {}

  record EnumShape(CoreType type, DefinitionId definition, CoreDefinition.Enum declaration)
      implements Shape {}

  record FieldShape(
      int ordinal,
      String coreName,
      String name,
      DefinitionOccurrenceId owner,
      CoreType type,
      boolean ignored,
      Shape shape) {}

  record AggregateShape(
      CoreType type,
      DefinitionId definition,
      String name,
      RuntimeValues.AggregateInfo info,
      List<FieldShape> fields,
      Map<String, Integer> names)
      implements Shape {
    AggregateShape {
      fields = List.copyOf(fields);
      names = Map.copyOf(names);
    }
  }

  static final class ShapeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String path;

    ShapeException(String code, String path, String message) {
      super(message);
      this.code = code;
      this.path = path;
    }

    String code() {
      return code;
    }

    String path() {
      return path;
    }
  }
}
