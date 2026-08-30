package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.CoreType;
import java.util.LinkedHashMap;
import java.util.Map;

final class MapperEngine {
  private final SerializationRuntime shapes;
  private final Map<PlanKey, CompiledReaderPlan<?>> readers = new LinkedHashMap<>();
  private final Map<PlanKey, CompiledWriterPlan<?>> writers = new LinkedHashMap<>();

  MapperEngine(SerializationRuntime shapes) {
    this.shapes = java.util.Objects.requireNonNull(shapes, "shapes");
  }

  <P> P write(
      DataFormat<P> format, CoreType type, Object value, ExecutionState execution, Node location) {
    try {
      CompiledWriterPlan<P> plan = writer(format, type);
      return plan.write(value, execution, location);
    } catch (SerializationRuntime.ShapeException failure) {
      throw format.shapeFailure(failure, execution, location);
    }
  }

  <P> Object read(
      DataFormat<P> format, CoreType type, P source, ExecutionState execution, Node location) {
    try {
      CompiledReaderPlan<P> plan = reader(format, type);
      return plan.read(source, execution, location);
    } catch (SerializationRuntime.ShapeException failure) {
      throw format.shapeFailure(failure, execution, location);
    }
  }

  synchronized int cachedReaderCount() {
    return readers.size();
  }

  synchronized int cachedWriterCount() {
    return writers.size();
  }

  private synchronized <P> CompiledWriterPlan<P> writer(DataFormat<P> format, CoreType type) {
    PlanKey key = new PlanKey(format, type);
    @SuppressWarnings("unchecked")
    CompiledWriterPlan<P> plan =
        (CompiledWriterPlan<P>)
            writers.computeIfAbsent(key, ignored -> format.compileWriter(shapes.shape(type)));
    return plan;
  }

  private synchronized <P> CompiledReaderPlan<P> reader(DataFormat<P> format, CoreType type) {
    PlanKey key = new PlanKey(format, type);
    @SuppressWarnings("unchecked")
    CompiledReaderPlan<P> plan =
        (CompiledReaderPlan<P>)
            readers.computeIfAbsent(key, ignored -> format.compileReader(shapes.shape(type)));
    return plan;
  }

  private record PlanKey(DataFormat<?> format, CoreType type) {}
}
