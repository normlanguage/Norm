package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.Type;

public final class JarBindingSourceGenerator {
  private static final String BINDING_ABI = "java-v14";
  private static final Set<String> RESERVED_TYPE_NAMES =
      java.util.stream.Stream.concat(
              BuiltinCatalog.standard().typeNames().stream(),
              java.util.stream.Stream.of(
                  "Comparable",
                  "Exception",
                  "InputStream",
                  "Iterable",
                  "IterableView",
                  "Iterator",
                  "IteratorView",
                  "MutableCollection",
                  "MutableList",
                  "MutableMap",
                  "MutableSet",
                  "OutputStream",
                  "Path",
                  "Resource",
                  "Task",
                  "Unit",
                  "Uri"))
          .collect(java.util.stream.Collectors.toUnmodifiableSet());

  public GeneratedJarBinding generate(
      ModuleCoordinate module, List<String> exports, Sha256Digest graphId, JarApiSchema schema) {
    return generateSelected(
        module,
        exports.stream().map(name -> BindingSelection.allMembers(name, name)).toList(),
        graphId,
        schema);
  }

  public GeneratedJarBinding generateSurface(
      ModuleCoordinate module,
      List<JarBindingType> api,
      Sha256Digest graphId,
      JarApiSchema schema) {
    return generateSurface(
        module, api.stream().map(JarBindingType::name).toList(), api, graphId, schema);
  }

  public GeneratedJarBinding generateSurface(
      ModuleCoordinate module,
      List<String> exports,
      List<JarBindingType> api,
      Sha256Digest graphId,
      JarApiSchema schema) {
    if (exports.size() != api.size()) {
      throw new IllegalArgumentException("JAR binding exports must match API types");
    }
    List<BindingSelection> selections = new ArrayList<>(api.size());
    for (int index = 0; index < api.size(); index++) {
      selections.add(BindingSelection.declaredMembers(api.get(index), exports.get(index)));
    }
    return generateSelected(module, selections, graphId, schema);
  }

  private GeneratedJarBinding generateSelected(
      ModuleCoordinate module,
      List<BindingSelection> selections,
      Sha256Digest graphId,
      JarApiSchema schema) {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(selections, "selections");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(schema, "schema");
    Map<String, JavaApiType> apiTypes =
        schema.allTypes().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    JavaApiType::binaryName,
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    Set<String> resourceTypes = resourceTypes(apiTypes);
    Map<String, JavaApiType> exportedTypes = new LinkedHashMap<>();
    List<String> rootExports = new ArrayList<>();
    Map<String, String> referenceNames = new LinkedHashMap<>();
    Map<String, String> referencePaths = new LinkedHashMap<>();
    Map<String, Optional<MemberSelection>> selectedMembers = new LinkedHashMap<>();
    for (BindingSelection selection : selections) {
      String selectedName = selection.name();
      List<JavaApiType> matching =
          schema.allTypes().stream()
              .filter(type -> JavaTypeNames.matches(type.binaryName(), selectedName))
              .toList();
      if (matching.size() > 1) {
        List<JavaApiType> localMatching =
            matching.stream()
                .filter(
                    type -> {
                      String binaryName = type.binaryName();
                      return binaryName
                          .substring(binaryName.lastIndexOf('.') + 1)
                          .replace('$', '.')
                          .equals(selectedName);
                    })
                .toList();
        if (localMatching.size() == 1) matching = localMatching;
      }
      if (matching.size() != 1) {
        throw new IllegalArgumentException(
            "JAR binding export '"
                + selectedName
                + "' must identify exactly one dependency graph class; found "
                + matching.size());
      }
      JavaApiType owner = matching.getFirst();
      String exportedName =
          allocateTypePath(
              exportPath(selection.exportName(), owner), owner.binaryName(), referencePaths);
      if (referenceNames.putIfAbsent(owner.binaryName(), simpleName(exportedName)) != null) {
        throw new IllegalArgumentException(
            "JAR binding class is exported more than once: " + owner.binaryName());
      }
      referencePaths.put(owner.binaryName(), exportedName);
      if (exportedTypes.putIfAbsent(exportedName, owner) != null) {
        throw new IllegalArgumentException(
            "JAR binding exports map to the same Norm declaration: " + exportedName);
      }
      rootExports.add(exportedName);
      selectedMembers.put(exportedName, selection.members());
    }
    String javaPackagePrefix = javaPackagePrefix(exportedTypes);
    Map<String, List<JavaBindingTypeParameter>> exportedTypeParameters = new LinkedHashMap<>();
    Map<String, List<JavaBindingCallable>> exportedBindings = new LinkedHashMap<>();
    Map<String, List<JavaReferenceType>> exportedInterfaces = new LinkedHashMap<>();
    Set<JavaArrayType> arrays = new java.util.LinkedHashSet<>();
    List<String> generationOrder = new ArrayList<>(exportedTypes.keySet());
    for (int index = 0; index < generationOrder.size(); index++) {
      String exportedName = generationOrder.get(index);
      JavaApiType owner = exportedTypes.get(exportedName);
      List<JavaBindingTypeParameter> typeParameters = classTypeParameters(owner, apiTypes);
      List<JavaBindingCallable> availableBindings = bindings(owner, apiTypes);
      Optional<MemberSelection> selection = selectedMembers.get(exportedName);
      if (selection.isPresent())
        validateSelectedMembers(exportedName, owner, availableBindings, selection.orElseThrow());
      List<JavaBindingCallable> ownerBindings =
          availableBindings.stream()
              .filter(
                  callable ->
                      selection.isEmpty()
                          || selection.orElseThrow().matches(callable)
                          || enumConstant(owner, callable)
                          || requiredProtocolBinding(callable)
                          || resourceTypes.contains(owner.binaryName())
                              && callable.kind() == JavaCallableKind.INSTANCE_METHOD
                              && callable.name().equals("close")
                              && callable.descriptor().equals("()V"))
              .map(callable -> markResources(callable, resourceTypes))
              .toList();
      exportedTypeParameters.put(exportedName, typeParameters);
      exportedBindings.put(exportedName, ownerBindings);
      List<JavaReferenceType> interfaces = projectedInterfaces(owner, apiTypes);
      exportedInterfaces.put(exportedName, interfaces);
      ownerBindings.forEach(callable -> collectArrays(callable, arrays));
      Set<String> referencedTypes = new java.util.LinkedHashSet<>();
      typeParameters.forEach(
          parameter ->
              parameter.bound().ifPresent(type -> collectReferences(type, referencedTypes)));
      ownerBindings.forEach(callable -> collectReferences(callable, referencedTypes));
      interfaces.forEach(type -> collectReferences(type, referencedTypes));
      for (String binaryName : referencedTypes) {
        JavaApiType referenced = apiTypes.get(binaryName);
        if (referenced == null || referenceNames.containsKey(binaryName)) continue;
        String relativeName =
            allocateTypePath(
                relativeTypeName(binaryName, javaPackagePrefix), binaryName, referencePaths);
        String existingBinaryName =
            referencePaths.entrySet().stream()
                .filter(entry -> entry.getValue().equals(relativeName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (existingBinaryName != null) {
          throw new IllegalArgumentException(
              "Java types map to the same Norm declaration: "
                  + existingBinaryName
                  + " and "
                  + binaryName);
        }
        referenceNames.put(binaryName, simpleName(relativeName));
        referencePaths.put(binaryName, relativeName);
        exportedTypes.put(relativeName, referenced);
        selectedMembers.put(relativeName, Optional.of(MemberSelection.none()));
        generationOrder.add(relativeName);
      }
    }
    Map<String, Map<String, String>> enumVariants = new LinkedHashMap<>();
    exportedTypes.values().stream()
        .filter(type -> type.kind() == JavaApiTypeKind.ENUM)
        .forEach(type -> enumVariants.put(type.binaryName(), enumVariants(type)));
    NormTypes normTypes =
        new NormTypes(
            Map.copyOf(referenceNames),
            Map.copyOf(referencePaths),
            allocateArrayNames(arrays),
            enumVariants,
            exportedTypes.values().stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        JavaApiType::binaryName,
                        type -> type.signature().typeParameters().size())));
    Map<String, JavaAnnotationBinding> annotationBindings = new LinkedHashMap<>();
    exportedTypes.forEach(
        (exportedName, owner) -> {
          if (owner.kind() == JavaApiTypeKind.ANNOTATION) {
            annotationBindings.put(
                exportedName,
                annotationBinding(owner, exportedBindings.getOrDefault(exportedName, List.of())));
          }
        });
    List<GeneratedBindingSource> sources = new ArrayList<>();
    Map<String, JavaBindingCallable> calls = new LinkedHashMap<>();
    exportedTypes.forEach(
        (exportedName, owner) ->
            sources.add(
                generateSource(
                    module,
                    exportedName,
                    graphId,
                    owner,
                    exportedTypeParameters.get(exportedName),
                    exportedBindings.get(exportedName),
                    exportedInterfaces.get(exportedName),
                    resourceTypes.contains(owner.binaryName()),
                    enumVariants.getOrDefault(owner.binaryName(), Map.of()),
                    Optional.ofNullable(annotationBindings.get(exportedName)),
                    normTypes,
                    calls)));
    if (!arrays.isEmpty()) {
      sources.add(generateArraySource(module, graphId, arrays, normTypes, calls));
    }
    Map<JarBindingClassReference.Nominal, String> classDescriptors = new LinkedHashMap<>();
    exportedTypes.forEach(
        (exportedName, owner) ->
            classDescriptors.put(
                new JarBindingClassReference.Nominal(
                    module, module.name() + exportPackage(exportedName), simpleName(exportedName)),
                "L" + owner.binaryName().replace('.', '/') + ";"));
    exportedTypes.forEach(
        (exportedName, owner) -> {
          if (owner.kind() != JavaApiTypeKind.INTERFACE) return;
          classDescriptors.put(
              new JarBindingClassReference.Nominal(
                  module,
                  module.name() + exportPackage(exportedName),
                  simpleName(exportedName) + "BindingValue"),
              "L" + owner.binaryName().replace('.', '/') + ";");
        });
    arrays.forEach(
        array ->
            classDescriptors.put(
                new JarBindingClassReference.Nominal(
                    module, module.name(), normTypes.arrays().get(array)),
                array.descriptor()));
    Map<JarBindingClassReference.Nominal, Map<String, String>> enumConstants =
        new LinkedHashMap<>();
    exportedTypes.forEach(
        (exportedName, owner) -> {
          Map<String, String> constants = enumVariants.get(owner.binaryName());
          if (constants == null) return;
          enumConstants.put(
              new JarBindingClassReference.Nominal(
                  module, module.name() + exportPackage(exportedName), simpleName(exportedName)),
              constants);
        });
    Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotations =
        new LinkedHashMap<>();
    annotationBindings.forEach(
        (exportedName, binding) ->
            annotations.put(
                new JarBindingClassReference.Nominal(
                    module, module.name() + exportPackage(exportedName), simpleName(exportedName)),
                binding));
    return new GeneratedJarBinding(
        rootExports, sources, calls, classDescriptors, enumConstants, annotations);
  }

