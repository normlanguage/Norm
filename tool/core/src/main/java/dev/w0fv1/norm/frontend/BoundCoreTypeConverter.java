package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundAggregate;
import dev.w0fv1.norm.bound.BoundBuiltinConformance;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundEnum;
import dev.w0fv1.norm.bound.BoundInterface;
import dev.w0fv1.norm.bound.BoundInterfaceMethod;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.ValueCategory;
import java.util.LinkedHashMap;
import java.util.Map;

final class BoundCoreTypeConverter {
  private final Map<String, Integer> parameters;
  private final Map<String, Integer> nominalTypes;

  private BoundCoreTypeConverter(
      Map<String, Integer> parameters, Map<String, Integer> nominalTypes) {
    this.parameters = Map.copyOf(parameters);
    this.nominalTypes = Map.copyOf(nominalTypes);
  }

  static BoundCoreTypeConverter forAggregate(
      BoundAggregate declaration, Map<String, Integer> nominalTypes) {
    Map<String, Integer> parameters = new LinkedHashMap<>();
    for (int index = 0; index < declaration.typeParameters().size(); index++) {
      parameters.put(declaration.typeParameters().get(index).type().identity(), index);
    }
    return new BoundCoreTypeConverter(parameters, nominalTypes);
  }

  static BoundCoreTypeConverter forEnum(BoundEnum declaration, Map<String, Integer> nominalTypes) {
    Map<String, Integer> parameters = new LinkedHashMap<>();
    for (int index = 0; index < declaration.typeParameters().size(); index++) {
      parameters.put(declaration.typeParameters().get(index).type().identity(), index);
    }
    return new BoundCoreTypeConverter(parameters, nominalTypes);
  }

  static BoundCoreTypeConverter forInterface(
      BoundInterface declaration, Map<String, Integer> nominalTypes) {
    Map<String, Integer> parameters = new LinkedHashMap<>();
    for (int index = 0; index < declaration.typeParameters().size(); index++) {
      parameters.put(declaration.typeParameters().get(index).type().identity(), index);
    }
    return new BoundCoreTypeConverter(parameters, nominalTypes);
  }

  static BoundCoreTypeConverter forInterfaceMethod(
      BoundInterface owner, BoundInterfaceMethod declaration, Map<String, Integer> nominalTypes) {
    Map<String, Integer> parameters = new LinkedHashMap<>();
    int index = 0;
    for (var parameter : owner.typeParameters()) {
      parameters.put(parameter.type().identity(), index++);
    }
    for (var parameter : declaration.typeParameters()) {
      parameters.put(parameter.type().identity(), index++);
    }
    return new BoundCoreTypeConverter(parameters, nominalTypes);
  }

  static BoundCoreTypeConverter forCallable(
      BoundCallable declaration, Map<String, Integer> nominalTypes) {
    Map<String, Integer> parameters = new LinkedHashMap<>();
    for (int index = 0; index < declaration.reifiedParameters().size(); index++) {
      parameters.put(declaration.reifiedParameters().get(index).typeParameterIdentity(), index);
    }
    return new BoundCoreTypeConverter(parameters, nominalTypes);
  }

  static BoundCoreTypeConverter forBuiltinConformance(
      BoundBuiltinConformance declaration, Map<String, Integer> nominalTypes) {
    Map<String, Integer> parameters = new LinkedHashMap<>();
    for (int index = 0; index < declaration.typeParameters().size(); index++) {
      parameters.put(declaration.typeParameters().get(index).type().identity(), index);
    }
    return new BoundCoreTypeConverter(parameters, nominalTypes);
  }

  CoreType convert(SemanticType type) {
    return switch (type.kind()) {
      case TYPE_PARAMETER ->
          new CoreType.Parameter(parameterIndex(type.identity()), nullability(type.nullability()));
      case DECLARED ->
          type.isFunction()
              ? new CoreType.Function(
                  convert(type.functionReturnType()),
                  type.functionParameterTypes().stream().map(this::convert).toList(),
                  nullability(type.nullability()))
              : new CoreType.Declared(
                  constructor(type),
                  type.arguments().stream().map(this::convert).toList(),
                  category(type.category()),
                  nullability(type.nullability()));
      case REFERENCE -> new CoreType.Reference(convert(type.referenceTarget()));
      case VOID -> CoreType.VOID;
      case NULL -> CoreType.NULL;
      case ERROR -> CoreType.DYNAMIC;
    };
  }

  private CoreTypeConstructor constructor(SemanticType type) {
    Integer declaration = nominalTypes.get(type.identity());
    if (declaration != null) {
      return new CoreTypeConstructor.User(new PendingDefinitionReference(declaration));
    }
    if (!type.identity().startsWith("std.core.")) {
      throw new IllegalStateException("core nominal type is absent: " + type.identity());
    }
    return new CoreTypeConstructor.Builtin(new BuiltinTypeId(type.identity()));
  }

  int parameterIndex(String identity) {
    Integer index = parameters.get(identity);
    if (index == null)
      throw new IllegalStateException("core type parameter is absent: " + identity);
    return index;
  }

  private static CoreValueCategory category(ValueCategory category) {
    return switch (category) {
      case VALUE -> CoreValueCategory.VALUE;
      case IDENTITY -> CoreValueCategory.IDENTITY;
      case POLYMORPHIC -> CoreValueCategory.POLYMORPHIC;
      case DYNAMIC -> CoreValueCategory.DYNAMIC;
      case VOID -> CoreValueCategory.VOID;
    };
  }

  private static CoreNullability nullability(SemanticType.Nullability nullability) {
    return nullability == SemanticType.Nullability.NULLABLE
        ? CoreNullability.NULLABLE
        : CoreNullability.NON_NULL;
  }
}
