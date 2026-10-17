package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class EnumMigrationContext implements MigrationContext {
  private final RuntimeValues.EnumValue source;
  private final AnnotationRuntime target;
  private final CoreDefinition.Enum sourceDefinition;
  private final CoreDefinition.Enum targetDefinition;
  private RuntimeValues.EnumValue replacement;
  private boolean active = true;

  EnumMigrationContext(RuntimeValues.EnumValue source, AnnotationRuntime target) {
    this.source = source;
    this.target = Objects.requireNonNull(target);
    if (source.annotations() == null)
      throw new RuntimeValues.StateMigrationPending("Retained enum has no source schema");
    var previous = source.annotations().program().structure(source.definition()).orElse(null);
    var candidate = target.program().structure(source.definition()).orElse(null);
    if (!(previous instanceof CoreDefinition.Enum previousEnum))
      throw new RuntimeValues.StateMigrationFailure("Retained enum source schema is absent");
    if (!(candidate instanceof CoreDefinition.Enum candidateEnum))
      throw new RuntimeValues.StateMigrationPending(
          "Retained enum declaration is absent from the candidate");
    sourceDefinition = previousEnum;
    targetDefinition = candidateEnum;
  }

  boolean compatible() {
    var previous =
        sourceDefinition.variants().stream()
            .filter(item -> item.key().equals(source.variantKey()))
            .findFirst()
            .orElseThrow();
    var candidate =
        targetDefinition.variants().stream()
            .filter(item -> item.key().equals(source.variantKey()))
            .findFirst();
    if (candidate.isEmpty()
        || dev.w0fv1.norm.core.CoreRuntimeSchema.requiresTypeParameterMigration(
            sourceDefinition.typeParameters(), targetDefinition.typeParameters())) return false;
    var selected = candidate.orElseThrow();
    if (previous.fields().size() != selected.fields().size()) return false;
    for (int index = 0; index < previous.fields().size(); index++) {
      if (!payloadType(previous, index, source.type(), source.annotations())
          .equals(payloadType(selected, index, source.type(), target))) return false;
    }
    return true;
  }

  void adoptCompatible() {
    replace(
        new RuntimeValues.EnumValue(
            source.definition(),
            source.type(),
            targetDefinition.nominalType().name(),
            source.variantKey(),
            source.payloadValues(),
            target));
  }

  @Override
  public CoreType objectType() {
    return source.type();
  }

  @Override
  public CoreType targetType() {
    return replacement == null ? source.type() : replacement.type();
  }

  @Override
  public AnnotationRuntime sourceAnnotations() {
    return source.annotations();
  }

  @Override
  public AnnotationRuntime targetAnnotations() {
    return target;
  }

  @Override
  public Optional<StateField> sourceField(String name) {
    requireActive();
    return Optional.empty();
  }

  @Override
  public Optional<StateField> targetField(String name) {
    requireActive();
    return Optional.empty();
  }

  @Override
  public Object read(RuntimeValues.FieldKey key, CoreType expected) {
    throw new RuntimeValues.StateMigrationFailure("An enum migration uses indexed payload reads");
  }

  @Override
  public void write(RuntimeValues.FieldKey key, CoreType declared, Object value) {
    throw new RuntimeValues.StateMigrationFailure(
        "An enum migration requires a replacement representation");
  }

  @Override
  public void remove(RuntimeValues.FieldKey key) {
    throw new RuntimeValues.StateMigrationFailure(
        "An enum migration requires a replacement representation");
  }

  @Override
  public String variant() {
    requireActive();
    return source.variantKey();
  }

  @Override
  public Object readPayload(int index, CoreType expected) {
    requireActive();
    var variant =
        sourceDefinition.variants().stream()
            .filter(item -> item.key().equals(source.variantKey()))
            .findFirst()
            .orElseThrow();
    if (index < 0 || index >= variant.fields().size())
      throw new RuntimeValues.StateMigrationFailure(
          "Migration source enum payload index is outside its schema: " + index);
    CoreType declared = payloadType(variant, index, source.type(), source.annotations());
    if (!source.annotations().isAssignable(expected, declared))
      throw new RuntimeValues.StateMigrationFailure(
          "Migration source enum payload type does not match: " + index);
    return source.field(index);
  }

  @Override
  public void replace(Object value) {
    requireActive();
    if (!(value instanceof RuntimeValues.EnumValue candidate)
        || !candidate.definition().equals(source.definition()))
      throw new RuntimeValues.StateMigrationFailure(
          "Migration replacement must be the same nominal enum");
    if (candidate.annotations() != target)
      throw new RuntimeValues.StateMigrationFailure(
          "Migration replacement must use the candidate enum schema");
    if (!(candidate.type() instanceof CoreType.Declared declared)
        || declared.arguments().size() != targetDefinition.typeParameters().size())
      throw new RuntimeValues.StateMigrationFailure(
          "Migration replacement enum specialization does not match the candidate schema");
    var variant =
        targetDefinition.variants().stream()
            .filter(item -> item.key().equals(candidate.variantKey()))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeValues.StateMigrationFailure(
                        "Migration replacement variant is absent from the candidate schema"));
    if (variant.fields().size() != candidate.fieldCount())
      throw new RuntimeValues.StateMigrationFailure(
          "Migration replacement payload count does not match the candidate schema");
    for (int index = 0; index < candidate.fieldCount(); index++) {
      CoreType expected = payloadType(variant, index, candidate.type(), target);
      Object payload = candidate.field(index);
      if (payload == RuntimeValues.NullValue.INSTANCE
          ? !expected.isNullable()
          : payload == null || !target.isAssignable(expected, RuntimeValues.runtimeType(payload)))
        throw new RuntimeValues.StateMigrationFailure(
            "Migration replacement payload type does not match: " + index);
    }
    replacement = (RuntimeValues.EnumValue) RuntimeValues.copy(candidate);
  }

  @Override
  public Object replacement() {
    requireActive();
    return replacement;
  }

  @Override
  public void validate() {
    requireActive();
    if (replacement == null)
      throw new RuntimeValues.StateMigrationFailure(
          "Migration has not supplied an enum replacement");
  }

  @Override
  public void close() {
    active = false;
  }

  private CoreType payloadType(
      CoreEnumVariant variant, int index, CoreType type, AnnotationRuntime annotations) {
    List<CoreType> arguments = ((CoreType.Declared) type).arguments();
    return CoreTypes.absolute(
            variant.fields().get(index).type(), source.definition(), annotations.program())
        .substitute(arguments::get);
  }

  private void requireActive() {
    if (!active)
      throw new RuntimeValues.StateMigrationFailure("Migration state is no longer active");
  }
}
