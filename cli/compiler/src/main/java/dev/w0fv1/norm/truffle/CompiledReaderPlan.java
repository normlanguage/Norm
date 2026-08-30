package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;

interface CompiledReaderPlan<P> {
  Object read(P source, ExecutionState execution, Node location);
}
