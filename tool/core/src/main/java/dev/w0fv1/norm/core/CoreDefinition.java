package dev.w0fv1.norm.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public sealed interface CoreDefinition
    permits CoreDefinition.Callable, CoreDefinition.Class, CoreDefinition.Enum {
  record Callable(
      Optional<CoreType> receiverType,
      List<CoreType> parameterTypes,
      List<Integer> parameterLocals,
      List<Integer> reifiedTypeLocals,
      CoreType returnType,
      List<CoreLocal> locals,
      CoreBlock body)
      implements CoreDefinition {
    public Callable {
      receiverType = Objects.requireNonNull(receiverType, "receiverType");
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
      for (int index = 0; index < locals.size(); index++) {
        if (locals.get(index).index() != index) {
          throw new IllegalArgumentException("core locals must be dense and ordered");
        }
      }
      int localCount = locals.size();
      parameterLocals.forEach(index -> requireLocal(index, localCount));
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
      int receiverTypeParameterCount = receiverTypeParameterCount(receiverType);
      if (receiverTypeParameterCount > reifiedTypeLocals.size()) {
        throw new IllegalArgumentException(
            "receiver type parameters exceed callable reified parameters");
      }
      if (receiverType.isPresent()
          && !locals.getFirst().type().equals(receiverType.orElseThrow())) {
        throw new IllegalArgumentException("receiver local does not match the receiver ABI type");
      }
      requireDistinct(parameterLocals, "parameter local");
      requireDistinct(reifiedTypeLocals, "reified type local");
      Set<Integer> occupied = new HashSet<>(parameterLocals);
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
        if (local.kind() == CoreLocal.Kind.REIFIED_TYPE
            && !reifiedTypeLocals.contains(local.index())) {
          throw new IllegalArgumentException("reified local is absent from the callable ABI");
        }
      }
    }

    public boolean hasReceiver() {
      return receiverType.isPresent();
    }

    public int receiverTypeParameterCount() {
      return receiverTypeParameterCount(receiverType);
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

    private static int receiverTypeParameterCount(Optional<CoreType> receiverType) {
      if (receiverType.isEmpty()) return 0;
      if (!(receiverType.orElseThrow() instanceof CoreType.Declared declared)
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

  record Class(CoreNominalTypeKey nominalType, int typeParameterCount, List<CoreField> fields)
      implements CoreDefinition {
    public Class {
      Objects.requireNonNull(nominalType, "nominalType");
      if (typeParameterCount < 0) {
        throw new IllegalArgumentException("type parameter count must not be negative");
      }
      fields = List.copyOf(fields);
      for (int index = 0; index < fields.size(); index++) {
        if (fields.get(index).ordinal() != index) {
          throw new IllegalArgumentException("core fields must be dense and ordered");
        }
      }
    }
  }

  record Enum(CoreNominalTypeKey nominalType, List<String> members) implements CoreDefinition {
    public Enum {
      Objects.requireNonNull(nominalType, "nominalType");
      members = List.copyOf(members);
      if (members.stream().anyMatch(String::isBlank)) {
        throw new IllegalArgumentException("enum member names must not be blank");
      }
    }
  }
}
