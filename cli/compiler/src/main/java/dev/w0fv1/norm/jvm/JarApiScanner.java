package dev.w0fv1.norm.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

public final class JarApiScanner {
  private static final JavaGenericSignatureParser SIGNATURES = new JavaGenericSignatureParser();

  public JarApiSchema scan(ResolvedJarGraph graph) throws IOException {
    Map<String, RawClass> rootClasses = readClasses(graph.root());
    Map<String, RawClass> classes = readClasses(graph.artifacts());
    Map<String, JavaReferenceKind> rootTypes = new LinkedHashMap<>();
    rootClasses.forEach(
        (name, type) ->
            rootTypes.put(
                name,
                kind(type.access()) == JavaApiTypeKind.ENUM
                    ? JavaReferenceKind.ENUM
                    : JavaReferenceKind.OPAQUE));
    Map<String, RawSam> samTypes = samTypes(classes);
    List<JavaApiType> types =
        rootClasses.values().stream()
            .filter(owner -> publiclyAccessible(owner, classes))
            .filter(owner -> !isSynthetic(owner.access()))
            .map(owner -> apiType(owner, classes, rootTypes, samTypes))
            .toList();
    return new JarApiSchema(types);
  }

  private static JavaApiType apiType(
      RawClass owner,
      Map<String, RawClass> classes,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes) {
    boolean deprecated = deprecated(owner.access(), owner.annotations());
    List<JavaApiField> fields =
        owner.fields().stream()
            .filter(field -> isPublic(field.access()))
            .filter(field -> !isSynthetic(field.access()))
            .map(field -> apiField(owner, field, rootTypes, samTypes, deprecated))
            .toList();
    List<EffectiveMethod> effectiveMethods = effectiveMethods(owner, classes);
    Set<String> declaredMethodKeys =
        owner.methods().stream()
            .filter(method -> isPublic(method.access()))
            .map(JarApiScanner::methodKey)
            .collect(java.util.stream.Collectors.toSet());
    List<JavaApiMethod> methods =
        effectiveMethods.stream()
            .filter(method -> declaredMethodKeys.contains(methodKey(method.declaration())))
            .filter(method -> isPublic(method.declaration().access()))
            .filter(method -> !isSynthetic(method.declaration().access()))
            .filter(method -> !method.declaration().name().equals("<clinit>"))
            .map(method -> apiMethod(owner, method, rootTypes, samTypes, deprecated))
            .toList();
    List<JavaApiMethod> inheritedMethods =
        effectiveMethods.stream()
            .filter(method -> !declaredMethodKeys.contains(methodKey(method.declaration())))
            .map(method -> apiMethod(owner, method, rootTypes, samTypes, deprecated))
            .toList();
    List<JavaApiRecordComponent> recordComponents =
        owner.recordComponents().stream()
            .map(
                component ->
                    new JavaApiRecordComponent(
                        owner.binaryName(),
                        component.name(),
                        component.descriptor(),
                        SIGNATURES.parseType(
                            component.signature() == null
                                ? component.descriptor()
                                : component.signature()),
                        component.annotations(),
                        component.typeAnnotations()))
            .toList();
    return new JavaApiType(
        owner.binaryName(),
        kind(owner.access()),
        owner.access(),
        publicSignature(owner, classes),
        owner.annotations(),
        owner.typeAnnotations(),
        Optional.ofNullable(binaryName(owner.enclosingType())),
        recordComponents,
        owner.permittedSubclasses().stream().map(JarApiScanner::binaryName).toList(),
        fields,
        methods,
        inheritedMethods,
        deprecated ? JavaApiDisposition.EXCLUDED_DEPRECATED : JavaApiDisposition.BINDABLE);
  }

  private static JavaClassSignature publicSignature(RawClass owner, Map<String, RawClass> classes) {
    JavaClassSignature declared = classSignature(owner);
    Map<String, JavaClassTypeSignature> interfaces = new LinkedHashMap<>();
    Map<String, JavaTypeSignature> variables = new LinkedHashMap<>();
    declared
        .typeParameters()
        .forEach(
            parameter ->
                variables.put(parameter.name(), new JavaTypeVariableSignature(parameter.name())));
    for (JavaClassTypeSignature relation : declared.interfaces()) {
      collectPublicInterface(relation, variables, classes, new java.util.HashSet<>(), interfaces);
    }
    declared
        .superclass()
        .ifPresent(
            relation ->
                collectInheritedInterfaces(
                    relation, variables, classes, new java.util.HashSet<>(), interfaces));
    return new JavaClassSignature(
        declared.typeParameters(), declared.superclass(), List.copyOf(interfaces.values()));
  }

  private static List<EffectiveMethod> effectiveMethods(
      RawClass owner, Map<String, RawClass> classes) {
    Map<String, EffectiveMethod> methods = new LinkedHashMap<>();
    owner.methods().stream()
        .filter(method -> isPublic(method.access()))
        .filter(method -> !isSynthetic(method.access()))
        .filter(method -> !method.name().equals("<clinit>"))
        .forEach(
            method ->
                methods.put(
                    methodKey(method),
                    new EffectiveMethod(method, methodSignature(method), owner.binaryName())));
    owner.methods().stream()
        .filter(method -> isPublic(method.access()))
        .filter(method -> isSynthetic(method.access()))
        .filter(method -> !method.name().equals("<clinit>"))
        .forEach(
            method ->
                methods.putIfAbsent(
                    methodKey(method),
                    new EffectiveMethod(method, methodSignature(method), owner.binaryName())));
    JavaClassSignature signature = classSignature(owner);
    Map<String, JavaTypeSignature> variables = new LinkedHashMap<>();
    signature
        .typeParameters()
        .forEach(
            parameter ->
                variables.put(parameter.name(), new JavaTypeVariableSignature(parameter.name())));
    Set<String> visited = new java.util.HashSet<>();
    signature
        .superclass()
        .ifPresent(
            relation ->
                collectInheritedMethods(
                    owner.binaryName(), relation, variables, classes, visited, methods));
    for (JavaClassTypeSignature relation : signature.interfaces()) {
      collectInheritedMethods(owner.binaryName(), relation, variables, classes, visited, methods);
    }
    return List.copyOf(methods.values());
  }

