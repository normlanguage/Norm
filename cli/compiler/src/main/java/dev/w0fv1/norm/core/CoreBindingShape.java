package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.ParameterPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface CoreBindingShape {
  default List<Parameter> parameters() {
    return switch (this) {
      case Callable callable -> callable.parameters();
      case MethodSignature method -> method.parameters();
      case Aggregate aggregate ->
          aggregate.constructors().stream()
              .flatMap(constructor -> constructor.parameters().stream())
              .toList();
      case Enum enumeration ->
          enumeration.variants().stream().flatMap(variant -> variant.fields().stream()).toList();
      case Interface ignored -> List.of();
    };
  }

  default CoreBindingShape mapLinks(
      java.util.function.Function<CoreDefinitionLink, CoreDefinitionLink> types,
      java.util.function.UnaryOperator<CoreDefaultArgument> defaults) {
    return switch (this) {
      case Callable callable ->
          new Callable(
              callable.kind(),
              mapTypeParameters(callable.typeParameters(), types),
              callable.parameters().stream()
                  .map(parameter -> parameter.mapLinks(types, defaults))
                  .toList(),
              CoreTypes.mapLinks(callable.returnType(), types));
      case MethodSignature method ->
          new MethodSignature(
              mapTypeParameters(method.typeParameters(), types),
              method.parameters().stream()
                  .map(parameter -> parameter.mapLinks(types, defaults))
                  .toList(),
              CoreTypes.mapLinks(method.returnType(), types));
      case Aggregate aggregate ->
          new Aggregate(
              aggregate.kind(),
              aggregate.valueCategory(),
              mapTypeParameters(aggregate.typeParameters(), types),
              aggregate.parentType().map(type -> CoreTypes.mapLinks(type, types)),
              aggregate.fields().stream()
                  .map(
                      field ->
                          new Field(
                              field.name(),
                              field.visibility(),
                              CoreTypes.mapLinks(field.type(), types)))
                  .toList(),
              aggregate.constructors().stream()
                  .map(
                      constructor ->
                          new Constructor(
                              constructor.parameters().stream()
                                  .map(parameter -> parameter.mapLinks(types, defaults))
                                  .toList()))
                  .toList(),
              aggregate.conformances().stream()
                  .map(type -> CoreTypes.mapLinks(type, types))
                  .toList());
      case Enum enumeration ->
          new Enum(
              mapTypeParameters(enumeration.typeParameters(), types),
              enumeration.variants().stream()
                  .map(
                      variant ->
                          new Variant(
                              variant.name(),
                              variant.fields().stream()
                                  .map(parameter -> parameter.mapLinks(types, defaults))
                                  .toList()))
                  .toList());
      case Interface contract ->
          new Interface(
              mapTypeParameters(contract.typeParameters(), types),
              contract.directParents().stream()
                  .map(type -> CoreTypes.mapLinks(type, types))
                  .toList());
    };
  }

  private static List<CoreTypeParameter> mapTypeParameters(
      List<CoreTypeParameter> parameters,
      java.util.function.Function<CoreDefinitionLink, CoreDefinitionLink> types) {
    return parameters.stream()
        .map(
            parameter ->
                new CoreTypeParameter(
                    parameter.index(),
                    parameter.upperBound().map(type -> CoreTypes.mapLinks(type, types)),
                    parameter.defaultType().map(type -> CoreTypes.mapLinks(type, types))))
        .toList();
  }

  record Callable(
      CoreCallableBindingKind kind,
      List<CoreTypeParameter> typeParameters,
      List<Parameter> parameters,
      CoreType returnType)
      implements CoreBindingShape {
    public Callable {
      Objects.requireNonNull(kind, "kind");
      typeParameters = requireDenseTypeParameters(typeParameters);
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(returnType, "returnType");
    }

    public Callable(
        List<CoreTypeParameter> typeParameters, List<Parameter> parameters, CoreType returnType) {
      this(CoreCallableBindingKind.FUNCTION, typeParameters, parameters, returnType);
    }
  }

  record Aggregate(
      CoreAggregateKind kind,
      CoreValueCategory valueCategory,
      List<CoreTypeParameter> typeParameters,
      Optional<CoreType> parentType,
      List<Field> fields,
      List<Constructor> constructors,
      List<CoreType> conformances)
      implements CoreBindingShape {
    public Aggregate {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(valueCategory, "valueCategory");
      if (valueCategory != CoreValueCategory.IDENTITY && valueCategory != CoreValueCategory.VALUE) {
        throw new IllegalArgumentException("aggregate binding must be identity or value");
      }
      typeParameters = requireTypeParameters(typeParameters, 0);
      parentType = Objects.requireNonNull(parentType, "parentType");
      fields = List.copyOf(fields);
      constructors = List.copyOf(constructors);
      if (constructors.isEmpty())
        throw new IllegalArgumentException("aggregate binding requires a constructor");
      conformances = List.copyOf(conformances);
    }

    public Aggregate(
        CoreValueCategory valueCategory,
        List<CoreTypeParameter> typeParameters,
        List<Field> fields,
        List<CoreType> conformances) {
      this(
          valueCategory == CoreValueCategory.VALUE
              ? CoreAggregateKind.VALUE
              : CoreAggregateKind.CLASS,
          valueCategory,
          typeParameters,
          Optional.empty(),
          fields,
          List.of(
              new Constructor(
                  fields.stream()
                      .map(field -> new Parameter(field.name(), field.type()))
                      .toList())),
          conformances);
    }
  }

  record Constructor(List<Parameter> parameters) {
    public Constructor {
      parameters = List.copyOf(parameters);
    }
  }

  record Enum(List<CoreTypeParameter> typeParameters, List<Variant> variants)
      implements CoreBindingShape {
    public Enum {
      typeParameters = requireTypeParameters(typeParameters, 0);
      variants = variants.stream().sorted(java.util.Comparator.comparing(Variant::name)).toList();
    }
  }

  record Interface(List<CoreTypeParameter> typeParameters, List<CoreType> directParents)
      implements CoreBindingShape {
    public Interface {
      typeParameters = requireTypeParameters(typeParameters, 0);
      directParents = List.copyOf(directParents);
    }
  }

  record MethodSignature(
      List<CoreTypeParameter> typeParameters, List<Parameter> parameters, CoreType returnType)
      implements CoreBindingShape {
    public MethodSignature {
      typeParameters = requireDenseTypeParameters(typeParameters);
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(returnType, "returnType");
    }
  }

  record Variant(String name, List<Parameter> fields) {
    public Variant {
      Objects.requireNonNull(name, "name");
      if (name.isBlank()) throw new IllegalArgumentException("variant name must not be blank");
      fields = List.copyOf(fields);
    }
  }

  record Parameter(
      String label,
      CoreType type,
      ParameterPolicy policy,
      Optional<CoreDefaultArgument> defaultValue) {
    public Parameter {
      Objects.requireNonNull(label, "label");
      if (label.isBlank()) throw new IllegalArgumentException("parameter label must not be blank");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(policy, "policy");
      defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
      if (policy.hasDefault() != defaultValue.isPresent()) {
        throw new IllegalArgumentException(
            "default implementation must match the parameter policy");
      }
      if (!policy.callbackParameterNames().isEmpty()
          && (!(type instanceof CoreType.Function function)
              || function.parameterTypes().size() != policy.callbackParameterNames().size()))
        throw new IllegalArgumentException(
            "callback parameter names must match the function signature");
    }

    public Parameter(String label, CoreType type) {
      this(label, type, ParameterPolicy.REQUIRED, Optional.empty());
    }

    Parameter mapLinks(
        java.util.function.Function<CoreDefinitionLink, CoreDefinitionLink> types,
        java.util.function.UnaryOperator<CoreDefaultArgument> defaults) {
      return new Parameter(
          label, CoreTypes.mapLinks(type, types), policy, defaultValue.map(defaults));
    }
  }

  record Field(String name, CoreVisibility visibility, CoreType type) {
    public Field {
      Objects.requireNonNull(name, "name");
      if (name.isBlank()) throw new IllegalArgumentException("field name must not be blank");
      Objects.requireNonNull(visibility, "visibility");
      Objects.requireNonNull(type, "type");
    }
  }

  private static List<CoreTypeParameter> requireTypeParameters(
      List<CoreTypeParameter> parameters, int firstIndex) {
    List<CoreTypeParameter> result = List.copyOf(parameters);
    for (int offset = 0; offset < result.size(); offset++) {
      if (result.get(offset).index() != firstIndex + offset) {
        throw new IllegalArgumentException("core type parameters must be dense and ordered");
      }
    }
    return result;
  }

  private static List<CoreTypeParameter> requireDenseTypeParameters(
      List<CoreTypeParameter> parameters) {
    List<CoreTypeParameter> result = List.copyOf(parameters);
    if (result.isEmpty()) return result;
    return requireTypeParameters(result, result.getFirst().index());
  }
}
