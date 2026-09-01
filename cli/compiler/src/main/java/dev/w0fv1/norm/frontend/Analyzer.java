package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.abi.ExceptionAbi;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.TypeSystem.AggregateView;
import dev.w0fv1.norm.semantic.AnnotationIndex;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.LexicalLifetime;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class Analyzer {
  final SemanticAnalysisContext context;
  private final Deque<Set<SymbolId>> flowWriteCollectors = new ArrayDeque<>();
  final TypeSystem typeSystem;
  private final ExpressionChecker expressionChecker;

  Analyzer(SemanticAnalysisInput input, DiagnosticBag diagnostics, CompilationGuard guard) {
    context =
        new SemanticAnalysisContext(
            input, diagnostics, guard, this::isNominallyAssignable, this::typeOf);
    typeSystem = new TypeSystem(this);
    expressionChecker = new ExpressionChecker(this);
  }

  SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    return expressionChecker.typeOf(expression, expected);
  }

  TypeProbe probeType(Syntax.Expression expression, SemanticType expected) {
    return expressionChecker.probeType(expression, expected);
  }

  SemanticType memberType(Syntax.Member member, SemanticType expected) {
    return expressionChecker.memberType(member, expected);
  }

  SemanticType functionReturnType(
      Syntax.FunctionDecl function, Map<String, SemanticType> typeParameters) {
    return expressionChecker.functionReturnType(function, typeParameters);
  }

  boolean isNominallyAssignable(SemanticType expected, SemanticType actual) {
    return typeSystem.isNominallyAssignable(expected, actual);
  }

  private String requirementShape(InterfaceRequirement requirement) {
    return expressionChecker.requirementShape(requirement);
  }

  private List<InterfaceRequirement> mostSpecificRequirements(
      List<InterfaceRequirement> requirements) {
    return expressionChecker.mostSpecificRequirements(requirements);
  }

  private SemanticType assignmentTargetType(Syntax.Expression target) {
    return expressionChecker.assignmentTargetType(target);
  }

  private Symbol scopedSymbol(FlowScopes.ScopedSymbol scoped) {
    return expressionChecker.scopedSymbol(scoped);
  }

  private void reportMutableCapture(SymbolId symbol, SourceSpan span) {
    expressionChecker.reportMutableCapture(symbol, span);
  }

  private Optional<ResolvedIteration> resolveInterfaceIteration(SemanticType iterableType) {
    return expressionChecker.resolveInterfaceIteration(iterableType);
  }

  private void analyzeBreak(Syntax.BreakStatement statement) {
    expressionChecker.analyzeBreak(statement);
  }

  private LexicalLifetime referenceLifetime(Syntax.Expression expression) {
    return expressionChecker.referenceLifetime(expression);
  }

  FrontendAnalysis analyze(boolean resolveProgram) {
    context.guard.checkpoint();
    collectDeclarations();
    context.nextSymbolId = Math.max(context.nextSymbolId, context.minimumBodySymbolId);
    ImportResolver.Result imports =
        new ImportResolver()
            .resolve(
                new ImportResolver.Input(
                    context.programs,
                    context.declarations,
                    context.symbols,
                    context.declarationSymbols,
                    context.nextSymbolId));
    imports.diagnostics().forEach(context.diagnostics::report);
    context.symbols.putAll(imports.aliases());
    context.bindings.putAll(imports.bindings());
    context.importAliases.putAll(imports.importAliases());
    context.aliasTargets.putAll(imports.aliasTargets());
    context.nextSymbolId = imports.nextSymbolId();
    VisibilityResolver.Result visibility =
        new VisibilityResolver(
                new VisibilityResolver.Input(
                    context.programs,
                    context.scope,
                    context.symbols,
                    context.declarationSymbols,
                    context.importAliases,
                    context.declarations,
                    context.exportedSources))
            .build();
    visibility.scopes().forEach(context.flowScopes::addSemanticScope);
    validateClassHierarchy();
    context.currentProgram = context.entryProgram;
    Syntax.FunctionDecl main =
        context.entryProgram.functions().stream()
            .filter(function -> function.name().equals("main"))
            .findFirst()
            .orElse(null);
    if (main == null && context.requireEntryPoint) {
      context.diagnostics.error(
          MISSING_MAIN, "program must declare 'main()'", context.syntax.span());
    } else if (main != null
        && (!functionReturnType(main, typeSystem.functionTypeParameters(main))
                .equals(SemanticType.VOID)
            || !main.typeParameters().isEmpty()
            || !main.parameters().isEmpty())) {
      context.diagnostics.error(TYPE_MISMATCH, "entry function must be 'main()'", main.span());
    }

    for (Syntax.Program program : context.programs) {
      context.guard.checkpoint();
      context.currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        if (reuse(enumDecl.span())) continue;
        typeSystem.validateTypeParameterNames(enumDecl.typeParameters());
        validateEnum(enumDecl);
      }
      for (Syntax.InterfaceDecl interfaceDecl : program.interfaces()) {
        if (reuse(interfaceDecl.span())) continue;
        typeSystem.validateTypeParameterNames(interfaceDecl.typeParameters());
        validateInterface(interfaceDecl);
      }
      for (Syntax.FunctionDecl function : program.functions()) {
        if (reuse(function.span())) continue;
        analyzeFunction(function, null);
      }
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        if (reuse(aggregateDecl.span())) continue;
        typeSystem.validateTypeParameterNames(aggregateDecl.typeParameters());
        validateFields(aggregateDecl);
        for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
          analyzeConstructor(constructor, aggregateDecl);
        }
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          analyzeFunction(method, aggregateDecl);
        }
      }
    }
    AnnotationChecker annotationChecker = new AnnotationChecker(this);
    annotationChecker.validateAnnotationSchemas();
    annotationChecker.validateAnnotationApplications();
    validateInterfaceGraphAndConformances();
    List<dev.w0fv1.norm.diagnostic.Diagnostic> snapshot = context.diagnostics.snapshot();
    SemanticModel semanticModel =
        new SemanticModel(
            context.syntax.span().source(),
            context.syntax,
            context.symbols,
            context.bindings,
            context.semanticTypes,
            context.resolvedCalls,
            context.functionReferenceTypeArguments,
            context.iterations,
            context.indexes,
            context.members,
            context.aliasTargets,
            typeSystem.callableGroups(),
            context.witnesses,
            context.aggregateParents,
            context.methodOverrides,
            context.typeSymbols,
            typeSystem.interfaceParentTypes(),
            new AnnotationIndex(context.annotationSchemas, context.annotationApplications),
            context.flowScopes.semanticScopes(),
            snapshot,
            visibility.importableSymbols(),
            context.scope,
            context.builtins);
    Optional<dev.w0fv1.norm.bound.BoundProgram> boundProgram =
        !resolveProgram
                || snapshot.stream()
                    .anyMatch(
                        diagnostic ->
                            diagnostic.severity()
                                == dev.w0fv1.norm.diagnostic.DiagnosticSeverity.ERROR)
            ? Optional.empty()
            : Optional.of(new Binder(context.programs, semanticModel).bind(main));
    return new FrontendAnalysis(
        new AnalysisResult(semanticModel, Optional.ofNullable(main), snapshot), boundProgram);
  }

  private boolean reuse(SourceSpan root) {
    SemanticContribution contribution = context.reusableDeclarations.get(root);
    if (contribution == null) return false;
    context.symbols.putAll(contribution.symbols());
    context.bindings.putAll(contribution.bindings());
    context.semanticTypes.putAll(contribution.expressionTypes());
    context.resolvedCalls.putAll(contribution.resolvedCalls());
    context.functionReferenceTypeArguments.putAll(contribution.functionReferenceTypeArguments());
    context.iterations.putAll(contribution.iterations());
    context.indexes.putAll(contribution.indexes());
    contribution.scopes().forEach(context.flowScopes::addSemanticScope);
    return true;
  }

  private void collectDeclarations() {
    for (Syntax.Program program : context.programs) {
      context.currentProgram = program;
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
        context.typeSymbols.putIfAbsent(type.type().identity(), type.id());
        typeSystem.registerTypeParameters(
            declaration.typeParameters(),
            type.id(),
            typeSystem.interfaceTypeParameters(declaration));
      }
    }
    for (Syntax.Program program : context.programs) {
      context.currentProgram = program;
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
        context.typeSymbols.putIfAbsent(type.type().identity(), type.id());
        typeSystem.registerTypeParameters(
            enumDecl.typeParameters(), type.id(), typeSystem.enumTypeParameters(enumDecl));
      }
    }
    for (Syntax.Program program : context.programs) {
      context.currentProgram = program;
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
        context.typeSymbols.putIfAbsent(type.type().identity(), type.id());
        typeSystem.registerTypeParameters(
            aggregateDecl.typeParameters(),
            type.id(),
            typeSystem.aggregateTypeParameters(aggregateDecl));
      }
    }
    for (Syntax.Program program : context.programs) {
      context.currentProgram = program;
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        Symbol type = context.symbols.get(context.declarationSymbols.get(declaration));
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
          parameters =
              typeSystem.withTypeParameters(
                  parameters,
                  method.typeParameters(),
                  context.declarations.owner(declaration),
                  "interface-method/" + method.name());
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
        Symbol type = context.symbols.get(context.declarationSymbols.get(enumDecl));
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
        Symbol type = context.symbols.get(context.declarationSymbols.get(aggregateDecl));
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
        context.symbols.put(type.id(), type);
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
          context.symbols.put(copyId, copy);
          typeSystem.addMember(type.id(), copyId);
          context.copyMethods.put(type.type().identity(), copyId);
        }
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          typeSystem.validateTypeParameterNames(method.typeParameters());
          Symbol symbol =
              typeSystem.registerDeclaration(
                  method,
                  method.name(),
                  SymbolKind.METHOD,
                  functionReturnType(method, typeSystem.typeParameters(method, aggregateDecl)),
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
      context.currentProgram = program;
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
                functionReturnType(function, typeSystem.functionTypeParameters(function)),
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

  private void validateClassHierarchy() {
    Set<Syntax.AggregateDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.AggregateDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.Program program : context.programs) {
      context.currentProgram = program;
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        context.activeTypeParameters = typeSystem.aggregateTypeParameters(declaration);
        context.activeTypeParameterSymbols =
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
                          declaration.fields().getFirst().type(), context.activeTypeParameters)
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
                      typeSystem.resolveType(parentRef, context.activeTypeParameters);
                  Syntax.AggregateDecl parentDeclaration = typeSystem.resolveAggregate(parent);
                  if (declaration.kind() != Syntax.AggregateKind.CLASS
                      || parentDeclaration == null
                      || parentDeclaration.kind() != Syntax.AggregateKind.CLASS) {
                    context.diagnostics.error(
                        TYPE_MISMATCH, "class inheritance requires a class", parentRef.span());
                    return;
                  }
                  context.aggregateParents.put(
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
        context.activeTypeParameters = Map.of();
        context.activeTypeParameterSymbols = Map.of();
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
    Syntax.Program previous = context.currentProgram;
    context.currentProgram = context.declarations.owner(declaration);
    typeSystem
        .directParentType(declaration, typeSystem.aggregateSelfType(declaration))
        .map(typeSystem::resolveAggregate)
        .ifPresent(parent -> validateClassCycle(parent, visiting, visited));
    context.currentProgram = previous;
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
      Symbol methodSymbol = context.symbols.get(context.declarationSymbols.get(method));
      Symbol parentSymbol = context.symbols.get(context.declarationSymbols.get(parent.method()));
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
          functionReturnType(
                  parent.method(),
                  typeSystem.typeParameters(parent.method(), parent.view().declaration()))
              .substitute(parentSubstitutions);
      SemanticType actual =
          functionReturnType(method, typeSystem.typeParameters(method, declaration));
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
      context.methodOverrides.put(
          context.declarationSymbols.get(method), context.declarationSymbols.get(parent.method()));
    }
  }

  private boolean sameOverrideParameters(
      Syntax.FunctionDecl method, Syntax.FunctionDecl inherited, AggregateView parent) {
    if (method.typeParameters().size() != inherited.typeParameters().size()
        || method.parameters().size() != inherited.parameters().size()) return false;
    Symbol methodSymbol = context.symbols.get(context.declarationSymbols.get(method));
    Symbol parentSymbol = context.symbols.get(context.declarationSymbols.get(inherited));
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
      if (candidateBound.isPresent() != requiredBound.isPresent()) return false;
      if (candidateBound.isPresent()
          && !canonicalType(
                  candidateBound.orElseThrow().substitute(candidateSubstitutions),
                  candidateParameters)
              .equals(
                  canonicalType(
                      requiredBound.orElseThrow().substitute(requirementSubstitutions),
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

  private void validateFields(Syntax.AggregateDecl aggregateDecl) {
    context.activeTypeParameters = typeSystem.aggregateTypeParameters(aggregateDecl);
    context.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(aggregateDecl.typeParameters());
    registerBounds(aggregateDecl.typeParameters(), context.activeTypeParameters);
    Set<String> names = new HashSet<>();
    for (Syntax.FieldDecl field : aggregateDecl.fields()) {
      typeSystem.validateType(field.type(), false);
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
    context.activeTypeParameters = Map.of();
    context.activeTypeParameterSymbols = Map.of();
  }

  private void validateEnum(Syntax.EnumDecl enumDecl) {
    context.activeTypeParameters = typeSystem.enumTypeParameters(enumDecl);
    context.activeTypeParameterSymbols = typeSystem.typeParameterSymbols(enumDecl.typeParameters());
    registerBounds(enumDecl.typeParameters(), context.activeTypeParameters);
    for (Syntax.EnumVariant variant : enumDecl.variants()) {
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
    context.activeTypeParameters = Map.of();
    context.activeTypeParameterSymbols = Map.of();
  }

  private void validateInterface(Syntax.InterfaceDecl declaration) {
    context.activeTypeParameters = typeSystem.interfaceTypeParameters(declaration);
    context.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(declaration.typeParameters());
    registerBounds(declaration.typeParameters(), context.activeTypeParameters);
    for (Syntax.TypeRef parent : declaration.extendedInterfaces()) {
      typeSystem.validateType(parent, false);
      if (typeSystem.resolveInterface(typeSystem.resolveType(parent, context.activeTypeParameters))
          == null) {
        context.diagnostics.error(
            TYPE_MISMATCH, "interface may extend interfaces only", parent.span());
      }
    }
    for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
      typeSystem.validateTypeParameterNames(method.typeParameters());
      Map<String, SemanticType> methodTypes =
          typeSystem.withTypeParameters(
              context.activeTypeParameters,
              method.typeParameters(),
              context.declarations.owner(declaration),
              "interface-method/" + method.name());
      Map<String, SymbolId> methodSymbols = new LinkedHashMap<>(context.activeTypeParameterSymbols);
      method
          .typeParameters()
          .forEach(
              parameter ->
                  methodSymbols.put(parameter.name(), context.declarationSymbols.get(parameter)));
      context.activeTypeParameters = methodTypes;
      context.activeTypeParameterSymbols = Map.copyOf(methodSymbols);
      registerBounds(method.typeParameters(), methodTypes);
      typeSystem.validateType(method.returnType(), true);
      method
          .parameters()
          .forEach(parameter -> typeSystem.validateReferenceCapableType(parameter.type()));
      if (method.body().isPresent())
        analyzeInterfaceDefault(declaration, method, methodTypes, methodSymbols);
      context.activeTypeParameters = typeSystem.interfaceTypeParameters(declaration);
      context.activeTypeParameterSymbols =
          typeSystem.typeParameterSymbols(declaration.typeParameters());
    }
    context.activeTypeParameters = Map.of();
    context.activeTypeParameterSymbols = Map.of();
  }

  private void analyzeInterfaceDefault(
      Syntax.InterfaceDecl owner,
      Syntax.InterfaceMethodDecl method,
      Map<String, SemanticType> methodTypes,
      Map<String, SymbolId> methodSymbols) {
    SemanticType previousReturn = context.expectedReturnType;
    SymbolId previousCallable = context.currentCallable;
    context.expectedReturnType =
        typeSystem.resolveDeclarationType(method.returnType(), method, methodTypes);
    context.currentCallable = defaultMethodId(method);
    context.flowScopes.clear();
    typeSystem.pushScope(method.span());
    for (Syntax.TypeParameter parameter : owner.typeParameters()) {
      typeSystem.declareExisting(
          parameter.name(),
          methodTypes.get(parameter.name()),
          parameter.nameSpan(),
          context.declarationSymbols.get(parameter));
    }
    for (Syntax.TypeParameter parameter : method.typeParameters()) {
      typeSystem.declareExisting(
          parameter.name(),
          methodTypes.get(parameter.name()),
          parameter.nameSpan(),
          methodSymbols.get(parameter.name()));
    }
    typeSystem.declareSelf(typeSystem.interfaceSelfType(owner), owner.nameSpan());
    for (Syntax.Parameter parameter : method.parameters()) {
      SemanticType type = typeSystem.resolveDeclarationType(parameter.type(), method, methodTypes);
      typeSystem.declareExisting(
          parameter.name(), type, parameter.nameSpan(), context.declarationSymbols.get(parameter));
    }
    analyzeStatements(method.body().orElseThrow());
    if (!context.expectedReturnType.equals(SemanticType.VOID)
        && !TypeSystem.definitelyExits(method.body().orElseThrow())) {
      context.diagnostics.error(
          INVALID_CONTROL,
          "default method '"
              + method.name()
              + "' must return "
              + context.expectedReturnType.displayName(),
          method.span());
    }
    typeSystem.popScope();
    context.expectedReturnType = previousReturn;
    context.currentCallable = previousCallable;
  }

  private SymbolId defaultMethodId(Syntax.InterfaceMethodDecl method) {
    return new SymbolId(context.declarationSymbols.get(method).value() + "/default");
  }

  private void registerBounds(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> declaredTypes) {
    for (Syntax.TypeParameter parameter : parameters) {
      if (parameter.upperBound().isEmpty()) continue;
      Syntax.TypeRef boundSyntax = parameter.upperBound().orElseThrow();
      SemanticType bound = typeSystem.resolveType(boundSyntax, declaredTypes);
      context.typeParameterBounds.put(declaredTypes.get(parameter.name()).identity(), bound);
      typeSystem.validateType(boundSyntax, false);
      Syntax.AggregateDecl aggregate = typeSystem.resolveAggregate(bound);
      boolean classBound = aggregate != null && aggregate.kind() == Syntax.AggregateKind.CLASS;
      boolean typeParameterBound = bound.kind() == SemanticType.Kind.TYPE_PARAMETER;
      if (bound.isNullable()
          || typeSystem.resolveInterface(bound) == null && !classBound && !typeParameterBound) {
        context.diagnostics.error(
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
          context.diagnostics.error(
              TYPE_MISMATCH,
              "cyclic type parameter bound",
              parameter.upperBound().map(Syntax.TypeRef::span).orElse(parameter.nameSpan()));
          break;
        }
        current = context.typeParameterBounds.get(current.identity());
      }
    }
  }

  private void validateInterfaceGraphAndConformances() {
    Set<Syntax.InterfaceDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.InterfaceDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.InterfaceDecl declaration : context.declarations.interfaces()) {
      validateInterfaceCycle(declaration, visiting, visited);
    }
    for (Syntax.Program program : context.programs) {
      context.currentProgram = program;
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
    Syntax.Program previous = context.currentProgram;
    context.currentProgram = context.declarations.owner(declaration);
    Map<String, SemanticType> parameters = typeSystem.interfaceTypeParameters(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      Syntax.InterfaceDecl parent =
          typeSystem.resolveInterface(typeSystem.resolveType(parentRef, parameters));
      if (parent != null) validateInterfaceCycle(parent, visiting, visited);
    }
    context.currentProgram = previous;
    visiting.remove(declaration);
    visited.add(declaration);
  }

  private void validateAggregateConformance(Syntax.AggregateDecl declaration) {
    context.activeTypeParameters = typeSystem.aggregateTypeParameters(declaration);
    context.activeTypeParameterSymbols =
        typeSystem.typeParameterSymbols(declaration.typeParameters());
    registerBounds(declaration.typeParameters(), context.activeTypeParameters);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : declaration.implementedInterfaces()) {
      typeSystem.validateType(interfaceRef, false);
      SemanticType interfaceType =
          typeSystem.resolveType(interfaceRef, context.activeTypeParameters);
      Syntax.InterfaceDecl interfaceDecl = typeSystem.resolveInterface(interfaceType);
      if (interfaceDecl == null) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            TypeSystem.aggregateKeyword(declaration) + " may implement interfaces only",
            interfaceRef.span());
        continue;
      }
      collectConformances(interfaceDecl, interfaceType, conformances, interfaceRef.span());
    }
    Map<String, InterfaceRequirement> requirements = new LinkedHashMap<>();
    for (SemanticType conformance : conformances.values()) {
      Syntax.InterfaceDecl interfaceDecl = typeSystem.resolveInterface(conformance);
      if (interfaceDecl == null) continue;
      for (InterfaceRequirement requirement : directRequirements(interfaceDecl, conformance)) {
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
                    this::requirementShape,
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
      SymbolId implementation = witness == null ? null : context.declarationSymbols.get(witness);
      if (implementation == null) {
        List<InterfaceRequirement> active = mostSpecificRequirements(group);
        List<InterfaceRequirement> defaults =
            active.stream().filter(value -> value.method().body().isPresent()).toList();
        if (defaults.size() == 1) {
          implementation = defaultMethodId(defaults.getFirst().method());
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
        Map<SymbolId, SymbolId> aggregateWitnesses =
            context.witnesses.computeIfAbsent(
                context.declarationSymbols.get(declaration), ignored -> new LinkedHashMap<>());
        for (InterfaceRequirement requirement : group) {
          aggregateWitnesses.put(context.declarationSymbols.get(requirement.method()), selected);
        }
      }
    }
    context.activeTypeParameters = Map.of();
    context.activeTypeParameterSymbols = Map.of();
  }

  void collectConformances(
      Syntax.InterfaceDecl declaration,
      SemanticType instance,
      Map<String, SemanticType> result,
      SourceSpan span) {
    SemanticType existing = result.putIfAbsent(instance.identity(), instance);
    if (existing != null) {
      if (!existing.equals(instance)) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "interface '" + declaration.name() + "' is inherited with conflicting type arguments",
            span);
      }
      return;
    }
    Map<String, SemanticType> substitutions = interfaceSubstitutions(declaration, instance);
    Map<String, SemanticType> parameters = typeSystem.interfaceTypeParameters(declaration);
    Syntax.Program previous = context.currentProgram;
    context.currentProgram = context.declarations.owner(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      SemanticType parent = typeSystem.resolveType(parentRef, parameters).substitute(substitutions);
      Syntax.InterfaceDecl parentDecl = typeSystem.resolveInterface(parent);
      if (parentDecl != null) collectConformances(parentDecl, parent, result, span);
    }
    context.currentProgram = previous;
  }

  List<InterfaceRequirement> directRequirements(
      Syntax.InterfaceDecl declaration, SemanticType instance) {
    Map<String, SemanticType> substitutions = interfaceSubstitutions(declaration, instance);
    Map<String, SemanticType> parameters = typeSystem.interfaceTypeParameters(declaration);
    return declaration.methods().stream()
        .map(
            method -> {
              Map<String, SemanticType> methodTypes =
                  typeSystem.withTypeParameters(
                      parameters,
                      method.typeParameters(),
                      context.declarations.owner(declaration),
                      "interface-method/" + method.name());
              List<ParameterInfo> methodParameters =
                  method.parameters().stream()
                      .map(
                          parameter ->
                              new ParameterInfo(
                                  parameter.name(),
                                  typeSystem
                                      .resolveDeclarationType(parameter.type(), method, methodTypes)
                                      .substitute(substitutions)))
                      .toList();
              SemanticType result =
                  typeSystem
                      .resolveDeclarationType(method.returnType(), method, methodTypes)
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
                  context.declarationSymbols.get(method).value(),
                  signature);
            })
        .toList();
  }

  private boolean witnessMatches(Syntax.FunctionDecl witness, InterfaceRequirement requirement) {
    if (witness.typeParameters().size() != requirement.method().typeParameters().size()
        || witness.parameters().size() != requirement.parameters().size()) return false;
    Map<String, SemanticType> witnessTypes =
        typeSystem.typeParameters(witness, typeSystem.ownerOf(witness));
    Symbol requiredSymbol =
        context.symbols.get(context.declarationSymbols.get(requirement.method()));
    Symbol witnessSymbol = context.symbols.get(context.declarationSymbols.get(witness));
    Map<String, SemanticType> requiredSubstitutions =
        interfaceSubstitutions(requirement.owner(), requirement.receiver());
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
    return canonicalType(functionReturnType(witness, witnessTypes), witnessParameters)
        .equals(canonicalType(requirement.result(), requiredParameters));
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

  Map<String, SemanticType> interfaceSubstitutions(
      Syntax.InterfaceDecl declaration, SemanticType instance) {
    Map<String, SemanticType> parameterTypes = typeSystem.interfaceTypeParameters(declaration);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(declaration.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = parameterTypes.get(declaration.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return Map.copyOf(result);
  }

  private void analyzeFunction(Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    if (function.kind() == Syntax.FunctionKind.EXTENSION && function.parameters().isEmpty()) {
      context.diagnostics.error(
          INVALID_CALL, "extension function requires a receiver parameter", function.nameSpan());
    }
    context.activeTypeParameters = typeSystem.typeParameters(function, owner);
    context.activeTypeParameterSymbols = typeSystem.typeParameterSymbols(function, owner);
    if (owner != null) registerBounds(owner.typeParameters(), context.activeTypeParameters);
    registerBounds(function.typeParameters(), context.activeTypeParameters);
    function.returnType().ifPresent(type -> typeSystem.validateType(type, true));
    context.expectedReturnType = functionReturnType(function, context.activeTypeParameters);
    context.implicitSelfReturn = owner != null && function.returnType().isEmpty();
    context.currentAggregate = owner;
    if (function.visibility() == Syntax.Visibility.PUBLIC
        && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC)) {
      function.returnType().ifPresent(typeSystem::validatePublicType);
      function.parameters().forEach(parameter -> typeSystem.validatePublicType(parameter.type()));
    }
    context.currentCallable = context.declarationSymbols.get(function);
    context.flowScopes.clear();
    context.assignedLocals.clear();
    context.capturedLocals.clear();
    context.reportedMutableCaptures.clear();
    context.lambdaLocals.clear();
    typeSystem.pushScope(function.span());
    if (owner != null) {
      for (Syntax.TypeParameter parameter : owner.typeParameters()) {
        typeSystem.declareExisting(
            parameter.name(),
            context.activeTypeParameters.get(parameter.name()),
            parameter.nameSpan(),
            context.declarationSymbols.get(parameter));
      }
    }
    for (Syntax.TypeParameter parameter : function.typeParameters()) {
      typeSystem.declareExisting(
          parameter.name(),
          context.activeTypeParameters.get(parameter.name()),
          parameter.nameSpan(),
          context.declarationSymbols.get(parameter));
    }
    if (owner != null) {
      typeSystem.declareSelf(typeSystem.aggregateSelfType(owner), owner.nameSpan());
      for (AggregateView view : typeSystem.aggregateViews(typeSystem.aggregateSelfType(owner))) {
        Map<String, SemanticType> substitutions =
            typeSystem.aggregateSubstitutions(view.declaration(), view.type());
        for (Syntax.FieldDecl field : view.declaration().fields()) {
          if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
            continue;
          typeSystem.declareExisting(
              field.name(),
              typeSystem
                  .resolveDeclarationType(
                      field.type(), field, typeSystem.aggregateTypeParameters(view.declaration()))
                  .substitute(substitutions),
              field.nameSpan(),
              context.declarationSymbols.get(field));
        }
      }
    }
    for (Syntax.Parameter parameter : function.parameters()) {
      typeSystem.validateReferenceCapableType(parameter.type());
      Symbol symbol =
          typeSystem.register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              typeSystem.resolveType(parameter.type(), context.activeTypeParameters),
              parameter.nameSpan(),
              context.declarationSymbols.get(function),
              List.of(),
              List.of());
      typeSystem.declareExisting(
          parameter.name(),
          typeSystem.resolveType(parameter.type(), context.activeTypeParameters),
          parameter.nameSpan(),
          symbol.id());
    }
    analyzeStatements(function.body());
    if (!context.expectedReturnType.equals(SemanticType.VOID)
        && !context.implicitSelfReturn
        && !TypeSystem.definitelyExits(function.body())) {
      context.diagnostics.error(
          INVALID_CONTROL,
          "function '"
              + function.name()
              + "' must return "
              + context.expectedReturnType.displayName(),
          function.span());
    }
    typeSystem.popScope();
    context.currentCallable = null;
    context.currentAggregate = null;
    context.implicitSelfReturn = false;
    context.activeTypeParameters = Map.of();
    context.activeTypeParameterSymbols = Map.of();
  }

  private void analyzeConstructor(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    context.activeTypeParameters = typeSystem.aggregateTypeParameters(owner);
    context.activeTypeParameterSymbols = typeSystem.typeParameterSymbols(owner.typeParameters());
    registerBounds(owner.typeParameters(), context.activeTypeParameters);
    context.expectedReturnType = SemanticType.VOID;
    context.implicitSelfReturn = false;
    context.currentAggregate = owner;
    context.currentCallable = context.declarationSymbols.get(constructor);
    context.flowScopes.clear();
    context.assignedLocals.clear();
    context.capturedLocals.clear();
    context.reportedMutableCaptures.clear();
    context.lambdaLocals.clear();
    typeSystem.pushScope(constructor.span());
    for (Syntax.TypeParameter parameter : owner.typeParameters()) {
      typeSystem.declareExisting(
          parameter.name(),
          context.activeTypeParameters.get(parameter.name()),
          parameter.nameSpan(),
          context.declarationSymbols.get(parameter));
    }
    typeSystem.declareSelf(typeSystem.aggregateSelfType(owner), owner.nameSpan());
    for (AggregateView view : typeSystem.aggregateViews(typeSystem.aggregateSelfType(owner))) {
      Map<String, SemanticType> substitutions =
          typeSystem.aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FieldDecl field : view.declaration().fields()) {
        if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
          continue;
        typeSystem.declareExisting(
            field.name(),
            typeSystem
                .resolveDeclarationType(
                    field.type(), field, typeSystem.aggregateTypeParameters(view.declaration()))
                .substitute(substitutions),
            field.nameSpan(),
            context.declarationSymbols.get(field));
      }
    }
    typeSystem.pushScope(constructor.span());
    for (Syntax.Parameter parameter : constructor.parameters()) {
      typeSystem.validateReferenceCapableType(parameter.type());
      Symbol symbol =
          typeSystem.register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              typeSystem.resolveType(parameter.type(), context.activeTypeParameters),
              parameter.nameSpan(),
              context.currentCallable,
              List.of(),
              List.of());
      typeSystem.declareExisting(
          parameter.name(), symbol.type(), parameter.nameSpan(), symbol.id());
    }
    analyzeSuperCall(constructor, owner);
    analyzeStatements(constructor.body());
    Map<SymbolId, String> fields = new LinkedHashMap<>();
    for (AggregateView view : typeSystem.aggregateViews(typeSystem.aggregateSelfType(owner))) {
      for (Syntax.FieldDecl field : view.declaration().fields()) {
        SymbolId fieldId = context.declarationSymbols.get(field);
        fields.put(fieldId, field.name());
      }
    }
    Set<SymbolId> inheritedFields = new HashSet<>(fields.keySet());
    owner.fields().stream().map(context.declarationSymbols::get).forEach(inheritedFields::remove);
    List<ConstructorFlowAnalyzer.RequiredField> requiredFields =
        owner.fields().stream()
            .map(
                field ->
                    new ConstructorFlowAnalyzer.RequiredField(
                        context.declarationSymbols.get(field), field.name(), field.nameSpan()))
            .toList();
    new ConstructorFlowAnalyzer(context.bindings, context.symbols)
        .analyze(
            new ConstructorFlowAnalyzer.Input(constructor, fields, inheritedFields, requiredFields))
        .diagnostics()
        .forEach(context.diagnostics::report);
    typeSystem.popScope();
    typeSystem.popScope();
    context.currentCallable = null;
    context.currentAggregate = null;
    context.activeTypeParameters = Map.of();
    context.activeTypeParameterSymbols = Map.of();
  }

  private void analyzeSuperCall(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    Optional<SemanticType> parentType =
        typeSystem.directParentType(owner, typeSystem.aggregateSelfType(owner));
    if (parentType.isEmpty()) {
      if (constructor.superCall().isPresent()) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "root class constructor cannot call super",
            constructor.superCall().orElseThrow().span());
        typeSystem.analyzeArguments(constructor.superCall().orElseThrow().arguments());
      }
      return;
    }
    if (constructor.superCall().isEmpty()) {
      context.diagnostics.error(
          INVALID_CONTROL, "subclass constructor must call super", constructor.nameSpan());
      return;
    }
    SemanticType parent = parentType.orElseThrow();
    Syntax.AggregateDecl declaration = typeSystem.resolveAggregate(parent);
    if (declaration == null) {
      typeSystem.analyzeArguments(constructor.superCall().orElseThrow().arguments());
      return;
    }
    Syntax.SuperCall call = constructor.superCall().orElseThrow();
    Syntax.Call syntaxCall =
        new Syntax.Call(new Syntax.Name("super", call.span()), call.arguments(), call.span());
    OverloadResolver.Candidate selected =
        typeSystem.resolveConstructor(declaration, parent, syntaxCall, call.span());
    if (selected == null) return;
    SymbolId target =
        selected.target() instanceof Syntax.ConstructorDecl parentConstructor
            ? context.declarationSymbols.get(parentConstructor)
            : context.declarationSymbols.get(declaration);
    typeSystem.recordCall(
        syntaxCall,
        call.span(),
        ResolvedCall.Kind.SUPER,
        target,
        selected.parameters(),
        List.of(),
        SemanticType.VOID);
  }

  void analyzeStatements(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      context.guard.checkpoint();
      analyzeStatement(statement);
    }
  }

  void analyzeStatement(Syntax.Statement statement) {
    switch (statement) {
      case Syntax.VariableDecl variable -> {
        SemanticType requested =
            variable
                .type()
                .map(
                    type -> {
                      typeSystem.validateReferenceCapableType(type);
                      return typeSystem.resolveType(type, context.activeTypeParameters);
                    })
                .orElse(null);
        SemanticType actual = typeOf(variable.initializer(), requested);
        if (requested != null)
          typeSystem.requireAssignable(requested, actual, variable.initializer().span());
        if (requested == null && TypeSystem.containsDynamic(actual)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "cannot infer variable type from initializer",
              variable.initializer().span());
        }
        SemanticType declaredType = requested == null ? actual : requested;
        Symbol symbol =
            typeSystem.register(
                variable,
                variable.name(),
                SymbolKind.LOCAL_VARIABLE,
                declaredType,
                variable.nameSpan(),
                context.currentCallable,
                List.of(),
                List.of());
        typeSystem.declareExisting(variable.name(), declaredType, variable.nameSpan(), symbol.id());
        if (declaredType.isReference()) {
          FlowScopes.ScopedSymbol scoped = typeSystem.findScoped(variable.name());
          if (scoped != null && scoped.id().equals(symbol.id())) {
            updateReferenceLifetime(scoped, variable.initializer());
          }
        }
        if (!context.lambdaLocals.isEmpty()) context.lambdaLocals.getFirst().add(symbol.id());
      }
      case Syntax.Assignment assignment -> {
        SemanticType target = assignmentTargetType(assignment.target());
        if (assignment.target() instanceof Syntax.Name name
            && context.currentAggregate != null
            && context.currentAggregate.kind() == Syntax.AggregateKind.VALUE) {
          FlowScopes.ScopedSymbol scoped = typeSystem.findScoped(name.value());
          if (scoped != null && scopedSymbol(scoped).kind() == SymbolKind.FIELD) {
            context.diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", name.span());
          }
        }
        SemanticType value = typeOf(assignment.value(), target);
        typeSystem.requireAssignable(target, value, assignment.value().span());
        if (assignment.target() instanceof Syntax.Name name) {
          FlowScopes.ScopedSymbol scoped = typeSystem.findScoped(name.value());
          if (scoped != null && target.isReference() && value.isReference()) {
            updateReferenceLifetime(scoped, assignment.value());
          }
          if (scoped != null
              && (scopedSymbol(scoped).kind() == SymbolKind.LOCAL_VARIABLE
                  || scopedSymbol(scoped).kind() == SymbolKind.PARAMETER)) {
            if (!context.lambdaLocals.isEmpty()
                && !context.lambdaLocals.getFirst().contains(scoped.id())) {
              context.capturedLocals.add(scoped.id());
              reportMutableCapture(scoped.id(), name.span());
            }
            context.assignedLocals.add(scoped.id());
            flowWriteCollectors.forEach(writes -> writes.add(scoped.id()));
            if (context.capturedLocals.contains(scoped.id())) {
              reportMutableCapture(scoped.id(), name.span());
            }
          }
          typeSystem.invalidateNarrowing(name.value());
        }
      }
      case Syntax.ExpressionStatement expression -> typeOf(expression.expression(), null);
      case Syntax.IfStatement ifStatement -> {
        typeSystem.requireType(
            SemanticType.BOOLEAN,
            typeOf(ifStatement.condition(), SemanticType.BOOLEAN),
            ifStatement.condition().span());
        FlowScopes.FlowState incoming = context.flowScopes.snapshot();
        FlowScopes.FlowState thenFlow =
            typeSystem.analyzeBranch(
                ifStatement.thenBody(),
                typeSystem.narrowingsFor(ifStatement.condition(), true),
                incoming);
        FlowScopes.FlowState elseFlow =
            typeSystem.analyzeBranch(
                ifStatement.elseBody(),
                typeSystem.narrowingsFor(ifStatement.condition(), false),
                incoming);
        boolean thenReturns = TypeSystem.definitelyExits(ifStatement.thenBody());
        boolean elseReturns = TypeSystem.definitelyExits(ifStatement.elseBody());
        if (thenReturns && !elseReturns) {
          typeSystem.replaceFlow(elseFlow);
        } else if (elseReturns && !thenReturns) {
          typeSystem.replaceFlow(thenFlow);
        } else if (!thenReturns && !elseReturns) {
          typeSystem.replaceFlow(typeSystem.mergeFlows(incoming, thenFlow, elseFlow));
        } else {
          typeSystem.replaceFlow(incoming);
        }
      }
      case Syntax.ConditionalForStatement loop -> {
        typeSystem.requireType(
            SemanticType.BOOLEAN,
            typeOf(loop.condition(), SemanticType.BOOLEAN),
            loop.condition().span());
        FlowScopes.FlowState incoming = context.flowScopes.snapshot();
        typeSystem.pushScope(loop.span());
        typeSystem.applyNarrowings(typeSystem.narrowingsFor(loop.condition(), true));
        context.controls.addFirst(ControlContext.loop());
        analyzeStatements(loop.body());
        context.controls.removeFirst();
        typeSystem.popScope();
        FlowScopes.FlowState bodyFlow = context.flowScopes.snapshot();
        typeSystem.replaceFlow(typeSystem.mergeFlows(incoming, incoming, bodyFlow));
      }
      case Syntax.ForStatement forStatement -> {
        SemanticType iterableType = typeOf(forStatement.iterable(), null);
        Optional<dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable> builtinIterable =
            context.builtins.resolveIterable(iterableType);
        Optional<ResolvedIteration> interfaceIteration = resolveInterfaceIteration(iterableType);
        builtinIterable.ifPresent(
            capability ->
                context.iterations.put(
                    forStatement.iterable().span(),
                    new ResolvedIteration(
                        capability.elementType(),
                        new ResolvedIteration.Strategy.Builtin(capability.intrinsic()))));
        interfaceIteration.ifPresent(
            resolution -> context.iterations.put(forStatement.iterable().span(), resolution));
        Optional<SemanticType> elementType =
            builtinIterable
                .map(dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable::elementType)
                .or(() -> interfaceIteration.map(ResolvedIteration::elementType));
        if (elementType.isEmpty()) {
          context.diagnostics.error(
              TYPE_MISMATCH, "for requires an iterable value", forStatement.iterable().span());
        }
        SemanticType variableType;
        if (forStatement.variableType().isPresent()) {
          Syntax.TypeRef explicitType = forStatement.variableType().orElseThrow();
          typeSystem.validateType(explicitType, false);
          variableType = typeSystem.resolveType(explicitType, context.activeTypeParameters);
          elementType.ifPresent(
              itemType ->
                  typeSystem.requireAssignable(
                      variableType, itemType, forStatement.variableNameSpan()));
        } else {
          if (elementType.isEmpty()) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "cannot infer loop variable type from " + iterableType.displayName(),
                forStatement.variableNameSpan());
            variableType = SemanticType.DYNAMIC;
          } else {
            variableType = elementType.orElseThrow();
          }
        }
        typeSystem.pushScope(forStatement.span());
        Symbol symbol =
            typeSystem.register(
                forStatement,
                forStatement.variableName(),
                SymbolKind.LOCAL_VARIABLE,
                variableType,
                forStatement.variableNameSpan(),
                context.currentCallable,
                List.of(),
                List.of());
        typeSystem.declareExisting(
            forStatement.variableName(),
            variableType,
            forStatement.variableNameSpan(),
            symbol.id());
        if (!context.lambdaLocals.isEmpty()) context.lambdaLocals.getFirst().add(symbol.id());
        forStatement
            .index()
            .ifPresent(
                index -> {
                  Symbol indexSymbol =
                      typeSystem.register(
                          index,
                          index.name(),
                          SymbolKind.LOCAL_VARIABLE,
                          SemanticType.INTEGER,
                          index.nameSpan(),
                          context.currentCallable,
                          List.of(),
                          List.of());
                  typeSystem.declareExisting(
                      index.name(), SemanticType.INTEGER, index.nameSpan(), indexSymbol.id());
                  if (!context.lambdaLocals.isEmpty())
                    context.lambdaLocals.getFirst().add(indexSymbol.id());
                });
        context.controls.addFirst(ControlContext.loop());
        analyzeStatements(forStatement.body());
        context.controls.removeFirst();
        typeSystem.popScope();
      }
      case Syntax.TryStatement tried -> analyzeTry(tried);
      case Syntax.ThrowStatement thrown -> {
        SemanticType type = typeOf(thrown.exception(), SemanticType.EXCEPTION);
        if (!typeSystem.isAssignable(SemanticType.EXCEPTION, type)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "throw requires an Exception but found " + type.displayName(),
              thrown.exception().span());
        }
      }
      case Syntax.ReturnStatement returnStatement -> {
        if (context.implicitSelfReturn) {
          if (returnStatement.value() != null) {
            typeOf(returnStatement.value(), context.expectedReturnType);
            context.diagnostics.error(
                TYPE_MISMATCH,
                "fluent methods return their receiver; use a bare return",
                returnStatement.span());
          }
        } else {
          SemanticType actual =
              returnStatement.value() == null
                  ? SemanticType.VOID
                  : typeOf(returnStatement.value(), context.expectedReturnType);
          typeSystem.requireAssignable(context.expectedReturnType, actual, returnStatement.span());
        }
      }
      case Syntax.BreakStatement breakStatement -> analyzeBreak(breakStatement);
      case Syntax.ContinueStatement continueStatement ->
          typeSystem.validateContinue(continueStatement.span());
    }
  }

  private void analyzeTry(Syntax.TryStatement tried) {
    FlowScopes.FlowState incoming = context.flowScopes.snapshot();
    List<FlowScopes.FlowState> completing = new ArrayList<>();
    FlowScopes.FlowState tryFlow = typeSystem.analyzeBranch(tried.body(), Map.of(), incoming);
    if (!TypeSystem.definitelyExits(tried.body())) completing.add(tryFlow);
    List<SemanticType> preceding = new ArrayList<>();
    for (Syntax.CatchClause clause : tried.catches()) {
      typeSystem.validateType(clause.type(), false);
      SemanticType type = typeSystem.resolveType(clause.type(), context.activeTypeParameters);
      if (!typeSystem.isAssignable(SemanticType.EXCEPTION, type)) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "catch requires an Exception type but found " + type.displayName(),
            clause.type().span());
      }
      if (preceding.stream().anyMatch(previous -> typeSystem.isAssignable(previous, type))) {
        context.diagnostics.error(
            INVALID_CONTROL,
            "catch type " + type.displayName() + " is already covered by an earlier catch",
            clause.type().span());
      }
      preceding.add(type);
      typeSystem.replaceFlow(incoming);
      typeSystem.pushScope(clause.span());
      Symbol symbol =
          typeSystem.register(
              clause,
              clause.name(),
              SymbolKind.LOCAL_VARIABLE,
              type,
              clause.nameSpan(),
              context.currentCallable,
              List.of(),
              List.of());
      typeSystem.declareExisting(clause.name(), type, clause.nameSpan(), symbol.id());
      if (!context.lambdaLocals.isEmpty()) context.lambdaLocals.getFirst().add(symbol.id());
      analyzeStatements(clause.body());
      typeSystem.popScope();
      if (!TypeSystem.definitelyExits(clause.body())) completing.add(context.flowScopes.snapshot());
    }
    FlowScopes.FlowState normal = mergeCompletingFlows(incoming, completing);
    typeSystem.replaceFlow(normal);
    if (tried.finallyClause().isPresent()) {
      Set<SymbolId> finalWrites = new HashSet<>();
      flowWriteCollectors.addFirst(finalWrites);
      FlowScopes.FlowState finalFlow;
      try {
        finalFlow =
            typeSystem.analyzeBranch(
                tried.finallyClause().orElseThrow().body(), Map.of(), incoming);
      } finally {
        flowWriteCollectors.removeFirst();
      }
      Map<SymbolId, SemanticType> types = new LinkedHashMap<>(normal.types());
      for (Map.Entry<SymbolId, SemanticType> entry : finalFlow.types().entrySet()) {
        if (finalWrites.contains(entry.getKey())
            || !entry.getValue().equals(incoming.types().get(entry.getKey()))) {
          types.put(entry.getKey(), entry.getValue());
        }
      }
      Map<SymbolId, LexicalLifetime> lifetimes = new LinkedHashMap<>(normal.referenceLifetimes());
      Set<SymbolId> lifetimeSymbols = new HashSet<>(incoming.referenceLifetimes().keySet());
      lifetimeSymbols.addAll(finalFlow.referenceLifetimes().keySet());
      for (SymbolId symbol : lifetimeSymbols) {
        LexicalLifetime before = incoming.referenceLifetimes().get(symbol);
        LexicalLifetime after = finalFlow.referenceLifetimes().get(symbol);
        if (!finalWrites.contains(symbol) && Objects.equals(before, after)) continue;
        if (after == null) lifetimes.remove(symbol);
        else lifetimes.put(symbol, after);
      }
      typeSystem.replaceFlow(new FlowScopes.FlowState(types, lifetimes));
    }
  }

  private FlowScopes.FlowState mergeCompletingFlows(
      FlowScopes.FlowState incoming, List<FlowScopes.FlowState> flows) {
    if (flows.isEmpty()) return incoming;
    FlowScopes.FlowState result = flows.getFirst();
    for (int index = 1; index < flows.size(); index++) {
      result = typeSystem.mergeFlows(incoming, result, flows.get(index));
    }
    return result;
  }

  private void updateReferenceLifetime(
      FlowScopes.ScopedSymbol destination, Syntax.Expression value) {
    LexicalLifetime sourceLifetime = referenceLifetime(value);
    LexicalLifetime destinationLifetime = context.flowScopes.storageLifetime(destination);
    if (!sourceLifetime.outlives(destinationLifetime)) {
      context.diagnostics.error(
          INVALID_CONTROL, "reference cannot outlive the addressed storage location", value.span());
      context.flowScopes.updateReferenceLifetime(destination, LexicalLifetime.unusable());
      return;
    }
    context.flowScopes.updateReferenceLifetime(destination, sourceLifetime);
  }
}
