package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundClass;
import dev.w0fv1.norm.bound.BoundEnum;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.bound.BoundSource;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionOrigin;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreVisibility;
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
  private final Map<String, Integer> fieldOwnerIndices = new LinkedHashMap<>();
  private final Map<String, Integer> enumMemberOrdinals = new LinkedHashMap<>();

  BoundCoreConverter(
      BoundProgram program, Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates) {
    this.program = Objects.requireNonNull(program, "program");
    this.sourceCoordinates = Map.copyOf(sourceCoordinates);
  }

  Result convert() {
    indexDeclarations();
    List<Declaration> declarations = new ArrayList<>();
    program.enums().forEach(value -> declarations.add(convert(value)));
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
      for (var member : value.members()) {
        enumMemberOrdinals.put(member.id().value(), member.ordinal());
      }
    }
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
      source.classes().forEach(value -> sourceOwners.put(value.value(), owner));
      source.callables().forEach(value -> sourceOwners.put(value.value(), owner));
    }
  }

  private Declaration convert(BoundEnum declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    CoreDefinition definition =
        new CoreDefinition.Enum(
            nominalType(source, declaration.name(), visibility(declaration.visibility())),
            declaration.members().stream()
                .map(dev.w0fv1.norm.bound.BoundEnumMember::name)
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
                declaration.members().stream()
                    .map(dev.w0fv1.norm.bound.BoundEnumMember::name)
                    .toList()),
            true));
  }

  private Declaration convert(BoundClass declaration) {
    SourceOwner source = sourceOwner(declaration.id().value());
    BoundCoreTypeConverter types = BoundCoreTypeConverter.forClass(declaration, nominalTypeIndices);
    CoreDefinition definition =
        new CoreDefinition.Class(
            nominalType(source, declaration.name(), visibility(declaration.visibility())),
            declaration.typeParameters().size(),
            declaration.fields().stream()
                .map(field -> new CoreField(field.ordinal(), types.convert(field.type())))
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
            new CoreBindingShape.Class(
                declaration.typeParameters().size(),
                declaration.fields().stream()
                    .map(
                        field ->
                            new CoreBindingShape.Field(
                                field.name(),
                                visibility(field.visibility()),
                                types.convert(field.type())))
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
                        value.typeParameters(),
                        value.type().category())));
    BoundCoreBodyConverter.Result body =
        new BoundCoreBodyConverter(
                types,
                receiverType,
                this::declarationIndex,
                this::enumMemberOrdinal,
                this::fieldOwnerIndex)
            .convert(declaration);
    CoreDefinition definition =
        new CoreDefinition.Callable(
            receiverType,
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
                declaration.reifiedParameters().size(),
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

  private int enumMemberOrdinal(String member) {
    Integer ordinal = enumMemberOrdinals.get(member);
    if (ordinal == null) throw new IllegalStateException("core enum member is absent: " + member);
    return ordinal;
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
    private final BindingSeed binding;

    private Declaration(
        CoreDefinition definition,
        CoreDefinitionOrigin origin,
        Map<Integer, Integer> referenceTargets,
        BindingSeed binding) {
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

    CoreBinding bind(
        DefinitionOccurrenceId occurrence,
        Set<DocumentId> exportedSources,
        java.util.function.Function<PendingDefinitionReference, DefinitionReference> resolver) {
      return binding.bind(occurrence, exportedSources, resolver);
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
                callable.typeParameterCount(),
                callable.parameters().stream()
                    .map(
                        parameter ->
                            new CoreBindingShape.Parameter(
                                parameter.label(), CoreTypes.mapLinks(parameter.type(), links)))
                    .toList(),
                CoreTypes.mapLinks(callable.returnType(), links));
        case CoreBindingShape.Class classShape ->
            new CoreBindingShape.Class(
                classShape.typeParameterCount(),
                classShape.fields().stream()
                    .map(
                        field ->
                            new CoreBindingShape.Field(
                                field.name(),
                                field.visibility(),
                                CoreTypes.mapLinks(field.type(), links)))
                    .toList());
        case CoreBindingShape.Enum enumShape -> enumShape;
      };
    }
  }
}
