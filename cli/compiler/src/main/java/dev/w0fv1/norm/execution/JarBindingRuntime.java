package dev.w0fv1.norm.execution;

import java.util.List;

@FunctionalInterface
public interface JarBindingRuntime {
  JarBindingResult invoke(String callId, List<Object> arguments);

  default List<JarBindingClassReference.Nominal> referenceCandidates(Object value) {
    return List.of();
  }

  default JarBindingResult referenceResult(Object value) {
    return new JarBindingResult.Reference(
        value, value.getClass().getName(), referenceCandidates(value));
  }

  static JarBindingRuntime unavailable() {
    return (callId, arguments) -> {
      throw new JarBindingRuntimeException("JAR binding runtime is unavailable");
    };
  }
}
