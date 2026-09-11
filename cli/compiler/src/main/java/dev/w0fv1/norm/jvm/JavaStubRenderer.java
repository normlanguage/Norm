package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class JavaStubRenderer {
  List<JavaAnnotationStub> render(JavaStubPlan plan) {
    Objects.requireNonNull(plan, "plan");
    return plan.types().stream()
        .map(type -> new JavaAnnotationStub(type.binaryName(), source(type)))
        .toList();
  }

  private static String source(JavaStubPlan.Type type) {
    var source = new StringBuilder();
    source.append("package ").append(type.packageName()).append(";\n\n");
    appendAnnotations(source, type.annotations(), "");
    source.append("public ");
    if (type.abstractType()) source.append("abstract ");
    source.append(
        switch (type.kind()) {
          case CLASS -> "class ";
          case VALUE -> "final class ";
          case INTERFACE -> "interface ";
          case ENUM -> "enum ";
          case ANNOTATION -> "@interface ";
        });
    source.append(type.name());
    appendTypeParameters(source, type.typeParameters());
    type.parentType().ifPresent(parent -> source.append(" extends ").append(parent));
    if (!type.interfaces().isEmpty()) {
      source.append(type.kind() == JavaStubPlan.TypeKind.INTERFACE ? " extends " : " implements ");
      source.append(String.join(", ", type.interfaces()));
    }
    source.append(" {\n");
    for (int index = 0; index < type.enumConstants().size(); index++) {
      source.append("  ").append(type.enumConstants().get(index));
      source.append(index + 1 == type.enumConstants().size() ? ";\n" : ",\n");
    }
    for (var field : type.fields()) {
      appendAnnotations(source, field.annotations(), "  ");
      source.append("  public ").append(field.type()).append(' ').append(field.name());
      source.append(type.kind() == JavaStubPlan.TypeKind.ANNOTATION ? "();\n" : ";\n");
    }
    if (type.superCallConstructor()) {
      source
          .append("  protected ")
          .append(type.name())
          .append("(dev.w0fv1.norm.bridge.JavaApplicationBridge.SuperCall call) {");
      if (type.generatedParent()) source.append(" super(call);");
      source.append(" }\n");
    }
    for (var callable : type.callables()) appendCallable(source, type, callable);
    type.allocationDefinitionLiteral()
        .ifPresent(
            definition -> {
              source.append("  public ").append(type.name()).append("() {");
              if (type.generatedParent()) {
                source.append(
                    " super(dev.w0fv1.norm.bridge.JavaApplicationBridge.SuperCall.INSTANCE);");
              }
              source
                  .append(" dev.w0fv1.norm.bridge.JavaApplicationBridge.allocate(")
                  .append(type.name())
                  .append(".class, this, ")
                  .append(definition)
                  .append("); }\n");
            });
    return source.append("}\n").toString();
  }

  private static void appendCallable(
      StringBuilder source, JavaStubPlan.Type owner, JavaStubPlan.Callable callable) {
    appendAnnotations(source, callable.annotations(), "  ");
    source
        .append("  @dev.w0fv1.norm.bridge.NormApplicationMethod(value = ")
        .append(callable.definitionLiteral());
    if (!callable.implementationLiteral().equals(callable.definitionLiteral())) {
      source.append(", implementation = ").append(callable.implementationLiteral());
    }
    source.append(")\n  public ");
    if (callable.isStatic()) source.append("static ");
    if (callable.kind() == JavaStubPlan.CallableKind.ABSTRACT_METHOD) source.append("abstract ");
    if (callable.kind() == JavaStubPlan.CallableKind.DEFAULT_METHOD) source.append("default ");
    appendTypeParameters(source, callable.typeParameters());
    if (!callable.typeParameters().isEmpty()) source.append(' ');
    if (callable.kind() == JavaStubPlan.CallableKind.CONSTRUCTOR) source.append(owner.name());
    else source.append(callable.returnType()).append(' ').append(callable.name());
    source.append('(');
    for (int index = 0; index < callable.parameters().size(); index++) {
      if (index > 0) source.append(", ");
      var parameter = callable.parameters().get(index);
      appendAnnotations(source, parameter.annotations(), "");
      source.append(parameter.type()).append(' ').append(parameter.name());
    }
    source.append(')');
    if (callable.kind() == JavaStubPlan.CallableKind.ABSTRACT_METHOD
        || callable.kind() == JavaStubPlan.CallableKind.INTERFACE_METHOD) {
      source.append(";\n");
      return;
    }
    source.append(" { ");
    if (callable.kind() == JavaStubPlan.CallableKind.CONSTRUCTOR) {
      if (owner.generatedParent()) {
        source.append("super(dev.w0fv1.norm.bridge.JavaApplicationBridge.SuperCall.INSTANCE); ");
      }
      source
          .append("dev.w0fv1.norm.bridge.JavaApplicationBridge.construct(")
          .append(owner.name())
          .append(".class, this, ")
          .append(callable.implementationLiteral());
    } else {
      callable.returnCast().ifPresent(type -> source.append("return (").append(type).append(") "));
      source
          .append("dev.w0fv1.norm.bridge.JavaApplicationBridge.invoke(")
          .append(owner.name())
          .append(".class, ")
          .append(callable.isStatic() ? "null" : "this")
          .append(", ")
          .append(callable.implementationLiteral());
    }
    source
        .append(", new Object[] {")
        .append(
            callable.parameters().stream()
                .map(JavaStubPlan.Parameter::name)
                .collect(Collectors.joining(", ")))
        .append("}); }\n");
  }

  private static void appendTypeParameters(
      StringBuilder source, List<JavaStubPlan.TypeParameter> parameters) {
    if (parameters.isEmpty()) return;
    source.append('<');
    for (int index = 0; index < parameters.size(); index++) {
      if (index > 0) source.append(", ");
      var parameter = parameters.get(index);
      source.append(parameter.name());
      parameter.upperBound().ifPresent(bound -> source.append(" extends ").append(bound));
    }
    source.append('>');
  }

  private static void appendAnnotations(
      StringBuilder source, List<JavaStubPlan.Annotation> annotations, String indent) {
    for (var annotation : annotations) {
      source.append(indent).append('@').append(annotation.binaryName());
      if (!annotation.arguments().isEmpty()) {
        source.append('(');
        for (int index = 0; index < annotation.arguments().size(); index++) {
          if (index > 0) source.append(", ");
          var argument = annotation.arguments().get(index);
          argument.name().ifPresent(name -> source.append(name).append(" = "));
          source.append(argument.expression());
        }
        source.append(')');
      }
      source.append('\n');
    }
  }
}