  private static void collectInheritedMethods(
      String surfaceOwner,
      JavaClassTypeSignature relation,
      Map<String, JavaTypeSignature> variables,
      Map<String, RawClass> classes,
      Set<String> visited,
      Map<String, EffectiveMethod> methods) {
    JavaClassTypeSignature resolved = substitute(relation, variables);
    RawClass parent = classes.get(resolved.binaryName());
    if (parent == null || !visited.add(resolved.toString())) return;
    JavaClassSignature signature = classSignature(parent);
    Map<String, JavaTypeSignature> parentVariables = relationVariables(signature, resolved);
    String invocationOwner =
        publiclyAccessible(parent, classes) ? parent.binaryName() : surfaceOwner;
    parent.methods().stream()
        .filter(method -> isPublic(method.access()))
        .filter(method -> (method.access() & Opcodes.ACC_STATIC) == 0)
        .filter(method -> !isSynthetic(method.access()))
        .filter(method -> !method.name().startsWith("<"))
        .forEach(
            method ->
                methods.putIfAbsent(
                    methodKey(method),
                    new EffectiveMethod(
                        method,
                        substitute(methodSignature(method), parentVariables),
                        invocationOwner)));
    signature
        .superclass()
        .ifPresent(
            candidate ->
                collectInheritedMethods(
                    surfaceOwner, candidate, parentVariables, classes, visited, methods));
    for (JavaClassTypeSignature candidate : signature.interfaces()) {
      collectInheritedMethods(surfaceOwner, candidate, parentVariables, classes, visited, methods);
    }
  }

  private static JavaMethodSignature methodSignature(RawMethod method) {
    return SIGNATURES.parseMethod(
        method.signature() == null ? method.descriptor() : method.signature());
  }

  private static JavaMethodSignature substitute(
      JavaMethodSignature signature, Map<String, JavaTypeSignature> variables) {
    Map<String, JavaTypeSignature> visible = new LinkedHashMap<>(variables);
    signature.typeParameters().forEach(parameter -> visible.remove(parameter.name()));
    List<JavaTypeParameter> typeParameters =
        signature.typeParameters().stream()
            .map(
                parameter ->
                    new JavaTypeParameter(
                        parameter.name(),
                        parameter.classBound().map(bound -> substitute(bound, visible)),
                        parameter.interfaceBounds().stream()
                            .map(bound -> substitute(bound, visible))
                            .toList()))
            .toList();
    return new JavaMethodSignature(
        typeParameters,
        signature.parameters().stream().map(type -> substitute(type, visible)).toList(),
        substitute(signature.returnType(), visible),
        signature.exceptions().stream().map(type -> substitute(type, visible)).toList());
  }

  private static void collectPublicInterface(
      JavaClassTypeSignature relation,
      Map<String, JavaTypeSignature> variables,
      Map<String, RawClass> classes,
      Set<String> visited,
      Map<String, JavaClassTypeSignature> interfaces) {
    JavaClassTypeSignature resolved = substitute(relation, variables);
    RawClass type = classes.get(resolved.binaryName());
    if (type == null || publiclyAccessible(type, classes)) {
      interfaces.putIfAbsent(resolved.binaryName(), resolved);
      return;
    }
    collectInheritedInterfaces(resolved, Map.of(), classes, visited, interfaces);
  }

  private static void collectInheritedInterfaces(
      JavaClassTypeSignature relation,
      Map<String, JavaTypeSignature> variables,
      Map<String, RawClass> classes,
      Set<String> visited,
      Map<String, JavaClassTypeSignature> interfaces) {
    JavaClassTypeSignature resolved = substitute(relation, variables);
    RawClass type = classes.get(resolved.binaryName());
    if (type == null || !visited.add(type.binaryName())) return;
    JavaClassSignature signature = classSignature(type);
    Map<String, JavaTypeSignature> parentVariables = relationVariables(signature, resolved);
    for (JavaClassTypeSignature candidate : signature.interfaces()) {
      collectPublicInterface(candidate, parentVariables, classes, visited, interfaces);
    }
    signature
        .superclass()
        .ifPresent(
            candidate ->
                collectInheritedInterfaces(
                    candidate, parentVariables, classes, visited, interfaces));
  }

  private static Map<String, JavaTypeSignature> relationVariables(
      JavaClassSignature signature, JavaClassTypeSignature relation) {
    List<JavaTypeArgument> arguments =
        relation.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    if (arguments.size() != signature.typeParameters().size()) return Map.of();
    Map<String, JavaTypeSignature> variables = new LinkedHashMap<>();
    for (int index = 0; index < arguments.size(); index++) {
      JavaTypeArgument argument = arguments.get(index);
      if (argument.variance() != JavaTypeVariance.EXACT) return Map.of();
      variables.put(signature.typeParameters().get(index).name(), argument.type().orElseThrow());
    }
    return variables;
  }

  private static JavaClassTypeSignature substitute(
      JavaClassTypeSignature type, Map<String, JavaTypeSignature> variables) {
    return (JavaClassTypeSignature) substitute((JavaTypeSignature) type, variables);
  }

