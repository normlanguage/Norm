package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreStateMigrationProtocol;

final class MigrationPolicy {
  private MigrationPolicy() {}

  static boolean migrate(MigrationContext context, ExecutionState execution) {
    var annotations = context.targetAnnotations();
    if (annotations == null) return false;
    var protocol = CoreStateMigrationProtocol.resolve(annotations.program().structures());
    if (protocol.isEmpty()) return false;
    var contract = protocol.orElseThrow();
    var annotation =
        annotations.annotation(context.targetType(), contract.annotationType(), execution);
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

  static Object migrateValue(Object value, ExecutionState execution) {
    if (!(value instanceof RuntimeValues.EnumValue enumeration)
        || enumeration.annotations() == execution.runtime()) return value;
    try (var context = new EnumMigrationContext(enumeration, execution.runtime())) {
      if (context.compatible()) context.adoptCompatible();
      else if (!migrate(context, execution))
        throw new RuntimeValues.StateMigrationFailure(
            "Retained enum requires an explicit state converter: " + enumeration);
      context.validate();
      return context.replacement();
    }
  }

  static final class Handle implements AutoCloseable {
    private MigrationContext context;

    Handle(MigrationContext context) {
      this.context = java.util.Objects.requireNonNull(context);
    }

    MigrationContext context() {
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
