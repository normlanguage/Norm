package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreType;
import java.util.Objects;

record ReflectionBinding(AnnotationRuntime annotations, boolean frozen) {
  ReflectionBinding {
    Objects.requireNonNull(annotations, "annotations");
  }

  AnnotationRuntime select(CoreType type, ExecutionState execution) {
    return frozen ? annotations : execution.runtime().forType(type, annotations);
  }

  ReflectionBinding snapshot() {
    return frozen ? this : new ReflectionBinding(annotations, true);
  }
}
