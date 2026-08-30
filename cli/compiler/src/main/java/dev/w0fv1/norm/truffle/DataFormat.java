package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;

interface DataFormat<P> {
  CompiledReaderPlan<P> compileReader(SerializationRuntime.Shape shape);

  CompiledWriterPlan<P> compileWriter(SerializationRuntime.Shape shape);

  NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location);
}
