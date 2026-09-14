package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.ParameterPolicy;
import dev.w0fv1.norm.value.ParameterPolicy.LabelPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ParameterInfo(
    String name, SemanticType type, ParameterPolicy policy, Optional<SemanticType> resultBuilder) {
  public ParameterInfo(
      String name,
      SemanticType type,
      boolean hasDefault,
      List<String> callbackParameterNames,
      LabelPolicy labelPolicy,
      Optional<SemanticType> resultBuilder) {
    this(
        name,
        type,
        new ParameterPolicy(hasDefault, callbackParameterNames, labelPolicy),
        resultBuilder);
  }

  public ParameterInfo(
      String name,
      SemanticType type,
      boolean hasDefault,
      List<String> callbackParameterNames,
      LabelPolicy labelPolicy) {
    this(name, type, hasDefault, callbackParameterNames, labelPolicy, Optional.empty());
  }

  public ParameterInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(resultBuilder, "resultBuilder");
    if (!policy.callbackParameterNames().isEmpty()
        && (!type.isFunction()
            || type.functionParameterTypes().size() != policy.callbackParameterNames().size())) {
      throw new IllegalArgumentException(
          "callback parameter names must match the function signature");
    }
  }

  public ParameterInfo(
      String name, SemanticType type, boolean hasDefault, List<String> callbackParameterNames) {
    this(name, type, hasDefault, callbackParameterNames, LabelPolicy.NAMED);
  }

  public ParameterInfo(String name, SemanticType type, boolean hasDefault) {
    this(name, type, hasDefault, List.of());
  }

  public ParameterInfo(String name, SemanticType type) {
    this(name, type, false);
  }

  public static java.util.OptionalInt trailingIndex(List<ParameterInfo> parameters) {
    for (int index = parameters.size() - 1; index >= 0; index--)
      if (parameters.get(index).type().isFunction()) return java.util.OptionalInt.of(index);
    return java.util.OptionalInt.empty();
  }

  public ParameterInfo substitute(java.util.Map<String, SemanticType> substitutions) {
    return new ParameterInfo(
        name,
        type.substitute(substitutions),
        policy,
        resultBuilder.map(builder -> builder.substitute(substitutions)));
  }
}
