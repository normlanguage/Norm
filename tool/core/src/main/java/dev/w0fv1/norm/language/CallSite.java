package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.Symbol;
import java.util.Objects;

record CallSite(Symbol callable, int activeParameter) {
  CallSite {
    Objects.requireNonNull(callable, "callable");
    int parameterCount = callable.parameters().size();
    if (activeParameter < 0
        || parameterCount == 0 && activeParameter != 0
        || parameterCount > 0 && activeParameter >= parameterCount) {
      throw new IllegalArgumentException("active parameter is outside the callable signature");
    }
  }
}
