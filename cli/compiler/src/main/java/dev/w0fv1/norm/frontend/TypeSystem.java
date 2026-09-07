package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.builtin.BuiltinSymbols;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceRequirement;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateField;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateView;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeApplication;
import dev.w0fv1.norm.semantic.TypeArguments;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.TypeRelations;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TypeSystem {
  private final DeclarationCatalog declarations;
  private final BuiltinSymbols builtins;
  private final TypeResolutionState resolution;
  private final SemanticModelBuilder model;
  private final DiagnosticBag diagnostics;
  final TypeRelations.DeclarationGraph typeRelations;

  TypeSystem(
      DeclarationCatalog declarations,
      BuiltinSymbols builtins,
      TypeResolutionState resolution,
      SemanticModelBuilder model,
      DiagnosticBag diagnostics) {
    this.declarations = declarations;
    this.builtins = builtins;
    this.resolution = resolution;
    this.model = model;
    this.diagnostics = diagnostics;
    typeRelations = new TypeRelations.DeclarationGraph(this::directParents);
  }

  SymbolId defaultMethodId(Syntax.InterfaceMethodDecl method) {
    return new SymbolId(model.declarationSymbols().get(method).value() + "/default");
  }

  void registerBounds(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> declaredTypes) {
    for (Syntax.TypeParameter parameter : parameters) {
      if (parameter.upperBound().isEmpty()) continue;
      Syntax.TypeRef boundSyntax = parameter.upperBound().orElseThrow();
      SemanticType bound = resolveType(boundSyntax, declaredTypes);
      resolution.typeParameterBounds.put(declaredTypes.get(parameter.name()).identity(), bound);
      validateType(boundSyntax, false);
      Syntax.AggregateDecl aggregate = resolveAggregate(bound);
      boolean classBound = aggregate != null && aggregate.kind() == Syntax.AggregateKind.CLASS;
      boolean typeParameterBound = bound.kind() == SemanticType.Kind.TYPE_PARAMETER;
      if (bound.isNullable()
          || resolveInterface(bound) == null && !classBound && !typeParameterBound) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type parameter bound must be a non-null class, interface, or type parameter",
            boundSyntax.span());
      }
    }
    for (Syntax.TypeParameter parameter : parameters) {
      SemanticType declared = declaredTypes.get(parameter.name());
      Set<String> visited = new HashSet<>();
      SemanticType current = declared;
      while (current != null && current.kind() == SemanticType.Kind.TYPE_PARAMETER) {
        if (!visited.add(current.identity())) {
          diagnostics.error(
              TYPE_MISMATCH,
              "cyclic type parameter bound",
              parameter.upperBound().map(Syntax.TypeRef::span).orElse(parameter.nameSpan()));
          break;
        }
        current = resolution.typeParameterBounds.get(current.identity());
      }
    }
  }

  void validateTypeParameterDefaults(
      List<Syntax.TypeParameter> parameters,
      Map<String, SemanticType> declaredTypes,
      boolean publicSignature) {
    Set<String> parameterNames =
        parameters.stream()
            .map(Syntax.TypeParameter::name)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> available = new HashSet<>();
    boolean defaultSeen = false;
    for (Syntax.TypeParameter parameter : parameters) {
      if (parameter.defaultType().isEmpty()) {
        if (defaultSeen) {
          diagnostics.error(
              TYPE_MISMATCH,
              "required type parameter follows a default type parameter",
              parameter.nameSpan());
        }
        available.add(parameter.name());
        continue;
      }
      defaultSeen = true;
      Syntax.TypeRef defaultSyntax = parameter.defaultType().orElseThrow();
      Set<String> referenced = new HashSet<>();
      collectTypeNames(defaultSyntax, referenced);
      referenced.retainAll(parameterNames);
      referenced.removeAll(available);
      if (!referenced.isEmpty()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type parameter default may reference earlier type parameters only",
            defaultSyntax.span());
      }
      validateType(defaultSyntax, false);
      if (publicSignature) validatePublicType(defaultSyntax);
      SemanticType defaultType = resolveType(defaultSyntax, declaredTypes);
      if (parameter.upperBound().isPresent()) {
        SemanticType bound = resolveType(parameter.upperBound().orElseThrow(), declaredTypes);
        if (!isAssignable(bound, defaultType)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "default type '"
                  + defaultType.displayName()
                  + "' does not satisfy bound '"
                  + bound.displayName()
                  + "' for '"
                  + parameter.name()
                  + "'",
              defaultSyntax.span());
        }
      }
      available.add(parameter.name());
    }
  }

  private static void collectTypeNames(Syntax.TypeRef type, Set<String> names) {
    if (type.isWildcard()) return;
    names.add(type.name());
    type.arguments().forEach(argument -> collectTypeNames(argument, names));
  }

  void collectConformances(
      Syntax.InterfaceDecl declaration,
      SemanticType instance,
      Map<String, SemanticType> result,
      SourceSpan span) {
    SemanticType existing = result.putIfAbsent(instance.identity(), instance);
    if (existing != null) {
      if (!existing.equals(instance)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "interface '" + declaration.name() + "' is inherited with conflicting type arguments",
            span);
      }
      return;
    }
    Map<String, SemanticType> substitutions = interfaceSubstitutions(declaration, instance);
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    Syntax.Program previous = resolution.currentProgram;
    resolution.currentProgram = declarations.owner(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      SemanticType parent = resolveType(parentRef, parameters).substitute(substitutions);
      Syntax.InterfaceDecl parentDecl = resolveInterface(parent);
      if (parentDecl != null) collectConformances(parentDecl, parent, result, span);
    }
    resolution.currentProgram = previous;
  }

  Map<String, SemanticType> interfaceSubstitutions(
      Syntax.InterfaceDecl declaration, SemanticType instance) {
    Map<String, SemanticType> parameterTypes = interfaceTypeParameters(declaration);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(declaration.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = parameterTypes.get(declaration.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return Map.copyOf(result);
  }

  List<SemanticType> nominalViews(SemanticType actual) {
    return typeRelations.views(actual);
  }

  private List<SemanticType> directParents(SemanticType type) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      return Optional.ofNullable(resolution.typeParameterBounds.get(type.identity())).stream()
          .toList();
    }
    List<SemanticType> result =
        new ArrayList<>(
            builtins.protocolConformances(type).stream()
                .filter(parent -> resolveInterface(parent) != null)
                .toList());
    Syntax.InterfaceDecl contract = resolveInterface(type);
    Syntax.AggregateDecl aggregate = resolveAggregate(type);
    if (contract == null && aggregate == null) return List.copyOf(result);
    Syntax.Program previous = resolution.currentProgram;
    try {
      Object owner = contract == null ? aggregate : contract;
      resolution.currentProgram = declarations.owner(owner);
      Map<String, SemanticType> parameters =
          contract == null ? aggregateTypeParameters(aggregate) : interfaceTypeParameters(contract);
      Map<String, SemanticType> substitutions =
          contract == null
              ? aggregateSubstitutions(aggregate, type)
              : interfaceSubstitutions(contract, type);
      if (aggregate != null) directParentType(aggregate, type).ifPresent(result::add);
      List<Syntax.TypeRef> parents =
          contract == null ? aggregate.implementedInterfaces() : contract.extendedInterfaces();
      parents.stream()
          .map(parent -> resolveType(parent, parameters).substitute(substitutions))
          .forEach(result::add);
      return List.copyOf(result);
    } finally {
      resolution.currentProgram = previous;
    }
  }

  void validateType(Syntax.TypeRef type, boolean allowVoid) {
    validateType(type, allowVoid, false);
  }

  void validateReferenceCapableType(Syntax.TypeRef type) {
    validateType(type, false, true);
  }

  private void validateType(
      Syntax.TypeRef type, boolean allowVoid, boolean allowTopLevelReference) {
    if (type.isWildcard()) return;
    if (type.name().equals("ref")) {
      if (!allowTopLevelReference) {
        diagnostics.error(
            TYPE_MISMATCH, "ref is only valid as a local or callable parameter type", type.span());
      }
      if (type.arguments().size() != 1) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type 'ref' requires 1 type argument, found " + type.arguments().size(),
            type.span());
        type.arguments().forEach(argument -> validateType(argument, false, false));
        return;
      }
      if (type.nullable()) {
        diagnostics.error(INVALID_NULLABLE_TYPE, "ref cannot be nullable", type.span());
      }
      Syntax.TypeRef targetSyntax = type.arguments().getFirst();
      validateType(targetSyntax, false, false);
      SemanticType target = resolveType(targetSyntax, resolution.activeTypeParameters);
      if (target.isReference() || target.isNullable() || target.category() != ValueCategory.VALUE) {
        diagnostics.error(
            TYPE_MISMATCH, "ref target must be a non-reference value type", targetSyntax.span());
      }
      return;
    }
    String name = type.displayName();
    SymbolId typeParameter = resolution.activeTypeParameterSymbols.get(type.name());
    if (typeParameter != null) {
      model.putBinding(type.span(), typeParameter);
    } else {
      SymbolId alias = importedAlias(type.name());
      if (alias != null) {
        model.putBinding(type.span(), alias);
      } else {
        typeSymbol(type.name()).ifPresent(symbol -> model.putBinding(type.span(), symbol.id()));
      }
    }
    int arity =
        type.name().equals("Function") ? type.arguments().size() : declaredTypeArity(type.name());
    if (resolution.activeTypeParameters.containsKey(type.name())) arity = 0;
    if (type.name().equals("Function") && type.arguments().isEmpty()) {
      diagnostics.error(TYPE_MISMATCH, "Function requires a complete call signature", type.span());
      return;
    }
    if (arity < 0 && !resolution.activeTypeParameters.containsKey(type.name())) {
      diagnostics.error(UNKNOWN_NAME, "unknown type '" + name + "'", type.span());
      return;
    }
    if (!allowVoid && type.name().equals("Void")) {
      diagnostics.error(TYPE_MISMATCH, "type 'Void' is not valid here", type.span());
      return;
    }
    if (type.nullable() && type.name().equals("Void")) {
      diagnostics.error(INVALID_NULLABLE_TYPE, "Void cannot be nullable", type.span());
      return;
    }
    List<Syntax.TypeParameter> declaredParameters = declaredTypeParameters(type.name());
    boolean validArity =
        declaredParameters == null
            ? arity == type.arguments().size()
            : acceptsTypeArgumentCount(declaredParameters, type.arguments().size());
    if (!type.name().equals("Function") && !validArity) {
      diagnostics.error(
          TYPE_MISMATCH,
          typeArgumentCountMessage(
              "type", type.name(), declaredParameters, arity, type.arguments().size()),
          type.span());
    }
    for (int index = 0; index < type.arguments().size(); index++) {
      Syntax.TypeRef argument = type.arguments().get(index);
      validateType(argument, type.name().equals("Function") && index == 0, false);
    }
    validateDeclaredTypeBounds(type);
  }

  void validateDeclaredTypeBounds(Syntax.TypeRef reference) {
    if (reference.isWildcard()) return;
    List<Syntax.TypeParameter> parameters;
    Map<String, SemanticType> declared;
    Object declaration;
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(reference.name());
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(reference.name());
    Syntax.EnumDecl enumDecl = resolveEnum(reference.name());
    if (interfaceDecl != null) {
      parameters = interfaceDecl.typeParameters();
      declared = interfaceTypeParameters(interfaceDecl);
      declaration = interfaceDecl;
    } else if (aggregateDecl != null) {
      parameters = aggregateDecl.typeParameters();
      declared = aggregateTypeParameters(aggregateDecl);
      declaration = aggregateDecl;
    } else if (enumDecl != null) {
      parameters = enumDecl.typeParameters();
      declared = enumTypeParameters(enumDecl);
      declaration = enumDecl;
    } else {
      return;
    }
    if (!acceptsTypeArgumentCount(parameters, reference.arguments().size())) return;
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    List<SemanticType> providedArguments =
        reference.arguments().stream()
            .map(argument -> resolveType(argument, resolution.activeTypeParameters))
            .toList();
    List<SemanticType> actualArguments =
        completeTypeArguments(parameters, declared, declaration, providedArguments);
    for (int index = 0; index < parameters.size(); index++) {
      substitutions.put(
          declared.get(parameters.get(index).name()).identity(), actualArguments.get(index));
    }
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      if (parameter.upperBound().isEmpty()) continue;
      SemanticType bound =
          resolveDeclarationType(parameter.upperBound().orElseThrow(), declaration, declared)
              .substitute(substitutions);
      SemanticType actual = actualArguments.get(index);
      if (actual.kind() != SemanticType.Kind.EXISTENTIAL && !isAssignable(bound, actual)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type argument '"
                + actual.displayName()
                + "' does not satisfy bound '"
                + bound.displayName()
                + "' for '"
                + parameter.name()
                + "'",
            index < reference.arguments().size()
                ? reference.arguments().get(index).span()
                : parameter.defaultType().orElseThrow().span());
      }
    }
  }

  void requireBoth(SemanticType expected, SemanticType left, SemanticType right, SourceSpan span) {
    requireType(expected, left, span);
    requireType(expected, right, span);
  }

  void requireType(SemanticType expected, SemanticType actual, SourceSpan span) {
    requireAssignable(expected, actual, span);
  }

  void requireAssignable(SemanticType expected, SemanticType actual, SourceSpan span) {
    if (!isAssignable(expected, actual)) {
      DiagnosticCode code =
          actual.mayContainNull() && !expected.mayContainNull()
              ? NULLABILITY_MISMATCH
              : TYPE_MISMATCH;
      diagnostics.error(
          code, "expected " + expected.displayName() + " but found " + actual.displayName(), span);
    }
  }

  boolean isAssignable(SemanticType expected, SemanticType actual) {
    return typeRelations.isAssignable(expected, actual);
  }

  Optional<SemanticType> commonType(SemanticType left, SemanticType right) {
    return typeRelations.commonType(left, right);
  }

  SemanticType receiverType(SemanticType type, boolean nullSafe, SourceSpan span) {
    if (!typeRelations.mayContainNull(type)) return type;
    if (!nullSafe) {
      diagnostics.error(
          UNSAFE_NULLABLE_ACCESS,
          "nullable value of type " + type.displayName() + " must be narrowed or accessed with ?.",
          span);
    }
    return type.nonNullable();
  }

  Optional<SemanticType> directParentType(Syntax.AggregateDecl declaration, SemanticType instance) {
    if (declaration.extendedClass().isEmpty()) return Optional.empty();
    Syntax.Program previous = resolution.currentProgram;
    resolution.currentProgram = declarations.ownerOr(declaration, resolution.currentProgram);
    SemanticType parent =
        resolveType(declaration.extendedClass().orElseThrow(), aggregateTypeParameters(declaration))
            .substitute(aggregateSubstitutions(declaration, instance));
    resolution.currentProgram = previous;
    return Optional.of(parent);
  }

  List<AggregateView> aggregateViews(SemanticType instance) {
    List<AggregateView> result = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    SemanticType current = instance.nonNullable();
    while (visited.add(current.identity())) {
      Syntax.AggregateDecl declaration = resolveAggregate(current);
      if (declaration == null) break;
      result.add(new AggregateView(declaration, current));
      Optional<SemanticType> parent = directParentType(declaration, current);
      if (parent.isEmpty()) break;
      current = parent.orElseThrow().nonNullable();
    }
    return List.copyOf(result);
  }

  Symbol register(
      Object declaration,
      String name,
      SymbolKind kind,
      SemanticType type,
      SourceSpan nameSpan,
      SymbolId owner,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> parameters) {
    SymbolId id = model.allocate(nameSpan.source().id());
    Symbol symbol =
        new Symbol(
            id,
            name,
            kind,
            type,
            Optional.of(nameSpan.location()),
            Optional.ofNullable(owner),
            typeParameters,
            parameters,
            "");
    model.putSymbol(id, symbol);
    model.putDeclaration(declaration, id);
    model.putBinding(nameSpan, id);
    return symbol;
  }

  Symbol registerDeclaration(
      Object declaration,
      String name,
      SymbolKind kind,
      SemanticType type,
      SourceSpan nameSpan,
      SymbolId owner,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> parameters) {
    SymbolId id = declarationId(declaration, kind, name, owner);
    Symbol symbol =
        new Symbol(
            id,
            name,
            kind,
            type,
            Optional.of(nameSpan.location()),
            Optional.ofNullable(owner),
            typeParameters,
            parameters,
            "");
    model.putSymbol(id, symbol);
    model.putDeclaration(declaration, id);
    model.putBinding(nameSpan, id);
    return symbol;
  }

  List<TypeParameterInfo> symbolTypeParameters(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> types) {
    return parameters.stream()
        .map(
            parameter -> {
              SemanticType type = types.get(parameter.name());
              Optional<SemanticType> bound =
                  parameter.upperBound().map(value -> resolveType(value, types));
              Optional<SemanticType> defaultType =
                  parameter.defaultType().map(value -> resolveType(value, types));
              return new TypeParameterInfo(parameter.name(), type, bound, defaultType);
            })
        .toList();
  }

  void registerTypeParameters(
      List<Syntax.TypeParameter> parameters, SymbolId owner, Map<String, SemanticType> types) {
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      SymbolId id = SymbolId.authored(DeclarationIdentity.typeParameter(owner, index));
      Symbol symbol =
          new Symbol(
              id,
              parameter.name(),
              SymbolKind.TYPE_PARAMETER,
              types.get(parameter.name()),
              Optional.of(parameter.nameSpan().location()),
              Optional.of(owner),
              List.of(),
              List.of(),
              "");
      model.putSymbol(id, symbol);
      model.putDeclaration(parameter, id);
      model.putBinding(parameter.nameSpan(), id);
    }
  }

  Map<String, SymbolId> typeParameterSymbols(
      Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    Map<String, SymbolId> result = new LinkedHashMap<>();
    if (owner != null) {
      owner
          .typeParameters()
          .forEach(
              parameter -> result.put(parameter.name(), model.declarationSymbols().get(parameter)));
    }
    function
        .typeParameters()
        .forEach(
            parameter -> result.put(parameter.name(), model.declarationSymbols().get(parameter)));
    return Map.copyOf(result);
  }

  Map<String, SymbolId> typeParameterSymbols(List<Syntax.TypeParameter> parameters) {
    Map<String, SymbolId> result = new LinkedHashMap<>();
    parameters.forEach(
        parameter -> result.put(parameter.name(), model.declarationSymbols().get(parameter)));
    return Map.copyOf(result);
  }

  void addMember(SymbolId owner, SymbolId member) {
    model.addMember(owner, member);
  }

  Optional<Symbol> typeSymbol(String name) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null)
      return Optional.ofNullable(
          model.symbols().get(model.declarationSymbols().get(interfaceDecl)));
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl != null)
      return Optional.ofNullable(
          model.symbols().get(model.declarationSymbols().get(aggregateDecl)));
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    if (enumDecl != null)
      return Optional.ofNullable(model.symbols().get(model.declarationSymbols().get(enumDecl)));
    return builtins.type(name);
  }

  List<ParameterInfo> parameters(List<Syntax.Parameter> parameters) {
    return parameters(parameters, Map.of(), resolution.activeTypeParameters);
  }

  List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters, Map<String, SemanticType> substitutions) {
    return parameters(parameters, substitutions, resolution.activeTypeParameters);
  }

  List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarationTypes) {
    return parameters.stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    resolveType(parameter.type(), declarationTypes).substitute(substitutions),
                    parameter.defaultValue().isPresent()))
        .toList();
  }

  List<ParameterInfo> parametersOf(
      Syntax.FunctionDecl function, Map<String, SemanticType> substitutions) {
    return function.parameters().stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    resolveDeclarationType(
                            parameter.type(), function, typeParameters(function, ownerOf(function)))
                        .substitute(substitutions),
                    parameter.defaultValue().isPresent()))
        .toList();
  }

  List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields, Map<String, SemanticType> substitutions) {
    return fieldParameters(fields, substitutions, resolution.activeTypeParameters);
  }

  List<ParameterInfo> fieldParameters(
      Syntax.AggregateDecl aggregateDecl, Map<String, SemanticType> substitutions) {
    return aggregateDecl.fields().stream()
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    resolveDeclarationType(
                            field.type(), field, aggregateTypeParameters(aggregateDecl))
                        .substitute(substitutions),
                    field.defaultValue().isPresent()))
        .toList();
  }

  List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarationTypes) {
    return fields.stream()
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    resolveType(field.type(), declarationTypes).substitute(substitutions),
                    field.defaultValue().isPresent()))
        .toList();
  }

  SemanticType appliedType(String name, List<Syntax.TypeRef> arguments, SourceSpan span) {
    int arity = declaredTypeArity(name);
    if (arity < 0) return sourceType(name, List.of());
    List<Syntax.TypeParameter> parameters = declaredTypeParameters(name);
    boolean validArity =
        parameters == null
            ? arity == arguments.size()
            : acceptsTypeArgumentCount(parameters, arguments.size());
    if (!validArity) {
      diagnostics.error(
          TYPE_MISMATCH,
          typeArgumentCountMessage("type", name, parameters, arity, arguments.size()),
          span);
    }
    List<SemanticType> resolved =
        arguments.stream()
            .map(argument -> resolveCheckedType(argument, resolution.activeTypeParameters))
            .toList();
    if (builtins.isType(name)) return builtins.instantiate(name, resolved);
    DeclaredType declaration = declaredType(name);
    return sourceType(
        name,
        declaration == null
            ? resolved
            : completeTypeArguments(
                declaration.parameters(),
                declaration.types(),
                declaration.declaration(),
                resolved));
  }

  SemanticType resolveType(Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    SemanticType parameter = typeParameters.get(type.name());
    if (parameter != null) {
      if (parameter.containsReference()) return SemanticType.DYNAMIC;
      return type.nullable() ? parameter.nullable() : parameter;
    }
    List<SemanticType> arguments =
        type.arguments().stream().map(argument -> resolveType(argument, typeParameters)).toList();
    return TypeApplication.resolve(
        type,
        arguments,
        () -> {
          DeclaredType declaration = declaredType(type.name());
          return builtins.isType(type.name())
              ? builtins.instantiate(type.name(), arguments)
              : sourceType(
                  type.name(),
                  declaration == null
                      ? arguments
                      : completeTypeArguments(
                          declaration.parameters(),
                          declaration.types(),
                          declaration.declaration(),
                          arguments));
        });
  }

  SemanticType resolveCheckedType(Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    validateType(type, false);
    SemanticType resolved = resolveType(type, typeParameters);
    return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
  }

  SemanticType resolveDeclarationType(
      Syntax.TypeRef type, Object declaration, Map<String, SemanticType> typeParameters) {
    Syntax.Program previous = resolution.currentProgram;
    resolution.currentProgram = declarations.ownerOr(declaration, previous);
    try {
      return resolveType(type, typeParameters);
    } finally {
      resolution.currentProgram = previous;
    }
  }

  void validatePublicType(Syntax.TypeRef type) {
    if (type.isWildcard()) return;
    if (resolution.activeTypeParameters.containsKey(type.name())) return;
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(type.name());
    Syntax.EnumDecl enumDecl = resolveEnum(type.name());
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(type.name());
    boolean privateType =
        interfaceDecl != null && interfaceDecl.visibility() == Syntax.Visibility.PRIVATE
            || aggregateDecl != null && aggregateDecl.visibility() == Syntax.Visibility.PRIVATE
            || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE;
    if (privateType) {
      diagnostics.error(
          TYPE_MISMATCH,
          "private type '" + type.name() + "' cannot appear in a public signature",
          type.span());
    }
    type.arguments().forEach(this::validatePublicType);
  }

  void validateTypeArgumentCount(
      String name, int expected, List<Syntax.TypeRef> arguments, SourceSpan span) {
    if (arguments.size() != expected) {
      diagnostics.error(
          INVALID_CALL,
          "function '"
              + name
              + "' requires "
              + expected
              + " type argument(s), found "
              + arguments.size(),
          span);
    }
  }

  void validateTypeArgumentCount(
      String name,
      List<Syntax.TypeParameter> parameters,
      List<Syntax.TypeRef> arguments,
      SourceSpan span) {
    if (!acceptsTypeArgumentCount(parameters, arguments.size())) {
      diagnostics.error(
          INVALID_CALL,
          typeArgumentCountMessage(
              "function", name, parameters, parameters.size(), arguments.size()),
          span);
    }
  }

  void validateSemanticTypeArgumentCount(
      String name,
      List<TypeParameterInfo> parameters,
      List<Syntax.TypeRef> arguments,
      SourceSpan span) {
    int required = 0;
    for (int index = 0; index < parameters.size(); index++) {
      if (parameters.get(index).defaultType().isEmpty()) required = index + 1;
    }
    if (arguments.size() < required || arguments.size() > parameters.size()) {
      diagnostics.error(
          INVALID_CALL,
          typeArgumentCountMessage("function", name, required, parameters.size(), arguments.size()),
          span);
    }
  }

  void bindDeclarationUse(SourceSpan span, String localName, Object declaration) {
    for (Syntax.ImportDecl imported : resolution.currentProgram.imports()) {
      if (imported.alias().isPresent() && imported.localName().equals(localName)) {
        model.putBinding(span, model.importAliases().get(imported));
        return;
      }
    }
    model.putBinding(span, model.declarationSymbols().get(declaration));
  }

  SymbolId importedAlias(String localName) {
    if (resolution.currentProgram == null) return null;
    for (Syntax.ImportDecl imported : resolution.currentProgram.imports()) {
      if (imported.alias().isPresent() && imported.localName().equals(localName)) {
        return model.importAliases().get(imported);
      }
    }
    return null;
  }

  SemanticType sourceType(String name, List<SemanticType> arguments) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl == null) aggregateDecl = resolveImportedAggregateByDeclaredName(name);
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    Object declaration =
        interfaceDecl != null ? interfaceDecl : aggregateDecl != null ? aggregateDecl : enumDecl;
    Syntax.Program owner =
        declaration == null ? resolution.currentProgram : declarations.owner(declaration);
    String declaredName =
        interfaceDecl != null
            ? interfaceDecl.name()
            : aggregateDecl != null
                ? aggregateDecl.name()
                : enumDecl != null ? enumDecl.name() : name;
    String identity = qualifiedName(owner == null ? "" : owner.packageName(), declaredName);
    if (interfaceDecl != null && interfaceDecl.visibility() == Syntax.Visibility.PRIVATE
        || aggregateDecl != null && aggregateDecl.visibility() == Syntax.Visibility.PRIVATE
        || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE) {
      identity = fileLocalIdentity(identity, owner);
    }
    ValueCategory category =
        interfaceDecl != null
            ? ValueCategory.POLYMORPHIC
            : aggregateDecl != null
                ? aggregateDecl.kind() != Syntax.AggregateKind.VALUE
                    ? ValueCategory.IDENTITY
                    : ValueCategory.VALUE
                : ValueCategory.VALUE;
    return SemanticType.declared(identity, declaredName, arguments, category);
  }

  int declaredTypeArity(String name) {
    int builtinArity = builtins.typeArity(name);
    if (builtinArity >= 0) return builtinArity;
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null) return interfaceDecl.typeParameters().size();
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl != null) return aggregateDecl.typeParameters().size();
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    if (enumDecl != null) return enumDecl.typeParameters().size();
    return resolveAnnotation(name) == null ? -1 : 0;
  }

  private DeclaredType declaredType(String name) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null) {
      return new DeclaredType(
          interfaceDecl, interfaceDecl.typeParameters(), interfaceTypeParameters(interfaceDecl));
    }
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl != null) {
      return new DeclaredType(
          aggregateDecl, aggregateDecl.typeParameters(), aggregateTypeParameters(aggregateDecl));
    }
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    if (enumDecl != null) {
      return new DeclaredType(enumDecl, enumDecl.typeParameters(), enumTypeParameters(enumDecl));
    }
    return null;
  }

  private List<Syntax.TypeParameter> declaredTypeParameters(String name) {
    DeclaredType declaration = declaredType(name);
    return declaration == null ? null : declaration.parameters();
  }

  private List<SemanticType> completeTypeArguments(
      List<Syntax.TypeParameter> parameters,
      Map<String, SemanticType> declaredTypes,
      Object declaration,
      List<SemanticType> provided) {
    if (provided.size() >= parameters.size()) return List.copyOf(provided);
    return TypeArguments.complete(
            parameters,
            provided,
            parameter -> declaredTypes.get(parameter.name()).identity(),
            parameter ->
                parameter
                    .defaultType()
                    .map(type -> resolveDeclarationType(type, declaration, declaredTypes))
                    .or(() -> Optional.of(SemanticType.DYNAMIC)))
        .orElseThrow();
  }

  static boolean acceptsTypeArgumentCount(List<Syntax.TypeParameter> parameters, int count) {
    int required = requiredTypeArgumentCount(parameters);
    return count >= required && count <= parameters.size();
  }

  private static int requiredTypeArgumentCount(List<Syntax.TypeParameter> parameters) {
    return TypeArguments.required(parameters, parameter -> parameter.defaultType().isPresent());
  }

  static String typeArgumentCountMessage(
      String kind, String name, List<Syntax.TypeParameter> parameters, int arity, int actual) {
    int required = parameters == null ? arity : requiredTypeArgumentCount(parameters);
    return typeArgumentCountMessage(kind, name, required, arity, actual);
  }

  static String typeArgumentCountMessage(
      String kind, String name, int required, int arity, int actual) {
    String expectation = required == arity ? Integer.toString(arity) : required + " to " + arity;
    return kind + " '" + name + "' requires " + expectation + " type argument(s), found " + actual;
  }

  private record DeclaredType(
      Object declaration, List<Syntax.TypeParameter> parameters, Map<String, SemanticType> types) {}

  Map<String, SemanticType> typeParameters(
      Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    if (owner != null) result.putAll(aggregateTypeParameters(owner));
    result.putAll(functionTypeParameters(function));
    return Map.copyOf(result);
  }

  Map<String, SemanticType> aggregateTypeParameters(Syntax.AggregateDecl aggregateDecl) {
    return declarationTypeParameters(
        declarationId(aggregateDecl, SymbolKind.TYPE, aggregateDecl.name(), null),
        aggregateDecl.typeParameters());
  }

  static String aggregateKeyword(Syntax.AggregateDecl declaration) {
    return declaration.kind().keyword();
  }

  record AggregateView(Syntax.AggregateDecl declaration, SemanticType type) {}

  AggregateField aggregateField(SemanticType receiver, String name) {
    for (AggregateView view : aggregateViews(receiver)) {
      Syntax.FieldDecl field =
          view.declaration().fields().stream()
              .filter(candidate -> candidate.name().equals(name))
              .findFirst()
              .orElse(null);
      if (field != null) return new AggregateField(field, view);
    }
    return null;
  }

  record AggregateField(Syntax.FieldDecl field, AggregateView view) {}

  Map<String, SemanticType> interfaceTypeParameters(Syntax.InterfaceDecl declaration) {
    return declarationTypeParameters(
        declarationId(declaration, SymbolKind.INTERFACE, declaration.name(), null),
        declaration.typeParameters());
  }

  SemanticType interfaceSelfType(Syntax.InterfaceDecl declaration) {
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    return sourceType(
        declaration.name(),
        declaration.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  Map<String, SemanticType> interfaceMethodTypes(
      Syntax.InterfaceDecl owner, Syntax.InterfaceMethodDecl method) {
    Map<String, SemanticType> result = new LinkedHashMap<>(interfaceTypeParameters(owner));
    SymbolId ownerId = declarationId(owner, SymbolKind.INTERFACE, owner.name(), null);
    result.putAll(
        declarationTypeParameters(
            declarationId(method, SymbolKind.INTERFACE_METHOD, method.name(), ownerId),
            method.typeParameters()));
    return Map.copyOf(result);
  }

  SemanticType aggregateSelfType(Syntax.AggregateDecl aggregateDecl) {
    Map<String, SemanticType> parameters = aggregateTypeParameters(aggregateDecl);
    return sourceType(
        aggregateDecl.name(),
        aggregateDecl.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  Map<String, SemanticType> enumTypeParameters(Syntax.EnumDecl enumDecl) {
    return declarationTypeParameters(
        declarationId(enumDecl, SymbolKind.TYPE, enumDecl.name(), null), enumDecl.typeParameters());
  }

  SemanticType enumSelfType(Syntax.EnumDecl enumDecl) {
    Map<String, SemanticType> parameters = enumTypeParameters(enumDecl);
    return sourceType(
        enumDecl.name(),
        enumDecl.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  Map<String, SemanticType> functionTypeParameters(Syntax.FunctionDecl function) {
    Syntax.AggregateDecl owner = ownerOf(function);
    SymbolId ownerId =
        owner == null ? null : declarationId(owner, SymbolKind.TYPE, owner.name(), null);
    return declarationTypeParameters(
        declarationId(
            function,
            owner == null ? SymbolKind.FUNCTION : SymbolKind.METHOD,
            function.name(),
            ownerId),
        function.typeParameters());
  }

  private SymbolId declarationId(Object declaration, SymbolKind kind, String name, SymbolId owner) {
    return owner == null
        ? SymbolId.authored(
            DeclarationIdentity.topLevel(
                    declarations.ownerOr(declaration, resolution.currentProgram), declaration)
                .value())
        : SymbolId.authored(DeclarationIdentity.member(owner, kind, declaration, name));
  }

  private Map<String, SemanticType> declarationTypeParameters(
      SymbolId owner, List<Syntax.TypeParameter> parameters) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      result.putIfAbsent(
          parameter.name(),
          SemanticType.parameter(
              SymbolId.authored(DeclarationIdentity.typeParameter(owner, index)).value(),
              parameter.name()));
    }
    return Map.copyOf(result);
  }

  void validateTypeParameterNames(List<Syntax.TypeParameter> parameters) {
    Set<String> names = new HashSet<>();
    for (Syntax.TypeParameter parameter : parameters) {
      if (!names.add(parameter.name())) {
        diagnostics.error(
            DUPLICATE_NAME,
            "type parameter '" + parameter.name() + "' is already declared",
            parameter.nameSpan());
      }
    }
  }

  Map<String, SemanticType> aggregateSubstitutions(
      Syntax.AggregateDecl aggregateDecl, SemanticType instance) {
    Map<String, SemanticType> parameters = aggregateTypeParameters(aggregateDecl);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(aggregateDecl.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = parameters.get(aggregateDecl.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return result;
  }

  Map<String, SemanticType> enumSubstitutions(Syntax.EnumDecl enumDecl, SemanticType instance) {
    Map<String, SemanticType> parameters = enumTypeParameters(enumDecl);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(enumDecl.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = parameters.get(enumDecl.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return result;
  }

  Syntax.FunctionDecl resolveFunction(String name) {
    return declarations.resolveFunction(resolution.currentProgram, name);
  }

  List<Syntax.FunctionDecl> resolveFunctions(String name) {
    return declarations.resolveFunctions(resolution.currentProgram, name);
  }

  Map<SymbolId, List<SymbolId>> callableGroups() {
    Map<SymbolId, List<SymbolId>> result = new LinkedHashMap<>();
    for (List<Syntax.FunctionDecl> group : declarations.functionGroups()) {
      List<SymbolId> ids = group.stream().map(model.declarationSymbols()::get).toList();
      ids.forEach(id -> result.put(id, ids));
    }
    return Map.copyOf(result);
  }

  Map<String, List<SemanticType>> interfaceParentTypes() {
    Map<String, List<SemanticType>> result = new LinkedHashMap<>();
    for (Syntax.InterfaceDecl declaration : declarations.interfaces()) {
      Symbol symbol = model.symbols().get(model.declarationSymbols().get(declaration));
      SemanticType instance =
          symbol
              .specialize(symbol.typeParameters().stream().map(TypeParameterInfo::type).toList())
              .orElseThrow()
              .type();
      result.put(instance.identity(), directParents(instance));
    }
    for (Symbol symbol : model.symbols().values()) {
      if (symbol.kind() != SymbolKind.TYPE || resolveAggregate(symbol.type()) == null) continue;
      SemanticType instance =
          symbol
              .specialize(symbol.typeParameters().stream().map(TypeParameterInfo::type).toList())
              .orElseThrow()
              .type();
      result.put(
          instance.identity(),
          directParents(instance).stream()
              .filter(parent -> resolveInterface(parent) != null)
              .toList());
    }
    return Map.copyOf(result);
  }

  Syntax.AggregateDecl resolveAggregate(String name) {
    return declarations.resolveAggregate(resolution.currentProgram, name);
  }

  Syntax.AggregateDecl resolveImportedAggregateByDeclaredName(String name) {
    return declarations.importedAggregateByDeclaredName(resolution.currentProgram, name);
  }

  Syntax.AggregateDecl resolveAggregate(SemanticType type) {
    return declarations.resolveAggregate(type);
  }

  Syntax.AggregateDecl ownerOf(Syntax.FunctionDecl method) {
    return declarations.ownerOf(method);
  }

  Syntax.EnumDecl resolveEnum(String name) {
    return declarations.resolveEnum(resolution.currentProgram, name);
  }

  Syntax.AggregateDecl resolveAnnotation(String name) {
    return declarations.resolveAnnotation(resolution.currentProgram, name);
  }

  Syntax.AggregateDecl resolveAnnotation(SemanticType type) {
    return declarations.resolveAnnotation(type);
  }

  Syntax.InterfaceDecl resolveInterface(String name) {
    return declarations.resolveInterface(resolution.currentProgram, name);
  }

  Syntax.InterfaceDecl resolveInterface(SemanticType type) {
    return declarations.resolveInterface(type);
  }

  Syntax.EnumDecl resolveEnum(SemanticType type) {
    return declarations.resolveEnum(type);
  }

  boolean canImport(Syntax.Program importer, Object declaration) {
    return declarations.canImport(importer, declaration);
  }

  static String callableSignature(Syntax.FunctionDecl function) {
    return DeclarationIdentity.functionSignature(function);
  }

  static String constructorSignature(Syntax.ConstructorDecl constructor) {
    return DeclarationIdentity.callableSignature("", List.of(), constructor.parameters());
  }

  static String fileLocalIdentity(String qualified, Syntax.Program program) {
    return qualified + "@" + program.span().source().id().uri();
  }

  static String interfaceMethodSignature(Syntax.InterfaceMethodDecl method) {
    return DeclarationIdentity.callableSignature(
        method.name(), method.typeParameters(), method.parameters());
  }

  static String qualifiedName(String packageName, String name) {
    return packageName.isEmpty() ? name : packageName + "." + name;
  }

  final SemanticType functionReturnType(
      Syntax.FunctionDecl declaration, Map<String, SemanticType> typeParameters) {
    return declaration
        .returnType()
        .map(type -> resolveDeclarationType(type, declaration, typeParameters))
        .orElseGet(
            () -> {
              Syntax.AggregateDecl owner = ownerOf(declaration);
              return owner == null ? SemanticType.VOID : aggregateSelfType(owner);
            });
  }

  List<InterfaceRequirement> interfaceRequirements(SemanticType receiver) {
    SemanticType interfaceType = receiver;
    if (receiver.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      interfaceType = resolution.typeParameterBounds.get(receiver.identity());
    }
    if (interfaceType == null) return List.of();
    Syntax.InterfaceDecl root = resolveInterface(interfaceType);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    if (root != null) {
      collectConformances(root, interfaceType, conformances, resolution.currentProgram.span());
    } else {
      for (Syntax.InterfaceDecl declaration : declarations.interfaces()) {
        String identity =
            model.symbols().get(model.declarationSymbols().get(declaration)).type().identity();
        conformanceTo(interfaceType, identity)
            .ifPresent(value -> conformances.putIfAbsent(value.identity(), value));
      }
    }
    if (conformances.isEmpty()) return List.of();
    Map<String, InterfaceRequirement> result = new LinkedHashMap<>();
    for (SemanticType conformance : conformances.values()) {
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration == null) continue;
      directRequirements(declaration, conformance)
          .forEach(requirement -> result.putIfAbsent(requirement.key(), requirement));
    }
    return mostSpecificRequirements(List.copyOf(result.values()));
  }

  final List<InterfaceRequirement> mostSpecificRequirements(
      List<InterfaceRequirement> requirements) {
    return requirements.stream()
        .filter(
            requirement ->
                requirements.stream()
                    .noneMatch(
                        candidate ->
                            candidate != requirement
                                && requirementShape(candidate).equals(requirementShape(requirement))
                                && isAssignable(requirement.receiver(), candidate.receiver())
                                && !isAssignable(candidate.receiver(), requirement.receiver())))
        .toList();
  }

  final String requirementShape(InterfaceRequirement requirement) {
    Symbol symbol = model.symbols().get(model.declarationSymbols().get(requirement.method()));
    Map<String, String> typeParameters = new LinkedHashMap<>();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      typeParameters.put(symbol.typeParameters().get(index).type().identity(), "$" + index);
    }
    return requirement.method().name()
        + "("
        + requirement.parameters().stream()
            .map(
                parameter ->
                    parameter.name() + ":" + semanticTypeShape(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")->"
        + semanticTypeShape(requirement.result(), typeParameters);
  }

  private static String semanticTypeShape(SemanticType type, Map<String, String> typeParameters) {
    String identity = typeParameters.getOrDefault(type.identity(), type.identity());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> semanticTypeShape(argument, typeParameters))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"));
    return identity + arguments + (type.isNullable() ? "?" : "");
  }

  Optional<ResolvedIteration> resolveInterfaceIteration(SemanticType iterableType) {
    if (builtins.resolveIterable(iterableType).isPresent()) return Optional.empty();
    SemanticType iterableInterface = conformanceTo(iterableType, "std.core.Iterable").orElse(null);
    if (iterableInterface == null || iterableInterface.arguments().size() != 1) {
      return Optional.empty();
    }
    InterfaceRequirement iterator =
        interfaceRequirements(iterableInterface).stream()
            .filter(requirement -> requirement.method().name().equals("iterator"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    if (iterator == null) return Optional.empty();
    SemanticType iteratorInterface = iterator.result();
    InterfaceRequirement hasNext =
        interfaceRequirements(iteratorInterface).stream()
            .filter(requirement -> requirement.method().name().equals("hasNext"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    InterfaceRequirement next =
        interfaceRequirements(iteratorInterface).stream()
            .filter(requirement -> requirement.method().name().equals("next"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    if (hasNext == null || next == null || !hasNext.result().equals(SemanticType.BOOLEAN)) {
      return Optional.empty();
    }
    return Optional.of(
        new ResolvedIteration(
            iterableInterface.arguments().getFirst(),
            new ResolvedIteration.Strategy.Interface(
                iterableInterface,
                model.declarationSymbols().get(iterator.method()),
                iteratorInterface,
                model.declarationSymbols().get(hasNext.method()),
                model.declarationSymbols().get(next.method()))));
  }

  Optional<SemanticType> conformanceTo(SemanticType concrete, String interfaceIdentity) {
    return nominalViews(concrete).stream()
        .filter(type -> type.identity().equals(interfaceIdentity))
        .findFirst();
  }

  Syntax.InterfaceDecl interfaceByIdentity(String identity) {
    for (Syntax.InterfaceDecl declaration : declarations.interfaces()) {
      Syntax.Program owner = declarations.owner(declaration);
      String candidate = qualifiedName(owner.packageName(), declaration.name());
      if (declaration.visibility() == Syntax.Visibility.PRIVATE) {
        candidate = fileLocalIdentity(candidate, owner);
      }
      if (candidate.equals(identity)) return declaration;
    }
    return null;
  }

  List<InterfaceRequirement> directRequirements(
      Syntax.InterfaceDecl declaration, SemanticType instance) {
    Map<String, SemanticType> substitutions = interfaceSubstitutions(declaration, instance);
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    return declaration.methods().stream()
        .map(
            method -> {
              Map<String, SemanticType> methodTypes = interfaceMethodTypes(declaration, method);
              List<ParameterInfo> methodParameters =
                  method.parameters().stream()
                      .map(
                          parameter ->
                              new ParameterInfo(
                                  parameter.name(),
                                  resolveDeclarationType(parameter.type(), method, methodTypes)
                                      .substitute(substitutions),
                                  parameter.defaultValue().isPresent()))
                      .toList();
              SemanticType result =
                  resolveDeclarationType(method.returnType(), method, methodTypes)
                      .substitute(substitutions);
              String signature =
                  methodParameters.stream()
                          .map(value -> value.name() + ":" + value.type().identity())
                          .collect(java.util.stream.Collectors.joining(","))
                      + "->"
                      + result.identity();
              return new InterfaceRequirement(
                  declaration,
                  instance,
                  method,
                  methodParameters,
                  result,
                  model.declarationSymbols().get(method).value(),
                  signature);
            })
        .toList();
  }
}
