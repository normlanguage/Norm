package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record BindingPlan(
    ModuleCoordinate module,
    List<String> exports,
    List<Declaration> declarations,
    List<Array> arrays,
    BindingTypeNames types,
    Map<String, JavaBindingCallable> calls,
    Map<JarBindingClassReference.Nominal, String> classDescriptors,
    Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants,
    Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotations) {
  public BindingPlan {
    Objects.requireNonNull(module);
    exports = List.copyOf(exports);
    declarations = List.copyOf(declarations);
    arrays = List.copyOf(arrays);
    Objects.requireNonNull(types);
    calls = Map.copyOf(calls);
    classDescriptors = Map.copyOf(classDescriptors);
    enumConstants =
        enumConstants.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    annotations = Map.copyOf(annotations);
  }

  public record Call(
      String name,
      String id,
      JavaBindingCallable callable,
      List<JavaBindingTypeParameter> typeParameters) {
    public Call {
      Objects.requireNonNull(name);
      Objects.requireNonNull(id);
      Objects.requireNonNull(callable);
      typeParameters = List.copyOf(typeParameters);
    }
  }

  public record Declaration(
      String exportedName,
      String binaryName,
      JavaApiTypeKind kind,
      List<JavaBindingTypeParameter> typeParameters,
      List<JavaBindingCallable> bindings,
      List<JavaReferenceType> interfaces,
      boolean resource,
      Map<String, String> enumVariants,
      Optional<JavaAnnotationBinding> annotation,
      Map<JavaAnnotationElementBinding, String> annotationNames,
      List<Call> functions,
      List<Call> members) {
    public Declaration {
      Objects.requireNonNull(exportedName);
      Objects.requireNonNull(binaryName);
      Objects.requireNonNull(kind);
      typeParameters = List.copyOf(typeParameters);
      bindings = List.copyOf(bindings);
      interfaces = List.copyOf(interfaces);
      enumVariants =
          java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(enumVariants));
      Objects.requireNonNull(annotation);
      annotationNames = Map.copyOf(annotationNames);
      functions = List.copyOf(functions);
      members = List.copyOf(members);
    }
  }

  public record Array(
      String name, JavaArrayType type, Call constructor, Call length, Call get, Call set) {
    public Array {
      Objects.requireNonNull(name);
      Objects.requireNonNull(type);
      Objects.requireNonNull(constructor);
      Objects.requireNonNull(length);
      Objects.requireNonNull(get);
      Objects.requireNonNull(set);
    }
  }
}
