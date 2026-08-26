package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class Analyzer extends AnalyzerExpressions {
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
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          analyzeFunction(method, aggregateDecl);
        }
      }
    }
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
            typeSymbols,
            interfaceParentTypes(),
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
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        if (!declarations.addAggregate(program, aggregateDecl)
            || resolveEnum(aggregateDecl.name()) != null
            || resolveInterface(aggregateDecl.name()) != null
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
        type =
            new Symbol(
                type.id(),
                type.name(),
                type.kind(),
                type.type(),
                type.declaration(),
                type.owner(),
                type.typeParameters(),
                fieldParameters(
                    aggregateDecl.fields(), Map.of(), aggregateTypeParameters(aggregateDecl)),
                type.documentation());
        symbols.put(type.id(), type);
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
                  symbolTypeParameters(method.typeParameters(), functionTypeParameters(method)),
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
      method.parameters().forEach(parameter -> validateType(parameter.type(), false));
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
      Symbol symbol =
          register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              type,
              parameter.nameSpan(),
              currentCallable,
              List.of(),
              List.of());
      declareExisting(parameter.name(), type, parameter.nameSpan(), symbol.id());
    }
    analyzeStatements(method.body().orElseThrow());
    if (!expectedReturnType.equals(SemanticType.VOID)
        && !definitelyReturns(method.body().orElseThrow())) {
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
    Map<String, String> requiredParameters = new LinkedHashMap<>();
    Symbol requiredSymbol = symbols.get(declarationSymbols.get(requirement.method()));
    for (int index = 0; index < requiredSymbol.typeParameters().size(); index++) {
      requiredParameters.put(
          requiredSymbol.typeParameters().get(index).type().identity(), "$" + index);
    }
    Map<String, String> witnessParameters = new LinkedHashMap<>();
    Symbol witnessSymbol = symbols.get(declarationSymbols.get(witness));
    for (int index = 0; index < witnessSymbol.typeParameters().size(); index++) {
      witnessParameters.put(
          witnessSymbol.typeParameters().get(index).type().identity(), "$" + index);
      Optional<SemanticType> requiredBound =
          requiredSymbol.typeParameters().get(index).upperBound();
      Optional<SemanticType> witnessBound = witnessSymbol.typeParameters().get(index).upperBound();
      if (requiredBound.isPresent() != witnessBound.isPresent()
          || requiredBound.isPresent()
              && !canonicalType(requiredBound.orElseThrow(), requiredParameters)
                  .equals(canonicalType(witnessBound.orElseThrow(), witnessParameters))) {
        return false;
      }
    }
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
      for (Syntax.FieldDecl field : owner.fields()) {
        declareExisting(
            field.name(),
            resolveType(field.type(), activeTypeParameters),
            field.nameSpan(),
            declarationSymbols.get(field));
      }
    }
    for (Syntax.Parameter parameter : function.parameters()) {
      validateType(parameter.type(), false);
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
        && !definitelyReturns(function.body())) {
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
                      validateType(type, false);
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
          if (scoped != null
              && (scopedSymbol(scoped).kind() == SymbolKind.LOCAL_VARIABLE
                  || scopedSymbol(scoped).kind() == SymbolKind.PARAMETER)) {
            if (!lambdaLocals.isEmpty() && !lambdaLocals.getFirst().contains(scoped.id())) {
              capturedLocals.add(scoped.id());
              reportMutableCapture(scoped.id(), name.span());
            }
            assignedLocals.add(scoped.id());
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
        Map<SymbolId, SemanticType> incoming = flowScopes.snapshot();
        Map<SymbolId, SemanticType> thenFlow =
            analyzeBranch(
                ifStatement.thenBody(), narrowingsFor(ifStatement.condition(), true), incoming);
        Map<SymbolId, SemanticType> elseFlow =
            analyzeBranch(
                ifStatement.elseBody(), narrowingsFor(ifStatement.condition(), false), incoming);
        boolean thenReturns = definitelyReturns(ifStatement.thenBody());
        boolean elseReturns = definitelyReturns(ifStatement.elseBody());
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
        Map<SymbolId, SemanticType> incoming = flowScopes.snapshot();
        pushScope(loop.span());
        applyNarrowings(narrowingsFor(loop.condition(), true));
        controls.addFirst(ControlContext.loop());
        analyzeStatements(loop.body());
        controls.removeFirst();
        popScope();
        Map<SymbolId, SemanticType> bodyFlow = flowScopes.snapshot();
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
}