  private static JavaTypeSignature substitute(
      JavaTypeSignature type, Map<String, JavaTypeSignature> variables) {
    return switch (type) {
      case JavaArrayTypeSignature array ->
          new JavaArrayTypeSignature(substitute(array.component(), variables));
      case JavaClassTypeSignature classType ->
          new JavaClassTypeSignature(
              classType.segments().stream()
                  .map(
                      segment ->
                          new JavaClassTypeSegment(
                              segment.name(),
                              segment.arguments().stream()
                                  .map(
                                      argument ->
                                          argument.variance() == JavaTypeVariance.UNBOUNDED
                                              ? argument
                                              : JavaTypeArgument.of(
                                                  argument.variance(),
                                                  substitute(
                                                      argument.type().orElseThrow(), variables)))
                                  .toList()))
                  .toList());
      case JavaPrimitiveTypeSignature primitive -> primitive;
      case JavaTypeVariableSignature variable -> variables.getOrDefault(variable.name(), variable);
    };
  }

  private static JavaApiField apiField(
      RawClass owner,
      RawField field,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes,
      boolean ownerDeprecated) {
    boolean deprecated = ownerDeprecated || deprecated(field.access(), field.annotations());
    JavaApiDisposition disposition;
    Optional<JavaApiIssue> issue;
    List<JavaBindingCallable> bindings;
    JavaTypeSignature declaredType =
        SIGNATURES.parseType(field.signature() == null ? field.descriptor() : field.signature());
    if (deprecated) {
      disposition = JavaApiDisposition.EXCLUDED_DEPRECATED;
      issue = Optional.empty();
      bindings = List.of();
    } else {
      JavaBindingType type;
      if (field.signature() == null) {
        type = bindingType(Type.getType(field.descriptor()), rootTypes, samTypes);
      } else {
        Map<String, JavaBindingTypeVariable> variables = new LinkedHashMap<>();
        Optional<JavaGenericParameterProjector.Projection> projection =
            addVariables(variables, classSignature(owner).typeParameters(), rootTypes, samTypes);
        type =
            projection
                .map(
                    value ->
                        bindingType(
                            declaredType,
                            new LinkedHashMap<>(value.variables()),
                            rootTypes,
                            samTypes))
                .orElse(null);
      }
      if (type == null || !exposableValue(type)) {
        disposition = JavaApiDisposition.UNSUPPORTED;
        issue =
            Optional.of(
                new JavaApiIssue(
                    field.signature() == null
                        ? JavaApiIssueCode.UNSUPPORTED_TYPE
                        : JavaApiIssueCode.GENERIC_MAPPING,
                    field.signature() == null
                        ? "unsupported Java type " + Type.getType(field.descriptor()).getClassName()
                        : "Java generic field type cannot be represented in Norm"));
        bindings = List.of();
      } else {
        disposition = JavaApiDisposition.BINDABLE;
        issue = Optional.empty();
        bindings = fieldBindings(owner, field, type);
      }
    }
    return new JavaApiField(
        owner.binaryName(),
        field.name(),
        field.descriptor(),
        declaredType,
        field.access(),
        Optional.ofNullable(field.value()),
        field.annotations(),
        field.typeAnnotations(),
        disposition,
        issue,
        bindings);
  }

  private static List<JavaBindingCallable> fieldBindings(
      RawClass owner, RawField field, JavaBindingType type) {
    boolean isStatic = (field.access() & Opcodes.ACC_STATIC) != 0;
    JavaCallableKind getter =
        isStatic ? JavaCallableKind.STATIC_FIELD_GET : JavaCallableKind.INSTANCE_FIELD_GET;
    List<JavaBindingCallable> bindings = new ArrayList<>();
    bindings.add(
        new JavaBindingCallable(
            owner.binaryName(), field.name(), field.descriptor(), getter, List.of(), type));
    if ((field.access() & Opcodes.ACC_FINAL) == 0) {
      JavaCallableKind setter =
          isStatic ? JavaCallableKind.STATIC_FIELD_SET : JavaCallableKind.INSTANCE_FIELD_SET;
      bindings.add(
          new JavaBindingCallable(
              owner.binaryName(),
              field.name(),
              field.descriptor(),
              setter,
              List.of(type),
              JavaPrimitiveType.VOID));
    }
    return List.copyOf(bindings);
  }

  private static JavaApiMethod apiMethod(
      RawClass owner,
      EffectiveMethod method,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes,
      boolean ownerDeprecated) {
    RawMethod declaration = method.declaration();
    JavaCallableKind kind =
        declaration.name().equals("<init>")
            ? JavaCallableKind.CONSTRUCTOR
            : (declaration.access() & Opcodes.ACC_STATIC) != 0
                ? JavaCallableKind.STATIC_METHOD
                : JavaCallableKind.INSTANCE_METHOD;
    if (ownerDeprecated || deprecated(declaration.access(), declaration.annotations())) {
      return method(
          owner,
          method,
          kind,
          JavaApiDisposition.EXCLUDED_DEPRECATED,
          Optional.empty(),
          Optional.empty());
    }
    BindingResult result = bind(owner, method, rootTypes, samTypes, kind);
    return method(
        owner,
        method,
        kind,
        result.issue().isPresent() ? JavaApiDisposition.UNSUPPORTED : JavaApiDisposition.BINDABLE,
        result.issue(),
        result.binding());
  }

  private static JavaApiMethod method(
      RawClass owner,
      EffectiveMethod method,
      JavaCallableKind kind,
      JavaApiDisposition disposition,
      Optional<JavaApiIssue> issue,
      Optional<JavaBindingCallable> binding) {
    RawMethod declaration = method.declaration();
    return new JavaApiMethod(
        owner.binaryName(),
        declaration.name(),
        declaration.descriptor(),
        method.signature(),
        declaration.access(),
        kind,
        declaration.exceptions().stream().map(JarApiScanner::binaryName).toList(),
        declaration.annotations(),
        declaration.typeAnnotations(),
        declaration.parameters().stream()
            .map(
                parameter ->
                    new JavaApiParameter(
                        parameter.index(),
                        Optional.ofNullable(parameter.name()),
                        parameter.access(),
                        parameter.annotations()))
            .toList(),
        declaration.annotationDefaults().stream().findFirst(),
        disposition,
        issue,
        binding);
  }

