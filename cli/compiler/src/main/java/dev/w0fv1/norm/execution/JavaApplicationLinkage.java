package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import java.util.Map;
import java.util.Set;

public record JavaApplicationLinkage(Map<String, JavaDirectCall> calls, Set<String> types) {
  public static final JavaApplicationLinkage EMPTY = new JavaApplicationLinkage(Map.of(), Set.of());

  public JavaApplicationLinkage {
    calls = Map.copyOf(calls);
    types = Set.copyOf(types);
  }
}
