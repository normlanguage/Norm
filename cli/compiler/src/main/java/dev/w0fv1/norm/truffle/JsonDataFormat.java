package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;

final class JsonDataFormat implements DataFormat<String> {
  static final JsonDataFormat INSTANCE = new JsonDataFormat();

  private JsonDataFormat() {}

  @Override
  public CompiledReaderPlan<String> compileReader(SerializationRuntime.Shape shape) {
    return (source, execution, location) -> JsonRuntime.decode(source, shape, execution, location);
  }

  @Override
  public CompiledWriterPlan<String> compileWriter(SerializationRuntime.Shape shape) {
    return (value, execution, location) -> JsonRuntime.encode(value, shape, execution, location);
  }

  @Override
  public NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return JsonRuntime.shapeFailure(failure, execution, location);
  }
}