  private static BindingResult bind(
      RawClass owner,
      EffectiveMethod method,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes,
      JavaCallableKind kind) {
    RawMethod declaration = method.declaration();
    Type methodType = Type.getMethodType(declaration.descriptor());
    int invocationArity = methodType.getArgumentTypes().length + (kind.requiresReceiver() ? 1 : 0);
    if (invocationArity > 8) {
      return BindingResult.unsupported(
          JavaApiIssueCode.PARAMETER_LIMIT, "Java binding invocation exceeds 8 arguments");
    }
    JavaMethodSignature signature = method.signature();
    Map<String, JavaBindingTypeVariable> variables = new LinkedHashMap<>();
    Optional<JavaGenericParameterProjector.Projection> classProjection =
        addVariables(variables, classSignature(owner).typeParameters(), rootTypes, samTypes);
    if (classProjection.isEmpty()) {
      return BindingResult.unsupported(
          JavaApiIssueCode.GENERIC_MAPPING, "Java generic bound cannot be represented in Norm");
    }
    variables.putAll(classProjection.orElseThrow().variables());
    Optional<JavaGenericParameterProjector.Projection> methodProjection =
        addVariables(variables, signature.typeParameters(), rootTypes, samTypes);
    if (methodProjection.isEmpty()) {
      return BindingResult.unsupported(
          JavaApiIssueCode.GENERIC_MAPPING, "Java generic bound cannot be represented in Norm");
    }
    variables.putAll(methodProjection.orElseThrow().variables());
    List<JavaBindingType> parameters = new ArrayList<>();
    for (int index = 0; index < signature.parameters().size(); index++) {
      JavaBindingType type =
          bindingType(signature.parameters().get(index), variables, rootTypes, samTypes, true);
      if (type == null || !exposableParameter(type)) {
        return declaration.signature() == null
            ? BindingResult.unsupported(methodType.getArgumentTypes()[index])
            : BindingResult.unsupported(
                JavaApiIssueCode.GENERIC_MAPPING,
                "Java generic parameter cannot be represented in Norm");
      }
      parameters.add(type);
    }
    JavaBindingType returnType =
        kind == JavaCallableKind.CONSTRUCTOR
            ? ownerType(owner, variables)
            : bindingType(signature.returnType(), variables, rootTypes, samTypes);
    if (returnType == null || !exposableValue(returnType)) {
      return declaration.signature() == null
          ? BindingResult.unsupported(methodType.getReturnType())
          : BindingResult.unsupported(
              JavaApiIssueCode.GENERIC_MAPPING,
              "Java generic return type cannot be represented in Norm");
    }
    List<JavaBindingTypeParameter> typeParameters = methodProjection.orElseThrow().parameters();
    return new BindingResult(
        Optional.empty(),
        Optional.of(
            new JavaBindingCallable(
                method.invocationOwner(),
                declaration.name(),
                declaration.descriptor(),
                kind,
                typeParameters,
                parameters,
                returnType)));
  }

  private static Optional<JavaGenericParameterProjector.Projection> addVariables(
      Map<String, JavaBindingTypeVariable> variables,
      List<JavaTypeParameter> parameters,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes) {
    return JavaGenericParameterProjector.project(
        parameters,
        variables,
        (signature, available) -> bindingType(signature, available, rootTypes, samTypes));
  }

  private static JavaBindingType bindingType(
      JavaTypeSignature signature,
      Map<String, ? extends JavaBindingType> variables,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes) {
    return bindingType(signature, variables, rootTypes, samTypes, false);
  }

  private static JavaBindingType bindingType(
      JavaTypeSignature signature,
      Map<String, ? extends JavaBindingType> variables,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes,
      boolean allowCallback) {
    return switch (signature) {
      case JavaPrimitiveTypeSignature primitive -> primitive.type();
      case JavaTypeVariableSignature variable -> variables.get(variable.name());
      case JavaArrayTypeSignature array -> {
        JavaBindingType component = bindingType(array.component(), variables, rootTypes, samTypes);
        yield component == null ? null : new JavaArrayType(component);
      }
      case JavaClassTypeSignature classType ->
          bindingClassType(classType, variables, rootTypes, samTypes, allowCallback);
    };
  }

  private static JavaBindingType bindingClassType(
      JavaClassTypeSignature classType,
      Map<String, ? extends JavaBindingType> variables,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes,
      boolean allowCallback) {
    String name = classType.binaryName();
    Optional<JavaBoxedType> boxed = JavaBoxedType.fromBinaryName(name);
    if (boxed.isPresent()
        && classType.segments().stream().allMatch(segment -> segment.arguments().isEmpty())) {
      return boxed.orElseThrow();
    }
    if (allowCallback) {
      Optional<JavaCallbackType> callback =
          JavaPlatformCallbacks.project(
              classType, signature -> bindingType(signature, variables, rootTypes, samTypes));
      if (callback.isPresent()) return callback.orElseThrow();
      callback = projectSam(classType, variables, rootTypes, samTypes);
      if (callback.isPresent()) return callback.orElseThrow();
    }
    JavaReferenceKind kind = JavaPlatformTypes.referenceKind(name).orElse(rootTypes.get(name));
    if (kind == null) return null;
    List<JavaBindingTypeArgument> arguments = new ArrayList<>();
    for (JavaClassTypeSegment segment : classType.segments()) {
      for (JavaTypeArgument argument : segment.arguments()) {
        if (argument.variance() == JavaTypeVariance.UNBOUNDED) {
          arguments.add(JavaBindingTypeArgument.unbounded());
          continue;
        }
        if (argument.variance() != JavaTypeVariance.EXACT) return null;
        JavaBindingType type =
            bindingType(argument.type().orElseThrow(), variables, rootTypes, samTypes);
        if (type == null) return null;
        arguments.add(JavaBindingTypeArgument.exact(type));
      }
    }
    if (kind == JavaReferenceKind.CLASS
        && arguments.stream()
            .filter(argument -> argument.variance() == JavaTypeVariance.EXACT)
            .map(argument -> argument.type().orElseThrow())
            .anyMatch(type -> !JavaPlatformTypes.classTokenCompatible(type))) {
      return null;
    }
    return new JavaReferenceType(name, kind, arguments);
  }

