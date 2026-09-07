package dev.w0fv1.norm.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TypeArguments {
  private TypeArguments() {}

  public static <P> int required(List<P> parameters, Predicate<P> hasDefault) {
    int required = 0;
    for (int index = 0; index < parameters.size(); index++) {
      if (!hasDefault.test(parameters.get(index))) required = index + 1;
    }
    return required;
  }

  public static <P> Optional<List<SemanticType>> complete(
      List<P> parameters,
      List<SemanticType> provided,
      Function<P, String> identity,
      Function<P, Optional<SemanticType>> defaultType) {
    if (provided.size() > parameters.size()) return Optional.empty();
    var substitutions = new LinkedHashMap<String, SemanticType>();
    for (int index = 0; index < provided.size(); index++) {
      substitutions.put(identity.apply(parameters.get(index)), provided.get(index));
    }
    return resolve(parameters, substitutions, identity, defaultType, ignored -> Optional.empty())
        .map(
            completed ->
                parameters.stream()
                    .map(parameter -> completed.get(identity.apply(parameter)))
                    .toList());
  }

  public static Optional<List<SemanticType>> complete(
      List<TypeParameterInfo> parameters, List<SemanticType> provided) {
    return complete(
        parameters,
        provided,
        parameter -> parameter.type().identity(),
        TypeParameterInfo::defaultType);
  }

  public static Map<String, SemanticType> completeInferred(
      List<TypeParameterInfo> parameters,
      Map<String, SemanticType> inferred,
      Function<TypeParameterInfo, SemanticType> missing) {
    return resolve(
            parameters,
            inferred,
            parameter -> parameter.type().identity(),
            TypeParameterInfo::defaultType,
            parameter -> Optional.of(missing.apply(parameter)))
        .orElseThrow();
  }

  private static <P> Optional<Map<String, SemanticType>> resolve(
      List<P> parameters,
      Map<String, SemanticType> provided,
      Function<P, String> identity,
      Function<P, Optional<SemanticType>> defaultType,
      Function<P, Optional<SemanticType>> missing) {
    var substitutions = new LinkedHashMap<>(provided);
    for (P parameter : parameters) {
      String key = identity.apply(parameter);
      if (substitutions.containsKey(key)) continue;
      Optional<SemanticType> argument =
          defaultType
              .apply(parameter)
              .map(type -> type.substitute(substitutions))
              .or(() -> missing.apply(parameter));
      if (argument.isEmpty()) return Optional.empty();
      substitutions.put(key, argument.orElseThrow());
    }
    return Optional.of(Map.copyOf(substitutions));
  }
}