  private static void validateSelectedMembers(
      String exportedName,
      JavaApiType owner,
      List<JavaBindingCallable> availableBindings,
      MemberSelection selected) {
    for (String member : selected.groups()) {
      List<JavaApiIssue> issues = new ArrayList<>();
      boolean found =
          availableBindings.stream()
              .anyMatch(callable -> bindingMemberName(callable).equals(member));
      for (JavaApiField field : owner.fields()) {
        if (!field.name().equals(member)
            || field.disposition() == JavaApiDisposition.EXCLUDED_DEPRECATED) continue;
        found = true;
        field.issue().ifPresent(issues::add);
      }
      for (JavaApiMethod method : owner.effectiveMethods()) {
        String name = method.kind() == JavaCallableKind.CONSTRUCTOR ? "new" : method.name();
        if (!name.equals(member) || method.disposition() == JavaApiDisposition.EXCLUDED_DEPRECATED)
          continue;
        found = true;
        method.issue().ifPresent(issues::add);
      }
      if (!found) {
        throw new IllegalArgumentException(
            "JAR binding API member does not exist: " + exportedName + "." + member);
      }
      if (!issues.isEmpty()) {
        String reasons =
            issues.stream()
                .map(issue -> issue.code() + ": " + issue.detail())
                .distinct()
                .collect(java.util.stream.Collectors.joining("; "));
        throw new IllegalArgumentException(
            "JAR binding API member cannot be exposed: "
                + exportedName
                + "."
                + member
                + " ("
                + reasons
                + ")");
      }
    }
    for (JarBindingOverload overload : selected.overloads()) {
      List<JavaApiMethod> matching =
          owner.effectiveMethods().stream()
              .filter(method -> method.disposition() != JavaApiDisposition.EXCLUDED_DEPRECATED)
              .filter(method -> matches(method, overload))
              .toList();
      List<JavaBindingCallable> availableMatches =
          availableBindings.stream().filter(callable -> matches(callable, overload)).toList();
      if (matching.isEmpty() && availableMatches.isEmpty()) {
        throw new IllegalArgumentException(
            "JAR binding API overload does not exist: "
                + exportedName
                + "."
                + overloadName(overload));
      }
      List<JavaApiIssue> issues =
          matching.stream().flatMap(method -> method.issue().stream()).distinct().toList();
      if (!issues.isEmpty()) {
        String reasons =
            issues.stream()
                .map(issue -> issue.code() + ": " + issue.detail())
                .distinct()
                .collect(java.util.stream.Collectors.joining("; "));
        throw new IllegalArgumentException(
            "JAR binding API overload cannot be exposed: "
                + exportedName
                + "."
                + overloadName(overload)
                + " ("
                + reasons
                + ")");
      }
      if (availableMatches.isEmpty()) {
        throw new IllegalStateException(
            "bindable JAR API overload has no generated call: "
                + exportedName
                + "."
                + overloadName(overload));
      }
    }
  }

  private static boolean matches(JavaApiMethod method, JarBindingOverload overload) {
    String name = method.kind() == JavaCallableKind.CONSTRUCTOR ? "new" : method.name();
    return name.equals(overload.name())
        && descriptorParameters(method.descriptor()).equals(normalizedParameters(overload));
  }

  private static boolean matches(JavaBindingCallable callable, JarBindingOverload overload) {
    return bindingMemberName(callable).equals(overload.name())
        && descriptorParameters(callable.descriptor()).equals(normalizedParameters(overload));
  }

  private static List<String> descriptorParameters(String descriptor) {
    return java.util.Arrays.stream(Type.getArgumentTypes(descriptor))
        .map(Type::getClassName)
        .map(JarBindingSourceGenerator::normalizeJavaTypeName)
        .toList();
  }

  private static List<String> normalizedParameters(JarBindingOverload overload) {
    return overload.parameterTypes().stream()
        .map(JarBindingSourceGenerator::normalizeJavaTypeName)
        .toList();
  }

  private static String normalizeJavaTypeName(String value) {
    return value.replace('$', '.');
  }

  private static String overloadName(JarBindingOverload overload) {
    return overload.name() + "(" + String.join(", ", overload.parameterTypes()) + ")";
  }

  private static String bindingMemberName(JavaBindingCallable callable) {
    return callable.kind() == JavaCallableKind.CONSTRUCTOR ? "new" : callable.name();
  }

  private static void collectArrays(JavaBindingCallable callable, Set<JavaArrayType> arrays) {
    callable.parameters().forEach(type -> collectArrays(type, arrays));
    collectArrays(callable.returnType(), arrays);
  }

  private static void collectReferences(JavaBindingCallable callable, Set<String> references) {
    callable
        .typeParameters()
        .forEach(
            parameter -> parameter.bound().ifPresent(type -> collectReferences(type, references)));
    callable.parameters().forEach(type -> collectReferences(type, references));
    collectReferences(callable.returnType(), references);
  }

  private static void collectReferences(JavaBindingType type, Set<String> references) {
    switch (type) {
      case JavaArrayType array -> collectReferences(array.component(), references);
      case JavaBindingTypeVariable variable -> collectReferences(variable.erasure(), references);
      case JavaCallbackType callback -> {
        callback.parameters().forEach(parameter -> collectReferences(parameter, references));
        collectReferences(callback.returnType(), references);
      }
      case JavaReferenceType reference -> {
        if (reference.kind() == JavaReferenceKind.OPAQUE
            || reference.kind() == JavaReferenceKind.ENUM
            || reference.kind() == JavaReferenceKind.RESOURCE) {
          references.add(reference.binaryName());
        }
        reference
            .arguments()
            .forEach(
                argument ->
                    argument.type().ifPresent(value -> collectReferences(value, references)));
      }
      case JavaBoxedType ignored -> {}
      case JavaPrimitiveType ignored -> {}
    }
  }

  private static String javaPackagePrefix(Map<String, JavaApiType> exportedTypes) {
    List<String> prefixes = new ArrayList<>();
    exportedTypes.forEach(
        (exportedName, type) -> {
          String suffix = "." + exportedName;
          if (type.binaryName().endsWith(suffix)) {
            prefixes.add(
                type.binaryName().substring(0, type.binaryName().length() - suffix.length()));
          }
        });
    if (prefixes.isEmpty()) return "";
    String[] shared = prefixes.getFirst().split("\\.");
    int length = shared.length;
    for (String prefix : prefixes.subList(1, prefixes.size())) {
      String[] segments = prefix.split("\\.");
      length = Math.min(length, segments.length);
      int index = 0;
      while (index < length && shared[index].equals(segments[index])) index++;
      length = index;
    }
    return String.join(".", java.util.Arrays.copyOf(shared, length));
  }

  private static String relativeTypeName(String binaryName, String javaPackagePrefix) {
    String relative =
        !javaPackagePrefix.isEmpty() && binaryName.startsWith(javaPackagePrefix + ".")
            ? binaryName.substring(javaPackagePrefix.length() + 1)
            : binaryName;
    return relative.replace('$', '_');
  }

  private static String allocateTypePath(
      String preferred, String binaryName, Map<String, String> allocated) {
    int separator = preferred.lastIndexOf('.');
    String prefix = separator < 0 ? "" : preferred.substring(0, separator + 1);
    String name = simpleName(preferred);
    String safeName = RESERVED_TYPE_NAMES.contains(name) ? "Java" + name : name;
    String candidate = prefix + safeName;
    boolean occupied =
        allocated.entrySet().stream()
            .anyMatch(
                entry -> !entry.getKey().equals(binaryName) && entry.getValue().equals(candidate));
    if (!occupied) return candidate;
    String digest =
        Sha256Digest.compute(binaryName.getBytes(StandardCharsets.UTF_8)).value().substring(0, 8);
    return prefix + safeName + "X" + digest;
  }

  private static void collectArrays(JavaBindingType type, Set<JavaArrayType> arrays) {
    switch (type) {
      case JavaArrayType array -> {
        arrays.add(array);
        collectArrays(array.component(), arrays);
      }
      case JavaReferenceType reference ->
          reference
              .arguments()
              .forEach(
                  argument -> argument.type().ifPresent(value -> collectArrays(value, arrays)));
      case JavaBindingTypeVariable variable -> collectArrays(variable.erasure(), arrays);
      case JavaCallbackType callback -> {
        callback.parameters().forEach(parameter -> collectArrays(parameter, arrays));
        collectArrays(callback.returnType(), arrays);
      }
      case JavaBoxedType ignored -> {}
      case JavaPrimitiveType ignored -> {}
    }
  }

  private static Map<JavaArrayType, String> allocateArrayNames(Set<JavaArrayType> arrays) {
    Map<String, List<JavaArrayType>> groups = new LinkedHashMap<>();
    arrays.forEach(
        array ->
            groups
                .computeIfAbsent(
                    array.component() instanceof JavaBindingTypeVariable
                        ? genericArrayName((JavaBindingTypeVariable) array.component())
                        : "Java" + bindingTypeSuffix(array.component()) + "Array",
                    ignored -> new ArrayList<>())
                .add(array));
    Map<JavaArrayType, String> names = new LinkedHashMap<>();
    groups.forEach(
        (base, values) ->
            values.forEach(
                array -> {
                  String name = base;
                  if (values.stream().map(JavaBindingType::descriptor).distinct().count() > 1) {
                    String digest =
                        Sha256Digest.compute(array.descriptor().getBytes(StandardCharsets.UTF_8))
                            .value();
                    name += "X" + digest.substring(0, 8);
                  }
                  names.put(array, name);
                }));
    return Map.copyOf(names);
  }

