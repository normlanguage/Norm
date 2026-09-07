package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import java.util.Map;

public record LinkedJarBinding(
    Map<String, JavaBindingCallable> calls,
    Map<JarBindingClassReference.Nominal, String> classDescriptors,
    Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants) {
  public LinkedJarBinding {
    calls = Map.copyOf(calls);
    classDescriptors = Map.copyOf(classDescriptors);
    enumConstants =
        enumConstants.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
  }

  public static LinkedJarBinding from(ResolvedJarBinding binding) {
    GeneratedJarBinding generated = binding.generated();
    return new LinkedJarBinding(
        generated.calls(), generated.classDescriptors(), generated.enumConstants());
  }

  public static Map<String, JavaBindingCallable> linkCalls(
      java.util.List<LinkedJarBinding> bindings) {
    var calls = new java.util.LinkedHashMap<String, JavaBindingCallable>();
    for (var binding : bindings) {
      binding
          .calls()
          .forEach(
              (name, callable) -> {
                if (calls.putIfAbsent(name, callable) != null)
                  throw new JarBindingRuntimeException("duplicate JAR binding call " + name);
              });
    }
    return Map.copyOf(calls);
  }

  public LinkedJarBinding retainCalls(java.util.Set<String> required) {
    return new LinkedJarBinding(
        calls.entrySet().stream()
            .filter(entry -> required.contains(entry.getKey()))
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, Map.Entry::getValue)),
        classDescriptors,
        enumConstants);
  }
}
