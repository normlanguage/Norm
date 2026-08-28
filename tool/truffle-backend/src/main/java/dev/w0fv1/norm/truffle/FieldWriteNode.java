package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreInterceptor;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import java.util.List;

final class FieldWriteNode extends Node {
  private static final CoreType FIELD_CONTEXT_TYPE = builtin("std.core.FieldContext");
  private static final CoreType FIELD_TYPE =
      builtin("std.core.Field", List.of(CoreType.EXISTENTIAL, CoreType.EXISTENTIAL));
  private static final CoreType COMPLETION_TYPE = builtin("std.core.FunctionCompletion");
  private final AnnotationRuntime annotations;
  @Child private AnnotationLifecycleNode lifecycle = new AnnotationLifecycleNode();

  FieldWriteNode(AnnotationRuntime annotations) {
    this.annotations = annotations;
  }

  void execute(
      RuntimeValues.ObjectValue receiver, int field, Object value, ExecutionState execution) {
    RuntimeValues.FieldPlan plan =
        ((RuntimeValues.AggregateInfo) receiver.objectInfo).fields().get(field);
    invoke(0, receiver, plan, value, execution);
  }

  private void invoke(
      int layer,
      RuntimeValues.ObjectValue receiver,
      RuntimeValues.FieldPlan plan,
      Object value,
      ExecutionState execution) {
    if (layer == plan.interceptors().size()) {
      receiver.fields[plan.index()] = RuntimeValues.copy(value);
      return;
    }
    CoreInterceptor interceptor = plan.interceptors().get(layer);
    RuntimeValues.FieldContextValue context =
        new RuntimeValues.FieldContextValue(
            FIELD_CONTEXT_TYPE, annotations.field(plan, receiver.type, FIELD_TYPE));
    RuntimeValues.ObjectValue annotation =
        annotations.fieldAnnotation(plan.owner(), plan.index(), interceptor, execution);
    Object transformed =
        lifecycle.execute(
            annotations.fieldBefore(annotation),
            execution,
            annotation,
            new Object[] {context, RuntimeValues.copy(value)},
            new CoreType[0]);
    boolean succeeded = false;
    try {
      invoke(layer + 1, receiver, plan, RuntimeValues.copy(transformed), execution);
      succeeded = true;
    } finally {
      lifecycle.execute(
          annotations.fieldAfter(annotation),
          execution,
          annotation,
          new Object[] {
            context, new RuntimeValues.FunctionCompletionValue(COMPLETION_TYPE, succeeded)
          },
          new CoreType[0]);
    }
  }

  private static CoreType builtin(String identity) {
    return builtin(identity, List.of());
  }

  private static CoreType builtin(String identity, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId(identity)),
        arguments,
        CoreValueCategory.IDENTITY,
        CoreNullability.NON_NULL);
  }
}
