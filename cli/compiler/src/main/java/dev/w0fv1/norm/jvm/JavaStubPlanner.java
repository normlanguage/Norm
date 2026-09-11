package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationApplication;
import dev.w0fv1.norm.core.CoreAnnotationPolicy;
import dev.w0fv1.norm.core.CoreAnnotationReference;
import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreBindingKind;
import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionLink;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypeParameter;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreVisibility;
import dev.w0fv1.norm.core.CoreWitnessTarget;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.CompilationScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class JavaStubPlanner {

  private static void applyNormAnnotations(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      CompilationScope scope,
      Set<DocumentId> excludedDocuments) {
    Map<DefinitionId, ApplicationAnnotationBinding> annotations = new LinkedHashMap<>();
    types.values().stream()
        .filter(type -> type.binding != null)
        .filter(type -> type.binding.kind() == CoreBindingKind.ANNOTATION)
        .forEach(
            type -> {
              CoreDefinition.Aggregate declaration =
                  (CoreDefinition.Aggregate)
                      artifact.program().definition(type.binding.definition()).orElseThrow();
              CoreAnnotationPolicy policy =
                  CoreAnnotationPolicy.resolve(
                      artifact.program(), type.binding.definition(), declaration);
              annotations.put(
                  type.binding.definition(),
                  new ApplicationAnnotationBinding(
                      type.key.binaryName(), type.binding, policy.inherited()));
            });
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      if (!belongsTo(application.target(), artifact, scope, excludedDocuments)) continue;
      ApplicationAnnotationBinding annotation = annotations.get(application.annotation());
      if (annotation == null) continue;
      apply(
          artifact,
          types,
          annotation.binaryName,
          annotation.inherited,
          applicationAnnotationStub(artifact, enumerations, javaTypes, annotation, application),
          application);
    }
  }

  private static AnnotationStub applicationAnnotationStub(
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      ApplicationAnnotationBinding annotation,
      CoreAnnotationApplication application) {
    CoreBindingShape.Aggregate shape = (CoreBindingShape.Aggregate) annotation.binding.shape();
    if (application.values().size() != shape.fields().size()) {
      throw new IllegalArgumentException(
          "Java annotation argument count does not match " + annotation.binaryName);
    }
    List<AnnotationArgument> arguments = new ArrayList<>();
    for (int index = 0; index < shape.fields().size(); index++) {
      CoreBindingShape.Field field = shape.fields().get(index);
      CoreAnnotationValue value = application.values().get(index);
      if (value.value() == CoreAnnotationValue.Null.INSTANCE) {
        throw new IllegalArgumentException(
            "Java-visible Norm annotation argument '" + field.name() + "' must be explicit");
      }
      arguments.add(
          new AnnotationArgument(
              field.name(),
              annotationValue(
                  artifact,
                  enumerations,
                  javaTypes,
                  application.annotation(),
                  descriptor(artifact.program(), annotation.binding.definition(), field.type()),
                  value)));
    }
    return new AnnotationStub(annotation.binaryName, arguments);
  }

  private static void addNormTypes(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      CompilationScope scope,
      Set<DocumentId> excludedDocuments) {
    if (types.isEmpty()
        && artifact.namespace().bindings().stream()
            .noneMatch(
                binding ->
                    belongsTo(binding.occurrence(), artifact, scope, excludedDocuments)
                        && hasManagedMethods(artifact, binding))) return;
    for (CoreBinding binding : artifact.namespace().bindings()) {
      if (binding.ownerName().isPresent()) continue;
      if (!belongsTo(binding.occurrence(), artifact, scope, excludedDocuments)) continue;
      Optional<CoreNominalTypeKey> nominal = nominalType(artifact.program(), binding);
      if (nominal.isEmpty()) continue;
      CoreNominalTypeKey key = nominal.orElseThrow();
      if (javaTypes.containsKey(
          new JarBindingClassReference.Nominal(key.module(), key.packageName(), key.name())))
        continue;
      ensureType(artifact, types, binding);
    }
  }

  private static boolean hasManagedMethods(CoreArtifact artifact, CoreBinding binding) {
    if (!(artifact.program().definition(binding.definition()).orElseThrow()
        instanceof CoreDefinition.Aggregate aggregate)) return false;
    return aggregate.dispatch().stream()
        .anyMatch(
            dispatch -> {
              var target =
                  artifact
                      .program()
                      .resolve(binding.definition(), (DefinitionReference) dispatch.target());
              return artifact.program().definition(target).orElseThrow()
                  instanceof CoreDefinition.MethodSignature;
            });
  }

  private static boolean belongsTo(
      CoreAnnotationTarget target,
      CoreArtifact artifact,
      CompilationScope scope,
      Set<DocumentId> excludedDocuments) {
    return switch (target) {
      case CoreAnnotationTarget.Package value ->
          scope.coordinates().entrySet().stream()
              .anyMatch(
                  entry ->
                      entry.getValue().module().equals(value.module())
                          && !excludedDocuments.contains(entry.getKey()));
      case CoreAnnotationTarget.Definition value ->
          belongsTo(value.occurrence(), artifact, scope, excludedDocuments);
      case CoreAnnotationTarget.Field value ->
          belongsTo(value.owner(), artifact, scope, excludedDocuments);
      case CoreAnnotationTarget.Parameter value ->
          belongsTo(value.callable(), artifact, scope, excludedDocuments);
      case CoreAnnotationTarget.Local value ->
          belongsTo(value.callable(), artifact, scope, excludedDocuments);
    };
  }

  private static boolean belongsTo(
      DefinitionOccurrenceId occurrence,
      CoreArtifact artifact,
      CompilationScope scope,
      Set<DocumentId> excludedDocuments) {
    DocumentId document = artifact.authoring().origin(occurrence).rootSpan().source().id();
    var coordinate = scope.coordinates().get(document);
    return coordinate != null && !excludedDocuments.contains(document);
  }

  private static Optional<CoreNominalTypeKey> nominalType(
      CoreProgram program, CoreBinding binding) {
    return switch (program.definition(binding.definition()).orElseThrow()) {
      case CoreDefinition.Aggregate aggregate -> Optional.of(aggregate.nominalType());
      case CoreDefinition.Enum enumeration -> Optional.of(enumeration.nominalType());
      case CoreDefinition.Interface implemented -> Optional.of(implemented.nominalType());
      default -> Optional.empty();
    };
  }

  private static Map<JarBindingClassReference.Nominal, String> javaTypes(
      List<ResolvedJarBinding> bindings) {
    Map<JarBindingClassReference.Nominal, String> result = new LinkedHashMap<>();
    JavaPlatformTypes.classDescriptors()
        .forEach(
            (reference, descriptor) -> {
              if (reference instanceof JarBindingClassReference.Nominal nominal
                  && descriptor.startsWith("L")
                  && descriptor.endsWith(";")) {
                result.put(
                    nominal,
                    JavaTypeNames.sourceName(
                        descriptor.substring(1, descriptor.length() - 1).replace('/', '.')));
              }
            });
    for (ResolvedJarBinding binding : bindings) {
      binding
          .generated()
          .classDescriptors()
          .forEach(
              (reference, descriptor) -> {
                if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
                  return;
                }
                String sourceName =
                    JavaTypeNames.sourceName(
                        descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
                String previous = result.putIfAbsent(reference, sourceName);
                if (previous != null && !previous.equals(sourceName)) {
                  throw new IllegalArgumentException("conflicting Java type binding " + reference);
                }
              });
    }
    return Map.copyOf(result);
  }

  private static Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumBindings(
      List<ResolvedJarBinding> bindings) {
    Map<JarBindingClassReference.Nominal, JavaEnumBinding> result = new LinkedHashMap<>();
    for (ResolvedJarBinding binding : bindings) {
      binding
          .generated()
          .enumConstants()
          .forEach(
              (reference, constants) -> {
                String descriptor = binding.generated().classDescriptors().get(reference);
                if (descriptor == null
                    || !descriptor.startsWith("L")
                    || !descriptor.endsWith(";")) {
                  throw new IllegalArgumentException(
                      "Java enum binding has no class descriptor " + reference);
                }
                JavaEnumBinding enumeration =
                    new JavaEnumBinding(
                        descriptor.substring(1, descriptor.length() - 1).replace('/', '.'),
                        constants);
                JavaEnumBinding previous = result.putIfAbsent(reference, enumeration);
                if (previous != null && !previous.equals(enumeration)) {
                  throw new IllegalArgumentException("conflicting Java enum binding " + reference);
                }
              });
    }
    return Map.copyOf(result);
  }

  private static Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotationBindings(
      List<ResolvedJarBinding> bindings) {
    Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> result = new LinkedHashMap<>();
    for (ResolvedJarBinding binding : bindings) {
      binding
          .generated()
          .annotations()
          .forEach(
              (reference, annotation) -> {
                JavaAnnotationBinding previous = result.putIfAbsent(reference, annotation);
                if (previous != null && !previous.equals(annotation)) {
                  throw new IllegalArgumentException(
                      "conflicting Java annotation binding " + reference);
                }
              });
    }
    return Map.copyOf(result);
  }

  private static Optional<JavaAnnotationBinding> annotation(
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotations,
      CoreAnnotationApplication application) {
    CoreDefinition definition =
        artifact.program().definition(application.annotation()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)
        || aggregate.kind() != CoreAggregateKind.ANNOTATION) {
      return Optional.empty();
    }
    CoreNominalTypeKey nominal = aggregate.nominalType();
    return Optional.ofNullable(
        annotations.get(
            new JarBindingClassReference.Nominal(
                nominal.module(), nominal.packageName(), nominal.name())));
  }

  private static void apply(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      JavaAnnotationBinding binding,
      CoreAnnotationApplication application) {
    AnnotationStub annotation =
        annotationStub(artifact, enumerations, javaTypes, binding, application);
    apply(
        artifact,
        types,
        binding.binaryName(),
        binding.contract().inherited(),
        annotation,
        application);
  }

  private static void apply(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      String binaryName,
      boolean inherited,
      AnnotationStub annotation,
      CoreAnnotationApplication application) {
    switch (application.target()) {
      case CoreAnnotationTarget.Definition target ->
          applyDefinition(artifact, types, inherited, target, annotation);
      case CoreAnnotationTarget.Field target -> applyField(artifact, types, target, annotation);
      case CoreAnnotationTarget.Parameter target ->
          parameterOwner(artifact, types, target.callable())
              .parameter(target.index())
              .annotations
              .add(annotation);
      case CoreAnnotationTarget.Package target ->
          throw unsupported(binaryName, target.kind(), "package annotations require package-info");
      case CoreAnnotationTarget.Local target ->
          throw unsupported(
              binaryName, target.kind(), "local annotations are not processor elements");
    }
  }

  private static CallableStub parameterOwner(
      CoreArtifact artifact, Map<TypeKey, TypeStub> types, DefinitionOccurrenceId occurrence) {
    CoreDefinitionRole role = artifact.authoring().occurrence(occurrence).orElseThrow().role();
    return role == CoreDefinitionRole.CONSTRUCTOR
        ? constructor(artifact, types, occurrence)
        : callable(artifact, types, occurrence);
  }

  private static void applyDefinition(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      boolean inherited,
      CoreAnnotationTarget.Definition target,
      AnnotationStub annotation) {
    if (target.kind() == AnnotationTarget.TYPE) {
      type(artifact, types, target.occurrence()).annotations.add(annotation);
      if (inherited) {
        addDescendants(artifact, types, target.occurrence().representative());
      }
      return;
    }
    if (target.kind() == AnnotationTarget.FUNCTION) {
      callable(artifact, types, target.occurrence()).annotations.add(annotation);
      return;
    }
    if (target.kind() == AnnotationTarget.CONSTRUCTOR) {
      constructor(artifact, types, target.occurrence()).annotations.add(annotation);
      return;
    }
    throw new IllegalArgumentException(
        "unsupported Java annotation definition target " + target.kind());
  }

  private static void addDescendants(
      CoreArtifact artifact, Map<TypeKey, TypeStub> types, DefinitionId ancestor) {
    for (CoreBinding binding : artifact.namespace().bindings()) {
      if (binding.ownerName().isPresent()
          || !(binding.shape() instanceof CoreBindingShape.Aggregate)) continue;
      if (descendsFrom(artifact.program(), binding.definition(), ancestor)) {
        ensureType(artifact, types, binding);
      }
    }
  }

  private static boolean descendsFrom(
      CoreProgram program, DefinitionId candidate, DefinitionId ancestor) {
    DefinitionId current = candidate;
    java.util.Set<DefinitionId> visited = new java.util.HashSet<>();
    while (visited.add(current)) {
      CoreDefinition definition = program.definition(current).orElse(null);
      if (!(definition instanceof CoreDefinition.Aggregate aggregate)
          || aggregate.parentType().isEmpty()) return false;
      CoreType parent = CoreTypes.absolute(aggregate.parentType().orElseThrow(), current, program);
      if (!(parent instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)
          || !(user.definition() instanceof DefinitionReference.External reference)) return false;
      current = reference.definition();
      if (current.equals(ancestor)) return true;
    }
    return false;
  }

  private static void applyField(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      CoreAnnotationTarget.Field target,
      AnnotationStub annotation) {
    TypeStub owner = type(artifact, types, target.owner());
    CoreDefinition definition =
        artifact.program().definition(target.owner().representative()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)) {
      throw new IllegalArgumentException("Java annotation field owner is not an aggregate");
    }
    int index = -1;
    for (int candidate = 0; candidate < aggregate.fields().size(); candidate++) {
      if (aggregate.fields().get(candidate).ordinal() == target.ordinal()) {
        index = candidate;
        break;
      }
    }
    if (index < 0)
      throw new IllegalArgumentException("Java annotation field is not declared by its owner");
    CoreBindingShape.Aggregate shape = (CoreBindingShape.Aggregate) owner.binding.shape();
    CoreBindingShape.Field field = shape.fields().get(index);
    owner
        .fields
        .computeIfAbsent(
            target.ordinal(),
            ignored -> new FieldStub(field.name(), field.type(), target.owner().representative()))
        .annotations
        .add(annotation);
  }

  private static TypeStub type(
      CoreArtifact artifact, Map<TypeKey, TypeStub> types, DefinitionOccurrenceId occurrence) {
    CoreBinding binding = binding(artifact, occurrence);
    if (binding.ownerName().isPresent()
        || (binding.kind() != CoreBindingKind.CLASS
            && binding.kind() != CoreBindingKind.VALUE
            && binding.kind() != CoreBindingKind.ANNOTATION
            && binding.kind() != CoreBindingKind.INTERFACE
            && binding.kind() != CoreBindingKind.ENUM)) {
      throw new IllegalArgumentException("Java annotation type target is not a JVM declaration");
    }
    return ensureType(artifact, types, binding);
  }

  private static CallableStub callable(
      CoreArtifact artifact, Map<TypeKey, TypeStub> types, DefinitionOccurrenceId occurrence) {
    CoreBinding binding = binding(artifact, occurrence);
    if (!(binding.shape() instanceof CoreBindingShape.Callable)
        && !(binding.shape() instanceof CoreBindingShape.MethodSignature)) {
      throw new IllegalArgumentException("Java annotation callable target is not a function");
    }
    TypeStub owner;
    if (binding.ownerName().isPresent()) {
      CoreBinding ownerBinding =
          artifact.namespace().bindings().stream()
              .filter(candidate -> candidate.packageName().equals(binding.packageName()))
              .filter(candidate -> candidate.ownerName().isEmpty())
              .filter(candidate -> candidate.name().equals(binding.ownerName().orElseThrow()))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalArgumentException("Java annotation method owner is absent"));
      owner = ensureType(artifact, types, ownerBinding);
    } else {
      TypeKey key = new TypeKey(binding.packageName(), "$Functions");
      owner = types.computeIfAbsent(key, ignored -> TypeStub.synthetic(key));
    }
    return owner.callables.computeIfAbsent(occurrence, ignored -> CallableStub.method(binding));
  }

  private static CallableStub constructor(
      CoreArtifact artifact, Map<TypeKey, TypeStub> types, DefinitionOccurrenceId occurrence) {
    for (CoreBinding binding : artifact.namespace().bindings()) {
      if (!(binding.shape() instanceof CoreBindingShape.Aggregate shape)) continue;
      CoreDefinition definition = artifact.program().definition(binding.definition()).orElseThrow();
      if (!(definition instanceof CoreDefinition.Aggregate aggregate)) continue;
      for (int index = 0; index < aggregate.constructors().size(); index++) {
        CoreDefinitionLink link = aggregate.constructors().get(index);
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("canonical constructor reference is pending");
        }
        DefinitionId constructor = artifact.program().resolve(binding.definition(), reference);
        if (!constructor.equals(occurrence.representative())) continue;
        TypeStub owner = ensureType(artifact, types, binding);
        int constructorIndex = index;
        return owner.callables.computeIfAbsent(
            occurrence,
            ignored ->
                new CallableStub(
                    binding.name(),
                    List.of(),
                    shape.constructors().get(constructorIndex).parameters(),
                    CoreType.VOID,
                    constructor,
                    false,
                    false,
                    true));
      }
    }
    throw new IllegalArgumentException("Java annotation constructor owner is absent");
  }

  private static CoreBinding binding(CoreArtifact artifact, DefinitionOccurrenceId occurrence) {
    return artifact.namespace().bindings().stream()
        .filter(candidate -> candidate.occurrence().equals(occurrence))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Java annotation target is not bound"));
  }

  private static TypeStub ensureType(
      CoreArtifact artifact, Map<TypeKey, TypeStub> types, CoreBinding binding) {
    TypeKey key = new TypeKey(binding.packageName(), binding.name());
    TypeStub existing = types.get(key);
    if (existing != null) return existing;
    TypeStub created = new TypeStub(key, binding, false);
    types.put(key, created);
    if (binding.shape() instanceof CoreBindingShape.Interface) {
      artifact.namespace().bindings().stream()
          .filter(candidate -> candidate.packageName().equals(binding.packageName()))
          .filter(candidate -> candidate.ownerName().equals(Optional.of(binding.name())))
          .filter(candidate -> candidate.shape() instanceof CoreBindingShape.MethodSignature)
          .sorted(Comparator.comparing(CoreBinding::occurrence))
          .forEach(
              method -> created.callables.put(method.occurrence(), CallableStub.method(method)));
    }
    if (binding.shape() instanceof CoreBindingShape.Aggregate shape) {
      CoreDefinition.Aggregate aggregate =
          (CoreDefinition.Aggregate)
              artifact.program().definition(binding.definition()).orElseThrow();
      for (int index = 0; index < shape.fields().size(); index++) {
        CoreBindingShape.Field field = shape.fields().get(index);
        if (field.visibility() != CoreVisibility.PUBLIC) continue;
        CoreField declaration = aggregate.fields().get(index);
        created.fields.put(
            declaration.ordinal(),
            new FieldStub(field.name(), field.type(), binding.occurrence().representative()));
      }
      for (int index = 0; index < shape.constructors().size(); index++) {
        CoreDefinitionLink link = aggregate.constructors().get(index);
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("canonical constructor reference is pending");
        }
        DefinitionId definition = artifact.program().resolve(binding.definition(), reference);
        Optional<CoreBinding> constructorBinding =
            artifact.namespace().bindings().stream()
                .filter(candidate -> candidate.definition().equals(definition))
                .filter(
                    candidate ->
                        candidate.shape() instanceof CoreBindingShape.Callable
                            || candidate.shape() instanceof CoreBindingShape.MethodSignature)
                .findFirst();
        DefinitionOccurrenceId occurrence =
            constructorBinding
                .map(CoreBinding::occurrence)
                .orElseGet(
                    () ->
                        artifact.authoring().occurrences(definition).stream()
                            .filter(candidate -> candidate.role() == CoreDefinitionRole.CONSTRUCTOR)
                            .findFirst()
                            .orElseThrow()
                            .id());
        created.callables.put(
            occurrence,
            new CallableStub(
                binding.name(),
                List.of(),
                shape.constructors().get(index).parameters(),
                CoreType.VOID,
                definition,
                false,
                artifact
                    .authoring()
                    .origin(occurrence)
                    .rootSpan()
                    .equals(artifact.authoring().origin(binding.occurrence()).rootSpan()),
                true));
      }
      artifact.namespace().bindings().stream()
          .filter(candidate -> candidate.packageName().equals(binding.packageName()))
          .filter(candidate -> candidate.ownerName().equals(Optional.of(binding.name())))
          .filter(candidate -> candidate.visibility() == CoreVisibility.PUBLIC)
          .filter(
              candidate ->
                  candidate.shape() instanceof CoreBindingShape.Callable
                      || candidate.shape() instanceof CoreBindingShape.MethodSignature)
          .sorted(Comparator.comparing(CoreBinding::occurrence))
          .forEach(
              method ->
                  created.callables.putIfAbsent(method.occurrence(), CallableStub.method(method)));
      for (var conformance : aggregate.conformances()) {
        for (var witness : conformance.witnesses()) {
          if (!(witness.implementation() instanceof CoreWitnessTarget.Callable callable)) continue;
          if (!(callable.definition() instanceof DefinitionReference reference)) {
            throw new IllegalArgumentException("Java conformance implementation is pending");
          }
          DefinitionId definition = artifact.program().resolve(binding.definition(), reference);
          if (!(artifact.program().definition(definition).orElseThrow()
              instanceof CoreDefinition.Callable callableDefinition)) {
            throw new IllegalArgumentException("Java conformance implementation is not callable");
          }
          if (!(witness.requirement() instanceof DefinitionReference requirementReference)) {
            throw new IllegalArgumentException("Java conformance requirement is pending");
          }
          DefinitionId requirement =
              artifact.program().resolve(binding.definition(), requirementReference);
          if (!(artifact.program().definition(requirement).orElseThrow()
              instanceof CoreDefinition.MethodSignature interfaceMethod)) {
            throw new IllegalArgumentException("Java conformance requirement is not a method");
          }
          List<CoreBinding> implementations =
              artifact.namespace().bindings().stream()
                  .filter(candidate -> candidate.packageName().equals(binding.packageName()))
                  .filter(candidate -> candidate.ownerName().equals(Optional.of(binding.name())))
                  .filter(candidate -> candidate.shape() instanceof CoreBindingShape.Callable)
                  .filter(candidate -> candidate.name().equals(interfaceMethod.name()))
                  .filter(
                      candidate ->
                          ((CoreBindingShape.Callable) candidate.shape()).parameters().size()
                              == callableDefinition.parameterTypes().size())
                  .sorted(Comparator.comparing(CoreBinding::occurrence))
                  .toList();
          if (implementations.isEmpty()) {
            continue;
          }
          List<CoreBinding> matchingImplementations =
              implementations.stream()
                  .filter(
                      candidate ->
                          matchesCallableSignature(
                              artifact.program(), candidate, definition, callableDefinition))
                  .toList();
          if (matchingImplementations.isEmpty()) {
            List<List<CoreType>> signatures =
                implementations.stream()
                    .map(candidate -> callableParameterTypes(artifact.program(), candidate))
                    .distinct()
                    .toList();
            if (signatures.size() != 1) {
              throw new IllegalArgumentException(
                  "Java conformance implementation is ambiguous: "
                      + binding.name()
                      + "."
                      + interfaceMethod.name());
            }
            matchingImplementations = implementations;
          }
          CoreBinding implementation = matchingImplementations.getFirst();
          CoreBindingShape.Callable callableShape =
              (CoreBindingShape.Callable) implementation.shape();
          created.callables.putIfAbsent(
              implementation.occurrence(),
              new CallableStub(
                  implementation.name(),
                  callableShape.typeParameters(),
                  callableShape.parameters(),
                  callableShape.returnType(),
                  implementation.definition(),
                  false,
                  false,
                  false));
        }
      }
    }
    if (binding.shape() instanceof CoreBindingShape.Aggregate shape
        && shape.parentType().isPresent()) {
      CoreType parent =
          CoreTypes.absolute(
              shape.parentType().orElseThrow(), binding.definition(), artifact.program());
      if (parent instanceof CoreType.Declared declared
          && declared.constructor() instanceof CoreTypeConstructor.User user
          && user.definition() instanceof DefinitionReference.External reference) {
        artifact.namespace().bindings().stream()
            .filter(candidate -> candidate.ownerName().isEmpty())
            .filter(candidate -> candidate.definition().equals(reference.definition()))
            .findFirst()
            .ifPresent(candidate -> ensureType(artifact, types, candidate));
      }
    }
    return created;
  }

  private static boolean matchesCallableSignature(
      CoreProgram program,
      CoreBinding candidate,
      DefinitionId implementation,
      CoreDefinition.Callable callable) {
    CoreBindingShape.Callable shape = (CoreBindingShape.Callable) candidate.shape();
    if (shape.parameters().size() != callable.parameterTypes().size()) return false;
    List<CoreType> candidateTypes = callableParameterTypes(program, candidate);
    for (int index = 0; index < shape.parameters().size(); index++) {
      CoreType implementationType =
          CoreTypes.absolute(callable.parameterTypes().get(index), implementation, program);
      if (!candidateTypes.get(index).equals(implementationType)) return false;
    }
    return true;
  }

  private static List<CoreType> callableParameterTypes(CoreProgram program, CoreBinding binding) {
    CoreBindingShape.Callable shape = (CoreBindingShape.Callable) binding.shape();
    return shape.parameters().stream()
        .map(parameter -> CoreTypes.absolute(parameter.type(), binding.definition(), program))
        .toList();
  }

  private static AnnotationStub annotationStub(
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      JavaAnnotationBinding binding,
      CoreAnnotationApplication application) {
    if (application.values().size() != binding.elements().size()) {
      throw new IllegalArgumentException(
          "Java annotation argument count does not match " + binding.binaryName());
    }
    List<AnnotationArgument> arguments = new ArrayList<>();
    for (int index = 0; index < binding.elements().size(); index++) {
      JavaAnnotationElementBinding element = binding.elements().get(index);
      CoreAnnotationValue value = application.values().get(index);
      if (value.value() == CoreAnnotationValue.Null.INSTANCE) {
        if (element.defaultValue().isEmpty()) {
          throw new IllegalArgumentException(
              "Java annotation argument '" + element.name() + "' is required");
        }
        continue;
      }
      arguments.add(
          new AnnotationArgument(
              element.name(),
              annotationValue(
                  artifact, enumerations, javaTypes, application.annotation(), element, value)));
    }
    return new AnnotationStub(binding.binaryName(), arguments);
  }

  private static String annotationValue(
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      DefinitionId owner,
      JavaAnnotationElementBinding element,
      CoreAnnotationValue value) {
    String returnDescriptor = element.descriptor().substring(element.descriptor().indexOf(')') + 1);
    return annotationValue(artifact, enumerations, javaTypes, owner, returnDescriptor, value);
  }

  private static String annotationValue(
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      DefinitionId owner,
      String returnDescriptor,
      CoreAnnotationValue value) {
    return switch (value.value()) {
      case CoreAnnotationValue.Literal literal -> literal(literal.value(), returnDescriptor);
      case CoreAnnotationValue.ListValue list ->
          "{"
              + list.values().stream()
                  .map(
                      item ->
                          annotationValue(
                              artifact, enumerations, javaTypes, owner, returnDescriptor, item))
                  .collect(java.util.stream.Collectors.joining(", "))
              + "}";
      case CoreAnnotationReference.ClassReference reference ->
          javaType(artifact.program(), owner, reference.reflectedType(), javaTypes) + ".class";
      case CoreAnnotationReference.FieldReference reference ->
          throw new IllegalArgumentException(
              "Java annotation values cannot reference a field: " + reference);
      case CoreAnnotationReference.EnumReference reference ->
          javaEnumValue(artifact.program(), enumerations, owner, value.type(), reference.variant());
      case CoreAnnotationReference.CallableReference reference ->
          throw new IllegalArgumentException(
              "Java annotation values cannot reference a callable: " + reference);
      case CoreAnnotationValue.Null ignored ->
          throw new IllegalArgumentException("Java annotation values cannot be null");
    };
  }

  private static String descriptor(CoreProgram program, DefinitionId owner, CoreType type) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (absolute instanceof CoreType.Reference reference) {
      return descriptor(program, owner, reference.target());
    }
    if (absolute instanceof CoreType.Declared declared) {
      if (declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
        return switch (builtin.id().value()) {
          case "std.core.Boolean" -> "Z";
          case "std.core.Integer", "std.core.CodePoint" -> "I";
          case "std.core.Long" -> "J";
          case "std.core.Float" -> "F";
          case "std.core.Double" -> "D";
          case "std.core.String" -> "Ljava/lang/String;";
          default -> "Ljava/lang/Object;";
        };
      }
      if (declared.constructor() instanceof CoreTypeConstructor.User user
          && user.definition() instanceof DefinitionReference.External reference) {
        CoreDefinition definition = program.definition(reference.definition()).orElseThrow();
        CoreNominalTypeKey nominal =
            switch (definition) {
              case CoreDefinition.Aggregate aggregate -> aggregate.nominalType();
              case CoreDefinition.Enum enumeration -> enumeration.nominalType();
              case CoreDefinition.Interface implemented -> implemented.nominalType();
              default -> null;
            };
        if (nominal != null) return "L" + binaryName(nominal).replace('.', '/') + ";";
      }
    }
    return "Ljava/lang/Object;";
  }

  private static String javaEnumValue(
      CoreProgram program,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      DefinitionId owner,
      CoreType type,
      String variant) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (!(absolute instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference.External external)
        || !(program.definition(external.definition()).orElseThrow()
            instanceof CoreDefinition.Enum enumeration)) {
      throw new IllegalArgumentException("Java annotation enum value has an invalid Norm type");
    }
    CoreNominalTypeKey nominal = enumeration.nominalType();
    JavaEnumBinding binding =
        enumerations.get(
            new JarBindingClassReference.Nominal(
                nominal.module(), nominal.packageName(), nominal.name()));
    if (binding == null || !binding.constants().containsKey(variant)) {
      throw new IllegalArgumentException(
          "Java annotation enum variant is not bound: " + nominal.name() + "." + variant);
    }
    return binding.binaryName().replace('$', '.') + "." + binding.constants().get(variant);
  }

  private static String literal(Object value, String descriptor) {
    return switch (value) {
      case Boolean item -> item.toString();
      case Integer item -> integral(item.longValue(), descriptor);
      case Long item -> integral(item, descriptor);
      case Float item -> floating(item.doubleValue(), item.toString(), descriptor);
      case Double item -> floating(item, item.toString(), descriptor);
      case String item -> stringLiteral(item);
      default -> throw new IllegalArgumentException("unsupported Java annotation literal " + value);
    };
  }

  private static String integral(long value, String descriptor) {
    return switch (descriptor) {
      case "B" -> "(byte) " + value;
      case "C" -> "(char) " + value;
      case "S" -> "(short) " + value;
      case "J" -> value + "L";
      default -> Long.toString(value);
    };
  }

  private static String floating(double value, String literal, String descriptor) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("non-finite Java annotation value " + literal);
    }
    return descriptor.equals("F") ? literal + "F" : literal;
  }

  private static Optional<String> generatedParent(
      TypeStub type,
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<String> generatedTypes) {
    if (type.binding == null
        || !(type.binding.shape() instanceof CoreBindingShape.Aggregate shape)
        || shape.parentType().isEmpty()) {
      return Optional.empty();
    }
    String parent =
        javaType(
            artifact.program(),
            type.binding.definition(),
            shape.parentType().orElseThrow(),
            javaTypes);
    int arguments = parent.indexOf('<');
    String raw = arguments < 0 ? parent : parent.substring(0, arguments);
    return generatedTypes.contains(raw) ? Optional.of(raw) : Optional.empty();
  }

  private static String javaType(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    return javaType(program, owner, type, javaTypes, name -> true);
  }

  private static String javaType(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<String> generatedTypes) {
    return javaType(
        program,
        owner,
        type,
        javaTypes,
        name -> name.startsWith("java.lang.") || generatedTypes.contains(name));
  }

  private static String javaType(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      java.util.function.Predicate<String> available) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (absolute.equals(CoreType.VOID)) return "void";
    if (absolute instanceof CoreType.Function function) {
      boolean returnsVoid = function.returnType().equals(CoreType.VOID);
      var shape = JavaFunctionShape.of(function.parameterTypes().size(), returnsVoid);
      if (shape.isEmpty()) return "java.lang.Object";
      List<CoreType> arguments = new ArrayList<>(function.parameterTypes());
      if (!returnsVoid) arguments.add(function.returnType());
      return shape.orElseThrow().binaryName()
          + (arguments.isEmpty()
              ? ""
              : arguments.stream()
                  .map(argument -> javaTypeArgument(program, owner, argument, javaTypes, available))
                  .collect(java.util.stream.Collectors.joining(", ", "<", ">")));
    }
    if (absolute instanceof CoreType.Reference reference) {
      return javaType(program, owner, reference.target(), javaTypes, available);
    }
    if (absolute instanceof CoreType.Parameter parameter) {
      return "T" + parameter.index();
    }
    if (absolute instanceof CoreType.Declared declared) {
      if (declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
        String descriptor =
            JavaPlatformTypes.classDescriptors()
                .get(new JarBindingClassReference.Builtin(builtin.id().value()));
        if (!declared.arguments().isEmpty() && descriptor != null && descriptor.startsWith("L")) {
          return JavaTypeNames.sourceName(
                  descriptor.substring(1, descriptor.length() - 1).replace('/', '.'))
              + declared.arguments().stream()
                  .map(argument -> javaTypeArgument(program, owner, argument, javaTypes, available))
                  .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
        }
        boolean nullable = declared.nullability() == CoreNullability.NULLABLE;
        return switch (builtin.id().value()) {
          case "std.core.Boolean" -> nullable ? "java.lang.Boolean" : "boolean";
          case "std.core.Integer" -> nullable ? "java.lang.Integer" : "int";
          case "std.core.Long" -> nullable ? "java.lang.Long" : "long";
          case "std.core.Float" -> nullable ? "java.lang.Float" : "float";
          case "std.core.Double" -> nullable ? "java.lang.Double" : "double";
          case "std.core.CodePoint" -> nullable ? "java.lang.Integer" : "int";
          case "std.core.String" -> "java.lang.String";
          default -> "java.lang.Object";
        };
      }
      if (declared.constructor() instanceof CoreTypeConstructor.User user) {
        DefinitionReference.External reference = (DefinitionReference.External) user.definition();
        CoreDefinition declaration = program.definition(reference.definition()).orElseThrow();
        CoreNominalTypeKey nominal =
            switch (declaration) {
              case CoreDefinition.Aggregate aggregate -> aggregate.nominalType();
              case CoreDefinition.Enum enumeration -> enumeration.nominalType();
              case CoreDefinition.Interface implemented -> implemented.nominalType();
              default -> null;
            };
        if (nominal == null) return "java.lang.Object";
        String binaryName =
            javaTypes.getOrDefault(
                new JarBindingClassReference.Nominal(
                    nominal.module(), nominal.packageName(), nominal.name()),
                binaryName(nominal));
        if (!available.test(binaryName)) return "java.lang.Object";
        if (declared.arguments().isEmpty()) return binaryName;
        return binaryName
            + declared.arguments().stream()
                .map(argument -> javaTypeArgument(program, owner, argument, javaTypes, available))
                .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
      }
    }
    return "java.lang.Object";
  }

  private static String javaTypeArgument(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      java.util.function.Predicate<String> available) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (absolute instanceof CoreType.Reference reference) {
      return javaTypeArgument(program, owner, reference.target(), javaTypes, available);
    }
    String projected = javaType(program, owner, absolute, javaTypes, available);
    String boxed =
        switch (projected) {
          case "boolean" -> "java.lang.Boolean";
          case "int" -> "java.lang.Integer";
          case "long" -> "java.lang.Long";
          case "float" -> "java.lang.Float";
          case "double" -> "java.lang.Double";
          default -> projected;
        };
    return nullableType(absolute, boxed);
  }

  private static String nullableType(CoreType type, String projected) {
    if (!type.isNullable()) return projected;
    int arguments = projected.indexOf('<');
    int qualifier = projected.lastIndexOf('.', arguments < 0 ? projected.length() : arguments) + 1;
    return projected.substring(0, qualifier)
        + "@org.jspecify.annotations.Nullable "
        + projected.substring(qualifier);
  }

  private static String binaryName(CoreNominalTypeKey nominal) {
    return JavaApplicationTypeName.binaryName(nominal);
  }

  private static String stringLiteral(String value) {
    StringBuilder result = new StringBuilder("\"");
    value
        .codePoints()
        .forEach(
            character -> {
              switch (character) {
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                default -> {
                  if (Character.isISOControl(character)) {
                    result.append("\\u").append("%04x".formatted(character));
                  } else {
                    result.appendCodePoint(character);
                  }
                }
              }
            });
    return result.append('"').toString();
  }

  private static IllegalArgumentException unsupported(
      String binaryName, AnnotationTarget target, String reason) {
    return new IllegalArgumentException(
        "cannot lower Java annotation " + binaryName + " on " + target + ": " + reason);
  }

  private record TypeKey(String packageName, String name) {
    private TypeKey {
      Objects.requireNonNull(packageName, "packageName");
      Objects.requireNonNull(name, "name");
    }

    private String binaryName() {
      return javaPackageName() + "." + name;
    }

    private String javaPackageName() {
      return JavaApplicationTypeName.packageName(packageName);
    }
  }

  private static final class TypeStub {
    private final TypeKey key;
    private final CoreBinding binding;
    private final boolean synthetic;
    private final List<AnnotationStub> annotations = new ArrayList<>();
    private final Map<Integer, FieldStub> fields = new LinkedHashMap<>();
    private final Map<DefinitionOccurrenceId, CallableStub> callables = new LinkedHashMap<>();

    private TypeStub(TypeKey key, CoreBinding binding, boolean synthetic) {
      this.key = key;
      this.binding = binding;
      this.synthetic = synthetic;
    }

    private static TypeStub synthetic(TypeKey key) {
      return new TypeStub(key, null, true);
    }
  }

  private static final class CallableStub {
    private final String name;
    private final List<CoreTypeParameter> typeParameters;
    private final List<ParameterStub> parameters;
    private final CoreType returnType;
    private final DefinitionId owner;
    private DefinitionId invocation;
    private final boolean isStatic;
    private final boolean implicitConstructor;
    private final boolean constructor;
    private final List<AnnotationStub> annotations = new ArrayList<>();

    private CallableStub(
        String name,
        List<CoreTypeParameter> typeParameters,
        List<CoreBindingShape.Parameter> parameters,
        CoreType returnType,
        DefinitionId owner,
        boolean isStatic,
        boolean implicitConstructor,
        boolean constructor) {
      this.name = name;
      this.typeParameters = List.copyOf(typeParameters);
      this.parameters =
          parameters.stream()
              .map(parameter -> new ParameterStub(parameter.label(), parameter.type()))
              .toList();
      this.returnType = returnType;
      this.owner = owner;
      this.invocation = owner;
      this.isStatic = isStatic;
      this.implicitConstructor = implicitConstructor;
      this.constructor = constructor;
    }

    private static CallableStub method(CoreBinding binding) {
      return switch (binding.shape()) {
        case CoreBindingShape.Callable shape ->
            new CallableStub(
                binding.name(),
                shape.typeParameters(),
                shape.parameters(),
                shape.returnType(),
                binding.definition(),
                binding.ownerName().isEmpty(),
                false,
                false);
        case CoreBindingShape.MethodSignature shape ->
            new CallableStub(
                binding.name(),
                shape.typeParameters(),
                shape.parameters(),
                shape.returnType(),
                binding.definition(),
                false,
                false,
                false);
        default ->
            throw new IllegalArgumentException("Java callable binding has no method signature");
      };
    }

    private ParameterStub parameter(int index) {
      if (index < 0 || index >= parameters.size()) {
        throw new IllegalArgumentException("Java annotation parameter is outside its callable");
      }
      return parameters.get(index);
    }
  }

  private static final class ParameterStub {
    private final String name;
    private final CoreType type;
    private final List<AnnotationStub> annotations = new ArrayList<>();

    private ParameterStub(String name, CoreType type) {
      this.name = name;
      this.type = type;
    }
  }

  private static final class FieldStub {
    private final String name;
    private final CoreType type;
    private final DefinitionId owner;
    private final List<AnnotationStub> annotations = new ArrayList<>();

    private FieldStub(String name, CoreType type, DefinitionId owner) {
      this.name = name;
      this.type = type;
      this.owner = owner;
    }
  }

  private record AnnotationStub(String binaryName, List<AnnotationArgument> arguments) {
    private AnnotationStub {
      arguments = List.copyOf(arguments);
    }
  }

  private record AnnotationArgument(String name, String value) {}

  private record ApplicationAnnotationBinding(
      String binaryName, CoreBinding binding, boolean inherited) {}

  private record JavaEnumBinding(String binaryName, Map<String, String> constants) {
    private JavaEnumBinding {
      Objects.requireNonNull(binaryName, "binaryName");
      constants = Map.copyOf(constants);
    }
  }

  JavaStubPlan plan(
      CoreArtifact artifact,
      List<ResolvedJarBinding> bindings,
      CompilationScope scope,
      DocumentId entryDocument,
      Set<DocumentId> bindingDocuments) {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(bindings, "bindings");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(entryDocument, "entryDocument");
    bindingDocuments = Set.copyOf(bindingDocuments);
    Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotations =
        annotationBindings(bindings);
    Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations = enumBindings(bindings);
    Map<JarBindingClassReference.Nominal, String> javaTypes = javaTypes(bindings);
    Map<TypeKey, TypeStub> types = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      if (!belongsTo(application.target(), artifact, scope, bindingDocuments)) continue;
      Optional<JavaAnnotationBinding> annotation = annotation(artifact, annotations, application);
      if (annotation.isEmpty()) continue;
      apply(artifact, types, enumerations, javaTypes, annotation.orElseThrow(), application);
    }
    addNormTypes(artifact, types, javaTypes, scope, bindingDocuments);
    applyNormAnnotations(artifact, types, enumerations, javaTypes, scope, bindingDocuments);
    Map<DefinitionId, DefinitionId> interfaceDefaults = new LinkedHashMap<>();
    for (var record : artifact.program().definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Aggregate aggregate)) continue;
      for (var conformance : aggregate.conformances()) {
        for (var witness : conformance.witnesses()) {
          if (!(witness.requirement() instanceof DefinitionReference requirement)
              || !(witness.implementation() instanceof CoreWitnessTarget.Callable target)
              || !(target.definition() instanceof DefinitionReference reference)) continue;
          DefinitionId requirementId = artifact.program().resolve(record.id(), requirement);
          DefinitionId targetId = artifact.program().resolve(record.id(), reference);
          var method =
              (CoreDefinition.MethodSignature)
                  artifact.program().definition(requirementId).orElseThrow();
          var callable =
              (CoreDefinition.Callable) artifact.program().definition(targetId).orElseThrow();
          CoreType receiver =
              CoreTypes.absolute(
                  callable.receiverType().orElseThrow(), targetId, artifact.program());
          CoreType contract =
              CoreTypes.absolute(method.receiverType(), requirementId, artifact.program());
          if (receiver instanceof CoreType.Declared declared
              && contract instanceof CoreType.Declared expected
              && declared.constructor().equals(expected.constructor())) {
            interfaceDefaults.put(requirementId, targetId);
          }
        }
      }
    }
    for (TypeStub type : types.values()) {
      if (type.binding == null || type.binding.kind() != CoreBindingKind.INTERFACE) continue;
      for (CallableStub callable : type.callables.values()) {
        callable.invocation = interfaceDefaults.getOrDefault(callable.owner, callable.owner);
      }
    }
    Set<String> generatedTypes =
        java.util.stream.Stream.concat(
                types.keySet().stream().map(TypeKey::binaryName), javaTypes.values().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    Set<String> generatedParents =
        types.values().stream()
            .map(type -> generatedParent(type, artifact, javaTypes, generatedTypes))
            .flatMap(Optional::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new JavaStubPlan(
        types.values().stream()
            .sorted(Comparator.comparing(type -> type.key.binaryName()))
            .map(
                type ->
                    typePlan(
                        type,
                        artifact,
                        javaTypes,
                        generatedTypes,
                        generatedParents.contains(type.key.binaryName())))
            .toList());
  }

  private static JavaStubPlan.Type typePlan(
      TypeStub type,
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<String> generatedTypes,
      boolean generatedParentType) {
    JavaStubPlan.TypeKind kind =
        type.synthetic
            ? JavaStubPlan.TypeKind.CLASS
            : switch (type.binding.kind()) {
              case CLASS -> JavaStubPlan.TypeKind.CLASS;
              case VALUE -> JavaStubPlan.TypeKind.VALUE;
              case INTERFACE -> JavaStubPlan.TypeKind.INTERFACE;
              case ENUM -> JavaStubPlan.TypeKind.ENUM;
              case ANNOTATION -> JavaStubPlan.TypeKind.ANNOTATION;
              default ->
                  throw new IllegalArgumentException(
                      "unsupported annotated JVM type " + type.binding.kind());
            };
    boolean annotation = kind == JavaStubPlan.TypeKind.ANNOTATION;
    List<JavaStubPlan.TypeParameter> parameters = List.of();
    if (!annotation && type.binding != null) {
      List<CoreTypeParameter> declared =
          switch (type.binding.shape()) {
            case CoreBindingShape.Aggregate shape -> shape.typeParameters();
            case CoreBindingShape.Interface shape -> shape.typeParameters();
            default -> List.of();
          };
      parameters =
          typeParameters(artifact.program(), type.binding.definition(), declared, javaTypes);
    }
    Optional<String> parentType = Optional.empty();
    List<String> interfaces = List.of();
    if (!annotation
        && type.binding != null
        && type.binding.shape() instanceof CoreBindingShape.Aggregate shape) {
      parentType =
          shape
              .parentType()
              .map(
                  parent ->
                      javaType(
                          artifact.program(),
                          type.binding.definition(),
                          parent,
                          javaTypes,
                          generatedTypes));
      interfaces =
          shape.conformances().stream()
              .map(
                  conformance ->
                      javaType(
                          artifact.program(),
                          type.binding.definition(),
                          conformance,
                          javaTypes,
                          generatedTypes))
              .filter(
                  conformance -> {
                    int arguments = conformance.indexOf('<');
                    return generatedTypes.contains(
                        arguments < 0 ? conformance : conformance.substring(0, arguments));
                  })
              .toList();
    } else if (type.binding != null
        && type.binding.shape() instanceof CoreBindingShape.Interface shape) {
      interfaces =
          shape.directParents().stream()
              .map(
                  parent ->
                      javaType(
                          artifact.program(),
                          type.binding.definition(),
                          parent,
                          javaTypes,
                          generatedTypes))
              .filter(parent -> !parent.equals("java.lang.Object"))
              .toList();
    }
    List<String> variants =
        type.binding != null && type.binding.shape() instanceof CoreBindingShape.Enum shape
            ? shape.variants().stream().map(variant -> variant.name()).toList()
            : List.of();
    var annotations = new ArrayList<JavaStubPlan.Annotation>();
    if (annotation) {
      var declaration =
          (CoreDefinition.Aggregate)
              artifact.program().definition(type.binding.definition()).orElseThrow();
      CoreAnnotationPolicy policy =
          CoreAnnotationPolicy.resolve(artifact.program(), type.binding.definition(), declaration);
      var targets = new ArrayList<String>();
      policy.targets().stream()
          .sorted()
          .forEach(
              target -> {
                switch (target) {
                  case PACKAGE -> targets.add("java.lang.annotation.ElementType.PACKAGE");
                  case TYPE -> {
                    targets.add("java.lang.annotation.ElementType.TYPE");
                    targets.add("java.lang.annotation.ElementType.ANNOTATION_TYPE");
                  }
                  case FIELD -> targets.add("java.lang.annotation.ElementType.FIELD");
                  case CONSTRUCTOR -> targets.add("java.lang.annotation.ElementType.CONSTRUCTOR");
                  case FUNCTION -> targets.add("java.lang.annotation.ElementType.METHOD");
                  case PARAMETER -> targets.add("java.lang.annotation.ElementType.PARAMETER");
                  case LOCAL -> targets.add("java.lang.annotation.ElementType.LOCAL_VARIABLE");
                }
              });
      annotations.add(
          new JavaStubPlan.Annotation(
              "java.lang.annotation.Target",
              List.of(
                  new JavaStubPlan.AnnotationArgument(
                      Optional.empty(), "{" + String.join(", ", targets) + "}"))));
      String retention =
          switch (policy.retention()) {
            case SOURCE -> "SOURCE";
            case BINARY -> "CLASS";
            case RUNTIME -> "RUNTIME";
          };
      annotations.add(
          new JavaStubPlan.Annotation(
              "java.lang.annotation.Retention",
              List.of(
                  new JavaStubPlan.AnnotationArgument(
                      Optional.empty(), "java.lang.annotation.RetentionPolicy." + retention))));
      if (policy.inherited())
        annotations.add(new JavaStubPlan.Annotation("java.lang.annotation.Inherited", List.of()));
    }
    annotations.addAll(annotations(type.annotations));
    List<JavaStubPlan.Field> fields =
        type.fields.values().stream()
            .map(
                field ->
                    new JavaStubPlan.Field(
                        field.name,
                        nullableType(
                            field.type,
                            javaType(
                                artifact.program(),
                                field.owner,
                                field.type,
                                javaTypes,
                                generatedTypes)),
                        annotations(field.annotations)))
            .toList();
    boolean generatedParent =
        generatedParent(type, artifact, javaTypes, generatedTypes).isPresent();
    List<JavaStubPlan.Callable> callables =
        annotation
            ? List.of()
            : type.callables.values().stream()
                .sorted(
                    Comparator.comparingInt(
                        callable ->
                            callable.implicitConstructor && callable.parameters.isEmpty() ? 0 : 1))
                .map(callable -> callablePlan(type, callable, artifact, javaTypes, generatedTypes))
                .toList();
    boolean hasZeroArgumentConstructor =
        type.callables.values().stream()
            .anyMatch(callable -> callable.constructor && callable.parameters.isEmpty());
    boolean hasExplicitConstructor =
        type.callables.values().stream()
            .anyMatch(callable -> callable.constructor && !callable.implicitConstructor);
    Optional<String> allocation =
        !type.synthetic
                && kind == JavaStubPlan.TypeKind.CLASS
                && hasExplicitConstructor
                && !hasZeroArgumentConstructor
            ? Optional.of(stringLiteral(type.binding.definition().toString()))
            : Optional.empty();
    return new JavaStubPlan.Type(
        type.key.binaryName(),
        type.key.javaPackageName(),
        type.key.name(),
        kind,
        kind == JavaStubPlan.TypeKind.CLASS
            && !type.synthetic
            && hasManagedMethods(artifact, type.binding),
        parameters,
        parentType,
        interfaces,
        variants,
        annotations,
        fields,
        callables,
        !annotation
            && !type.synthetic
            && kind != JavaStubPlan.TypeKind.INTERFACE
            && kind != JavaStubPlan.TypeKind.ENUM
            && generatedParentType,
        generatedParent,
        allocation);
  }

  private static JavaStubPlan.Callable callablePlan(
      TypeStub owner,
      CallableStub callable,
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<String> generatedTypes) {
    boolean interfaceMethod =
        owner.binding != null && owner.binding.kind() == CoreBindingKind.INTERFACE;
    boolean declaration =
        artifact.program().definition(callable.invocation).orElseThrow()
            instanceof CoreDefinition.MethodSignature;
    JavaStubPlan.CallableKind kind =
        declaration
            ? (interfaceMethod
                ? JavaStubPlan.CallableKind.INTERFACE_METHOD
                : JavaStubPlan.CallableKind.ABSTRACT_METHOD)
            : callable.constructor
                ? JavaStubPlan.CallableKind.CONSTRUCTOR
                : interfaceMethod
                    ? JavaStubPlan.CallableKind.DEFAULT_METHOD
                    : JavaStubPlan.CallableKind.METHOD;
    String projected =
        callable.constructor
            ? "void"
            : javaType(
                artifact.program(), callable.owner, callable.returnType, javaTypes, generatedTypes);
    Optional<String> cast =
        projected.equals("void")
            ? Optional.empty()
            : Optional.of(
                switch (projected) {
                  case "boolean" -> "java.lang.Boolean";
                  case "int" -> "java.lang.Integer";
                  case "long" -> "java.lang.Long";
                  case "float" -> "java.lang.Float";
                  case "double" -> "java.lang.Double";
                  default -> projected;
                });
    List<JavaStubPlan.Parameter> parameters =
        callable.parameters.stream()
            .map(
                parameter ->
                    new JavaStubPlan.Parameter(
                        parameter.name,
                        nullableType(
                            parameter.type,
                            javaType(
                                artifact.program(),
                                callable.owner,
                                parameter.type,
                                javaTypes,
                                generatedTypes)),
                        annotations(parameter.annotations)))
            .toList();
    return new JavaStubPlan.Callable(
        callable.name,
        kind,
        callable.isStatic,
        typeParameters(artifact.program(), callable.owner, callable.typeParameters, javaTypes),
        nullableType(callable.returnType, projected),
        cast,
        parameters,
        annotations(callable.annotations),
        stringLiteral(callable.owner.toString()),
        stringLiteral(callable.invocation.toString()));
  }

  private static List<JavaStubPlan.TypeParameter> typeParameters(
      CoreProgram program,
      DefinitionId owner,
      List<CoreTypeParameter> parameters,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    return parameters.stream()
        .map(
            parameter ->
                new JavaStubPlan.TypeParameter(
                    "T" + parameter.index(),
                    parameter
                        .upperBound()
                        .map(
                            bound ->
                                javaTypeArgument(program, owner, bound, javaTypes, name -> true))))
        .toList();
  }

  private static List<JavaStubPlan.Annotation> annotations(List<AnnotationStub> annotations) {
    return annotations.stream()
        .map(
            annotation ->
                new JavaStubPlan.Annotation(
                    annotation.binaryName,
                    annotation.arguments.stream()
                        .map(
                            argument ->
                                new JavaStubPlan.AnnotationArgument(
                                    Optional.of(argument.name), argument.value))
                        .toList()))
        .toList();
  }
}
