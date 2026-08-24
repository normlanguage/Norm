package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundBuiltinConformance;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundClass;
import dev.w0fv1.norm.bound.BoundConformance;
import dev.w0fv1.norm.bound.BoundEnum;
import dev.w0fv1.norm.bound.BoundInterface;
import dev.w0fv1.norm.bound.BoundInterfaceMethod;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.bound.BoundSource;
import dev.w0fv1.norm.bound.BoundTypeParameter;
import dev.w0fv1.norm.bound.BoundWitness;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreConformance;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionOrigin;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeParameter;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreVisibility;
import dev.w0fv1.norm.core.CoreWitness;
import dev.w0fv1.norm.core.CoreWitnessTarget;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class BoundCoreConverter {
  private final BoundProgram program;
  private final Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates;
  private final Map<String, Integer> declarationIndices = new LinkedHashMap<>();
  private final Map<String, Integer> nominalTypeIndices = new LinkedHashMap<>();
  private final Map<String, SourceOwner> sourceOwners = new LinkedHashMap<>();
  private final Map<String, BoundClass> classes = new LinkedHashMap<>();
  private final Map<String, BoundInterface> interfaces = new LinkedHashMap<>();
  private final Map<String, Integer> fieldOwnerIndices = new LinkedHashMap<>();

  BoundCoreConverter(
      BoundProgram program, Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates) {
    this.program = Objects.requireNonNull(program, "program");
    this.sourceCoordinates = Map.copyOf(sourceCoordinates);
  }

  Result convert() {
    indexDeclarations();
    List<Declaration> declarations = new ArrayList<>();
    program.enums().forEach(value -> declarations.add(convert(value)));
    for (BoundInterface value : program.interfaces()) {
      declarations.add(convert(value));
      value.methods().forEach(method -> declarations.add(convert(value, method)));
    }
    program.builtinConformances().forEach(value -> declarations.add(convert(value)));
    program.classes().forEach(value -> declarations.add(convert(value)));
    program.callables().forEach(value -> declarations.add(convert(value)));
    Optional<Integer> entryPointIndex =
        program.entryPoint().map(entry -> declarationIndex(entry.value()));
    return new Result(declarations, entryPointIndex);
  }

  private void indexDeclarations() {
    int index = 0;
    for (BoundEnum value : program.enums()) {
      declarationIndices.put(value.id().value(), index);
      nominalTypeIndices.put(value.type().identity(), index++);
    }
    for (BoundInterface value : program.interfaces()) {
      declarationIndices.put(value.id().value(), index);
      nominalTypeIndices.put(value.type().identity(), index++);
      interfaces.put(value.id().value(), value);
      for (BoundInterfaceMethod method : value.methods()) {
        declarationIndices.put(method.id().value(), index++);
      }
    }
    index += program.builtinConformances().size();
    for (BoundClass value : program.classes()) {
      int declaration = index++;
      declarationIndices.put(value.id().value(), declaration);
      nominalTypeIndices.put(value.type().identity(), declaration);
      classes.put(value.id().value(), value);
      value.fields().forEach(field -> fieldOwnerIndices.put(field.id().value(), declaration));
    }
    for (BoundCallable value : program.callables()) {
      declarationIndices.put(value.id().value(), index++);
    }
    for (BoundSource source : program.sources()) {
      ModuleSourceCoordinate coordinate = sourceCoordinates.get(source.source().id());
      if (coordinate == null) {
        throw new IllegalStateException("source coordinate is absent: " + source.source().id());
      }
      SourceOwner owner = new SourceOwner(source.source(), source.packageName(), coordinate);
      source.enums().forEach(value -> sourceOwners.put(value.value(), owner));
      source
          .interfaces()
          .forEach(
              value -> {
                sourceOwners.put(value.value(), owner);
                BoundInterface declaration = interfaces.get(value.value());
                if (declaration == null) {
                  throw new IllegalStateException(
                      "bound interface source is absent: " + value.value());
                }
                declaration
                    .methods()
                    .forEach(method -> sourceOwners.put(method.id().value(), owner));
              });
      source.classes().forEach(value -> sourceOwners.put(value.value(), owner));
      source.callables().forEach(value -> sourceOwners.put(value.value(), owner));
    }
  }

  private Declaration convert(BoundInterface declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types =
        BoundCoreTypeConverter.forInterface(declaration, nominalTypeIndices);
    List<CoreTypeParameter> typeParameters =
        coreTypeParameters(declaration.typeParameters(), types);
    List<CoreType> parents = declaration.parents().stream().map(types::convert).toList();
    CoreDefinition definition =
        new CoreDefinition.Interface(
            nominalType(source, declaration.name(), visibility(declaration.visibility())),
            typeParameters,
            parents,
            declaration.methods().stream()
                .map(
                    method -> new PendingDefinitionReference(declarationIndex(method.id().value())))
                .map(dev.w0fv1.norm.core.CoreDefinitionLink.class::cast)
                .toList());
    return new Declaration(
        definition,
        origin(declaration.name(), declaration.span(), Map.of(0, declaration.span())),
        Map.of(),
        new BindingSeed(
            source,
            Optional.empty(),
            declaration.name(),
            visibility(declaration.visibility()),
            new CoreBindingShape.Interface(typeParameters, parents),
            true));
  }

  private Declaration convert(BoundInterface owner, BoundInterfaceMethod declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types =
        BoundCoreTypeConverter.forInterfaceMethod(owner, declaration, nominalTypeIndices);
    CoreType receiverType =
        types.convert(
            SemanticType.declared(
                owner.type().identity(),
                owner.name(),
                owner.typeParameters().stream().map(BoundTypeParameter::type).toList(),
                owner.type().category()));
    List<CoreTypeParameter> typeParameters =
        coreTypeParameters(declaration.typeParameters(), types);
    List<CoreType> parameterTypes =
        declaration.parameters().stream()
            .map(parameter -> types.convert(parameter.type()))
            .toList();
    CoreType returnType = types.convert(declaration.returnType());
    CoreDefinition definition =
        new CoreDefinition.InterfaceMethod(
            declaration.name(), receiverType, typeParameters, parameterTypes, returnType);
    return new Declaration(
        definition,
        origin(declaration.name(), declaration.span(), Map.of(0, declaration.span())),
        Map.of(),
        new BindingSeed(
            source,
            Optional.of(owner.name()),
            declaration.name(),
            CoreVisibility.PUBLIC,
            new CoreBindingShape.InterfaceMethod(
                typeParameters,
                declaration.parameters().stream()
                    .map(
                        parameter ->
                            new CoreBindingShape.Parameter(
                                parameter.name(), types.convert(parameter.type())))
                    .toList(),
                returnType),
            owner.visibility() == dev.w0fv1.norm.bound.BoundVisibility.PUBLIC));
  }

  private Declaration convert(BoundEnum declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types = BoundCoreTypeConverter.forEnum(declaration, nominalTypeIndices);
    CoreDefinition definition =
        new CoreDefinition.Enum(
            nominalType(source, declaration.name(), visibility(declaration.visibility())),
            coreTypeParameters(declaration.typeParameters(), types),
            declaration.variants().stream()
                .map(
                    variant ->
                        new CoreEnumVariant(
                            variant.name(),
                            variant.fields().stream()
                                .map(
                                    field ->
                                        new CoreField(field.ordinal(), types.convert(field.type())))
                                .toList()))
                .toList());
    return new Declaration(
        definition,
        origin(declaration.name(), declaration.span(), Map.of(0, declaration.span())),
        Map.of(),
        new BindingSeed(
            source,
            Optional.empty(),
            declaration.name(),
            visibility(declaration.visibility()),
            new CoreBindingShape.Enum(
                coreTypeParameters(declaration.typeParameters(), types),
                declaration.variants().stream()
                    .map(
                        variant ->
                            new CoreBindingShape.Variant(
                                variant.name(),
                                variant.fields().stream()
                                    .map(
                                        field ->
                                            new CoreBindingShape.Parameter(
                                                field.name(), types.convert(field.type())))
                                    .toList()))
                    .toList()),
            true));
  }

  private Declaration convert(BoundBuiltinConformance declaration) {
    BoundCoreTypeConverter types =
        BoundCoreTypeConverter.forBuiltinConformance(declaration, nominalTypeIndices);
    String name =
        declaration.concreteType().identity() + " : " + declaration.interfaceType().identity();
    CoreDefinition definition =
        new CoreDefinition.BuiltinConformance(
            coreTypeParameters(declaration.typeParameters(), types),
            types.convert(declaration.concreteType()),
            types.convert(declaration.interfaceType()),
            coreWitnesses(declaration.witnesses()));
    return new Declaration(
        definition,
        origin(name, declaration.span(), Map.of(0, declaration.span())),
        Map.of(),
        Optional.empty());
  }

  private Declaration convert(BoundClass declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types = BoundCoreTypeConverter.forClass(declaration, nominalTypeIndices);
    CoreDefinition definition =
        new CoreDefinition.Class(
            nominalType(source, declaration.name(), visibility(declaration.visibility())),
            coreTypeParameters(declaration.typeParameters(), types),
            declaration.fields().stream()
                .map(field -> new CoreField(field.ordinal(), types.convert(field.type())))
                .toList(),
            coreConformances(declaration.conformances(), types));
    return new Declaration(
        definition,
        origin(declaration.name(), declaration.span(), Map.of(0, declaration.span())),
        Map.of(),
        new BindingSeed(
            source,
            Optional.empty(),
            declaration.name(),
            visibility(declaration.visibility()),
            new CoreBindingShape.Class(
                coreTypeParameters(declaration.typeParameters(), types),
                declaration.fields().stream()
                    .map(
                        field ->
                            new CoreBindingShape.Field(
                                field.name(),
                                visibility(field.visibility()),
                                types.convert(field.type())))
                    .toList(),
                declaration.conformances().stream()
                    .map(conformance -> types.convert(conformance.interfaceType()))
                    .toList()),
            true));
  }

  private Declaration convert(BoundCallable declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types =
        BoundCoreTypeConverter.forCallable(declaration, nominalTypeIndices);
    Optional<BoundClass> owner = declaration.owner().map(value -> classDeclaration(value.value()));
    Optional<CoreType> receiverType =
        owner.map(
            value ->
                types.convert(
                    SemanticType.declared(
                        value.type().identity(),
                        value.name(),
                        value.typeParameters().stream().map(BoundTypeParameter::type).toList(),
                        value.type().category())));
    int ownerTypeParameterCount = owner.map(value -> value.typeParameters().size()).orElse(0);
    List<BoundTypeParameter> callableTypeParameters =
        declaration
            .typeParameters()
            .subList(ownerTypeParameterCount, declaration.typeParameters().size());
    BoundCoreBodyConverter.Result body =
        new BoundCoreBodyConverter(
                types, receiverType, this::declarationIndex, this::fieldOwnerIndex)
            .convert(declaration);
    CoreDefinition definition =
        new CoreDefinition.Callable(
            receiverType,
            coreTypeParameters(callableTypeParameters, types),
            declaration.parameters().stream()
                .map(parameter -> types.convert(parameter.type()))
                .toList(),
            declaration.parameters().stream()
                .map(parameter -> body.localIndex(parameter.id()))
                .toList(),
            declaration.reifiedParameters().stream()
                .map(parameter -> body.localIndex(parameter.source()))
                .toList(),
            types.convert(declaration.returnType()),
            body.locals(),
            body.body());
    Optional<String> ownerName = owner.map(BoundClass::name);
    return new Declaration(
        definition,
        origin(declaration.name(), declaration.span(), body.nodeSpans()),
        body.referenceTargets(),
        new BindingSeed(
            source,
            ownerName,
            declaration.name(),
            visibility(declaration.visibility()),
            new CoreBindingShape.Callable(
                coreTypeParameters(callableTypeParameters, types),
                declaration.parameters().stream()
                    .map(
                        parameter ->
                            new CoreBindingShape.Parameter(
                                parameter.name(), types.convert(parameter.type())))
                    .toList(),
                types.convert(declaration.returnType())),
            owner
                .map(value -> value.visibility() == dev.w0fv1.norm.bound.BoundVisibility.PUBLIC)
                .orElse(true)));
  }

  private int declarationIndex(String declaration) {
    Integer index = declarationIndices.get(declaration);
    if (index == null)
      throw new IllegalStateException("core declaration is absent: " + declaration);
    return index;
  }

  private int fieldOwnerIndex(String field) {
    Integer index = fieldOwnerIndices.get(field);
    if (index == null) throw new IllegalStateException("core field owner is absent: " + field);
    return index;
  }

  private SourceOwner sourceOwner(String declaration) {
    SourceOwner source = sourceOwners.get(declaration);
    if (source == null)
      throw new IllegalStateException("declaration source is absent: " + declaration);
    return source;
  }

  private BoundClass classDeclaration(String declaration) {
    BoundClass value = classes.get(declaration);
    if (value == null) throw new IllegalStateException("core class is absent: " + declaration);
    return value;
  }

  private static CoreDefinitionOrigin origin(
      String definitionName, SourceSpan root, Map<Integer, SourceSpan> nodes) {
    return new CoreDefinitionOrigin(definitionName, root, nodes);
  }

  private static CoreNominalTypeKey nominalType(
      SourceOwner source, String name, CoreVisibility visibility) {
    return new CoreNominalTypeKey(
        source.coordinate().module(),
        source.packageName(),
        name,
        visibility,
        visibility == CoreVisibility.PRIVATE
            ? Optional.of(source.coordinate().relativePath())
            : Optional.empty());
  }

  private static CoreVisibility visibility(dev.w0fv1.norm.bound.BoundVisibility visibility) {
    return CoreVisibility.valueOf(visibility.name());
  }

  private static List<CoreTypeParameter> coreTypeParameters(
      List<BoundTypeParameter> parameters, BoundCoreTypeConverter types) {
    return parameters.stream()
        .map(
            parameter ->
                new CoreTypeParameter(
                    types.parameterIndex(parameter.type().identity()),
                    parameter.upperBound().map(types::convert)))
        .toList();
  }

  private List<CoreConformance> coreConformances(
      List<BoundConformance> conformances, BoundCoreTypeConverter types) {
    return conformances.stream()
        .map(
            conformance ->
                new CoreConformance(
                    types.convert(conformance.interfaceType()),
                    coreWitnesses(conformance.witnesses())))
        .toList();
  }

  private List<CoreWitness> coreWitnesses(List<BoundWitness> witnesses) {
    return witnesses.stream()
        .map(
            witness ->
                new CoreWitness(
                    new PendingDefinitionReference(declarationIndex(witness.requirement().value())),
                    switch (witness.implementation()) {
                      case BoundWitness.Target.Callable callable ->
                          new CoreWitnessTarget.Callable(
                              new PendingDefinitionReference(
                                  declarationIndex(callable.target().value())));
                      case BoundWitness.Target.Intrinsic intrinsic ->
                          new CoreWitnessTarget.Intrinsic(intrinsic.target());
                    }))
        .toList();
  }

  record Result(List<Declaration> declarations, Optional<Integer> entryPointIndex) {
    Result {
      declarations = List.copyOf(declarations);
      entryPointIndex = Objects.requireNonNull(entryPointIndex, "entryPointIndex");
    }
  }

  static final class Declaration {
    private final CoreDefinition definition;
    private final CoreDefinitionOrigin origin;
    private final Map<Integer, Integer> referenceTargets;
    private final Optional<BindingSeed> binding;

    private Declaration(
        CoreDefinition definition,
        CoreDefinitionOrigin origin,
        Map<Integer, Integer> referenceTargets,
        BindingSeed binding) {
      this(definition, origin, referenceTargets, Optional.of(binding));
    }

    private Declaration(
        CoreDefinition definition,
        CoreDefinitionOrigin origin,
        Map<Integer, Integer> referenceTargets,
        Optional<BindingSeed> binding) {
      this.definition = Objects.requireNonNull(definition, "definition");
      this.origin = Objects.requireNonNull(origin, "origin");
      this.referenceTargets = Map.copyOf(referenceTargets);
      this.binding = Objects.requireNonNull(binding, "binding");
    }

    CoreDefinition definition() {
      return definition;
    }

    CoreDefinitionOrigin origin() {
      return origin;
    }

    Map<Integer, Integer> referenceTargets() {
      return referenceTargets;
    }

    Optional<CoreBinding> bind(
        DefinitionOccurrenceId occurrence,
        Set<DocumentId> exportedSources,
        java.util.function.Function<PendingDefinitionReference, DefinitionReference> resolver) {
      return binding.map(value -> value.bind(occurrence, exportedSources, resolver));
    }
  }

  private record SourceOwner(
      SourceFile source, String packageName, ModuleSourceCoordinate coordinate) {
    private SourceOwner {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(packageName, "packageName");
      Objects.requireNonNull(coordinate, "coordinate");
    }
  }

  private record BindingSeed(
      SourceOwner source,
      Optional<String> ownerName,
      String name,
      CoreVisibility visibility,
      CoreBindingShape shape,
      boolean ownerPublic) {
    private BindingSeed {
      source = Objects.requireNonNull(source, "source");
      ownerName = Objects.requireNonNull(ownerName, "ownerName");
      name = Objects.requireNonNull(name, "name");
      visibility = Objects.requireNonNull(visibility, "visibility");
      shape = Objects.requireNonNull(shape, "shape");
    }

    private CoreBinding bind(
        DefinitionOccurrenceId occurrence,
        Set<DocumentId> exportedSources,
        java.util.function.Function<PendingDefinitionReference, DefinitionReference> resolver) {
      boolean exported =
          visibility == CoreVisibility.PUBLIC
              && ownerPublic
              && exportedSources.contains(source.source().id());
      return new CoreBinding(
          source.packageName(),
          ownerName,
          name,
          visibility,
          resolve(shape, resolver),
          occurrence,
          exported);
    }

    private static CoreBindingShape resolve(
        CoreBindingShape shape,
        java.util.function.Function<PendingDefinitionReference, DefinitionReference> resolver) {
      java.util.function.Function<
              dev.w0fv1.norm.core.CoreDefinitionLink, dev.w0fv1.norm.core.CoreDefinitionLink>
          links =
              link ->
                  link instanceof PendingDefinitionReference pending
                      ? resolver.apply(pending)
                      : link;
      return switch (shape) {
        case CoreBindingShape.Callable callable ->
            new CoreBindingShape.Callable(
                resolveTypeParameters(callable.typeParameters(), links),
                callable.parameters().stream()
                    .map(
                        parameter ->
                            new CoreBindingShape.Parameter(
                                parameter.label(), CoreTypes.mapLinks(parameter.type(), links)))
                    .toList(),
                CoreTypes.mapLinks(callable.returnType(), links));
        case CoreBindingShape.Class classShape ->
            new CoreBindingShape.Class(
                resolveTypeParameters(classShape.typeParameters(), links),
                classShape.fields().stream()
                    .map(
                        field ->
                            new CoreBindingShape.Field(
                                field.name(),
                                field.visibility(),
                                CoreTypes.mapLinks(field.type(), links)))
                    .toList(),
                classShape.conformances().stream()
                    .map(type -> CoreTypes.mapLinks(type, links))
                    .toList());
        case CoreBindingShape.Enum enumShape ->
            new CoreBindingShape.Enum(
                resolveTypeParameters(enumShape.typeParameters(), links),
                enumShape.variants().stream()
                    .map(
                        variant ->
                            new CoreBindingShape.Variant(
                                variant.name(),
                                variant.fields().stream()
                                    .map(
                                        field ->
                                            new CoreBindingShape.Parameter(
                                                field.label(),
                                                CoreTypes.mapLinks(field.type(), links)))
                                    .toList()))
                    .toList());
        case CoreBindingShape.Interface interfaceShape ->
            new CoreBindingShape.Interface(
                resolveTypeParameters(interfaceShape.typeParameters(), links),
                interfaceShape.directParents().stream()
                    .map(type -> CoreTypes.mapLinks(type, links))
                    .toList());
        case CoreBindingShape.InterfaceMethod method ->
            new CoreBindingShape.InterfaceMethod(
                resolveTypeParameters(method.typeParameters(), links),
                method.parameters().stream()
                    .map(
                        parameter ->
                            new CoreBindingShape.Parameter(
                                parameter.label(), CoreTypes.mapLinks(parameter.type(), links)))
                    .toList(),
                CoreTypes.mapLinks(method.returnType(), links));
      };
    }

    private static List<CoreTypeParameter> resolveTypeParameters(
        List<CoreTypeParameter> parameters,
        java.util.function.Function<
                dev.w0fv1.norm.core.CoreDefinitionLink, dev.w0fv1.norm.core.CoreDefinitionLink>
            links) {
      return parameters.stream()
          .map(
              parameter ->
                  new CoreTypeParameter(
                      parameter.index(),
                      parameter.upperBound().map(type -> CoreTypes.mapLinks(type, links))))
          .toList();
    }
  }
}
