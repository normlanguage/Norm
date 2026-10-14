package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
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
  @Child private AnnotationLifecycleNode lifecycle = new AnnotationLifecycleNode();

  void execute(
      RuntimeValues.ObjectValue receiver,
      RuntimeValues.FieldKey field,
      Object value,
      ExecutionState execution) {
    var view = receiver.info(execution);
    receiver.ensureField(field, execution);
    RuntimeValues.FieldPlan plan =
        view.fields().stream()
            .filter(candidate -> candidate.key().equals(field))
            .findFirst()
            .orElseThrow();
    AnnotationRuntime runtime = view.annotations();
    invoke(0, receiver, plan, value, execution, runtime);
  }

  private void invoke(
      int layer,
      RuntimeValues.ObjectValue receiver,
      RuntimeValues.FieldPlan plan,
      Object value,
      ExecutionState execution,
      AnnotationRuntime annotations) {
    if (layer == interceptorCount(plan)) {
      Object stored = RuntimeValues.copy(value);
      Object previous = receiver.rawField(plan.key());
      receiver.writeRawField(plan.key(), stored);
      if (receiver.hostValue != null) {
        ClassLoader loader = execution.applicationClassLoader();
        RuntimeValues.afterStateCommit(
            () -> writeHostField(loader, receiver.hostValue, plan.name(), stored));
      }
      receiver.fieldChanged(receiver.fieldIndex(plan.key()), previous);
      return;
    }
    CoreInterceptor interceptor = interceptor(plan, layer);
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
      invoke(layer + 1, receiver, plan, RuntimeValues.copy(transformed), execution, annotations);
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

  @TruffleBoundary
  private static int interceptorCount(RuntimeValues.FieldPlan plan) {
    return plan.interceptors().size();
  }

  @TruffleBoundary
  private static CoreInterceptor interceptor(RuntimeValues.FieldPlan plan, int index) {
    return plan.interceptors().get(index);
  }

  @TruffleBoundary
  private static void writeHostField(
      ClassLoader loader, Object receiver, String name, Object value) {
    JavaApplicationBridge.writeField(loader, receiver, name, value);
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
