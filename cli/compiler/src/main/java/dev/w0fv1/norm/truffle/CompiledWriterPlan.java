package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;

interface CompiledWriterPlan<P> {
  P write(Object value, ExecutionState execution, Node location);
}
