package dev.w0fv1.norm.jvm;

import static dev.w0fv1.norm.jvm.BindingNames.addEnumSignature;
import static dev.w0fv1.norm.jvm.BindingNames.addSignature;
import static dev.w0fv1.norm.jvm.BindingNames.allocateEnumNames;
import static dev.w0fv1.norm.jvm.BindingNames.allocateNames;
import static dev.w0fv1.norm.jvm.BindingNames.allocateTypePath;
import static dev.w0fv1.norm.jvm.BindingNames.annotationElementNames;
import static dev.w0fv1.norm.jvm.BindingNames.callId;
import static dev.w0fv1.norm.jvm.BindingNames.enumVariants;
import static dev.w0fv1.norm.jvm.BindingNames.exportPackage;
import static dev.w0fv1.norm.jvm.BindingNames.exportPath;
import static dev.w0fv1.norm.jvm.BindingNames.functionName;
import static dev.w0fv1.norm.jvm.BindingNames.lowerCamel;
import static dev.w0fv1.norm.jvm.BindingNames.simpleName;
import static dev.w0fv1.norm.jvm.BindingTypeNames.allocateArrayNames;
import static dev.w0fv1.norm.jvm.BindingTypeNames.collectArrays;
import static dev.w0fv1.norm.jvm.BindingTypeNames.collectReferences;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.arrayConstructor;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.arrayGet;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.arrayLength;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.arraySet;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.arraySupportType;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.constructorTypeParameters;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.enumConstant;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.markResources;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.methodKey;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.requiredProtocolBinding;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.Type;

public final class BindingPlanner {
  public BindingPlan plan(
      ModuleCoordinate module, List<String> exports, Sha256Digest graphId, JarApiSchema schema) {
    return planSelected(
        module,
        exports.stream().map(name -> BindingSelection.allMembers(name, name)).toList(),
        graphId,
        schema);
  }

  public BindingPlan planSurface(
      ModuleCoordinate module,
      List<JarBindingType> api,
      Sha256Digest graphId,
      JarApiSchema schema) {
    return planSurface(
        module, api.stream().map(JarBindingType::name).toList(), api, graphId, schema);
  }

  public BindingPlan planSurface(
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
    return planSelected(module, selections, graphId, schema);
  }

