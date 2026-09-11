package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

record JavaStubPlan(List<Type> types) {
  JavaStubPlan {
    types = List.copyOf(types);
    if (types.stream().map(Type::binaryName).distinct().count() != types.size()) {
      throw new IllegalArgumentException("Java stub binary names must be unique");
    }
  }

  enum TypeKind {
    CLASS,
    VALUE,
    INTERFACE,
    ENUM,
    ANNOTATION
  }

  enum CallableKind {
    METHOD,
    DEFAULT_METHOD,
    ABSTRACT_METHOD,
    INTERFACE_METHOD,
    CONSTRUCTOR
  }

  record Type(
      String binaryName,
      String packageName,
      String name,
      TypeKind kind,
      boolean abstractType,
      List<TypeParameter> typeParameters,
      Optional<String> parentType,
      List<String> interfaces,
      List<String> enumConstants,
      List<Annotation> annotations,
      List<Field> fields,
      List<Callable> callables,
      boolean superCallConstructor,
      boolean generatedParent,
      Optional<String> allocationDefinitionLiteral) {
    Type {
      Objects.requireNonNull(binaryName, "binaryName");
      Objects.requireNonNull(packageName, "packageName");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(kind, "kind");
      typeParameters = List.copyOf(typeParameters);
      Objects.requireNonNull(parentType, "parentType");
      interfaces = List.copyOf(interfaces);
      enumConstants = List.copyOf(enumConstants);
      annotations = List.copyOf(annotations);
      fields = List.copyOf(fields);
      callables = List.copyOf(callables);
      Objects.requireNonNull(allocationDefinitionLiteral, "allocationDefinitionLiteral");
    }
  }

  record TypeParameter(String name, Optional<String> upperBound) {
    TypeParameter {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(upperBound, "upperBound");
    }
  }

  record Field(String name, String type, List<Annotation> annotations) {
    Field {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      annotations = List.copyOf(annotations);
    }
  }

  record Parameter(String name, String type, List<Annotation> annotations) {
    Parameter {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      annotations = List.copyOf(annotations);
    }
  }

  record Callable(
      String name,
      CallableKind kind,
      boolean isStatic,
      List<TypeParameter> typeParameters,
      String returnType,
      Optional<String> returnCast,
      List<Parameter> parameters,
      List<Annotation> annotations,
      String definitionLiteral,
      String implementationLiteral) {
    Callable {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(kind, "kind");
      typeParameters = List.copyOf(typeParameters);
      Objects.requireNonNull(returnType, "returnType");
      Objects.requireNonNull(returnCast, "returnCast");
      parameters = List.copyOf(parameters);
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(definitionLiteral, "definitionLiteral");
      Objects.requireNonNull(implementationLiteral, "implementationLiteral");
    }
  }

  record Annotation(String binaryName, List<AnnotationArgument> arguments) {
    Annotation {
      Objects.requireNonNull(binaryName, "binaryName");
      arguments = List.copyOf(arguments);
    }
  }

  record AnnotationArgument(Optional<String> name, String expression) {
    AnnotationArgument {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(expression, "expression");
    }
  }
}
