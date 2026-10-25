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
    var interfaces = new java.util.ArrayList<>(type.interfaces());
    boolean ownsState =
        (type.kind() == JavaStubPlan.TypeKind.CLASS
                || type.kind() == JavaStubPlan.TypeKind.VALUE
                || type.kind() == JavaStubPlan.TypeKind.ENUM)
            && !type.generatedParent();
    if (ownsState) interfaces.add("dev.w0fv1.norm.bridge.JavaApplicationObject");
    if (!interfaces.isEmpty()) {
      source.append(type.kind() == JavaStubPlan.TypeKind.INTERFACE ? " extends " : " implements ");
      source.append(String.join(", ", interfaces));
    }
    source.append(" {\n");
    for (int index = 0; index < type.enumConstants().size(); index++) {
      source.append("  ").append(type.enumConstants().get(index));
      source.append(index + 1 == type.enumConstants().size() ? ";\n" : ",\n");
    }
    if (ownsState)
      source
          .append("  private transient Object $normState;\n")
          .append("  public Object $normState() { return $normState; }\n")
          .append("  public void $normState(Object state) { $normState = state; }\n");
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
    var constructorSignatures = new java.util.HashSet<List<String>>();
    for (var callable : type.callables()) {
      if (callable.kind() == JavaStubPlan.CallableKind.CONSTRUCTOR)
        constructorSignatures.add(
            callable.parameters().stream().map(JavaStubPlan.Parameter::type).toList());
    }
    var projectedConstructors = new java.util.HashMap<List<String>, String>();
    var projected = new java.util.ArrayList<java.util.Map.Entry<JavaStubPlan.Callable, Integer>>();
    for (var callable : type.callables()) {
      if (callable.construction().isEmpty()) continue;
      var construction = callable.construction().orElseThrow();
      for (int count = construction.count() - 1;
          construction.acceptsProvidedPrefix(count);
          count--) {
        var signature =
            callable.parameters().subList(0, count).stream()
                .map(JavaStubPlan.Parameter::type)
                .toList();
        if (constructorSignatures.contains(signature)) continue;
        var previous =
            projectedConstructors.putIfAbsent(signature, callable.implementationLiteral());
        if (previous != null && !previous.equals(callable.implementationLiteral()))
          throw new IllegalArgumentException(
              "ambiguous Java constructor omission: " + type.binaryName());
        if (previous == null) projected.add(java.util.Map.entry(callable, count));
      }
    }
    projected.sort(java.util.Comparator.comparingInt(java.util.Map.Entry::getValue));
    for (var constructor : projected)
      appendCallable(source, type, constructor.getKey(), constructor.getValue(), false);
    for (var callable : type.callables())
      appendCallable(source, type, callable, callable.parameters().size(), true);
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
      StringBuilder source,
      JavaStubPlan.Type owner,
      JavaStubPlan.Callable callable,
      int parameterCount,
      boolean indexed) {
    if (indexed) {
      appendAnnotations(source, callable.annotations(), "  ");
      source
          .append("  @dev.w0fv1.norm.bridge.NormApplicationMethod(value = ")
          .append(callable.definitionLiteral());
      if (!callable.implementationLiteral().equals(callable.definitionLiteral())) {
        source.append(", implementation = ").append(callable.implementationLiteral());
      }
      source.append(")\n");
    }
    source.append("  public ");
    if (callable.isStatic()) source.append("static ");
    if (callable.kind() == JavaStubPlan.CallableKind.ABSTRACT_METHOD) source.append("abstract ");
    if (callable.kind() == JavaStubPlan.CallableKind.DEFAULT_METHOD) source.append("default ");
    appendTypeParameters(source, callable.typeParameters());
    if (!callable.typeParameters().isEmpty()) source.append(' ');
    if (callable.kind() == JavaStubPlan.CallableKind.CONSTRUCTOR) source.append(owner.name());
    else source.append(callable.returnType()).append(' ').append(callable.name());
    source.append('(');
    for (int index = 0; index < parameterCount; index++) {
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
            callable.parameters().subList(0, parameterCount).stream()
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
