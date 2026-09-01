package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import java.util.List;
import java.util.Map;

public record GeneratedJarBinding(
    List<String> exports,
    List<GeneratedBindingSource> sources,
    Map<String, JavaBindingCallable> calls,
    Map<JarBindingClassReference.Nominal, String> classDescriptors,
    Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants,
    Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotations) {
  public GeneratedJarBinding {
    exports = List.copyOf(exports);
    sources = List.copyOf(sources);
    calls = Map.copyOf(calls);
    classDescriptors = Map.copyOf(classDescriptors);
    enumConstants =
        enumConstants.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    annotations = Map.copyOf(annotations);
  }
}
