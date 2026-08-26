package dev.w0fv1.norm.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public sealed interface CoreDefinition
    permits CoreDefinition.Callable,
        CoreDefinition.Aggregate,
        CoreDefinition.Enum,
        CoreDefinition.Interface,
        CoreDefinition.InterfaceMethod,
        CoreDefinition.BuiltinConformance {
  record Callable(
      Optional<CoreType> receiverType,
      List<CoreTypeParameter> typeParameters,
      List<CoreType> captureTypes,
      List<Integer> captureLocals,
      List<CoreType> parameterTypes,
      List<Integer> parameterLocals,
      List<Integer> reifiedTypeLocals,
      CoreType returnType,
      List<CoreLocal> locals,
      CoreBlock body)
      implements CoreDefinition {
    public Callable {
      receiverType = Objects.requireNonNull(receiverType, "receiverType");
      int receiverTypeParameterCount =
          receiverType.map(CoreDefinition::receiverParameterCount).orElse(0);
      typeParameters = requireTypeParameters(typeParameters, receiverTypeParameterCount);
      captureTypes = List.copyOf(captureTypes);
      captureLocals = List.copyOf(captureLocals);
      parameterTypes = List.copyOf(parameterTypes);
      parameterLocals = List.copyOf(parameterLocals);
      reifiedTypeLocals = List.copyOf(reifiedTypeLocals);
      Objects.requireNonNull(returnType, "returnType");
      locals = List.copyOf(locals);
      Objects.requireNonNull(body, "body");
      if (parameterTypes.size() != parameterLocals.size()) {
        throw new IllegalArgumentException(
            "parameter types and local bindings must have equal size");
      }
      if (captureTypes.size() != captureLocals.size()) {
        throw new IllegalArgumentException("capture types and local bindings must have equal size");
      }
      for (int index = 0; index < locals.size(); index++) {
        if (locals.get(index).index() != index) {
          throw new IllegalArgumentException("core locals must be dense and ordered");
        }
      }
      int localCount = locals.size();
      parameterLocals.forEach(index -> requireLocal(index, localCount));
      captureLocals.forEach(index -> requireLocal(index, localCount));
      reifiedTypeLocals.forEach(index -> requireLocal(index, localCount));
      if (receiverType.isPresent()
          && (locals.isEmpty() || locals.getFirst().kind() != CoreLocal.Kind.RECEIVER)) {
        throw new IllegalArgumentException("methods require receiver local zero");
      }
      if (receiverType.isEmpty()
          && locals.stream().anyMatch(local -> local.kind() == CoreLocal.Kind.RECEIVER)) {
        throw new IllegalArgumentException("functions cannot declare a receiver local");
      }
      long receiverLocals =
          locals.stream().filter(local -> local.kind() == CoreLocal.Kind.RECEIVER).count();
      if (receiverLocals > 1) {
        throw new IllegalArgumentException("callables cannot declare multiple receiver locals");
      }
      if (receiverTypeParameterCount + typeParameters.size() != reifiedTypeLocals.size()) {
        throw new IllegalArgumentException(
            "callable type parameters do not match reified parameters");
      }
      if (receiverType.isPresent()
          && !locals.getFirst().type().equals(receiverType.orElseThrow())) {
        throw new IllegalArgumentException("receiver local does not match the receiver ABI type");
      }
      requireDistinct(parameterLocals, "parameter local");
      requireDistinct(captureLocals, "capture local");
      requireDistinct(reifiedTypeLocals, "reified type local");
      Set<Integer> occupied = new HashSet<>(parameterLocals);
      if (captureLocals.stream().anyMatch(index -> !occupied.add(index))) {
        throw new IllegalArgumentException("capture and parameter local bindings overlap");
      }
      if (reifiedTypeLocals.stream().anyMatch(index -> !occupied.add(index))) {
        throw new IllegalArgumentException("parameter and reified local bindings overlap");
      }
      for (int index = 0; index < parameterLocals.size(); index++) {
        CoreLocal local = locals.get(parameterLocals.get(index));
        if (local.kind() != CoreLocal.Kind.PARAMETER
            || !local.type().equals(parameterTypes.get(index))) {
          throw new IllegalArgumentException("parameter local does not match its ABI type");
        }
      }
      for (int index = 0; index < captureLocals.size(); index++) {
        CoreLocal local = locals.get(captureLocals.get(index));
        if (local.kind() != CoreLocal.Kind.CAPTURE
            || !local.type().equals(captureTypes.get(index))) {
          throw new IllegalArgumentException("capture local does not match its ABI type");
        }
      }
      for (int index : reifiedTypeLocals) {
        CoreLocal local = locals.get(index);
        if (local.kind() != CoreLocal.Kind.REIFIED_TYPE || !local.type().equals(CoreType.DYNAMIC)) {
          throw new IllegalArgumentException("reified type binding must reference a reified local");
        }
      }
      for (CoreLocal local : locals) {
        if (local.kind() == CoreLocal.Kind.PARAMETER && !parameterLocals.contains(local.index())) {
          throw new IllegalArgumentException("parameter local is absent from the callable ABI");
        }
        if (local.kind() == CoreLocal.Kind.CAPTURE && !captureLocals.contains(local.index())) {
          throw new IllegalArgumentException("capture local is absent from the callable ABI");
        }
        if (local.kind() == CoreLocal.Kind.REIFIED_TYPE
            && !reifiedTypeLocals.contains(local.index())) {
          throw new IllegalArgumentException("reified local is absent from the callable ABI");
        }
      }
    }

    public Callable(
        Optional<CoreType> receiverType,
        List<CoreTypeParameter> typeParameters,
        List<CoreType> parameterTypes,
        List<Integer> parameterLocals,
        List<Integer> reifiedTypeLocals,
        CoreType returnType,
        List<CoreLocal> locals,
        CoreBlock body) {
      this(
          receiverType,
          typeParameters,
          List.of(),
          List.of(),
          parameterTypes,
          parameterLocals,
          reifiedTypeLocals,
          returnType,
          locals,
          body);
    }

    public boolean hasReceiver() {
      return receiverType.isPresent();
    }

    public int receiverTypeParameterCount() {
      return receiverType.map(CoreDefinition::receiverParameterCount).orElse(0);
    }

    private static void requireLocal(int index, int size) {
      if (index < 0 || index >= size) {
        throw new IllegalArgumentException("callable local binding is outside its local table");
      }
    }

    private static void requireDistinct(List<Integer> indices, String name) {
      if (new HashSet<>(indices).size() != indices.size()) {
        throw new IllegalArgumentException(name + " bindings must be unique");
      }
    }
  }

  record Aggregate(
      CoreNominalTypeKey nominalType,
      CoreValueCategory valueCategory,
      List<CoreTypeParameter> typeParameters,
      Optional<CoreType> parentType,
      int fieldCount,
      List<CoreField> fields,
      List<CoreMethodDispatch> dispatch,
      CoreDefinitionLink constructor,
      List<CoreConformance> conformances)
      implements CoreDefinition {
    public Aggregate {
      Objects.requireNonNull(nominalType, "nominalType");
      Objects.requireNonNull(valueCategory, "valueCategory");
      if (valueCategory != CoreValueCategory.IDENTITY && valueCategory != CoreValueCategory.VALUE) {
        throw new IllegalArgumentException("core aggregate must be identity or value");
      }
      typeParameters = requireTypeParameters(typeParameters, 0);
      parentType = Objects.requireNonNull(parentType, "parentType");
      if (fieldCount < fields.size()) throw new IllegalArgumentException("field count is invalid");
      fields = List.copyOf(fields);
      dispatch = List.copyOf(dispatch);
      Objects.requireNonNull(constructor, "constructor");
      conformances = List.copyOf(conformances);
      int firstOrdinal = fieldCount - fields.size();
      for (int index = 0; index < fields.size(); index++) {
        if (fields.get(index).ordinal() != firstOrdinal + index) {
          throw new IllegalArgumentException(
              "core fields must be dense and ordered after inherited fields");
        }
      }
    }
  }

  record Interface(
      CoreNominalTypeKey nominalType,
      List<CoreTypeParameter> typeParameters,
      List<CoreType> directParents,
      List<CoreDefinitionLink> declaredMethods)
      implements CoreDefinition {
    public Interface {
      Objects.requireNonNull(nominalType, "nominalType");
      typeParameters = requireTypeParameters(typeParameters, 0);
      directParents = List.copyOf(directParents);
      declaredMethods = List.copyOf(declaredMethods);
    }
  }

  record InterfaceMethod(
      String name,
      CoreType receiverInterfaceType,
      List<CoreTypeParameter> typeParameters,
      List<CoreType> parameterTypes,
      CoreType returnType)
      implements CoreDefinition {
    public InterfaceMethod {
      Objects.requireNonNull(name, "name");
      if (name.isBlank())
        throw new IllegalArgumentException("interface method name must not be blank");
      Objects.requireNonNull(receiverInterfaceType, "receiverInterfaceType");
      int receiverParameters = receiverParameterCount(receiverInterfaceType);
      typeParameters = requireTypeParameters(typeParameters, receiverParameters);
      parameterTypes = List.copyOf(parameterTypes);
      Objects.requireNonNull(returnType, "returnType");
    }
  }

  record BuiltinConformance(
      List<CoreTypeParameter> typeParameters,
      CoreType concreteBuiltinType,
      CoreType interfaceType,
      List<CoreWitness> witnesses)
      implements CoreDefinition {
    public BuiltinConformance {
      typeParameters = requireTypeParameters(typeParameters, 0);
      Objects.requireNonNull(concreteBuiltinType, "concreteBuiltinType");
      Objects.requireNonNull(interfaceType, "interfaceType");
      witnesses = List.copyOf(witnesses);
    }
  }

  record Enum(
      CoreNominalTypeKey nominalType,
      List<CoreTypeParameter> typeParameters,
      List<CoreEnumVariant> variants)
      implements CoreDefinition {
    public Enum {
      Objects.requireNonNull(nominalType, "nominalType");
      typeParameters = requireTypeParameters(typeParameters, 0);
      variants =
          variants.stream().sorted(java.util.Comparator.comparing(CoreEnumVariant::key)).toList();
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

  private static int receiverParameterCount(CoreType receiverType) {
    if (!(receiverType instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User)) {
      throw new IllegalArgumentException("method receiver must be a user-declared type");
    }
    for (int index = 0; index < declared.arguments().size(); index++) {
      if (!(declared.arguments().get(index) instanceof CoreType.Parameter parameter)
          || parameter.index() != index
          || parameter.nullability() != CoreNullability.NON_NULL) {
        throw new IllegalArgumentException(
            "receiver type arguments must map directly to leading type parameters");
      }
    }
    return declared.arguments().size();
  }
}
