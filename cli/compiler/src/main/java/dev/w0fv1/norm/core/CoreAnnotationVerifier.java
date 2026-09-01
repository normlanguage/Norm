package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CoreAnnotationVerifier {
  private CoreAnnotationVerifier() {}

  static void verifyArtifact(
      CoreProgram program, CoreAuthoringMap authoring, CoreMetadata metadata) {
    CoreFunctionInterceptorProtocol functionInterceptor =
        CoreFunctionInterceptorProtocol.resolve(program).orElse(null);
    CoreParameterInterceptorProtocol parameterInterceptor =
        CoreParameterInterceptorProtocol.resolve(program).orElse(null);
    CoreFieldInterceptorProtocol fieldInterceptor =
        CoreFieldInterceptorProtocol.resolve(program).orElse(null);
    Map<ApplicationKey, CoreAnnotationApplication> applications = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : metadata.annotations()) {
      verifyApplication(program, authoring, application);
      if (applications.putIfAbsent(applicationKey(application), application) != null
          && !policy(program, application.annotation()).repeatable()) {
        throw new IllegalArgumentException("annotation application must be unique per target");
      }
    }
    Set<ApplicationKey> matched = new HashSet<>();
    for (CoreDefinitionOccurrence occurrence : authoring.occurrences()) {
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      if (definition instanceof CoreDefinition.Aggregate aggregate
          && occurrence.role() == CoreDefinitionRole.AGGREGATE) {
        for (CoreField field : aggregate.fields()) {
          for (CoreInterceptor interceptor : field.interceptors()) {
            matchInterceptor(
                program,
                occurrence.id(),
                AnnotationTarget.FIELD,
                new IndexedKey(occurrence.id(), field.ordinal()),
                interceptor,
                applications,
                matched);
          }
        }
      }
      if (!(definition instanceof CoreDefinition.Callable callable)) continue;
      for (CoreInterceptor interceptor : callable.interceptors()) {
        matchInterceptor(
            program,
            occurrence.id(),
            AnnotationTarget.FUNCTION,
            new DefinitionKey(AnnotationTarget.FUNCTION, occurrence.id()),
            interceptor,
            applications,
            matched);
      }
      for (int index = 0; index < callable.parameters().size(); index++) {
        for (CoreInterceptor interceptor : callable.parameters().get(index).interceptors()) {
          matchInterceptor(
              program,
              occurrence.id(),
              AnnotationTarget.PARAMETER,
              new IndexedKey(occurrence.id(), index),
              interceptor,
              applications,
              matched);
        }
      }
    }
    for (Map.Entry<ApplicationKey, CoreAnnotationApplication> entry : applications.entrySet()) {
      ApplicationKey application = entry.getKey();
      if (isBehaviorApplication(
              program,
              entry.getValue(),
              functionInterceptor,
              parameterInterceptor,
              fieldInterceptor)
          && !matched.contains(application)) {
        throw new IllegalArgumentException(
            "stored behavior annotation requires a declaration interceptor");
      }
    }
  }

  private static CoreAnnotationPolicy policy(CoreProgram program, DefinitionId annotationId) {
    CoreDefinition definition = program.definition(annotationId).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("annotation application must reference an annotation");
    }
    return CoreAnnotationPolicy.resolve(program, annotationId, annotation);
  }

  private static void matchInterceptor(
      CoreProgram program,
      DefinitionOccurrenceId owner,
      AnnotationTarget target,
      Object targetKey,
      CoreInterceptor interceptor,
      Map<ApplicationKey, CoreAnnotationApplication> applications,
      Set<ApplicationKey> matched) {
    DefinitionId annotationId = resolve(program, owner.representative(), interceptor.annotation());
    CoreDefinition.Aggregate annotation =
        (CoreDefinition.Aggregate) program.definition(annotationId).orElseThrow();
    AnnotationRetention retention =
        CoreAnnotationPolicy.resolve(program, annotationId, annotation).retention();
    ApplicationKey key = new ApplicationKey(annotationId, target, targetKey);
    CoreAnnotationApplication application = applications.get(key);
    if (retention == AnnotationRetention.SOURCE) {
      if (application != null) {
        throw new IllegalArgumentException("source interceptor cannot be stored in metadata");
      }
      return;
    }
    if (application == null || !application.values().equals(interceptor.values())) {
      throw new IllegalArgumentException(
          "stored behavior annotation must match its declaration interceptor");
    }
    matched.add(key);
  }

  static void verifyInterceptor(
      CoreProgram program,
      DefinitionId callableId,
      CoreInterceptor interceptor,
      CoreFunctionInterceptorProtocol functionInterceptor) {
    CoreDefinition.Callable callable =
        (CoreDefinition.Callable) program.definition(callableId).orElseThrow();
    if (CoreTypes.containsReference(callable.returnType())
        || callable.parameterTypes().stream().anyMatch(CoreTypes::containsReference)) {
      throw new IllegalArgumentException("interceptor callable cannot use reference types");
    }
    DefinitionId annotationId = resolve(program, callableId, interceptor.annotation());
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("interceptor annotation is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("interceptor must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (!policy.targets().contains(dev.w0fv1.norm.value.AnnotationTarget.FUNCTION)) {
      throw new IllegalArgumentException("interceptor annotation must allow function targets");
    }
    if (functionInterceptor == null
        || !implementsProtocol(
            program, annotationId, annotation, functionInterceptor.interfaceId())) {
      throw new IllegalArgumentException(
          "interceptor annotation must implement FunctionInterceptor");
    }
    verifyValues(program, callableId, annotationId, annotation, interceptor.values());
  }

  static void verifyParameterInterceptor(
      CoreProgram program,
      DefinitionId callableId,
      CoreCallableParameter parameter,
      CoreInterceptor interceptor,
      CoreParameterInterceptorProtocol parameterInterceptor) {
    if (CoreTypes.containsReference(parameter.type())) {
      throw new IllegalArgumentException("interceptor parameter cannot use a reference type");
    }
    DefinitionId annotationId = resolve(program, callableId, interceptor.annotation());
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("interceptor annotation is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("interceptor must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (!policy.targets().contains(AnnotationTarget.PARAMETER)) {
      throw new IllegalArgumentException("interceptor annotation must allow parameter targets");
    }
    if (parameterInterceptor == null) {
      throw new IllegalArgumentException("ParameterInterceptor protocol is absent");
    }
    CoreType expected =
        targetType(
            program,
            annotationId,
            annotation,
            parameterInterceptor.interfaceId(),
            "ParameterInterceptor");
    CoreType actual = CoreTypes.absolute(parameter.type(), callableId, program);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("ParameterInterceptor type does not match parameter type");
    }
    verifyValues(program, callableId, annotationId, annotation, interceptor.values());
  }

  static void verifyFieldInterceptor(
      CoreProgram program,
      DefinitionId aggregateId,
      CoreField field,
      CoreInterceptor interceptor,
      CoreFieldInterceptorProtocol fieldInterceptor) {
    DefinitionId annotationId = resolve(program, aggregateId, interceptor.annotation());
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("interceptor annotation is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("interceptor must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (!policy.targets().contains(AnnotationTarget.FIELD)) {
      throw new IllegalArgumentException("interceptor annotation must allow field targets");
    }
    if (fieldInterceptor == null) {
      throw new IllegalArgumentException("FieldInterceptor protocol is absent");
    }
    CoreType expected =
        targetType(
            program, annotationId, annotation, fieldInterceptor.interfaceId(), "FieldInterceptor");
    CoreType actual = CoreTypes.absolute(field.type(), aggregateId, program);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("FieldInterceptor type does not match field type");
    }
    verifyValues(program, aggregateId, annotationId, annotation, interceptor.values());
  }

  private static CoreType targetType(
      CoreProgram program,
      DefinitionId annotationId,
      CoreDefinition.Aggregate annotation,
      DefinitionId protocol,
      String name) {
    Map<DefinitionId, CoreType.Declared> interfaces = new LinkedHashMap<>();
    CoreInterfaceHierarchy hierarchy = new CoreInterfaceHierarchy(program);
    for (CoreConformance conformance : annotation.conformances()) {
      hierarchy.collect(annotationId, conformance.interfaceType(), interfaces);
    }
    CoreType.Declared target = interfaces.get(protocol);
    if (target == null || target.arguments().size() != 1) {
      throw new IllegalArgumentException("interceptor annotation must implement " + name);
    }
    return target.arguments().getFirst();
  }

  private static boolean isBehaviorApplication(
      CoreProgram program,
      CoreAnnotationApplication application,
      CoreFunctionInterceptorProtocol functionInterceptor,
      CoreParameterInterceptorProtocol parameterInterceptor,
      CoreFieldInterceptorProtocol fieldInterceptor) {
    DefinitionId annotationId = application.annotation();
    CoreDefinition.Aggregate annotation =
        (CoreDefinition.Aggregate) program.definition(annotationId).orElseThrow();
    DefinitionId protocol =
        switch (application.target().kind()) {
          case FUNCTION -> functionInterceptor == null ? null : functionInterceptor.interfaceId();
          case PARAMETER ->
              parameterInterceptor == null ? null : parameterInterceptor.interfaceId();
          case FIELD -> fieldInterceptor == null ? null : fieldInterceptor.interfaceId();
          default -> null;
        };
    return protocol != null && implementsProtocol(program, annotationId, annotation, protocol);
  }

  private static boolean implementsProtocol(
      CoreProgram program,
      DefinitionId annotationId,
      CoreDefinition.Aggregate annotation,
      DefinitionId protocol) {
    Map<DefinitionId, CoreType.Declared> interfaces = new LinkedHashMap<>();
    CoreInterfaceHierarchy hierarchy = new CoreInterfaceHierarchy(program);
    for (CoreConformance conformance : annotation.conformances()) {
      hierarchy.collect(annotationId, conformance.interfaceType(), interfaces);
    }
    return interfaces.containsKey(protocol);
  }

  static void verifyDeclaration(
      CoreProgram program, DefinitionId annotationId, CoreDefinition.Aggregate annotation) {
    DefinitionId constructorId = annotationConstructor(program, annotationId, annotation);
    CoreDefinition.Callable constructor = callable(program, constructorId);
    constructor.parameterTypes().forEach(type -> requireMetadataType(program, constructorId, type));
  }

  private static void verifyApplication(
      CoreProgram program, CoreAuthoringMap authoring, CoreAnnotationApplication application) {
    DefinitionId annotationId = application.annotation();
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("annotation definition is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("annotation application must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (policy.retention() == AnnotationRetention.SOURCE) {
      throw new IllegalArgumentException("source annotation cannot be stored in Core");
    }
    if (!policy.targets().contains(application.target().kind())) {
      throw new IllegalArgumentException("annotation application target is not allowed");
    }
    verifyTarget(program, authoring, application.target());
    verifyValues(program, annotationId, annotationId, annotation, application.values());
  }

  private static void verifyValues(
      CoreProgram program,
      DefinitionId valueOwner,
      DefinitionId annotationId,
      CoreDefinition.Aggregate annotation,
      List<CoreAnnotationValue> values) {
    DefinitionId constructorId = annotationConstructor(program, annotationId, annotation);
    CoreDefinition constructorDefinition = program.definition(constructorId).orElseThrow();
    if (!(constructorDefinition instanceof CoreDefinition.Callable constructor)) {
      throw new IllegalArgumentException("annotation constructor is not callable");
    }
    if (values.size() != constructor.parameterTypes().size()) {
      throw new IllegalArgumentException("annotation application values are not normalized");
    }
    for (int index = 0; index < values.size(); index++) {
      verifyValue(
          program,
          valueOwner,
          constructorId,
          constructor.parameterTypes().get(index),
          values.get(index));
    }
  }

  private static DefinitionId annotationConstructor(
      CoreProgram program, DefinitionId annotationId, CoreDefinition.Aggregate annotation) {
    if (annotation.constructors().size() != 1) {
      throw new IllegalArgumentException("annotation must declare exactly one constructor");
    }
    return resolve(program, annotationId, annotation.constructors().getFirst());
  }

  private static ApplicationKey applicationKey(CoreAnnotationApplication application) {
    Object target =
        switch (application.target()) {
          case CoreAnnotationTarget.Package targetPackage ->
              new PackageKey(targetPackage.module(), targetPackage.packageName());
          case CoreAnnotationTarget.Definition definition ->
              new DefinitionKey(definition.kind(), definition.occurrence());
          case CoreAnnotationTarget.Field field -> new IndexedKey(field.owner(), field.ordinal());
          case CoreAnnotationTarget.Parameter parameter ->
              new IndexedKey(parameter.callable(), parameter.index());
          case CoreAnnotationTarget.Local local -> new IndexedKey(local.callable(), local.index());
        };
    return new ApplicationKey(application.annotation(), application.target().kind(), target);
  }

  private static void verifyTarget(
      CoreProgram program, CoreAuthoringMap authoring, CoreAnnotationTarget target) {
    switch (target) {
      case CoreAnnotationTarget.Package targetPackage -> {
        if (targetPackage.packageName().isBlank()) {
          throw new IllegalArgumentException("annotation package target must not be blank");
        }
      }
      case CoreAnnotationTarget.Definition definition -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, definition.occurrence());
        CoreDefinition targetDefinition =
            definition(program, definition.occurrence().representative());
        boolean valid =
            switch (definition.kind()) {
              case TYPE ->
                  occurrence.role() == CoreDefinitionRole.AGGREGATE
                          && targetDefinition instanceof CoreDefinition.Aggregate
                      || occurrence.role() == CoreDefinitionRole.ENUM
                          && targetDefinition instanceof CoreDefinition.Enum
                      || occurrence.role() == CoreDefinitionRole.INTERFACE
                          && targetDefinition instanceof CoreDefinition.Interface;
              case CONSTRUCTOR ->
                  occurrence.role() == CoreDefinitionRole.CONSTRUCTOR
                      && targetDefinition instanceof CoreDefinition.Callable;
              case FUNCTION ->
                  (occurrence.role() == CoreDefinitionRole.FUNCTION
                              || occurrence.role() == CoreDefinitionRole.EXTENSION
                              || occurrence.role() == CoreDefinitionRole.METHOD)
                          && targetDefinition instanceof CoreDefinition.Callable
                      || occurrence.role() == CoreDefinitionRole.INTERFACE_METHOD
                          && targetDefinition instanceof CoreDefinition.InterfaceMethod;
              default -> false;
            };
        if (!valid) {
          throw new IllegalArgumentException(
              "annotation "
                  + definition.kind().keyword()
                  + " target has the wrong Core kind: "
                  + definition.occurrence());
        }
      }
      case CoreAnnotationTarget.Field field -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, field.owner());
        CoreDefinition targetDefinition = definition(program, field.owner().representative());
        if (occurrence.role() != CoreDefinitionRole.AGGREGATE) {
          throw new IllegalArgumentException("annotation field target has the wrong role");
        }
        if (!(targetDefinition instanceof CoreDefinition.Aggregate aggregate)) {
          throw new IllegalArgumentException("annotation field target has no fields");
        }
        List<CoreField> fields = aggregate.fields();
        if (fields.stream().noneMatch(candidate -> candidate.ordinal() == field.ordinal())) {
          throw new IllegalArgumentException("annotation field target is outside its owner");
        }
      }
      case CoreAnnotationTarget.Parameter parameter -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, parameter.callable());
        if (occurrence.role() != CoreDefinitionRole.CONSTRUCTOR
            && occurrence.role() != CoreDefinitionRole.FUNCTION
            && occurrence.role() != CoreDefinitionRole.EXTENSION
            && occurrence.role() != CoreDefinitionRole.METHOD
            && occurrence.role() != CoreDefinitionRole.INTERFACE_METHOD) {
          throw new IllegalArgumentException("annotation parameter target has the wrong role");
        }
        CoreDefinition targetDefinition =
            definition(program, parameter.callable().representative());
        int parameterCount =
            switch (targetDefinition) {
              case CoreDefinition.Callable callable -> callable.parameterTypes().size();
              case CoreDefinition.InterfaceMethod method -> method.parameterTypes().size();
              default ->
                  throw new IllegalArgumentException("annotation parameter target is not callable");
            };
        if (parameter.index() >= parameterCount) {
          throw new IllegalArgumentException("annotation parameter target is outside its callable");
        }
      }
      case CoreAnnotationTarget.Local local -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, local.callable());
        if (occurrence.role() != CoreDefinitionRole.CONSTRUCTOR
            && occurrence.role() != CoreDefinitionRole.FUNCTION
            && occurrence.role() != CoreDefinitionRole.EXTENSION
            && occurrence.role() != CoreDefinitionRole.METHOD
            && occurrence.role() != CoreDefinitionRole.LAMBDA) {
          throw new IllegalArgumentException("annotation local target has the wrong role");
        }
        CoreDefinition.Callable callable = callable(program, local.callable().representative());
        if (local.index() >= callable.locals().size()
            || callable.locals().get(local.index()).kind() != CoreLocal.Kind.VARIABLE) {
          throw new IllegalArgumentException("annotation local target is outside its callable");
        }
      }
    }
  }

  private static void verifyValue(
      CoreProgram program,
      DefinitionId valueOwner,
      DefinitionId expectedOwner,
      CoreType expected,
      CoreAnnotationValue value) {
    CoreType absoluteExpected = CoreTypes.absolute(expected, expectedOwner, program);
    CoreType absoluteActual = CoreTypes.absolute(value.type(), valueOwner, program);
    if (!absoluteExpected.equals(absoluteActual)) {
      throw new IllegalArgumentException(
          "annotation value type does not match its constructor parameter");
    }
    requireMetadataType(program, valueOwner, value.type());
    switch (value.value()) {
      case CoreAnnotationValue.Null ignored -> {
        if (!value.type().isNullable()) {
          throw new IllegalArgumentException(
              "null annotation value requires a nullable constructor parameter");
        }
      }
      case CoreAnnotationReference reference ->
          requireDeclarationReference(program, valueOwner, absoluteActual, reference);
      case CoreAnnotationValue.ListValue list -> {
        CoreType nonNull = nonNullable(absoluteActual);
        if (!isBuiltin(nonNull, "std.core.List", 1)) {
          throw new IllegalArgumentException("list value requires a List annotation value");
        }
        CoreType elementType = ((CoreType.Declared) nonNull).arguments().getFirst();
        for (CoreAnnotationValue element : list.values()) {
          verifyValue(program, valueOwner, valueOwner, elementType, element);
        }
      }
      case CoreAnnotationValue.Literal literal -> {
        CoreType nonNull = nonNullable(absoluteActual);
        Object raw = literal.value();
        boolean valid =
            nonNull.equals(CoreType.BOOLEAN) && raw instanceof Boolean
                || nonNull.equals(CoreType.CODE_POINT) && raw instanceof Integer
                || nonNull.equals(CoreType.INTEGER) && raw instanceof Integer
                || nonNull.equals(CoreType.LONG) && raw instanceof Long
                || nonNull.equals(CoreType.FLOAT) && raw instanceof Float
                || nonNull.equals(CoreType.DOUBLE) && raw instanceof Double
                || nonNull.equals(CoreType.STRING) && raw instanceof String;
        if (!valid)
          throw new IllegalArgumentException("annotation value has an invalid representation");
      }
    }
  }

  private static void requireMetadataType(CoreProgram program, DefinitionId owner, CoreType type) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    CoreType nonNull = nonNullable(absolute);
    if (!nonNull.equals(CoreType.BOOLEAN)
        && !nonNull.equals(CoreType.CODE_POINT)
        && !nonNull.equals(CoreType.INTEGER)
        && !nonNull.equals(CoreType.LONG)
        && !nonNull.equals(CoreType.FLOAT)
        && !nonNull.equals(CoreType.DOUBLE)
        && !nonNull.equals(CoreType.STRING)
        && !isMetadataEnum(program, owner, nonNull)
        && !isMetadataList(program, owner, nonNull)
        && !isDeclarationReferenceType(nonNull)) {
      throw new IllegalArgumentException(
          "annotation constructor parameter is not a metadata value");
    }
  }

  private static boolean isMetadataList(CoreProgram program, DefinitionId owner, CoreType type) {
    if (!isBuiltin(type, "std.core.List", 1)) return false;
    requireMetadataType(program, owner, ((CoreType.Declared) type).arguments().getFirst());
    return true;
  }

  private static boolean isMetadataEnum(CoreProgram program, DefinitionId owner, CoreType type) {
    if (!(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      return false;
    }
    DefinitionId id = resolve(program, owner, user.definition());
    return definition(program, id) instanceof CoreDefinition.Enum enumeration
        && enumeration.variants().stream().allMatch(variant -> variant.fields().isEmpty());
  }

  private static boolean isDeclarationReferenceType(CoreType type) {
    if (type instanceof CoreType.Function) return true;
    if (!(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)) {
      return false;
    }
    return builtin.id().value().equals("std.core.Class")
        || builtin.id().value().equals("std.core.Field");
  }

  private static void requireDeclarationReference(
      CoreProgram program,
      DefinitionId owner,
      CoreType expected,
      CoreAnnotationReference reference) {
    CoreType nonNull = nonNullable(expected);
    switch (reference) {
      case CoreAnnotationReference.ClassReference classReference -> {
        if (!isBuiltin(nonNull, "std.core.Class", 1)) {
          throw new IllegalArgumentException("class reference requires a Class annotation value");
        }
        CoreType reflected = CoreTypes.absolute(classReference.reflectedType(), owner, program);
        requireReferencedTypes(program, owner, List.of(classReference.reflectedType()));
        CoreType projected = ((CoreType.Declared) nonNull).arguments().getFirst();
        if (!projected.equals(CoreType.EXISTENTIAL) && !projected.equals(reflected)) {
          throw new IllegalArgumentException("class annotation reference type does not match");
        }
      }
      case CoreAnnotationReference.FieldReference field -> {
        if (!isBuiltin(nonNull, "std.core.Field", 2)) {
          throw new IllegalArgumentException("field reference requires a Field annotation value");
        }
        CoreType ownerType = CoreTypes.absolute(field.ownerType(), owner, program);
        CoreType valueType = CoreTypes.absolute(field.valueType(), owner, program);
        requireReferencedTypes(program, owner, List.of(field.ownerType(), field.valueType()));
        CoreType actualFieldType = reflectedFieldType(program, ownerType, field.ordinal());
        if (!actualFieldType.equals(valueType)) {
          throw new IllegalArgumentException("field annotation reference type does not match");
        }
      }
      case CoreAnnotationReference.CallableReference callableReference -> {
        if (!(nonNull instanceof CoreType.Function)) {
          throw new IllegalArgumentException(
              "callable reference requires a Function annotation value");
        }
        DefinitionId callableId = resolve(program, owner, callableReference.callable());
        CoreDefinition definition = definition(program, callableId);
        if (!(definition instanceof CoreDefinition.Callable callable)) {
          throw new IllegalArgumentException("annotation callable reference is not callable");
        }
        if (callableReference.virtual() && !callable.hasReceiver()) {
          throw new IllegalArgumentException("only methods can be virtual declaration references");
        }
        if (callableReference.receiverTypeArguments().size()
            != callable.receiverTypeParameterCount()) {
          throw new IllegalArgumentException(
              "callable annotation reference receiver type arguments do not match");
        }
        if (callableReference.reifiedArguments().size() != callable.typeParameters().size()) {
          throw new IllegalArgumentException(
              "callable annotation reference type arguments do not match");
        }
        List<CoreType> referencedTypes = new ArrayList<>(callableReference.receiverTypeArguments());
        referencedTypes.addAll(callableReference.reifiedArguments());
        requireReferencedTypes(program, owner, referencedTypes);
        CoreType.Function expectedFunction = (CoreType.Function) nonNull;
        if (!expectedFunction.returnType().equals(CoreType.EXISTENTIAL)
            || !expectedFunction.parameterTypes().isEmpty()) {
          List<CoreType> substitutions = new ArrayList<>();
          callableReference.receiverTypeArguments().stream()
              .map(type -> CoreTypes.absolute(type, owner, program))
              .forEach(substitutions::add);
          callableReference.reifiedArguments().stream()
              .map(type -> CoreTypes.absolute(type, owner, program))
              .forEach(substitutions::add);
          List<CoreType> parameters = new ArrayList<>();
          callable
              .receiverType()
              .map(type -> CoreTypes.absolute(type, callableId, program))
              .map(type -> type.substitute(substitutions::get))
              .ifPresent(parameters::add);
          callable.parameterTypes().stream()
              .map(type -> CoreTypes.absolute(type, callableId, program))
              .map(type -> type.substitute(substitutions::get))
              .forEach(parameters::add);
          CoreType result =
              CoreTypes.absolute(callable.returnType(), callableId, program)
                  .substitute(substitutions::get);
          CoreType.Function actualFunction =
              new CoreType.Function(result, parameters, CoreNullability.NON_NULL);
          if (!expectedFunction.equals(actualFunction)) {
            throw new IllegalArgumentException(
                "callable annotation reference signature does not match");
          }
        }
      }
      case CoreAnnotationReference.EnumReference enumeration -> {
        if (!(nonNull instanceof CoreType.Declared declared)
            || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
          throw new IllegalArgumentException("enum reference requires an enum annotation value");
        }
        DefinitionId id = resolve(program, owner, user.definition());
        if (!(definition(program, id) instanceof CoreDefinition.Enum enumDefinition)) {
          throw new IllegalArgumentException("enum reference requires an enum annotation value");
        }
        dev.w0fv1.norm.core.CoreEnumVariant variant =
            enumDefinition.variants().stream()
                .filter(candidate -> candidate.key().equals(enumeration.variant()))
                .findFirst()
                .orElseThrow(
                    () -> new IllegalArgumentException("annotation enum variant is absent"));
        if (!variant.fields().isEmpty()) {
          throw new IllegalArgumentException("annotation enum variant must not have a payload");
        }
      }
    }
  }

  private static void requireReferencedTypes(
      CoreProgram program, DefinitionId owner, List<CoreType> types) {
    types.stream()
        .flatMap(type -> CoreTypes.links(type).stream())
        .map(link -> resolve(program, owner, link))
        .forEach(id -> definition(program, id));
  }

  private static boolean isBuiltin(CoreType type, String identity, int arity) {
    return type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
        && builtin.id().value().equals(identity)
        && declared.arguments().size() == arity;
  }

  private static CoreType reflectedFieldType(CoreProgram program, CoreType ownerType, int ordinal) {
    CoreType current = ownerType;
    while (current instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user) {
      if (!(user.definition() instanceof DefinitionReference.External external)) {
        throw new IllegalArgumentException("annotation field owner is not absolute");
      }
      DefinitionId aggregateId = external.definition();
      CoreDefinition definition = definition(program, aggregateId);
      if (!(definition instanceof CoreDefinition.Aggregate aggregate)) break;
      for (CoreField field : aggregate.fields()) {
        if (field.ordinal() == ordinal) {
          return CoreTypes.absolute(field.type(), aggregateId, program)
              .substitute(declared.arguments()::get);
        }
      }
      if (aggregate.parentType().isEmpty()) break;
      current =
          CoreTypes.absolute(aggregate.parentType().orElseThrow(), aggregateId, program)
              .substitute(declared.arguments()::get);
    }
    throw new IllegalArgumentException("annotation field reference is absent");
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          declared.nullability() == CoreNullability.NON_NULL
              ? declared
              : new CoreType.Declared(
                  declared.constructor(),
                  declared.arguments(),
                  declared.category(),
                  CoreNullability.NON_NULL);
      case CoreType.Function function ->
          function.nullability() == CoreNullability.NON_NULL
              ? function
              : new CoreType.Function(
                  function.returnType(), function.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          parameter.nullability() == CoreNullability.NON_NULL
              ? parameter
              : new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }

  private static CoreDefinition definition(CoreProgram program, DefinitionId id) {
    return program
        .definition(id)
        .orElseThrow(() -> new IllegalArgumentException("annotation target is absent"));
  }

  private static CoreDefinition.Callable callable(CoreProgram program, DefinitionId id) {
    if (!(definition(program, id) instanceof CoreDefinition.Callable callable)) {
      throw new IllegalArgumentException("annotation target must be callable");
    }
    return callable;
  }

  private static CoreDefinitionOccurrence requireOccurrence(
      CoreAuthoringMap authoring, DefinitionOccurrenceId occurrence) {
    return authoring
        .occurrence(occurrence)
        .orElseThrow(() -> new IllegalArgumentException("annotation target occurrence is absent"));
  }

  private static DefinitionId resolve(
      CoreProgram program, DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("annotation contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  record ApplicationKey(
      DefinitionId annotation, dev.w0fv1.norm.value.AnnotationTarget kind, Object target) {}

  private record PackageKey(dev.w0fv1.norm.value.ModuleCoordinate module, String packageName) {}

  private record DefinitionKey(
      dev.w0fv1.norm.value.AnnotationTarget kind, DefinitionOccurrenceId definition) {}

  private record IndexedKey(DefinitionOccurrenceId owner, int index) {}
}
