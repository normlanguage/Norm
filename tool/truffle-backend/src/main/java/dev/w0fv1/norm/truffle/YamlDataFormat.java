package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;

final class YamlDataFormat implements DataFormat<String> {
  static final YamlDataFormat INSTANCE = new YamlDataFormat();

  private YamlDataFormat() {}

  @Override
  public CompiledReaderPlan<String> compileReader(SerializationRuntime.Shape shape) {
    return (source, execution, location) -> YamlRuntime.decode(source, shape, execution, location);
  }

  @Override
  public CompiledWriterPlan<String> compileWriter(SerializationRuntime.Shape shape) {
    return (value, execution, location) -> YamlRuntime.encode(value, shape, execution, location);
  }

  @Override
  public NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return YamlRuntime.shapeFailure(failure, execution, location);
  }
}