  private static String genericArrayName(JavaBindingTypeVariable component) {
    if (JavaGenericParameterProjector.isComparable(component.erasure())) {
      return "JavaComparableArray";
    }
    if (JavaGenericParameterProjector.isException(component.erasure())) {
      return "Java" + simpleName(component.erasure().displayName()) + "Array";
    }
    return "JavaObjectArray";
  }

  private static GeneratedBindingSource generateArraySource(
      ModuleCoordinate module,
      Sha256Digest graphId,
      Set<JavaArrayType> arrays,
      NormTypes normTypes,
      Map<String, JavaBindingCallable> calls) {
    StringBuilder text = new StringBuilder("package ").append(module.name()).append('\n');
    boolean comparableArrays =
        arrays.stream()
            .map(JavaArrayType::component)
            .filter(JavaBindingTypeVariable.class::isInstance)
            .map(JavaBindingTypeVariable.class::cast)
            .map(JavaBindingTypeVariable::erasure)
            .anyMatch(JavaGenericParameterProjector::isComparable);
    boolean exceptionArrays =
        arrays.stream()
            .map(JavaArrayType::component)
            .anyMatch(JarBindingSourceGenerator::containsException);
    if (comparableArrays) {
      text.append("import std.core.Comparable\n");
    }
    if (exceptionArrays) text.append("import std.core.Exception\n");
    if (arrays.stream().anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.UNIT))) {
      text.append("import std.core.Unit\n");
    }
    if (arrays.stream().anyMatch(JarBindingSourceGenerator::containsPath)) {
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
    Set<String> generatedClasses = new java.util.LinkedHashSet<>();
    for (JavaArrayType exposedArray : arrays) {
      String className = normTypes.arrays().get(exposedArray);
      if (!generatedClasses.add(className)) continue;
      JavaArrayType array = arraySupportType(exposedArray);
      boolean generic = array.component() instanceof JavaBindingTypeVariable;
      String typeUse = generic ? "<T>" : "";
      String typeDeclaration =
          generic ? genericTypeDeclaration((JavaBindingTypeVariable) array.component()) : "";
      String tokenName = className + "BindingToken";
      JavaBindingCallable constructor = arrayConstructor(array);
      JavaBindingCallable length = arrayLength(array);
      JavaBindingCallable get = arrayGet(array);
      JavaBindingCallable set = arraySet(array);
      String constructorCall = register(graphId, constructor, calls, callIds);
      String lengthCall = register(graphId, length, calls, callIds);
      String getCall = register(graphId, get, calls, callIds);
      String setCall = register(graphId, set, calls, callIds);
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

  private static JavaArrayType arraySupportType(JavaArrayType array) {
    if (!(array.component() instanceof JavaBindingTypeVariable variable)) return array;
    return new JavaArrayType(new JavaBindingTypeVariable("T", variable.erasure()));
  }

  private static JavaBindingCallable arrayConstructor(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "<array>",
        "(I)" + array.descriptor(),
        JavaCallableKind.ARRAY_CONSTRUCTOR,
        List.of(JavaPrimitiveType.INT),
        array);
  }

  private static JavaBindingCallable arrayLength(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "length",
        "()I",
        JavaCallableKind.ARRAY_LENGTH,
        List.of(),
        JavaPrimitiveType.INT);
  }

  private static JavaBindingCallable arrayGet(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "get",
        "(I)" + array.component().descriptor(),
        JavaCallableKind.ARRAY_GET,
        List.of(JavaPrimitiveType.INT),
        array.component());
  }

  private static JavaBindingCallable arraySet(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "set",
        "(I" + array.component().descriptor() + ")V",
        JavaCallableKind.ARRAY_SET,
        List.of(JavaPrimitiveType.INT, array.component()),
        JavaPrimitiveType.VOID);
  }

  private static JavaAnnotationBinding annotationBinding(
      JavaApiType owner, List<JavaBindingCallable> bindings) {
    Map<String, JavaApiMethod> methods =
        owner.methods().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    method -> methodKey(method.name(), method.descriptor()),
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    List<JavaAnnotationElementBinding> elements = new ArrayList<>();
    for (JavaBindingCallable binding : bindings) {
      if (binding.kind() != JavaCallableKind.INSTANCE_METHOD
          || !binding.parameters().isEmpty()
          || binding.returnType() == JavaPrimitiveType.VOID) {
        throw new IllegalArgumentException(
            "invalid Java annotation element "
                + binding.owner()
                + "."
                + binding.name()
                + binding.descriptor());
      }
      JavaApiMethod method = methods.get(methodKey(binding.name(), binding.descriptor()));
      if (method == null) {
        throw new IllegalArgumentException(
            "Java annotation element is not declared by "
                + owner.binaryName()
                + ": "
                + binding.name()
                + binding.descriptor());
      }
      elements.add(
          new JavaAnnotationElementBinding(
              binding.name(),
              binding.descriptor(),
              binding.returnType(),
              method.annotationDefault()));
    }
    return new JavaAnnotationBinding(
        owner.binaryName(), JavaAnnotationContract.from(owner), elements);
  }

  private static GeneratedBindingSource generateSource(
      ModuleCoordinate module,
      String exportedName,
      Sha256Digest graphId,
      JavaApiType owner,
      List<JavaBindingTypeParameter> ownerTypeParameters,
      List<JavaBindingCallable> bindings,
      List<JavaReferenceType> interfaces,
      boolean resource,
      Map<String, String> enumVariants,
      Optional<JavaAnnotationBinding> annotationBinding,
      NormTypes normTypes,
      Map<String, JavaBindingCallable> calls) {
    String className = simpleName(exportedName);
    String packageName = module.name() + exportPackage(exportedName);
    String functionPrefix = lowerCamel(className);
    StringBuilder text = new StringBuilder("package ").append(packageName).append('\n');
    if (owner.kind() == JavaApiTypeKind.ANNOTATION) {
      return generateAnnotationSource(
          module,
          exportedName,
          owner,
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
    if (interfaces.stream().anyMatch(JarBindingSourceGenerator::iterableRelation)) {
      text.append("import std.core.Iterable\n");
    }
    if (bindings.stream().anyMatch(JarBindingSourceGenerator::requiredProtocolBinding)) {
      text.append("import std.core.Iterator\n");
    }
    if (signatureTypes.stream().anyMatch(JavaGenericParameterProjector::isException)
        || bindings.stream().anyMatch(JarBindingSourceGenerator::containsException)) {
      text.append("import std.core.Exception\n");
    }
    if (signatureTypes.stream()
            .anyMatch(type -> containsReferenceKind(type, JavaReferenceKind.UNIT))
        || bindings.stream()
            .anyMatch(callable -> containsReferenceKind(callable, JavaReferenceKind.UNIT))) {
      text.append("import std.core.Unit\n");
    }
    if (signatureTypes.stream().anyMatch(JarBindingSourceGenerator::containsPath)
        || bindings.stream().anyMatch(JarBindingSourceGenerator::containsPath)) {
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
        text, module, packageName, owner.binaryName(), referencedTypes, normTypes);
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
    Map<String, JavaBindingCallable> signatures = new LinkedHashMap<>();
    boolean javaEnum = owner.kind() == JavaApiTypeKind.ENUM;
    boolean javaInterface = owner.kind() == JavaApiTypeKind.INTERFACE;
    if (javaEnum) {
      appendEnum(text, className, enumVariants.keySet());
    } else if (javaInterface) {
      appendInterface(
          text,
          className,
          graphId,
          ownerTypeParameters,
          bindings,
          interfaces,
          resource,
          normTypes,
          calls);
    } else {
      appendClass(
          text,
          className,
          graphId,
          ownerTypeParameters,
          bindings,
          interfaces,
          resource,
          normTypes,
          calls);
    }
    List<JavaBindingCallable> functions =
        bindings.stream()
            .filter(callable -> javaEnum || !callable.kind().requiresReceiver())
            .filter(callable -> !javaEnum || !enumConstant(owner, callable))
            .toList();
    Map<JavaBindingCallable, String> functionNames =
        javaEnum
            ? allocateEnumNames(functions, functionPrefix, normTypes)
            : allocateNames(
                functions, callable -> functionName(functionPrefix, callable), normTypes);
    for (JavaBindingCallable callable : functions) {
      String functionName = functionNames.get(callable);
      if (javaEnum) {
        addEnumSignature(signatures, functionName, callable, normTypes);
      } else {
        addSignature(signatures, functionName, callable, normTypes);
      }
      String callId = register(graphId, callable, calls, callIds);
      List<JavaBindingTypeParameter> typeParameters =
          callable.kind() == JavaCallableKind.CONSTRUCTOR
              ? constructorTypeParameters(ownerTypeParameters, callable)
              : callable.typeParameters();
      if (javaEnum && callable.kind().requiresReceiver()) {
        appendEnumFunction(
            text, className, functionName, callId, callable, typeParameters, normTypes);
      } else {
        appendFunction(text, functionName, callId, callable, typeParameters, normTypes);
      }
    }
    if (!javaEnum) {
      for (JavaBindingCallable callable : bindings) {
        if (!callable.kind().requiresReceiver()) continue;
        callIds.add(callId(graphId, callable));
      }
    }
    return new GeneratedBindingSource(
        (module.name() + "." + exportedName).replace('.', '/') + ".norm", text.toString(), callIds);
  }

  private static GeneratedBindingSource generateAnnotationSource(
      ModuleCoordinate module,
      String exportedName,
      JavaApiType owner,
      JavaAnnotationBinding binding,
      NormTypes normTypes,
      String annotationName,
      String packageName,
      StringBuilder text) {
    JavaAnnotationContract contract = binding.contract();
    List<String> targets = contract.normTargetInterfaces();
    if (targets.isEmpty()) {
      throw new IllegalArgumentException(
          "Java annotation has no Norm declaration target: " + owner.binaryName());
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
    appendReferenceImports(
        text, module, packageName, owner.binaryName(), referencedTypes, normTypes);
    List<JavaAnnotationElementBinding> elements = binding.elements();
    Map<JavaAnnotationElementBinding, String> elementNames = annotationElementNames(elements);
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

  private static String annotationElementType(JavaBindingType type, NormTypes normTypes) {
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

  private static Map<JavaAnnotationElementBinding, String> annotationElementNames(
      List<JavaAnnotationElementBinding> elements) {
    Map<JavaAnnotationElementBinding, String> names = new LinkedHashMap<>();
    Set<String> used = new java.util.LinkedHashSet<>();
    elements.stream()
        .map(JavaAnnotationElementBinding::name)
        .filter(LanguageSyntax::isIdentifier)
        .forEach(used::add);
    for (JavaAnnotationElementBinding element : elements) {
      if (LanguageSyntax.isIdentifier(element.name())) {
        names.put(element, element.name());
        continue;
      }
      String name = normIdentifier(element.name());
      if (!used.add(name)) {
        name +=
            "Java"
                + Sha256Digest.compute(element.name().getBytes(StandardCharsets.UTF_8))
                    .value()
                    .substring(0, 8);
        used.add(name);
      }
      names.put(element, name);
    }
    return Map.copyOf(names);
  }

  private static String annotationDefaultLiteral(
      JavaAnnotationValue value, JavaBindingType expectedType, NormTypes normTypes) {
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
      NormTypes normTypes) {
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
      NormTypes normTypes) {
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
      Sha256Digest graphId,
      List<JavaBindingTypeParameter> ownerTypeParameters,
      List<JavaBindingCallable> bindings,
      List<JavaReferenceType> interfaces,
      boolean resource,
      NormTypes normTypes,
      Map<String, JavaBindingCallable> calls) {
    String tokenName = className + "BindingToken";
    text.append("private class ").append(tokenName).append(" {\n}\n\n");
    text.append("class ").append(className);
    appendTypeParameters(text, ownerTypeParameters, normTypes);
    appendRelations(text, " implements ", interfaces, resource, normTypes);
    text.append(" {\n");
    text.append("  ").append(className).append('(').append(tokenName).append(" token) {\n  }\n\n");
    Map<String, JavaBindingCallable> signatures = new LinkedHashMap<>();
    List<JavaBindingCallable> members =
        bindings.stream().filter(callable -> callable.kind().requiresReceiver()).toList();
    Map<JavaBindingCallable, String> memberNames =
        allocateNames(members, JarBindingSourceGenerator::memberName, normTypes);
    for (JavaBindingCallable callable : members) {
      String memberName = memberNames.get(callable);
      addSignature(signatures, memberName, callable, normTypes);
      String callId = callId(graphId, callable);
      registerCall(calls, callId, callable);
      appendMethod(text, memberName, callId, callable, normTypes);
    }
    text.append("}\n\n");
  }

  private static void appendInterface(
      StringBuilder text,
      String interfaceName,
      Sha256Digest graphId,
      List<JavaBindingTypeParameter> ownerTypeParameters,
      List<JavaBindingCallable> bindings,
      List<JavaReferenceType> interfaces,
      boolean resource,
      NormTypes normTypes,
      Map<String, JavaBindingCallable> calls) {
    text.append("interface ").append(interfaceName);
    appendTypeParameters(text, ownerTypeParameters, normTypes);
    appendRelations(text, " extends ", interfaces, resource, normTypes);
    text.append(" {\n");
    Map<String, JavaBindingCallable> signatures = new LinkedHashMap<>();
    List<JavaBindingCallable> members =
        bindings.stream().filter(callable -> callable.kind().requiresReceiver()).toList();
    Map<JavaBindingCallable, String> memberNames =
        allocateNames(members, JarBindingSourceGenerator::memberName, normTypes);
    for (JavaBindingCallable callable : members) {
      String memberName = memberNames.get(callable);
      addSignature(signatures, memberName, callable, normTypes);
      String callId = callId(graphId, callable);
      registerCall(calls, callId, callable);
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
      NormTypes normTypes) {
    List<String> relations = new ArrayList<>();
    interfaces.forEach(type -> relations.add(normRelationType(type, normTypes)));
    if (resource) relations.add("Resource");
    if (!relations.isEmpty()) text.append(keyword).append(String.join(", ", relations));
  }

  private static String normRelationType(JavaReferenceType type, NormTypes normTypes) {
    if (iterableRelation(type)) {
      return "Iterable<" + normReferenceArgument(type, 0, 1, normTypes) + ">";
    }
    return normBoundType(type, normTypes);
  }

  private static boolean iterableRelation(JavaReferenceType type) {
    return switch (type.kind()) {
      case ITERABLE, COLLECTION, LIST, SET -> true;
      default -> false;
    };
  }

  private static void appendMethod(
      StringBuilder text,
      String memberName,
      String callId,
      JavaBindingCallable callable,
      NormTypes normTypes) {
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
      NormTypes normTypes) {
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
      NormTypes normTypes) {
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
      NormTypes normTypes,
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
      NormTypes normTypes,
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
      StringBuilder text, List<JavaBindingType> parameters, NormTypes normTypes) {
    for (int index = 0; index < parameters.size(); index++) {
      if (index > 0) text.append(", ");
      text.append(normType(parameters.get(index), normTypes, false)).append(" arg").append(index);
    }
  }

  private static void addSignature(
      Map<String, JavaBindingCallable> signatures,
      String name,
      JavaBindingCallable callable,
      NormTypes normTypes) {
    if (signatures.putIfAbsent(signature(name, callable, normTypes), callable) != null) {
      throw new IllegalArgumentException(
          "Java overloads collapse to the same Norm signature: "
              + callable.owner()
              + "."
              + callable.name()
              + callable.descriptor());
    }
  }

  private static Map<JavaBindingCallable, String> allocateNames(
      List<JavaBindingCallable> callables,
      Function<JavaBindingCallable, String> baseName,
      NormTypes normTypes) {
    Map<String, List<JavaBindingCallable>> groups = new LinkedHashMap<>();
    for (JavaBindingCallable callable : callables) {
      String name = baseName.apply(callable);
      String signature = signature(name, callable, normTypes);
      groups.computeIfAbsent(signature, ignored -> new ArrayList<>()).add(callable);
    }
    Map<JavaBindingCallable, String> names = new LinkedHashMap<>();
    for (List<JavaBindingCallable> group : groups.values()) {
      Map<JavaBindingCallable, String> candidates = new LinkedHashMap<>();
      for (JavaBindingCallable callable : group) {
        String name = baseName.apply(callable);
        if (group.size() > 1) name += "Java" + parameterSuffix(callable);
        candidates.put(callable, name);
      }
      Map<String, Long> counts =
          candidates.values().stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(
                      Function.identity(),
                      LinkedHashMap::new,
                      java.util.stream.Collectors.counting()));
      for (JavaBindingCallable callable : group) {
        String name = candidates.get(callable);
        if (counts.get(name) > 1) {
          name +=
              "Java"
                  + Sha256Digest.compute(
                          (callable.name() + callable.descriptor())
                              .getBytes(StandardCharsets.UTF_8))
                      .value()
                      .substring(0, 8);
        }
        names.put(callable, name);
      }
    }
    return Map.copyOf(names);
  }

  private static Map<JavaBindingCallable, String> allocateEnumNames(
      List<JavaBindingCallable> callables, String prefix, NormTypes normTypes) {
    Map<String, List<JavaBindingCallable>> groups = new LinkedHashMap<>();
    for (JavaBindingCallable callable : callables) {
      String name = enumFunctionName(prefix, callable);
      groups
          .computeIfAbsent(enumSignature(name, callable, normTypes), ignored -> new ArrayList<>())
          .add(callable);
    }
    Map<JavaBindingCallable, String> names = new LinkedHashMap<>();
    for (List<JavaBindingCallable> group : groups.values()) {
      for (JavaBindingCallable callable : group) {
        String name = enumFunctionName(prefix, callable);
        if (group.size() > 1) name += "Java" + parameterSuffix(callable);
        names.put(callable, name);
      }
    }
    return Map.copyOf(names);
  }

  private static void addEnumSignature(
      Map<String, JavaBindingCallable> signatures,
      String name,
      JavaBindingCallable callable,
      NormTypes normTypes) {
    if (signatures.putIfAbsent(enumSignature(name, callable, normTypes), callable) != null) {
      throw new IllegalArgumentException(
          "Java overloads collapse to the same Norm signature: "
              + callable.owner()
              + "."
              + callable.name()
              + callable.descriptor());
    }
  }

  private static String enumSignature(
      String name, JavaBindingCallable callable, NormTypes normTypes) {
    String receiver = callable.kind().requiresReceiver() ? "<enum>," : "";
    return name
        + callable.parameters().stream()
            .map(type -> normType(type, normTypes, false))
            .collect(java.util.stream.Collectors.joining(",", "(" + receiver, ")"));
  }

  private static String signature(String name, JavaBindingCallable callable, NormTypes normTypes) {
    return name
        + callable.parameters().stream()
            .map(type -> normType(type, normTypes, false))
            .collect(java.util.stream.Collectors.joining(",", "(", ")"));
  }

  private static String parameterSuffix(JavaBindingCallable callable) {
    if (callable.parameters().isEmpty()) return "NoArguments";
    return callable.parameters().stream()
        .map(JarBindingSourceGenerator::bindingTypeSuffix)
        .collect(java.util.stream.Collectors.joining("And"));
  }

  private static String bindingTypeSuffix(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType array -> bindingTypeSuffix(array.component()) + "Array";
      case JavaPrimitiveType primitive -> upperCamel(primitive.name().toLowerCase(Locale.ROOT));
      case JavaBoxedType boxed ->
          "Boxed" + upperCamel(boxed.primitive().name().toLowerCase(Locale.ROOT));
      case JavaBindingTypeVariable variable -> "Type" + variable.name();
      case JavaCallbackType callback -> simpleName(callback.binaryName()).replace('$', '_');
      case JavaReferenceType reference ->
          reference.kind() == JavaReferenceKind.OBJECT
              ? "Any"
              : simpleName(reference.binaryName()).replace('$', '_');
    };
  }

  private static String register(
      Sha256Digest graphId,
      JavaBindingCallable callable,
      Map<String, JavaBindingCallable> calls,
      List<String> callIds) {
    String callId = callId(graphId, callable);
    registerCall(calls, callId, callable);
    callIds.add(callId);
    return callId;
  }

  private static void registerCall(
      Map<String, JavaBindingCallable> calls, String callId, JavaBindingCallable callable) {
    JavaBindingCallable existing = calls.putIfAbsent(callId, callable);
    if (existing != null && !existing.equals(callable)) {
      throw new IllegalArgumentException("conflicting Java binding call " + callId);
    }
  }

  private static List<JavaBindingCallable> bindings(
      JavaApiType owner, Map<String, JavaApiType> apiTypes) {
    List<JavaBindingCallable> bindings = new ArrayList<>();
    owner.fields().stream().flatMap(field -> field.bindings().stream()).forEach(bindings::add);
    owner.effectiveMethods().stream()
        .flatMap(method -> method.binding().stream())
        .forEach(bindings::add);
    Map<String, JavaBindingType> variables = classVariables(owner, apiTypes);
    java.util.Optional<JavaReferenceType> collection =
        platformCollectionType(owner, variables, apiTypes, new java.util.LinkedHashSet<>());
    if (collection.isPresent()) {
      List<JavaBindingCallable> platform = platformCollectionBindings(collection.orElseThrow());
      Set<String> platformShapes =
          platform.stream()
              .map(JarBindingSourceGenerator::methodShape)
              .collect(java.util.stream.Collectors.toUnmodifiableSet());
      bindings.removeIf(
          callable ->
              callable.kind() == JavaCallableKind.INSTANCE_METHOD
                  && platformShapes.contains(methodShape(callable)));
      bindings.addAll(platform);
    }
    java.util.Set<String> declared = new java.util.LinkedHashSet<>();
    owner
        .effectiveMethods()
        .forEach(method -> declared.add(methodKey(method.name(), method.descriptor())));
    java.util.Set<String> resolvedShapes =
        bindings.stream()
            .filter(callable -> callable.kind() == JavaCallableKind.INSTANCE_METHOD)
            .map(JarBindingSourceGenerator::methodShape)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    inheritedBindings(
        owner,
        variables,
        apiTypes,
        declared,
        resolvedShapes,
        new java.util.LinkedHashSet<>(),
        bindings);
    return List.copyOf(bindings);
  }

  private static java.util.Optional<JavaReferenceType> platformCollectionType(
      JavaApiType owner,
      Map<String, JavaBindingType> variables,
      Map<String, JavaApiType> apiTypes,
      java.util.Set<String> visited) {
    if (!visited.add(owner.binaryName())) return java.util.Optional.empty();
    List<JavaClassTypeSignature> parents = new ArrayList<>();
    owner.signature().superclass().ifPresent(parents::add);
    parents.addAll(owner.signature().interfaces());
    for (JavaClassTypeSignature relation : parents) {
      JavaBindingType projected = bindingType(relation, variables, apiTypes);
      if (projected instanceof JavaReferenceType reference
          && switch (reference.kind()) {
            case ITERABLE, COLLECTION, LIST, SET, MAP -> true;
            default -> false;
          }) {
        return java.util.Optional.of(reference);
      }
      JavaApiType parent = apiTypes.get(relation.binaryName());
      if (parent == null) continue;
      java.util.Optional<Map<String, JavaBindingType>> resolved =
          parentVariables(parent, relation, variables, apiTypes);
      if (resolved.isEmpty()) continue;
      java.util.Optional<JavaReferenceType> inherited =
          platformCollectionType(parent, resolved.orElseThrow(), apiTypes, visited);
      if (inherited.isPresent()) return inherited;
    }
    return java.util.Optional.empty();
  }

  private static List<JavaBindingCallable> platformCollectionBindings(
      JavaReferenceType collection) {
    JavaBindingType object = new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    JavaBindingType first =
        collection.arguments().isEmpty()
            ? object
            : collection.arguments().getFirst().type().orElse(object);
    JavaBindingType second =
        collection.arguments().size() < 2
            ? object
            : collection.arguments().get(1).type().orElse(object);
    List<JavaBindingCallable> bindings = new ArrayList<>();
    if (collection.kind() == JavaReferenceKind.MAP) {
      bindings.add(
          new JavaBindingCallable(
              "java.util.Map",
              "size",
              "()I",
              JavaCallableKind.INSTANCE_METHOD,
              List.of(),
              JavaPrimitiveType.INT));
      bindings.add(
          new JavaBindingCallable(
              "java.util.Map",
              "containsKey",
              "(Ljava/lang/Object;)Z",
              JavaCallableKind.INSTANCE_METHOD,
              List.of(first),
              JavaPrimitiveType.BOOLEAN));
      bindings.add(
          new JavaBindingCallable(
              "java.util.Map",
              "get",
              "(Ljava/lang/Object;)Ljava/lang/Object;",
              JavaCallableKind.INSTANCE_METHOD,
              List.of(first),
              second));
      bindings.add(
          new JavaBindingCallable(
              "java.util.Map",
              "put",
              "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
              JavaCallableKind.INSTANCE_METHOD,
              List.of(first, second),
              second));
      bindings.add(
          new JavaBindingCallable(
              "java.util.Map",
              "remove",
              "(Ljava/lang/Object;)Ljava/lang/Object;",
              JavaCallableKind.INSTANCE_METHOD,
              List.of(first),
              second));
      return List.copyOf(bindings);
    }
    bindings.add(
        new JavaBindingCallable(
            "java.lang.Iterable",
            "iterator",
            "()Ljava/util/Iterator;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            List.of(),
            new JavaReferenceType(
                "java.util.Iterator",
                JavaReferenceKind.ITERATOR,
                List.of(JavaBindingTypeArgument.exact(first))),
            JavaNullability.NON_NULL));
    if (collection.kind() == JavaReferenceKind.ITERABLE) return List.copyOf(bindings);
    bindings.add(
        new JavaBindingCallable(
            "java.util.Collection",
            "size",
            "()I",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.INT));
    bindings.add(
        new JavaBindingCallable(
            "java.util.Collection",
            "contains",
            "(Ljava/lang/Object;)Z",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(first),
            JavaPrimitiveType.BOOLEAN));
    bindings.add(
        new JavaBindingCallable(
            "java.util.Collection",
            "add",
            "(Ljava/lang/Object;)Z",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(first),
            JavaPrimitiveType.BOOLEAN));
    bindings.add(
        new JavaBindingCallable(
            "java.util.Collection",
            "remove",
            "(Ljava/lang/Object;)Z",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(first),
            JavaPrimitiveType.BOOLEAN));
    if (collection.kind() != JavaReferenceKind.LIST) return List.copyOf(bindings);
    bindings.add(
        new JavaBindingCallable(
            "java.util.List",
            "get",
            "(I)Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(JavaPrimitiveType.INT),
            first));
    bindings.add(
        new JavaBindingCallable(
            "java.util.List",
            "set",
            "(ILjava/lang/Object;)Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(JavaPrimitiveType.INT, first),
            first));
    bindings.add(
        new JavaBindingCallable(
            "java.util.List",
            "remove",
            "(I)Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(JavaPrimitiveType.INT),
            first));
    return List.copyOf(bindings);
  }

  private static void inheritedBindings(
      JavaApiType owner,
      Map<String, JavaBindingType> variables,
      Map<String, JavaApiType> apiTypes,
      java.util.Set<String> declared,
      java.util.Set<String> resolvedShapes,
      java.util.Set<String> visited,
      List<JavaBindingCallable> bindings) {
    List<JavaClassTypeSignature> parents = new ArrayList<>();
    owner.signature().superclass().ifPresent(parents::add);
    parents.addAll(owner.signature().interfaces());
    for (JavaClassTypeSignature relation : parents) {
      JavaApiType parent = apiTypes.get(relation.binaryName());
      if (parent == null || !visited.add(parent.binaryName())) continue;
      java.util.Optional<Map<String, JavaBindingType>> resolvedParentVariables =
          parentVariables(parent, relation, variables, apiTypes);
      if (resolvedParentVariables.isEmpty()) continue;
      Map<String, JavaBindingType> parentVariables = resolvedParentVariables.orElseThrow();
      for (JavaApiMethod method : parent.methods()) {
        if (method.kind() != JavaCallableKind.INSTANCE_METHOD) continue;
        String key = methodKey(method.name(), method.descriptor());
        if (!declared.add(key)) continue;
        method
            .binding()
            .map(callable -> substitute(callable, parentVariables))
            .filter(callable -> resolvedShapes.add(methodShape(callable)))
            .ifPresent(bindings::add);
      }
      inheritedBindings(
          parent, parentVariables, apiTypes, declared, resolvedShapes, visited, bindings);
    }
  }

  private static List<JavaReferenceType> projectedInterfaces(
      JavaApiType owner, Map<String, JavaApiType> apiTypes) {
    Map<String, JavaReferenceType> projected = new LinkedHashMap<>();
    Map<String, JavaBindingType> variables = classVariables(owner, apiTypes);
    projectedInterfaces(owner, variables, apiTypes, new java.util.HashSet<>(), projected);
    platformCollectionType(owner, variables, apiTypes, new java.util.LinkedHashSet<>())
        .filter(JarBindingSourceGenerator::iterableRelation)
        .ifPresent(type -> projected.putIfAbsent("protocol:" + type.displayName(), type));
    return List.copyOf(projected.values());
  }

  private static void projectedInterfaces(
      JavaApiType owner,
      Map<String, JavaBindingType> variables,
      Map<String, JavaApiType> apiTypes,
      Set<String> visited,
      Map<String, JavaReferenceType> projected) {
    if (!visited.add(owner.binaryName())) return;
    for (JavaClassTypeSignature relation : owner.signature().interfaces()) {
      JavaApiType interfaceType = apiTypes.get(relation.binaryName());
      if (interfaceType == null || interfaceType.kind() != JavaApiTypeKind.INTERFACE) continue;
      JavaBindingType binding = bindingType(relation, variables, apiTypes);
      if (!(binding instanceof JavaReferenceType reference)) {
        throw new IllegalArgumentException(
            "Java interface relation cannot be represented in Norm: " + relation.binaryName());
      }
      projected.putIfAbsent(reference.displayName(), reference);
    }
    if (owner.kind() == JavaApiTypeKind.INTERFACE || owner.signature().superclass().isEmpty()) {
      return;
    }
    JavaClassTypeSignature relation = owner.signature().superclass().orElseThrow();
    JavaApiType parent = apiTypes.get(relation.binaryName());
    if (parent == null) return;
    parentVariables(parent, relation, variables, apiTypes)
        .ifPresent(
            parentVariables ->
                projectedInterfaces(parent, parentVariables, apiTypes, visited, projected));
  }

  private static Map<String, JavaBindingType> classVariables(
      JavaApiType owner, Map<String, JavaApiType> apiTypes) {
    return Map.copyOf(
        JavaGenericParameterProjector.project(
                owner.signature().typeParameters(),
                Map.of(),
                (signature, variables) ->
                    bindingType(signature, new LinkedHashMap<>(variables), apiTypes))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Java generic bound cannot be represented in Norm: " + owner.binaryName()))
            .variables());
  }

  private static java.util.Optional<Map<String, JavaBindingType>> parentVariables(
      JavaApiType parent,
      JavaClassTypeSignature relation,
      Map<String, JavaBindingType> variables,
      Map<String, JavaApiType> apiTypes) {
    List<JavaTypeArgument> arguments =
        relation.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    if (arguments.size() != parent.signature().typeParameters().size()) {
      return java.util.Optional.empty();
    }
    Map<String, JavaBindingType> result = new LinkedHashMap<>();
    for (int index = 0; index < arguments.size(); index++) {
      JavaTypeArgument argument = arguments.get(index);
      if (argument.variance() != JavaTypeVariance.EXACT) {
        return java.util.Optional.empty();
      }
      JavaBindingType type = bindingType(argument.type().orElseThrow(), variables, apiTypes);
      if (type == null) {
        return java.util.Optional.empty();
      }
      result.put(parent.signature().typeParameters().get(index).name(), type);
    }
    return java.util.Optional.of(Map.copyOf(result));
  }

  private static JavaBindingType bindingType(
      JavaTypeSignature signature,
      Map<String, JavaBindingType> variables,
      Map<String, JavaApiType> apiTypes) {
    return switch (signature) {
      case JavaPrimitiveTypeSignature primitive -> primitive.type();
      case JavaTypeVariableSignature variable -> variables.get(variable.name());
      case JavaArrayTypeSignature array -> {
        JavaBindingType component = bindingType(array.component(), variables, apiTypes);
        yield component == null ? null : new JavaArrayType(component);
      }
      case JavaClassTypeSignature classType -> {
        java.util.Optional<JavaBoxedType> boxed =
            JavaBoxedType.fromBinaryName(classType.binaryName());
        if (boxed.isPresent()) yield boxed.orElseThrow();
        JavaReferenceKind kind =
            JavaPlatformTypes.referenceKind(classType.binaryName())
                .orElse(JavaReferenceKind.OPAQUE);
        if (kind == JavaReferenceKind.OPAQUE && !apiTypes.containsKey(classType.binaryName())) {
          yield null;
        }
        List<JavaBindingTypeArgument> arguments = new ArrayList<>();
        boolean supported = true;
        for (JavaClassTypeSegment segment : classType.segments()) {
          for (JavaTypeArgument argument : segment.arguments()) {
            if (argument.variance() == JavaTypeVariance.UNBOUNDED) {
              arguments.add(JavaBindingTypeArgument.unbounded());
            } else if (kind == JavaReferenceKind.CLASS
                && argument.variance() == JavaTypeVariance.EXTENDS) {
              arguments.add(JavaBindingTypeArgument.unbounded());
            } else if (argument.variance() == JavaTypeVariance.EXACT) {
              JavaBindingType type =
                  bindingType(argument.type().orElseThrow(), variables, apiTypes);
              if (type == null) {
                supported = false;
              } else {
                arguments.add(JavaBindingTypeArgument.exact(type));
              }
            } else {
              supported = false;
            }
          }
        }
        if (kind == JavaReferenceKind.CLASS
            && arguments.stream()
                .filter(argument -> argument.variance() == JavaTypeVariance.EXACT)
                .map(argument -> argument.type().orElseThrow())
                .anyMatch(type -> !JavaPlatformTypes.classTokenCompatible(type))) {
          supported = false;
        }
        yield supported ? new JavaReferenceType(classType.binaryName(), kind, arguments) : null;
      }
    };
  }

  private static JavaBindingCallable substitute(
      JavaBindingCallable callable, Map<String, JavaBindingType> variables) {
    java.util.Set<String> methodVariables =
        callable.typeParameters().stream()
            .map(JavaBindingTypeParameter::name)
            .collect(java.util.stream.Collectors.toSet());
    return new JavaBindingCallable(
        callable.owner(),
        callable.name(),
        callable.descriptor(),
        callable.kind(),
        callable.typeParameters().stream()
            .map(
                parameter ->
                    new JavaBindingTypeParameter(
                        parameter.name(),
                        parameter
                            .bound()
                            .map(type -> substitute(type, variables, methodVariables))))
            .toList(),
        callable.parameters().stream()
            .map(type -> substitute(type, variables, methodVariables))
            .toList(),
        substitute(callable.returnType(), variables, methodVariables),
        callable.returnNullability());
  }

  private static JavaBindingType substitute(
      JavaBindingType type,
      Map<String, JavaBindingType> variables,
      java.util.Set<String> methodVariables) {
    return switch (type) {
      case JavaArrayType array ->
          new JavaArrayType(substitute(array.component(), variables, methodVariables));
      case JavaPrimitiveType primitive -> primitive;
      case JavaBoxedType boxed -> boxed;
      case JavaBindingTypeVariable variable ->
          methodVariables.contains(variable.name())
              ? variable
              : variables.getOrDefault(variable.name(), variable);
      case JavaCallbackType callback ->
          new JavaCallbackType(
              callback.binaryName(),
              callback.methodName(),
              callback.parameters().stream()
                  .map(parameter -> substitute(parameter, variables, methodVariables))
                  .toList(),
              substitute(callback.returnType(), variables, methodVariables));
      case JavaReferenceType reference ->
          new JavaReferenceType(
              reference.binaryName(),
              reference.kind(),
              reference.arguments().stream()
                  .map(
                      argument ->
                          argument.variance() == JavaTypeVariance.UNBOUNDED
                              ? argument
                              : JavaBindingTypeArgument.exact(
                                  substitute(
                                      argument.type().orElseThrow(), variables, methodVariables)))
                  .toList());
    };
  }

  private static String methodKey(String name, String descriptor) {
    return name + descriptor;
  }

  private static String methodShape(JavaBindingCallable callable) {
    return callable.name()
        + callable.parameters().stream()
            .map(JavaBindingType::descriptor)
            .collect(java.util.stream.Collectors.joining(",", "(", ")"));
  }

  private static String normReturnType(JavaBindingCallable callable, NormTypes normTypes) {
    if (requiredProtocolBinding(callable)) {
      return "Iterator<"
          + normReferenceArgument((JavaReferenceType) callable.returnType(), 0, 1, normTypes)
          + ">";
    }
    return normType(
        callable.returnType(),
        normTypes,
        callable.kind() == JavaCallableKind.CONSTRUCTOR
            || callable.returnNullability() == JavaNullability.NON_NULL);
  }

  private static String functionName(String prefix, JavaBindingCallable callable) {
    return switch (callable.kind()) {
      case CONSTRUCTOR -> prefix + "New";
      case STATIC_METHOD -> prefix + upperCamel(callable.name());
      case STATIC_FIELD_GET -> prefix + "FieldGet" + fieldName(callable.name());
      case STATIC_FIELD_SET -> prefix + "FieldSet" + fieldName(callable.name());
      case ARRAY_CONSTRUCTOR, ARRAY_LENGTH, ARRAY_GET, ARRAY_SET ->
          throw new IllegalArgumentException("array binding is generated separately");
      case INSTANCE_METHOD, INSTANCE_FIELD_GET, INSTANCE_FIELD_SET ->
          throw new IllegalArgumentException("instance binding cannot generate a function");
    };
  }

  private static String enumFunctionName(String prefix, JavaBindingCallable callable) {
    return callable.kind().requiresReceiver()
        ? prefix + upperCamel(memberName(callable))
        : functionName(prefix, callable);
  }

  private static boolean enumConstant(JavaApiType owner, JavaBindingCallable callable) {
    return callable.kind() == JavaCallableKind.STATIC_FIELD_GET
        && owner.fields().stream()
            .anyMatch(
                field ->
                    field.name().equals(callable.name())
                        && (field.modifiers() & org.objectweb.asm.Opcodes.ACC_ENUM) != 0);
  }

  private static boolean requiredProtocolBinding(JavaBindingCallable callable) {
    return callable.owner().equals("java.lang.Iterable")
        && callable.name().equals("iterator")
        && callable.descriptor().equals("()Ljava/util/Iterator;");
  }

  private static Map<String, String> enumVariants(JavaApiType owner) {
    Map<String, String> variants = new LinkedHashMap<>();
    owner.fields().stream()
        .filter(field -> (field.modifiers() & org.objectweb.asm.Opcodes.ACC_ENUM) != 0)
        .forEach(
            field -> {
              String name = enumVariantName(field.name());
              if (variants.containsKey(name)) {
                name +=
                    "_"
                        + Sha256Digest.compute(field.name().getBytes(StandardCharsets.UTF_8))
                            .value()
                            .substring(0, 8);
              }
              variants.put(name, field.name());
            });
    if (variants.isEmpty()) {
      throw new IllegalArgumentException(
          "Java enum has no public constants: " + owner.binaryName());
    }
    return java.util.Collections.unmodifiableMap(variants);
  }

  private static String enumVariantName(String javaName) {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < javaName.length(); index++) {
      char value = javaName.charAt(index);
      if (value >= 'A' && value <= 'Z'
          || value >= 'a' && value <= 'z'
          || value >= '0' && value <= '9'
          || value == '_') {
        result.append(value);
      } else {
        result.append("_u").append(String.format(Locale.ROOT, "%04X", (int) value)).append('_');
      }
    }
    return result.toString();
  }

  private static String memberName(JavaBindingCallable callable) {
    return switch (callable.kind()) {
      case INSTANCE_METHOD -> normIdentifier(callable.name());
      case INSTANCE_FIELD_GET -> "fieldGet" + fieldName(callable.name());
      case INSTANCE_FIELD_SET -> "fieldSet" + fieldName(callable.name());
      case ARRAY_CONSTRUCTOR, ARRAY_LENGTH, ARRAY_GET, ARRAY_SET ->
          throw new IllegalArgumentException("array binding is generated separately");
      case CONSTRUCTOR, STATIC_METHOD, STATIC_FIELD_GET, STATIC_FIELD_SET ->
          throw new IllegalArgumentException("static binding cannot generate a member");
    };
  }

  private static String normIdentifier(String value) {
    if (LanguageSyntax.isIdentifier(value)) return value;
    StringBuilder result = new StringBuilder();
    for (int offset = 0; offset < value.length(); ) {
      int character = value.codePointAt(offset);
      boolean valid =
          result.isEmpty()
              ? character == '_' || Character.isUnicodeIdentifierStart(character)
              : Character.isUnicodeIdentifierPart(character);
      if (valid) {
        result.appendCodePoint(character);
      } else {
        result.append("_u").append(String.format(Locale.ROOT, "%04X", character)).append('_');
      }
      offset += Character.charCount(character);
    }
    String identifier = result.toString();
    return LanguageSyntax.isIdentifier(identifier) ? identifier : identifier + "Value";
  }

  private static String normType(
      JavaBindingType type, NormTypes normTypes, boolean nonNullReference) {
    return switch (type) {
      case JavaArrayType array ->
          normTypes.arrays().get(array)
              + (array.component() instanceof JavaBindingTypeVariable variable
                  ? "<" + variable.name() + ">"
                  : "")
              + (nonNullReference ? "" : "?");
      case JavaPrimitiveType primitive ->
          switch (primitive) {
            case BOOLEAN -> "Boolean";
            case BYTE, SHORT, INT -> "Integer";
            case LONG -> "Long";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case VOID -> "Void";
            case CHAR -> "CodePoint";
          };
      case JavaBoxedType boxed -> normPrimitive(boxed.primitive()) + "?";
      case JavaBindingTypeVariable variable -> variable.name() + (nonNullReference ? "" : "?");
      case JavaCallbackType callback ->
          "Function<"
              + normType(callback.returnType(), normTypes, false)
              + callback.parameters().stream()
                  .map(parameter -> normType(parameter, normTypes, false))
                  .collect(java.util.stream.Collectors.joining(", ", "(", ")"))
              + ">"
              + (nonNullReference ? "" : "?");
      case JavaReferenceType reference ->
          switch (reference.kind()) {
            case OBJECT -> "Any" + (nonNullReference ? "" : "?");
            case CLASS -> {
              String arguments =
                  reference.arguments().isEmpty()
                      ? "<?>"
                      : reference.arguments().stream()
                          .map(argument -> normClassTypeArgument(argument, normTypes))
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
              yield "Class" + arguments + (nonNullReference ? "" : "?");
            }
            case OPTIONAL -> normType(optionalElement(reference), normTypes, false);
            case OPTIONAL_INT -> "Integer?";
            case OPTIONAL_LONG -> "Long?";
            case OPTIONAL_DOUBLE -> "Double?";
            case ITERABLE ->
                "IterableView<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case ITERATOR ->
                "IteratorView<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case COLLECTION ->
                "MutableCollection<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case LIST ->
                "MutableList<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case SET ->
                "MutableSet<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case MAP ->
                "MutableMap<"
                    + normReferenceArgument(reference, 0, 2, normTypes)
                    + ", "
                    + normReferenceArgument(reference, 1, 2, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case STRING -> "String" + (nonNullReference ? "" : "?");
            case UNIT -> "Unit" + (nonNullReference ? "" : "?");
            case CHAR_SEQUENCE -> "String" + (nonNullReference ? "" : "?");
            case CHARSET -> "String" + (nonNullReference ? "" : "?");
            case NUMBER -> "Number" + (nonNullReference ? "" : "?");
            case EXCEPTION -> "Exception" + (nonNullReference ? "" : "?");
            case INPUT_STREAM -> "InputStream" + (nonNullReference ? "" : "?");
            case OUTPUT_STREAM -> "OutputStream" + (nonNullReference ? "" : "?");
            case TASK ->
                "Task<"
                    + normType(referenceElement(reference), normTypes, false)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case PUBLISHER ->
                "Publisher<"
                    + normType(referenceElement(reference), normTypes, false)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case DURATION -> "Duration" + (nonNullReference ? "" : "?");
            case URI -> "Uri" + (nonNullReference ? "" : "?");
            case PATH, FILE -> "Path" + (nonNullReference ? "" : "?");
            case ENUM, OPAQUE, RESOURCE -> {
              String mapped = normTypes.references().get(reference.binaryName());
              if (mapped == null) {
                throw new IllegalArgumentException(
                    "Java type is not exported by this Module: " + reference.binaryName());
              }
              String arguments =
                  reference.arguments().isEmpty()
                      ? ""
                      : reference.arguments().stream()
                          .map(argument -> normTypeArgument(argument, normTypes))
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
              yield mapped + arguments + (nonNullReference ? "" : "?");
            }
          };
    };
  }

  private static String normPrimitive(JavaPrimitiveType primitive) {
    return switch (primitive) {
      case BOOLEAN -> "Boolean";
      case BYTE, SHORT, INT -> "Integer";
      case LONG -> "Long";
      case FLOAT -> "Float";
      case DOUBLE -> "Double";
      case CHAR -> "CodePoint";
      case VOID -> throw new IllegalArgumentException("Void cannot be boxed");
    };
  }

  private static JavaBindingType optionalElement(JavaReferenceType optional) {
    return referenceElement(optional);
  }

  private static JavaBindingType referenceElement(JavaReferenceType reference) {
    return referenceArgument(reference, 0, 1);
  }

  private static JavaBindingType referenceArgument(
      JavaReferenceType reference, int index, int arity) {
    if (reference.arguments().isEmpty()) {
      return new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    }
    if (reference.arguments().size() != arity) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    JavaBindingTypeArgument argument = reference.arguments().get(index);
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) {
      return new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    }
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    return argument.type().orElseThrow();
  }

  private static String normReferenceArgument(
      JavaReferenceType reference, int index, int arity, NormTypes normTypes) {
    if (reference.arguments().isEmpty()) {
      return normType(
          new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT), normTypes, false);
    }
    if (reference.arguments().size() != arity) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    JavaBindingTypeArgument argument = reference.arguments().get(index);
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    return normType(argument.type().orElseThrow(), normTypes, false);
  }

  private static String normTypeArgument(JavaBindingTypeArgument argument, NormTypes normTypes) {
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException("bounded Java wildcard cannot be represented in Norm");
    }
    return normType(argument.type().orElseThrow(), normTypes, false);
  }

  private static String normClassTypeArgument(
      JavaBindingTypeArgument argument, NormTypes normTypes) {
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException("bounded Java wildcard cannot be represented in Norm");
    }
    return normType(argument.type().orElseThrow(), normTypes, true);
  }

  private static List<JavaBindingTypeParameter> classTypeParameters(
      JavaApiType owner, Map<String, JavaApiType> apiTypes) {
    return JavaGenericParameterProjector.project(
            owner.signature().typeParameters(),
            Map.of(),
            (signature, variables) ->
                bindingType(signature, new LinkedHashMap<>(variables), apiTypes))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Java generic bound cannot be represented in Norm: " + owner.binaryName()))
        .parameters();
  }

  private static List<JavaBindingTypeParameter> constructorTypeParameters(
      List<JavaBindingTypeParameter> ownerTypeParameters, JavaBindingCallable callable) {
    return java.util.stream.Stream.concat(
            ownerTypeParameters.stream(), callable.typeParameters().stream())
        .toList();
  }

  private static void appendTypeParameters(
      StringBuilder text, List<JavaBindingTypeParameter> parameters, NormTypes normTypes) {
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

  private static String normBoundType(JavaBindingType type, NormTypes normTypes) {
    if (type instanceof JavaBindingTypeVariable variable) return variable.name();
    if (JavaGenericParameterProjector.isException(type)) return "Exception";
    if (!(type instanceof JavaReferenceType reference)) {
      throw new IllegalArgumentException(
          "Java generic bound cannot be represented in Norm: " + type.displayName());
    }
    if (reference.kind() == JavaReferenceKind.RESOURCE
        && (reference.binaryName().equals("java.lang.AutoCloseable")
            || reference.binaryName().equals("java.io.Closeable"))) {
      return "Resource";
    }
    if (!JavaGenericParameterProjector.isComparable(reference)) {
      if (!reference.arguments().isEmpty()
          && (reference.kind() == JavaReferenceKind.OPAQUE
              || reference.kind() == JavaReferenceKind.RESOURCE)) {
        String mapped = normTypes.references().get(reference.binaryName());
        if (mapped == null) {
          throw new IllegalArgumentException(
              "Java generic bound is not exported by this Module: " + type.displayName());
        }
        return mapped
            + reference.arguments().stream()
                .map(argument -> normClassTypeArgument(argument, normTypes))
                .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
      }
      return normType(reference, normTypes, true);
    }
    if (reference.arguments().size() != 1) {
      throw new IllegalArgumentException(
          "Java generic bound cannot be represented in Norm: " + type.displayName());
    }
    JavaBindingType argument = reference.arguments().getFirst().type().orElseThrow();
    return "Comparable<" + normType(argument, normTypes, true) + ">";
  }

  private static List<JavaBindingType> bounds(
      List<JavaBindingTypeParameter> ownerParameters, List<JavaBindingCallable> bindings) {
    return java.util.stream.Stream.concat(
            ownerParameters.stream(),
            bindings.stream().flatMap(callable -> callable.typeParameters().stream()))
        .map(JavaBindingTypeParameter::bound)
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  private static boolean containsException(JavaBindingCallable callable) {
    return callable.parameters().stream().anyMatch(JarBindingSourceGenerator::containsException)
        || containsException(callable.returnType());
  }

  private static boolean containsException(JavaBindingType type) {
    return containsReferenceKind(type, JavaReferenceKind.EXCEPTION);
  }

  private static boolean containsPath(JavaBindingCallable callable) {
    return callable.parameters().stream().anyMatch(JarBindingSourceGenerator::containsPath)
        || containsPath(callable.returnType());
  }

  private static boolean containsPath(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType array -> containsPath(array.component());
      case JavaBindingTypeVariable variable -> containsPath(variable.erasure());
      case JavaCallbackType callback ->
          callback.parameters().stream().anyMatch(JarBindingSourceGenerator::containsPath)
              || containsPath(callback.returnType());
      case JavaReferenceType reference ->
          reference.kind() == JavaReferenceKind.PATH || reference.kind() == JavaReferenceKind.FILE;
      case JavaBoxedType ignored -> false;
      case JavaPrimitiveType ignored -> false;
    };
  }

  private static boolean containsReferenceKind(
      JavaBindingCallable callable, JavaReferenceKind kind) {
    return callable.parameters().stream().anyMatch(type -> containsReferenceKind(type, kind))
        || containsReferenceKind(callable.returnType(), kind);
  }

  private static boolean containsReferenceKind(JavaBindingType type, JavaReferenceKind kind) {
    return switch (type) {
      case JavaArrayType array -> containsReferenceKind(array.component(), kind);
      case JavaBindingTypeVariable variable -> containsReferenceKind(variable.erasure(), kind);
      case JavaCallbackType callback ->
          callback.parameters().stream()
                  .anyMatch(parameter -> containsReferenceKind(parameter, kind))
              || containsReferenceKind(callback.returnType(), kind);
      case JavaReferenceType reference ->
          reference.kind() == kind
              || reference.arguments().stream()
                  .flatMap(argument -> argument.type().stream())
                  .anyMatch(argument -> containsReferenceKind(argument, kind));
      case JavaBoxedType ignored -> false;
      case JavaPrimitiveType ignored -> false;
    };
  }

  private static Set<String> resourceTypes(Map<String, JavaApiType> apiTypes) {
    return apiTypes.keySet().stream()
        .filter(name -> isResourceType(name, apiTypes, new java.util.HashSet<>()))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static boolean isResourceType(
      String name, Map<String, JavaApiType> apiTypes, Set<String> visited) {
    if (name.equals("java.lang.AutoCloseable") || name.equals("java.io.Closeable")) return true;
    if (!visited.add(name)) return false;
    JavaApiType type = apiTypes.get(name);
    if (type == null) return false;
    return java.util.stream.Stream.concat(
            type.signature().superclass().stream(), type.signature().interfaces().stream())
        .map(JavaClassTypeSignature::binaryName)
        .anyMatch(parent -> isResourceType(parent, apiTypes, visited));
  }

  private static JavaBindingCallable markResources(
      JavaBindingCallable callable, Set<String> resources) {
    return new JavaBindingCallable(
        callable.owner(),
        callable.name(),
        callable.descriptor(),
        callable.kind(),
        callable.typeParameters().stream()
            .map(
                parameter ->
                    new JavaBindingTypeParameter(
                        parameter.name(),
                        parameter.bound().map(type -> markResources(type, resources))))
            .toList(),
        callable.parameters().stream().map(type -> markResources(type, resources)).toList(),
        markResources(callable.returnType(), resources),
        callable.returnNullability());
  }

  private static JavaBindingType markResources(JavaBindingType type, Set<String> resources) {
    return switch (type) {
      case JavaArrayType array -> new JavaArrayType(markResources(array.component(), resources));
      case JavaBindingTypeVariable variable ->
          new JavaBindingTypeVariable(
              variable.name(), markResources(variable.erasure(), resources));
      case JavaCallbackType callback ->
          new JavaCallbackType(
              callback.binaryName(),
              callback.methodName(),
              callback.parameters().stream()
                  .map(parameter -> markResources(parameter, resources))
                  .toList(),
              markResources(callback.returnType(), resources));
      case JavaReferenceType reference ->
          new JavaReferenceType(
              reference.binaryName(),
              resources.contains(reference.binaryName())
                  ? JavaReferenceKind.RESOURCE
                  : reference.kind(),
              reference.arguments().stream()
                  .map(
                      argument ->
                          argument.type().isEmpty()
                              ? argument
                              : new JavaBindingTypeArgument(
                                  argument.variance(),
                                  Optional.of(
                                      markResources(argument.type().orElseThrow(), resources))))
                  .toList());
      case JavaBoxedType boxed -> boxed;
      case JavaPrimitiveType primitive -> primitive;
    };
  }

  private static String genericTypeDeclaration(JavaBindingTypeVariable variable) {
    if (JavaGenericParameterProjector.isComparable(variable.erasure())) {
      return "<T extends Comparable<T>>";
    }
    if (JavaGenericParameterProjector.isException(variable.erasure())) {
      return "<T extends Exception>";
    }
    return "<T>";
  }

  private static String callId(Sha256Digest graphId, JavaBindingCallable callable) {
    String exposedSignature =
        callable.typeParameters().stream()
                .map(
                    parameter ->
                        parameter.name()
                            + parameter.bound().map(bound -> ":" + bound.displayName()).orElse(""))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"))
            + callable.parameters().stream()
                .map(JavaBindingType::displayName)
                .collect(java.util.stream.Collectors.joining(",", "(", ")"))
            + callable.returnType().displayName()
            + ":"
            + callable.returnNullability().name();
    String exposedId =
        Sha256Digest.compute(exposedSignature.getBytes(StandardCharsets.UTF_8)).value();
    return BINDING_ABI
        + ":"
        + graphId.value()
        + ":"
        + exposedId
        + ":"
        + callable.kind().name().toLowerCase(Locale.ROOT)
        + ":"
        + callable.owner()
        + ":"
        + callable.name()
        + ":"
        + callable.descriptor();
  }

  private static String exportPath(String selectedName, JavaApiType owner) {
    if (owner.enclosingType().isEmpty()) return selectedName;
    String binaryName = owner.binaryName();
    String localName = binaryName.substring(binaryName.lastIndexOf('.') + 1).replace('$', '.');
    if (!selectedName.endsWith(localName)) return selectedName;
    return selectedName.substring(0, selectedName.length() - localName.length())
        + localName.replace(".", "");
  }

  private static String exportPackage(String exportedName) {
    int separator = exportedName.lastIndexOf('.');
    return separator < 0 ? "" : "." + exportedName.substring(0, separator);
  }

  private static String simpleName(String value) {
    int separator = value.lastIndexOf('.');
    return separator < 0 ? value : value.substring(separator + 1);
  }

  private static String lowerCamel(String value) {
    int uppercasePrefix = 0;
    while (uppercasePrefix < value.length()
        && Character.isUpperCase(value.charAt(uppercasePrefix))) {
      uppercasePrefix++;
    }
    int lowercaseLength =
        uppercasePrefix == value.length() ? uppercasePrefix : Math.max(1, uppercasePrefix - 1);
    return value.substring(0, lowercaseLength).toLowerCase(Locale.ROOT)
        + value.substring(lowercaseLength);
  }

  private static String upperCamel(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String fieldName(String value) {
    if (!value.contains("_")) {
      return value.equals(value.toUpperCase(Locale.ROOT))
          ? upperCamel(value.toLowerCase(Locale.ROOT))
          : upperCamel(value);
    }
    return java.util.Arrays.stream(value.split("_+"))
        .filter(part -> !part.isEmpty())
        .map(part -> upperCamel(part.toLowerCase(Locale.ROOT)))
        .collect(java.util.stream.Collectors.joining());
  }

  private record BindingSelection(
      String name, String exportName, Optional<MemberSelection> members) {
    private BindingSelection {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(exportName, "exportName");
      Objects.requireNonNull(members, "members");
    }

    private static BindingSelection allMembers(String name, String exportName) {
      return new BindingSelection(name, exportName, Optional.empty());
    }

    private static BindingSelection declaredMembers(JarBindingType type, String exportName) {
      return new BindingSelection(
          type.name(),
          exportName,
          Optional.of(
              new MemberSelection(Set.copyOf(type.members()), Set.copyOf(type.overloads()))));
    }
  }

  private record MemberSelection(Set<String> groups, Set<JarBindingOverload> overloads) {
    private MemberSelection {
      groups = Set.copyOf(groups);
      overloads = Set.copyOf(overloads);
    }

    private static MemberSelection none() {
      return new MemberSelection(Set.of(), Set.of());
    }

    private boolean matches(JavaBindingCallable callable) {
      return groups.contains(bindingMemberName(callable))
          || overloads.stream()
              .anyMatch(overload -> JarBindingSourceGenerator.matches(callable, overload));
    }
  }

  private record NormTypes(
      Map<String, String> references,
      Map<String, String> referencePaths,
      Map<JavaArrayType, String> arrays,
      Map<String, Map<String, String>> enumVariants,
      Map<String, Integer> typeParameterCounts) {
    private NormTypes {
      references = Map.copyOf(references);
      referencePaths = Map.copyOf(referencePaths);
      arrays = Map.copyOf(arrays);
      typeParameterCounts = Map.copyOf(typeParameterCounts);
      enumVariants =
          enumVariants.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    }
  }
}
