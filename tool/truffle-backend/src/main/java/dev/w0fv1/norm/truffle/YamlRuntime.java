package dev.w0fv1.norm.truffle;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.oracle.truffle.api.nodes.Node;

final class YamlRuntime {
  private static final YAMLFactory FACTORY =
      YAMLFactory.builder()
          .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
          .streamReadConstraints(
              StreamReadConstraints.builder()
                  .maxDocumentLength(JacksonDataRuntime.MAX_INPUT_BYTES)
                  .maxNestingDepth(JacksonDataRuntime.MAX_DEPTH)
                  .maxStringLength(JacksonDataRuntime.MAX_STRING_LENGTH)
                  .maxNameLength(JacksonDataRuntime.MAX_STRING_LENGTH)
                  .build())
          .streamWriteConstraints(
              StreamWriteConstraints.builder()
                  .maxNestingDepth(JacksonDataRuntime.MAX_DEPTH)
                  .build())
          .build();
  private static final JacksonDataRuntime DATA =
      new JacksonDataRuntime(
          "YAML",
          FACTORY,
          reader -> ((YAMLParser) reader).isCurrentAlias(),
          (code, message, path, offset, line, column, execution, location) ->
              execution
                  .values()
                  .yamlException(code, message, path, offset, line, column, execution, location));

  private YamlRuntime() {}

  static String encode(
      Object value, SerializationRuntime.Shape shape, ExecutionState execution, Node location) {
    String encoded = DATA.encode(value, shape, execution, location);
    if (encoded.endsWith("\r\n")) return encoded.substring(0, encoded.length() - 2);
    if (encoded.endsWith("\n")) return encoded.substring(0, encoded.length() - 1);
    return encoded;
  }

  static Object decode(
      String input, SerializationRuntime.Shape shape, ExecutionState execution, Node location) {
    return DATA.decode(input, shape, execution, location);
  }

  static NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return DATA.shapeFailure(failure, execution, location);
  }
}