  private static Optional<JavaCallbackType> projectSam(
      JavaClassTypeSignature type,
      Map<String, ? extends JavaBindingType> outerVariables,
      Map<String, JavaReferenceKind> rootTypes,
      Map<String, RawSam> samTypes) {
    RawSam sam = samTypes.get(type.binaryName());
    if (sam == null) return Optional.empty();
    List<JavaTypeArgument> arguments =
        type.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    List<JavaTypeParameter> parameters = classSignature(sam.owner()).typeParameters();
    if (!arguments.isEmpty() && arguments.size() != parameters.size()) return Optional.empty();
    Map<String, JavaBindingType> variables = new LinkedHashMap<>(outerVariables);
    JavaBindingType object = new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    Map<String, RawSam> nestedSamTypes = new LinkedHashMap<>(samTypes);
    nestedSamTypes.remove(type.binaryName());
    for (int index = 0; index < parameters.size(); index++) {
      JavaBindingType argument = object;
      if (!arguments.isEmpty() && arguments.get(index).variance() != JavaTypeVariance.UNBOUNDED) {
        argument =
            bindingType(
                arguments.get(index).type().orElseThrow(),
                outerVariables,
                rootTypes,
                nestedSamTypes);
        if (argument == null) return Optional.empty();
      }
      variables.put(parameters.get(index).name(), argument);
    }
    JavaMethodSignature signature =
        SIGNATURES.parseMethod(
            sam.method().signature() == null
                ? sam.method().descriptor()
                : sam.method().signature());
    if (!signature.typeParameters().isEmpty()) return Optional.empty();
    List<JavaBindingType> callbackParameters = new ArrayList<>();
    for (JavaTypeSignature parameter : signature.parameters()) {
      JavaBindingType projected = bindingType(parameter, variables, rootTypes, nestedSamTypes);
      if (projected == null || !exposableValue(projected)) return Optional.empty();
      callbackParameters.add(projected);
    }
    JavaBindingType returnType =
        bindingType(signature.returnType(), variables, rootTypes, nestedSamTypes);
    if (returnType == null || !exposableValue(returnType)) return Optional.empty();
    return Optional.of(
        new JavaCallbackType(
            type.binaryName(), sam.method().name(), callbackParameters, returnType));
  }

  private static Map<String, RawSam> samTypes(Map<String, RawClass> classes) {
    Map<String, RawSam> result = new LinkedHashMap<>();
    for (RawClass owner : classes.values()) {
      if ((owner.access() & Opcodes.ACC_INTERFACE) == 0
          || (owner.access() & Opcodes.ACC_ANNOTATION) != 0) continue;
      List<RawMethod> declared =
          owner.methods().stream()
              .filter(method -> isPublic(method.access()))
              .filter(method -> (method.access() & Opcodes.ACC_ABSTRACT) != 0)
              .filter(method -> (method.access() & Opcodes.ACC_STATIC) == 0)
              .filter(method -> !isSynthetic(method.access()))
              .filter(method -> !objectMethod(method))
              .toList();
      Optional<Set<String>> abstractMethods =
          abstractMethods(owner, classes, new java.util.HashSet<>());
      boolean compilerVerified =
          owner.annotations().stream()
              .anyMatch(annotation -> annotation.type().equals("java.lang.FunctionalInterface"));
      if (declared.size() == 1
          && (compilerVerified
              || abstractMethods
                  .filter(methods -> methods.size() == 1)
                  .filter(methods -> methods.contains(methodKey(declared.getFirst())))
                  .isPresent())) {
        result.put(owner.binaryName(), new RawSam(owner, declared.getFirst()));
      }
    }
    return Map.copyOf(result);
  }

  private static Optional<Set<String>> abstractMethods(
      RawClass owner, Map<String, RawClass> classes, Set<String> visiting) {
    if (!visiting.add(owner.binaryName())) return Optional.of(Set.of());
    Set<String> methods = new java.util.LinkedHashSet<>();
    for (String parentName : owner.interfaces()) {
      String binaryName = binaryName(parentName);
      RawClass parent = classes.get(binaryName);
      if (parent == null) {
        if (!markerInterface(binaryName)) return Optional.empty();
        continue;
      }
      Optional<Set<String>> inherited = abstractMethods(parent, classes, visiting);
      if (inherited.isEmpty()) return Optional.empty();
      methods.addAll(inherited.orElseThrow());
    }
    for (RawMethod method : owner.methods()) {
      if (!isPublic(method.access())
          || (method.access() & Opcodes.ACC_STATIC) != 0
          || isSynthetic(method.access())
          || objectMethod(method)) continue;
      if ((method.access() & Opcodes.ACC_ABSTRACT) != 0) {
        methods.add(methodKey(method));
      } else {
        methods.remove(methodKey(method));
      }
    }
    visiting.remove(owner.binaryName());
    return Optional.of(Set.copyOf(methods));
  }

  private static boolean markerInterface(String binaryName) {
    return binaryName.equals("java.io.Serializable")
        || binaryName.equals("java.lang.Cloneable")
        || binaryName.equals("java.util.RandomAccess");
  }

  private static boolean objectMethod(RawMethod method) {
    return method.name().equals("equals") && method.descriptor().equals("(Ljava/lang/Object;)Z")
        || method.name().equals("hashCode") && method.descriptor().equals("()I")
        || method.name().equals("toString") && method.descriptor().equals("()Ljava/lang/String;");
  }

