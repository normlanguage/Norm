package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.ExceptionAbi;
import dev.w0fv1.norm.semantic.AnnotationIndex;
import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
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

final class Analyzer extends AnalyzerAnnotations {
  private final Deque<Set<SymbolId>> flowWriteCollectors = new ArrayDeque<>();

  Analyzer(
      List<Syntax.Program> programs,
      Syntax.Program entryProgram,
      DiagnosticBag diagnostics,
      boolean requireEntryPoint,
      Set<DocumentId> exportedSources,
      CompilationGuard guard,
      Map<SourceSpan, SemanticContribution> reusableDeclarations,
      int minimumBodySymbolId,
      Set<DocumentId> moduleEvaluationDocuments,
      CompilationScope scope) {
    super(
        programs,
        entryProgram,
        diagnostics,
        requireEntryPoint,
        exportedSources,
        guard,
        reusableDeclarations,
        minimumBodySymbolId,
        moduleEvaluationDocuments,
        scope);
  }

  FrontendAnalysis analyze(boolean resolveProgram) {
    guard.checkpoint();
    collectDeclarations();
    nextSymbolId = Math.max(nextSymbolId, minimumBodySymbolId);
    validateImports();
    createFileScopes();
    validateAnnotationSchemas();
    validateClassHierarchy();
    currentProgram = entryProgram;
    Syntax.FunctionDecl main =
        entryProgram.functions().stream()
            .filter(function -> function.name().equals("main"))
            .findFirst()
            .orElse(null);
    if (main == null && requireEntryPoint) {
      diagnostics.error(MISSING_MAIN, "program must declare 'main()'", syntax.span());
    } else if (main != null
        && (!functionReturnType(main, functionTypeParameters(main)).equals(SemanticType.VOID)
            || !main.typeParameters().isEmpty()
            || !main.parameters().isEmpty())) {
      diagnostics.error(TYPE_MISMATCH, "entry function must be 'main()'", main.span());
    }

    for (Syntax.Program program : programs) {
      guard.checkpoint();
      currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        if (reuse(enumDecl.span())) continue;
        validateTypeParameterNames(enumDecl.typeParameters());
        validateEnum(enumDecl);
      }
      for (Syntax.InterfaceDecl interfaceDecl : program.interfaces()) {
        if (reuse(interfaceDecl.span())) continue;
        validateTypeParameterNames(interfaceDecl.typeParameters());
        validateInterface(interfaceDecl);
      }
      for (Syntax.FunctionDecl function : program.functions()) {
        if (reuse(function.span())) continue;
        analyzeFunction(function, null);
      }
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        if (reuse(aggregateDecl.span())) continue;
        validateTypeParameterNames(aggregateDecl.typeParameters());
        validateFields(aggregateDecl);
        for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
          analyzeConstructor(constructor, aggregateDecl);
        }
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          analyzeFunction(method, aggregateDecl);
        }
      }
    }
    validateAnnotationApplications();
    validateInterfaceGraphAndConformances();
    List<dev.w0fv1.norm.diagnostic.Diagnostic> snapshot = diagnostics.snapshot();
    SemanticModel semanticModel =
        new SemanticModel(
            syntax.span().source(),
            syntax,
            symbols,
            bindings,
            semanticTypes,
            resolvedCalls,
            functionReferenceTypeArguments,
            iterations,
            indexes,
            members,
            aliasTargets,
            callableGroups(),
            witnesses,
            aggregateParents,
            methodOverrides,
            typeSymbols,
            interfaceParentTypes(),
            new AnnotationIndex(annotationSchemas, annotationApplications),
            flowScopes.semanticScopes(),
            snapshot,
            importableSymbols(),
            scope);
    Optional<dev.w0fv1.norm.bound.BoundProgram> boundProgram =
        !resolveProgram
                || snapshot.stream()
                    .anyMatch(
                        diagnostic ->
                            diagnostic.severity()
                                == dev.w0fv1.norm.diagnostic.DiagnosticSeverity.ERROR)
            ? Optional.empty()
            : Optional.of(new Binder(programs, semanticModel).bind(main));
    return new FrontendAnalysis(
        new AnalysisResult(semanticModel, Optional.ofNullable(main), snapshot), boundProgram);
  }

  private boolean reuse(SourceSpan root) {
    SemanticContribution contribution = reusableDeclarations.get(root);
    if (contribution == null) return false;
    symbols.putAll(contribution.symbols());
    bindings.putAll(contribution.bindings());
    semanticTypes.putAll(contribution.expressionTypes());
    resolvedCalls.putAll(contribution.resolvedCalls());
    functionReferenceTypeArguments.putAll(contribution.functionReferenceTypeArguments());
    iterations.putAll(contribution.iterations());
    indexes.putAll(contribution.indexes());
    contribution.scopes().forEach(flowScopes::addSemanticScope);
    return true;
  }

  private void collectDeclarations() {
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        if (!declarations.addInterface(program, declaration)
            || builtins.isType(declaration.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "type '" + declaration.name() + "' is already declared",
              declaration.span());
        }
        Symbol type =
            register(
                declaration,
                declaration.name(),
                SymbolKind.INTERFACE,
                sourceType(declaration.name(), List.of()),
                declaration.nameSpan(),
                null,
                symbolTypeParameters(
                    declaration.typeParameters(), interfaceTypeParameters(declaration)),
                List.of());
        typeSymbols.putIfAbsent(type.type().identity(), type.id());
        registerTypeParameters(
            declaration.typeParameters(), type.id(), interfaceTypeParameters(declaration));
      }
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        if (!declarations.addEnum(program, enumDecl)
            || resolveInterface(enumDecl.name()) != null
            || builtins.isType(enumDecl.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "type '" + enumDecl.name() + "' is already declared",
              enumDecl.span());
        }
        Symbol type =
            register(
                enumDecl,
                enumDecl.name(),
                SymbolKind.TYPE,
                sourceType(enumDecl.name(), List.of()),
                enumDecl.nameSpan(),
                null,
                symbolTypeParameters(enumDecl.typeParameters(), enumTypeParameters(enumDecl)),
                List.of());
        typeSymbols.putIfAbsent(type.type().identity(), type.id());
        registerTypeParameters(enumDecl.typeParameters(), type.id(), enumTypeParameters(enumDecl));
      }
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.AnnotationDecl declaration : program.annotationDeclarations()) {
        if (!declarations.addAnnotation(program, declaration)
            || resolveEnum(declaration.name()) != null
            || resolveInterface(declaration.name()) != null
            || builtins.isType(declaration.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "type '" + declaration.name() + "' is already declared",
              declaration.span());
        }
        Symbol type =
            register(
                declaration,
                declaration.name(),
                SymbolKind.TYPE,
                sourceType(declaration.name(), List.of()),
                declaration.nameSpan(),
                null,
                List.of(),
                List.of());
        typeSymbols.putIfAbsent(type.type().identity(), type.id());
      }
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        if (!declarations.addAggregate(program, aggregateDecl)
            || resolveEnum(aggregateDecl.name()) != null
            || resolveInterface(aggregateDecl.name()) != null
            || resolveAnnotation(aggregateDecl.name()) != null
            || builtins.isType(aggregateDecl.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "type '" + aggregateDecl.name() + "' is already declared",
              aggregateDecl.span());
        }
        Symbol type =
            register(
                aggregateDecl,
                aggregateDecl.name(),
                SymbolKind.TYPE,
                sourceType(aggregateDecl.name(), List.of()),
                aggregateDecl.nameSpan(),
                null,
                symbolTypeParameters(
                    aggregateDecl.typeParameters(), aggregateTypeParameters(aggregateDecl)),
                List.of());
        typeSymbols.putIfAbsent(type.type().identity(), type.id());
        registerTypeParameters(
            aggregateDecl.typeParameters(), type.id(), aggregateTypeParameters(aggregateDecl));
      }
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        Symbol type = symbols.get(declarationSymbols.get(declaration));
        Set<String> signatures = new HashSet<>();
        for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
          String signature = interfaceMethodSignature(method);
          if (!signatures.add(signature)) {
            diagnostics.error(
                DUPLICATE_NAME,
                "interface method '" + method.name() + "' is already declared",
                method.span());
          }
          Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
          parameters =
              withTypeParameters(
                  parameters,
                  method.typeParameters(),
                  declarations.owner(declaration),
                  "interface-method/" + method.name());
          Symbol symbol =
              register(
                  method,
                  method.name(),
                  SymbolKind.INTERFACE_METHOD,
                  resolveDeclarationType(method.returnType(), method, parameters),
                  method.nameSpan(),
                  type.id(),
                  symbolTypeParameters(method.typeParameters(), parameters),
                  parameters(method.parameters(), Map.of(), parameters));
          registerTypeParameters(method.typeParameters(), symbol.id(), parameters);
          for (Syntax.Parameter parameter : method.parameters()) {
            register(
                parameter,
                parameter.name(),
                SymbolKind.PARAMETER,
                resolveDeclarationType(parameter.type(), method, parameters),
                parameter.nameSpan(),
                symbol.id(),
                List.of(),
                List.of());
          }
          addMember(type.id(), symbol.id());
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
        Symbol type = symbols.get(declarationSymbols.get(enumDecl));
        for (Syntax.EnumVariant variant : enumDecl.variants()) {
          Symbol value =
              register(
                  variant,
                  variant.name(),
                  SymbolKind.ENUM_VARIANT,
                  enumSelfType(enumDecl),
                  variant.nameSpan(),
                  type.id(),
                  symbolTypeParameters(enumDecl.typeParameters(), enumTypeParameters(enumDecl)),
                  parameters(variant.parameters(), Map.of(), enumTypeParameters(enumDecl)));
          addMember(type.id(), value.id());
        }
      }
      for (Syntax.AnnotationDecl declaration : program.annotationDeclarations()) {
        Symbol type = symbols.get(declarationSymbols.get(declaration));
        for (Syntax.AnnotationParameter parameter : declaration.parameters()) {
          Symbol symbol =
              register(
                  parameter,
                  parameter.name(),
                  SymbolKind.FIELD,
                  resolveDeclarationType(parameter.type(), parameter, Map.of()),
                  parameter.nameSpan(),
                  type.id(),
                  List.of(),
                  List.of());
          addMember(type.id(), symbol.id());
        }
      }
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        Symbol type = symbols.get(declarationSymbols.get(aggregateDecl));
        for (Syntax.FieldDecl field : aggregateDecl.fields()) {
          Symbol symbol =
              register(
                  field,
                  field.name(),
                  SymbolKind.FIELD,
                  resolveDeclarationType(
                      field.type(), field, aggregateTypeParameters(aggregateDecl)),
                  field.nameSpan(),
                  type.id(),
                  List.of(),
                  List.of());
          if (field.visibility() == Syntax.Visibility.PUBLIC) {
            addMember(type.id(), symbol.id());
          }
        }
        List<ParameterInfo> constructionParameters =
            aggregateDecl.constructors().isEmpty()
                ? fieldParameters(
                    aggregateDecl.fields(), Map.of(), aggregateTypeParameters(aggregateDecl))
                : parameters(
                    aggregateDecl.constructors().getFirst().parameters(),
                    Map.of(),
                    aggregateTypeParameters(aggregateDecl));
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
        symbols.put(type.id(), type);
        for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
          register(
              constructor,
              constructor.name(),
              SymbolKind.CONSTRUCTOR,
              SemanticType.VOID,
              constructor.nameSpan(),
              type.id(),
              List.of(),
              parameters(
                  constructor.parameters(), Map.of(), aggregateTypeParameters(aggregateDecl)));
        }
        if (aggregateDecl.kind() == Syntax.AggregateKind.CLASS) {
          SymbolId copyId = SymbolId.source(aggregateDecl.nameSpan().source().id(), nextSymbolId++);
          Symbol copy =
              new Symbol(
                  copyId,
                  "copy",
                  SymbolKind.METHOD,
                  aggregateSelfType(aggregateDecl),
                  Optional.empty(),
                  Optional.of(type.id()),
                  List.of(),
                  List.of(),
                  "Creates a new top-level object identity.");
          symbols.put(copyId, copy);
          addMember(type.id(), copyId);
          copyMethods.put(type.type().identity(), copyId);
        }
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          validateTypeParameterNames(method.typeParameters());
          Symbol symbol =
              register(
                  method,
                  method.name(),
                  SymbolKind.METHOD,
                  functionReturnType(method, typeParameters(method, aggregateDecl)),
                  method.nameSpan(),
                  type.id(),
                  symbolTypeParameters(
                      method.typeParameters(), typeParameters(method, aggregateDecl)),
                  parametersOf(method, Map.of()));
          registerTypeParameters(
              method.typeParameters(), symbol.id(), functionTypeParameters(method));
          if (method.visibility() == Syntax.Visibility.PUBLIC) {
            addMember(type.id(), symbol.id());
          }
        }
      }
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.FunctionDecl function : program.functions()) {
        validateTypeParameterNames(function.typeParameters());
        if (!declarations.addFunction(program, function)) {
          diagnostics.error(
              DUPLICATE_NAME,
              "function overload '" + function.name() + "' is already declared",
              function.span());
        }
        Symbol symbol =
            register(
                function,
                function.name(),
                SymbolKind.FUNCTION,
                functionReturnType(function, functionTypeParameters(function)),
                function.nameSpan(),
                null,
                symbolTypeParameters(function.typeParameters(), functionTypeParameters(function)),
                parametersOf(function, Map.of()));
        registerTypeParameters(
            function.typeParameters(), symbol.id(), functionTypeParameters(function));
      }
    }
  }

  private void validateImports() {
    for (Syntax.Program program : programs) {
      Set<String> localNames = new HashSet<>();
      program.enums().forEach(declaration -> localNames.add(declaration.name()));
      program.interfaces().forEach(declaration -> localNames.add(declaration.name()));
      program.annotationDeclarations().forEach(declaration -> localNames.add(declaration.name()));
      program.aggregates().forEach(declaration -> localNames.add(declaration.name()));
      program.functions().forEach(declaration -> localNames.add(declaration.name()));
      Set<String> importedNames = new HashSet<>();
      for (Syntax.ImportDecl imported : program.imports()) {
        if (!importedNames.add(imported.localName()) || localNames.contains(imported.localName())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "import name '" + imported.localName() + "' is already declared",
              imported.span());
        }
        List<Syntax.FunctionDecl> importedFunctions =
            declarations.functions(imported.qualifiedName());
        Object declaration = importedFunctions.isEmpty() ? null : importedFunctions.getFirst();
        if (declaration == null) declaration = declarations.declaration(imported.qualifiedName());
        if (declaration == null || !canImport(program, declaration)) {
          diagnostics.error(
              UNKNOWN_NAME,
              "cannot import inaccessible or unknown declaration '"
                  + imported.qualifiedName()
                  + "'",
              imported.span());
        } else if (imported.alias().isEmpty()) {
          bindings.put(imported.nameSpan(), declarationSymbols.get(declaration));
        } else {
          Symbol target = symbols.get(declarationSymbols.get(declaration));
          bindings.put(imported.nameSpan(), target.id());
          Symbol alias =
              register(
                  imported,
                  imported.localName(),
                  target.kind(),
                  target.type(),
                  imported.aliasSpan().orElseThrow(),
                  null,
                  target.typeParameters(),
                  target.parameters());
          importAliases.put(imported, alias.id());
          List<SymbolId> targets =
              importedFunctions == null || importedFunctions.isEmpty()
                  ? List.of(target.id())
                  : importedFunctions.stream()
                      .filter(candidate -> canImport(program, candidate))
                      .map(declarationSymbols::get)
                      .toList();
          aliasTargets.put(alias.id(), targets);
        }
      }
    }
  }

  private void createFileScopes() {
    for (Syntax.Program program : programs) {
      LinkedHashMap<SymbolId, SymbolId> visible = new LinkedHashMap<>();
      symbols.values().stream()
          .filter(symbol -> symbol.id().value().startsWith("builtin/"))
          .filter(symbol -> symbol.owner().isEmpty())
          .forEach(symbol -> visible.put(symbol.id(), symbol.id()));
      for (Syntax.Program candidate : programs) {
        boolean sameFile = candidate == program;
        boolean samePackage =
            candidate.packageName().equals(program.packageName())
                && scope.sameModule(program.span().source().id(), candidate.span().source().id());
        for (Syntax.EnumDecl declaration : candidate.enums()) {
          if (sameFile || samePackage && declaration.visibility() == Syntax.Visibility.PUBLIC) {
            SymbolId id = declarationSymbols.get(declaration);
            visible.put(id, id);
          }
        }
        for (Syntax.InterfaceDecl declaration : candidate.interfaces()) {
          if (sameFile || samePackage && declaration.visibility() == Syntax.Visibility.PUBLIC) {
            SymbolId id = declarationSymbols.get(declaration);
            visible.put(id, id);
          }
        }
        for (Syntax.AggregateDecl declaration : candidate.aggregates()) {
          if (sameFile || samePackage && declaration.visibility() == Syntax.Visibility.PUBLIC) {
            SymbolId id = declarationSymbols.get(declaration);
            visible.put(id, id);
          }
        }
        for (Syntax.AnnotationDecl declaration : candidate.annotationDeclarations()) {
          if (sameFile || samePackage && declaration.visibility() == Syntax.Visibility.PUBLIC) {
            SymbolId id = declarationSymbols.get(declaration);
            visible.put(id, id);
          }
        }
        for (Syntax.FunctionDecl declaration : candidate.functions()) {
          if (sameFile || samePackage && declaration.visibility() == Syntax.Visibility.PUBLIC) {
            SymbolId id = declarationSymbols.get(declaration);
            visible.put(id, id);
          }
        }
      }
      Syntax.Program previous = currentProgram;
      currentProgram = program;
      for (Syntax.ImportDecl imported : program.imports()) {
        Object declaration = resolveFunction(imported.localName());
        if (declaration == null) declaration = resolveAggregate(imported.localName());
        if (declaration == null) declaration = resolveEnum(imported.localName());
        if (declaration == null) declaration = resolveInterface(imported.localName());
        if (declaration == null) declaration = resolveAnnotation(imported.localName());
        if (declaration != null) {
          SymbolId id =
              imported.alias().isPresent()
                  ? importAliases.get(imported)
                  : declarationSymbols.get(declaration);
          visible.put(id, id);
        }
      }
      currentProgram = previous;
      flowScopes.addSemanticScope(
          new SemanticScope(program.span(), 0, List.copyOf(visible.values())));
    }
  }

  private List<ImportableSymbol> importableSymbols() {
    List<ImportableSymbol> result = new ArrayList<>();
    for (Syntax.Program program : programs) {
      if (!exportedSources.contains(program.span().source().id())) continue;
      program.enums().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(
              declaration ->
                  new ImportableSymbol(
                      symbols.get(declarationSymbols.get(declaration)),
                      qualifiedName(program.packageName(), declaration.name())))
          .forEach(result::add);
      program.interfaces().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(
              declaration ->
                  new ImportableSymbol(
                      symbols.get(declarationSymbols.get(declaration)),
                      qualifiedName(program.packageName(), declaration.name())))
          .forEach(result::add);
      program.aggregates().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(
              declaration ->
                  new ImportableSymbol(
                      symbols.get(declarationSymbols.get(declaration)),
                      qualifiedName(program.packageName(), declaration.name())))
          .forEach(result::add);
      program.annotationDeclarations().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(
              declaration ->
                  new ImportableSymbol(
                      symbols.get(declarationSymbols.get(declaration)),
                      qualifiedName(program.packageName(), declaration.name())))
          .forEach(result::add);
      program.functions().stream()
          .filter(declaration -> declaration.visibility() == Syntax.Visibility.PUBLIC)
          .map(
              declaration ->
                  new ImportableSymbol(
                      symbols.get(declarationSymbols.get(declaration)),
                      qualifiedName(program.packageName(), declaration.name())))
          .forEach(result::add);
    }
    return List.copyOf(result);
  }

  private void validateClassHierarchy() {
    Set<Syntax.AggregateDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.AggregateDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        activeTypeParameters = aggregateTypeParameters(declaration);
        activeTypeParameterSymbols = typeParameterSymbols(declaration.typeParameters());
        if (aggregateSelfType(declaration).identity().equals(SemanticType.EXCEPTION.identity())) {
          boolean validField =
              declaration.fields().size() == 1
                  && declaration.fields().getFirst().visibility() == Syntax.Visibility.PUBLIC
                  && declaration.fields().getFirst().name().equals(ExceptionAbi.MESSAGE_FIELD_NAME)
                  && resolveType(declaration.fields().getFirst().type(), activeTypeParameters)
                      .equals(SemanticType.STRING);
          if (declaration.kind() != Syntax.AggregateKind.CLASS
              || declaration.visibility() != Syntax.Visibility.PUBLIC
              || !declaration.typeParameters().isEmpty()
              || declaration.extendedClass().isPresent()
              || !validField) {
            diagnostics.error(
                TYPE_MISMATCH,
                "Exception root ABI requires public class Exception with one public String message field",
                declaration.nameSpan());
          }
        }
        declaration
            .extendedClass()
            .ifPresent(
                parentRef -> {
                  validateType(parentRef, false);
                  SemanticType parent = resolveType(parentRef, activeTypeParameters);
                  Syntax.AggregateDecl parentDeclaration = resolveAggregate(parent);
                  if (declaration.kind() != Syntax.AggregateKind.CLASS
                      || parentDeclaration == null
                      || parentDeclaration.kind() != Syntax.AggregateKind.CLASS) {
                    diagnostics.error(
                        TYPE_MISMATCH, "class inheritance requires a class", parentRef.span());
                    return;
                  }
                  aggregateParents.put(aggregateSelfType(declaration).identity(), parent);
                  if (isAssignable(SemanticType.EXCEPTION, aggregateSelfType(declaration))
                      && !declaration.typeParameters().isEmpty()) {
                    diagnostics.error(
                        TYPE_MISMATCH,
                        "Exception classes cannot declare type parameters",
                        declaration.nameSpan());
                  }
                  if (declaration.constructors().isEmpty()) {
                    diagnostics.error(
                        TYPE_MISMATCH,
                        "subclass '" + declaration.name() + "' must declare a constructor",
                        declaration.nameSpan());
                  }
                  validateInheritedFields(declaration);
                  validateOverrides(declaration);
                });
        activeTypeParameters = Map.of();
        activeTypeParameterSymbols = Map.of();
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
    Syntax.Program previous = currentProgram;
    currentProgram = declarations.owner(declaration);
    directParentType(declaration, aggregateSelfType(declaration))
        .map(this::resolveAggregate)
        .ifPresent(parent -> validateClassCycle(parent, visiting, visited));
    currentProgram = previous;
    visiting.remove(declaration);
    visited.add(declaration);
  }

  private void validateInheritedFields(Syntax.AggregateDecl declaration) {
    Set<String> inherited = new HashSet<>();
    List<AggregateView> views = aggregateViews(aggregateSelfType(declaration));
    for (AggregateView view : views.subList(Math.min(1, views.size()), views.size())) {
      view.declaration().fields().forEach(field -> inherited.add(field.name()));
    }
    for (Syntax.FieldDecl field : declaration.fields()) {
      if (inherited.contains(field.name())) {
        diagnostics.error(
            DUPLICATE_NAME,
            "field '" + field.name() + "' is already declared by a parent class",
            field.nameSpan());
      }
    }
  }

  private void validateOverrides(Syntax.AggregateDecl declaration) {
    List<AggregateView> views = aggregateViews(aggregateSelfType(declaration));
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
      Symbol methodSymbol = symbols.get(declarationSymbols.get(method));
      Symbol parentSymbol = symbols.get(declarationSymbols.get(parent.method()));
      Map<String, SemanticType> parentSubstitutions =
          aggregateSubstitutions(parent.view().declaration(), parent.view().type());
      if (!sameGenericShape(methodSymbol, Map.of(), parentSymbol, parentSubstitutions)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "override of '" + method.name() + "' must preserve type parameter bounds",
            method.nameSpan());
        continue;
      }
      SemanticType expected =
          functionReturnType(
                  parent.method(), typeParameters(parent.method(), parent.view().declaration()))
              .substitute(parentSubstitutions);
      SemanticType actual = functionReturnType(method, typeParameters(method, declaration));
      boolean sameReturnShape =
          canonicalType(expected, canonicalTypeParameters(parentSymbol))
              .equals(canonicalType(actual, canonicalTypeParameters(methodSymbol)));
      if (!sameReturnShape && !isAssignable(expected, actual)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "override of '" + method.name() + "' must return " + expected.displayName(),
            method.nameSpan());
        continue;
      }
      methodOverrides.put(declarationSymbols.get(method), declarationSymbols.get(parent.method()));
    }
  }

  private boolean sameOverrideParameters(
      Syntax.FunctionDecl method, Syntax.FunctionDecl inherited, AggregateView parent) {
    if (method.typeParameters().size() != inherited.typeParameters().size()
        || method.parameters().size() != inherited.parameters().size()) return false;
    Symbol methodSymbol = symbols.get(declarationSymbols.get(method));
    Symbol parentSymbol = symbols.get(declarationSymbols.get(inherited));
    Map<String, String> methodTypes = canonicalTypeParameters(methodSymbol);
    Map<String, String> parentTypes = canonicalTypeParameters(parentSymbol);
    Map<String, SemanticType> substitutions =
        aggregateSubstitutions(parent.declaration(), parent.type());
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
    activeTypeParameters = aggregateTypeParameters(aggregateDecl);
    activeTypeParameterSymbols = typeParameterSymbols(aggregateDecl.typeParameters());
    registerBounds(aggregateDecl.typeParameters(), activeTypeParameters);
    Set<String> names = new HashSet<>();
    for (Syntax.FieldDecl field : aggregateDecl.fields()) {
      validateType(field.type(), false);
      if (aggregateDecl.visibility() == Syntax.Visibility.PUBLIC
          && field.visibility() == Syntax.Visibility.PUBLIC) {
        validatePublicType(field.type());
      }
      if (!names.add(field.name())) {
        diagnostics.error(
            DUPLICATE_NAME, "field '" + field.name() + "' is already declared", field.span());
      }
    }
    Set<String> methods = new HashSet<>();
    for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
      if (aggregateDecl.kind() == Syntax.AggregateKind.CLASS && method.name().equals("copy")) {
        diagnostics.error(
            DUPLICATE_NAME, "method 'copy' is reserved for identity copying", method.nameSpan());
      }
      if (!methods.add(callableSignature(method))) {
        diagnostics.error(
            DUPLICATE_NAME, "method '" + method.name() + "' is already declared", method.span());
      }
    }
    if (aggregateDecl.constructors().size() > 1) {
      diagnostics.error(
          DUPLICATE_NAME,
          "class '" + aggregateDecl.name() + "' may declare one constructor",
          aggregateDecl.constructors().get(1).span());
    }
    if (aggregateDecl.kind() == Syntax.AggregateKind.VALUE
        && !aggregateDecl.constructors().isEmpty()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "value '" + aggregateDecl.name() + "' cannot declare a constructor",
          aggregateDecl.constructors().getFirst().span());
    }
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void validateEnum(Syntax.EnumDecl enumDecl) {
    activeTypeParameters = enumTypeParameters(enumDecl);
    activeTypeParameterSymbols = typeParameterSymbols(enumDecl.typeParameters());
    registerBounds(enumDecl.typeParameters(), activeTypeParameters);
    for (Syntax.EnumVariant variant : enumDecl.variants()) {
      Set<String> names = new HashSet<>();
      for (Syntax.Parameter parameter : variant.parameters()) {
        validateType(parameter.type(), false);
        if (!names.add(parameter.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "enum data '" + parameter.name() + "' is already declared",
              parameter.nameSpan());
        }
      }
    }
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void validateInterface(Syntax.InterfaceDecl declaration) {
    activeTypeParameters = interfaceTypeParameters(declaration);
    activeTypeParameterSymbols = typeParameterSymbols(declaration.typeParameters());
    registerBounds(declaration.typeParameters(), activeTypeParameters);
    for (Syntax.TypeRef parent : declaration.extendedInterfaces()) {
      validateType(parent, false);
      if (resolveInterface(resolveType(parent, activeTypeParameters)) == null) {
        diagnostics.error(TYPE_MISMATCH, "interface may extend interfaces only", parent.span());
      }
    }
    for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
      validateTypeParameterNames(method.typeParameters());
      Map<String, SemanticType> methodTypes =
          withTypeParameters(
              activeTypeParameters,
              method.typeParameters(),
              declarations.owner(declaration),
              "interface-method/" + method.name());
      Map<String, SymbolId> methodSymbols = new LinkedHashMap<>(activeTypeParameterSymbols);
      method
          .typeParameters()
          .forEach(
              parameter -> methodSymbols.put(parameter.name(), declarationSymbols.get(parameter)));
      activeTypeParameters = methodTypes;
      activeTypeParameterSymbols = Map.copyOf(methodSymbols);
      registerBounds(method.typeParameters(), methodTypes);
      validateType(method.returnType(), true);
      method.parameters().forEach(parameter -> validateReferenceCapableType(parameter.type()));
      if (method.body().isPresent())
        analyzeInterfaceDefault(declaration, method, methodTypes, methodSymbols);
      activeTypeParameters = interfaceTypeParameters(declaration);
      activeTypeParameterSymbols = typeParameterSymbols(declaration.typeParameters());
    }
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void analyzeInterfaceDefault(
      Syntax.InterfaceDecl owner,
      Syntax.InterfaceMethodDecl method,
      Map<String, SemanticType> methodTypes,
      Map<String, SymbolId> methodSymbols) {
    SemanticType previousReturn = expectedReturnType;
    SymbolId previousCallable = currentCallable;
    expectedReturnType = resolveDeclarationType(method.returnType(), method, methodTypes);
    currentCallable = defaultMethodId(method);
    flowScopes.clear();
    pushScope(method.span());
    for (Syntax.TypeParameter parameter : owner.typeParameters()) {
      declareExisting(
          parameter.name(),
          methodTypes.get(parameter.name()),
          parameter.nameSpan(),
          declarationSymbols.get(parameter));
    }
    for (Syntax.TypeParameter parameter : method.typeParameters()) {
      declareExisting(
          parameter.name(),
          methodTypes.get(parameter.name()),
          parameter.nameSpan(),
          methodSymbols.get(parameter.name()));
    }
    declareSelf(interfaceSelfType(owner), owner.nameSpan());
    for (Syntax.Parameter parameter : method.parameters()) {
      SemanticType type = resolveDeclarationType(parameter.type(), method, methodTypes);
      declareExisting(
          parameter.name(), type, parameter.nameSpan(), declarationSymbols.get(parameter));
    }
    analyzeStatements(method.body().orElseThrow());
    if (!expectedReturnType.equals(SemanticType.VOID)
        && !definitelyExits(method.body().orElseThrow())) {
      diagnostics.error(
          INVALID_CONTROL,
          "default method '" + method.name() + "' must return " + expectedReturnType.displayName(),
          method.span());
    }
    popScope();
    expectedReturnType = previousReturn;
    currentCallable = previousCallable;
  }

  private SymbolId defaultMethodId(Syntax.InterfaceMethodDecl method) {
    return new SymbolId(declarationSymbols.get(method).value() + "/default");
  }

  private void registerBounds(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> declaredTypes) {
    for (Syntax.TypeParameter parameter : parameters) {
      if (parameter.upperBound().isEmpty()) continue;
      Syntax.TypeRef boundSyntax = parameter.upperBound().orElseThrow();
      validateType(boundSyntax, false);
      SemanticType bound = resolveType(boundSyntax, declaredTypes);
      if (bound.isNullable() || resolveInterface(bound) == null) {
        diagnostics.error(
            TYPE_MISMATCH, "type parameter bound must be an interface", boundSyntax.span());
      }
      typeParameterBounds.put(declaredTypes.get(parameter.name()).identity(), bound);
    }
  }

  private void validateInterfaceGraphAndConformances() {
    Set<Syntax.InterfaceDecl> visiting =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Syntax.InterfaceDecl> visited =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (Syntax.InterfaceDecl declaration : declarations.interfaces()) {
      validateInterfaceCycle(declaration, visiting, visited);
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
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
      diagnostics.error(
          TYPE_MISMATCH, "interface inheritance contains a cycle", declaration.nameSpan());
      return;
    }
    Syntax.Program previous = currentProgram;
    currentProgram = declarations.owner(declaration);
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      Syntax.InterfaceDecl parent = resolveInterface(resolveType(parentRef, parameters));
      if (parent != null) validateInterfaceCycle(parent, visiting, visited);
    }
    currentProgram = previous;
    visiting.remove(declaration);
    visited.add(declaration);
  }

  private void validateAggregateConformance(Syntax.AggregateDecl declaration) {
    activeTypeParameters = aggregateTypeParameters(declaration);
    activeTypeParameterSymbols = typeParameterSymbols(declaration.typeParameters());
    registerBounds(declaration.typeParameters(), activeTypeParameters);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : declaration.implementedInterfaces()) {
      validateType(interfaceRef, false);
      SemanticType interfaceType = resolveType(interfaceRef, activeTypeParameters);
      Syntax.InterfaceDecl interfaceDecl = resolveInterface(interfaceType);
      if (interfaceDecl == null) {
        diagnostics.error(
            TYPE_MISMATCH,
            aggregateKeyword(declaration) + " may implement interfaces only",
            interfaceRef.span());
        continue;
      }
      collectConformances(interfaceDecl, interfaceType, conformances, interfaceRef.span());
    }
    Map<String, InterfaceRequirement> requirements = new LinkedHashMap<>();
    for (SemanticType conformance : conformances.values()) {
      Syntax.InterfaceDecl interfaceDecl = resolveInterface(conformance);
      if (interfaceDecl == null) continue;
      for (InterfaceRequirement requirement : directRequirements(interfaceDecl, conformance)) {
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
                    this::requirementShape,
                    LinkedHashMap::new,
                    java.util.stream.Collectors.toList()));
    for (List<InterfaceRequirement> group : requirementGroups.values()) {
      long defaults = group.stream().filter(value -> value.method().body().isPresent()).count();
      if (defaults < 2) continue;
      boolean resolved =
          declaration.methods().stream()
              .filter(method -> method.visibility() == Syntax.Visibility.PUBLIC)
              .anyMatch(
                  method ->
                      group.stream().allMatch(requirement -> witnessMatches(method, requirement)));
      if (!resolved) {
        diagnostics.error(
            TYPE_MISMATCH,
            "inherited interface default methods conflict for method '"
                + group.getFirst().method().name()
                + "'",
            declaration.nameSpan());
      }
    }
    for (InterfaceRequirement requirement : requirements.values()) {
      List<Syntax.FunctionDecl> candidates =
          declaration.methods().stream()
              .filter(method -> method.name().equals(requirement.method().name()))
              .filter(method -> method.visibility() == Syntax.Visibility.PUBLIC)
              .toList();
      Syntax.FunctionDecl matched =
          candidates.stream()
              .filter(method -> witnessMatches(method, requirement))
              .findFirst()
              .orElse(null);
      if (matched == null) {
        if (requirement.method().body().isEmpty()) {
          diagnostics.error(
              TYPE_MISMATCH,
              aggregateKeyword(declaration)
                  + " '"
                  + declaration.name()
                  + "' must provide public interface method '"
                  + requirement.method().name()
                  + "'",
              declaration.nameSpan());
        } else {
          witnesses
              .computeIfAbsent(
                  declarationSymbols.get(declaration), ignored -> new LinkedHashMap<>())
              .put(
                  declarationSymbols.get(requirement.method()),
                  defaultMethodId(requirement.method()));
        }
      } else {
        witnesses
            .computeIfAbsent(declarationSymbols.get(declaration), ignored -> new LinkedHashMap<>())
            .put(declarationSymbols.get(requirement.method()), declarationSymbols.get(matched));
      }
    }
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  @Override
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
    Syntax.Program previous = currentProgram;
    currentProgram = declarations.owner(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      SemanticType parent = resolveType(parentRef, parameters).substitute(substitutions);
      Syntax.InterfaceDecl parentDecl = resolveInterface(parent);
      if (parentDecl != null) collectConformances(parentDecl, parent, result, span);
    }
    currentProgram = previous;
  }

  @Override
  List<InterfaceRequirement> directRequirements(
      Syntax.InterfaceDecl declaration, SemanticType instance) {
    Map<String, SemanticType> substitutions = interfaceSubstitutions(declaration, instance);
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    return declaration.methods().stream()
        .map(
            method -> {
              Map<String, SemanticType> methodTypes =
                  withTypeParameters(
                      parameters,
                      method.typeParameters(),
                      declarations.owner(declaration),
                      "interface-method/" + method.name());
              List<ParameterInfo> methodParameters =
                  parameters(method.parameters(), substitutions, methodTypes);
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
                  declarationSymbols.get(method).value(),
                  signature);
            })
        .toList();
  }

  private boolean witnessMatches(Syntax.FunctionDecl witness, InterfaceRequirement requirement) {
    if (witness.typeParameters().size() != requirement.method().typeParameters().size()
        || witness.parameters().size() != requirement.parameters().size()) return false;
    Map<String, SemanticType> witnessTypes = typeParameters(witness, ownerOf(witness));
    Symbol requiredSymbol = symbols.get(declarationSymbols.get(requirement.method()));
    Symbol witnessSymbol = symbols.get(declarationSymbols.get(witness));
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
                  resolveDeclarationType(parameter.type(), witness, witnessTypes),
                  witnessParameters)
              .equals(canonicalType(required.type(), requiredParameters))) return false;
    }
    return canonicalType(functionReturnType(witness, witnessTypes), witnessParameters)
        .equals(canonicalType(requirement.result(), requiredParameters));
  }

  private String requirementShape(InterfaceRequirement requirement) {
    Symbol symbol = symbols.get(declarationSymbols.get(requirement.method()));
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

  @Override
  Map<String, SemanticType> interfaceSubstitutions(
      Syntax.InterfaceDecl declaration, SemanticType instance) {
    Map<String, SemanticType> declarations = interfaceTypeParameters(declaration);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(declaration.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = declarations.get(declaration.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return Map.copyOf(result);
  }

  private void analyzeFunction(Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    activeTypeParameters = typeParameters(function, owner);
    activeTypeParameterSymbols = typeParameterSymbols(function, owner);
    if (owner != null) registerBounds(owner.typeParameters(), activeTypeParameters);
    registerBounds(function.typeParameters(), activeTypeParameters);
    function.returnType().ifPresent(type -> validateType(type, true));
    expectedReturnType = functionReturnType(function, activeTypeParameters);
    implicitSelfReturn = owner != null && function.returnType().isEmpty();
    currentAggregate = owner;
    if (function.visibility() == Syntax.Visibility.PUBLIC
        && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC)) {
      function.returnType().ifPresent(this::validatePublicType);
      function.parameters().forEach(parameter -> validatePublicType(parameter.type()));
    }
    currentCallable = declarationSymbols.get(function);
    flowScopes.clear();
    assignedLocals.clear();
    capturedLocals.clear();
    reportedMutableCaptures.clear();
    lambdaLocals.clear();
    pushScope(function.span());
    if (owner != null) {
      for (Syntax.TypeParameter parameter : owner.typeParameters()) {
        declareExisting(
            parameter.name(),
            activeTypeParameters.get(parameter.name()),
            parameter.nameSpan(),
            declarationSymbols.get(parameter));
      }
    }
    for (Syntax.TypeParameter parameter : function.typeParameters()) {
      declareExisting(
          parameter.name(),
          activeTypeParameters.get(parameter.name()),
          parameter.nameSpan(),
          declarationSymbols.get(parameter));
    }
    if (owner != null) {
      declareSelf(aggregateSelfType(owner), owner.nameSpan());
      for (AggregateView view : aggregateViews(aggregateSelfType(owner))) {
        Map<String, SemanticType> substitutions =
            aggregateSubstitutions(view.declaration(), view.type());
        for (Syntax.FieldDecl field : view.declaration().fields()) {
          if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
            continue;
          declareExisting(
              field.name(),
              resolveDeclarationType(
                      field.type(), field, aggregateTypeParameters(view.declaration()))
                  .substitute(substitutions),
              field.nameSpan(),
              declarationSymbols.get(field));
        }
      }
    }
    for (Syntax.Parameter parameter : function.parameters()) {
      validateReferenceCapableType(parameter.type());
      Symbol symbol =
          register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              resolveType(parameter.type(), activeTypeParameters),
              parameter.nameSpan(),
              declarationSymbols.get(function),
              List.of(),
              List.of());
      declareExisting(
          parameter.name(),
          resolveType(parameter.type(), activeTypeParameters),
          parameter.nameSpan(),
          symbol.id());
    }
    analyzeStatements(function.body());
    if (!expectedReturnType.equals(SemanticType.VOID)
        && !implicitSelfReturn
        && !definitelyExits(function.body())) {
      diagnostics.error(
          INVALID_CONTROL,
          "function '" + function.name() + "' must return " + expectedReturnType.displayName(),
          function.span());
    }
    popScope();
    currentCallable = null;
    currentAggregate = null;
    implicitSelfReturn = false;
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void analyzeConstructor(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    activeTypeParameters = aggregateTypeParameters(owner);
    activeTypeParameterSymbols = typeParameterSymbols(owner.typeParameters());
    registerBounds(owner.typeParameters(), activeTypeParameters);
    expectedReturnType = SemanticType.VOID;
    implicitSelfReturn = false;
    currentAggregate = owner;
    currentCallable = declarationSymbols.get(constructor);
    flowScopes.clear();
    assignedLocals.clear();
    capturedLocals.clear();
    reportedMutableCaptures.clear();
    lambdaLocals.clear();
    pushScope(constructor.span());
    for (Syntax.TypeParameter parameter : owner.typeParameters()) {
      declareExisting(
          parameter.name(),
          activeTypeParameters.get(parameter.name()),
          parameter.nameSpan(),
          declarationSymbols.get(parameter));
    }
    declareSelf(aggregateSelfType(owner), owner.nameSpan());
    for (AggregateView view : aggregateViews(aggregateSelfType(owner))) {
      Map<String, SemanticType> substitutions =
          aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.FieldDecl field : view.declaration().fields()) {
        if (view.declaration() != owner && field.visibility() == Syntax.Visibility.PRIVATE)
          continue;
        declareExisting(
            field.name(),
            resolveDeclarationType(field.type(), field, aggregateTypeParameters(view.declaration()))
                .substitute(substitutions),
            field.nameSpan(),
            declarationSymbols.get(field));
      }
    }
    pushScope(constructor.span());
    for (Syntax.Parameter parameter : constructor.parameters()) {
      validateReferenceCapableType(parameter.type());
      Symbol symbol =
          register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              resolveType(parameter.type(), activeTypeParameters),
              parameter.nameSpan(),
              currentCallable,
              List.of(),
              List.of());
      declareExisting(parameter.name(), symbol.type(), parameter.nameSpan(), symbol.id());
    }
    analyzeSuperCall(constructor, owner);
    analyzeStatements(constructor.body());
    Map<SymbolId, String> fields = new LinkedHashMap<>();
    Set<SymbolId> ownFields = new HashSet<>();
    for (AggregateView view : aggregateViews(aggregateSelfType(owner))) {
      for (Syntax.FieldDecl field : view.declaration().fields()) {
        SymbolId fieldId = declarationSymbols.get(field);
        fields.put(fieldId, field.name());
        if (view.declaration() == owner) ownFields.add(fieldId);
      }
    }
    List<Set<SymbolId>> exits = new ArrayList<>();
    ConstructorInitialization beforeSuper = new ConstructorInitialization(fields, true);
    ConstructorFlow prefix = ConstructorFlow.normal(Set.of());
    if (constructor.superCall().isPresent()) {
      for (Syntax.CallArgument argument : constructor.superCall().orElseThrow().arguments()) {
        if (prefix.normal().isEmpty()) break;
        prefix =
            prefix.then(
                constructorExpressionFlow(
                    argument.value(), prefix.normal().orElseThrow(), beforeSuper));
      }
    }
    if (prefix.returned().isPresent()) {
      diagnostics.error(
          INVALID_CONTROL,
          "constructor cannot return before super initialization",
          constructor.superCall().orElseThrow().span());
    }
    ConstructorInitialization body = new ConstructorInitialization(fields, false);
    Set<SymbolId> inheritedFields = new HashSet<>(fields.keySet());
    inheritedFields.removeAll(ownFields);
    ConstructorFlow flow =
        prefix.then(
            prefix.normal().isPresent()
                ? constructorFlow(constructor.body(), inheritedFields, body)
                : ConstructorFlow.empty());
    flow.normal().ifPresent(exits::add);
    flow.returned().ifPresent(exits::add);
    for (Syntax.FieldDecl field : owner.fields()) {
      SymbolId fieldId = declarationSymbols.get(field);
      if (exits.stream().anyMatch(assigned -> !assigned.contains(fieldId))) {
        diagnostics.error(
            INVALID_CONTROL,
            "constructor must initialize field '" + field.name() + "'",
            field.nameSpan());
      }
    }
    popScope();
    popScope();
    currentCallable = null;
    currentAggregate = null;
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void analyzeSuperCall(Syntax.ConstructorDecl constructor, Syntax.AggregateDecl owner) {
    Optional<SemanticType> parentType = directParentType(owner, aggregateSelfType(owner));
    if (parentType.isEmpty()) {
      if (constructor.superCall().isPresent()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "root class constructor cannot call super",
            constructor.superCall().orElseThrow().span());
        analyzeArguments(constructor.superCall().orElseThrow().arguments());
      }
      return;
    }
    if (constructor.superCall().isEmpty()) {
      diagnostics.error(
          INVALID_CONTROL, "subclass constructor must call super", constructor.nameSpan());
      return;
    }
    SemanticType parent = parentType.orElseThrow();
    Syntax.AggregateDecl declaration = resolveAggregate(parent);
    if (declaration == null) {
      analyzeArguments(constructor.superCall().orElseThrow().arguments());
      return;
    }
    Syntax.SuperCall call = constructor.superCall().orElseThrow();
    Syntax.Call syntaxCall =
        new Syntax.Call(new Syntax.Name("super", call.span()), call.arguments(), call.span());
    Map<String, SemanticType> substitutions = aggregateSubstitutions(declaration, parent);
    List<ParameterInfo> parameters;
    SymbolId target;
    if (declaration.constructors().isEmpty()) {
      parameters = fieldParameters(declaration, substitutions);
      target = declarationSymbols.get(declaration);
    } else {
      Syntax.ConstructorDecl parentConstructor = declaration.constructors().getFirst();
      parameters =
          parameters(
              parentConstructor.parameters(), substitutions, aggregateTypeParameters(declaration));
      target = declarationSymbols.get(parentConstructor);
    }
    recordCall(
        syntaxCall,
        call.span(),
        ResolvedCall.Kind.SUPER,
        target,
        parameters,
        List.of(),
        SemanticType.VOID);
  }

  private ConstructorFlow constructorFlow(
      List<Syntax.Statement> statements,
      Set<SymbolId> incoming,
      ConstructorInitialization initialization) {
    ConstructorFlow flow = ConstructorFlow.normal(incoming);
    for (Syntax.Statement statement : statements) {
      if (flow.normal().isEmpty()) break;
      ConstructorFlow next =
          constructorStatementFlow(statement, flow.normal().orElseThrow(), initialization);
      flow = flow.then(next);
    }
    return flow;
  }

  private ConstructorFlow constructorStatementFlow(
      Syntax.Statement statement,
      Set<SymbolId> incoming,
      ConstructorInitialization initialization) {
    Set<SymbolId> assigned = new HashSet<>(incoming);
    return switch (statement) {
      case Syntax.VariableDecl variable ->
          constructorExpressionFlow(variable.initializer(), assigned, initialization);
      case Syntax.Assignment assignment -> {
        SymbolId field = constructorFieldBinding(assignment.target(), initialization);
        ConstructorFlow targetFlow =
            field == null
                ? constructorExpressionFlow(assignment.target(), assigned, initialization)
                : ConstructorFlow.normal(assigned);
        if (targetFlow.normal().isEmpty()) yield targetFlow;
        if (field != null) {
          if (initialization.beforeSuper()) {
            diagnostics.error(
                INVALID_CONTROL,
                "field cannot be assigned before super initialization",
                assignment.target().span());
          }
        }
        ConstructorFlow valueFlow =
            constructorExpressionFlow(
                assignment.value(), targetFlow.normal().orElseThrow(), initialization);
        ConstructorFlow flow = targetFlow.then(valueFlow);
        if (field == null || flow.normal().isEmpty() || initialization.beforeSuper()) yield flow;
        assigned = new HashSet<>(flow.normal().orElseThrow());
        assigned.add(field);
        yield flow.withNormal(assigned);
      }
      case Syntax.ExpressionStatement expression ->
          constructorExpressionFlow(expression.expression(), assigned, initialization);
      case Syntax.IfStatement conditional -> {
        ConstructorFlow condition =
            constructorExpressionFlow(conditional.condition(), assigned, initialization);
        if (condition.normal().isEmpty()) yield condition;
        Set<SymbolId> afterCondition = condition.normal().orElseThrow();
        ConstructorFlow branches =
            constructorFlow(conditional.thenBody(), afterCondition, initialization)
                .merge(constructorFlow(conditional.elseBody(), afterCondition, initialization));
        yield condition.withoutNormal().merge(branches);
      }
      case Syntax.ConditionalForStatement loop -> {
        ConstructorFlow condition =
            constructorExpressionFlow(loop.condition(), assigned, initialization);
        if (condition.normal().isEmpty()) yield condition;
        Set<SymbolId> afterCondition = condition.normal().orElseThrow();
        ConstructorFlow body = constructorFlow(loop.body(), afterCondition, initialization);
        ConstructorFlow completion =
            new ConstructorFlow(
                Optional.of(afterCondition),
                body.returned(),
                Optional.empty(),
                Optional.empty(),
                ConstructorFlow.mergeAssigned(Optional.of(afterCondition), body.thrown()));
        yield condition.withoutNormal().merge(completion);
      }
      case Syntax.ForStatement loop -> {
        ConstructorFlow iterable =
            constructorExpressionFlow(loop.iterable(), assigned, initialization);
        if (iterable.normal().isEmpty()) yield iterable;
        Set<SymbolId> afterIterable = iterable.normal().orElseThrow();
        ConstructorFlow body = constructorFlow(loop.body(), afterIterable, initialization);
        ConstructorFlow completion =
            new ConstructorFlow(
                Optional.of(afterIterable),
                body.returned(),
                Optional.empty(),
                Optional.empty(),
                ConstructorFlow.mergeAssigned(Optional.of(afterIterable), body.thrown()));
        yield iterable.withoutNormal().merge(completion);
      }
      case Syntax.TryStatement tried -> constructorTryFlow(tried, assigned, initialization);
      case Syntax.ThrowStatement thrown -> {
        ConstructorFlow exception =
            constructorExpressionFlow(thrown.exception(), assigned, initialization);
        ConstructorFlow completion =
            exception.normal().isPresent()
                ? ConstructorFlow.thrown(exception.normal().orElseThrow())
                : ConstructorFlow.empty();
        yield exception.withoutNormal().merge(completion);
      }
      case Syntax.ReturnStatement returned -> {
        if (returned.value() == null) yield ConstructorFlow.returned(assigned);
        ConstructorFlow value =
            constructorExpressionFlow(returned.value(), assigned, initialization);
        ConstructorFlow completion =
            value.normal().isPresent()
                ? ConstructorFlow.returned(value.normal().orElseThrow())
                : ConstructorFlow.empty();
        yield value.withoutNormal().merge(completion);
      }
      case Syntax.BreakStatement broken -> {
        if (broken.value() == null) yield ConstructorFlow.broken(assigned);
        ConstructorFlow value = constructorExpressionFlow(broken.value(), assigned, initialization);
        ConstructorFlow completion =
            value.normal().isPresent()
                ? ConstructorFlow.broken(value.normal().orElseThrow())
                : ConstructorFlow.empty();
        yield value.withoutNormal().merge(completion);
      }
      case Syntax.ContinueStatement ignored -> ConstructorFlow.continued(assigned);
    };
  }

  private ConstructorFlow constructorTryFlow(
      Syntax.TryStatement tried, Set<SymbolId> incoming, ConstructorInitialization initialization) {
    ConstructorFlow triedFlow = constructorFlow(tried.body(), incoming, initialization);
    ConstructorFlow combined = triedFlow;
    if (triedFlow.thrown().isPresent()) {
      Set<SymbolId> catchEntry = triedFlow.thrown().orElseThrow();
      for (Syntax.CatchClause clause : tried.catches()) {
        combined = combined.merge(constructorFlow(clause.body(), catchEntry, initialization));
      }
    }
    if (tried.finallyClause().isEmpty()) return combined;
    List<Set<SymbolId>> entries = new ArrayList<>(combined.completionStates());
    ConstructorFlow finalFlow =
        constructorFlow(
            tried.finallyClause().orElseThrow().body(),
            ConstructorFlow.intersect(entries),
            initialization);
    ConstructorFlow preserved = combined.afterFinally(finalFlow.normal());
    return preserved.merge(finalFlow.withoutNormal());
  }

  private ConstructorFlow constructorExpressionFlow(
      Syntax.Expression expression,
      Set<SymbolId> incoming,
      ConstructorInitialization initialization) {
    Set<SymbolId> assigned = new HashSet<>(incoming);
    return switch (expression) {
      case Syntax.Name name -> {
        validateConstructorBindingRead(name.span(), assigned, initialization);
        yield ConstructorFlow.normal(assigned);
      }
      case Syntax.Unary unary ->
          constructorExpressionFlow(unary.operand(), assigned, initialization);
      case Syntax.Binary binary -> {
        ConstructorFlow left = constructorExpressionFlow(binary.left(), assigned, initialization);
        if (left.normal().isEmpty()) yield left;
        ConstructorFlow right =
            constructorExpressionFlow(binary.right(), left.normal().orElseThrow(), initialization);
        if (binary.operator() == TokenKind.AND_AND || binary.operator() == TokenKind.OR_OR) {
          yield left.withoutNormal()
              .merge(right.withoutNormal())
              .withNormal(left.normal().orElseThrow());
        }
        ConstructorFlow flow = left.then(right);
        if ((binary.operator() == TokenKind.SLASH || binary.operator() == TokenKind.PERCENT)
            && flow.normal().isPresent()) {
          flow = flow.merge(ConstructorFlow.thrown(flow.normal().orElseThrow()));
        }
        yield flow;
      }
      case Syntax.Call call -> {
        SymbolId callee = binding(call.callee());
        if (call.callee() instanceof Syntax.Name
            && callee != null
            && symbols.get(callee) != null
            && symbols.get(callee).kind() == SymbolKind.METHOD) {
          requireInitializedReceiver(call.callee().span(), assigned, initialization);
        }
        ConstructorFlow flow = constructorExpressionFlow(call.callee(), assigned, initialization);
        for (Syntax.CallArgument argument : call.arguments()) {
          if (flow.normal().isEmpty()) break;
          flow =
              flow.then(
                  constructorExpressionFlow(
                      argument.value(), flow.normal().orElseThrow(), initialization));
        }
        if (flow.normal().isPresent()) {
          flow = flow.merge(ConstructorFlow.thrown(flow.normal().orElseThrow()));
        }
        yield flow;
      }
      case Syntax.Member member -> {
        SymbolId receiver = binding(member.receiver());
        SymbolId field = bindings.get(member.nameSpan());
        if (receiver != null
            && symbols.get(receiver) != null
            && symbols.get(receiver).kind() == SymbolKind.SELF
            && initialization.fields().containsKey(field)) {
          validateConstructorBindingRead(member.nameSpan(), assigned, initialization);
          yield ConstructorFlow.normal(assigned);
        } else {
          yield constructorExpressionFlow(member.receiver(), assigned, initialization);
        }
      }
      case Syntax.ArrayLiteral array -> {
        ConstructorFlow flow = ConstructorFlow.normal(assigned);
        for (Syntax.Expression value : array.elements()) {
          if (flow.normal().isEmpty()) break;
          flow =
              flow.then(
                  constructorExpressionFlow(value, flow.normal().orElseThrow(), initialization));
        }
        yield flow;
      }
      case Syntax.MethodReference reference ->
          constructorExpressionFlow(reference.receiver(), assigned, initialization);
      case Syntax.Index index -> {
        ConstructorFlow receiver =
            constructorExpressionFlow(index.receiver(), assigned, initialization);
        if (receiver.normal().isEmpty()) yield receiver;
        ConstructorFlow flow =
            receiver.then(
                constructorExpressionFlow(
                    index.index(), receiver.normal().orElseThrow(), initialization));
        if (flow.normal().isPresent()) {
          flow = flow.merge(ConstructorFlow.thrown(flow.normal().orElseThrow()));
        }
        yield flow;
      }
      case Syntax.SwitchExpression switched -> {
        ConstructorFlow value =
            constructorExpressionFlow(switched.value(), assigned, initialization);
        if (value.normal().isEmpty()) yield value;
        List<Set<SymbolId>> caseExits = new ArrayList<>();
        ConstructorFlow abrupt = value.withoutNormal();
        for (Syntax.SwitchCase branch : switched.cases()) {
          ConstructorFlow branchFlow =
              constructorFlow(branch.body(), value.normal().orElseThrow(), initialization);
          ConstructorFlow.mergeAssigned(branchFlow.normal(), branchFlow.broken())
              .ifPresent(caseExits::add);
          abrupt = abrupt.merge(branchFlow.withoutNormalAndBroken());
        }
        yield caseExits.isEmpty()
            ? abrupt
            : abrupt.withNormal(ConstructorFlow.intersect(caseExits));
      }
      case Syntax.Lambda lambda -> {
        if (constructorStatementsUseSelf(lambda.body())) {
          requireInitializedReceiver(lambda.span(), assigned, initialization);
        }
        yield ConstructorFlow.normal(assigned);
      }
      case Syntax.IntegerLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.DecimalLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.CodePointLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.BooleanLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.NullLiteral ignored -> ConstructorFlow.normal(assigned);
      case Syntax.StringLiteralExpr ignored -> ConstructorFlow.normal(assigned);
    };
  }

  private boolean constructorStatementsUseSelf(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      boolean usesSelf =
          switch (statement) {
            case Syntax.VariableDecl variable ->
                constructorExpressionUsesSelf(variable.initializer());
            case Syntax.Assignment assignment ->
                constructorExpressionUsesSelf(assignment.target())
                    || constructorExpressionUsesSelf(assignment.value());
            case Syntax.ExpressionStatement expression ->
                constructorExpressionUsesSelf(expression.expression());
            case Syntax.IfStatement conditional ->
                constructorExpressionUsesSelf(conditional.condition())
                    || constructorStatementsUseSelf(conditional.thenBody())
                    || constructorStatementsUseSelf(conditional.elseBody());
            case Syntax.ConditionalForStatement loop ->
                constructorExpressionUsesSelf(loop.condition())
                    || constructorStatementsUseSelf(loop.body());
            case Syntax.ForStatement loop ->
                constructorExpressionUsesSelf(loop.iterable())
                    || constructorStatementsUseSelf(loop.body());
            case Syntax.TryStatement tried ->
                constructorStatementsUseSelf(tried.body())
                    || tried.catches().stream()
                        .anyMatch(clause -> constructorStatementsUseSelf(clause.body()))
                    || tried.finallyClause().stream()
                        .anyMatch(clause -> constructorStatementsUseSelf(clause.body()));
            case Syntax.ThrowStatement thrown -> constructorExpressionUsesSelf(thrown.exception());
            case Syntax.ReturnStatement returned ->
                returned.value() != null && constructorExpressionUsesSelf(returned.value());
            case Syntax.BreakStatement broken ->
                broken.value() != null && constructorExpressionUsesSelf(broken.value());
            case Syntax.ContinueStatement ignored -> false;
          };
      if (usesSelf) return true;
    }
    return false;
  }

  private boolean constructorExpressionUsesSelf(Syntax.Expression expression) {
    return switch (expression) {
      case Syntax.Name name -> {
        SymbolId id = bindings.get(name.span());
        Symbol symbol = id == null ? null : symbols.get(id);
        yield symbol != null
            && (symbol.kind() == SymbolKind.SELF
                || symbol.kind() == SymbolKind.FIELD
                || symbol.kind() == SymbolKind.METHOD);
      }
      case Syntax.Unary unary -> constructorExpressionUsesSelf(unary.operand());
      case Syntax.Binary binary ->
          constructorExpressionUsesSelf(binary.left())
              || constructorExpressionUsesSelf(binary.right());
      case Syntax.Call call ->
          constructorExpressionUsesSelf(call.callee())
              || call.arguments().stream()
                  .anyMatch(argument -> constructorExpressionUsesSelf(argument.value()));
      case Syntax.Member member -> constructorExpressionUsesSelf(member.receiver());
      case Syntax.ArrayLiteral array ->
          array.elements().stream().anyMatch(this::constructorExpressionUsesSelf);
      case Syntax.MethodReference reference -> constructorExpressionUsesSelf(reference.receiver());
      case Syntax.Index index ->
          constructorExpressionUsesSelf(index.receiver())
              || constructorExpressionUsesSelf(index.index());
      case Syntax.SwitchExpression switched ->
          constructorExpressionUsesSelf(switched.value())
              || switched.cases().stream()
                  .anyMatch(branch -> constructorStatementsUseSelf(branch.body()));
      case Syntax.Lambda lambda -> constructorStatementsUseSelf(lambda.body());
      case Syntax.IntegerLiteral ignored -> false;
      case Syntax.DecimalLiteral ignored -> false;
      case Syntax.CodePointLiteral ignored -> false;
      case Syntax.BooleanLiteral ignored -> false;
      case Syntax.NullLiteral ignored -> false;
      case Syntax.StringLiteralExpr ignored -> false;
    };
  }

  private void validateConstructorBindingRead(
      SourceSpan span, Set<SymbolId> assigned, ConstructorInitialization initialization) {
    SymbolId id = bindings.get(span);
    if (id == null) return;
    String field = initialization.fields().get(id);
    if (field != null && !assigned.contains(id)) {
      diagnostics.error(
          INVALID_CONTROL, "field '" + field + "' is read before initialization", span);
    } else if (symbols.get(id) != null && symbols.get(id).kind() == SymbolKind.SELF) {
      requireInitializedReceiver(span, assigned, initialization);
    }
  }

  private SymbolId constructorFieldBinding(
      Syntax.Expression target, ConstructorInitialization initialization) {
    if (target instanceof Syntax.Name) {
      SymbolId id = binding(target);
      return initialization.fields().containsKey(id) ? id : null;
    }
    if (target instanceof Syntax.Member member) {
      SymbolId receiver = binding(member.receiver());
      SymbolId id = bindings.get(member.nameSpan());
      if (receiver != null
          && symbols.get(receiver) != null
          && symbols.get(receiver).kind() == SymbolKind.SELF
          && initialization.fields().containsKey(id)) {
        return id;
      }
    }
    return null;
  }

  private SymbolId binding(Syntax.Expression expression) {
    return expression instanceof Syntax.Member member
        ? bindings.get(member.nameSpan())
        : bindings.get(expression.span());
  }

  private void requireInitializedReceiver(
      SourceSpan span, Set<SymbolId> assigned, ConstructorInitialization initialization) {
    if (initialization.beforeSuper() || !assigned.containsAll(initialization.fields().keySet())) {
      diagnostics.error(
          INVALID_CONTROL, "this cannot be used before initialization completes", span);
    }
  }

  private record ConstructorInitialization(Map<SymbolId, String> fields, boolean beforeSuper) {
    private ConstructorInitialization {
      fields = Map.copyOf(fields);
    }
  }

  @Override
  void analyzeStatements(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      guard.checkpoint();
      analyzeStatement(statement);
    }
  }

  @Override
  void analyzeStatement(Syntax.Statement statement) {
    switch (statement) {
      case Syntax.VariableDecl variable -> {
        SemanticType requested =
            variable
                .type()
                .map(
                    type -> {
                      validateReferenceCapableType(type);
                      return resolveType(type, activeTypeParameters);
                    })
                .orElse(null);
        SemanticType actual = typeOf(variable.initializer(), requested);
        if (requested != null) requireAssignable(requested, actual, variable.initializer().span());
        if (requested == null && containsDynamic(actual)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "cannot infer variable type from initializer",
              variable.initializer().span());
        }
        SemanticType declaredType = requested == null ? actual : requested;
        Symbol symbol =
            register(
                variable,
                variable.name(),
                SymbolKind.LOCAL_VARIABLE,
                declaredType,
                variable.nameSpan(),
                currentCallable,
                List.of(),
                List.of());
        declareExisting(variable.name(), declaredType, variable.nameSpan(), symbol.id());
        if (declaredType.isReference()) {
          FlowScopes.ScopedSymbol scoped = findScoped(variable.name());
          if (scoped != null && scoped.id().equals(symbol.id())) {
            updateReferenceLifetime(scoped, variable.initializer());
          }
        }
        if (!lambdaLocals.isEmpty()) lambdaLocals.getFirst().add(symbol.id());
      }
      case Syntax.Assignment assignment -> {
        SemanticType target = assignmentTargetType(assignment.target());
        if (assignment.target() instanceof Syntax.Name name
            && currentAggregate != null
            && currentAggregate.kind() == Syntax.AggregateKind.VALUE) {
          FlowScopes.ScopedSymbol scoped = findScoped(name.value());
          if (scoped != null && scopedSymbol(scoped).kind() == SymbolKind.FIELD) {
            diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", name.span());
          }
        }
        SemanticType value = typeOf(assignment.value(), target);
        requireAssignable(target, value, assignment.value().span());
        if (assignment.target() instanceof Syntax.Name name) {
          FlowScopes.ScopedSymbol scoped = findScoped(name.value());
          if (scoped != null && target.isReference() && value.isReference()) {
            updateReferenceLifetime(scoped, assignment.value());
          }
          if (scoped != null
              && (scopedSymbol(scoped).kind() == SymbolKind.LOCAL_VARIABLE
                  || scopedSymbol(scoped).kind() == SymbolKind.PARAMETER)) {
            if (!lambdaLocals.isEmpty() && !lambdaLocals.getFirst().contains(scoped.id())) {
              capturedLocals.add(scoped.id());
              reportMutableCapture(scoped.id(), name.span());
            }
            assignedLocals.add(scoped.id());
            flowWriteCollectors.forEach(writes -> writes.add(scoped.id()));
            if (capturedLocals.contains(scoped.id())) {
              reportMutableCapture(scoped.id(), name.span());
            }
          }
          invalidateNarrowing(name.value());
        }
      }
      case Syntax.ExpressionStatement expression -> typeOf(expression.expression(), null);
      case Syntax.IfStatement ifStatement -> {
        requireType(
            SemanticType.BOOLEAN,
            typeOf(ifStatement.condition(), SemanticType.BOOLEAN),
            ifStatement.condition().span());
        FlowScopes.FlowState incoming = flowScopes.snapshot();
        FlowScopes.FlowState thenFlow =
            analyzeBranch(
                ifStatement.thenBody(), narrowingsFor(ifStatement.condition(), true), incoming);
        FlowScopes.FlowState elseFlow =
            analyzeBranch(
                ifStatement.elseBody(), narrowingsFor(ifStatement.condition(), false), incoming);
        boolean thenReturns = definitelyExits(ifStatement.thenBody());
        boolean elseReturns = definitelyExits(ifStatement.elseBody());
        if (thenReturns && !elseReturns) {
          replaceFlow(elseFlow);
        } else if (elseReturns && !thenReturns) {
          replaceFlow(thenFlow);
        } else if (!thenReturns && !elseReturns) {
          replaceFlow(mergeFlows(incoming, thenFlow, elseFlow));
        } else {
          replaceFlow(incoming);
        }
      }
      case Syntax.ConditionalForStatement loop -> {
        requireType(
            SemanticType.BOOLEAN,
            typeOf(loop.condition(), SemanticType.BOOLEAN),
            loop.condition().span());
        FlowScopes.FlowState incoming = flowScopes.snapshot();
        pushScope(loop.span());
        applyNarrowings(narrowingsFor(loop.condition(), true));
        controls.addFirst(ControlContext.loop());
        analyzeStatements(loop.body());
        controls.removeFirst();
        popScope();
        FlowScopes.FlowState bodyFlow = flowScopes.snapshot();
        replaceFlow(mergeFlows(incoming, incoming, bodyFlow));
      }
      case Syntax.ForStatement forStatement -> {
        SemanticType iterableType = typeOf(forStatement.iterable(), null);
        Optional<dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable> builtinIterable =
            builtins.resolveIterable(iterableType);
        Optional<ResolvedIteration> interfaceIteration = resolveInterfaceIteration(iterableType);
        builtinIterable.ifPresent(
            capability ->
                iterations.put(
                    forStatement.iterable().span(),
                    new ResolvedIteration(
                        capability.elementType(),
                        new ResolvedIteration.Strategy.Builtin(capability.intrinsic()))));
        interfaceIteration.ifPresent(
            resolution -> iterations.put(forStatement.iterable().span(), resolution));
        Optional<SemanticType> elementType =
            builtinIterable
                .map(dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable::elementType)
                .or(() -> interfaceIteration.map(ResolvedIteration::elementType));
        if (elementType.isEmpty()) {
          diagnostics.error(
              TYPE_MISMATCH, "for requires an iterable value", forStatement.iterable().span());
        }
        SemanticType variableType;
        if (forStatement.variableType().isPresent()) {
          Syntax.TypeRef explicitType = forStatement.variableType().orElseThrow();
          validateType(explicitType, false);
          variableType = resolveType(explicitType, activeTypeParameters);
          elementType.ifPresent(
              itemType ->
                  requireAssignable(variableType, itemType, forStatement.variableNameSpan()));
        } else {
          if (elementType.isEmpty()) {
            diagnostics.error(
                TYPE_MISMATCH,
                "cannot infer loop variable type from " + iterableType.displayName(),
                forStatement.variableNameSpan());
            variableType = SemanticType.DYNAMIC;
          } else {
            variableType = elementType.orElseThrow();
          }
        }
        pushScope(forStatement.span());
        Symbol symbol =
            register(
                forStatement,
                forStatement.variableName(),
                SymbolKind.LOCAL_VARIABLE,
                variableType,
                forStatement.variableNameSpan(),
                currentCallable,
                List.of(),
                List.of());
        declareExisting(
            forStatement.variableName(),
            variableType,
            forStatement.variableNameSpan(),
            symbol.id());
        if (!lambdaLocals.isEmpty()) lambdaLocals.getFirst().add(symbol.id());
        forStatement
            .index()
            .ifPresent(
                index -> {
                  Symbol indexSymbol =
                      register(
                          index,
                          index.name(),
                          SymbolKind.LOCAL_VARIABLE,
                          SemanticType.INTEGER,
                          index.nameSpan(),
                          currentCallable,
                          List.of(),
                          List.of());
                  declareExisting(
                      index.name(), SemanticType.INTEGER, index.nameSpan(), indexSymbol.id());
                  if (!lambdaLocals.isEmpty()) lambdaLocals.getFirst().add(indexSymbol.id());
                });
        controls.addFirst(ControlContext.loop());
        analyzeStatements(forStatement.body());
        controls.removeFirst();
        popScope();
      }
      case Syntax.TryStatement tried -> analyzeTry(tried);
      case Syntax.ThrowStatement thrown -> {
        SemanticType type = typeOf(thrown.exception(), SemanticType.EXCEPTION);
        if (!isAssignable(SemanticType.EXCEPTION, type)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "throw requires an Exception but found " + type.displayName(),
              thrown.exception().span());
        }
      }
      case Syntax.ReturnStatement returnStatement -> {
        if (implicitSelfReturn) {
          if (returnStatement.value() != null) {
            typeOf(returnStatement.value(), expectedReturnType);
            diagnostics.error(
                TYPE_MISMATCH,
                "fluent methods return their receiver; use a bare return",
                returnStatement.span());
          }
        } else {
          SemanticType actual =
              returnStatement.value() == null
                  ? SemanticType.VOID
                  : typeOf(returnStatement.value(), expectedReturnType);
          requireAssignable(expectedReturnType, actual, returnStatement.span());
        }
      }
      case Syntax.BreakStatement breakStatement -> analyzeBreak(breakStatement);
      case Syntax.ContinueStatement continueStatement -> validateContinue(continueStatement.span());
    }
  }

  private void analyzeTry(Syntax.TryStatement tried) {
    FlowScopes.FlowState incoming = flowScopes.snapshot();
    List<FlowScopes.FlowState> completing = new ArrayList<>();
    FlowScopes.FlowState tryFlow = analyzeBranch(tried.body(), Map.of(), incoming);
    if (!definitelyExits(tried.body())) completing.add(tryFlow);
    List<SemanticType> preceding = new ArrayList<>();
    for (Syntax.CatchClause clause : tried.catches()) {
      validateType(clause.type(), false);
      SemanticType type = resolveType(clause.type(), activeTypeParameters);
      if (!isAssignable(SemanticType.EXCEPTION, type)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "catch requires an Exception type but found " + type.displayName(),
            clause.type().span());
      }
      if (preceding.stream().anyMatch(previous -> isAssignable(previous, type))) {
        diagnostics.error(
            INVALID_CONTROL,
            "catch type " + type.displayName() + " is already covered by an earlier catch",
            clause.type().span());
      }
      preceding.add(type);
      replaceFlow(incoming);
      pushScope(clause.span());
      Symbol symbol =
          register(
              clause,
              clause.name(),
              SymbolKind.LOCAL_VARIABLE,
              type,
              clause.nameSpan(),
              currentCallable,
              List.of(),
              List.of());
      declareExisting(clause.name(), type, clause.nameSpan(), symbol.id());
      if (!lambdaLocals.isEmpty()) lambdaLocals.getFirst().add(symbol.id());
      analyzeStatements(clause.body());
      popScope();
      if (!definitelyExits(clause.body())) completing.add(flowScopes.snapshot());
    }
    FlowScopes.FlowState normal = mergeCompletingFlows(incoming, completing);
    replaceFlow(normal);
    if (tried.finallyClause().isPresent()) {
      Set<SymbolId> finalWrites = new HashSet<>();
      flowWriteCollectors.addFirst(finalWrites);
      FlowScopes.FlowState finalFlow;
      try {
        finalFlow = analyzeBranch(tried.finallyClause().orElseThrow().body(), Map.of(), incoming);
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
      replaceFlow(new FlowScopes.FlowState(types, lifetimes));
    }
  }

  private FlowScopes.FlowState mergeCompletingFlows(
      FlowScopes.FlowState incoming, List<FlowScopes.FlowState> flows) {
    if (flows.isEmpty()) return incoming;
    FlowScopes.FlowState result = flows.getFirst();
    for (int index = 1; index < flows.size(); index++) {
      result = mergeFlows(incoming, result, flows.get(index));
    }
    return result;
  }

  private void updateReferenceLifetime(
      FlowScopes.ScopedSymbol destination, Syntax.Expression value) {
    LexicalLifetime sourceLifetime = referenceLifetime(value);
    LexicalLifetime destinationLifetime = flowScopes.storageLifetime(destination);
    if (!sourceLifetime.outlives(destinationLifetime)) {
      diagnostics.error(
          INVALID_CONTROL, "reference cannot outlive the addressed storage location", value.span());
      flowScopes.updateReferenceLifetime(destination, LexicalLifetime.unusable());
      return;
    }
    flowScopes.updateReferenceLifetime(destination, sourceLifetime);
  }
}
