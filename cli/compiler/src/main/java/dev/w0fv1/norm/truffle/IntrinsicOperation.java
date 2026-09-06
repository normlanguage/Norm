package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;

@FunctionalInterface
interface IntrinsicOperation {
  Object execute(
      Object receiver,
      Object[] arguments,
      CoreType type,
      ExecutionContext context,
      Node location,
      AnnotationRuntime annotations,
      ExecutionState execution);

  default Object execute(
      Object receiver, Object[] arguments, CoreType type, ExecutionContext context, Node location) {
    return execute(receiver, arguments, type, context, location, null, null);
  }
}
