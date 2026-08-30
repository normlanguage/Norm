package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.Symbol;
import java.util.List;
import java.util.Objects;

record CallSite(List<Symbol> callables, int activeSignature, int activeParameter) {
  CallSite {
    callables = List.copyOf(callables);
    if (callables.isEmpty()) throw new IllegalArgumentException("callables must not be empty");
    if (activeSignature < 0 || activeSignature >= callables.size()) {
      throw new IllegalArgumentException("active signature is outside the callable set");
    }
    int parameterCount = callables.get(activeSignature).parameters().size();
    if (activeParameter < 0
        || parameterCount == 0 && activeParameter != 0
        || parameterCount > 0 && activeParameter >= parameterCount) {
      throw new IllegalArgumentException("active parameter is outside the callable signature");
    }
  }

  Symbol callable() {
    return Objects.requireNonNull(callables.get(activeSignature));
  }
}
