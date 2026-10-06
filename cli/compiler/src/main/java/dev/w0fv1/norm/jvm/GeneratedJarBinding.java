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

  public Map<String, JarBindingClassReference.Nominal> exportedClasses() {
    Map<String, JarBindingClassReference.Nominal> result = new java.util.LinkedHashMap<>();
    classDescriptors.forEach(
        (reference, descriptor) -> {
          String path =
              (reference.packageName() + "." + reference.name())
                  .substring(reference.module().name().length() + 1);
          if (exports.contains(path) && descriptor.startsWith("L")) {
            result.put(
                descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), reference);
          }
        });
    return Map.copyOf(result);
  }
}
