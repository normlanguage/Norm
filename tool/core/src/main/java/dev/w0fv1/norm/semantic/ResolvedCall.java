package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record ResolvedCall(
    Kind kind,
    SymbolId target,
    SourceSpan calleeSpan,
    ArgumentBinding arguments,
    List<ParameterInfo> parameters,
    List<SemanticType> callableTypeArguments,
    SemanticType resultType) {
  public ResolvedCall {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(calleeSpan, "calleeSpan");
    Objects.requireNonNull(arguments, "arguments");
    List<ParameterInfo> copiedParameters = List.copyOf(parameters);
    callableTypeArguments = List.copyOf(callableTypeArguments);
    Objects.requireNonNull(resultType, "resultType");
    if (arguments.parameterIndices().stream()
        .anyMatch(index -> index < 0 || index >= copiedParameters.size())) {
      throw new IllegalArgumentException("argument binding is outside the resolved parameter list");
    }
    parameters = copiedParameters;
  }

  public enum Kind {
    CALLABLE,
    CONSTRUCT,
    INTRINSIC,
    COPY
  }
}
