package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.CoreEnumVariant;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

final class XmlRuntime {
  private static final StructuredValueAccess VALUES = new StructuredValueAccess();
  private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
  private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
  private static final int MAX_TEXT_LENGTH = 1024 * 1024;
  private static final int MAX_ELEMENTS = 1_000_000;
  private static final int MAX_DEPTH = 128;

  private XmlRuntime() {}

  static String encode(Object value, XmlPlan plan, ExecutionState execution, Node location) {
    try {
      StringWriter output = new StringWriter();
      XMLStreamWriter writer = outputFactory().createXMLStreamWriter(output);
      Counter elements = new Counter();
      writeElement(writer, plan.rootName(), plan.root(), value, "$", 0, elements, plan);
      writer.close();
      return output.toString();
    } catch (XmlFailure failure) {
      throw xmlFailure(failure, execution, location);
    } catch (XMLStreamException failure) {
      throw xmlException(
          "NORM-XML-WRITE",
          message(failure, "cannot write XML"),
          "$",
          0,
          1,
          1,
          execution,
          location);
    }
  }

  static Object decode(String source, XmlPlan plan, ExecutionState execution, Node location) {
    if (source.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
      throw xmlException(
          "NORM-XML-LIMIT", "XML input exceeds the byte limit", "$", 0, 1, 1, execution, location);
    }
    XMLStreamReader reader = null;
    try {
      reader = inputFactory().createXMLStreamReader(new StringReader(source));
      moveToRoot(reader);
      Counter elements = new Counter();
      Object result =
          readElement(reader, plan.rootName(), plan.root(), "$", 0, elements, plan, execution);
      requireDocumentEnd(reader);
      reader.close();
      return result;
    } catch (XmlFailure failure) {
      close(reader);
      throw xmlFailure(failure, execution, location);
    } catch (XMLStreamException failure) {
      Location position = location(failure, reader);
      close(reader);
      throw xmlException(
          "NORM-XML-SYNTAX",
          message(failure, "invalid XML"),
          "$",
          position.offset(),
          position.line(),
          position.column(),
          execution,
          location);
    }
  }

