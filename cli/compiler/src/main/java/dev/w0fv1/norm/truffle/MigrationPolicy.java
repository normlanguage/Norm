package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreStateMigrationProtocol;

final class MigrationPolicy {
  private MigrationPolicy() {}

  static boolean migrate(RuntimeObjectState.MigrationContext context, ExecutionState execution) {
    var annotations = context.targetInfo().annotations();
    if (annotations == null) return false;
    var protocol = CoreStateMigrationProtocol.resolve(annotations.program().structures());
    if (protocol.isEmpty()) return false;
    var contract = protocol.orElseThrow();
    var annotation =
        annotations.annotation(context.objectType(), contract.annotationType(), execution);
    if (annotation == RuntimeValues.NullValue.INSTANCE) return false;
    if (!(annotation instanceof RuntimeValues.ObjectValue object))
      throw new RuntimeValues.StateMigrationFailure("State migration annotation is not an object");
    var converterField =
        object.info(execution).fields().stream()
            .filter(field -> field.name().equals(CoreStateMigrationProtocol.CONVERTER_FIELD))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeValues.StateMigrationFailure("State migration converter is absent"));
    if (!(object.rawField(converterField.key()) instanceof RuntimeValues.Closure converter))
      throw new RuntimeValues.StateMigrationFailure(
          "State migration converter is not a Norm function");
    try (var handle = new Handle(context)) {
      var state = annotations.values().opaque(contract.stateType(), handle, "MigrationState");
      RuntimeInvocation.invoke(execution, converter, state);
    }
    return true;
  }

  static final class Handle implements AutoCloseable {
    private RuntimeObjectState.MigrationContext context;

    Handle(RuntimeObjectState.MigrationContext context) {
      this.context = java.util.Objects.requireNonNull(context);
    }

    RuntimeObjectState.MigrationContext context() {
      if (context == null)
        throw new RuntimeValues.StateMigrationFailure("Migration state is no longer active");
      return context;
    }

    @Override
    public void close() {
      context = null;
    }
  }
}
