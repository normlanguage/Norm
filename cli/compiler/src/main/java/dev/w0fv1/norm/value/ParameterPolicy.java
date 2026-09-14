package dev.w0fv1.norm.value;

import java.util.List;
import java.util.Objects;

public record ParameterPolicy(
    boolean hasDefault, List<String> callbackParameterNames, LabelPolicy labelPolicy) {
  public enum LabelPolicy {
    NAMED,
    POSITIONAL_ONLY
  }

  public static final ParameterPolicy REQUIRED =
      new ParameterPolicy(false, List.of(), LabelPolicy.NAMED);

  public ParameterPolicy {
    callbackParameterNames = List.copyOf(callbackParameterNames);
    Objects.requireNonNull(labelPolicy, "labelPolicy");
  }
}
