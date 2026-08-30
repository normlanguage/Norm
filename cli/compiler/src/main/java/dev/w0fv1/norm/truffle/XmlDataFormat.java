package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;

final class XmlDataFormat implements DataFormat<String> {
  private final SerializationRuntime serialization;

  XmlDataFormat(SerializationRuntime serialization) {
    this.serialization = java.util.Objects.requireNonNull(serialization, "serialization");
  }

  @Override
  public CompiledReaderPlan<String> compileReader(SerializationRuntime.Shape shape) {
    XmlPlan plan = XmlPlan.compile(shape, serialization);
    return (source, execution, location) -> XmlRuntime.decode(source, plan, execution, location);
  }

  @Override
  public CompiledWriterPlan<String> compileWriter(SerializationRuntime.Shape shape) {
    XmlPlan plan = XmlPlan.compile(shape, serialization);
    return (value, execution, location) -> XmlRuntime.encode(value, plan, execution, location);
  }

  @Override
  public NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return XmlRuntime.shapeFailure(failure, execution, location);
  }
}
