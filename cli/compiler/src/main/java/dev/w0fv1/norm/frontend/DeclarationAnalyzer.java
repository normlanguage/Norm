package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.abi.ExceptionAbi;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InterfaceRequirement;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateView;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
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
  private final SemanticAnalysisContext context;
  private final TypeSystem typeSystem;
  private final BodyAnalyzer bodies;
  private final ExpressionChecker expressionChecker;

  DeclarationAnalyzer(SemanticAnalysisContext context, TypeSystem typeSystem, BodyAnalyzer bodies) {
    this.context = context;
    this.typeSystem = typeSystem;
    this.bodies = bodies;
    expressionChecker = bodies.expressionChecker;
  }

  void collectDeclarations() {
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        if (context.declarations.duplicate(declaration)
            || context.builtins.isType(declaration.name())) {
          context.diagnostics.error(
              DUPLICATE_NAME,
              "type '" + declaration.name() + "' is already declared",
              declaration.span());
        }
        Symbol type =
            typeSystem.registerDeclaration(
                declaration,
                declaration.name(),
                SymbolKind.INTERFACE,
                typeSystem.sourceType(declaration.name(), List.of()),
                declaration.nameSpan(),
                null,
                typeSystem.symbolTypeParameters(
                    declaration.typeParameters(), typeSystem.interfaceTypeParameters(declaration)),
                List.of());
        context.model.putTypeSymbol(type.type().identity(), type.id());
        typeSystem.registerTypeParameters(
            declaration.typeParameters(),
            type.id(),
            typeSystem.interfaceTypeParameters(declaration));
      }
    }
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        if (context.declarations.duplicate(enumDecl)
            || typeSystem.resolveInterface(enumDecl.name()) != null
            || context.builtins.isType(enumDecl.name())) {
          context.diagnostics.error(
              DUPLICATE_NAME,
              "type '" + enumDecl.name() + "' is already declared",
              enumDecl.span());
        }
        Symbol type =
            typeSystem.registerDeclaration(
                enumDecl,
                enumDecl.name(),
                SymbolKind.TYPE,
                typeSystem.sourceType(enumDecl.name(), List.of()),
                enumDecl.nameSpan(),
                null,
                typeSystem.symbolTypeParameters(
                    enumDecl.typeParameters(), typeSystem.enumTypeParameters(enumDecl)),
                List.of());
        context.model.putTypeSymbol(type.type().identity(), type.id());
        typeSystem.registerTypeParameters(
            enumDecl.typeParameters(), type.id(), typeSystem.enumTypeParameters(enumDecl));
      }
    }
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        if (context.declarations.duplicate(aggregateDecl)
            || typeSystem.resolveEnum(aggregateDecl.name()) != null
            || typeSystem.resolveInterface(aggregateDecl.name()) != null
            || context.builtins.isType(aggregateDecl.name())) {
          context.diagnostics.error(
              DUPLICATE_NAME,
              "type '" + aggregateDecl.name() + "' is already declared",
              aggregateDecl.span());
        }
        Symbol type =
            typeSystem.registerDeclaration(
                aggregateDecl,
                aggregateDecl.name(),
                SymbolKind.TYPE,
                typeSystem.sourceType(aggregateDecl.name(), List.of()),
                aggregateDecl.nameSpan(),
                null,
                typeSystem.symbolTypeParameters(
                    aggregateDecl.typeParameters(),
                    typeSystem.aggregateTypeParameters(aggregateDecl)),
                List.of());
        context.model.putTypeSymbol(type.type().identity(), type.id());
        typeSystem.registerTypeParameters(
            aggregateDecl.typeParameters(),
            type.id(),
            typeSystem.aggregateTypeParameters(aggregateDecl));
      }
    }
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        Symbol type =
            context.model.symbols().get(context.model.declarationSymbols().get(declaration));
        Set<String> signatures = new HashSet<>();
        for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
          String signature = TypeSystem.interfaceMethodSignature(method);
          if (!signatures.add(signature)) {
            context.diagnostics.error(
                DUPLICATE_NAME,
                "interface method '" + method.name() + "' is already declared",
                method.span());
          }
          Map<String, SemanticType> parameters = typeSystem.interfaceTypeParameters(declaration);
          parameters = typeSystem.interfaceMethodTypes(declaration, method);
          Symbol symbol =
              typeSystem.registerDeclaration(
                  method,
                  method.name(),
                  SymbolKind.INTERFACE_METHOD,
                  typeSystem.resolveDeclarationType(method.returnType(), method, parameters),
                  method.nameSpan(),
                  type.id(),
                  typeSystem.symbolTypeParameters(method.typeParameters(), parameters),
                  typeSystem.parameters(method.parameters(), Map.of(), parameters));
          typeSystem.registerTypeParameters(method.typeParameters(), symbol.id(), parameters);
          for (Syntax.Parameter parameter : method.parameters()) {
            typeSystem.registerDeclaration(
                parameter,
                parameter.name(),
                SymbolKind.PARAMETER,
                typeSystem.resolveDeclarationType(parameter.type(), method, parameters),
                parameter.nameSpan(),
                symbol.id(),
                List.of(),
                List.of());
          }
          typeSystem.addMember(type.id(), symbol.id());
        }
      }
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        Set<String> variants = new HashSet<>();
        for (Syntax.EnumVariant variant : enumDecl.variants()) {
          if (!variants.add(variant.name())) {
            context.diagnostics.error(
                DUPLICATE_NAME,
                "enum variant '" + variant.name() + "' is already declared",
                variant.nameSpan());
          }
        }
        if (enumDecl.variants().isEmpty()) {
          context.diagnostics.error(
              TYPE_MISMATCH, "enum must declare at least one variant", enumDecl.span());
        }
        Symbol type = context.model.symbols().get(context.model.declarationSymbols().get(enumDecl));
        for (Syntax.EnumVariant variant : enumDecl.variants()) {
          Symbol value =
              typeSystem.registerDeclaration(
                  variant,
                  variant.name(),
                  SymbolKind.ENUM_VARIANT,
                  typeSystem.enumSelfType(enumDecl),
                  variant.nameSpan(),
                  type.id(),
                  typeSystem.symbolTypeParameters(
                      enumDecl.typeParameters(), typeSystem.enumTypeParameters(enumDecl)),
                  typeSystem.parameters(
                      variant.parameters(), Map.of(), typeSystem.enumTypeParameters(enumDecl)));
          typeSystem.addMember(type.id(), value.id());
        }
      }
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        Symbol type =
            context.model.symbols().get(context.model.declarationSymbols().get(aggregateDecl));
        for (Syntax.FieldDecl field : aggregateDecl.fields()) {
          Symbol symbol =
              typeSystem.registerDeclaration(
                  field,
                  field.name(),
                  SymbolKind.FIELD,
                  typeSystem.resolveDeclarationType(
                      field.type(), field, typeSystem.aggregateTypeParameters(aggregateDecl)),
                  field.nameSpan(),
                  type.id(),
                  List.of(),
                  List.of());
          if (field.visibility() == Syntax.Visibility.PUBLIC) {
            typeSystem.addMember(type.id(), symbol.id());
          }
        }
        List<ParameterInfo> constructionParameters =
            aggregateDecl.constructors().isEmpty()
                ? typeSystem.fieldParameters(
                    aggregateDecl.fields(),
                    Map.of(),
                    typeSystem.aggregateTypeParameters(aggregateDecl))
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
        context.model.putSymbol(type.id(), type);
        for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
          typeSystem.registerDeclaration(
              constructor,
              constructor.name(),
              SymbolKind.CONSTRUCTOR,
              SemanticType.VOID,
              constructor.nameSpan(),
              type.id(),
              List.of(),
              typeSystem.parameters(
                  constructor.parameters(),
                  Map.of(),
                  typeSystem.aggregateTypeParameters(aggregateDecl)));
        }
        if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE) {
          SymbolId copyId = SymbolId.authored(DeclarationIdentity.synthetic(type.id(), "copy"));
          Symbol copy =
              new Symbol(
                  copyId,
                  "copy",
                  SymbolKind.METHOD,
                  typeSystem.aggregateSelfType(aggregateDecl),
                  Optional.empty(),
                  Optional.of(type.id()),
                  List.of(),
                  List.of(),
                  "Creates a new top-level object identity.");
          context.model.putSymbol(copyId, copy);
          typeSystem.addMember(type.id(), copyId);
          context.model.putCopyMethod(type.type().identity(), copyId);
        }
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          typeSystem.validateTypeParameterNames(method.typeParameters());
          Symbol symbol =
              typeSystem.registerDeclaration(
                  method,
                  method.name(),
                  SymbolKind.METHOD,
                  typeSystem.functionReturnType(
                      method, typeSystem.typeParameters(method, aggregateDecl)),
                  method.nameSpan(),
                  type.id(),
                  typeSystem.symbolTypeParameters(
                      method.typeParameters(), typeSystem.typeParameters(method, aggregateDecl)),
                  typeSystem.parametersOf(method, Map.of()));
          typeSystem.registerTypeParameters(
              method.typeParameters(), symbol.id(), typeSystem.functionTypeParameters(method));
          if (method.visibility() == Syntax.Visibility.PUBLIC) {
            typeSystem.addMember(type.id(), symbol.id());
          }
        }
      }
    }
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.FunctionDecl function : program.functions()) {
        typeSystem.validateTypeParameterNames(function.typeParameters());
        if (context.declarations.duplicate(function)) {
          context.diagnostics.error(
              DUPLICATE_NAME,
              "function overload '" + function.name() + "' is already declared",
              function.span());
        }
        Symbol symbol =
            typeSystem.registerDeclaration(
                function,
                function.name(),
                function.kind() == Syntax.FunctionKind.EXTENSION
                    ? SymbolKind.EXTENSION
                    : SymbolKind.FUNCTION,
                typeSystem.functionReturnType(
                    function, typeSystem.functionTypeParameters(function)),
                function.nameSpan(),
                null,
                typeSystem.symbolTypeParameters(
                    function.typeParameters(), typeSystem.functionTypeParameters(function)),
                typeSystem.parametersOf(function, Map.of()));
        typeSystem.registerTypeParameters(
            function.typeParameters(), symbol.id(), typeSystem.functionTypeParameters(function));
      }
    }
  }

  void validateClassHierarchy() {
    Set<Syntax.AggregateDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.AggregateDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        context.resolution.activeTypeParameters = typeSystem.aggregateTypeParameters(declaration);
        context.resolution.activeTypeParameterSymbols =
            typeSystem.typeParameterSymbols(declaration.typeParameters());
        if (typeSystem
            .aggregateSelfType(declaration)
            .identity()
            .equals(SemanticType.EXCEPTION.identity())) {
          boolean validField =
              declaration.fields().size() == 1
                  && declaration.fields().getFirst().visibility() == Syntax.Visibility.PUBLIC
                  && declaration.fields().getFirst().name().equals(ExceptionAbi.MESSAGE_FIELD_NAME)
                  && typeSystem
                      .resolveType(
                          declaration.fields().getFirst().type(),
                          context.resolution.activeTypeParameters)
                      .equals(SemanticType.STRING);
          if (declaration.kind() != Syntax.AggregateKind.CLASS
              || declaration.visibility() != Syntax.Visibility.PUBLIC
              || !declaration.typeParameters().isEmpty()
              || declaration.extendedClass().isPresent()
              || !validField) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "Exception root ABI requires public class Exception with one public String message field",
                declaration.nameSpan());
          }
        }
        declaration
            .extendedClass()
            .ifPresent(
                parentRef -> {
                  typeSystem.validateType(parentRef, false);
                  SemanticType parent =
                      typeSystem.resolveType(parentRef, context.resolution.activeTypeParameters);
                  Syntax.AggregateDecl parentDeclaration = typeSystem.resolveAggregate(parent);
                  if (declaration.kind() != Syntax.AggregateKind.CLASS
                      || parentDeclaration == null
                      || parentDeclaration.kind() != Syntax.AggregateKind.CLASS) {
                    context.diagnostics.error(
                        TYPE_MISMATCH, "class inheritance requires a class", parentRef.span());
                    return;
                  }
                  context.model.putAggregateParent(
                      typeSystem.aggregateSelfType(declaration).identity(), parent);
                  if (typeSystem.isAssignable(
                          SemanticType.EXCEPTION, typeSystem.aggregateSelfType(declaration))
                      && !declaration.typeParameters().isEmpty()) {
                    context.diagnostics.error(
                        TYPE_MISMATCH,
                        "Exception classes cannot declare type parameters",
                        declaration.nameSpan());
                  }
                  if (declaration.constructors().isEmpty()) {
                    context.diagnostics.error(
                        TYPE_MISMATCH,
                        "subclass '" + declaration.name() + "' must declare a constructor",
                        declaration.nameSpan());
                  }
                  validateInheritedFields(declaration);
                  validateOverrides(declaration);
                });
        context.resolution.activeTypeParameters = Map.of();
        context.resolution.activeTypeParameterSymbols = Map.of();
      }
    }
    for (Syntax.Program program : context.programs) {
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
      context.diagnostics.error(
          TYPE_MISMATCH, "class inheritance contains a cycle", declaration.nameSpan());
      return;
    }
    Syntax.Program previous = context.resolution.currentProgram;
    context.resolution.currentProgram = context.declarations.owner(declaration);
    typeSystem
        .directParentType(declaration, typeSystem.aggregateSelfType(declaration))
        .map(typeSystem::resolveAggregate)
        .ifPresent(parent -> validateClassCycle(parent, visiting, visited));
    context.resolution.currentProgram = previous;
    visiting.remove(declaration);
    visited.add(declaration);
  }

  private void validateInheritedFields(Syntax.AggregateDecl declaration) {
    Set<String> inherited = new HashSet<>();
    List<AggregateView> views =
        typeSystem.aggregateViews(typeSystem.aggregateSelfType(declaration));
    for (AggregateView view : views.subList(Math.min(1, views.size()), views.size())) {
      view.declaration().fields().forEach(field -> inherited.add(field.name()));
    }
    for (Syntax.FieldDecl field : declaration.fields()) {
      if (inherited.contains(field.name())) {
        context.diagnostics.error(
            DUPLICATE_NAME,
            "field '" + field.name() + "' is already declared by a parent class",
            field.nameSpan());
      }
    }
  }

  private void validateOverrides(Syntax.AggregateDecl declaration) {
    List<AggregateView> views =
        typeSystem.aggregateViews(typeSystem.aggregateSelfType(declaration));
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
      Symbol methodSymbol =
          context.model.symbols().get(context.model.declarationSymbols().get(method));
      Symbol parentSymbol =
          context.model.symbols().get(context.model.declarationSymbols().get(parent.method()));
      Map<String, SemanticType> parentSubstitutions =
          typeSystem.aggregateSubstitutions(parent.view().declaration(), parent.view().type());
      if (!sameGenericShape(methodSymbol, Map.of(), parentSymbol, parentSubstitutions)) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "override of '" + method.name() + "' must preserve type parameter bounds",
            method.nameSpan());
        continue;
      }
      SemanticType expected =
          typeSystem
              .functionReturnType(
                  parent.method(),
                  typeSystem.typeParameters(parent.method(), parent.view().declaration()))
              .substitute(parentSubstitutions);
      SemanticType actual =
          typeSystem.functionReturnType(method, typeSystem.typeParameters(method, declaration));
      boolean sameReturnShape =
          canonicalType(expected, canonicalTypeParameters(parentSymbol))
              .equals(canonicalType(actual, canonicalTypeParameters(methodSymbol)));
      if (!sameReturnShape && !typeSystem.isAssignable(expected, actual)) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "override of '" + method.name() + "' must return " + expected.displayName(),
            method.nameSpan());
        continue;
      }
      context.model.putOverride(
          context.model.declarationSymbols().get(method),
          context.model.declarationSymbols().get(parent.method()));
    }
  }

  private boolean sameOverrideParameters(
      Syntax.FunctionDecl method, Syntax.FunctionDecl inherited, AggregateView parent) {
    if (method.typeParameters().size() != inherited.typeParameters().size()
        || method.parameters().size() != inherited.parameters().size()) return false;
    Symbol methodSymbol =
        context.model.symbols().get(context.model.declarationSymbols().get(method));
    Symbol parentSymbol =
        context.model.symbols().get(context.model.declarationSymbols().get(inherited));
    Map<String, String> methodTypes = canonicalTypeParameters(methodSymbol);
    Map<String, String> parentTypes = canonicalTypeParameters(parentSymbol);
    Map<String, SemanticType> substitutions =
        typeSystem.aggregateSubstitutions(parent.declaration(), parent.type());
    for (int index = 0; index < methodSymbol.parameters().size(); index++) {
      ParameterInfo own = methodSymbol.parameters().get(index);
      ParameterInfo base = parentSymbol.parameters().get(index);
      if (!own.name().equals(base.name())
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

  void validateFields(Syntax.AggregateDecl aggregateDecl) {
    context.resolution.activeTypeParameters = typeSystem.aggregateTypeParameters(aggregateDecl);
    context.resolution.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(aggregateDecl.typeParameters());
    typeSystem.registerBounds(
        aggregateDecl.typeParameters(), context.resolution.activeTypeParameters);
    typeSystem.validateTypeParameterDefaults(
        aggregateDecl.typeParameters(),
        context.resolution.activeTypeParameters,
        aggregateDecl.visibility() == Syntax.Visibility.PUBLIC);
    Set<String> names = new HashSet<>();
    boolean defaultFieldSeen = false;
    for (Syntax.FieldDecl field : aggregateDecl.fields()) {
      typeSystem.validateType(field.type(), false);
      if (field.defaultValue().isPresent()) {
        defaultFieldSeen = true;
        SemanticType expected =
            typeSystem.resolveType(field.type(), context.resolution.activeTypeParameters);
        typeSystem.requireType(
            expected,
            expressionChecker.typeOf(field.defaultValue().orElseThrow(), expected),
            field.defaultValue().orElseThrow().span());
      } else if (defaultFieldSeen) {
        context.diagnostics.error(
            INVALID_CALL, "required field follows a default field", field.nameSpan());
      }
      if (aggregateDecl.visibility() == Syntax.Visibility.PUBLIC
          && field.visibility() == Syntax.Visibility.PUBLIC) {
        typeSystem.validatePublicType(field.type());
      }
      if (!names.add(field.name())) {
        context.diagnostics.error(
            DUPLICATE_NAME, "field '" + field.name() + "' is already declared", field.span());
      }
    }
    Set<String> methods = new HashSet<>();
    for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
      if (aggregateDecl.kind() != Syntax.AggregateKind.VALUE && method.name().equals("copy")) {
        context.diagnostics.error(
            DUPLICATE_NAME, "method 'copy' is reserved for identity copying", method.nameSpan());
      }
      if (!methods.add(TypeSystem.callableSignature(method))) {
        context.diagnostics.error(
            DUPLICATE_NAME, "method '" + method.name() + "' is already declared", method.span());
      }
    }
    Set<String> constructors = new HashSet<>();
    for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
      if (!constructors.add(TypeSystem.constructorSignature(constructor))) {
        context.diagnostics.error(
            DUPLICATE_NAME,
            "constructor '" + aggregateDecl.name() + "' is already declared",
            constructor.span());
      }
    }
    if (aggregateDecl.kind() == Syntax.AggregateKind.VALUE
        && !aggregateDecl.constructors().isEmpty()) {
      context.diagnostics.error(
          TYPE_MISMATCH,
          "value '" + aggregateDecl.name() + "' cannot declare a constructor",
          aggregateDecl.constructors().getFirst().span());
    }
    context.resolution.activeTypeParameters = Map.of();
    context.resolution.activeTypeParameterSymbols = Map.of();
  }

  void validateEnum(Syntax.EnumDecl enumDecl) {
    context.resolution.activeTypeParameters = typeSystem.enumTypeParameters(enumDecl);
    context.resolution.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(enumDecl.typeParameters());
    typeSystem.registerBounds(enumDecl.typeParameters(), context.resolution.activeTypeParameters);
    typeSystem.validateTypeParameterDefaults(
        enumDecl.typeParameters(),
        context.resolution.activeTypeParameters,
        enumDecl.visibility() == Syntax.Visibility.PUBLIC);
    for (Syntax.EnumVariant variant : enumDecl.variants()) {
      bodies.analyzeParameterDefaults(variant.parameters());
      Set<String> names = new HashSet<>();
      for (Syntax.Parameter parameter : variant.parameters()) {
        typeSystem.validateType(parameter.type(), false);
        if (!names.add(parameter.name())) {
          context.diagnostics.error(
              DUPLICATE_NAME,
              "enum data '" + parameter.name() + "' is already declared",
              parameter.nameSpan());
        }
      }
    }
    context.resolution.activeTypeParameters = Map.of();
    context.resolution.activeTypeParameterSymbols = Map.of();
  }

  void validateInterface(Syntax.InterfaceDecl declaration) {
    context.resolution.activeTypeParameters = typeSystem.interfaceTypeParameters(declaration);
    context.resolution.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(declaration.typeParameters());
    typeSystem.registerBounds(
        declaration.typeParameters(), context.resolution.activeTypeParameters);
    typeSystem.validateTypeParameterDefaults(
        declaration.typeParameters(),
        context.resolution.activeTypeParameters,
        declaration.visibility() == Syntax.Visibility.PUBLIC);
    for (Syntax.TypeRef parent : declaration.extendedInterfaces()) {
      typeSystem.validateType(parent, false);
      if (typeSystem.resolveInterface(
              typeSystem.resolveType(parent, context.resolution.activeTypeParameters))
          == null) {
        context.diagnostics.error(
            TYPE_MISMATCH, "interface may extend interfaces only", parent.span());
      }
    }
    for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
      typeSystem.validateTypeParameterNames(method.typeParameters());
      Map<String, SemanticType> methodTypes = typeSystem.interfaceMethodTypes(declaration, method);
      Map<String, SymbolId> methodSymbols =
          new LinkedHashMap<>(context.resolution.activeTypeParameterSymbols);
      method
          .typeParameters()
          .forEach(
              parameter ->
                  methodSymbols.put(
                      parameter.name(), context.model.declarationSymbols().get(parameter)));
      context.resolution.activeTypeParameters = methodTypes;
      context.resolution.activeTypeParameterSymbols = Map.copyOf(methodSymbols);
      typeSystem.registerBounds(method.typeParameters(), methodTypes);
      typeSystem.validateTypeParameterDefaults(
          method.typeParameters(),
          methodTypes,
          declaration.visibility() == Syntax.Visibility.PUBLIC);
      typeSystem.validateType(method.returnType(), true);
      method
          .parameters()
          .forEach(parameter -> typeSystem.validateReferenceCapableType(parameter.type()));
      if (method.body().isPresent())
        bodies.analyzeInterfaceDefault(declaration, method, methodTypes, methodSymbols);
      context.resolution.activeTypeParameters = typeSystem.interfaceTypeParameters(declaration);
      context.resolution.activeTypeParameterSymbols =
          typeSystem.typeParameterSymbols(declaration.typeParameters());
    }
    context.resolution.activeTypeParameters = Map.of();
    context.resolution.activeTypeParameterSymbols = Map.of();
  }

  void validateInterfaceGraphAndConformances() {
    Set<Syntax.InterfaceDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.InterfaceDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.InterfaceDecl declaration : context.declarations.interfaces()) {
      validateInterfaceCycle(declaration, visiting, visited);
    }
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        validateAggregateConformance(declaration);
      }
    }
  }

  private void validateInterfaceCycle(
      Syntax.InterfaceDecl declaration,
      Set<Syntax.InterfaceDecl> visiting,
      Set<Syntax.InterfaceDecl> visited) {
    if (visited.contains(declaration)) return;
    if (!visiting.add(declaration)) {
      context.diagnostics.error(
          TYPE_MISMATCH, "interface inheritance contains a cycle", declaration.nameSpan());
      return;
    }
    Syntax.Program previous = context.resolution.currentProgram;
    context.resolution.currentProgram = context.declarations.owner(declaration);
    Map<String, SemanticType> parameters = typeSystem.interfaceTypeParameters(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      Syntax.InterfaceDecl parent =
          typeSystem.resolveInterface(typeSystem.resolveType(parentRef, parameters));
      if (parent != null) validateInterfaceCycle(parent, visiting, visited);
    }
    context.resolution.currentProgram = previous;
    visiting.remove(declaration);
    visited.add(declaration);
  }

  private void validateAggregateConformance(Syntax.AggregateDecl declaration) {
    context.resolution.activeTypeParameters = typeSystem.aggregateTypeParameters(declaration);
    context.resolution.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(declaration.typeParameters());
    typeSystem.registerBounds(
        declaration.typeParameters(), context.resolution.activeTypeParameters);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : declaration.implementedInterfaces()) {
      typeSystem.validateType(interfaceRef, false);
      SemanticType interfaceType =
          typeSystem.resolveType(interfaceRef, context.resolution.activeTypeParameters);
      Syntax.InterfaceDecl interfaceDecl = typeSystem.resolveInterface(interfaceType);
      if (interfaceDecl == null) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            TypeSystem.aggregateKeyword(declaration) + " may implement interfaces only",
            interfaceRef.span());
        continue;
      }
      typeSystem.collectConformances(
          interfaceDecl, interfaceType, conformances, interfaceRef.span());
    }
    Map<String, InterfaceRequirement> requirements = new LinkedHashMap<>();
    for (SemanticType conformance : conformances.values()) {
      Syntax.InterfaceDecl interfaceDecl = typeSystem.resolveInterface(conformance);
      if (interfaceDecl == null) continue;
      for (InterfaceRequirement requirement :
          typeSystem.directRequirements(interfaceDecl, conformance)) {
        InterfaceRequirement existing = requirements.putIfAbsent(requirement.key(), requirement);
        if (existing != null && !existing.signature().equals(requirement.signature())) {
          context.diagnostics.error(
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
                    typeSystem::requirementShape,
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
      SymbolId implementation =
          witness == null ? null : context.model.declarationSymbols().get(witness);
      if (implementation == null) {
        List<InterfaceRequirement> active = typeSystem.mostSpecificRequirements(group);
        List<InterfaceRequirement> defaults =
            active.stream().filter(value -> value.method().body().isPresent()).toList();
        if (defaults.size() == 1) {
          implementation = typeSystem.defaultMethodId(defaults.getFirst().method());
        } else if (defaults.size() > 1) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "inherited interface default methods conflict for method '"
                  + group.getFirst().method().name()
                  + "'",
              declaration.nameSpan());
        } else {
          context.diagnostics.error(
              TYPE_MISMATCH,
              TypeSystem.aggregateKeyword(declaration)
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
          context.model.putWitness(
              context.model.declarationSymbols().get(declaration),
              context.model.declarationSymbols().get(requirement.method()),
              selected);
        }
      }
    }
    context.resolution.activeTypeParameters = Map.of();
    context.resolution.activeTypeParameterSymbols = Map.of();
  }

  private boolean witnessMatches(Syntax.FunctionDecl witness, InterfaceRequirement requirement) {
    if (witness.typeParameters().size() != requirement.method().typeParameters().size()
        || witness.parameters().size() != requirement.parameters().size()) return false;
    Map<String, SemanticType> witnessTypes =
        typeSystem.typeParameters(witness, typeSystem.ownerOf(witness));
    Symbol requiredSymbol =
        context.model.symbols().get(context.model.declarationSymbols().get(requirement.method()));
    Symbol witnessSymbol =
        context.model.symbols().get(context.model.declarationSymbols().get(witness));
    Map<String, SemanticType> requiredSubstitutions =
        typeSystem.interfaceSubstitutions(requirement.owner(), requirement.receiver());
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
                  typeSystem.resolveDeclarationType(parameter.type(), witness, witnessTypes),
                  witnessParameters)
              .equals(canonicalType(required.type(), requiredParameters))) return false;
    }
    SemanticType actualResult = typeSystem.functionReturnType(witness, witnessTypes);
    return canonicalType(actualResult, witnessParameters)
            .equals(canonicalType(requirement.result(), requiredParameters))
        || typeSystem.isAssignable(requirement.result(), actualResult);
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
}
