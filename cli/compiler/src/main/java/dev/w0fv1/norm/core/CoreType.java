package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

public sealed interface CoreType
    permits CoreType.Declared,
        CoreType.Function,
        CoreType.Parameter,
        CoreType.Reference,
        CoreType.Special {
  CoreType ANY =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Any")),
          List.of(),
          CoreValueCategory.POLYMORPHIC,
          CoreNullability.NON_NULL);
  CoreType INTEGER =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Integer")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType LONG =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Long")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType FLOAT =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Float")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType DOUBLE =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Double")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType NUMBER =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Number")),
          List.of(),
          CoreValueCategory.POLYMORPHIC,
          CoreNullability.NON_NULL);
  CoreType CODE_POINT =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.CodePoint")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType BOOLEAN =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Boolean")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType STRING =
      new Declared(
          new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.String")),
          List.of(),
          CoreValueCategory.VALUE,
          CoreNullability.NON_NULL);
  CoreType VOID = new Special(SpecialKind.VOID);
  CoreType NULL = new Special(SpecialKind.NULL);
  CoreType DYNAMIC = new Special(SpecialKind.DYNAMIC);
  CoreType EXISTENTIAL = new Special(SpecialKind.EXISTENTIAL);

  default boolean isNullable() {
    return switch (this) {
      case Declared declared -> declared.nullability() == CoreNullability.NULLABLE;
      case Function function -> function.nullability() == CoreNullability.NULLABLE;
      case Parameter parameter -> parameter.nullability() == CoreNullability.NULLABLE;
      case Reference ignored -> false;
      case Special ignored -> false;
    };
  }

  default CoreType substitute(IntFunction<CoreType> substitutions) {
    Objects.requireNonNull(substitutions, "substitutions");
    return switch (this) {
      case Declared declared ->
          new Declared(
              declared.constructor(),
              declared.arguments().stream()
                  .map(argument -> argument.substitute(substitutions))
                  .toList(),
              declared.category(),
              declared.nullability());
      case Function function ->
          new Function(
              function.returnType().substitute(substitutions),
              function.parameterTypes().stream()
                  .map(parameter -> parameter.substitute(substitutions))
                  .toList(),
              function.nullability());
      case Parameter parameter -> {
        CoreType replacement =
            Objects.requireNonNull(
                substitutions.apply(parameter.index()), "type parameter substitution");
        yield parameter.nullability() == CoreNullability.NULLABLE
            ? replacement.asNullable()
            : replacement;
      }
      case Reference reference -> new Reference(reference.target().substitute(substitutions));
      case Special special -> special;
    };
  }

  default CoreType asNullable() {
    return switch (this) {
      case Declared declared ->
          declared.nullability() == CoreNullability.NULLABLE
              ? declared
              : new Declared(
                  declared.constructor(),
                  declared.arguments(),
                  declared.category(),
                  CoreNullability.NULLABLE);
      case Function function ->
          function.nullability() == CoreNullability.NULLABLE
              ? function
              : new Function(
                  function.returnType(), function.parameterTypes(), CoreNullability.NULLABLE);
      case Parameter parameter ->
          parameter.nullability() == CoreNullability.NULLABLE
              ? parameter
              : new Parameter(parameter.index(), CoreNullability.NULLABLE);
      case Reference ignored ->
          throw new IllegalStateException("reference types cannot be nullable");
      case Special special -> special;
    };
  }

  record Declared(
      CoreTypeConstructor constructor,
      List<CoreType> arguments,
      CoreValueCategory category,
      CoreNullability nullability)
      implements CoreType {
    public Declared {
      Objects.requireNonNull(constructor, "constructor");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(nullability, "nullability");
      if (category == CoreValueCategory.DYNAMIC || category == CoreValueCategory.VOID) {
        throw new IllegalArgumentException("declared core types require a concrete value category");
      }
    }
  }

  record Function(CoreType returnType, List<CoreType> parameterTypes, CoreNullability nullability)
      implements CoreType {
    public Function {
      Objects.requireNonNull(returnType, "returnType");
      parameterTypes = List.copyOf(parameterTypes);
      Objects.requireNonNull(nullability, "nullability");
    }
  }

  record Parameter(int index, CoreNullability nullability) implements CoreType {
    public Parameter {
      if (index < 0)
        throw new IllegalArgumentException("type parameter index must not be negative");
      Objects.requireNonNull(nullability, "nullability");
    }
  }

  record Reference(CoreType target) implements CoreType {
    public Reference {
      Objects.requireNonNull(target, "target");
      if (target instanceof Reference || target.isNullable()) {
        throw new IllegalArgumentException(
            "reference target must be non-reference and non-nullable");
      }
    }
  }

  record Special(SpecialKind kind) implements CoreType {
    public Special {
      Objects.requireNonNull(kind, "kind");
    }
  }

  enum SpecialKind {
    VOID,
    NULL,
    DYNAMIC,
    EXISTENTIAL
  }
}