  private BindingPlan planSelected(
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
    JavaBindingMembers javaMembers = new JavaBindingMembers(apiTypes);
    Set<String> resourceTypes = javaMembers.resourceTypes();
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
      List<JavaBindingTypeParameter> typeParameters = javaMembers.classTypeParameters(owner);
      List<JavaBindingCallable> availableBindings = javaMembers.bindings(owner);
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
      List<JavaReferenceType> interfaces = javaMembers.projectedInterfaces(owner);
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
    BindingTypeNames normTypes =
        new BindingTypeNames(
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
    Map<String, JavaBindingCallable> calls = new LinkedHashMap<>();
    List<BindingPlan.Declaration> declarations = new ArrayList<>();
    exportedTypes.forEach(
        (exportedName, owner) -> {
          List<JavaBindingCallable> ownerBindings = exportedBindings.get(exportedName);
          List<BindingPlan.Call> functions = new ArrayList<>();
          List<BindingPlan.Call> members = new ArrayList<>();
          Map<JavaAnnotationElementBinding, String> annotationNames = Map.of();
          if (owner.kind() == JavaApiTypeKind.ANNOTATION) {
            JavaAnnotationBinding annotation = annotationBindings.get(exportedName);
            if (annotation.contract().normTargetInterfaces().isEmpty())
              throw new IllegalArgumentException(
                  "Java annotation has no Norm declaration target: " + owner.binaryName());
            annotationNames = annotationElementNames(annotation.elements());
          } else {
            boolean javaEnum = owner.kind() == JavaApiTypeKind.ENUM;
            String prefix = lowerCamel(simpleName(exportedName));
            List<JavaBindingCallable> functionBindings =
                ownerBindings.stream()
                    .filter(callable -> javaEnum || !callable.kind().requiresReceiver())
                    .filter(callable -> !javaEnum || !enumConstant(owner, callable))
                    .toList();
            Map<JavaBindingCallable, String> functionNames =
                javaEnum
                    ? allocateEnumNames(functionBindings, prefix, normTypes)
                    : allocateNames(
                        functionBindings, callable -> functionName(prefix, callable), normTypes);
            Map<String, JavaBindingCallable> signatures = new LinkedHashMap<>();
            for (JavaBindingCallable callable : functionBindings) {
              String name = functionNames.get(callable);
              if (javaEnum) addEnumSignature(signatures, name, callable, normTypes);
              else addSignature(signatures, name, callable, normTypes);
              String id = callId(graphId, callable);
              registerCall(calls, id, callable);
              functions.add(
                  new BindingPlan.Call(
                      name,
                      id,
                      callable,
                      callable.kind() == JavaCallableKind.CONSTRUCTOR
                          ? constructorTypeParameters(
                              exportedTypeParameters.get(exportedName), callable)
                          : callable.typeParameters()));
            }
            if (!javaEnum) {
              List<JavaBindingCallable> memberBindings =
                  ownerBindings.stream()
                      .filter(callable -> callable.kind().requiresReceiver())
                      .toList();
              Map<JavaBindingCallable, String> memberNames =
                  allocateNames(memberBindings, BindingNames::memberName, normTypes);
              signatures.clear();
              for (JavaBindingCallable callable : memberBindings) {
                String name = memberNames.get(callable);
                addSignature(signatures, name, callable, normTypes);
                String id = callId(graphId, callable);
                registerCall(calls, id, callable);
                members.add(new BindingPlan.Call(name, id, callable, callable.typeParameters()));
              }
            }
          }
          declarations.add(
              new BindingPlan.Declaration(
                  exportedName,
                  owner.binaryName(),
                  owner.kind(),
                  exportedTypeParameters.get(exportedName),
                  ownerBindings,
                  exportedInterfaces.get(exportedName),
                  resourceTypes.contains(owner.binaryName()),
                  enumVariants.getOrDefault(owner.binaryName(), Map.of()),
                  Optional.ofNullable(annotationBindings.get(exportedName)),
                  annotationNames,
                  functions,
                  members));
        });
    List<BindingPlan.Array> plannedArrays = new ArrayList<>();
    Set<String> arrayClasses = new java.util.LinkedHashSet<>();
    for (JavaArrayType exposedArray : arrays) {
      String name = normTypes.arrays().get(exposedArray);
      if (!arrayClasses.add(name)) continue;
      JavaArrayType array = arraySupportType(exposedArray);
      List<BindingPlan.Call> arrayCalls = new ArrayList<>();
      for (JavaBindingCallable callable :
          List.of(arrayConstructor(array), arrayLength(array), arrayGet(array), arraySet(array))) {
        String id = callId(graphId, callable);
        registerCall(calls, id, callable);
        arrayCalls.add(
            new BindingPlan.Call(callable.name(), id, callable, callable.typeParameters()));
      }
      plannedArrays.add(
          new BindingPlan.Array(
              name,
              array,
              arrayCalls.get(0),
              arrayCalls.get(1),
              arrayCalls.get(2),
              arrayCalls.get(3)));
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
    return new BindingPlan(
        module,
        rootExports,
        declarations,
        plannedArrays,
        normTypes,
        calls,
        classDescriptors,
        enumConstants,
        annotations);
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
        .map(BindingPlanner::normalizeJavaTypeName)
        .toList();
  }

  private static List<String> normalizedParameters(JarBindingOverload overload) {
    return overload.parameterTypes().stream().map(BindingPlanner::normalizeJavaTypeName).toList();
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

  private static void registerCall(
      Map<String, JavaBindingCallable> calls, String callId, JavaBindingCallable callable) {
    JavaBindingCallable existing = calls.putIfAbsent(callId, callable);
    if (existing != null && !existing.equals(callable)) {
      throw new IllegalArgumentException("conflicting Java binding call " + callId);
    }
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
          || overloads.stream().anyMatch(overload -> BindingPlanner.matches(callable, overload));
    }
  }
}
