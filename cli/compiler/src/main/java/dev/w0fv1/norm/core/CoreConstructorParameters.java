package dev.w0fv1.norm.core;

import java.util.LinkedHashSet;
import java.util.Set;

public record CoreConstructorParameters(int count, Set<Integer> defaulted) {
  public CoreConstructorParameters {
    if (count < 0) throw new IllegalArgumentException("parameter count must not be negative");
    defaulted = Set.copyOf(defaulted);
    if (defaulted.stream().anyMatch(index -> index < 0 || index >= count))
      throw new IllegalArgumentException(
          "constructor default parameter is outside its declaration");
  }

  public static CoreConstructorParameters forCallable(CoreDefinition.Callable callable) {
    if (!callable.hasReceiver() || !callable.captureTypes().isEmpty())
      throw new IllegalArgumentException(
          "constructor argument binding requires an uncaptured receiver callable");
    var defaulted = new LinkedHashSet<Integer>();
    new CoreWalker() {
      @Override
      protected void visitExpression(CoreExpression expression) {
        if (expression instanceof CoreExpression.ParameterProvided provided)
          defaulted.add(provided.parameterIndex());
      }
    }.walkExecution(callable);
    return new CoreConstructorParameters(callable.parameters().size(), defaulted);
  }

  public boolean acceptsProvidedPrefix(int providedCount) {
    if (providedCount < 0 || providedCount > count) return false;
    for (int index = providedCount; index < count; index++) {
      if (!defaulted.contains(index)) return false;
    }
    return true;
  }

  public boolean acceptsEmpty() {
    return acceptsProvidedPrefix(0);
  }
}
