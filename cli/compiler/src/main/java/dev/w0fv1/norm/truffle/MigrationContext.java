package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreType;
import java.util.Optional;

interface MigrationContext extends AutoCloseable {
  record StateField(RuntimeValues.FieldKey key, CoreType type) {}

  CoreType objectType();

  CoreType targetType();

  AnnotationRuntime sourceAnnotations();

  AnnotationRuntime targetAnnotations();

  Optional<StateField> sourceField(String name);

  Optional<StateField> targetField(String name);

  Object read(RuntimeValues.FieldKey key, CoreType expected);

  void write(RuntimeValues.FieldKey key, CoreType declared, Object value);

  void remove(RuntimeValues.FieldKey key);

  String variant();

  Object readPayload(int index, CoreType expected);

  void replace(Object value);

  Object replacement();

  void validate();

  @Override
  void close();
}
