package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.XmlAbi;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class XmlPlan {
  private final SerializationRuntime.Shape root;
  private final String rootName;
  private final Map<CoreType, AggregatePlan> aggregates;

  private XmlPlan(
      SerializationRuntime.Shape root, String rootName, Map<CoreType, AggregatePlan> aggregates) {
    this.root = root;
    this.rootName = rootName;
    this.aggregates = Map.copyOf(aggregates);
  }

  static XmlPlan compile(SerializationRuntime.Shape shape, SerializationRuntime serialization) {
    SerializationRuntime.Shape root = SerializationRuntime.resolved(shape);
    String rootName = rootName(root);
    requireName(rootName, "$", "root");
    Map<CoreType, AggregatePlan> aggregates = new LinkedHashMap<>();
    DefinitionId attribute =
        serialization.annotation(
            XmlAbi.MODULE_NAME,
            XmlAbi.MODULE_VERSION,
            XmlAbi.PACKAGE_NAME,
            XmlAbi.XML_ATTRIBUTE_ANNOTATION_NAME);
    visit(root, serialization, attribute, aggregates);
    return new XmlPlan(root, rootName, aggregates);
  }

  SerializationRuntime.Shape root() {
    return root;
  }

  String rootName() {
    return rootName;
  }

  AggregatePlan aggregate(SerializationRuntime.AggregateShape shape) {
    AggregatePlan plan = aggregates.get(shape.type());
    if (plan == null) throw new IllegalStateException("XML aggregate plan is absent");
    return plan;
  }

  private static void visit(
      SerializationRuntime.Shape source,
      SerializationRuntime serialization,
      DefinitionId attribute,
      Map<CoreType, AggregatePlan> aggregates) {
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    switch (shape) {
      case SerializationRuntime.NullableShape nullable ->
          visit(nullable.value(), serialization, attribute, aggregates);
      case SerializationRuntime.SequenceShape sequence ->
          visit(sequence.element(), serialization, attribute, aggregates);
      case SerializationRuntime.MapShape map -> {
        visit(map.key(), serialization, attribute, aggregates);
        visit(map.value(), serialization, attribute, aggregates);
      }
      case SerializationRuntime.AggregateShape aggregate -> {
        if (aggregates.containsKey(aggregate.type())) return;
        List<SerializationRuntime.FieldShape> attributes = new ArrayList<>();
        List<SerializationRuntime.FieldShape> elements = new ArrayList<>();
        Map<String, SerializationRuntime.FieldShape> attributeNames = new LinkedHashMap<>();
        Map<String, SerializationRuntime.FieldShape> elementNames = new LinkedHashMap<>();
        for (SerializationRuntime.FieldShape field : aggregate.fields()) {
          if (field.ignored()) continue;
          requireName(field.name(), "$." + field.name(), "field");
          if (serialization.hasFieldAnnotation(attribute, field)) {
            if (!attributeCompatible(field.shape())) {
              throw new SerializationRuntime.ShapeException(
                  "NORM-SERIALIZATION-ATTRIBUTE",
                  "$." + field.name(),
                  "XML attribute requires a scalar or enum field");
            }
            attributes.add(field);
            attributeNames.put(field.name(), field);
          } else {
            elements.add(field);
            elementNames.put(field.name(), field);
          }
        }
        aggregates.put(
            aggregate.type(),
            new AggregatePlan(attributes, elements, attributeNames, elementNames));
        elements.forEach(field -> visit(field.shape(), serialization, attribute, aggregates));
      }
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred shape was not resolved");
      case SerializationRuntime.ScalarShape ignored -> {}
      case SerializationRuntime.EnumShape ignored -> {}
    }
  }

  private static boolean attributeCompatible(SerializationRuntime.Shape source) {
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      return attributeCompatible(nullable.value());
    }
    return shape instanceof SerializationRuntime.ScalarShape
        || shape instanceof SerializationRuntime.EnumShape;
  }

  private static String rootName(SerializationRuntime.Shape shape) {
    return switch (shape) {
      case SerializationRuntime.AggregateShape aggregate -> aggregate.name();
      case SerializationRuntime.EnumShape enumeration ->
          enumeration.declaration().nominalType().name();
      case SerializationRuntime.SequenceShape ignored -> "sequence";
      case SerializationRuntime.MapShape ignored -> "map";
      case SerializationRuntime.NullableShape nullable ->
          rootName(SerializationRuntime.resolved(nullable.value()));
      case SerializationRuntime.ScalarShape ignored -> "value";
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred shape was not resolved");
    };
  }

  private static void requireName(String name, String path, String target) {
    if (name.isEmpty() || !nameStart(name.codePointAt(0))) {
      throw invalidName(path, target, name);
    }
    for (int offset = Character.charCount(name.codePointAt(0)); offset < name.length(); ) {
      int point = name.codePointAt(offset);
      if (!namePart(point)) throw invalidName(path, target, name);
      offset += Character.charCount(point);
    }
  }

  private static boolean nameStart(int point) {
    return point == '_' || Character.isLetter(point);
  }

  private static boolean namePart(int point) {
    return nameStart(point) || Character.isDigit(point) || point == '-' || point == '.';
  }

  private static SerializationRuntime.ShapeException invalidName(
      String path, String target, String name) {
    return new SerializationRuntime.ShapeException(
        "NORM-SERIALIZATION-NAME", path, "invalid XML " + target + " name '" + name + "'");
  }

  record AggregatePlan(
      List<SerializationRuntime.FieldShape> attributes,
      List<SerializationRuntime.FieldShape> elements,
      Map<String, SerializationRuntime.FieldShape> attributeNames,
      Map<String, SerializationRuntime.FieldShape> elementNames) {
    AggregatePlan {
      attributes = List.copyOf(attributes);
      elements = List.copyOf(elements);
      attributeNames = Map.copyOf(attributeNames);
      elementNames = Map.copyOf(elementNames);
    }
  }
}