  private static String methodKey(RawMethod method) {
    int result = method.descriptor().indexOf(')');
    return method.name() + method.descriptor().substring(0, result + 1);
  }

  private static JavaReferenceType ownerType(
      RawClass owner, Map<String, JavaBindingTypeVariable> variables) {
    List<JavaBindingTypeArgument> arguments =
        classSignature(owner).typeParameters().stream()
            .map(parameter -> JavaBindingTypeArgument.exact(variables.get(parameter.name())))
            .toList();
    return new JavaReferenceType(owner.binaryName(), JavaReferenceKind.OPAQUE, arguments);
  }

  private static JavaClassSignature classSignature(RawClass owner) {
    if (owner.signature() != null) return SIGNATURES.parseClass(owner.signature());
    return new JavaClassSignature(
        List.of(),
        Optional.ofNullable(binaryName(owner.superName())).map(JavaClassTypeSignature::raw),
        owner.interfaces().stream()
            .map(JarApiScanner::binaryName)
            .map(JavaClassTypeSignature::raw)
            .toList());
  }

  private static Map<String, RawClass> readClasses(ResolvedJarArtifact root) throws IOException {
    Map<String, RawClass> classes = new LinkedHashMap<>();
    try (JarFile jar =
        new JarFile(root.file().toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
      var entries = jar.versionedStream().filter(value -> classEntry(value.getName())).toList();
      for (var entry : entries) {
        try (InputStream input = jar.getInputStream(entry)) {
          RawClass owner = readClass(input);
          classes.put(owner.binaryName(), owner);
        }
      }
    }
    return classes;
  }

  private static Map<String, RawClass> readClasses(List<ResolvedJarArtifact> artifacts)
      throws IOException {
    Map<String, RawClass> classes = new LinkedHashMap<>();
    for (ResolvedJarArtifact artifact : artifacts) {
      readClasses(artifact).forEach(classes::putIfAbsent);
    }
    return classes;
  }

  private static RawClass readClass(InputStream input) throws IOException {
    RawClassVisitor visitor = new RawClassVisitor();
    new ClassReader(input).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    return visitor.result();
  }

  private static JavaBindingType bindingType(
      Type type, Map<String, JavaReferenceKind> rootTypes, Map<String, RawSam> samTypes) {
    return switch (type.getSort()) {
      case Type.VOID -> JavaPrimitiveType.VOID;
      case Type.BOOLEAN -> JavaPrimitiveType.BOOLEAN;
      case Type.BYTE -> JavaPrimitiveType.BYTE;
      case Type.SHORT -> JavaPrimitiveType.SHORT;
      case Type.INT -> JavaPrimitiveType.INT;
      case Type.LONG -> JavaPrimitiveType.LONG;
      case Type.FLOAT -> JavaPrimitiveType.FLOAT;
      case Type.DOUBLE -> JavaPrimitiveType.DOUBLE;
      case Type.CHAR -> JavaPrimitiveType.CHAR;
      case Type.OBJECT -> {
        String name = type.getClassName();
        Optional<JavaBoxedType> boxed = JavaBoxedType.fromBinaryName(name);
        if (boxed.isPresent()) yield boxed.orElseThrow();
        Optional<JavaReferenceKind> platformKind = JavaPlatformTypes.referenceKind(name);
        if (platformKind.isPresent()) {
          yield new JavaReferenceType(name, platformKind.orElseThrow());
        }
        JavaReferenceKind rootKind = rootTypes.get(name);
        yield rootKind == null ? null : new JavaReferenceType(name, rootKind);
      }
      case Type.ARRAY -> {
        JavaBindingType component = bindingType(type.getElementType(), rootTypes, samTypes);
        if (component == null) yield null;
        JavaBindingType array = component;
        for (int dimension = 0; dimension < type.getDimensions(); dimension++) {
          array = new JavaArrayType(array);
        }
        yield array;
      }
      default -> null;
    };
  }

  private static boolean exposableParameter(JavaBindingType type) {
    if (type instanceof JavaCallbackType callback) {
      return callback.parameters().stream().allMatch(JarApiScanner::exposableValue)
          && exposableValue(callback.returnType());
    }
    return exposableValue(type);
  }

  private static boolean exposableValue(JavaBindingType type) {
    return switch (type) {
      case JavaPrimitiveType primitive -> true;
      case JavaBoxedType ignored -> true;
      case JavaBindingTypeVariable ignored -> true;
      case JavaCallbackType ignored -> false;
      case JavaArrayType array ->
          (array.component() instanceof JavaBindingTypeVariable || concrete(array.component()))
              && exposableValue(array.component());
      case JavaReferenceType ignored -> true;
    };
  }

  private static boolean concrete(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType array -> concrete(array.component());
      case JavaBindingTypeVariable ignored -> false;
      case JavaCallbackType ignored -> false;
      case JavaReferenceType reference ->
          reference.arguments().stream()
              .allMatch(
                  argument ->
                      argument.variance() == JavaTypeVariance.EXACT
                          && concrete(argument.type().orElseThrow()));
      case JavaBoxedType ignored -> true;
      case JavaPrimitiveType ignored -> true;
    };
  }

  private static JavaApiTypeKind kind(int access) {
    if ((access & Opcodes.ACC_ANNOTATION) != 0) return JavaApiTypeKind.ANNOTATION;
    if ((access & Opcodes.ACC_ENUM) != 0) return JavaApiTypeKind.ENUM;
    if ((access & Opcodes.ACC_RECORD) != 0) return JavaApiTypeKind.RECORD;
    if ((access & Opcodes.ACC_INTERFACE) != 0) return JavaApiTypeKind.INTERFACE;
    return JavaApiTypeKind.CLASS;
  }

  private static boolean deprecated(int access, List<JavaApiAnnotation> annotations) {
    return (access & Opcodes.ACC_DEPRECATED) != 0
        || annotations.stream().anyMatch(value -> value.type().equals("java.lang.Deprecated"));
  }

