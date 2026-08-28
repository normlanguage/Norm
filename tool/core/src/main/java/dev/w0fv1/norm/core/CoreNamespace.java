package dev.w0fv1.norm.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CoreNamespace {
  private static final String DOMAIN = "norm:core:namespace:v1\0";

  private final CoreNamespaceId id;
  private final List<CoreBinding> bindings;

  private CoreNamespace(CoreNamespaceId id, List<CoreBinding> bindings) {
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
    byte[] canonical = encodeNamespace(sorted);
    return new CoreNamespace(
        new CoreNamespaceId(ContentHasher.hash(DOMAIN, CoreIdentityVersion.CURRENT, canonical)),
        sorted);
  }

  public CoreNamespaceId id() {
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

  private static byte[] encodeNamespace(List<CoreBinding> bindings) {
    CanonicalWriter writer = new CanonicalWriter().writeTag("namespace").writeInt(bindings.size());
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
        writer.writeTag(callable.kind().name());
        writeTypeParameters(writer, callable.typeParameters());
        writer.writeInt(callable.parameters().size());
        callable
            .parameters()
            .forEach(
                parameter -> {
                  writer.writeString(parameter.label());
                  CoreCodec.writeType(writer, parameter.type());
                });
        CoreCodec.writeType(writer, callable.returnType());
      }
      case CoreBindingShape.Aggregate declared -> {
        writer.writeTag(declared.kind().name());
        writer.writeTag(declared.valueCategory().name());
        writeTypeParameters(writer, declared.typeParameters());
        writer.writeBoolean(declared.parentType().isPresent());
        declared.parentType().ifPresent(type -> CoreCodec.writeType(writer, type));
        writer.writeInt(declared.fields().size());
        declared
            .fields()
            .forEach(
                field -> {
                  writer.writeString(field.name()).writeTag(field.visibility().name());
                  CoreCodec.writeType(writer, field.type());
                });
        writer.writeInt(declared.constructors().size());
        declared.constructors().stream()
            .sorted(
                (left, right) ->
                    java.util.Arrays.compareUnsigned(
                        constructorBytes(left), constructorBytes(right)))
            .forEach(
                constructor -> {
                  writer.writeInt(constructor.parameters().size());
                  constructor
                      .parameters()
                      .forEach(
                          parameter -> {
                            writer.writeString(parameter.label());
                            CoreCodec.writeType(writer, parameter.type());
                          });
                });
        writer.writeInt(declared.conformances().size());
        declared.conformances().stream()
            .sorted(
                (left, right) ->
                    java.util.Arrays.compareUnsigned(typeBytes(left), typeBytes(right)))
            .forEach(conformance -> CoreCodec.writeType(writer, conformance));
      }
      case CoreBindingShape.Enum declared -> {
        writeTypeParameters(writer, declared.typeParameters());
        writer.writeInt(declared.variants().size());
        declared
            .variants()
            .forEach(
                variant -> {
                  writer.writeString(variant.name()).writeInt(variant.fields().size());
                  variant
                      .fields()
                      .forEach(
                          field -> {
                            writer.writeString(field.label());
                            CoreCodec.writeType(writer, field.type());
                          });
                });
      }
      case CoreBindingShape.Interface declared -> {
        writeTypeParameters(writer, declared.typeParameters());
        writer.writeInt(declared.directParents().size());
        declared.directParents().stream()
            .sorted(
                (left, right) ->
                    java.util.Arrays.compareUnsigned(typeBytes(left), typeBytes(right)))
            .forEach(parent -> CoreCodec.writeType(writer, parent));
      }
      case CoreBindingShape.InterfaceMethod method -> {
        writeTypeParameters(writer, method.typeParameters());
        writer.writeInt(method.parameters().size());
        method
            .parameters()
            .forEach(
                parameter -> {
                  writer.writeString(parameter.label());
                  CoreCodec.writeType(writer, parameter.type());
                });
        CoreCodec.writeType(writer, method.returnType());
      }
    }
  }

  private static void writeTypeParameters(
      CanonicalWriter writer, List<CoreTypeParameter> parameters) {
    writer.writeInt(parameters.size());
    parameters.forEach(
        parameter -> {
          writer.writeInt(parameter.index()).writeBoolean(parameter.upperBound().isPresent());
          parameter.upperBound().ifPresent(bound -> CoreCodec.writeType(writer, bound));
        });
  }

  private static byte[] typeBytes(CoreType type) {
    CanonicalWriter writer = new CanonicalWriter();
    CoreCodec.writeType(writer, type);
    return writer.toByteArray();
  }

  private static byte[] constructorBytes(CoreBindingShape.Constructor constructor) {
    CanonicalWriter writer = new CanonicalWriter();
    writer.writeInt(constructor.parameters().size());
    constructor
        .parameters()
        .forEach(
            parameter -> {
              writer.writeString(parameter.label());
              CoreCodec.writeType(writer, parameter.type());
            });
    return writer.toByteArray();
  }
}
