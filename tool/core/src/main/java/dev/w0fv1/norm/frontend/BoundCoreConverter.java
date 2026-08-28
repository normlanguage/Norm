package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundAggregate;
import dev.w0fv1.norm.bound.BoundAnnotationApplication;
import dev.w0fv1.norm.bound.BoundAnnotationReference;
import dev.w0fv1.norm.bound.BoundAnnotationTarget;
import dev.w0fv1.norm.bound.BoundAnnotationValue;
import dev.w0fv1.norm.bound.BoundBuiltinConformance;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundConformance;
import dev.w0fv1.norm.bound.BoundEnum;
import dev.w0fv1.norm.bound.BoundInterface;
import dev.w0fv1.norm.bound.BoundInterfaceMethod;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.bound.BoundSource;
import dev.w0fv1.norm.bound.BoundTypeParameter;
import dev.w0fv1.norm.bound.BoundWitness;
import dev.w0fv1.norm.core.CoreAnnotationApplication;
import dev.w0fv1.norm.core.CoreAnnotationReference;
import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreConformance;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionOrigin;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeParameter;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreValueCategory;
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
  private final Map<String, BoundAggregate> aggregates = new LinkedHashMap<>();
  private final Map<String, BoundInterface> interfaces = new LinkedHashMap<>();
  private final Map<String, Integer> fieldOwnerIndices = new LinkedHashMap<>();
  private final Map<String, Integer> fieldOrdinals = new LinkedHashMap<>();
  private final Map<String, Map<dev.w0fv1.norm.bound.BoundLocalId, Integer>> callableLocals =
      new LinkedHashMap<>();

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
    program.aggregates().forEach(value -> declarations.add(convert(value)));
    program.callables().forEach(value -> declarations.add(convert(value)));
    Optional<Integer> entryPointIndex =
        program.entryPoint().map(entry -> declarationIndex(entry.value()));
    List<AnnotationSeed> metadata = coreApplications().stream().map(this::annotationSeed).toList();
    return new Result(declarations, metadata, entryPointIndex);
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
    for (BoundAggregate value : program.aggregates()) {
      int declaration = index++;
      declarationIndices.put(value.id().value(), declaration);
      nominalTypeIndices.put(value.type().identity(), declaration);
      aggregates.put(value.id().value(), value);
      value
          .fields()
          .forEach(
              field -> {
                fieldOwnerIndices.put(field.id().value(), declaration);
                fieldOrdinals.put(field.id().value(), field.ordinal());
              });
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
      source.aggregates().forEach(value -> sourceOwners.put(value.value(), owner));
      source.callables().forEach(value -> sourceOwners.put(value.value(), owner));
    }
  }

  private List<BoundAnnotationApplication> coreApplications() {
    return program.annotationApplications();
  }

  private AnnotationSeed annotationSeed(BoundAnnotationApplication application) {
    BoundCoreTypeConverter types = BoundCoreTypeConverter.forMetadata(nominalTypeIndices);
    return new AnnotationSeed(
        declarationIndex(application.annotation().value()),
        annotationTarget(application.target()),
        application.values().stream().map(value -> annotationValue(value, types)).toList());
  }

  private PendingAnnotationTarget annotationTarget(BoundAnnotationTarget target) {
    return switch (target) {
      case BoundAnnotationTarget.Package value -> {
        ModuleSourceCoordinate coordinate = sourceCoordinates.get(value.document());
        if (coordinate == null) throw new IllegalStateException("annotation source is absent");
        yield new PendingAnnotationTarget.Package(coordinate.module(), value.packageName());
      }
      case BoundAnnotationTarget.Definition value ->
          new PendingAnnotationTarget.Definition(value.kind(), declarationIndex(value.id()));
      case BoundAnnotationTarget.Field value ->
          new PendingAnnotationTarget.Field(
              fieldOwnerIndex(value.field().value()), fieldOrdinal(value.field().value()));
      case BoundAnnotationTarget.Parameter value ->
          new PendingAnnotationTarget.Parameter(declarationIndex(value.owner()), value.ordinal());
      case BoundAnnotationTarget.Local value ->
          new PendingAnnotationTarget.Local(
              declarationIndex(value.owner().value()),
              localIndex(value.owner().value(), value.local()));
    };
  }

  private CoreAnnotationValue annotationValue(
      BoundAnnotationValue value, BoundCoreTypeConverter types) {
    return new CoreAnnotationValue(
        types.convert(value.type()), annotationContent(value.value(), value.type(), types));
  }

  private CoreAnnotationValue.Content annotationContent(
      BoundAnnotationValue.Content value,
      dev.w0fv1.norm.semantic.SemanticType type,
      BoundCoreTypeConverter types) {
    return switch (value) {
      case BoundAnnotationValue.Literal literal -> {
        Object materialized = literal.value();
        if (materialized instanceof java.math.BigInteger integer) {
          materialized = dev.w0fv1.norm.semantic.NumericTypes.materialize(integer, type);
        } else if (materialized instanceof java.math.BigDecimal decimal) {
          materialized = dev.w0fv1.norm.semantic.NumericTypes.materialize(decimal, type);
        }
        yield new CoreAnnotationValue.Literal(materialized);
      }
      case BoundAnnotationValue.Null ignored -> CoreAnnotationValue.Null.INSTANCE;
      case BoundAnnotationValue.ListValue list ->
          new CoreAnnotationValue.ListValue(
              list.values().stream().map(item -> annotationValue(item, types)).toList());
      case BoundAnnotationReference reference ->
          switch (reference) {
            case BoundAnnotationReference.ClassReference classReference ->
                new CoreAnnotationReference.ClassReference(
                    types.convert(classReference.reflectedType()));
            case BoundAnnotationReference.CallableReference callable ->
                new CoreAnnotationReference.CallableReference(
                    new PendingDefinitionReference(declarationIndex(callable.callable().value())),
                    callable.receiverTypeArguments().stream().map(types::convert).toList(),
                    callable.reifiedArguments().stream().map(types::convert).toList(),
                    callable.virtual());
            case BoundAnnotationReference.FieldReference field ->
                new CoreAnnotationReference.FieldReference(
                    field.ordinal(),
                    types.convert(field.ownerType()),
                    types.convert(field.valueType()));
          };
    };
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
                                        new CoreField(
                                            field.name(),
                                            field.ordinal(),
                                            types.convert(field.type()),
                                            List.of()))
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

  private Declaration convert(BoundAggregate declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types =
        BoundCoreTypeConverter.forAggregate(declaration, nominalTypeIndices);
    List<BoundCallable> constructors =
        declaration.constructors().stream()
            .map(
                constructorId ->
                    program.callables().stream()
                        .filter(callable -> callable.id().equals(constructorId))
                        .findFirst()
                        .orElseThrow())
            .toList();
    CoreDefinition definition =
        new CoreDefinition.Aggregate(
            nominalType(source, declaration.name(), visibility(declaration.visibility())),
            switch (declaration.kind()) {
              case CLASS -> dev.w0fv1.norm.core.CoreAggregateKind.CLASS;
              case VALUE -> dev.w0fv1.norm.core.CoreAggregateKind.VALUE;
              case ANNOTATION -> dev.w0fv1.norm.core.CoreAggregateKind.ANNOTATION;
            },
            switch (declaration.type().category()) {
              case IDENTITY -> CoreValueCategory.IDENTITY;
              case VALUE -> CoreValueCategory.VALUE;
              default -> throw new IllegalStateException("invalid aggregate value category");
            },
            coreTypeParameters(declaration.typeParameters(), types),
            declaration.parentType().map(types::convert),
            declaration.fieldCount(),
            declaration.fields().stream()
                .map(
                    field ->
                        new CoreField(
                            field.name(),
                            field.ordinal(),
                            types.convert(field.type()),
                            field.interceptors().stream()
                                .map(interceptor -> coreInterceptor(interceptor, types))
                                .toList()))
                .toList(),
            declaration.dispatch().stream()
                .map(
                    dispatch ->
                        new dev.w0fv1.norm.core.CoreMethodDispatch(
                            new PendingDefinitionReference(
                                declarationIndex(dispatch.slot().value())),
                            new PendingDefinitionReference(
                                declarationIndex(dispatch.implementation().value())),
                            types.convert(dispatch.receiverType())))
                .toList(),
            declaration.constructors().stream()
                .map(
                    constructor ->
                        (dev.w0fv1.norm.core.CoreDefinitionLink)
                            new PendingDefinitionReference(declarationIndex(constructor.value())))
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
            new CoreBindingShape.Aggregate(
                switch (declaration.kind()) {
                  case CLASS -> dev.w0fv1.norm.core.CoreAggregateKind.CLASS;
                  case VALUE -> dev.w0fv1.norm.core.CoreAggregateKind.VALUE;
                  case ANNOTATION -> dev.w0fv1.norm.core.CoreAggregateKind.ANNOTATION;
                },
                switch (declaration.type().category()) {
                  case IDENTITY -> CoreValueCategory.IDENTITY;
                  case VALUE -> CoreValueCategory.VALUE;
                  default -> throw new IllegalStateException("invalid aggregate value category");
                },
                coreTypeParameters(declaration.typeParameters(), types),
                declaration.parentType().map(types::convert),
                declaration.fields().stream()
                    .map(
                        field ->
                            new CoreBindingShape.Field(
                                field.name(),
                                visibility(field.visibility()),
                                types.convert(field.type())))
                    .toList(),
                constructors.stream()
                    .map(
                        constructor ->
                            new CoreBindingShape.Constructor(
                                constructor.parameters().stream()
                                    .map(
                                        parameter ->
                                            new CoreBindingShape.Parameter(
                                                parameter.name(), types.convert(parameter.type())))
                                    .toList()))
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
    Optional<BoundAggregate> owner =
        declaration.owner().map(value -> aggregateDeclaration(value.value()));
    Optional<CoreType> receiverType =
        declaration
            .receiverType()
            .map(types::convert)
            .or(
                () ->
                    owner.map(
                        value ->
                            types.convert(
                                SemanticType.declared(
                                    value.type().identity(),
                                    value.name(),
                                    value.typeParameters().stream()
                                        .map(BoundTypeParameter::type)
                                        .toList(),
                                    value.type().category()))));
    int ownerTypeParameterCount =
        owner
            .map(value -> value.typeParameters().size())
            .orElseGet(
                () -> declaration.receiverType().map(value -> value.arguments().size()).orElse(0));
    List<BoundTypeParameter> callableTypeParameters =
        declaration
            .typeParameters()
            .subList(ownerTypeParameterCount, declaration.typeParameters().size());
    BoundCoreBodyConverter.Result body =
        new BoundCoreBodyConverter(
                types, receiverType, this::declarationIndex, this::fieldOwnerIndex)
            .convert(declaration);
    callableLocals.put(declaration.id().value(), body.localIndices());
    CoreDefinition definition =
        new CoreDefinition.Callable(
            receiverType,
            coreTypeParameters(callableTypeParameters, types),
            declaration.captures().stream().map(capture -> types.convert(capture.type())).toList(),
            declaration.captures().stream().map(capture -> body.localIndex(capture.id())).toList(),
            declaration.parameters().stream()
                .map(
                    parameter ->
                        new dev.w0fv1.norm.core.CoreCallableParameter(
                            parameter.name(),
                            types.convert(parameter.type()),
                            body.localIndex(parameter.id()),
                            parameter.interceptors().stream()
                                .map(interceptor -> coreInterceptor(interceptor, types))
                                .toList()))
                .toList(),
            declaration.reifiedParameters().stream()
                .map(parameter -> body.localIndex(parameter.source()))
                .toList(),
            declaration.interceptors().stream()
                .map(interceptor -> coreInterceptor(interceptor, types))
                .toList(),
            types.convert(declaration.returnType()),
            body.locals(),
            body.body());
    Optional<String> ownerName =
        owner
            .map(BoundAggregate::name)
            .or(
                () ->
                    declaration.kind() == dev.w0fv1.norm.bound.BoundCallableKind.METHOD
                        ? declaration.receiverType().map(SemanticType::name)
                        : Optional.empty());
    CoreDefinitionRole role =
        switch (declaration.kind()) {
          case CONSTRUCTOR -> CoreDefinitionRole.CONSTRUCTOR;
          case FUNCTION -> CoreDefinitionRole.FUNCTION;
          case EXTENSION -> CoreDefinitionRole.EXTENSION;
          case METHOD -> CoreDefinitionRole.METHOD;
          case LAMBDA -> CoreDefinitionRole.LAMBDA;
        };
    return new Declaration(
        definition,
        origin(declaration.name(), declaration.span(), body.nodeSpans()),
        body.referenceTargets(),
        declaration.kind() == dev.w0fv1.norm.bound.BoundCallableKind.CONSTRUCTOR
                || declaration.kind() == dev.w0fv1.norm.bound.BoundCallableKind.LAMBDA
            ? Optional.empty()
            : Optional.of(
                new BindingSeed(
                    source,
                    ownerName,
                    declaration.name(),
                    visibility(declaration.visibility()),
                    new CoreBindingShape.Callable(
                        switch (declaration.kind()) {
                          case EXTENSION -> dev.w0fv1.norm.core.CoreCallableBindingKind.EXTENSION;
                          case METHOD -> dev.w0fv1.norm.core.CoreCallableBindingKind.METHOD;
                          default -> dev.w0fv1.norm.core.CoreCallableBindingKind.FUNCTION;
                        },
                        coreTypeParameters(callableTypeParameters, types),
                        declaration.parameters().stream()
                            .map(
                                parameter ->
                                    new CoreBindingShape.Parameter(
                                        parameter.name(), types.convert(parameter.type())))
                            .toList(),
                        types.convert(declaration.returnType())),
                    owner
                        .map(
                            value ->
                                value.visibility() == dev.w0fv1.norm.bound.BoundVisibility.PUBLIC)
                        .orElse(true))),
        role);
  }

  private int declarationIndex(String declaration) {
    Integer index = declarationIndices.get(declaration);
    if (index == null)
      throw new IllegalStateException("core declaration is absent: " + declaration);
    return index;
  }

  private dev.w0fv1.norm.core.CoreInterceptor coreInterceptor(
      dev.w0fv1.norm.bound.BoundInterceptor interceptor, BoundCoreTypeConverter types) {
    return new dev.w0fv1.norm.core.CoreInterceptor(
        new PendingDefinitionReference(declarationIndex(interceptor.annotation().value())),
        interceptor.values().stream().map(value -> annotationValue(value, types)).toList());
  }

  private int fieldOwnerIndex(String field) {
    Integer index = fieldOwnerIndices.get(field);
    if (index == null) throw new IllegalStateException("core field owner is absent: " + field);
    return index;
  }

  private int fieldOrdinal(String field) {
    Integer ordinal = fieldOrdinals.get(field);
    if (ordinal == null) throw new IllegalStateException("core field is absent: " + field);
    return ordinal;
  }

  private int localIndex(String callable, dev.w0fv1.norm.bound.BoundLocalId local) {
    Map<dev.w0fv1.norm.bound.BoundLocalId, Integer> locals = callableLocals.get(callable);
    if (locals == null || !locals.containsKey(local)) {
      throw new IllegalStateException("core local is absent: " + local);
    }
    return locals.get(local);
  }

  private SourceOwner sourceOwner(String declaration) {
    SourceOwner source = sourceOwners.get(declaration);
    if (source == null)
      throw new IllegalStateException("declaration source is absent: " + declaration);
    return source;
  }

  private BoundAggregate aggregateDeclaration(String declaration) {
    BoundAggregate value = aggregates.get(declaration);
    if (value == null) throw new IllegalStateException("core aggregate is absent: " + declaration);
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

  record Result(
      List<Declaration> declarations,
      List<AnnotationSeed> annotations,
      Optional<Integer> entryPointIndex) {
    Result {
      declarations = List.copyOf(declarations);
      annotations = List.copyOf(annotations);
      entryPointIndex = Objects.requireNonNull(entryPointIndex, "entryPointIndex");
    }
  }

  record AnnotationSeed(
      int annotationDeclaration, PendingAnnotationTarget target, List<CoreAnnotationValue> values) {
    AnnotationSeed {
      if (annotationDeclaration < 0) {
        throw new IllegalArgumentException("annotation declaration index must not be negative");
      }
      Objects.requireNonNull(target, "target");
      values = List.copyOf(values);
    }

    CoreAnnotationApplication resolve(
        Map<Integer, dev.w0fv1.norm.core.DefinitionId> definitions,
        List<DefinitionOccurrenceId> occurrences) {
      java.util.function.Function<
              dev.w0fv1.norm.core.CoreDefinitionLink, dev.w0fv1.norm.core.CoreDefinitionLink>
          links =
              link -> {
                if (link instanceof PendingDefinitionReference pending) {
                  return new DefinitionReference.External(
                      definitions.get(pending.declarationIndex()));
                }
                return link;
              };
      return new CoreAnnotationApplication(
          definitions.get(annotationDeclaration),
          target.resolve(occurrences),
          values.stream().map(value -> resolveAnnotationValue(value, links)).toList());
    }

    private static CoreAnnotationValue resolveAnnotationValue(
        CoreAnnotationValue value,
        java.util.function.Function<
                dev.w0fv1.norm.core.CoreDefinitionLink, dev.w0fv1.norm.core.CoreDefinitionLink>
            links) {
      return new CoreAnnotationValue(
          CoreTypes.mapLinks(value.type(), links), resolveAnnotationContent(value.value(), links));
    }

    private static CoreAnnotationValue.Content resolveAnnotationContent(
        CoreAnnotationValue.Content value,
        java.util.function.Function<
                dev.w0fv1.norm.core.CoreDefinitionLink, dev.w0fv1.norm.core.CoreDefinitionLink>
            links) {
      return switch (value) {
        case CoreAnnotationValue.Literal literal -> literal;
        case CoreAnnotationValue.Null ignored -> CoreAnnotationValue.Null.INSTANCE;
        case CoreAnnotationValue.ListValue list ->
            new CoreAnnotationValue.ListValue(
                list.values().stream().map(item -> resolveAnnotationValue(item, links)).toList());
        case CoreAnnotationReference.ClassReference classReference ->
            new CoreAnnotationReference.ClassReference(
                CoreTypes.mapLinks(classReference.reflectedType(), links));
        case CoreAnnotationReference.CallableReference callable ->
            new CoreAnnotationReference.CallableReference(
                links.apply(callable.callable()),
                callable.receiverTypeArguments().stream()
                    .map(type -> CoreTypes.mapLinks(type, links))
                    .toList(),
                callable.reifiedArguments().stream()
                    .map(type -> CoreTypes.mapLinks(type, links))
                    .toList(),
                callable.virtual());
        case CoreAnnotationReference.FieldReference field ->
            new CoreAnnotationReference.FieldReference(
                field.ordinal(),
                CoreTypes.mapLinks(field.ownerType(), links),
                CoreTypes.mapLinks(field.valueType(), links));
      };
    }
  }

  sealed interface PendingAnnotationTarget
      permits PendingAnnotationTarget.Package,
          PendingAnnotationTarget.Definition,
          PendingAnnotationTarget.Field,
          PendingAnnotationTarget.Parameter,
          PendingAnnotationTarget.Local {
    CoreAnnotationTarget resolve(List<DefinitionOccurrenceId> occurrences);

    record Package(dev.w0fv1.norm.value.ModuleCoordinate module, String packageName)
        implements PendingAnnotationTarget {
      @Override
      public CoreAnnotationTarget resolve(List<DefinitionOccurrenceId> occurrences) {
        return new CoreAnnotationTarget.Package(module, packageName);
      }
    }

    record Definition(dev.w0fv1.norm.value.AnnotationTarget kind, int declaration)
        implements PendingAnnotationTarget {
      @Override
      public CoreAnnotationTarget resolve(List<DefinitionOccurrenceId> occurrences) {
        return new CoreAnnotationTarget.Definition(kind, occurrences.get(declaration));
      }
    }

    record Field(int owner, int ordinal) implements PendingAnnotationTarget {
      @Override
      public CoreAnnotationTarget resolve(List<DefinitionOccurrenceId> occurrences) {
        return new CoreAnnotationTarget.Field(occurrences.get(owner), ordinal);
      }
    }

    record Parameter(int callable, int index) implements PendingAnnotationTarget {
      @Override
      public CoreAnnotationTarget resolve(List<DefinitionOccurrenceId> occurrences) {
        return new CoreAnnotationTarget.Parameter(occurrences.get(callable), index);
      }
    }

    record Local(int callable, int index) implements PendingAnnotationTarget {
      @Override
      public CoreAnnotationTarget resolve(List<DefinitionOccurrenceId> occurrences) {
        return new CoreAnnotationTarget.Local(occurrences.get(callable), index);
      }
    }
  }

  static final class Declaration {
    private final CoreDefinition definition;
    private final CoreDefinitionOrigin origin;
    private final Map<Integer, Integer> referenceTargets;
    private final Optional<BindingSeed> binding;
    private final CoreDefinitionRole role;

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
      this(definition, origin, referenceTargets, binding, role(definition));
    }

    private Declaration(
        CoreDefinition definition,
        CoreDefinitionOrigin origin,
        Map<Integer, Integer> referenceTargets,
        Optional<BindingSeed> binding,
        CoreDefinitionRole role) {
      this.definition = Objects.requireNonNull(definition, "definition");
      this.origin = Objects.requireNonNull(origin, "origin");
      this.referenceTargets = Map.copyOf(referenceTargets);
      this.binding = Objects.requireNonNull(binding, "binding");
      this.role = Objects.requireNonNull(role, "role");
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

    CoreDefinitionRole role() {
      return role;
    }

    private static CoreDefinitionRole role(CoreDefinition definition) {
      return switch (definition) {
        case CoreDefinition.Aggregate ignored -> CoreDefinitionRole.AGGREGATE;
        case CoreDefinition.Enum ignored -> CoreDefinitionRole.ENUM;
        case CoreDefinition.Interface ignored -> CoreDefinitionRole.INTERFACE;
        case CoreDefinition.InterfaceMethod ignored -> CoreDefinitionRole.INTERFACE_METHOD;
        case CoreDefinition.BuiltinConformance ignored -> CoreDefinitionRole.BUILTIN_CONFORMANCE;
        case CoreDefinition.Callable ignored ->
            throw new IllegalArgumentException("callable declaration role must be explicit");
      };
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
                callable.kind(),
                resolveTypeParameters(callable.typeParameters(), links),
                callable.parameters().stream()
                    .map(
                        parameter ->
                            new CoreBindingShape.Parameter(
                                parameter.label(), CoreTypes.mapLinks(parameter.type(), links)))
                    .toList(),
                CoreTypes.mapLinks(callable.returnType(), links));
        case CoreBindingShape.Aggregate aggregateShape ->
            new CoreBindingShape.Aggregate(
                aggregateShape.kind(),
                aggregateShape.valueCategory(),
                resolveTypeParameters(aggregateShape.typeParameters(), links),
                aggregateShape.parentType().map(type -> CoreTypes.mapLinks(type, links)),
                aggregateShape.fields().stream()
                    .map(
                        field ->
                            new CoreBindingShape.Field(
                                field.name(),
                                field.visibility(),
                                CoreTypes.mapLinks(field.type(), links)))
                    .toList(),
                aggregateShape.constructors().stream()
                    .map(
                        constructor ->
                            new CoreBindingShape.Constructor(
                                constructor.parameters().stream()
                                    .map(
                                        parameter ->
                                            new CoreBindingShape.Parameter(
                                                parameter.label(),
                                                CoreTypes.mapLinks(parameter.type(), links)))
                                    .toList()))
                    .toList(),
                aggregateShape.conformances().stream()
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
