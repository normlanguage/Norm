package dev.w0fv1.norm.semantic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ParameterInfo(
    String name,
    SemanticType type,
    boolean hasDefault,
    List<String> callbackParameterNames,
    LabelPolicy labelPolicy,
    Optional<SemanticType> resultBuilder) {
  public ParameterInfo(
      String name,
      SemanticType type,
      boolean hasDefault,
      List<String> callbackParameterNames,
      LabelPolicy labelPolicy) {
    this(name, type, hasDefault, callbackParameterNames, labelPolicy, Optional.empty());
  }

  public enum LabelPolicy {
    NAMED,
    POSITIONAL_ONLY
  }

  public ParameterInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(labelPolicy, "labelPolicy");
    Objects.requireNonNull(resultBuilder, "resultBuilder");
    callbackParameterNames = List.copyOf(callbackParameterNames);
    if (!callbackParameterNames.isEmpty()
        && (!type.isFunction()
            || type.functionParameterTypes().size() != callbackParameterNames.size())) {
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

  public ParameterInfo substitute(java.util.Map<String, SemanticType> substitutions) {
    return new ParameterInfo(
        name,
        type.substitute(substitutions),
        hasDefault,
        callbackParameterNames,
        labelPolicy,
        resultBuilder.map(builder -> builder.substitute(substitutions)));
  }
}
