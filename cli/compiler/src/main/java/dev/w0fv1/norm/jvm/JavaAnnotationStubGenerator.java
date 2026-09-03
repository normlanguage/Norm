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
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreVisibility;
import dev.w0fv1.norm.core.CoreWitnessTarget;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class JavaAnnotationStubGenerator {
  public List<JavaAnnotationStub> generate(
      CoreArtifact artifact,
      List<ResolvedJarBinding> bindings,
      CompilationScope scope,
      DocumentId entryDocument,
      Set<DocumentId> bindingDocuments) {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(bindings, "bindings");
    Objects.requireNonNull(scope, "scope");
    ModuleCoordinate entryModule =
        scope.coordinate(Objects.requireNonNull(entryDocument, "entryDocument")).module();
    bindingDocuments = Set.copyOf(bindingDocuments);
    Set<ModuleCoordinate> excludedModules =
        bindingDocuments.stream()
            .map(scope::coordinate)
            .map(dev.w0fv1.norm.value.ModuleSourceCoordinate::module)
            .filter(module -> !module.equals(entryModule))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    Map<JarBindingClassReference.Nominal, JavaAnnotationBinding> annotations =
        annotationBindings(bindings);
    Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations = enumBindings(bindings);
    Map<JarBindingClassReference.Nominal, String> javaTypes = javaTypes(bindings);
    Map<TypeKey, TypeStub> types = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      if (!belongsTo(application.target(), artifact, scope, excludedModules)) continue;
      Optional<JavaAnnotationBinding> annotation = annotation(artifact, annotations, application);
      if (annotation.isEmpty()) continue;
      apply(artifact, types, enumerations, javaTypes, annotation.orElseThrow(), application);
    }
    applyNormAnnotations(artifact, types, enumerations, javaTypes, scope, excludedModules);
    addNormTypes(artifact, types, javaTypes, scope, excludedModules);
    Set<String> generatedTypes =
        java.util.stream.Stream.concat(
                types.keySet().stream().map(TypeKey::binaryName), javaTypes.values().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return types.values().stream()
        .sorted(Comparator.comparing(type -> type.key.binaryName()))
        .map(
            type ->
                new JavaAnnotationStub(
                    type.key.binaryName(), source(type, artifact, javaTypes, generatedTypes)))
        .toList();
  }

  private static void applyNormAnnotations(
      CoreArtifact artifact,
      Map<TypeKey, TypeStub> types,
      Map<JarBindingClassReference.Nominal, JavaEnumBinding> enumerations,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      CompilationScope scope,
      Set<ModuleCoordinate> excludedModules) {
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
      if (!belongsTo(application.target(), artifact, scope, excludedModules)) continue;
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
      Set<ModuleCoordinate> excludedModules) {
    if (types.isEmpty()) return;
    for (CoreBinding binding : artifact.namespace().bindings()) {
      if (binding.ownerName().isPresent()) continue;
      if (!belongsTo(binding.occurrence(), artifact, scope, excludedModules)) continue;
      Optional<CoreNominalTypeKey> nominal = nominalType(artifact.program(), binding);
      if (nominal.isEmpty()) continue;
      CoreNominalTypeKey key = nominal.orElseThrow();
      if (javaTypes.containsKey(
          new JarBindingClassReference.Nominal(key.module(), key.packageName(), key.name())))
        continue;
      ensureType(artifact, types, binding);
    }
  }

  private static boolean belongsTo(
      CoreAnnotationTarget target,
      CoreArtifact artifact,
      CompilationScope scope,
      Set<ModuleCoordinate> excludedModules) {
    return switch (target) {
      case CoreAnnotationTarget.Package value ->
          scope.coordinates().entrySet().stream()
              .anyMatch(
                  entry ->
                      entry.getValue().module().equals(value.module())
                          && !excludedModules.contains(entry.getValue().module()));
      case CoreAnnotationTarget.Definition value ->
          belongsTo(value.occurrence(), artifact, scope, excludedModules);
      case CoreAnnotationTarget.Field value ->
          belongsTo(value.owner(), artifact, scope, excludedModules);
      case CoreAnnotationTarget.Parameter value ->
          belongsTo(value.callable(), artifact, scope, excludedModules);
      case CoreAnnotationTarget.Local value ->
          belongsTo(value.callable(), artifact, scope, excludedModules);
    };
  }

  private static boolean belongsTo(
      DefinitionOccurrenceId occurrence,
      CoreArtifact artifact,
      CompilationScope scope,
      Set<ModuleCoordinate> excludedModules) {
    DocumentId document = artifact.authoring().origin(occurrence).rootSpan().source().id();
    var coordinate = scope.coordinates().get(document);
    return coordinate != null && !excludedModules.contains(coordinate.module());
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
                    nominal, descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
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
                String binaryName =
                    descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                String previous = result.putIfAbsent(reference, binaryName);
                if (previous != null && !previous.equals(binaryName)) {
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
    if (!(binding.shape() instanceof CoreBindingShape.Callable shape)) {
      throw new IllegalArgumentException("Java annotation callable target is not a function");
    }
    TypeStub owner;
    boolean isStatic;
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
      isStatic = false;
    } else {
      TypeKey key = new TypeKey(binding.packageName(), "$Functions");
      owner = types.computeIfAbsent(key, ignored -> TypeStub.synthetic(key));
      isStatic = true;
    }
    return owner.callables.computeIfAbsent(
        occurrence,
        ignored ->
            new CallableStub(
                binding.name(),
                shape.parameters(),
                shape.returnType(),
                binding.definition(),
                isStatic,
                false));
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
                    shape.constructors().get(constructorIndex).parameters(),
                    CoreType.VOID,
                    constructor,
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
          .filter(candidate -> candidate.shape() instanceof CoreBindingShape.InterfaceMethod)
          .sorted(Comparator.comparing(CoreBinding::occurrence))
          .forEach(
              method -> {
                CoreBindingShape.InterfaceMethod shape =
                    (CoreBindingShape.InterfaceMethod) method.shape();
                created.callables.put(
                    method.occurrence(),
                    new CallableStub(
                        method.name(),
                        shape.parameters(),
                        shape.returnType(),
                        method.definition(),
                        false,
                        false));
              });
    }
    if (binding.shape() instanceof CoreBindingShape.Aggregate shape) {
      CoreDefinition.Aggregate aggregate =
          (CoreDefinition.Aggregate)
              artifact.program().definition(binding.definition()).orElseThrow();
      for (int index = 0; index < shape.fields().size(); index++) {
        CoreBindingShape.Field field = shape.fields().get(index);
        if (field.visibility() != CoreVisibility.PUBLIC) continue;
        dev.w0fv1.norm.core.CoreField declaration = aggregate.fields().get(index);
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
                .filter(candidate -> candidate.shape() instanceof CoreBindingShape.Callable)
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
                shape.constructors().get(index).parameters(),
                CoreType.VOID,
                definition,
                false,
                true));
      }
      artifact.namespace().bindings().stream()
          .filter(candidate -> candidate.packageName().equals(binding.packageName()))
          .filter(candidate -> candidate.ownerName().equals(Optional.of(binding.name())))
          .filter(candidate -> candidate.visibility() == CoreVisibility.PUBLIC)
          .filter(candidate -> candidate.shape() instanceof CoreBindingShape.Callable)
          .sorted(Comparator.comparing(CoreBinding::occurrence))
          .forEach(
              method -> {
                CoreBindingShape.Callable callable = (CoreBindingShape.Callable) method.shape();
                created.callables.putIfAbsent(
                    method.occurrence(),
                    new CallableStub(
                        method.name(),
                        callable.parameters(),
                        callable.returnType(),
                        method.definition(),
                        false,
                        false));
              });
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
              instanceof CoreDefinition.InterfaceMethod interfaceMethod)) {
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
                  callableShape.parameters(),
                  callableShape.returnType(),
                  implementation.definition(),
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

  private static String source(
      TypeStub type,
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<String> generatedTypes) {
    StringBuilder source = new StringBuilder();
    if (!type.key.packageName().isEmpty()) {
      source.append("package ").append(type.key.packageName()).append(";\n\n");
    }
    boolean annotation = type.binding != null && type.binding.kind() == CoreBindingKind.ANNOTATION;
    if (annotation) appendAnnotationPolicy(source, type, artifact);
    appendAnnotations(source, type.annotations, "");
    source.append("public ");
    if (type.synthetic || type.binding.kind() == CoreBindingKind.CLASS) {
      source.append("class ");
    } else if (type.binding.kind() == CoreBindingKind.VALUE) {
      source.append("final class ");
    } else if (type.binding.kind() == CoreBindingKind.INTERFACE) {
      source.append("interface ");
    } else if (type.binding.kind() == CoreBindingKind.ENUM) {
      source.append("enum ");
    } else if (type.binding.kind() == CoreBindingKind.ANNOTATION) {
      source.append("@interface ");
    } else {
      throw new IllegalArgumentException("unsupported annotated JVM type " + type.binding.kind());
    }
    source.append(type.key.name());
    if (!annotation
        && type.binding != null
        && type.binding.shape() instanceof CoreBindingShape.Aggregate shape) {
      shape
          .parentType()
          .ifPresent(
              parent ->
                  source
                      .append(" extends ")
                      .append(
                          javaType(
                              artifact.program(), type.binding.definition(), parent, javaTypes)));
      if (!shape.conformances().isEmpty()) {
        List<String> conformances =
            shape.conformances().stream()
                .map(
                    conformance ->
                        javaType(
                            artifact.program(), type.binding.definition(), conformance, javaTypes))
                .filter(
                    conformance -> {
                      int arguments = conformance.indexOf('<');
                      return generatedTypes.contains(
                          arguments < 0 ? conformance : conformance.substring(0, arguments));
                    })
                .toList();
        if (!conformances.isEmpty()) {
          source.append(" implements ").append(String.join(", ", conformances));
        }
      }
    } else if (type.binding != null
        && type.binding.shape() instanceof CoreBindingShape.Interface shape
        && !shape.directParents().isEmpty()) {
      source.append(
          shape.directParents().stream()
              .map(
                  parent ->
                      javaType(artifact.program(), type.binding.definition(), parent, javaTypes))
              .collect(java.util.stream.Collectors.joining(", ", " extends ", "")));
    }
    if (type.binding != null && type.binding.shape() instanceof CoreBindingShape.Enum shape) {
      source.append(" {\n");
      for (int index = 0; index < shape.variants().size(); index++) {
        source.append("  ").append(shape.variants().get(index).name());
        source.append(index + 1 == shape.variants().size() ? ";\n" : ",\n");
      }
    } else {
      source.append(" {\n");
    }
    for (FieldStub field : type.fields.values()) {
      appendAnnotations(source, field.annotations, "  ");
      source
          .append("  public ")
          .append(javaType(artifact.program(), field.owner, field.type, javaTypes))
          .append(' ')
          .append(field.name);
      source.append(annotation ? "();\n" : ";\n");
    }
    if (!annotation) {
      for (CallableStub callable : type.callables.values()) {
        appendCallable(source, type, callable, artifact, javaTypes);
      }
    }
    boolean hasConstructor =
        type.callables.values().stream().anyMatch(callable -> callable.constructor);
    if (!annotation
        && !type.synthetic
        && type.binding.kind() != CoreBindingKind.INTERFACE
        && type.binding.kind() != CoreBindingKind.ENUM
        && !hasConstructor) {
      source.append("  public ").append(type.key.name()).append("() {");
      source
          .append(" dev.w0fv1.norm.bridge.JavaApplicationBridge.allocate(")
          .append(type.key.name())
          .append(".class, this, ")
          .append(stringLiteral(type.binding.definition().toString()))
          .append("); }\n");
    }
    source.append("}\n");
    return source.toString();
  }

  private static void appendAnnotationPolicy(
      StringBuilder source, TypeStub type, CoreArtifact artifact) {
    CoreDefinition.Aggregate declaration =
        (CoreDefinition.Aggregate)
            artifact.program().definition(type.binding.definition()).orElseThrow();
    CoreAnnotationPolicy policy =
        CoreAnnotationPolicy.resolve(artifact.program(), type.binding.definition(), declaration);
    List<String> targets = new ArrayList<>();
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
    source
        .append("@java.lang.annotation.Target({")
        .append(String.join(", ", targets))
        .append("})\n")
        .append("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.")
        .append(
            switch (policy.retention()) {
              case SOURCE -> "SOURCE";
              case BINARY -> "CLASS";
              case RUNTIME -> "RUNTIME";
            })
        .append(")\n");
    if (policy.inherited()) source.append("@java.lang.annotation.Inherited\n");
  }

  private static void appendCallable(
      StringBuilder source,
      TypeStub owner,
      CallableStub callable,
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    appendAnnotations(source, callable.annotations, "  ");
    if (!callable.constructor) {
      source
          .append("  @dev.w0fv1.norm.bridge.NormApplicationMethod(")
          .append(stringLiteral(callable.owner.toString()))
          .append(")\n");
    }
    source.append("  public ");
    if (callable.isStatic) source.append("static ");
    if (callable.constructor) {
      source.append(owner.key.name());
    } else {
      source
          .append(javaType(artifact.program(), callable.owner, callable.returnType, javaTypes))
          .append(' ')
          .append(callable.name);
    }
    source.append('(');
    for (int index = 0; index < callable.parameters.size(); index++) {
      if (index > 0) source.append(", ");
      ParameterStub parameter = callable.parameters.get(index);
      appendAnnotations(source, parameter.annotations, "");
      source
          .append(javaType(artifact.program(), callable.owner, parameter.type, javaTypes))
          .append(' ')
          .append(parameter.name);
    }
    source.append(')');
    if (owner.binding != null && owner.binding.kind() == CoreBindingKind.INTERFACE) {
      source.append(";\n");
      return;
    }
    source.append(" { ");
    if (callable.constructor) {
      source
          .append("dev.w0fv1.norm.bridge.JavaApplicationBridge.construct(")
          .append(owner.key.name())
          .append(".class, this, ")
          .append(stringLiteral(callable.owner.toString()))
          .append(", ");
      appendArguments(source, callable.parameters);
      source.append("); }\n");
      return;
    }
    String javaType = javaType(artifact.program(), callable.owner, callable.returnType, javaTypes);
    String invocation =
        "dev.w0fv1.norm.bridge.JavaApplicationBridge.invoke("
            + owner.key.name()
            + ".class, "
            + (callable.isStatic ? "null" : "this")
            + ", "
            + stringLiteral(callable.owner.toString())
            + ", ";
    if (!javaType.equals("void")) source.append("return ").append(returnConversion(javaType));
    source.append(invocation);
    appendArguments(source, callable.parameters);
    source.append(')');
    source.append("; }\n");
  }

  private static void appendArguments(StringBuilder source, List<ParameterStub> parameters) {
    source.append("new Object[] {");
    for (int index = 0; index < parameters.size(); index++) {
      if (index > 0) source.append(", ");
      source.append(parameters.get(index).name);
    }
    source.append('}');
  }

  private static String returnConversion(String javaType) {
    return switch (javaType) {
      case "boolean" -> "(java.lang.Boolean) ";
      case "int" -> "(java.lang.Integer) ";
      case "long" -> "(java.lang.Long) ";
      case "float" -> "(java.lang.Float) ";
      case "double" -> "(java.lang.Double) ";
      default -> "(" + javaType + ") ";
    };
  }

  private static void appendAnnotations(
      StringBuilder source, List<AnnotationStub> annotations, String indent) {
    for (AnnotationStub annotation : annotations) {
      source.append(indent).append('@').append(annotation.binaryName);
      if (!annotation.arguments.isEmpty()) {
        source.append('(');
        for (int index = 0; index < annotation.arguments.size(); index++) {
          if (index > 0) source.append(", ");
          AnnotationArgument argument = annotation.arguments.get(index);
          source.append(argument.name).append(" = ").append(argument.value);
        }
        source.append(')');
      }
      source.append('\n');
    }
  }

  private static String javaType(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (absolute == CoreType.VOID) return "void";
    if (absolute instanceof CoreType.Reference reference) {
      return javaType(program, owner, reference.target(), javaTypes);
    }
    if (absolute instanceof CoreType.Declared declared) {
      if (declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
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
        if (declared.arguments().isEmpty()) return binaryName;
        return binaryName
            + declared.arguments().stream()
                .map(argument -> javaTypeArgument(program, owner, argument, javaTypes))
                .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
      }
    }
    return "java.lang.Object";
  }

  private static String javaTypeArgument(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (absolute instanceof CoreType.Reference reference) {
      return javaTypeArgument(program, owner, reference.target(), javaTypes);
    }
    if (absolute instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
      return switch (builtin.id().value()) {
        case "std.core.Boolean" -> "java.lang.Boolean";
        case "std.core.Integer", "std.core.CodePoint" -> "java.lang.Integer";
        case "std.core.Long" -> "java.lang.Long";
        case "std.core.Float" -> "java.lang.Float";
        case "std.core.Double" -> "java.lang.Double";
        case "std.core.String" -> "java.lang.String";
        default -> "java.lang.Object";
      };
    }
    return javaType(program, owner, absolute, javaTypes);
  }

  private static String binaryName(CoreNominalTypeKey nominal) {
    return nominal.packageName().isEmpty()
        ? nominal.name()
        : nominal.packageName() + "." + nominal.name();
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
      return packageName.isEmpty() ? name : packageName + "." + name;
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
    private final List<ParameterStub> parameters;
    private final CoreType returnType;
    private final DefinitionId owner;
    private final boolean isStatic;
    private final boolean constructor;
    private final List<AnnotationStub> annotations = new ArrayList<>();

    private CallableStub(
        String name,
        List<CoreBindingShape.Parameter> parameters,
        CoreType returnType,
        DefinitionId owner,
        boolean isStatic,
        boolean constructor) {
      this.name = name;
      this.parameters =
          parameters.stream()
              .map(parameter -> new ParameterStub(parameter.label(), parameter.type()))
              .toList();
      this.returnType = returnType;
      this.owner = owner;
      this.isStatic = isStatic;
      this.constructor = constructor;
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
}
