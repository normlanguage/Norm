package dev.w0fv1.norm.jvm;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JavaCallbackTypes {
  private JavaCallbackTypes() {}

  public static Set<String> from(List<LinkedJarBinding> bindings) {
    Set<String> callbacks = new LinkedHashSet<>();
    for (LinkedJarBinding binding : bindings) {
      for (JavaBindingCallable call : binding.calls().values()) {
        call.parameters().forEach(type -> collect(type, callbacks));
        collect(call.returnType(), callbacks);
      }
    }
    return java.util.Collections.unmodifiableSet(callbacks);
  }

  private static void collect(JavaBindingType type, Set<String> callbacks) {
    if (type instanceof JavaCallbackType callback) callbacks.add(callback.binaryName());
    if (type instanceof JavaBindingTypeVariable variable) collect(variable.erasure(), callbacks);
    if (type instanceof JavaArrayType array) collect(array.component(), callbacks);
  }
}
