package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;

final class MigrationIntrinsicDispatcher {
  private MigrationIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return (receiver, arguments, type, context, location, annotations, execution) -> {
      if (!(arguments[0] instanceof RuntimeValues.OpaqueValue opaque)
          || !(opaque.value instanceof MigrationPolicy.Handle handle))
        throw new RuntimeValues.StateMigrationFailure(
            "Migration operation requires an active migration state");
      var migration = handle.context();
      return switch (intrinsic) {
        case STATE_MIGRATION_CONTAINS -> migration.sourceField((String) arguments[1]).isPresent();
        case STATE_MIGRATION_READ -> {
          var field =
              migration
                  .sourceField((String) arguments[1])
                  .orElseThrow(
                      () ->
                          new RuntimeValues.StateMigrationFailure(
                              "Migration source field is absent: " + arguments[1]));
          yield migration.read(field.key(), type);
        }
        case STATE_MIGRATION_WRITE -> {
          var field = (RuntimeValues.FieldValue) arguments[1];
          migration.write(field.key(), field.fieldType(), arguments[2]);
          yield null;
        }
        case STATE_MIGRATION_COPY -> {
          var source =
              migration
                  .sourceField((String) arguments[1])
                  .orElseThrow(
                      () ->
                          new RuntimeValues.StateMigrationFailure(
                              "Migration source field is absent: " + arguments[1]));
          var target =
              migration
                  .targetField((String) arguments[2])
                  .orElseThrow(
                      () ->
                          new RuntimeValues.StateMigrationFailure(
                              "Migration target field is absent: " + arguments[2]));
          migration.write(target.key(), target.type(), migration.read(source.key(), source.type()));
          yield null;
        }
        case STATE_MIGRATION_REMOVE -> {
          var field =
              migration
                  .sourceField((String) arguments[1])
                  .orElseThrow(
                      () ->
                          new RuntimeValues.StateMigrationFailure(
                              "Migration source field is absent: " + arguments[1]));
          migration.remove(field.key());
          yield null;
        }
        default -> throw new IllegalArgumentException("Not a migration intrinsic: " + intrinsic);
      };
    };
  }
}
