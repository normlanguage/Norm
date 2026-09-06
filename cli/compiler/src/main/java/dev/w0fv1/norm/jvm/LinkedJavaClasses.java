package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LinkedJavaClasses(
    Map<JarBindingClassReference, Class<?>> classes,
    Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants) {
  public LinkedJavaClasses {
    classes = Map.copyOf(classes);
    enumConstants =
        enumConstants.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
  }

  public static LinkedJavaClasses resolve(List<LinkedJarBinding> bindings, ClassLoader loader) {
    Map<JarBindingClassReference, Class<?>> classes = new LinkedHashMap<>();
    for (var entry : JavaPlatformTypes.classDescriptors().entrySet()) {
      try {
        classes.put(entry.getKey(), descriptorClass(entry.getValue(), loader));
      } catch (TypeNotPresentException ignored) {
        continue;
      }
    }
    Map<JarBindingClassReference.Nominal, Map<String, String>> enums = new LinkedHashMap<>();
    for (var binding : bindings) {
      binding
          .classDescriptors()
          .forEach(
              (reference, descriptor) -> {
                if (classes.putIfAbsent(reference, descriptorClass(descriptor, loader)) != null)
                  throw new JarBindingRuntimeException("duplicate Norm class mapping " + reference);
              });
      binding
          .enumConstants()
          .forEach(
              (reference, constants) -> {
                if (enums.putIfAbsent(reference, constants) != null)
                  throw new JarBindingRuntimeException("duplicate Norm enum mapping " + reference);
              });
    }
    return new LinkedJavaClasses(classes, enums);
  }

  private static Class<?> descriptorClass(String descriptor, ClassLoader loader) {
    try {
      return MethodType.fromMethodDescriptorString("()" + descriptor, loader).returnType();
    } catch (IllegalArgumentException exception) {
      throw new JarBindingRuntimeException(
          "invalid Java class descriptor " + descriptor, exception);
    }
  }
}
