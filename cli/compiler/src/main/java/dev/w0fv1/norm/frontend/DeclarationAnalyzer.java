package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.abi.ExceptionAbi;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceRequirement;
import dev.w0fv1.norm.frontend.TypeResolver.AggregateView;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DeclarationAnalyzer {
  private final dev.w0fv1.norm.builtin.BuiltinSymbols builtins;
  private final DeclarationCatalog catalog;
  private final DiagnosticBag diagnostics;
  private final SemanticModelBuilder model;
  private final List<Syntax.Program> programs;
  private final TypeResolutionState resolution;
  private final TypeResolver typeResolver;
  private final DeclarationPolicyResolver declarationPolicies;

  DeclarationAnalyzer(
      dev.w0fv1.norm.builtin.BuiltinSymbols builtins,
      DeclarationCatalog catalog,
      DiagnosticBag diagnostics,
      SemanticModelBuilder model,
      List<Syntax.Program> programs,
      TypeResolutionState resolution,
      TypeResolver typeResolver,
      DeclarationPolicyResolver declarationPolicies) {
    this.builtins = builtins;
    this.catalog = catalog;
    this.diagnostics = diagnostics;
    this.model = model;
    this.programs = programs;
    this.resolution = resolution;
    this.typeResolver = typeResolver;
    this.declarationPolicies = declarationPolicies;
  }

  void collectDeclarations() {
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.InterfaceDecl declaration : program.interfaces()) {
          if (catalog.duplicate(declaration) || builtins.isType(declaration.name())) {
            diagnostics.error(
                DUPLICATE_NAME,
                "type '" + declaration.name() + "' is already declared",
                declaration.span());
          }
          Symbol type =
              registerDeclaration(
                  declaration,
                  declaration.name(),
                  SymbolKind.INTERFACE,
                  typeResolver.sourceType(declaration.name(), List.of()),
                  declaration.nameSpan(),
                  null,
                  symbolTypeParameters(
                      declaration.typeParameters(),
                      typeResolver.interfaceTypeParameters(declaration)),
                  List.of());
          model.putTypeSymbol(type.type().identity(), type.id());
          registerTypeParameters(
              declaration.typeParameters(),
              type.id(),
              typeResolver.interfaceTypeParameters(declaration));
        }
      }
    }
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.EnumDecl enumDecl : program.enums()) {
          if (catalog.duplicate(enumDecl)
              || typeResolver.resolveInterface(enumDecl.name()) != null
              || builtins.isType(enumDecl.name())) {
            diagnostics.error(
                DUPLICATE_NAME,
                "type '" + enumDecl.name() + "' is already declared",
                enumDecl.span());
          }
          Symbol type =
              registerDeclaration(
                  enumDecl,
                  enumDecl.name(),
                  SymbolKind.TYPE,
                  typeResolver.sourceType(enumDecl.name(), List.of()),
                  enumDecl.nameSpan(),
                  null,
                  symbolTypeParameters(
                      enumDecl.typeParameters(), typeResolver.enumTypeParameters(enumDecl)),
                  List.of());
          model.putTypeSymbol(type.type().identity(), type.id());
          registerTypeParameters(
              enumDecl.typeParameters(), type.id(), typeResolver.enumTypeParameters(enumDecl));
        }
      }
    }
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
          if (catalog.duplicate(aggregateDecl)
              || typeResolver.resolveEnum(aggregateDecl.name()) != null
              || typeResolver.resolveInterface(aggregateDecl.name()) != null
              || builtins.isType(aggregateDecl.name())) {
            diagnostics.error(
                DUPLICATE_NAME,
                "type '" + aggregateDecl.name() + "' is already declared",
                aggregateDecl.span());
          }
          Symbol type =
              registerDeclaration(
                  aggregateDecl,
                  aggregateDecl.name(),
                  SymbolKind.TYPE,
                  typeResolver.sourceType(aggregateDecl.name(), List.of()),
                  aggregateDecl.nameSpan(),
                  null,
                  symbolTypeParameters(
                      aggregateDecl.typeParameters(),
                      typeResolver.aggregateTypeParameters(aggregateDecl)),
                  List.of());
          model.putTypeSymbol(type.type().identity(), type.id());
          registerTypeParameters(
              aggregateDecl.typeParameters(),
              type.id(),
              typeResolver.aggregateTypeParameters(aggregateDecl));
        }
      }
    }
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.InterfaceDecl declaration : program.interfaces()) {
          Symbol type = model.symbols().get(model.declarationSymbols().get(declaration));
          Set<String> signatures = new HashSet<>();
          for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
            String signature = TypeResolver.interfaceMethodSignature(method);
            if (!signatures.add(signature)) {
              diagnostics.error(
                  DUPLICATE_NAME,
                  "interface method '" + method.name() + "' is already declared",
                  method.span());
            }
            Map<String, SemanticType> parameters =
                typeResolver.interfaceTypeParameters(declaration);
            parameters = typeResolver.interfaceMethodTypes(declaration, method);
            Symbol symbol =
                registerDeclaration(
                    method,
                    method.name(),
                    SymbolKind.INTERFACE_METHOD,
                    typeResolver.resolveDeclarationType(method.returnType(), method, parameters),
                    method.nameSpan(),
                    type.id(),
                    symbolTypeParameters(method.typeParameters(), parameters),
                    parameters(method.parameters(), Map.of(), parameters));
            registerTypeParameters(method.typeParameters(), symbol.id(), parameters);
            for (Syntax.Parameter parameter : method.parameters()) {
              registerDeclaration(
                  parameter,
                  parameter.name(),
                  SymbolKind.PARAMETER,
                  typeResolver.resolveDeclarationType(parameter.type(), method, parameters),
                  parameter.nameSpan(),
                  symbol.id(),
                  List.of(),
                  List.of());
            }
            model.addMember(type.id(), symbol.id());
          }
        }
        for (Syntax.EnumDecl enumDecl : program.enums()) {
          Set<String> variants = new HashSet<>();
          for (Syntax.EnumVariant variant : enumDecl.variants()) {
            if (!variants.add(variant.name())) {
              diagnostics.error(
                  DUPLICATE_NAME,
                  "enum variant '" + variant.name() + "' is already declared",
                  variant.nameSpan());
            }
          }
          if (enumDecl.variants().isEmpty()) {
            diagnostics.error(
                TYPE_MISMATCH, "enum must declare at least one variant", enumDecl.span());
          }
          Symbol type = model.symbols().get(model.declarationSymbols().get(enumDecl));
          for (Syntax.EnumVariant variant : enumDecl.variants()) {
            Symbol value =
                registerDeclaration(
                    variant,
                    variant.name(),
                    SymbolKind.ENUM_VARIANT,
                    typeResolver.enumSelfType(enumDecl),
                    variant.nameSpan(),
                    type.id(),
                    symbolTypeParameters(
                        enumDecl.typeParameters(), typeResolver.enumTypeParameters(enumDecl)),
                    parameters(
                        variant.parameters(), Map.of(), typeResolver.enumTypeParameters(enumDecl)));
            model.addMember(type.id(), value.id());
          }
        }
        for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
          Symbol type = model.symbols().get(model.declarationSymbols().get(aggregateDecl));
          for (Syntax.FieldDecl field : aggregateDecl.fields()) {
            Symbol symbol =
                registerDeclaration(
                    field,
                    field.name(),
                    SymbolKind.FIELD,
                    typeResolver.resolveDeclarationType(
                        field.type(), field, typeResolver.aggregateTypeParameters(aggregateDecl)),
                    field.nameSpan(),
                    type.id(),
                    List.of(),
                    List.of());
            if (field.visibility() == Syntax.Visibility.PUBLIC) {
              model.addMember(type.id(), symbol.id());
            }
          }
          List<ParameterInfo> constructionParameters =
              aggregateDecl.constructors().isEmpty()
                  ? fieldParameters(
                      aggregateDecl.fields(),
                      Map.of(),
                      typeResolver.aggregateTypeParameters(aggregateDecl))
                  : List.of();
          type =
              new Symbol(
                  type.id(),
                  type.name(),
                  type.kind(),
                  type.type(),
                  type.declaration(),
                  type.owner(),
                  type.typeParameters(),
                  constructionParameters,
                  type.documentation());
          model.putSymbol(type.id(), type);
          for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
            registerDeclaration(
                constructor,
                constructor.name(),
                SymbolKind.CONSTRUCTOR,
                SemanticType.VOID,
                constructor.nameSpan(),
                type.id(),
                List.of(),
                parameters(
                    constructor.parameters(),
                    Map.of(),
                    typeResolver.aggregateTypeParameters(aggregateDecl)));
          }
          if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE) {
            SymbolId copyId = SymbolId.authored(DeclarationIdentity.synthetic(type.id(), "copy"));
            Symbol copy =
                new Symbol(
                    copyId,
                    "copy",
                    SymbolKind.METHOD,
                    typeResolver.aggregateSelfType(aggregateDecl),
                    Optional.empty(),
                    Optional.of(type.id()),
                    List.of(),
                    List.of(),
                    "Creates a new top-level object identity.");
            model.putSymbol(copyId, copy);
            model.addMember(type.id(), copyId);
            model.putCopyMethod(type.type().identity(), copyId);
          }
          for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
            typeResolver.validateTypeParameterNames(method.typeParameters());
            Symbol symbol =
                registerDeclaration(
                    method,
                    method.name(),
                    SymbolKind.METHOD,
                    typeResolver.functionReturnType(
                        method, typeResolver.typeParameters(method, aggregateDecl)),
                    method.nameSpan(),
                    type.id(),
                    symbolTypeParameters(
                        method.typeParameters(),
                        typeResolver.typeParameters(method, aggregateDecl)),
                    parametersOf(method, Map.of()));
            registerTypeParameters(
                method.typeParameters(), symbol.id(), typeResolver.functionTypeParameters(method));
            if (method.visibility() == Syntax.Visibility.PUBLIC) {
              model.addMember(type.id(), symbol.id());
            }
          }
        }
      }
    }
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.FunctionDecl function : program.functions()) {
          typeResolver.validateTypeParameterNames(function.typeParameters());
          if (catalog.duplicate(function)) {
            diagnostics.error(
                DUPLICATE_NAME,
                "function overload '" + function.name() + "' is already declared",
                function.span());
          }
          Symbol symbol =
              registerDeclaration(
                  function,
                  function.name(),
                  function.kind() == Syntax.FunctionKind.EXTENSION
                      ? SymbolKind.EXTENSION
                      : SymbolKind.FUNCTION,
                  typeResolver.functionReturnType(
                      function, typeResolver.functionTypeParameters(function)),
                  function.nameSpan(),
                  null,
                  symbolTypeParameters(
                      function.typeParameters(), typeResolver.functionTypeParameters(function)),
                  parametersOf(function, Map.of()));
          registerTypeParameters(
              function.typeParameters(),
              symbol.id(),
              typeResolver.functionTypeParameters(function));
        }
      }
    }
  }

  void validateClassHierarchy() {
    Set<Syntax.AggregateDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.AggregateDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.AggregateDecl declaration : program.aggregates()) {
          try (var aggregateScope =
              resolution.enterParameters(
                  typeResolver.aggregateTypeParameters(declaration),
                  typeResolver.typeParameterSymbols(declaration.typeParameters()))) {
            if (typeResolver
                .aggregateSelfType(declaration)
                .identity()
                .equals(SemanticType.EXCEPTION.identity())) {
              boolean validField =
                  declaration.fields().size() == 1
                      && declaration.fields().getFirst().visibility() == Syntax.Visibility.PUBLIC
                      && declaration
                          .fields()
                          .getFirst()
                          .name()
                          .equals(ExceptionAbi.MESSAGE_FIELD_NAME)
                      && typeResolver
                          .resolveType(
                              declaration.fields().getFirst().type(), resolution.parameters())
                          .equals(SemanticType.STRING);
              if (declaration.kind() != Syntax.AggregateKind.CLASS
                  || declaration.visibility() != Syntax.Visibility.PUBLIC
                  || !declaration.typeParameters().isEmpty()
                  || declaration.extendedClass().isPresent()
                  || !validField) {
                diagnostics.error(
                    TYPE_MISMATCH,
                    "Exception root ABI requires public class Exception with one public String message"
                        + " field",
                    declaration.nameSpan());
              }
            }
            declaration
                .extendedClass()
                .ifPresent(
                    parentRef -> {
                      typeResolver.validateType(parentRef, false);
                      SemanticType parent =
                          typeResolver.resolveType(parentRef, resolution.parameters());
                      Syntax.AggregateDecl parentDeclaration =
                          typeResolver.resolveAggregate(parent);
                      if (declaration.kind() != Syntax.AggregateKind.CLASS
                          || parentDeclaration == null
                          || parentDeclaration.kind() != Syntax.AggregateKind.CLASS) {
                        diagnostics.error(
                            TYPE_MISMATCH, "class inheritance requires a class", parentRef.span());
                        return;
                      }
                      model.putAggregateParent(
                          typeResolver.aggregateSelfType(declaration).identity(), parent);
                      if (typeResolver.isAssignable(
                              SemanticType.EXCEPTION, typeResolver.aggregateSelfType(declaration))
                          && !declaration.typeParameters().isEmpty()) {
                        diagnostics.error(
                            TYPE_MISMATCH,
                            "Exception classes cannot declare type parameters",
                            declaration.nameSpan());
                      }
                      validateInheritedMembers(declaration);
                      validateOverrides(declaration);
                    });
          }
        }
      }
    }
    for (Syntax.Program program : programs) {
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        validateClassCycle(declaration, visiting, visited);
      }
    }
  }

  private void validateClassCycle(
      Syntax.AggregateDecl declaration,
      Set<Syntax.AggregateDecl> visiting,
      Set<Syntax.AggregateDecl> visited) {
    if (visited.contains(declaration)) return;
    if (!visiting.add(declaration)) {
      diagnostics.error(
          TYPE_MISMATCH, "class inheritance contains a cycle", declaration.nameSpan());
      return;
    }
    try (var selectedProgram = resolution.enterProgram(catalog.owner(declaration))) {
      typeResolver
          .directParentType(declaration, typeResolver.aggregateSelfType(declaration))
          .map(typeResolver::resolveAggregate)
          .ifPresent(parent -> validateClassCycle(parent, visiting, visited));

      visiting.remove(declaration);
      visited.add(declaration);
    }
  }

  private void validateInheritedMembers(Syntax.AggregateDecl declaration) {
    Set<String> inherited = new HashSet<>();
    Set<String> properties = new HashSet<>();
    List<AggregateView> views =
        typeResolver.aggregateViews(typeResolver.aggregateSelfType(declaration));
    for (AggregateView view : views.subList(Math.min(1, views.size()), views.size())) {
      view.declaration().fields().forEach(field -> inherited.add(field.name()));
      view.declaration().methods().stream()
          .filter(
              method ->
                  method.kind() == Syntax.FunctionKind.GETTER
                      && method.visibility() == Syntax.Visibility.PUBLIC)
          .forEach(method -> properties.add(method.name()));
    }
    for (Syntax.FieldDecl field : declaration.fields()) {
      if (inherited.contains(field.name()) || properties.contains(field.name())) {
        diagnostics.error(
            DUPLICATE_NAME,
            "field '" + field.name() + "' is already declared by a parent class",
            field.nameSpan());
      }
    }
    for (Syntax.FunctionDecl method : declaration.methods()) {
      if (method.kind() == Syntax.FunctionKind.GETTER && inherited.contains(method.name())) {
        diagnostics.error(
            DUPLICATE_NAME,
            "property '" + method.name() + "' conflicts with an inherited storage field",
            method.nameSpan());
      }
      if (method.kind() != Syntax.FunctionKind.GETTER) continue;
      for (AggregateView view : views.subList(Math.min(1, views.size()), views.size())) {
        var parent =
            view.declaration().methods().stream()
                .filter(
                    candidate ->
                        candidate.kind() == Syntax.FunctionKind.GETTER
                            && candidate.visibility() == Syntax.Visibility.PUBLIC
                            && candidate.name().equals(method.name()))
                .findFirst();
        if (parent.isEmpty()) continue;
        if (method.visibility() != Syntax.Visibility.PUBLIC) {
          diagnostics.error(
              TYPE_MISMATCH, "overriding property must remain public", method.nameSpan());
        }
        boolean writable =
            view.declaration().methods().stream()
                .anyMatch(
                    candidate ->
                        candidate.kind() == Syntax.FunctionKind.SETTER
                            && candidate.visibility() == Syntax.Visibility.PUBLIC
                            && candidate.name().equals(method.name()));
        if (writable) {
          SemanticType expected =
              typeResolver
                  .functionReturnType(
                      parent.orElseThrow(),
                      typeResolver.typeParameters(parent.orElseThrow(), view.declaration()))
                  .substitute(typeResolver.aggregateSubstitutions(view.declaration(), view.type()));
          SemanticType actual =
              typeResolver.functionReturnType(
                  method, typeResolver.typeParameters(method, declaration));
          boolean publicSetter =
              declaration.methods().stream()
                  .anyMatch(
                      candidate ->
                          candidate.kind() == Syntax.FunctionKind.SETTER
                              && candidate.visibility() == Syntax.Visibility.PUBLIC
                              && candidate.name().equals(method.name()));
          if (!expected.equals(actual) || !publicSetter) {
            diagnostics.error(
                TYPE_MISMATCH,
                "overriding writable property must preserve its type and public setter",
                method.nameSpan());
          }
        }
        break;
      }
    }
  }

  private void validateOverrides(Syntax.AggregateDecl declaration) {
    List<AggregateView> views =
        typeResolver.aggregateViews(typeResolver.aggregateSelfType(declaration));
    if (views.size() < 2) return;
    for (Syntax.FunctionDecl method : declaration.methods()) {
      if (method.visibility() != Syntax.Visibility.PUBLIC) continue;
      List<ParentMethod> sameShape = new ArrayList<>();
      for (AggregateView view : views.subList(1, views.size())) {
        for (Syntax.FunctionDecl inherited : view.declaration().methods()) {
          if (inherited.visibility() == Syntax.Visibility.PUBLIC
              && inherited.name().equals(method.name())
              && sameOverrideParameters(method, inherited, view)) {
            sameShape.add(new ParentMethod(inherited, view));
          }
        }
        if (!sameShape.isEmpty()) break;
      }
      if (sameShape.isEmpty()) continue;
      ParentMethod parent = sameShape.getFirst();
      Symbol methodSymbol = model.symbols().get(model.declarationSymbols().get(method));
      Symbol parentSymbol = model.symbols().get(model.declarationSymbols().get(parent.method()));
      Map<String, SemanticType> parentSubstitutions =
          typeResolver.aggregateSubstitutions(parent.view().declaration(), parent.view().type());
      if (!sameGenericShape(methodSymbol, Map.of(), parentSymbol, parentSubstitutions)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "override of '" + method.name() + "' must preserve type parameter bounds",
            method.nameSpan());
        continue;
      }
      SemanticType expected =
          typeResolver
              .functionReturnType(
                  parent.method(),
                  typeResolver.typeParameters(parent.method(), parent.view().declaration()))
              .substitute(parentSubstitutions);
      SemanticType actual =
          typeResolver.functionReturnType(method, typeResolver.typeParameters(method, declaration));
      boolean sameReturnShape =
          canonicalType(expected, canonicalTypeParameters(parentSymbol))
              .equals(canonicalType(actual, canonicalTypeParameters(methodSymbol)));
      if (!sameReturnShape && !typeResolver.isAssignable(expected, actual)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "override of '" + method.name() + "' must return " + expected.displayName(),
            method.nameSpan());
        continue;
      }
      model.putOverride(
          model.declarationSymbols().get(method), model.declarationSymbols().get(parent.method()));
    }
  }

  private boolean sameOverrideParameters(
      Syntax.FunctionDecl method, Syntax.FunctionDecl inherited, AggregateView parent) {
    if (method.typeParameters().size() != inherited.typeParameters().size()
        || method.parameters().size() != inherited.parameters().size()) return false;
    Symbol methodSymbol = model.symbols().get(model.declarationSymbols().get(method));
    Symbol parentSymbol = model.symbols().get(model.declarationSymbols().get(inherited));
    Map<String, String> methodTypes = canonicalTypeParameters(methodSymbol);
    Map<String, String> parentTypes = canonicalTypeParameters(parentSymbol);
    Map<String, SemanticType> substitutions =
        typeResolver.aggregateSubstitutions(parent.declaration(), parent.type());
    for (int index = 0; index < methodSymbol.parameters().size(); index++) {
      ParameterInfo own = methodSymbol.parameters().get(index);
      ParameterInfo base = parentSymbol.parameters().get(index);
      if (!(method.kind() == Syntax.FunctionKind.SETTER
                  && inherited.kind() == Syntax.FunctionKind.SETTER)
              && !own.name().equals(base.name())
          || !canonicalType(own.type(), methodTypes)
              .equals(canonicalType(base.type().substitute(substitutions), parentTypes)))
        return false;
    }
    return true;
  }

  private boolean sameGenericShape(
      Symbol candidate,
      Map<String, SemanticType> candidateSubstitutions,
      Symbol requirement,
      Map<String, SemanticType> requirementSubstitutions) {
    if (candidate.typeParameters().size() != requirement.typeParameters().size()) return false;
    Map<String, String> candidateParameters = canonicalTypeParameters(candidate);
    Map<String, String> requiredParameters = canonicalTypeParameters(requirement);
    for (int index = 0; index < candidate.typeParameters().size(); index++) {
      Optional<SemanticType> candidateBound = candidate.typeParameters().get(index).upperBound();
      Optional<SemanticType> requiredBound = requirement.typeParameters().get(index).upperBound();
      Optional<SemanticType> candidateDefault = candidate.typeParameters().get(index).defaultType();
      Optional<SemanticType> requiredDefault =
          requirement.typeParameters().get(index).defaultType();
      if (candidateBound.isPresent() != requiredBound.isPresent()
          || candidateDefault.isPresent() != requiredDefault.isPresent()) return false;
      if (candidateBound.isPresent()
          && !canonicalType(
                  candidateBound.orElseThrow().substitute(candidateSubstitutions),
                  candidateParameters)
              .equals(
                  canonicalType(
                      requiredBound.orElseThrow().substitute(requirementSubstitutions),
                      requiredParameters))) return false;
      if (candidateDefault.isPresent()
          && !canonicalType(
                  candidateDefault.orElseThrow().substitute(candidateSubstitutions),
                  candidateParameters)
              .equals(
                  canonicalType(
                      requiredDefault.orElseThrow().substitute(requirementSubstitutions),
                      requiredParameters))) return false;
    }
    return true;
  }

  private static Map<String, String> canonicalTypeParameters(Symbol symbol) {
    Map<String, String> result = new LinkedHashMap<>();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      result.put(symbol.typeParameters().get(index).type().identity(), "$" + index);
    }
    return result;
  }

  private record ParentMethod(Syntax.FunctionDecl method, AggregateView view) {}

  void validateFields(Syntax.AggregateDecl aggregateDecl, ExpressionChecker expressionChecker) {
    try (var aggregateScope =
        resolution.enterParameters(
            typeResolver.aggregateTypeParameters(aggregateDecl),
            typeResolver.typeParameterSymbols(aggregateDecl.typeParameters()))) {
      typeResolver.registerBounds(aggregateDecl.typeParameters(), resolution.parameters());
      typeResolver.validateTypeParameterDefaults(
          aggregateDecl.typeParameters(),
          resolution.parameters(),
          aggregateDecl.visibility() == Syntax.Visibility.PUBLIC);
      Set<String> names = new HashSet<>();
      boolean defaultFieldSeen = false;
      for (Syntax.FieldDecl field : aggregateDecl.fields()) {
        typeResolver.validateType(field.type(), false);
        if (declarationPolicies.managedField(field)
            && aggregateDecl.kind() != Syntax.AggregateKind.CLASS) {
          diagnostics.error(TYPE_MISMATCH, "managed fields require a class", field.nameSpan());
        }
        if (field.defaultValue().isPresent()) {
          if (declarationPolicies.constructorInput(field)) defaultFieldSeen = true;
          SemanticType expected = typeResolver.resolveType(field.type(), resolution.parameters());
          typeResolver.requireType(
              expected,
              expressionChecker.typeOf(field.defaultValue().orElseThrow(), expected),
              field.defaultValue().orElseThrow().span());
        } else if (declarationPolicies.constructorInput(field) && defaultFieldSeen) {
          diagnostics.error(
              INVALID_CALL, "required field follows a default field", field.nameSpan());
        }
        if (aggregateDecl.visibility() == Syntax.Visibility.PUBLIC
            && field.visibility() == Syntax.Visibility.PUBLIC) {
          typeResolver.validatePublicType(field.type());
        }
        if (!names.add(field.name())) {
          diagnostics.error(
              DUPLICATE_NAME, "field '" + field.name() + "' is already declared", field.span());
        }
      }
      Set<String> methods = new HashSet<>();
      for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
        if (method.kind() == Syntax.FunctionKind.GETTER && names.contains(method.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "property '" + method.name() + "' conflicts with a storage field",
              method.nameSpan());
        }
        if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE && method.name().equals("copy")) {
          diagnostics.error(
              DUPLICATE_NAME, "method 'copy' is reserved for identity copying", method.nameSpan());
        }
        if (!methods.add(TypeResolver.callableSignature(method))) {
          diagnostics.error(
              DUPLICATE_NAME, "method '" + method.name() + "' is already declared", method.span());
        }
      }
      Set<String> constructors = new HashSet<>();
      for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
        if (!constructors.add(TypeResolver.constructorSignature(constructor))) {
          diagnostics.error(
              DUPLICATE_NAME,
              "constructor '" + aggregateDecl.name() + "' is already declared",
              constructor.span());
        }
      }
    }
  }

  void validateEnum(Syntax.EnumDecl enumDecl, BodyAnalyzer bodies) {
    try (var enumScope =
        resolution.enterParameters(
            typeResolver.enumTypeParameters(enumDecl),
            typeResolver.typeParameterSymbols(enumDecl.typeParameters()))) {
      typeResolver.registerBounds(enumDecl.typeParameters(), resolution.parameters());
      typeResolver.validateTypeParameterDefaults(
          enumDecl.typeParameters(),
          resolution.parameters(),
          enumDecl.visibility() == Syntax.Visibility.PUBLIC);
      for (Syntax.EnumVariant variant : enumDecl.variants()) {
        bodies.analyzeParameterDefaults(variant.parameters());
        Set<String> names = new HashSet<>();
        for (Syntax.Parameter parameter : variant.parameters()) {
          typeResolver.validateType(parameter.type(), false);
          if (!names.add(parameter.name())) {
            diagnostics.error(
                DUPLICATE_NAME,
                "enum data '" + parameter.name() + "' is already declared",
                parameter.nameSpan());
          }
        }
      }
    }
  }

  void validateInterface(Syntax.InterfaceDecl declaration, BodyAnalyzer bodies) {
    try (var interfaceScope =
        resolution.enterParameters(
            typeResolver.interfaceTypeParameters(declaration),
            typeResolver.typeParameterSymbols(declaration.typeParameters()))) {
      typeResolver.registerBounds(declaration.typeParameters(), resolution.parameters());
      typeResolver.validateTypeParameterDefaults(
          declaration.typeParameters(),
          resolution.parameters(),
          declaration.visibility() == Syntax.Visibility.PUBLIC);
      for (Syntax.TypeRef parent : declaration.extendedInterfaces()) {
        typeResolver.validateType(parent, false);
        if (typeResolver.resolveInterface(typeResolver.resolveType(parent, resolution.parameters()))
            == null) {
          diagnostics.error(TYPE_MISMATCH, "interface may extend interfaces only", parent.span());
        }
      }
      for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
        typeResolver.validateTypeParameterNames(method.typeParameters());
        Map<String, SemanticType> methodTypes =
            typeResolver.interfaceMethodTypes(declaration, method);
        Map<String, SymbolId> methodSymbols = new LinkedHashMap<>(resolution.parameterSymbols());
        method
            .typeParameters()
            .forEach(
                parameter ->
                    methodSymbols.put(parameter.name(), model.declarationSymbols().get(parameter)));
        try (var methodScope = resolution.enterParameters(methodTypes, Map.copyOf(methodSymbols))) {
          typeResolver.registerBounds(method.typeParameters(), methodTypes);
          typeResolver.validateTypeParameterDefaults(
              method.typeParameters(),
              methodTypes,
              declaration.visibility() == Syntax.Visibility.PUBLIC);
          typeResolver.validateType(method.returnType(), true);
          method
              .parameters()
              .forEach(parameter -> typeResolver.validateReferenceCapableType(parameter.type()));
          if (method.body().isPresent()
              || method.parameters().stream()
                  .anyMatch(parameter -> parameter.defaultValue().isPresent())) {
            bodies.analyzeInterfaceMethod(declaration, method, methodTypes, methodSymbols);
          }
        }
      }
    }
  }

  void validateInterfaceGraphAndConformances() {
    Set<Syntax.InterfaceDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.InterfaceDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.InterfaceDecl declaration : catalog.interfaces()) {
      validateInterfaceCycle(declaration, visiting, visited);
    }
    for (Syntax.Program program : programs) {
      try (var programScope = resolution.enterProgram(program)) {

        for (Syntax.AggregateDecl declaration : program.aggregates()) {
          validateAggregateConformance(declaration);
        }
      }
    }
  }

  private void validateInterfaceCycle(
      Syntax.InterfaceDecl declaration,
      Set<Syntax.InterfaceDecl> visiting,
      Set<Syntax.InterfaceDecl> visited) {
    if (visited.contains(declaration)) return;
    if (!visiting.add(declaration)) {
      diagnostics.error(
          TYPE_MISMATCH, "interface inheritance contains a cycle", declaration.nameSpan());
      return;
    }
    try (var selectedProgram = resolution.enterProgram(catalog.owner(declaration))) {
      Map<String, SemanticType> parameters = typeResolver.interfaceTypeParameters(declaration);
      for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
        Syntax.InterfaceDecl parent =
            typeResolver.resolveInterface(typeResolver.resolveType(parentRef, parameters));
        if (parent != null) validateInterfaceCycle(parent, visiting, visited);
      }

      visiting.remove(declaration);
      visited.add(declaration);
    }
  }

  private void validateAggregateConformance(Syntax.AggregateDecl declaration) {
    try (var aggregateScope =
        resolution.enterParameters(
            typeResolver.aggregateTypeParameters(declaration),
            typeResolver.typeParameterSymbols(declaration.typeParameters()))) {
      typeResolver.registerBounds(declaration.typeParameters(), resolution.parameters());
      Map<String, SemanticType> conformances = new LinkedHashMap<>();
      for (Syntax.TypeRef interfaceRef : declaration.implementedInterfaces()) {
        typeResolver.validateType(interfaceRef, false);
        SemanticType interfaceType =
            typeResolver.resolveType(interfaceRef, resolution.parameters());
        Syntax.InterfaceDecl interfaceDecl = typeResolver.resolveInterface(interfaceType);
        if (interfaceDecl == null) {
          diagnostics.error(
              TYPE_MISMATCH,
              TypeResolver.aggregateKeyword(declaration) + " may implement interfaces only",
              interfaceRef.span());
          continue;
        }
        typeResolver.collectConformances(
            interfaceDecl, interfaceType, conformances, interfaceRef.span());
      }
      Map<String, InterfaceRequirement> requirements = new LinkedHashMap<>();
      for (SemanticType conformance : conformances.values()) {
        Syntax.InterfaceDecl interfaceDecl = typeResolver.resolveInterface(conformance);
        if (interfaceDecl == null) continue;
        for (InterfaceRequirement requirement :
            typeResolver.directRequirements(interfaceDecl, conformance)) {
          InterfaceRequirement existing = requirements.putIfAbsent(requirement.key(), requirement);
          if (existing != null && !existing.signature().equals(requirement.signature())) {
            diagnostics.error(
                TYPE_MISMATCH,
                "inherited interface requirements conflict for method '"
                    + requirement.method().name()
                    + "'",
                declaration.nameSpan());
          }
        }
      }
      Map<String, List<InterfaceRequirement>> requirementGroups =
          requirements.values().stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(
                      typeResolver::requirementShape,
                      LinkedHashMap::new,
                      java.util.stream.Collectors.toList()));
      for (List<InterfaceRequirement> group : requirementGroups.values()) {
        Syntax.FunctionDecl witness =
            declaration.methods().stream()
                .filter(method -> method.name().equals(group.getFirst().method().name()))
                .filter(method -> method.visibility() == Syntax.Visibility.PUBLIC)
                .filter(
                    method ->
                        group.stream().allMatch(requirement -> witnessMatches(method, requirement)))
                .findFirst()
                .orElse(null);
        SymbolId implementation = witness == null ? null : model.declarationSymbols().get(witness);
        if (implementation == null) {
          List<InterfaceRequirement> active = typeResolver.mostSpecificRequirements(group);
          List<InterfaceRequirement> defaults =
              active.stream().filter(value -> value.method().body().isPresent()).toList();
          if (defaults.size() == 1) {
            implementation = defaultMethodId(defaults.getFirst().method());
          } else if (defaults.size() > 1) {
            diagnostics.error(
                TYPE_MISMATCH,
                "inherited interface default methods conflict for method '"
                    + group.getFirst().method().name()
                    + "'",
                declaration.nameSpan());
          } else {
            diagnostics.error(
                TYPE_MISMATCH,
                TypeResolver.aggregateKeyword(declaration)
                    + " '"
                    + declaration.name()
                    + "' must provide public interface method '"
                    + group.getFirst().method().name()
                    + "'",
                declaration.nameSpan());
          }
        }
        if (implementation != null) {
          SymbolId selected = implementation;
          for (InterfaceRequirement requirement : group) {
            model.putWitness(
                model.declarationSymbols().get(declaration),
                model.declarationSymbols().get(requirement.method()),
                selected);
          }
        }
      }
    }
  }

  private boolean witnessMatches(Syntax.FunctionDecl witness, InterfaceRequirement requirement) {
    if (witness.typeParameters().size() != requirement.method().typeParameters().size()
        || witness.parameters().size() != requirement.parameters().size()) return false;
    Map<String, SemanticType> witnessTypes =
        typeResolver.typeParameters(witness, typeResolver.ownerOf(witness));
    Symbol requiredSymbol =
        model.symbols().get(model.declarationSymbols().get(requirement.method()));
    Symbol witnessSymbol = model.symbols().get(model.declarationSymbols().get(witness));
    Map<String, SemanticType> requiredSubstitutions =
        typeResolver.interfaceSubstitutions(requirement.owner(), requirement.receiver());
    if (!sameGenericShape(witnessSymbol, Map.of(), requiredSymbol, requiredSubstitutions)) {
      return false;
    }
    Map<String, String> requiredParameters = canonicalTypeParameters(requiredSymbol);
    Map<String, String> witnessParameters = canonicalTypeParameters(witnessSymbol);
    for (int index = 0; index < witness.parameters().size(); index++) {
      Syntax.Parameter parameter = witness.parameters().get(index);
      ParameterInfo required = requirement.parameters().get(index);
      if (!parameter.name().equals(required.name())
          || !canonicalType(
                  typeResolver.resolveDeclarationType(parameter.type(), witness, witnessTypes),
                  witnessParameters)
              .equals(canonicalType(required.type(), requiredParameters))) return false;
    }
    SemanticType actualResult = typeResolver.functionReturnType(witness, witnessTypes);
    return canonicalType(actualResult, witnessParameters)
            .equals(canonicalType(requirement.result(), requiredParameters))
        || typeResolver.isAssignable(requirement.result(), actualResult);
  }

  private static String canonicalType(SemanticType type, Map<String, String> typeParameters) {
    String identity = typeParameters.getOrDefault(type.identity(), type.identity());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> canonicalType(argument, typeParameters))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"));
    return identity + arguments + (type.isNullable() ? "?" : "");
  }

  SymbolId defaultMethodId(Syntax.InterfaceMethodDecl method) {
    return new SymbolId(model.declarationSymbols().get(method).value() + "/default");
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
    SymbolId id = catalog.symbolId(resolution.program(), declaration, kind, name, owner);
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
            "",
            declaration instanceof Syntax.FunctionDecl function
                ? switch (function.kind()) {
                  case GETTER -> Symbol.Accessor.GETTER;
                  case SETTER -> Symbol.Accessor.SETTER;
                  default -> Symbol.Accessor.NONE;
                }
                : Symbol.Accessor.NONE);
    model.putSymbol(id, symbol);
    model.putDeclaration(declaration, id);
    if (symbol.accessor() == Symbol.Accessor.SETTER) model.putDeclarationOperator(nameSpan, id);
    else model.putBinding(nameSpan, id);
    return symbol;
  }

  List<TypeParameterInfo> symbolTypeParameters(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> types) {
    return parameters.stream()
        .map(
            parameter -> {
              SemanticType type = types.get(parameter.name());
              Optional<SemanticType> bound =
                  parameter.upperBound().map(value -> typeResolver.resolveType(value, types));
              Optional<SemanticType> defaultType =
                  parameter.defaultType().map(value -> typeResolver.resolveType(value, types));
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

  List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarationTypes) {
    return parameters.stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    typeResolver
                        .resolveDeclarationType(parameter.type(), parameter, declarationTypes)
                        .substitute(substitutions),
                    parameter.defaultValue().isPresent(),
                    parameter.callableParameters().orElse(List.of()).stream()
                        .map(Syntax.Parameter::name)
                        .toList(),
                    dev.w0fv1.norm.value.ParameterPolicy.LabelPolicy.NAMED,
                    declarationPolicies
                        .resultBuilder(parameter, declarationTypes)
                        .map(type -> type.substitute(substitutions))))
        .toList();
  }

  List<ParameterInfo> parametersOf(
      Syntax.FunctionDecl function, Map<String, SemanticType> substitutions) {
    return parameters(
        function.parameters(),
        substitutions,
        typeResolver.typeParameters(function, typeResolver.ownerOf(function)));
  }

  List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields, Map<String, SemanticType> substitutions) {
    return fieldParameters(fields, substitutions, resolution.parameters());
  }

  List<ParameterInfo> fieldParameters(
      Syntax.AggregateDecl aggregateDecl, Map<String, SemanticType> substitutions) {
    return aggregateDecl.fields().stream()
        .filter(declarationPolicies::constructorInput)
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    typeResolver
                        .resolveDeclarationType(
                            field.type(),
                            field,
                            typeResolver.aggregateTypeParameters(aggregateDecl))
                        .substitute(substitutions),
                    field.defaultValue().isPresent()))
        .toList();
  }

  List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarationTypes) {
    return fields.stream()
        .filter(declarationPolicies::constructorInput)
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    typeResolver
                        .resolveType(field.type(), declarationTypes)
                        .substitute(substitutions),
                    field.defaultValue().isPresent()))
        .toList();
  }
}
