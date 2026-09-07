package dev.w0fv1.norm.jvm;

import static dev.w0fv1.norm.jvm.BindingNames.exportPackage;
import static dev.w0fv1.norm.jvm.BindingNames.lowerCamel;
import static dev.w0fv1.norm.jvm.BindingNames.simpleName;
import static dev.w0fv1.norm.jvm.BindingTypeNames.collectArrays;
import static dev.w0fv1.norm.jvm.BindingTypeNames.collectReferences;
import static dev.w0fv1.norm.jvm.BindingTypeNames.containsException;
import static dev.w0fv1.norm.jvm.BindingTypeNames.containsReferenceKind;
import static dev.w0fv1.norm.jvm.BindingTypeNames.genericTypeDeclaration;
import static dev.w0fv1.norm.jvm.BindingTypeNames.normBoundType;
import static dev.w0fv1.norm.jvm.BindingTypeNames.normRelationType;
import static dev.w0fv1.norm.jvm.BindingTypeNames.normReturnType;
import static dev.w0fv1.norm.jvm.BindingTypeNames.normType;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.bounds;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.requiredProtocolBinding;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BindingSourceRenderer {
  public GeneratedJarBinding render(BindingPlan plan) {
    List<GeneratedBindingSource> sources = new ArrayList<>();
    for (BindingPlan.Declaration declaration : plan.declarations())
      sources.add(generateSource(plan.module(), declaration, plan.types()));
    if (!plan.arrays().isEmpty())
      sources.add(generateArraySource(plan.module(), plan.arrays(), plan.types()));
    return new GeneratedJarBinding(
        plan.exports(),
        sources,
        plan.calls(),
        plan.classDescriptors(),
        plan.enumConstants(),
        plan.annotations());
  }

  private static GeneratedBindingSource generateArraySource(
      ModuleCoordinate module, List<BindingPlan.Array> plannedArrays, BindingTypeNames normTypes) {
    Set<JavaArrayType> arrays = normTypes.arrays().keySet();
    StringBuilder text = new StringBuilder("package ").append(module.name()).append('\n');
    boolean comparableArrays =
        arrays.stream()
            .map(JavaArrayType::component)
            .filter(JavaBindingTypeVariable.class::isInstance)
            .map(JavaBindingTypeVariable.class::cast)
            .map(JavaBindingTypeVariable::erasure)
            .anyMatch(JavaGenericParameterProjector::isComparable);
    boolean exceptionArrays =
        arrays.stream().map(JavaArrayType::component).anyMatch(BindingTypeNames::containsException);
    if (comparableArrays) {
      text.append("import std.core.Comparable\n");
    }
    if (exceptionArrays) text.append("import std.core.Exception\n");
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.UNIT))) {
      text.append("import std.core.Unit\n");
    }
    if (arrays.stream().anyMatch(BindingTypeNames::containsPath)) {
      text.append("import std.filesystem.Path\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.URI))) {
      text.append("import std.http.Uri\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.DURATION))) {
      text.append("import std.time.Duration\n");
    }
    if (arrays.stream()
        .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.INPUT_STREAM))) {
      text.append("import std.io.InputStream\n");
    }
    if (arrays.stream()
        .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.OUTPUT_STREAM))) {
      text.append("import std.io.OutputStream\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.TASK))) {
      text.append("import std.concurrent.Task\n");
    }
    if (arrays.stream()
        .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.PUBLISHER))) {
      text.append("import std.concurrent.Publisher\n");
    }
    if (arrays.stream()
        .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.COLLECTION))) {
      text.append("import std.collections.MutableCollection\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.ITERABLE))) {
      text.append("import std.collections.IterableView\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.ITERATOR))) {
      text.append("import std.collections.IteratorView\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.LIST))) {
      text.append("import std.collections.MutableList\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.SET))) {
      text.append("import std.collections.MutableSet\n");
    }
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.MAP))) {
      text.append("import std.collections.MutableMap\n");
    }
    Set<String> arrayReferences = new java.util.LinkedHashSet<>();
    arrays.forEach(array -> collectReferences(array, arrayReferences));
    appendReferenceImports(text, module, module.name(), null, arrayReferences, normTypes);
    text.append('\n');
    List<String> callIds = new ArrayList<>();
    for (BindingPlan.Array planned : plannedArrays) {
      String className = planned.name();
      JavaArrayType array = planned.type();
      boolean generic = array.component() instanceof JavaBindingTypeVariable;
      String typeUse = generic ? "<T>" : "";
      String typeDeclaration =
          generic ? genericTypeDeclaration((JavaBindingTypeVariable) array.component()) : "";
      String tokenName = className + "BindingToken";
      JavaBindingCallable length = planned.length().callable();
      JavaBindingCallable get = planned.get().callable();
      JavaBindingCallable set = planned.set().callable();
      String constructorCall = planned.constructor().id();
      String lengthCall = planned.length().id();
      String getCall = planned.get().id();
      String setCall = planned.set().id();
      callIds.addAll(List.of(constructorCall, lengthCall, getCall, setCall));
      text.append("private class ").append(tokenName).append(" {\n}\n\n");
      text.append("class ").append(className).append(typeDeclaration).append(" {\n");
      text.append("  ")
          .append(className)
          .append('(')
          .append(tokenName)
          .append(" token) {\n  }\n\n");
      text.append("  public Integer size() {\n    ");
      appendInvocation(text, lengthCall, length, normTypes, "this");
      text.append("  }\n\n");
      text.append("  public ")
          .append(normType(array.component(), normTypes, false))
          .append(" get(Integer index) {\n    ");
      appendInvocation(text, getCall, get, normTypes, "this", List.of("index"));
      text.append("  }\n\n");
      text.append("  public Void set(Integer index, ")
          .append(normType(array.component(), normTypes, false))
          .append(" value) {\n    ");
      appendInvocation(text, setCall, set, normTypes, "this", List.of("index", "value"));
      text.append("  }\n");
      text.append("}\n\n");
      text.append("public ")
          .append(className)
          .append(typeUse)
          .append(' ')
          .append(lowerCamel(className))
          .append(generic ? "New" + typeDeclaration : "New")
          .append("(Integer size) {\n  return __jarInvoke1<")
          .append(className)
          .append(typeUse)
          .append(">(call: \"")
          .append(constructorCall)
          .append("\", arg0: size)\n}\n\n");
    }
    return new GeneratedBindingSource(
        module.name().replace('.', '/') + "/JavaArrays.norm", text.toString(), callIds);
  }

  private static GeneratedBindingSource generateSource(
      ModuleCoordinate module, BindingPlan.Declaration declaration, BindingTypeNames normTypes) {
    String exportedName = declaration.exportedName();
    List<JavaBindingTypeParameter> ownerTypeParameters = declaration.typeParameters();
    List<JavaBindingCallable> bindings = declaration.bindings();
    List<JavaReferenceType> interfaces = declaration.interfaces();
    boolean resource = declaration.resource();
    Map<String, String> enumVariants = declaration.enumVariants();
    Optional<JavaAnnotationBinding> annotationBinding = declaration.annotation();
    String className = simpleName(exportedName);
    String packageName = module.name() + exportPackage(exportedName);
    String functionPrefix = lowerCamel(className);
    StringBuilder text = new StringBuilder("package ").append(packageName).append('\n');
    if (declaration.kind() == JavaApiTypeKind.ANNOTATION) {
      return generateAnnotationSource(
          module,
          exportedName,
          declaration.binaryName(),
          declaration.annotationNames(),
          annotationBinding.orElseThrow(),
          normTypes,
          className,
          packageName,
          text);
    }
    List<JavaBindingType> bounds = bounds(ownerTypeParameters, bindings);
    List<JavaBindingType> signatureTypes = new ArrayList<>(bounds);
    signatureTypes.addAll(interfaces);
    if (signatureTypes.stream().anyMatch(JavaGenericParameterProjector::isComparable)) {
      text.append("import std.core.Comparable\n");
    }
    if (interfaces.stream().anyMatch(BindingTypeNames::iterableRelation)) {
      text.append("import std.core.Iterable\n");
    }
    if (bindings.stream().anyMatch(JavaBindingMembers::requiredProtocolBinding)) {
      text.append("import std.core.Iterator\n");
    }
    if (signatureTypes.stream().anyMatch(JavaGenericParameterProjector::isException)
        || bindings.stream().anyMatch(BindingTypeNames::containsException)) {
      text.append("import std.core.Exception\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.UNIT))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.UNIT))) {
      text.append("import std.core.Unit\n");
    }
    if (signatureTypes.stream().anyMatch(BindingTypeNames::containsPath)
        || bindings.stream().anyMatch(BindingTypeNames::containsPath)) {
      text.append("import std.filesystem.Path\n");
    }
    if (signatureTypes.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.URI))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.URI))) {
      text.append("import std.http.Uri\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.DURATION))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.DURATION))) {
      text.append("import std.time.Duration\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.INPUT_STREAM))
        || bindings.stream()
            .anyMatch(
                callable -> containsReferenceKind(callable, JavaReferenceKind.INPUT_STREAM))) {
      text.append("import std.io.InputStream\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.OUTPUT_STREAM))
        || bindings.stream()
            .anyMatch(
                callable -> containsReferenceKind(callable, JavaReferenceKind.OUTPUT_STREAM))) {
      text.append("import std.io.OutputStream\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.TASK))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.TASK))) {
      text.append("import std.concurrent.Task\n");
    }
    if (interfaces.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.PUBLISHER))
        || signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.PUBLISHER))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.PUBLISHER))) {
      text.append("import std.concurrent.Publisher\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.COLLECTION))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.COLLECTION))) {
      text.append("import std.collections.MutableCollection\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.ITERABLE))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.ITERABLE))) {
      text.append("import std.collections.IterableView\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.ITERATOR))
        || bindings.stream()
            .anyMatch(
                callable ->
                    !requiredProtocolBinding(callable)
                        && containsReferenceKind(callable, JavaReferenceKind.ITERATOR))) {
      text.append("import std.collections.IteratorView\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.LIST))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.LIST))) {
      text.append("import std.collections.MutableList\n");
    }
    if (signatureTypes.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.SET))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.SET))) {
      text.append("import std.collections.MutableSet\n");
    }
    if (signatureTypes.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.MAP))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.MAP))) {
      text.append("import std.collections.MutableMap\n");
    }
    if (resource) text.append("import std.io.Resource\n");
    Set<String> referencedTypes = new java.util.LinkedHashSet<>();
    bounds.forEach(type -> collectReferences(type, referencedTypes));
    bindings.forEach(callable -> collectReferences(callable, referencedTypes));
    interfaces.forEach(type -> collectReferences(type, referencedTypes));
    appendReferenceImports(
        text, module, packageName, declaration.binaryName(), referencedTypes, normTypes);
    if (!packageName.equals(module.name())) {
      Set<JavaArrayType> arrays = new java.util.LinkedHashSet<>();
      bindings.forEach(callable -> collectArrays(callable, arrays));
      arrays.forEach(
          array ->
              text.append("import ")
                  .append(module.name())
                  .append('.')
                  .append(normTypes.arrays().get(array))
                  .append('\n'));
    }
    text.append('\n');
    List<String> callIds = new ArrayList<>();
    boolean javaEnum = declaration.kind() == JavaApiTypeKind.ENUM;
    boolean javaInterface = declaration.kind() == JavaApiTypeKind.INTERFACE;
    if (javaEnum) {
      appendEnum(text, className, enumVariants.keySet());
    } else if (javaInterface) {
      appendInterface(
          text,
          className,
          ownerTypeParameters,
          declaration.members(),
          interfaces,
          resource,
          normTypes);
    } else {
      appendClass(
          text,
          className,
          ownerTypeParameters,
          declaration.members(),
          interfaces,
          resource,
          normTypes);
    }
    for (BindingPlan.Call function : declaration.functions()) {
      JavaBindingCallable callable = function.callable();
      String functionName = function.name();
      String callId = function.id();
      callIds.add(callId);
      List<JavaBindingTypeParameter> typeParameters = function.typeParameters();
      if (javaEnum && callable.kind().requiresReceiver()) {
        appendEnumFunction(
            text, className, functionName, callId, callable, typeParameters, normTypes);
      } else {
        appendFunction(text, functionName, callId, callable, typeParameters, normTypes);
      }
    }
    declaration.members().forEach(member -> callIds.add(member.id()));
    return new GeneratedBindingSource(
        (module.name() + "." + exportedName).replace('.', '/') + ".norm", text.toString(), callIds);
  }

  private static GeneratedBindingSource generateAnnotationSource(
      ModuleCoordinate module,
      String exportedName,
      String binaryName,
      Map<JavaAnnotationElementBinding, String> elementNames,
      JavaAnnotationBinding binding,
      BindingTypeNames normTypes,
      String annotationName,
      String packageName,
      StringBuilder text) {
    JavaAnnotationContract contract = binding.contract();
    List<String> targets = contract.normTargetInterfaces();
    if (targets.isEmpty()) {
      throw new IllegalArgumentException(
          "Java annotation has no Norm declaration target: " + binaryName);
    }
    List<String> policies = new ArrayList<>(targets);
    policies.add(contract.retention().normInterface());
    if (contract.inherited()) policies.add("InheritedAnnotation");
    if (contract.repeatableContainer().isPresent()) policies.add("RepeatableAnnotation");
    policies.stream()
        .distinct()
        .forEach(policy -> text.append("import std.annotation.").append(policy).append('\n'));
    if (binding.elements().stream().anyMatch(element -> containsException(element.type()))
        || binding.elements().stream()
            .map(JavaAnnotationElementBinding::defaultValue)
            .flatMap(Optional::stream)
            .anyMatch(
                value ->
                    value instanceof JavaAnnotationClassValue classValue
                        && Set.of(
                                "Ljava/lang/Throwable;",
                                "Ljava/lang/Exception;",
                                "Ljava/lang/RuntimeException;")
                            .contains(classValue.descriptor()))) {
      text.append("import std.core.Exception\n");
    }
    Set<String> referencedTypes = new java.util.LinkedHashSet<>();
    binding
        .elements()
        .forEach(
            element -> {
              collectReferences(element.type(), referencedTypes);
              element
                  .defaultValue()
                  .ifPresent(value -> collectAnnotationDefaultReferences(value, referencedTypes));
            });
    appendReferenceImports(text, module, packageName, binaryName, referencedTypes, normTypes);
    List<JavaAnnotationElementBinding> elements = binding.elements();
    text.append('\n')
        .append("public annotation ")
        .append(annotationName)
        .append(" implements ")
        .append(String.join(", ", policies))
        .append(" {\n");
    for (JavaAnnotationElementBinding element : elements) {
      text.append("  ")
          .append(annotationElementType(element.type(), normTypes))
          .append(' ')
          .append(elementNames.get(element))
          .append('\n');
    }
    if (elements.stream().anyMatch(element -> element.defaultValue().isPresent())) {
      text.append('\n').append("  ").append(annotationName).append("(\n");
      for (int index = 0; index < elements.size(); index++) {
        JavaAnnotationElementBinding element = elements.get(index);
        text.append("    ").append(annotationElementType(element.type(), normTypes));
        if (element.defaultValue().isPresent()) text.append('?');
        text.append(' ').append(elementNames.get(element));
        if (index + 1 < elements.size()) text.append(',');
        text.append('\n');
      }
      text.append("  ) {\n");
      for (JavaAnnotationElementBinding element : elements) {
        String elementName = elementNames.get(element);
        text.append("    this.").append(elementName).append(" = ").append(elementName);
        element
            .defaultValue()
            .ifPresent(
                value ->
                    text.append(" ?? ")
                        .append(annotationDefaultLiteral(value, element.type(), normTypes)));
        text.append('\n');
      }
      text.append("  }\n");
    }
    text.append("}\n");
    return new GeneratedBindingSource(
        (module.name() + "." + exportedName).replace('.', '/') + ".norm",
        text.toString(),
        List.of());
  }

  private static String annotationElementType(JavaBindingType type, BindingTypeNames normTypes) {
    if (type instanceof JavaArrayType array) {
      return "List<" + annotationElementType(array.component(), normTypes) + ">";
    }
    return normType(type, normTypes, true);
  }

  private static void collectAnnotationDefaultReferences(
      JavaAnnotationValue value, Set<String> references) {
    if (value instanceof JavaAnnotationArrayValue array) {
      array.values().forEach(element -> collectAnnotationDefaultReferences(element, references));
      return;
    }
    if (!(value instanceof JavaAnnotationClassValue classValue)) return;
    String descriptor = classValue.descriptor();
    if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
      references.add(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
    }
  }

  private static String annotationDefaultLiteral(
      JavaAnnotationValue value, JavaBindingType expectedType, BindingTypeNames normTypes) {
    if (value instanceof JavaAnnotationArrayValue array) {
      if (!(expectedType instanceof JavaArrayType expectedArray)) {
        throw new IllegalArgumentException(
            "Java annotation array default does not match " + expectedType.displayName());
      }
      return array.values().stream()
          .map(element -> annotationDefaultLiteral(element, expectedArray.component(), normTypes))
          .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
    if (value instanceof JavaAnnotationEnumValue enumeration) {
      if (!(expectedType instanceof JavaReferenceType reference)
          || reference.kind() != JavaReferenceKind.ENUM
          || !reference.binaryName().equals(enumeration.type())) {
        throw new IllegalArgumentException(
            "Java annotation enum default does not match " + expectedType.displayName());
      }
      Map<String, String> constants = normTypes.enumVariants().get(enumeration.type());
      if (constants == null) {
        throw new IllegalArgumentException(
            "Java annotation enum default type is not exported: " + enumeration.type());
      }
      String variant =
          constants.entrySet().stream()
              .filter(entry -> entry.getValue().equals(enumeration.constant()))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Java annotation enum constant is not exported: "
                              + enumeration.type()
                              + "."
                              + enumeration.constant()));
      return normType(expectedType, normTypes, true) + "." + variant;
    }
    if (value instanceof JavaAnnotationClassValue classValue) {
      String descriptor = classValue.descriptor();
      String className =
          switch (descriptor) {
            case "Z" -> "Boolean";
            case "B", "S", "I" -> "Integer";
            case "J" -> "Long";
            case "F" -> "Float";
            case "D" -> "Double";
            case "C" -> "CodePoint";
            case "V", "Ljava/lang/Void;" -> "Void";
            case "Ljava/lang/Object;" -> "Any";
            case "Ljava/lang/String;" -> "String";
            case "Ljava/lang/Number;" -> "Number";
            case "Ljava/lang/Throwable;", "Ljava/lang/Exception;", "Ljava/lang/RuntimeException;" ->
                "Exception";
            default -> {
              if (descriptor.startsWith("[")) {
                yield normTypes.arrays().entrySet().stream()
                    .filter(entry -> entry.getKey().descriptor().equals(descriptor))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "Java annotation class default array is not exported: "
                                    + descriptor));
              }
              if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
                throw new IllegalArgumentException(
                    "invalid Java annotation class default " + descriptor);
              }
              String binaryName =
                  descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
              String reference = normTypes.references().get(binaryName);
              if (reference == null) {
                throw new IllegalArgumentException(
                    "Java annotation class default is not exported: " + binaryName);
              }
              int typeParameters = normTypes.typeParameterCounts().getOrDefault(binaryName, 0);
              yield typeParameters == 0
                  ? reference
                  : reference
                      + java.util.stream.IntStream.range(0, typeParameters)
                          .mapToObj(ignored -> "?")
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
            }
          };
      return className + ".class";
    }
    if (!(value instanceof JavaAnnotationConstantValue constant))
      throw new IllegalArgumentException("unsupported Java annotation default " + value);
    Object content = constant.value();
    return switch (content) {
      case Boolean item -> item.toString();
      case Byte item -> item.toString();
      case Short item -> item.toString();
      case Integer item -> item.toString();
      case Long item -> item.toString();
      case Float item -> finiteDecimal(item.doubleValue(), item.toString());
      case Double item -> finiteDecimal(item, item.toString());
      case Character item -> codePointLiteral(item);
      case String item -> stringLiteral(item);
      default ->
          throw new IllegalArgumentException(
              "unsupported Java annotation constant " + content.getClass().getName());
    };
  }

  private static String finiteDecimal(double value, String literal) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("non-finite Java annotation default " + literal);
    }
    return literal;
  }

  private static String stringLiteral(String value) {
    StringBuilder result = new StringBuilder("\"");
    value.codePoints().forEach(character -> appendLiteralCodePoint(result, character, false));
    return result.append('"').toString();
  }

  private static String codePointLiteral(char value) {
    StringBuilder result = new StringBuilder("'");
    appendLiteralCodePoint(result, value, true);
    return result.append('\'').toString();
  }

  private static void appendLiteralCodePoint(
      StringBuilder result, int character, boolean codePoint) {
    switch (character) {
      case '\n' -> result.append("\\n");
      case '\r' -> result.append("\\r");
      case '\t' -> result.append("\\t");
      case '\\' -> result.append("\\\\");
      case '"' -> result.append(codePoint ? '"' : "\\\"");
      case '\'' -> result.append(codePoint ? "\\'" : "'");
      default -> {
        if (Character.isISOControl(character)) {
          throw new IllegalArgumentException(
              "Java annotation default contains an unsupported control character");
        }
        result.appendCodePoint(character);
      }
    }
  }

  private static void appendEnum(StringBuilder text, String enumName, Set<String> variants) {
    text.append("enum ").append(enumName).append(" {\n");
    int index = 0;
    for (String variant : variants) {
      text.append("  ").append(variant);
      if (++index < variants.size()) text.append(',');
      text.append('\n');
    }
    text.append("}\n\n");
  }

  private static void appendEnumFunction(
      StringBuilder text,
      String enumName,
      String functionName,
      String callId,
      JavaBindingCallable callable,
      List<JavaBindingTypeParameter> typeParameters,
      BindingTypeNames normTypes) {
    text.append("public ")
        .append(normReturnType(callable, normTypes))
        .append(' ')
        .append(functionName);
    appendTypeParameters(text, typeParameters, normTypes);
    text.append('(').append(enumName).append(" receiver");
    if (!callable.parameters().isEmpty()) text.append(", ");
    appendParameters(text, callable.parameters(), normTypes);
    text.append(") {\n  ");
    appendInvocation(text, callId, callable, normTypes, "receiver");
    text.append("}\n\n");
  }

  private static void appendReferenceImports(
      StringBuilder text,
      ModuleCoordinate module,
      String packageName,
      String ownerBinaryName,
      Set<String> references,
      BindingTypeNames normTypes) {
    Map<String, String> importedNames = new LinkedHashMap<>();
    references.stream()
        .filter(reference -> !reference.equals(ownerBinaryName))
        .sorted()
        .forEach(
            reference -> {
              String path = normTypes.referencePaths().get(reference);
              if (path == null) return;
              String referencePackage = module.name() + exportPackage(path);
              if (referencePackage.equals(packageName)) return;
              String name = normTypes.references().get(reference);
              String existing = importedNames.putIfAbsent(name, reference);
              if (existing != null && !existing.equals(reference)) {
                throw new IllegalArgumentException(
                    "Java types require the same imported Norm name: "
                        + existing
                        + " and "
                        + reference);
              }
              text.append("import ").append(module.name()).append('.').append(path).append('\n');
            });
  }

  private static void appendClass(
      StringBuilder text,
      String className,
      List<JavaBindingTypeParameter> ownerTypeParameters,
      List<BindingPlan.Call> members,
      List<JavaReferenceType> interfaces,
      boolean resource,
      BindingTypeNames normTypes) {
    String tokenName = className + "BindingToken";
    text.append("private class ").append(tokenName).append(" {\n}\n\n");
    text.append("class ").append(className);
    appendTypeParameters(text, ownerTypeParameters, normTypes);
    appendRelations(text, " implements ", interfaces, resource, normTypes);
    text.append(" {\n");
    text.append("  ").append(className).append('(').append(tokenName).append(" token) {\n  }\n\n");
    for (BindingPlan.Call member : members) {
      JavaBindingCallable callable = member.callable();
      String memberName = member.name();
      String callId = member.id();
      appendMethod(text, memberName, callId, callable, normTypes);
    }
    text.append("}\n\n");
  }

  private static void appendInterface(
      StringBuilder text,
      String interfaceName,
      List<JavaBindingTypeParameter> ownerTypeParameters,
      List<BindingPlan.Call> members,
      List<JavaReferenceType> interfaces,
      boolean resource,
      BindingTypeNames normTypes) {
    text.append("interface ").append(interfaceName);
    appendTypeParameters(text, ownerTypeParameters, normTypes);
    appendRelations(text, " extends ", interfaces, resource, normTypes);
    text.append(" {\n");
    for (BindingPlan.Call member : members) {
      JavaBindingCallable callable = member.callable();
      String memberName = member.name();
      String callId = member.id();
      appendInterfaceMethod(text, memberName, callId, callable, normTypes);
    }
    text.append("}\n\n");
    String tokenName = interfaceName + "BindingToken";
    String valueName = interfaceName + "BindingValue";
    text.append("private class ").append(tokenName).append(" {\n}\n\n");
    text.append("private class ").append(valueName);
    appendTypeParameters(text, ownerTypeParameters, normTypes);
    text.append(" implements ").append(interfaceName);
    if (!ownerTypeParameters.isEmpty()) {
      text.append(
          ownerTypeParameters.stream()
              .map(JavaBindingTypeParameter::name)
              .collect(java.util.stream.Collectors.joining(", ", "<", ">")));
    }
    text.append(" {\n  ")
        .append(valueName)
        .append('(')
        .append(tokenName)
        .append(" token) {\n  }\n}\n\n");
  }

  private static void appendRelations(
      StringBuilder text,
      String keyword,
      List<JavaReferenceType> interfaces,
      boolean resource,
      BindingTypeNames normTypes) {
    List<String> relations = new ArrayList<>();
    interfaces.forEach(type -> relations.add(normRelationType(type, normTypes)));
    if (resource) relations.add("Resource");
    if (!relations.isEmpty()) text.append(keyword).append(String.join(", ", relations));
  }

  private static void appendMethod(
      StringBuilder text,
      String memberName,
      String callId,
      JavaBindingCallable callable,
      BindingTypeNames normTypes) {
    String returnType = normReturnType(callable, normTypes);
    text.append("  public ").append(returnType).append(' ').append(memberName);
    appendTypeParameters(text, callable.typeParameters(), normTypes);
    text.append('(');
    appendParameters(text, callable.parameters(), normTypes);
    text.append(") {\n    ");
    appendInvocation(text, callId, callable, normTypes, "this");
    text.append("  }\n\n");
  }

  private static void appendInterfaceMethod(
      StringBuilder text,
      String memberName,
      String callId,
      JavaBindingCallable callable,
      BindingTypeNames normTypes) {
    String returnType = normReturnType(callable, normTypes);
    text.append("  ").append(returnType).append(' ').append(memberName);
    appendTypeParameters(text, callable.typeParameters(), normTypes);
    text.append('(');
    appendParameters(text, callable.parameters(), normTypes);
    text.append(") {\n    ");
    appendInvocation(text, callId, callable, normTypes, "this");
    text.append("  }\n\n");
  }

  private static void appendFunction(
      StringBuilder text,
      String functionName,
      String callId,
      JavaBindingCallable callable,
      List<JavaBindingTypeParameter> typeParameters,
      BindingTypeNames normTypes) {
    String returnType = normReturnType(callable, normTypes);
    text.append("public ").append(returnType).append(' ').append(functionName);
    appendTypeParameters(text, typeParameters, normTypes);
    text.append('(');
    appendParameters(text, callable.parameters(), normTypes);
    text.append(") {\n  ");
    appendInvocation(text, callId, callable, normTypes, null);
    text.append("}\n\n");
  }

  private static void appendInvocation(
      StringBuilder text,
      String callId,
      JavaBindingCallable callable,
      BindingTypeNames normTypes,
      String receiver) {
    appendInvocation(
        text,
        callId,
        callable,
        normTypes,
        receiver,
        java.util.stream.IntStream.range(0, callable.parameters().size())
            .mapToObj(index -> "arg" + index)
            .toList());
  }

  private static void appendInvocation(
      StringBuilder text,
      String callId,
      JavaBindingCallable callable,
      BindingTypeNames normTypes,
      String receiver,
      List<String> arguments) {
    int arity = callable.parameters().size() + (receiver == null ? 0 : 1);
    boolean returnsVoid = callable.returnType() == JavaPrimitiveType.VOID;
    if (!returnsVoid) {
      text.append("return ");
    }
    text.append("__jarInvoke");
    if (returnsVoid) {
      text.append("Void");
    }
    text.append(arity);
    if (!returnsVoid) {
      text.append('<').append(normReturnType(callable, normTypes)).append('>');
    }
    text.append("(call: \"").append(callId).append('"');
    int argument = 0;
    if (receiver != null) {
      text.append(", arg0: ").append(receiver);
      argument++;
    }
    for (String value : arguments) {
      text.append(", arg").append(argument).append(": ").append(value);
      argument++;
    }
    text.append(")\n");
  }

  private static void appendParameters(
      StringBuilder text, List<JavaBindingType> parameters, BindingTypeNames normTypes) {
    for (int index = 0; index < parameters.size(); index++) {
      if (index > 0) text.append(", ");
      text.append(normType(parameters.get(index), normTypes, false)).append(" arg").append(index);
    }
  }

  private static void appendTypeParameters(
      StringBuilder text, List<JavaBindingTypeParameter> parameters, BindingTypeNames normTypes) {
    if (parameters.isEmpty()) return;
    text.append(
        parameters.stream()
            .map(
                parameter ->
                    parameter.name()
                        + parameter
                            .bound()
                            .map(bound -> " extends " + normBoundType(bound, normTypes))
                            .orElse(""))
            .collect(java.util.stream.Collectors.joining(", ", "<", ">")));
  }
}
