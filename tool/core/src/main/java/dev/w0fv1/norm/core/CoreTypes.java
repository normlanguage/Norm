package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class CoreTypes {
  private CoreTypes() {}

  public static CoreType mapLinks(
      CoreType type, Function<CoreDefinitionLink, CoreDefinitionLink> mapper) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(mapper, "mapper");
    return switch (type) {
      case CoreType.Declared declared ->
          new CoreType.Declared(
              switch (declared.constructor()) {
                case CoreTypeConstructor.Builtin builtin -> builtin;
                case CoreTypeConstructor.User user ->
                    new CoreTypeConstructor.User(mapper.apply(user.definition()));
              },
              declared.arguments().stream().map(argument -> mapLinks(argument, mapper)).toList(),
              declared.category(),
              declared.nullability());
      case CoreType.Function function ->
          new CoreType.Function(
              mapLinks(function.returnType(), mapper),
              function.parameterTypes().stream().map(value -> mapLinks(value, mapper)).toList(),
              function.nullability());
      case CoreType.Parameter parameter -> parameter;
      case CoreType.Special special -> special;
    };
  }

  public static List<CoreDefinitionLink> links(CoreType type) {
    List<CoreDefinitionLink> result = new ArrayList<>();
    collect(type, result);
    return List.copyOf(result);
  }

  public static CoreType absolute(CoreType type, DefinitionId owner, CoreProgram program) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(program, "program");
    return mapLinks(
        type,
        link -> {
          if (!(link instanceof DefinitionReference reference)) {
            throw new IllegalArgumentException("canonical core type contains a pending reference");
          }
          return new DefinitionReference.External(program.resolve(owner, reference));
        });
  }

  private static void collect(CoreType type, List<CoreDefinitionLink> result) {
    if (type instanceof CoreType.Declared declared) {
      if (declared.constructor() instanceof CoreTypeConstructor.User user) {
        result.add(user.definition());
      }
      declared.arguments().forEach(argument -> collect(argument, result));
    } else if (type instanceof CoreType.Function function) {
      collect(function.returnType(), result);
      function.parameterTypes().forEach(argument -> collect(argument, result));
    }
  }
}
