package dev.w0fv1.norm.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CoreNamespace {
  private static final String DOMAIN = "norm:core:interface:v1\0";

  private final InterfaceId id;
  private final List<CoreBinding> bindings;

  private CoreNamespace(InterfaceId id, List<CoreBinding> bindings) {
    this.id = id;
    this.bindings = bindings;
  }

  public static CoreNamespace create(List<CoreBinding> bindings) {
    List<CoreBinding> sorted =
        bindings.stream()
            .sorted(
                (left, right) -> {
                  int canonicalOrder =
                      Arrays.compareUnsigned(canonicalBinding(left), canonicalBinding(right));
                  return canonicalOrder != 0
                      ? canonicalOrder
                      : left.occurrence().compareTo(right.occurrence());
                })
            .toList();
    byte[] canonical = encodeInterface(sorted);
    return new CoreNamespace(
        new InterfaceId(ContentHasher.hash(DOMAIN, CoreIdentityVersion.CURRENT, canonical)),
        sorted);
  }

  public InterfaceId id() {
    return id;
  }

  public List<CoreBinding> bindings() {
    return bindings;
  }

  public Optional<DefinitionId> definition(String packageName, String name) {
    return occurrence(packageName, name).map(DefinitionOccurrenceId::representative);
  }

  public Optional<DefinitionOccurrenceId> occurrence(String packageName, String name) {
    Objects.requireNonNull(packageName, "packageName");
    Objects.requireNonNull(name, "name");
    List<DefinitionOccurrenceId> matches =
        bindings.stream()
            .filter(binding -> binding.packageName().equals(packageName))
            .filter(binding -> binding.ownerName().isEmpty())
            .filter(binding -> binding.name().equals(name))
            .map(CoreBinding::occurrence)
            .distinct()
            .toList();
    return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
  }

  private static byte[] encodeInterface(List<CoreBinding> bindings) {
    CanonicalWriter writer = new CanonicalWriter().writeTag("interface").writeInt(bindings.size());
    bindings.forEach(binding -> writeBinding(writer, binding));
    return writer.toByteArray();
  }

  static byte[] canonicalBinding(CoreBinding binding) {
    CanonicalWriter writer = new CanonicalWriter();
    writeBinding(writer, binding);
    return writer.toByteArray();
  }

  private static void writeBinding(CanonicalWriter writer, CoreBinding binding) {
    writer.writeString(binding.packageName()).writeBoolean(binding.ownerName().isPresent());
    binding.ownerName().ifPresent(writer::writeString);
    writer
        .writeString(binding.name())
        .writeTag(binding.kind().name())
        .writeTag(binding.visibility().name())
        .writeBoolean(binding.exported());
    switch (binding.shape()) {
      case CoreBindingShape.Callable callable -> {
        writer.writeInt(callable.typeParameterCount()).writeInt(callable.parameters().size());
        callable
            .parameters()
            .forEach(
                parameter -> {
                  writer.writeString(parameter.label());
                  CoreCodec.writeType(writer, parameter.type());
                });
        CoreCodec.writeType(writer, callable.returnType());
      }
      case CoreBindingShape.Class declared -> {
        writer.writeInt(declared.typeParameterCount()).writeInt(declared.fields().size());
        declared
            .fields()
            .forEach(
                field -> {
                  writer.writeString(field.name()).writeTag(field.visibility().name());
                  CoreCodec.writeType(writer, field.type());
                });
      }
      case CoreBindingShape.Enum declared -> {
        writer.writeInt(declared.members().size());
        declared.members().forEach(writer::writeString);
      }
    }
  }
}