  static NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return xmlException(
        failure.code().replace("NORM-SERIALIZATION-", "NORM-XML-"),
        failure.getMessage(),
        failure.path(),
        0,
        1,
        1,
        execution,
        location);
  }

  private static void writeElement(
      XMLStreamWriter writer,
      String name,
      SerializationRuntime.Shape source,
      Object value,
      String path,
      int depth,
      Counter elements,
      XmlPlan plan)
      throws XMLStreamException {
    requireDepth(path, depth);
    requireElement(elements, path);
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    writer.writeStartElement(name);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      if (value == RuntimeValues.NullValue.INSTANCE) {
        writer.writeNamespace("xsi", XSI);
        writer.writeAttribute("xsi", XSI, "nil", "true");
        writer.writeEndElement();
        return;
      }
      shape = SerializationRuntime.resolved(nullable.value());
    } else if (value == RuntimeValues.NullValue.INSTANCE) {
      throw new XmlFailure("NORM-XML-NULL", path, "null is not allowed");
    }
    writeContent(writer, shape, value, path, depth, elements, plan);
    writer.writeEndElement();
  }

  private static void writeContent(
      XMLStreamWriter writer,
      SerializationRuntime.Shape shape,
      Object value,
      String path,
      int depth,
      Counter elements,
      XmlPlan plan)
      throws XMLStreamException {
    switch (shape) {
      case SerializationRuntime.ScalarShape scalar ->
          writer.writeCharacters(scalarText(scalar, value, path));
      case SerializationRuntime.EnumShape enumeration ->
          writer.writeCharacters(enumVariant(enumeration, value, path));
      case SerializationRuntime.SequenceShape sequence -> {
        List<Object> values = sequenceValues(sequence, value, path);
        requireCollection(values.size(), path);
        for (int index = 0; index < values.size(); index++) {
          writeElement(
              writer,
              "item",
              sequence.element(),
              values.get(index),
              index(path, index),
              depth + 1,
              elements,
              plan);
        }
      }
      case SerializationRuntime.MapShape map -> {
        List<StructuredValueAccess.MapEntry> entries = mapValues(map, value, path);
        requireCollection(entries.size(), path);
        for (int index = 0; index < entries.size(); index++) {
          StructuredValueAccess.MapEntry entry = entries.get(index);
          requireElement(elements, index(path, index));
          writer.writeStartElement("entry");
          writeElement(
              writer,
              "key",
              map.key(),
              entry.key(),
              index(path, index) + ".key",
              depth + 2,
              elements,
              plan);
          writeElement(
              writer,
              "value",
              map.value(),
              entry.value(),
              index(path, index) + ".value",
              depth + 2,
              elements,
              plan);
          writer.writeEndElement();
        }
      }
      case SerializationRuntime.AggregateShape aggregate -> {
        StructuredValueAccess.AggregateValue object = aggregateValue(aggregate, value, path);
        XmlPlan.AggregatePlan aggregatePlan = plan.aggregate(aggregate);
        for (SerializationRuntime.FieldShape field : aggregatePlan.attributes()) {
          Object fieldValue = object.field(field.ordinal());
          if (fieldValue == RuntimeValues.NullValue.INSTANCE) continue;
          writer.writeAttribute(
              field.name(), attributeText(field.shape(), fieldValue, field(path, field.name())));
        }
        for (SerializationRuntime.FieldShape field : aggregatePlan.elements()) {
          Object fieldValue = object.field(field.ordinal());
          if (fieldValue == RuntimeValues.NullValue.INSTANCE
              && SerializationRuntime.resolved(field.shape())
                  instanceof SerializationRuntime.NullableShape) continue;
          writeElement(
              writer,
              field.name(),
              field.shape(),
              fieldValue,
              field(path, field.name()),
              depth + 1,
              elements,
              plan);
        }
      }
      case SerializationRuntime.NullableShape ignored ->
          throw new IllegalStateException("nullable shape was not unwrapped");
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred shape was not resolved");
    }
  }

  private static Object readElement(
      XMLStreamReader reader,
      String name,
      SerializationRuntime.Shape source,
      String path,
      int depth,
      Counter elements,
      XmlPlan plan,
      ExecutionState execution)
      throws XMLStreamException {
    requireDepth(path, depth);
    requireElement(elements, path);
    requireStart(reader, name, path);
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    boolean nullable = shape instanceof SerializationRuntime.NullableShape;
    if (nil(reader)) {
      if (!nullable) throw failure(reader, "NORM-XML-NULL", path, "null is not allowed");
      consumeEmpty(reader, path);
      return RuntimeValues.NullValue.INSTANCE;
    }
    if (nullable) {
      shape = SerializationRuntime.resolved(((SerializationRuntime.NullableShape) shape).value());
    }
    return switch (shape) {
      case SerializationRuntime.ScalarShape scalar ->
          parseScalar(scalar, readText(reader, path), path, reader);
      case SerializationRuntime.EnumShape enumeration ->
          parseEnum(enumeration, readText(reader, path), path, reader);
      case SerializationRuntime.SequenceShape sequence ->
          readSequence(reader, sequence, path, depth, elements, plan, execution);
      case SerializationRuntime.MapShape map ->
          readMap(reader, map, path, depth, elements, plan, execution);
      case SerializationRuntime.AggregateShape aggregate ->
          readAggregate(reader, aggregate, path, depth, elements, plan, execution);
      case SerializationRuntime.NullableShape ignored ->
          throw new IllegalStateException("nullable shape was not unwrapped");
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred shape was not resolved");
    };
  }

  private static Object readSequence(
      XMLStreamReader reader,
      SerializationRuntime.SequenceShape shape,
      String path,
      int depth,
      Counter elements,
      XmlPlan plan,
      ExecutionState execution)
      throws XMLStreamException {
    List<Object> values = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) break;
      if (ignorable(reader, event)) continue;
      if (event != XMLStreamConstants.START_ELEMENT || !reader.getLocalName().equals("item")) {
        throw failure(reader, "NORM-XML-UNKNOWN-FIELD", path, "expected XML item element");
      }
      requireCollection(values.size() + 1, path);
      values.add(
          readElement(
              reader,
              "item",
              shape.element(),
              index(path, values.size()),
              depth + 1,
              elements,
              plan,
              execution));
    }
    return VALUES.sequence(shape, values);
  }

  private static Object readMap(
      XMLStreamReader reader,
      SerializationRuntime.MapShape shape,
      String path,
      int depth,
      Counter elements,
      XmlPlan plan,
      ExecutionState execution)
      throws XMLStreamException {
    RuntimeValues.MapValue result = VALUES.map(shape);
    int count = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) break;
      if (ignorable(reader, event)) continue;
      if (event != XMLStreamConstants.START_ELEMENT || !reader.getLocalName().equals("entry")) {
        throw failure(reader, "NORM-XML-UNKNOWN-FIELD", path, "expected XML entry element");
      }
      requireElement(elements, index(path, count));
      requireCollection(++count, path);
      Object key = null;
      Object value = null;
      boolean keySeen = false;
      boolean valueSeen = false;
      while (reader.hasNext()) {
        event = reader.next();
        if (event == XMLStreamConstants.END_ELEMENT) break;
        if (ignorable(reader, event)) continue;
        if (event != XMLStreamConstants.START_ELEMENT) {
          throw failure(reader, "NORM-XML-SYNTAX", path, "invalid XML map entry");
        }
        if (reader.getLocalName().equals("key") && !keySeen) {
          key =
              readElement(
                  reader,
                  "key",
                  shape.key(),
                  index(path, count - 1) + ".key",
                  depth + 2,
                  elements,
                  plan,
                  execution);
          keySeen = true;
        } else if (reader.getLocalName().equals("value") && !valueSeen) {
          value =
              readElement(
                  reader,
                  "value",
                  shape.value(),
                  index(path, count - 1) + ".value",
                  depth + 2,
                  elements,
                  plan,
                  execution);
          valueSeen = true;
        } else {
          throw failure(reader, "NORM-XML-DUPLICATE-FIELD", path, "invalid XML map entry field");
        }
      }
      if (!keySeen || !valueSeen) {
        throw failure(reader, "NORM-XML-MISSING-FIELD", path, "XML map entry is incomplete");
      }
      if (VALUES.contains(result, key)) {
        throw failure(reader, "NORM-XML-DUPLICATE-KEY", path, "duplicate XML map key");
      }
      VALUES.put(result, key, value);
    }
    return result;
  }

  private static Object readAggregate(
      XMLStreamReader reader,
      SerializationRuntime.AggregateShape shape,
      String path,
      int depth,
      Counter elements,
      XmlPlan plan,
      ExecutionState execution)
      throws XMLStreamException {
    XmlPlan.AggregatePlan aggregatePlan = plan.aggregate(shape);
    Object[] fields = new Object[shape.fields().size()];
    boolean[] seen = new boolean[shape.fields().size()];
    for (int index = 0; index < reader.getAttributeCount(); index++) {
      String namespace = reader.getAttributeNamespace(index);
      if (XSI.equals(namespace) && reader.getAttributeLocalName(index).equals("nil")) continue;
      String name = reader.getAttributeLocalName(index);
      SerializationRuntime.FieldShape field = aggregatePlan.attributeNames().get(name);
      if (field == null) {
        throw failure(
            reader, "NORM-XML-UNKNOWN-FIELD", path + ".@" + name, "unknown XML attribute");
      }
      fields[field.ordinal()] =
          parseAttribute(
              field.shape(), reader.getAttributeValue(index), path + ".@" + name, reader);
      seen[field.ordinal()] = true;
    }
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) break;
      if (ignorable(reader, event)) continue;
      if (event != XMLStreamConstants.START_ELEMENT) {
        throw failure(reader, "NORM-XML-SYNTAX", path, "mixed XML content is not supported");
      }
      String name = reader.getLocalName();
      SerializationRuntime.FieldShape field = aggregatePlan.elementNames().get(name);
      if (field == null) {
        throw failure(
            reader,
            "NORM-XML-UNKNOWN-FIELD",
            field(path, name),
            "unknown XML field '" + name + "'");
      }
      if (seen[field.ordinal()]) {
        throw failure(
            reader,
            "NORM-XML-DUPLICATE-FIELD",
            field(path, name),
            "duplicate XML field '" + name + "'");
      }
      fields[field.ordinal()] =
          readElement(
              reader, name, field.shape(), field(path, name), depth + 1, elements, plan, execution);
      seen[field.ordinal()] = true;
    }
    for (SerializationRuntime.FieldShape field : shape.fields()) {
      if (field.ignored()) {
        fields[field.ordinal()] = RuntimeValues.NullValue.INSTANCE;
      } else if (!seen[field.ordinal()]) {
        if (SerializationRuntime.resolved(field.shape())
            instanceof SerializationRuntime.NullableShape) {
          fields[field.ordinal()] = RuntimeValues.NullValue.INSTANCE;
        } else {
          throw failure(
              reader,
              "NORM-XML-MISSING-FIELD",
              field(path, field.name()),
              "required XML field is missing");
        }
      }
    }
    return VALUES.aggregate(shape, fields, execution);
  }

  private static String attributeText(
      SerializationRuntime.Shape source, Object value, String path) {
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      shape = SerializationRuntime.resolved(nullable.value());
    }
    return switch (shape) {
      case SerializationRuntime.ScalarShape scalar -> scalarText(scalar, value, path);
      case SerializationRuntime.EnumShape enumeration -> enumVariant(enumeration, value, path);
      default -> throw new IllegalStateException("invalid XML attribute shape");
    };
  }

  private static Object parseAttribute(
      SerializationRuntime.Shape source, String value, String path, XMLStreamReader reader) {
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      shape = SerializationRuntime.resolved(nullable.value());
    }
    return switch (shape) {
      case SerializationRuntime.ScalarShape scalar -> parseScalar(scalar, value, path, reader);
      case SerializationRuntime.EnumShape enumeration ->
          parseEnum(enumeration, value, path, reader);
      default -> throw new IllegalStateException("invalid XML attribute shape");
    };
  }

  private static String scalarText(
      SerializationRuntime.ScalarShape shape, Object value, String path) {
    return switch (shape.kind()) {
      case STRING -> {
        if (!(value instanceof String text)) throw typeMismatch(path, "String");
        requireText(text, path);
        yield text;
      }
      case BOOLEAN -> {
        if (!(value instanceof Boolean bool)) throw typeMismatch(path, "Boolean");
        yield bool.toString();
      }
      case INTEGER -> {
        if (!(value instanceof Integer integer)) throw typeMismatch(path, "Integer");
        yield integer.toString();
      }
      case LONG -> {
        if (!(value instanceof Long number)) throw typeMismatch(path, "Long");
        yield number.toString();
      }
      case FLOAT -> {
        if (!(value instanceof Float number) || !Float.isFinite(number)) {
          throw new XmlFailure("NORM-XML-NUMBER", path, "Float must be finite");
        }
        yield number.toString();
      }
      case DOUBLE -> {
        if (!(value instanceof Double number) || !Double.isFinite(number)) {
          throw new XmlFailure("NORM-XML-NUMBER", path, "Double must be finite");
        }
        yield number.toString();
      }
      case CODE_POINT -> {
        if (!(value instanceof RuntimeValues.CodePointValue point)) {
          throw typeMismatch(path, "CodePoint");
        }
        yield point.toString();
      }
    };
  }

  private static Object parseScalar(
      SerializationRuntime.ScalarShape shape, String source, String path, XMLStreamReader reader) {
    requireText(source, path);
    return switch (shape.kind()) {
      case STRING -> source;
      case BOOLEAN -> {
        String value = source.trim();
        if (value.equals("true")) yield true;
        if (value.equals("false")) yield false;
        throw failure(reader, "NORM-XML-TYPE", path, "expected Boolean");
      }
      case INTEGER -> parseInteger(source.trim(), path, reader);
      case LONG -> parseLong(source.trim(), path, reader);
      case FLOAT -> parseFloat(source.trim(), path, reader);
      case DOUBLE -> parseDouble(source.trim(), path, reader);
      case CODE_POINT -> {
        if (source.codePointCount(0, source.length()) != 1) {
          throw failure(reader, "NORM-XML-CODE-POINT", path, "expected one Unicode code point");
        }
        yield new RuntimeValues.CodePointValue(source.codePointAt(0));
      }
    };
  }

  private static Object parseEnum(
      SerializationRuntime.EnumShape shape, String source, String path, XMLStreamReader reader) {
    String value = source.trim();
    CoreEnumVariant variant =
        shape.declaration().variants().stream()
            .filter(candidate -> candidate.key().equals(value))
            .findFirst()
            .orElseThrow(
                () ->
                    failure(reader, "NORM-XML-ENUM", path, "unknown enum variant '" + value + "'"));
    return VALUES.enumeration(shape, variant);
  }

  private static int parseInteger(String value, String path, XMLStreamReader reader) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException failure) {
      throw failure(reader, "NORM-XML-NUMBER", path, "Integer is out of range");
    }
  }

  private static long parseLong(String value, String path, XMLStreamReader reader) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException failure) {
      throw failure(reader, "NORM-XML-NUMBER", path, "Long is out of range");
    }
  }

  private static float parseFloat(String value, String path, XMLStreamReader reader) {
    try {
      float result = Float.parseFloat(value);
      if (!Float.isFinite(result)) throw new NumberFormatException();
      return result;
    } catch (NumberFormatException failure) {
      throw failure(reader, "NORM-XML-NUMBER", path, "Float is invalid or out of range");
    }
  }

  private static double parseDouble(String value, String path, XMLStreamReader reader) {
    try {
      double result = Double.parseDouble(value);
      if (!Double.isFinite(result)) throw new NumberFormatException();
      return result;
    } catch (NumberFormatException failure) {
      throw failure(reader, "NORM-XML-NUMBER", path, "Double is invalid or out of range");
    }
  }

  private static String readText(XMLStreamReader reader, String path) throws XMLStreamException {
    StringBuilder text = new StringBuilder();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) {
        String value = text.toString();
        requireText(value, path);
        return value;
      }
      if (event == XMLStreamConstants.CHARACTERS
          || event == XMLStreamConstants.CDATA
          || event == XMLStreamConstants.SPACE
          || event == XMLStreamConstants.ENTITY_REFERENCE) {
        text.append(reader.getText());
        if (text.length() > MAX_TEXT_LENGTH) {
          throw failure(reader, "NORM-XML-LIMIT", path, "XML text exceeds the length limit");
        }
      } else if (event == XMLStreamConstants.COMMENT
          || event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
      } else {
        throw failure(reader, "NORM-XML-TYPE", path, "expected XML text content");
      }
    }
    throw failure(reader, "NORM-XML-SYNTAX", path, "unterminated XML element");
  }

  private static void consumeEmpty(XMLStreamReader reader, String path) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) return;
      if (!ignorable(reader, event)) {
        throw failure(reader, "NORM-XML-NULL", path, "nil XML element must be empty");
      }
    }
    throw failure(reader, "NORM-XML-SYNTAX", path, "unterminated nil XML element");
  }

  private static void moveToRoot(XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.getEventType();
      if (event == XMLStreamConstants.START_ELEMENT) return;
      if (event == XMLStreamConstants.DTD) {
        throw failure(reader, "NORM-XML-SYNTAX", "$", "XML DTD is not supported");
      }
      if (event == XMLStreamConstants.CHARACTERS && !reader.isWhiteSpace()) {
        throw failure(reader, "NORM-XML-SYNTAX", "$", "text precedes the XML root");
      }
      reader.next();
    }
    throw failure(reader, "NORM-XML-SYNTAX", "$", "XML root element is missing");
  }

  private static void requireDocumentEnd(XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_DOCUMENT) return;
      if (event == XMLStreamConstants.CHARACTERS && reader.isWhiteSpace()) continue;
      if (event == XMLStreamConstants.COMMENT || event == XMLStreamConstants.PROCESSING_INSTRUCTION)
        continue;
      throw failure(reader, "NORM-XML-SYNTAX", "$", "trailing XML content");
    }
  }

  private static void requireStart(XMLStreamReader reader, String name, String path) {
    if (reader.getEventType() != XMLStreamConstants.START_ELEMENT
        || !reader.getLocalName().equals(name)
        || reader.getNamespaceURI() != null && !reader.getNamespaceURI().isEmpty()) {
      String code = path.equals("$") ? "NORM-XML-ROOT" : "NORM-XML-TYPE";
      throw failure(reader, code, path, "expected XML element '" + name + "'");
    }
  }

  private static boolean nil(XMLStreamReader reader) {
    String value = reader.getAttributeValue(XSI, "nil");
    return value != null && (value.equals("true") || value.equals("1"));
  }

  private static boolean ignorable(XMLStreamReader reader, int event) {
    return event == XMLStreamConstants.COMMENT
        || event == XMLStreamConstants.PROCESSING_INSTRUCTION
        || event == XMLStreamConstants.SPACE
        || event == XMLStreamConstants.CHARACTERS && reader.isWhiteSpace();
  }

  private static XMLInputFactory inputFactory() {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
    return factory;
  }

  private static XMLOutputFactory outputFactory() {
    return XMLOutputFactory.newFactory();
  }

  private static void close(XMLStreamReader reader) {
    if (reader == null) return;
    try {
      reader.close();
    } catch (XMLStreamException ignored) {
    }
  }

  private static void requireDepth(String path, int depth) {
    if (depth > MAX_DEPTH) throw new XmlFailure("NORM-XML-LIMIT", path, "XML nesting is too deep");
  }

  private static void requireElement(Counter elements, String path) {
    if (++elements.value > MAX_ELEMENTS) {
      throw new XmlFailure("NORM-XML-LIMIT", path, "XML contains too many elements");
    }
  }

  private static void requireCollection(int size, String path) {
    if (size > MAX_ELEMENTS) {
      throw new XmlFailure("NORM-XML-LIMIT", path, "XML collection contains too many elements");
    }
  }

  private static void requireText(String value, String path) {
    if (value.length() > MAX_TEXT_LENGTH) {
      throw new XmlFailure("NORM-XML-LIMIT", path, "XML text exceeds the length limit");
    }
  }

  private static List<Object> sequenceValues(
      SerializationRuntime.SequenceShape shape, Object value, String path) {
    try {
      return VALUES.sequence(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private static List<StructuredValueAccess.MapEntry> mapValues(
      SerializationRuntime.MapShape shape, Object value, String path) {
    try {
      return VALUES.map(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private static StructuredValueAccess.AggregateValue aggregateValue(
      SerializationRuntime.AggregateShape shape, Object value, String path) {
    try {
      return VALUES.aggregate(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private static String enumVariant(
      SerializationRuntime.EnumShape shape, Object value, String path) {
    try {
      return VALUES.enumVariant(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private static XmlFailure typeMismatch(String path, String expected) {
    return new XmlFailure("NORM-XML-TYPE", path, "expected " + expected);
  }

  private static XmlFailure failure(
      XMLStreamReader reader, String code, String path, String message) {
    javax.xml.stream.Location location = reader.getLocation();
    return new XmlFailure(
        code,
        path,
        message,
        Math.max(0, location.getCharacterOffset()),
        Math.max(1, location.getLineNumber()),
        Math.max(1, location.getColumnNumber()));
  }

  private static NormThrownException xmlFailure(
      XmlFailure failure, ExecutionState execution, Node location) {
    return xmlException(
        failure.code,
        failure.getMessage(),
        failure.path,
        failure.offset,
        failure.line,
        failure.column,
        execution,
        location);
  }

  private static NormThrownException xmlException(
      String code,
      String message,
      String path,
      int offset,
      int line,
      int column,
      ExecutionState execution,
      Node location) {
    return execution
        .values()
        .xmlException(code, message, path, offset, line, column, execution, location);
  }

  private static Location location(XMLStreamException failure, XMLStreamReader reader) {
    javax.xml.stream.Location location = failure.getLocation();
    if (location == null && reader != null) location = reader.getLocation();
    if (location == null) return new Location(0, 1, 1);
    return new Location(
        Math.max(0, location.getCharacterOffset()),
        Math.max(1, location.getLineNumber()),
        Math.max(1, location.getColumnNumber()));
  }

  private static String message(XMLStreamException failure, String fallback) {
    return failure.getMessage() == null ? fallback : failure.getMessage();
  }

  private static String field(String path, String name) {
    return path + "." + name;
  }

  private static String index(String path, int index) {
    return path + "[" + index + "]";
  }

  private record Location(int offset, int line, int column) {}

  private static final class Counter {
    private int value;
  }

  private static final class XmlFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String path;
    private final int offset;
    private final int line;
    private final int column;

    XmlFailure(String code, String path, String message) {
      this(code, path, message, 0, 1, 1);
    }

    XmlFailure(String code, String path, String message, int offset, int line, int column) {
      super(message);
      this.code = code;
      this.path = path;
      this.offset = offset;
      this.line = line;
      this.column = column;
    }
  }
}