  private static boolean classEntry(String name) {
    return name.endsWith(".class")
        && !name.equals("module-info.class")
        && !name.endsWith("/module-info.class")
        && !name.endsWith("/package-info.class");
  }

  private static boolean isPublic(int access) {
    return (access & Opcodes.ACC_PUBLIC) != 0;
  }

  private static boolean publiclyAccessible(RawClass type, Map<String, RawClass> classes) {
    RawClass current = type;
    Set<String> visited = new java.util.HashSet<>();
    while (current != null && visited.add(current.binaryName())) {
      if (!isPublic(current.access())) return false;
      current = classes.get(binaryName(current.enclosingType()));
    }
    return current == null;
  }

  private static boolean isSynthetic(int access) {
    return (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
  }

  private static String binaryName(String internalName) {
    return internalName == null ? null : internalName.replace('/', '.');
  }

  private static JavaAnnotationValue annotationValue(Object value) {
    if (value instanceof Type type) return new JavaAnnotationClassValue(type.getDescriptor());
    if (!value.getClass().isArray()) return new JavaAnnotationConstantValue(value);
    List<JavaAnnotationValue> values = new ArrayList<>();
    for (int index = 0; index < Array.getLength(value); index++) {
      values.add(annotationValue(Array.get(value, index)));
    }
    return new JavaAnnotationArrayValue(values);
  }

  private static AnnotationVisitor annotation(
      String descriptor, boolean visible, Consumer<JavaApiAnnotation> consumer) {
    return new AnnotationCollector(Type.getType(descriptor).getClassName(), visible, consumer);
  }

  private static AnnotationVisitor typeAnnotation(
      int typeReference,
      TypePath typePath,
      String descriptor,
      boolean visible,
      List<JavaApiTypeAnnotation> annotations) {
    return annotation(
        descriptor,
        visible,
        value ->
            annotations.add(
                new JavaApiTypeAnnotation(
                    typeReference, Optional.ofNullable(typePath).map(TypePath::toString), value)));
  }

  private static AnnotationVisitor value(Consumer<JavaAnnotationValue> consumer) {
    return new AnnotationValueCollector(consumer);
  }

  private record BindingResult(
      Optional<JavaApiIssue> issue, Optional<JavaBindingCallable> binding) {
    private static BindingResult unsupported(JavaApiIssueCode code, String detail) {
      return new BindingResult(Optional.of(new JavaApiIssue(code, detail)), Optional.empty());
    }

    private static BindingResult unsupported(Type type) {
      return unsupported(
          JavaApiIssueCode.UNSUPPORTED_TYPE, "unsupported Java type " + type.getClassName());
    }
  }

  private record RawSam(RawClass owner, RawMethod method) {}

  private record EffectiveMethod(
      RawMethod declaration, JavaMethodSignature signature, String invocationOwner) {}

  private record RawClass(
      String binaryName,
      int access,
      String signature,
      String superName,
      List<String> interfaces,
      List<JavaApiAnnotation> annotations,
      List<JavaApiTypeAnnotation> typeAnnotations,
      String enclosingType,
      List<RawRecordComponent> recordComponents,
      List<String> permittedSubclasses,
      List<RawField> fields,
      List<RawMethod> methods) {}

  private record RawRecordComponent(
      String name,
      String descriptor,
      String signature,
      List<JavaApiAnnotation> annotations,
      List<JavaApiTypeAnnotation> typeAnnotations) {}

  private record RawField(
      String name,
      String descriptor,
      String signature,
      int access,
      Object value,
      List<JavaApiAnnotation> annotations,
      List<JavaApiTypeAnnotation> typeAnnotations) {}

  private record RawMethod(
      String name,
      String descriptor,
      String signature,
      int access,
      List<String> exceptions,
      List<JavaApiAnnotation> annotations,
      List<JavaApiTypeAnnotation> typeAnnotations,
      List<RawParameter> parameters,
      List<JavaAnnotationValue> annotationDefaults) {}

  private record RawParameter(
      int index, String name, int access, List<JavaApiAnnotation> annotations) {}

  private static final class RawClassVisitor extends ClassVisitor {
    private String binaryName;
    private String internalName;
    private int access;
    private String signature;
    private String superName;
    private List<String> interfaces = List.of();
    private final List<JavaApiAnnotation> annotations = new ArrayList<>();
    private final List<JavaApiTypeAnnotation> typeAnnotations = new ArrayList<>();
    private String enclosingType;
    private final List<RawRecordComponent> recordComponents = new ArrayList<>();
    private final List<String> permittedSubclasses = new ArrayList<>();
    private final List<RawField> fields = new ArrayList<>();
    private final List<RawMethod> methods = new ArrayList<>();

    private RawClassVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.internalName = name;
      this.binaryName = JarApiScanner.binaryName(name);
      this.access = access;
      this.signature = signature;
      this.superName = superName;
      this.interfaces = interfaces == null ? List.of() : List.of(interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return annotation(descriptor, visible, annotations::add);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeReference, TypePath typePath, String descriptor, boolean visible) {
      return typeAnnotation(typeReference, typePath, descriptor, visible, typeAnnotations);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int innerAccess) {
      if (!name.equals(internalName)) return;
      int nestingModifiers =
          Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC;
      access = (access & ~nestingModifiers) | (innerAccess & nestingModifiers);
      enclosingType = outerName;
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(
        String name, String descriptor, String signature) {
      List<JavaApiAnnotation> componentAnnotations = new ArrayList<>();
      List<JavaApiTypeAnnotation> componentTypeAnnotations = new ArrayList<>();
      recordComponents.add(
          new RawRecordComponent(
              name, descriptor, signature, componentAnnotations, componentTypeAnnotations));
      return new RecordComponentVisitor(Opcodes.ASM9) {
        @Override
        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
          return annotation(annotationDescriptor, visible, componentAnnotations::add);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
            int typeReference, TypePath typePath, String annotationDescriptor, boolean visible) {
          return typeAnnotation(
              typeReference, typePath, annotationDescriptor, visible, componentTypeAnnotations);
        }
      };
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
      permittedSubclasses.add(permittedSubclass);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      List<JavaApiAnnotation> fieldAnnotations = new ArrayList<>();
      List<JavaApiTypeAnnotation> fieldTypeAnnotations = new ArrayList<>();
      fields.add(
          new RawField(
              name, descriptor, signature, access, value, fieldAnnotations, fieldTypeAnnotations));
      return new FieldVisitor(Opcodes.ASM9) {
        @Override
        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
          return annotation(annotationDescriptor, visible, fieldAnnotations::add);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
            int typeReference, TypePath typePath, String annotationDescriptor, boolean visible) {
          return typeAnnotation(
              typeReference, typePath, annotationDescriptor, visible, fieldTypeAnnotations);
        }
      };
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      List<JavaApiAnnotation> methodAnnotations = new ArrayList<>();
      List<JavaApiTypeAnnotation> methodTypeAnnotations = new ArrayList<>();
      List<RawParameter> parameters = new ArrayList<>();
      for (int index = 0; index < Type.getArgumentTypes(descriptor).length; index++) {
        parameters.add(new RawParameter(index, null, 0, new ArrayList<>()));
      }
      List<JavaAnnotationValue> annotationDefaults = new ArrayList<>();
      methods.add(
          new RawMethod(
              name,
              descriptor,
              signature,
              access,
              exceptions == null ? List.of() : List.of(exceptions),
              methodAnnotations,
              methodTypeAnnotations,
              parameters,
              annotationDefaults));
      return new MethodVisitor(Opcodes.ASM9) {
        private int parameterIndex;

        @Override
        public void visitParameter(String parameterName, int parameterAccess) {
          if (parameterIndex >= parameters.size()) return;
          RawParameter parameter = parameters.get(parameterIndex);
          parameters.set(
              parameterIndex,
              new RawParameter(
                  parameter.index(), parameterName, parameterAccess, parameter.annotations()));
          parameterIndex++;
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
          return value(annotationDefaults::add);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
          return annotation(annotationDescriptor, visible, methodAnnotations::add);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
            int typeReference, TypePath typePath, String annotationDescriptor, boolean visible) {
          return typeAnnotation(
              typeReference, typePath, annotationDescriptor, visible, methodTypeAnnotations);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(
            int parameter, String annotationDescriptor, boolean visible) {
          return annotation(
              annotationDescriptor, visible, parameters.get(parameter).annotations()::add);
        }
      };
    }

    private RawClass result() {
      return new RawClass(
          binaryName,
          access,
          signature,
          superName,
          List.copyOf(interfaces),
          List.copyOf(annotations),
          List.copyOf(typeAnnotations),
          enclosingType,
          List.copyOf(recordComponents),
          List.copyOf(permittedSubclasses),
          List.copyOf(fields),
          List.copyOf(methods));
    }
  }

  private static final class AnnotationCollector extends AnnotationVisitor {
    private final String type;
    private final boolean visible;
    private final Consumer<JavaApiAnnotation> consumer;
    private final List<JavaAnnotationElement> elements = new ArrayList<>();

    private AnnotationCollector(
        String type, boolean visible, Consumer<JavaApiAnnotation> consumer) {
      super(Opcodes.ASM9);
      this.type = type;
      this.visible = visible;
      this.consumer = consumer;
    }

    @Override
    public void visit(String name, Object value) {
      elements.add(new JavaAnnotationElement(name, annotationValue(value)));
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      elements.add(
          new JavaAnnotationElement(
              name, new JavaAnnotationEnumValue(Type.getType(descriptor).getClassName(), value)));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return annotation(
          descriptor,
          visible,
          nested ->
              elements.add(new JavaAnnotationElement(name, new JavaAnnotationNestedValue(nested))));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new AnnotationArrayCollector(
          values ->
              elements.add(new JavaAnnotationElement(name, new JavaAnnotationArrayValue(values))));
    }

    @Override
    public void visitEnd() {
      consumer.accept(new JavaApiAnnotation(type, visible, elements));
    }
  }

  private static final class AnnotationValueCollector extends AnnotationVisitor {
    private final Consumer<JavaAnnotationValue> consumer;

    private AnnotationValueCollector(Consumer<JavaAnnotationValue> consumer) {
      super(Opcodes.ASM9);
      this.consumer = consumer;
    }

    @Override
    public void visit(String name, Object value) {
      consumer.accept(annotationValue(value));
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      consumer.accept(new JavaAnnotationEnumValue(Type.getType(descriptor).getClassName(), value));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return annotation(
          descriptor, true, nested -> consumer.accept(new JavaAnnotationNestedValue(nested)));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new AnnotationArrayCollector(
          values -> consumer.accept(new JavaAnnotationArrayValue(values)));
    }
  }

  private static final class AnnotationArrayCollector extends AnnotationVisitor {
    private final Consumer<List<JavaAnnotationValue>> consumer;
    private final List<JavaAnnotationValue> values = new ArrayList<>();

    private AnnotationArrayCollector(Consumer<List<JavaAnnotationValue>> consumer) {
      super(Opcodes.ASM9);
      this.consumer = consumer;
    }

    @Override
    public void visit(String name, Object value) {
      values.add(annotationValue(value));
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      values.add(new JavaAnnotationEnumValue(Type.getType(descriptor).getClassName(), value));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return annotation(
          descriptor, true, nested -> values.add(new JavaAnnotationNestedValue(nested)));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new AnnotationArrayCollector(
          nested -> values.add(new JavaAnnotationArrayValue(nested)));
    }

    @Override
    public void visitEnd() {
      consumer.accept(List.copyOf(values));
    }
  }
}
