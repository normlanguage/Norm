package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.semantic.ArgumentBinding;
import dev.w0fv1.norm.semantic.BuiltinSymbols;
import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.NumericTypes;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.PatternCoverage;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.TypeRelations;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class Analyzer {
  private static final DiagnosticCode DUPLICATE_NAME = new DiagnosticCode("NORM-NAME-0001");
  private static final DiagnosticCode MISSING_MAIN = new DiagnosticCode("NORM-NAME-0002");
  private static final DiagnosticCode UNKNOWN_NAME = new DiagnosticCode("NORM-NAME-0003");
  private static final DiagnosticCode TYPE_MISMATCH = new DiagnosticCode("NORM-TYPE-0001");
  private static final DiagnosticCode INVALID_CALL = new DiagnosticCode("NORM-TYPE-0002");
  private static final DiagnosticCode INVALID_CONTROL = new DiagnosticCode("NORM-FLOW-0001");
  private static final DiagnosticCode NULLABILITY_MISMATCH = new DiagnosticCode("NORM-NULL-0001");
  private static final DiagnosticCode UNTYPED_NULL = new DiagnosticCode("NORM-NULL-0002");
  private static final DiagnosticCode INVALID_NULLABLE_TYPE = new DiagnosticCode("NORM-NULL-0003");
  private static final DiagnosticCode UNSAFE_NULLABLE_ACCESS = new DiagnosticCode("NORM-NULL-0004");
  private static final SemanticType STRINGABLE =
      SemanticType.declared(
          "std.core.Stringable", "Stringable", List.of(), ValueCategory.POLYMORPHIC);

  private final Syntax.Program syntax;
  private final List<Syntax.Program> programs;
  private final Syntax.Program entryProgram;
  private final DiagnosticBag diagnostics;
  private final OverloadResolver overloads;
  private final TypeRelations.DeclarationGraph typeRelations;
  private final boolean requireEntryPoint;
  private final Set<DocumentId> exportedSources;
  private final Map<String, List<Syntax.FunctionDecl>> functions = new HashMap<>();
  private final Map<String, Syntax.ClassDecl> classes = new HashMap<>();
  private final Map<String, Syntax.EnumDecl> enums = new HashMap<>();
  private final Map<String, Syntax.InterfaceDecl> interfaces = new HashMap<>();
  private final Map<String, SemanticType> typeParameterBounds = new HashMap<>();
  private final BuiltinSymbols builtins = new BuiltinSymbols();
  private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
  private final Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
  private final Map<SourceSpan, SemanticType> semanticTypes = new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedCall> resolvedCalls = new LinkedHashMap<>();
  private final Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments =
      new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedIteration> iterations = new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedIndex> indexes = new LinkedHashMap<>();
  private final Map<SymbolId, List<SymbolId>> members = new LinkedHashMap<>();
  private final Map<String, SymbolId> typeSymbols = new LinkedHashMap<>();
  private final Map<Object, SymbolId> declarationSymbols = new IdentityHashMap<>();
  private final Map<Object, Syntax.Program> declarationPrograms = new IdentityHashMap<>();
  private final Map<String, SymbolId> copyMethods = new HashMap<>();
  private final Map<Syntax.ImportDecl, SymbolId> importAliases = new IdentityHashMap<>();
  private final Map<SymbolId, List<SymbolId>> aliasTargets = new LinkedHashMap<>();
  private final Map<SymbolId, Map<SymbolId, SymbolId>> witnesses = new LinkedHashMap<>();
  private final Deque<ScopeFrame> scopes = new ArrayDeque<>();
  private final Map<SymbolId, SemanticType> flowTypes = new HashMap<>();
  private final List<SemanticScope> semanticScopes = new ArrayList<>();
  private int nextSymbolId;
  private SymbolId currentCallable;
  private SemanticType expectedReturnType = SemanticType.VOID;
  private boolean implicitSelfReturn;
  private Map<String, SemanticType> activeTypeParameters = Map.of();
  private Map<String, SymbolId> activeTypeParameterSymbols = Map.of();
  private Syntax.Program currentProgram;
  private Syntax.ClassDecl currentClass;
  private final Deque<ControlContext> controls = new ArrayDeque<>();
  private final Set<SymbolId> assignedLocals = new HashSet<>();
  private final Set<SymbolId> capturedLocals = new HashSet<>();
  private final Set<SymbolId> reportedMutableCaptures = new HashSet<>();
  private final Deque<Set<SymbolId>> lambdaLocals = new ArrayDeque<>();

  Analyzer(Syntax.Program syntax, DiagnosticBag diagnostics) {
    this(List.of(syntax), syntax, diagnostics, false, Set.of());
  }

  Analyzer(
      List<Syntax.Program> programs,
      Syntax.Program entryProgram,
      DiagnosticBag diagnostics,
      boolean requireEntryPoint,
      Set<DocumentId> exportedSources) {
    this.programs = List.copyOf(programs);
    this.entryProgram = entryProgram;
    this.syntax = merge(programs, entryProgram);
    this.diagnostics = diagnostics;
    this.typeRelations = new TypeRelations.DeclarationGraph(this::isNominallyAssignable);
    this.overloads = new OverloadResolver(diagnostics, INVALID_CALL, typeRelations, this::typeOf);
    this.requireEntryPoint = requireEntryPoint;
    this.exportedSources = Set.copyOf(exportedSources);
    symbols.putAll(builtins.symbols());
    symbols.values().stream()
        .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
        .forEach(symbol -> typeSymbols.put(symbol.type().identity(), symbol.id()));
    builtins.members().forEach((owner, values) -> members.put(owner, new ArrayList<>(values)));
  }

  FrontendAnalysis analyze(boolean resolveProgram) {
    indexDeclarationPrograms();
    collectDeclarations();
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
      currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        validateTypeParameterNames(enumDecl.typeParameters());
        validateEnum(enumDecl);
      }
      for (Syntax.InterfaceDecl interfaceDecl : program.interfaces()) {
        validateTypeParameterNames(interfaceDecl.typeParameters());
        validateInterface(interfaceDecl);
      }
      for (Syntax.FunctionDecl function : program.functions()) {
        analyzeFunction(function, null);
      }
      for (Syntax.ClassDecl classDecl : program.classes()) {
        validateTypeParameterNames(classDecl.typeParameters());
        validateFields(classDecl);
        for (Syntax.FunctionDecl method : classDecl.methods()) {
          analyzeFunction(method, classDecl);
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
            semanticScopes,
            snapshot,
            importableSymbols());
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

  private void collectDeclarations() {
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        if (interfaces.putIfAbsent(
                    declarationKey(program, declaration.name(), declaration.visibility()),
                    declaration)
                != null
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
        if (enums.putIfAbsent(
                    declarationKey(program, enumDecl.name(), enumDecl.visibility()), enumDecl)
                != null
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
      for (Syntax.ClassDecl classDecl : program.classes()) {
        if (classes.putIfAbsent(
                    declarationKey(program, classDecl.name(), classDecl.visibility()), classDecl)
                != null
            || resolveEnum(classDecl.name()) != null
            || resolveInterface(classDecl.name()) != null
            || builtins.isType(classDecl.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "type '" + classDecl.name() + "' is already declared",
              classDecl.span());
        }
        Symbol type =
            register(
                classDecl,
                classDecl.name(),
                SymbolKind.TYPE,
                sourceType(classDecl.name(), List.of()),
                classDecl.nameSpan(),
                null,
                symbolTypeParameters(classDecl.typeParameters(), classTypeParameters(classDecl)),
                List.of());
        typeSymbols.putIfAbsent(type.type().identity(), type.id());
        registerTypeParameters(
            classDecl.typeParameters(), type.id(), classTypeParameters(classDecl));
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
                  declarationPrograms.get(declaration),
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
      for (Syntax.ClassDecl classDecl : program.classes()) {
        Symbol type = symbols.get(declarationSymbols.get(classDecl));
        for (Syntax.FieldDecl field : classDecl.fields()) {
          Symbol symbol =
              register(
                  field,
                  field.name(),
                  SymbolKind.FIELD,
                  resolveDeclarationType(field.type(), field, classTypeParameters(classDecl)),
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
                fieldParameters(classDecl.fields(), Map.of(), classTypeParameters(classDecl)),
                type.documentation());
        symbols.put(type.id(), type);
        SymbolId copyId = SymbolId.source(classDecl.nameSpan().source().id(), nextSymbolId++);
        Symbol copy =
            new Symbol(
                copyId,
                "copy",
                SymbolKind.METHOD,
                classSelfType(classDecl),
                Optional.empty(),
                Optional.of(type.id()),
                List.of(),
                List.of(),
                "Creates a new top-level object identity.");
        symbols.put(copyId, copy);
        addMember(type.id(), copyId);
        copyMethods.put(type.type().identity(), copyId);
        for (Syntax.FunctionDecl method : classDecl.methods()) {
          validateTypeParameterNames(method.typeParameters());
          Symbol symbol =
              register(
                  method,
                  method.name(),
                  SymbolKind.METHOD,
                  functionReturnType(method, typeParameters(method, classDecl)),
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
        String key = declarationKey(program, function.name(), function.visibility());
        List<Syntax.FunctionDecl> overloads =
            functions.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (overloads.stream()
            .anyMatch(
                candidate -> callableSignature(candidate).equals(callableSignature(function)))) {
          diagnostics.error(
              DUPLICATE_NAME,
              "function overload '" + function.name() + "' is already declared",
              function.span());
        }
        overloads.add(function);
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
      program.classes().forEach(declaration -> localNames.add(declaration.name()));
      program.functions().forEach(declaration -> localNames.add(declaration.name()));
      Set<String> importedNames = new HashSet<>();
      for (Syntax.ImportDecl imported : program.imports()) {
        if (!importedNames.add(imported.localName()) || localNames.contains(imported.localName())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "import name '" + imported.localName() + "' is already declared",
              imported.span());
        }
        List<Syntax.FunctionDecl> importedFunctions = functions.get(imported.qualifiedName());
        Object declaration =
            importedFunctions == null || importedFunctions.isEmpty()
                ? null
                : importedFunctions.getFirst();
        if (declaration == null) declaration = classes.get(imported.qualifiedName());
        if (declaration == null) declaration = enums.get(imported.qualifiedName());
        if (declaration == null) declaration = interfaces.get(imported.qualifiedName());
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
        boolean samePackage = candidate.packageName().equals(program.packageName());
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
        for (Syntax.ClassDecl declaration : candidate.classes()) {
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
        if (declaration == null) declaration = resolveClass(imported.localName());
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
      semanticScopes.add(new SemanticScope(program.span(), 0, List.copyOf(visible.values())));
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
      program.classes().stream()
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

  private void validateFields(Syntax.ClassDecl classDecl) {
    activeTypeParameters = classTypeParameters(classDecl);
    activeTypeParameterSymbols = typeParameterSymbols(classDecl.typeParameters());
    registerBounds(classDecl.typeParameters(), activeTypeParameters);
    Set<String> names = new HashSet<>();
    for (Syntax.FieldDecl field : classDecl.fields()) {
      validateType(field.type(), false);
      if (classDecl.visibility() == Syntax.Visibility.PUBLIC
          && field.visibility() == Syntax.Visibility.PUBLIC) {
        validatePublicType(field.type());
      }
      if (!names.add(field.name())) {
        diagnostics.error(
            DUPLICATE_NAME, "field '" + field.name() + "' is already declared", field.span());
      }
    }
    Set<String> methods = new HashSet<>();
    for (Syntax.FunctionDecl method : classDecl.methods()) {
      if (method.name().equals("copy")) {
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
              declarationPrograms.get(declaration),
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
    scopes.clear();
    flowTypes.clear();
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
    for (Syntax.InterfaceDecl declaration : interfaces.values()) {
      validateInterfaceCycle(declaration, visiting, visited);
    }
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.ClassDecl declaration : program.classes()) {
        validateClassConformance(declaration);
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
    currentProgram = declarationPrograms.get(declaration);
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      Syntax.InterfaceDecl parent = resolveInterface(resolveType(parentRef, parameters));
      if (parent != null) validateInterfaceCycle(parent, visiting, visited);
    }
    currentProgram = previous;
    visiting.remove(declaration);
    visited.add(declaration);
  }

  private void validateClassConformance(Syntax.ClassDecl declaration) {
    activeTypeParameters = classTypeParameters(declaration);
    activeTypeParameterSymbols = typeParameterSymbols(declaration.typeParameters());
    registerBounds(declaration.typeParameters(), activeTypeParameters);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : declaration.implementedInterfaces()) {
      validateType(interfaceRef, false);
      SemanticType interfaceType = resolveType(interfaceRef, activeTypeParameters);
      Syntax.InterfaceDecl interfaceDecl = resolveInterface(interfaceType);
      if (interfaceDecl == null) {
        diagnostics.error(
            TYPE_MISMATCH, "class may implement interfaces only", interfaceRef.span());
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
              "class '"
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

  private void collectConformances(
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
    currentProgram = declarationPrograms.get(declaration);
    for (Syntax.TypeRef parentRef : declaration.extendedInterfaces()) {
      SemanticType parent = resolveType(parentRef, parameters).substitute(substitutions);
      Syntax.InterfaceDecl parentDecl = resolveInterface(parent);
      if (parentDecl != null) collectConformances(parentDecl, parent, result, span);
    }
    currentProgram = previous;
  }

  private List<InterfaceRequirement> directRequirements(
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
                      declarationPrograms.get(declaration),
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

  private Map<String, SemanticType> interfaceSubstitutions(
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

  private void analyzeFunction(Syntax.FunctionDecl function, Syntax.ClassDecl owner) {
    activeTypeParameters = typeParameters(function, owner);
    activeTypeParameterSymbols = typeParameterSymbols(function, owner);
    if (owner != null) registerBounds(owner.typeParameters(), activeTypeParameters);
    registerBounds(function.typeParameters(), activeTypeParameters);
    function.returnType().ifPresent(type -> validateType(type, true));
    expectedReturnType = functionReturnType(function, activeTypeParameters);
    implicitSelfReturn = owner != null && function.returnType().isEmpty();
    currentClass = owner;
    if (function.visibility() == Syntax.Visibility.PUBLIC
        && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC)) {
      function.returnType().ifPresent(this::validatePublicType);
      function.parameters().forEach(parameter -> validatePublicType(parameter.type()));
    }
    currentCallable = declarationSymbols.get(function);
    scopes.clear();
    flowTypes.clear();
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
      declareSelf(classSelfType(owner), owner.nameSpan());
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
    currentClass = null;
    implicitSelfReturn = false;
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void analyzeStatements(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      analyzeStatement(statement);
    }
  }

  private void analyzeStatement(Syntax.Statement statement) {
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
        SemanticType value = typeOf(assignment.value(), target);
        requireAssignable(target, value, assignment.value().span());
        if (assignment.target() instanceof Syntax.Name name) {
          ScopedSymbol scoped = findScoped(name.value());
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
        Map<SymbolId, SemanticType> incoming = new HashMap<>(flowTypes);
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
        Map<SymbolId, SemanticType> incoming = new HashMap<>(flowTypes);
        pushScope(loop.span());
        applyNarrowings(narrowingsFor(loop.condition(), true));
        controls.addFirst(ControlContext.loop());
        analyzeStatements(loop.body());
        controls.removeFirst();
        popScope();
        Map<SymbolId, SemanticType> bodyFlow = new HashMap<>(flowTypes);
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

  private SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    SemanticType type =
        switch (expression) {
          case Syntax.IntegerLiteral integer ->
              numericIntegerType(integer.value(), expected, integer.span());
          case Syntax.DecimalLiteral decimal ->
              numericDecimalType(decimal.value(), expected, decimal.span());
          case Syntax.CodePointLiteral ignored -> SemanticType.CODE_POINT;
          case Syntax.BooleanLiteral ignored -> SemanticType.BOOLEAN;
          case Syntax.NullLiteral literal -> analyzeNull(literal, expected);
          case Syntax.StringLiteralExpr ignored -> SemanticType.STRING;
          case Syntax.ArrayLiteral array -> analyzeArray(array, expected);
          case Syntax.Name name -> analyzeNameValue(name, expected);
          case Syntax.Unary unary -> analyzeUnary(unary, expected);
          case Syntax.Binary binary -> analyzeBinary(binary, expected);
          case Syntax.Call call -> analyzeCall(call, expected);
          case Syntax.Member member -> memberType(member);
          case Syntax.Lambda lambda -> analyzeLambda(lambda, expected);
          case Syntax.MethodReference reference -> analyzeMethodReference(reference, expected);
          case Syntax.Index index -> analyzeIndex(index);
          case Syntax.SwitchExpression switchExpression ->
              analyzeSwitch(switchExpression, expected);
        };
    if (expected != null && type.equals(SemanticType.DYNAMIC)) {
      type = expected;
    }
    semanticTypes.put(expression.span(), type);
    return type;
  }

  private SemanticType analyzeNameValue(Syntax.Name name, SemanticType expected) {
    ScopedSymbol scoped = findScoped(name.value());
    if (scoped != null) {
      Symbol symbol = scopedSymbol(scoped);
      if (!lambdaLocals.isEmpty()
          && !lambdaLocals.getFirst().contains(scoped.id())
          && (symbol.kind() == SymbolKind.LOCAL_VARIABLE
              || symbol.kind() == SymbolKind.PARAMETER
              || symbol.kind() == SymbolKind.SELF)) {
        capturedLocals.add(scoped.id());
        if (assignedLocals.contains(scoped.id())) reportMutableCapture(scoped.id(), name.span());
      }
      return lookup(name.value(), name.span());
    }
    List<Syntax.FunctionDecl> candidates = resolveFunctions(name.value());
    if (!candidates.isEmpty()) {
      if (expected == null || !expected.isFunction()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "function reference '" + name.value() + "' requires an expected function type",
            name.span());
        return SemanticType.DYNAMIC;
      }
      List<FunctionReferenceResolution> matches =
          candidates.stream()
              .map(
                  candidate ->
                      resolveFunctionReference(candidate, functionType(candidate), expected))
              .flatMap(Optional::stream)
              .toList();
      if (matches.size() != 1) {
        diagnostics.error(
            TYPE_MISMATCH,
            matches.isEmpty()
                ? "no overload of '" + name.value() + "' matches " + expected.displayName()
                : "function reference '"
                    + name.value()
                    + "' is ambiguous for "
                    + expected.displayName(),
            name.span());
        return SemanticType.DYNAMIC;
      }
      FunctionReferenceResolution resolution = matches.getFirst();
      Syntax.FunctionDecl selected = resolution.declaration();
      bindDeclarationUse(name.span(), name.value(), selected);
      functionReferenceTypeArguments.put(name.span(), resolution.reifiedArguments());
      return expected.nonNullable();
    }
    return lookup(name.value(), name.span());
  }

  private SemanticType functionType(Syntax.FunctionDecl declaration) {
    Map<String, SemanticType> parameters = functionTypeParameters(declaration);
    return SemanticType.function(
        functionReturnType(declaration, parameters),
        declaration.parameters().stream()
            .map(parameter -> resolveDeclarationType(parameter.type(), declaration, parameters))
            .toList());
  }

  private SemanticType functionReturnType(
      Syntax.FunctionDecl declaration, Map<String, SemanticType> typeParameters) {
    return declaration
        .returnType()
        .map(type -> resolveDeclarationType(type, declaration, typeParameters))
        .orElseGet(
            () -> {
              Syntax.ClassDecl owner = ownerOf(declaration);
              return owner == null ? SemanticType.VOID : classSelfType(owner);
            });
  }

  private SemanticType analyzeLambda(Syntax.Lambda lambda, SemanticType expected) {
    SemanticType expectedFunction = expected != null && expected.isFunction() ? expected : null;
    if (expectedFunction != null
        && expectedFunction.functionParameterTypes().size() != lambda.parameters().size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "lambda requires "
              + expectedFunction.functionParameterTypes().size()
              + " parameter(s), found "
              + lambda.parameters().size(),
          lambda.span());
      expectedFunction = null;
    }
    List<SemanticType> parameterTypes = new ArrayList<>();
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      SemanticType contextual =
          expectedFunction == null ? null : expectedFunction.functionParameterTypes().get(index);
      SemanticType explicit =
          parameter
              .type()
              .map(
                  type -> {
                    validateType(type, false);
                    return resolveType(type, activeTypeParameters);
                  })
              .orElse(null);
      if (explicit != null && contextual != null)
        requireType(contextual, explicit, parameter.span());
      SemanticType resolved = explicit != null ? explicit : contextual;
      if (resolved == null) {
        diagnostics.error(TYPE_MISMATCH, "cannot infer lambda parameter type", parameter.span());
        resolved = SemanticType.DYNAMIC;
      }
      parameterTypes.add(resolved);
    }
    SemanticType previousReturn = expectedReturnType;
    boolean previousImplicitSelfReturn = implicitSelfReturn;
    SemanticType declaredContextualReturn =
        lambda
            .returnType()
            .map(
                type -> {
                  validateType(type, true);
                  return resolveType(type, activeTypeParameters);
                })
            .orElse(expectedFunction == null ? null : expectedFunction.functionReturnType());
    SemanticType contextualReturn =
        declaredContextualReturn != null
                && declaredContextualReturn.kind() == SemanticType.Kind.TYPE_PARAMETER
            ? null
            : declaredContextualReturn;
    if (lambda.returnType().isPresent() && expectedFunction != null) {
      requireType(
          expectedFunction.functionReturnType(),
          declaredContextualReturn,
          lambda.returnType().orElseThrow().span());
    }
    expectedReturnType = contextualReturn == null ? SemanticType.DYNAMIC : contextualReturn;
    implicitSelfReturn = false;
    pushScope(lambda.span());
    Set<SymbolId> localSymbols = new HashSet<>();
    lambdaLocals.addFirst(localSymbols);
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      Symbol symbol =
          register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              parameterTypes.get(index),
              parameter.nameSpan(),
              currentCallable,
              List.of(),
              List.of());
      declareExisting(
          parameter.name(), parameterTypes.get(index), parameter.nameSpan(), symbol.id());
      localSymbols.add(symbol.id());
    }
    SemanticType result = contextualReturn;
    int last = lambda.body().size() - 1;
    for (int index = 0; index < lambda.body().size(); index++) {
      Syntax.Statement statement = lambda.body().get(index);
      if (index == last && statement instanceof Syntax.ExpressionStatement expression) {
        result = typeOf(expression.expression(), contextualReturn);
        if (contextualReturn != null)
          requireAssignable(contextualReturn, result, expression.span());
      } else {
        analyzeStatement(statement);
      }
    }
    popScope();
    lambdaLocals.removeFirst();
    expectedReturnType = previousReturn;
    implicitSelfReturn = previousImplicitSelfReturn;
    if (result == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "lambda return type requires an expected type or a final expression",
          lambda.span());
      result = SemanticType.DYNAMIC;
    }
    return SemanticType.function(result, parameterTypes);
  }

  private Symbol scopedSymbol(ScopedSymbol scoped) {
    return symbols.get(scoped.id());
  }

  private void reportMutableCapture(SymbolId symbol, SourceSpan span) {
    if (reportedMutableCaptures.add(symbol)) {
      diagnostics.error(
          INVALID_CONTROL,
          "captured local '" + symbols.get(symbol).name() + "' must be effectively final",
          span);
    }
  }

  private SemanticType analyzeMethodReference(
      Syntax.MethodReference reference, SemanticType expected) {
    SemanticType receiver = typeOf(reference.receiver(), null);
    if (expected == null || !expected.isFunction()) {
      diagnostics.error(
          TYPE_MISMATCH, "method reference requires an expected function type", reference.span());
      return SemanticType.DYNAMIC;
    }
    Syntax.ClassDecl owner = resolveClass(receiver.nonNullable());
    if (owner == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type '" + receiver.displayName() + "' has no source methods",
          reference.span());
      return SemanticType.DYNAMIC;
    }
    Map<String, SemanticType> substitutions = classSubstitutions(owner, receiver.nonNullable());
    List<FunctionReferenceResolution> matches =
        owner.methods().stream()
            .filter(method -> method.name().equals(reference.name()))
            .map(
                method -> {
                  Map<String, SemanticType> parameters = typeParameters(method, owner);
                  SemanticType pattern =
                      SemanticType.function(
                              functionReturnType(method, parameters),
                              method.parameters().stream()
                                  .map(
                                      parameter ->
                                          resolveDeclarationType(
                                              parameter.type(), method, parameters))
                                  .toList())
                          .substitute(substitutions);
                  return resolveFunctionReference(method, pattern, expected);
                })
            .flatMap(Optional::stream)
            .toList();
    if (matches.size() != 1) {
      diagnostics.error(
          TYPE_MISMATCH,
          matches.isEmpty()
              ? "no method '" + reference.name() + "' matches " + expected.displayName()
              : "method reference '"
                  + reference.name()
                  + "' is ambiguous for "
                  + expected.displayName(),
          reference.span());
      return SemanticType.DYNAMIC;
    }
    FunctionReferenceResolution resolution = matches.getFirst();
    Syntax.FunctionDecl selected = resolution.declaration();
    bindings.put(reference.nameSpan(), declarationSymbols.get(selected));
    functionReferenceTypeArguments.put(reference.span(), resolution.reifiedArguments());
    return expected.nonNullable();
  }

  private Optional<FunctionReferenceResolution> resolveFunctionReference(
      Syntax.FunctionDecl declaration, SemanticType pattern, SemanticType expected) {
    SemanticType target = expected.nonNullable();
    Symbol symbol = symbols.get(declarationSymbols.get(declaration));
    if (symbol.typeParameters().isEmpty()) {
      return pattern.equals(target)
          ? Optional.of(new FunctionReferenceResolution(declaration, List.of()))
          : Optional.empty();
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(
            symbol.typeParameters().stream().map(TypeParameterInfo::type).toList());
    constrainInference(solver, pattern, target);
    TypeConstraintSolver.Solution solution = solver.solve();
    if (!solution.missing().isEmpty() || !solution.conflicts().isEmpty()) {
      return Optional.empty();
    }
    List<SemanticType> arguments =
        symbol.typeParameters().stream()
            .map(parameter -> solution.substitutions().get(parameter.type().identity()))
            .toList();
    if (arguments.stream().anyMatch(java.util.Objects::isNull)) return Optional.empty();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      TypeParameterInfo parameter = symbol.typeParameters().get(index);
      SemanticType bound =
          parameter
              .upperBound()
              .map(value -> value.substitute(solution.substitutions()))
              .orElse(null);
      if (bound != null && !isAssignable(bound, arguments.get(index))) return Optional.empty();
    }
    return pattern.substitute(solution.substitutions()).equals(target)
        ? Optional.of(new FunctionReferenceResolution(declaration, arguments))
        : Optional.empty();
  }

  private SemanticType analyzeSwitch(
      Syntax.SwitchExpression switchExpression, SemanticType expected) {
    SemanticType valueType = typeOf(switchExpression.value(), null);
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    PatternCoverage<SemanticType> coverage = new PatternCoverage<>(new SemanticPatternDomain());
    ControlContext context = ControlContext.switchExpression(expected);
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      pushScope(switchCase.span());
      PatternCoverage.Pattern pattern = analyzePattern(switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        diagnostics.error(
            INVALID_CONTROL, "switch case is unreachable", switchCase.pattern().span());
      }
      previous.add(pattern);
      controls.addFirst(context);
      analyzeStatements(switchCase.body());
      controls.removeFirst();
      popScope();
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      diagnostics.error(INVALID_CONTROL, "switch is not exhaustive", switchExpression.span());
    }
    SemanticType result = context.resultType();
    if (result == null) return SemanticType.VOID;
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      if (!definitelyYields(switchCase.body())) {
        diagnostics.error(
            INVALID_CONTROL, "switch expression case must produce a value", switchCase.span());
      }
    }
    return result;
  }

  private PatternCoverage.Pattern analyzePattern(Syntax.Pattern pattern, SemanticType expected) {
    if (expected.isNullable() && !(pattern instanceof Syntax.NullPattern)) {
      if (pattern instanceof Syntax.WildcardPattern) return PatternCoverage.Pattern.any();
      return PatternCoverage.Pattern.constructor(
          "$value", List.of(analyzeNonNullPattern(pattern, expected.nonNullable())));
    }
    if (pattern instanceof Syntax.NullPattern) {
      if (!expected.isNullable()) {
        diagnostics.error(TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return analyzeNonNullPattern(pattern, expected.nonNullable());
  }

  private PatternCoverage.Pattern analyzeNonNullPattern(
      Syntax.Pattern pattern, SemanticType expected) {
    return switch (pattern) {
      case Syntax.WildcardPattern ignored -> PatternCoverage.Pattern.any();
      case Syntax.BindingPattern binding -> {
        validateType(binding.type(), false);
        SemanticType type = resolveType(binding.type(), activeTypeParameters);
        if (!type.equals(expected)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "pattern type " + type.displayName() + " does not match " + expected.displayName(),
              binding.type().span());
        }
        Symbol symbol =
            register(
                binding,
                binding.name(),
                SymbolKind.LOCAL_VARIABLE,
                type,
                binding.nameSpan(),
                currentCallable,
                List.of(),
                List.of());
        declareExisting(binding.name(), type, binding.nameSpan(), symbol.id());
        if (!lambdaLocals.isEmpty()) lambdaLocals.getFirst().add(symbol.id());
        yield PatternCoverage.Pattern.any();
      }
      case Syntax.VariantPattern variant -> analyzeVariantPattern(variant, expected);
      case Syntax.IntegerPattern integer -> {
        SemanticType literalType = numericIntegerType(integer.value(), expected, integer.span());
        requireType(expected, literalType, integer.span());
        semanticTypes.put(integer.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? integer.value()
                    : NumericTypes.materialize(integer.value(), literalType)),
            List.of());
      }
      case Syntax.DecimalPattern decimal -> {
        SemanticType literalType = numericDecimalType(decimal.value(), expected, decimal.span());
        requireType(expected, literalType, decimal.span());
        semanticTypes.put(decimal.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? decimal.value()
                    : NumericTypes.materialize(decimal.value(), literalType)),
            List.of());
      }
      case Syntax.CodePointPattern codePoint -> {
        requireType(SemanticType.CODE_POINT, expected, codePoint.span());
        yield PatternCoverage.Pattern.constructor("codepoint:" + codePoint.value(), List.of());
      }
      case Syntax.BooleanPattern bool -> {
        requireType(SemanticType.BOOLEAN, expected, bool.span());
        yield PatternCoverage.Pattern.constructor("boolean:" + bool.value(), List.of());
      }
      case Syntax.StringPattern string -> {
        requireType(SemanticType.STRING, expected, string.span());
        yield PatternCoverage.Pattern.constructor("string:" + string.value(), List.of());
      }
      case Syntax.NullPattern ignored -> {
        diagnostics.error(TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
        yield PatternCoverage.Pattern.constructor("$null", List.of());
      }
    };
  }

  private PatternCoverage.Pattern analyzeVariantPattern(
      Syntax.VariantPattern pattern, SemanticType expected) {
    Syntax.EnumDecl enumDecl = resolveEnum(expected);
    if (enumDecl == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern requires an enum value, found " + expected.displayName(),
          pattern.span());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    Syntax.EnumVariant variant =
        enumDecl.variants().stream()
            .filter(candidate -> candidate.name().equals(pattern.name()))
            .findFirst()
            .orElse(null);
    if (variant == null) {
      diagnostics.error(
          UNKNOWN_NAME,
          "enum '" + enumDecl.name() + "' has no variant '" + pattern.name() + "'",
          pattern.nameSpan());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    bindings.put(pattern.nameSpan(), declarationSymbols.get(variant));
    Map<String, SemanticType> substitutions = enumSubstitutions(enumDecl, expected);
    List<SemanticType> payloadTypes =
        variant.parameters().stream()
            .map(
                parameter ->
                    resolveDeclarationType(
                            parameter.type(), parameter, enumTypeParameters(enumDecl))
                        .substitute(substitutions))
            .toList();
    if (pattern.arguments().size() != payloadTypes.size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern '"
              + pattern.name()
              + "' requires "
              + payloadTypes.size()
              + " argument(s), found "
              + pattern.arguments().size(),
          pattern.span());
    }
    List<PatternCoverage.Pattern> arguments = new ArrayList<>();
    for (int index = 0;
        index < Math.min(pattern.arguments().size(), payloadTypes.size());
        index++) {
      arguments.add(analyzePattern(pattern.arguments().get(index), payloadTypes.get(index)));
    }
    return PatternCoverage.Pattern.constructor("variant:" + variant.name(), arguments);
  }

  private void analyzeBreak(Syntax.BreakStatement statement) {
    if (controls.isEmpty()) {
      diagnostics.error(
          INVALID_CONTROL, "break is only valid inside for or switch", statement.span());
      if (statement.value() != null) typeOf(statement.value(), null);
      return;
    }
    ControlContext context = controls.getFirst();
    if (context.kind() != ControlKind.SWITCH) {
      if (statement.value() != null) {
        diagnostics.error(INVALID_CONTROL, "loop break cannot produce a value", statement.span());
        typeOf(statement.value(), null);
      }
      return;
    }
    if (statement.value() == null) {
      diagnostics.error(INVALID_CONTROL, "switch break must produce a value", statement.span());
      return;
    }
    SemanticType actual = typeOf(statement.value(), context.resultType());
    if (context.resultType() == null || context.resultType().equals(SemanticType.DYNAMIC)) {
      context.setResultType(actual);
    } else {
      requireAssignable(context.resultType(), actual, statement.value().span());
    }
  }

  private TypeProbe probeType(Syntax.Expression expression, SemanticType expected) {
    AnalysisCheckpoint checkpoint = checkpoint();
    SemanticType type = typeOf(expression, expected);
    boolean hasErrors = diagnostics.hasErrorsSince(checkpoint.diagnosticMark());
    restore(checkpoint);
    return new TypeProbe(type, hasErrors);
  }

  private AnalysisCheckpoint checkpoint() {
    return new AnalysisCheckpoint(
        Map.copyOf(bindings),
        Map.copyOf(semanticTypes),
        Map.copyOf(resolvedCalls),
        Map.copyOf(functionReferenceTypeArguments),
        Map.copyOf(iterations),
        Map.copyOf(indexes),
        Map.copyOf(flowTypes),
        semanticScopes.size(),
        diagnostics.mark());
  }

  private void restore(AnalysisCheckpoint checkpoint) {
    restore(bindings, checkpoint.bindings());
    restore(semanticTypes, checkpoint.semanticTypes());
    restore(resolvedCalls, checkpoint.resolvedCalls());
    restore(functionReferenceTypeArguments, checkpoint.functionReferenceTypeArguments());
    restore(iterations, checkpoint.iterations());
    restore(indexes, checkpoint.indexes());
    restore(flowTypes, checkpoint.flowTypes());
    semanticScopes.subList(checkpoint.semanticScopeCount(), semanticScopes.size()).clear();
    diagnostics.rollback(checkpoint.diagnosticMark());
  }

  private static <K, V> void restore(Map<K, V> target, Map<K, V> snapshot) {
    target.clear();
    target.putAll(snapshot);
  }

  private SemanticType analyzeNull(Syntax.NullLiteral literal, SemanticType expected) {
    if (expected == null || expected.equals(SemanticType.DYNAMIC)) {
      diagnostics.error(UNTYPED_NULL, "null requires an expected nullable type", literal.span());
      return SemanticType.DYNAMIC;
    }
    if (!expected.isNullable()) {
      diagnostics.error(
          NULLABILITY_MISMATCH,
          "null is not assignable to " + expected.displayName(),
          literal.span());
      return SemanticType.DYNAMIC;
    }
    return expected;
  }

  private SemanticType analyzeArray(Syntax.ArrayLiteral array, SemanticType expected) {
    SemanticType expectedArray =
        expected == null
            ? null
            : builtins.resolveCollectionLiteral(expected).map(value -> value.type()).orElse(null);
    SemanticType expectedElement =
        expectedArray != null && expectedArray.arguments().size() == 1
            ? expectedArray.arguments().getFirst()
            : null;
    SemanticType elementType = expectedElement;
    for (Syntax.Expression element : array.elements()) {
      SemanticType current = typeOf(element, expectedElement);
      if (elementType == null && !containsDynamic(current)) {
        elementType = current;
      } else if (elementType != null && !containsDynamic(current)) {
        if (expectedElement != null) {
          if (!isAssignable(elementType, current)) {
            diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          }
        } else {
          SemanticType common = commonType(elementType, current).orElse(null);
          if (common == null) {
            diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          } else {
            elementType = common;
          }
        }
      }
    }
    SemanticType inferredElement = elementType == null ? SemanticType.DYNAMIC : elementType;
    return expectedArray == null
        ? builtins.instantiate("Array", List.of(inferredElement))
        : expectedArray;
  }

  private SemanticType analyzeUnary(Syntax.Unary unary, SemanticType expected) {
    SemanticType required =
        unary.operator() == TokenKind.BANG
            ? SemanticType.BOOLEAN
            : NumericTypes.isLeaf(expected == null ? SemanticType.DYNAMIC : expected)
                ? expected.nonNullable()
                : null;
    SemanticType operand = typeOf(unary.operand(), required);
    if (unary.operator() == TokenKind.BANG) {
      requireType(SemanticType.BOOLEAN, operand, unary.span());
      return SemanticType.BOOLEAN;
    }
    if (!NumericTypes.isLeaf(operand)) {
      diagnostics.error(TYPE_MISMATCH, "numeric negation requires a numeric leaf", unary.span());
      return SemanticType.DYNAMIC;
    }
    return operand;
  }

  private SemanticType analyzeBinary(Syntax.Binary binary, SemanticType expected) {
    if (binary.operator() == TokenKind.QUESTION_QUESTION) {
      SemanticType leftExpected = expected == null ? null : expected.nullable();
      SemanticType left = typeOf(binary.left(), leftExpected);
      if (!left.mayContainNull()) {
        diagnostics.error(TYPE_MISMATCH, "left side of ?? must be nullable", binary.left().span());
      }
      SemanticType result = left.equals(SemanticType.DYNAMIC) ? expected : left.nonNullable();
      SemanticType right = typeOf(binary.right(), result);
      if (result == null) return right;
      requireAssignable(result, right, binary.right().span());
      return result;
    }
    SemanticType left;
    SemanticType right;
    if ((binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL)
        && binary.left() instanceof Syntax.NullLiteral) {
      right = typeOf(binary.right(), null);
      left = typeOf(binary.left(), right);
    } else {
      SemanticType numericExpected =
          expected != null && NumericTypes.isLeaf(expected) ? expected.nonNullable() : null;
      left = typeOf(binary.left(), numericExpected);
      right = null;
    }
    if (right == null) {
      if (binary.operator() == TokenKind.AND_AND) {
        Map<SymbolId, SemanticType> incoming = new HashMap<>(flowTypes);
        pushScope(binary.right().span());
        applyNarrowings(narrowingsFor(binary.left(), true));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        popScope();
        replaceFlow(incoming);
      } else if (binary.operator() == TokenKind.OR_OR) {
        Map<SymbolId, SemanticType> incoming = new HashMap<>(flowTypes);
        pushScope(binary.right().span());
        applyNarrowings(narrowingsFor(binary.left(), false));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        popScope();
        replaceFlow(incoming);
      } else {
        right = typeOf(binary.right(), left);
      }
    }
    return switch (binary.operator()) {
      case PLUS -> {
        if (left.equals(SemanticType.STRING) && right.equals(SemanticType.STRING)) {
          yield SemanticType.STRING;
        }
        yield requireNumericLeaves(left, right, binary.span()) ? left : SemanticType.DYNAMIC;
      }
      case MINUS, STAR, SLASH, PERCENT -> {
        yield requireNumericLeaves(left, right, binary.span()) ? left : SemanticType.DYNAMIC;
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        requireNumericLeaves(left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case AND_AND, OR_OR -> {
        requireBoth(SemanticType.BOOLEAN, left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        if (!isAssignable(left, right) && !isAssignable(right, left)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "cannot compare " + left.displayName() + " with " + right.displayName(),
              binary.span());
        }
        yield SemanticType.BOOLEAN;
      }
      default -> SemanticType.DYNAMIC;
    };
  }

  private SemanticType numericIntegerType(
      java.math.BigInteger value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.integerLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  private SemanticType numericDecimalType(
      java.math.BigDecimal value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.decimalLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  private boolean requireNumericLeaves(SemanticType left, SemanticType right, SourceSpan span) {
    if (NumericTypes.isLeaf(left) && left.equals(right)) return true;
    diagnostics.error(
        TYPE_MISMATCH,
        "numeric operands require the same concrete leaf type; found "
            + left.displayName()
            + " and "
            + right.displayName(),
        span);
    return false;
  }

  private SemanticType analyzeCall(Syntax.Call call, SemanticType expected) {
    if (call.callee() instanceof Syntax.Name name) {
      return analyzeNamedCall(name, call, expected);
    }
    if (call.callee() instanceof Syntax.Member member) {
      if (member.receiver() instanceof Syntax.Name receiverName
          && (resolveEnum(receiverName.value()) != null
              || !builtins.typeMembers(receiverName.value(), member.name()).isEmpty())) {
        return analyzeMethodCall(member, call, expected, null);
      }
      SemanticType nullableReceiver = typeOf(member.receiver(), null);
      SemanticType memberType = memberTypeWithoutDiagnostics(member, nullableReceiver);
      if (memberType != null && memberType.isFunction()) {
        semanticTypes.put(member.span(), memberType);
        return analyzeFunctionInvocation(call, memberType, currentCallable);
      }
      return analyzeMethodCall(member, call, expected, nullableReceiver);
    }
    SemanticType calleeType = typeOf(call.callee(), null);
    if (calleeType.isFunction())
      return analyzeFunctionInvocation(call, calleeType, currentCallable);
    diagnostics.error(INVALID_CALL, "expression is not callable", call.callee().span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private SemanticType analyzeFunctionInvocation(
      Syntax.Call call, SemanticType function, SymbolId target) {
    List<ParameterInfo> parameters =
        java.util.stream.IntStream.range(0, function.functionParameterTypes().size())
            .mapToObj(
                index ->
                    new ParameterInfo(
                        "argument" + index, function.functionParameterTypes().get(index)))
            .toList();
    return recordCall(
        call,
        call.callee().span(),
        ResolvedCall.Kind.INVOKE,
        target,
        parameters,
        List.of(),
        function.functionReturnType());
  }

  private SemanticType memberTypeWithoutDiagnostics(
      Syntax.Member member, SemanticType nullableReceiver) {
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    Syntax.ClassDecl owner = resolveClass(receiver);
    if (owner == null) return null;
    Syntax.FieldDecl field =
        owner.fields().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .orElse(null);
    if (field == null) return null;
    if (field.visibility() == Syntax.Visibility.PRIVATE && currentClass != owner) {
      diagnostics.error(
          UNKNOWN_NAME,
          "field '" + member.name() + "' is private in class '" + owner.name() + "'",
          member.nameSpan());
    }
    bindings.put(member.nameSpan(), declarationSymbols.get(field));
    SemanticType type =
        resolveDeclarationType(field.type(), field, classTypeParameters(owner))
            .substitute(classSubstitutions(owner, receiver));
    return safeAccessResult(member, nullableReceiver, type);
  }

  private SemanticType analyzeNamedCall(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    String callee = name.value();
    ScopedSymbol scoped = findScoped(callee);
    if (scoped != null && scoped.declaredType().isFunction()) {
      SemanticType function = scoped.declaredType();
      bindings.put(name.span(), scoped.id());
      semanticTypes.put(name.span(), function);
      List<ParameterInfo> parameters =
          java.util.stream.IntStream.range(0, function.functionParameterTypes().size())
              .mapToObj(
                  index ->
                      new ParameterInfo(
                          "argument" + index, function.functionParameterTypes().get(index)))
              .toList();
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INVOKE,
          scoped.id(),
          parameters,
          List.of(),
          function.functionReturnType());
    }
    builtins.type(callee).ifPresent(symbol -> bindings.put(name.span(), symbol.id()));
    List<Symbol> builtinFunctions = builtins.globals(callee);
    if (!builtinFunctions.isEmpty()) {
      if (name.diamond()) {
        diagnostics.error(
            INVALID_CALL, "diamond is only valid for generic constructors", name.span());
      }
      Symbol symbol = selectBuiltinOverload(builtinFunctions, call, name.span());
      if (symbol == null) return SemanticType.DYNAMIC;
      bindings.put(name.span(), symbol.id());
      validateTypeArgumentCount(callee, 0, name.typeArguments(), name.span());
      name.typeArguments().forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          symbol.parameters(),
          List.of(),
          symbol.type());
    }
    SemanticType constructedType = constructedType(name, call, expected);
    Optional<List<ParameterInfo>> constructor = builtins.constructorParameters(constructedType);
    if (constructor.isPresent()) {
      Symbol target = builtins.type(callee).orElseThrow();
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          target.id(),
          constructor.orElseThrow(),
          List.of(),
          constructedType);
    }
    Syntax.ClassDecl classDecl = resolveClass(callee);
    if (classDecl != null) {
      bindDeclarationUse(name.span(), callee, classDecl);
      Map<String, SemanticType> substitutions = classSubstitutions(classDecl, constructedType);
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CONSTRUCT,
          declarationSymbols.get(classDecl),
          fieldParameters(classDecl, substitutions),
          List.of(),
          constructedType);
    }
    List<Syntax.FunctionDecl> functionCandidates = resolveFunctions(callee);
    if (!functionCandidates.isEmpty() && name.diamond()) {
      diagnostics.error(
          INVALID_CALL, "diamond is only valid for generic constructors", name.span());
    }
    SourceCallResolution resolution =
        resolveSourceCall(
            functionCandidates,
            name.typeArguments(),
            call,
            expected,
            Map.of(),
            name.span(),
            "function");
    if (resolution != null) {
      bindDeclarationUse(name.span(), callee, resolution.declaration());
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CALLABLE,
          declarationSymbols.get(resolution.declaration()),
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (!functionCandidates.isEmpty()) return SemanticType.DYNAMIC;
    diagnostics.error(UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private SemanticType analyzeMethodCall(
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected,
      SemanticType analyzedReceiver) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = resolveEnum(enumName.value());
      if (enumDecl != null) {
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant == null) {
          diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no variant '" + member.name() + "'",
              member.nameSpan());
          analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        Symbol variantSymbol = symbols.get(declarationSymbols.get(variant));
        bindings.put(enumName.span(), declarationSymbols.get(enumDecl));
        bindings.put(member.nameSpan(), variantSymbol.id());
        Map<String, SemanticType> substitutions =
            inferBuiltinTypeArguments(
                variantSymbol, enumName.typeArguments(), call, expected, member.span());
        List<ParameterInfo> parameters =
            variantSymbol.parameters().stream()
                .map(
                    parameter ->
                        new ParameterInfo(
                            parameter.name(), parameter.type().substitute(substitutions)))
                .toList();
        SemanticType result = variantSymbol.type().substitute(substitutions);
        List<SemanticType> reifiedArguments =
            variantSymbol.typeParameters().stream()
                .map(TypeParameterInfo::type)
                .map(parameter -> substitutions.get(parameter.identity()))
                .toList();
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.ENUM_CONSTRUCT,
            variantSymbol.id(),
            parameters,
            reifiedArguments,
            result);
      }
    }
    if (member.receiver() instanceof Syntax.Name typeName) {
      List<Symbol> typeMethods = builtins.typeMembers(typeName.value(), member.name());
      if (!typeMethods.isEmpty()) {
        builtins
            .type(typeName.value())
            .ifPresent(symbol -> bindings.put(typeName.span(), symbol.id()));
        Symbol symbol = selectBuiltinOverload(typeMethods, call, member.nameSpan());
        if (symbol == null) return SemanticType.DYNAMIC;
        bindings.put(member.nameSpan(), symbol.id());
        Map<String, SemanticType> substitutions =
            inferBuiltinTypeArguments(
                symbol, member.typeArguments(), call, expected, member.span());
        List<ParameterInfo> parameters =
            symbol.parameters().stream()
                .map(
                    parameter ->
                        new ParameterInfo(
                            parameter.name(), parameter.type().substitute(substitutions)))
                .toList();
        SemanticType result = symbol.type().substitute(substitutions);
        List<SemanticType> reifiedArguments =
            symbol.typeParameters().stream()
                .map(TypeParameterInfo::type)
                .map(parameter -> substitutions.get(parameter.identity()))
                .toList();
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.INTRINSIC,
            symbol.id(),
            parameters,
            reifiedArguments,
            result);
      }
    }
    SemanticType nullableReceiver =
        analyzedReceiver == null ? typeOf(member.receiver(), null) : analyzedReceiver;
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    if (member.name().isEmpty()) {
      analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    List<Symbol> builtinMembers = builtins.members(receiver, member.name());
    if (!builtinMembers.isEmpty()) {
      List<Symbol> builtinMethods =
          builtinMembers.stream().filter(symbol -> symbol.kind() == SymbolKind.METHOD).toList();
      if (builtinMethods.isEmpty()) {
        diagnostics.error(
            UNKNOWN_NAME,
            "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
            call.span());
        analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      Symbol symbol = selectBuiltinOverload(builtinMethods, call, member.nameSpan());
      if (symbol == null) return SemanticType.DYNAMIC;
      bindings.put(member.nameSpan(), symbol.id());
      validateTypeArgumentCount(member.name(), 0, member.typeArguments(), member.span());
      member
          .typeArguments()
          .forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
      return recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          symbol.parameters(),
          List.of(),
          safeAccessResult(member, nullableReceiver, symbol.type()));
    }
    Syntax.ClassDecl classDecl = resolveClass(receiver);
    if (classDecl != null) {
      if (member.name().equals("copy")) {
        bindings.put(member.nameSpan(), copyMethods.get(receiver.identity()));
        validateTypeArgumentCount(member.name(), 0, member.typeArguments(), member.span());
        member
            .typeArguments()
            .forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.COPY,
            copyMethods.get(receiver.identity()),
            List.of(),
            List.of(),
            safeAccessResult(member, nullableReceiver, receiver));
      }
      Map<String, SemanticType> substitutions = classSubstitutions(classDecl, receiver);
      List<Syntax.FunctionDecl> methods =
          classDecl.methods().stream()
              .filter(candidate -> candidate.name().equals(member.name()))
              .toList();
      List<Syntax.FunctionDecl> accessibleMethods =
          methods.stream()
              .filter(
                  candidate ->
                      candidate.visibility() != Syntax.Visibility.PRIVATE
                          || currentClass == classDecl)
              .toList();
      SourceCallResolution resolution =
          resolveSourceCall(
              accessibleMethods,
              member.typeArguments(),
              call,
              callableExpected(member, nullableReceiver, expected),
              substitutions,
              member.nameSpan(),
              "method");
      if (resolution == null) {
        if (!methods.isEmpty() && accessibleMethods.isEmpty()) {
          diagnostics.error(
              UNKNOWN_NAME,
              "method '" + member.name() + "' is private in class '" + classDecl.name() + "'",
              member.nameSpan());
          analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        if (!methods.isEmpty()) return SemanticType.DYNAMIC;
      } else {
        Syntax.FunctionDecl method = resolution.declaration();
        bindings.put(member.nameSpan(), declarationSymbols.get(method));
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.CALLABLE,
            declarationSymbols.get(method),
            resolution.parameters(),
            resolution.reifiedArguments(),
            safeAccessResult(member, nullableReceiver, resolution.result()));
      }
    }
    List<InterfaceRequirement> interfaceMethods =
        interfaceRequirements(receiver).stream()
            .filter(requirement -> requirement.method().name().equals(member.name()))
            .toList();
    OverloadResolver.Candidate selectedInterfaceMethod =
        overloads.select(
            interfaceMethods.stream()
                .map(
                    requirement ->
                        new OverloadResolver.Candidate(requirement, requirement.parameters()))
                .toList(),
            call,
            member.nameSpan());
    InterfaceRequirement interfaceMethod =
        selectedInterfaceMethod == null
            ? null
            : (InterfaceRequirement) selectedInterfaceMethod.target();
    if (!interfaceMethods.isEmpty() && interfaceMethod == null) return SemanticType.DYNAMIC;
    if (interfaceMethod != null) {
      Symbol target = symbols.get(declarationSymbols.get(interfaceMethod.method()));
      InterfaceCallResolution interfaceResolution =
          resolveInterfaceCall(interfaceMethod, target, member, call, expected);
      bindings.put(member.nameSpan(), target.id());
      return recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTERFACE_CALL,
          target.id(),
          interfaceResolution.parameters(),
          interfaceResolution.reifiedArguments(),
          safeAccessResult(member, nullableReceiver, interfaceResolution.result()));
    }
    if (builtins.isType(receiver.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
          member.span());
      analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH, "type '" + receiver.displayName() + "' has no methods", member.span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private InterfaceCallResolution resolveInterfaceCall(
      InterfaceRequirement requirement,
      Symbol method,
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected) {
    Map<String, SemanticType> substitutions =
        new LinkedHashMap<>(interfaceSubstitutions(requirement.owner(), requirement.receiver()));
    List<TypeConstraintSolver.Conflict> inferenceConflicts = List.of();
    List<SemanticType> explicit =
        member.typeArguments().stream()
            .map(argument -> resolveCheckedType(argument, activeTypeParameters))
            .toList();
    if (!explicit.isEmpty()) {
      validateTypeArgumentCount(
          member.name(), method.typeParameters().size(), member.typeArguments(), member.span());
      for (int index = 0;
          index < Math.min(explicit.size(), method.typeParameters().size());
          index++) {
        substitutions.put(
            method.typeParameters().get(index).type().identity(), explicit.get(index));
      }
    } else {
      TypeConstraintSolver solver =
          new TypeConstraintSolver(
              method.typeParameters().stream().map(TypeParameterInfo::type).toList());
      Set<String> variables = solverVariables(method.typeParameters());
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        constrainInference(solver, requirement.result().substitute(substitutions), expected);
      }
      Map<String, SemanticType> contextualSubstitutions = new LinkedHashMap<>(substitutions);
      contextualSubstitutions.putAll(solver.solve().substitutions());
      List<Integer> indices = overloads.argumentIndices(call, requirement.parameters(), false);
      if (indices != null) {
        for (int index = 0; index < call.arguments().size(); index++) {
          Syntax.Expression argument = call.arguments().get(index).value();
          SemanticType inferencePattern =
              requirement.parameters().get(indices.get(index)).type().substitute(substitutions);
          SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
          SemanticType argumentExpected =
              containsTypeParameter(pattern, variables)
                      && !(argument instanceof Syntax.Lambda && pattern.isFunction())
                  ? null
                  : pattern;
          constrainInference(solver, inferencePattern, typeOf(argument, argumentExpected));
        }
      }
      TypeConstraintSolver.Solution inferred = solver.solve();
      substitutions.putAll(inferred.substitutions());
      inferenceConflicts = inferred.conflicts();
    }
    List<SemanticType> reified = new ArrayList<>();
    for (TypeParameterInfo parameter : method.typeParameters()) {
      SemanticType argument = substitutions.get(parameter.type().identity());
      if (argument == null) {
        diagnostics.error(
            INVALID_CALL, "cannot infer type argument '" + parameter.name() + "'", member.span());
        argument = SemanticType.DYNAMIC;
        substitutions.put(parameter.type().identity(), argument);
      }
      SemanticType bound =
          parameter.upperBound().map(value -> value.substitute(substitutions)).orElse(null);
      if (bound != null && !isAssignable(bound, argument)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type argument '"
                + argument.displayName()
                + "' does not satisfy bound '"
                + bound.displayName()
                + "' for '"
                + parameter.name()
                + "'",
            member.span());
      }
      reified.add(argument);
    }
    Map<String, String> parameterNames =
        method.typeParameters().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    parameter -> parameter.type().identity(),
                    TypeParameterInfo::name,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (TypeConstraintSolver.Conflict conflict : inferenceConflicts) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + parameterNames.get(conflict.variable())
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          member.span());
    }
    List<ParameterInfo> parameters =
        requirement.parameters().stream()
            .map(value -> new ParameterInfo(value.name(), value.type().substitute(substitutions)))
            .toList();
    return new InterfaceCallResolution(
        parameters, requirement.result().substitute(substitutions), reified);
  }

  private SemanticType memberType(Syntax.Member member) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = resolveEnum(enumName.value());
      if (enumDecl != null) {
        bindings.put(enumName.span(), declarationSymbols.get(enumDecl));
        enumDecl.variants().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .map(declarationSymbols::get)
            .ifPresent(id -> bindings.put(member.nameSpan(), id));
        if (enumDecl.variants().stream().noneMatch(value -> value.name().equals(member.name()))) {
          diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no member '" + member.name() + "'",
              member.span());
        }
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(value -> value.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant != null && !variant.parameters().isEmpty()) {
          diagnostics.error(
              INVALID_CALL,
              "enum variant '" + member.name() + "' requires construction arguments",
              member.span());
        }
        return appliedType(enumDecl.name(), enumName.typeArguments(), enumName.span());
      }
    }
    SemanticType nullableReceiverType = typeOf(member.receiver(), null);
    SemanticType receiverType = accessibleReceiverType(member, nullableReceiverType);
    if (member.name().isEmpty()) return SemanticType.DYNAMIC;
    Optional<Symbol> builtinMember = builtins.member(receiverType, member.name());
    if (builtinMember.isPresent() && builtinMember.orElseThrow().kind() != SymbolKind.METHOD) {
      Symbol symbol = builtinMember.orElseThrow();
      bindings.put(member.nameSpan(), symbol.id());
      return safeAccessResult(member, nullableReceiverType, symbol.type());
    }
    if (builtins.isType(receiverType.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiverType.displayName() + "' has no field '" + member.name() + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    Syntax.ClassDecl classDecl = resolveClass(receiverType);
    if (classDecl != null) {
      Syntax.FieldDecl field =
          classDecl.fields().stream()
              .filter(candidate -> candidate.name().equals(member.name()))
              .findFirst()
              .orElse(null);
      if (field != null) {
        if (field.visibility() == Syntax.Visibility.PRIVATE && currentClass != classDecl) {
          diagnostics.error(
              UNKNOWN_NAME,
              "field '" + member.name() + "' is private in class '" + classDecl.name() + "'",
              member.nameSpan());
        }
        bindings.put(member.nameSpan(), declarationSymbols.get(field));
        SemanticType result =
            resolveDeclarationType(field.type(), field, classTypeParameters(classDecl))
                .substitute(classSubstitutions(classDecl, receiverType));
        return safeAccessResult(member, nullableReceiverType, result);
      }
      diagnostics.error(
          UNKNOWN_NAME,
          "class '" + receiverType.displayName() + "' has no field '" + member.name() + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH,
        "type '" + receiverType.displayName() + "' has no member '" + member.name() + "'",
        member.span());
    return SemanticType.DYNAMIC;
  }

  private List<InterfaceRequirement> interfaceRequirements(SemanticType receiver) {
    SemanticType interfaceType = receiver;
    if (receiver.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      interfaceType = typeParameterBounds.get(receiver.identity());
    }
    if (interfaceType == null) return List.of();
    Syntax.InterfaceDecl root = resolveInterface(interfaceType);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    if (root != null) {
      collectConformances(root, interfaceType, conformances, currentProgram.span());
    } else {
      for (Syntax.InterfaceDecl declaration : interfaces.values()) {
        String identity = symbols.get(declarationSymbols.get(declaration)).type().identity();
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
    return List.copyOf(result.values());
  }

  private Optional<ResolvedIteration> resolveInterfaceIteration(SemanticType iterableType) {
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
                declarationSymbols.get(iterator.method()),
                iteratorInterface,
                declarationSymbols.get(hasNext.method()),
                declarationSymbols.get(next.method()))));
  }

  private Optional<SemanticType> conformanceTo(SemanticType concrete, String interfaceIdentity) {
    if (concrete.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType bound = typeParameterBounds.get(concrete.identity());
      if (bound == null) return Optional.empty();
      return conformanceTo(bound, interfaceIdentity);
    }
    Syntax.InterfaceDecl directInterface = interfaceByIdentity(concrete.identity());
    if (directInterface != null) {
      Map<String, SemanticType> conformances = new LinkedHashMap<>();
      collectConformances(directInterface, concrete, conformances, currentProgram.span());
      return Optional.ofNullable(conformances.get(interfaceIdentity));
    }
    Optional<SemanticType> builtinConformance =
        builtins.protocolConformances(concrete).stream()
            .filter(value -> value.identity().equals(interfaceIdentity))
            .findFirst();
    if (builtinConformance.isPresent()) return builtinConformance;
    Syntax.ClassDecl declaration = resolveClass(concrete);
    if (declaration == null) return Optional.empty();
    Syntax.Program previous = currentProgram;
    currentProgram = declarationPrograms.get(declaration);
    Map<String, SemanticType> parameters = classTypeParameters(declaration);
    Map<String, SemanticType> substitutions = classSubstitutions(declaration, concrete);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : declaration.implementedInterfaces()) {
      SemanticType conformance = resolveType(interfaceRef, parameters).substitute(substitutions);
      Syntax.InterfaceDecl contract = resolveInterface(conformance);
      if (contract != null) {
        collectConformances(contract, conformance, conformances, interfaceRef.span());
      }
    }
    currentProgram = previous;
    return Optional.ofNullable(conformances.get(interfaceIdentity));
  }

  private Syntax.InterfaceDecl interfaceByIdentity(String identity) {
    for (Syntax.InterfaceDecl declaration : interfaces.values()) {
      Syntax.Program owner = declarationPrograms.get(declaration);
      String candidate = qualifiedName(owner.packageName(), declaration.name());
      if (declaration.visibility() == Syntax.Visibility.PRIVATE) {
        candidate = fileLocalIdentity(candidate, owner);
      }
      if (candidate.equals(identity)) return declaration;
    }
    return null;
  }

  private SemanticType accessibleReceiverType(Syntax.Member member, SemanticType receiverType) {
    if (receiverType.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType bound = typeParameterBounds.get(receiverType.identity());
      if (bound != null && !bound.mayContainNull()) return receiverType;
    }
    if (!receiverType.mayContainNull()) return receiverType;
    if (!member.nullSafe()) {
      diagnostics.error(
          UNSAFE_NULLABLE_ACCESS,
          "nullable value of type "
              + receiverType.displayName()
              + " must be narrowed or accessed with ?.",
          member.receiver().span());
    }
    return receiverType.equals(SemanticType.DYNAMIC)
        ? SemanticType.DYNAMIC
        : receiverType.nonNullable();
  }

  private static SemanticType safeAccessResult(
      Syntax.Member member, SemanticType receiverType, SemanticType result) {
    if (!member.nullSafe()
        || !receiverType.mayContainNull()
        || result.kind() == SemanticType.Kind.VOID
        || result.equals(SemanticType.DYNAMIC)) {
      return result;
    }
    return result.nullable();
  }

  private static SemanticType callableExpected(
      Syntax.Member member, SemanticType receiverType, SemanticType expected) {
    if (expected != null
        && member.nullSafe()
        && receiverType.mayContainNull()
        && expected.isNullable()) {
      return expected.nonNullable();
    }
    return expected;
  }

  private SemanticType analyzeIndex(Syntax.Index index) {
    SemanticType receiverType = typeOf(index.receiver(), null);
    SemanticType indexType = typeOf(index.index(), null);
    Optional<dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIndex> resolved =
        builtins.resolveIndex(receiverType);
    if (resolved.isEmpty()) {
      diagnostics.error(TYPE_MISMATCH, "only Array, List, and Map can be indexed", index.span());
      return SemanticType.DYNAMIC;
    }
    dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIndex capability = resolved.orElseThrow();
    indexes.put(
        index.span(),
        new ResolvedIndex(
            capability.kind(),
            capability.keyType(),
            capability.resultType(),
            capability.readIntrinsic(),
            capability.writeIntrinsic()));
    requireType(capability.keyType(), indexType, index.index().span());
    return capability.resultType();
  }

  private SemanticType assignmentTargetType(Syntax.Expression target) {
    return switch (target) {
      case Syntax.Name name -> lookupDeclared(name.value(), name.span());
      case Syntax.Member member -> {
        if (member.nullSafe()) {
          diagnostics.error(TYPE_MISMATCH, "safe access cannot be assigned", member.span());
        }
        yield memberType(member);
      }
      case Syntax.Index index -> analyzeIndex(index);
      default -> {
        diagnostics.error(TYPE_MISMATCH, "invalid assignment target", target.span());
        yield SemanticType.DYNAMIC;
      }
    };
  }

  private SourceCallResolution resolveSourceCall(
      List<Syntax.FunctionDecl> declarations,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span,
      String callableKind) {
    if (declarations.isEmpty()) return null;
    List<SemanticType> explicitTypes =
        explicitTypeArguments.stream()
            .map(argument -> resolveCheckedType(argument, activeTypeParameters))
            .toList();
    List<Syntax.FunctionDecl> arityMatches =
        explicitTypeArguments.isEmpty()
            ? declarations
            : declarations.stream()
                .filter(
                    declaration ->
                        declaration.typeParameters().size() == explicitTypeArguments.size())
                .toList();
    if (arityMatches.isEmpty()) {
      if (declarations.size() == 1) {
        validateTypeArgumentCount(
            declarations.getFirst().name(),
            declarations.getFirst().typeParameters().size(),
            explicitTypeArguments,
            span);
      } else {
        diagnostics.error(
            INVALID_CALL,
            "no overload of "
                + callableKind
                + " '"
                + declarations.getFirst().name()
                + "' accepts "
                + explicitTypeArguments.size()
                + " type argument(s)",
            span);
      }
      analyzeArguments(call.arguments());
      return null;
    }

    List<SourceCallCandidate> structural = new ArrayList<>();
    for (Syntax.FunctionDecl declaration : arityMatches) {
      List<ParameterInfo> unresolved = parametersOf(declaration, ownerSubstitutions);
      List<Integer> indices = overloads.argumentIndices(call, unresolved, false);
      if (indices != null) {
        structural.add(
            sourceCallCandidate(
                declaration,
                explicitTypes,
                !explicitTypeArguments.isEmpty(),
                call,
                indices,
                expected,
                ownerSubstitutions));
      }
    }
    if (structural.isEmpty()) {
      if (arityMatches.size() == 1) {
        overloads.argumentIndices(
            call, parametersOf(arityMatches.getFirst(), ownerSubstitutions), true);
      } else {
        diagnostics.error(
            INVALID_CALL,
            "no overload accepts the supplied argument labels and count",
            call.span());
      }
      return null;
    }

    List<SourceCallCandidate> applicable =
        structural.stream().filter(SourceCallCandidate::applicable).toList();
    int bestScore =
        applicable.stream().mapToInt(SourceCallCandidate::score).min().orElse(Integer.MAX_VALUE);
    List<SourceCallCandidate> best =
        applicable.stream().filter(candidate -> candidate.score() == bestScore).toList();
    if (best.size() == 1) return best.getFirst().resolution();
    if (best.size() > 1) {
      diagnostics.error(INVALID_CALL, "call is ambiguous between multiple overloads", span);
      return null;
    }
    if (structural.size() == 1) {
      SourceCallCandidate candidate = structural.getFirst();
      reportInferenceFailures(candidate, span);
      validateArguments(call, candidate.parameters());
    } else {
      diagnostics.error(INVALID_CALL, "no overload accepts the supplied argument types", span);
    }
    return null;
  }

  private SourceCallCandidate sourceCallCandidate(
      Syntax.FunctionDecl declaration,
      List<SemanticType> explicitTypes,
      boolean hasExplicitTypes,
      Syntax.Call call,
      List<Integer> argumentIndices,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions) {
    Map<String, SemanticType> callableParameters = functionTypeParameters(declaration);
    Set<String> callableParameterIds =
        callableParameters.values().stream()
            .map(SemanticType::identity)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, SemanticType> substitutions = new LinkedHashMap<>(ownerSubstitutions);
    TypeConstraintSolver solver = new TypeConstraintSolver(callableParameters.values());
    Map<String, SemanticType> declarations = typeParameters(declaration, ownerOf(declaration));
    if (hasExplicitTypes) {
      for (int index = 0; index < declaration.typeParameters().size(); index++) {
        SemanticType parameter =
            callableParameters.get(declaration.typeParameters().get(index).name());
        substitutions.put(parameter.identity(), explicitTypes.get(index));
      }
    } else {
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        SemanticType pattern =
            functionReturnType(declaration, declarations).substitute(ownerSubstitutions);
        constrainInference(solver, pattern, expected);
        substitutions.putAll(solver.solve().substitutions());
      }
      for (int index = 0; index < call.arguments().size(); index++) {
        Syntax.Parameter parameter = declaration.parameters().get(argumentIndices.get(index));
        SemanticType pattern =
            resolveDeclarationType(parameter.type(), declaration, declarations)
                .substitute(substitutions);
        SemanticType probeExpected =
            containsTypeParameter(pattern, callableParameterIds) ? null : pattern;
        TypeProbe probe = probeType(call.arguments().get(index).value(), probeExpected);
        constrainInference(solver, pattern, probe.type());
      }
    }

    List<String> missing = new ArrayList<>();
    List<InferenceConflict> conflicts = new ArrayList<>();
    TypeConstraintSolver.Solution inferredTypes = solver.solve();
    for (Syntax.TypeParameter parameterSyntax : declaration.typeParameters()) {
      SemanticType parameter = callableParameters.get(parameterSyntax.name());
      SemanticType inferred = inferredTypes.substitutions().get(parameter.identity());
      if (!hasExplicitTypes && inferred == null) {
        missing.add(parameterSyntax.name());
        substitutions.put(parameter.identity(), SemanticType.DYNAMIC);
      } else if (!hasExplicitTypes) {
        substitutions.put(parameter.identity(), inferred);
      }
    }
    Map<String, String> parameterNames =
        callableParameters.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    entry -> entry.getValue().identity(),
                    Map.Entry::getKey,
                    (left, right) -> left));
    inferredTypes
        .conflicts()
        .forEach(
            conflict ->
                conflicts.add(
                    new InferenceConflict(
                        parameterNames.get(conflict.variable()),
                        conflict.first(),
                        conflict.second())));
    List<ParameterInfo> parameters = parametersOf(declaration, substitutions);
    SemanticType result = functionReturnType(declaration, declarations).substitute(substitutions);
    boolean assignable = true;
    List<BoundViolation> boundViolations = new ArrayList<>();
    for (Syntax.TypeParameter parameterSyntax : declaration.typeParameters()) {
      if (parameterSyntax.upperBound().isEmpty()) continue;
      SemanticType parameter = callableParameters.get(parameterSyntax.name());
      SemanticType actual = substitutions.get(parameter.identity());
      SemanticType bound =
          resolveDeclarationType(
                  parameterSyntax.upperBound().orElseThrow(), declaration, declarations)
              .substitute(substitutions);
      if (actual != null && !isAssignable(bound, actual)) {
        assignable = false;
        boundViolations.add(new BoundViolation(parameterSyntax.name(), bound, actual));
      }
    }
    int score = 0;
    if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
      if (!isPotentiallyAssignable(expected, result)) assignable = false;
      else if (!expected.equals(result)) score++;
    }
    List<ParameterInfo> patterns = parametersOf(declaration, ownerSubstitutions);
    for (int index = 0; index < call.arguments().size(); index++) {
      int parameterIndex = argumentIndices.get(index);
      SemanticType parameter = parameters.get(parameterIndex).type();
      Syntax.Expression argument = call.arguments().get(index).value();
      TypeProbe probe = probeType(argument, parameter);
      SemanticType actual =
          argument instanceof Syntax.NullLiteral ? SemanticType.NULL : probe.type();
      if (probe.hasErrors() || !isPotentiallyAssignable(parameter, actual)) assignable = false;
      score +=
          callCompatibilityScore(
              patterns.get(parameterIndex).type(), parameter, actual, callableParameterIds);
      TypeProbe intrinsicProbe = probeType(argument, null);
      if (!intrinsicProbe.hasErrors() && !parameter.equals(intrinsicProbe.type())) {
        score += isPotentiallyAssignable(parameter, intrinsicProbe.type()) ? 1 : 2;
      }
    }
    List<SemanticType> reifiedArguments =
        declaration.typeParameters().stream()
            .map(
                parameter -> substitutions.get(callableParameters.get(parameter.name()).identity()))
            .toList();
    SourceCallResolution resolution =
        new SourceCallResolution(declaration, parameters, reifiedArguments, result);
    return new SourceCallCandidate(
        resolution,
        List.copyOf(missing),
        List.copyOf(conflicts),
        List.copyOf(boundViolations),
        assignable,
        score);
  }

  private void constrainInference(
      TypeConstraintSolver solver, SemanticType pattern, SemanticType actual) {
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      solver.constrain(pattern, actual);
      return;
    }
    SemanticType alignedActual = inferenceView(pattern, actual);
    if (!pattern.nonNullable().identity().equals(alignedActual.nonNullable().identity())) {
      SemanticType alignedPattern = inferenceView(actual, pattern);
      if (alignedPattern.nonNullable().identity().equals(actual.nonNullable().identity())) {
        solver.constrain(alignedPattern, actual);
      }
      return;
    }
    solver.constrain(pattern, alignedActual);
  }

  private SemanticType inferenceView(SemanticType pattern, SemanticType actual) {
    if (pattern.nonNullable().identity().equals(actual.nonNullable().identity())) return actual;
    for (SemanticType view : nominalViews(actual.nonNullable())) {
      if (pattern.nonNullable().identity().equals(view.nonNullable().identity())) {
        return actual.isNullable() ? view.nullable() : view;
      }
    }
    return actual;
  }

  private List<SemanticType> nominalViews(SemanticType actual) {
    List<SemanticType> result = new ArrayList<>(builtins.protocolConformances(actual));
    Syntax.ClassDecl concrete = resolveClass(actual);
    if (concrete == null) return List.copyOf(result);
    Syntax.Program previous = currentProgram;
    currentProgram = declarationPrograms.get(concrete);
    Map<String, SemanticType> substitutions = classSubstitutions(concrete, actual);
    Map<String, SemanticType> classParameters = classTypeParameters(concrete);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : concrete.implementedInterfaces()) {
      SemanticType conformance =
          resolveType(interfaceRef, classParameters).substitute(substitutions);
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration != null) {
        collectConformances(declaration, conformance, conformances, interfaceRef.span());
      }
    }
    currentProgram = previous;
    result.addAll(conformances.values());
    return List.copyOf(result);
  }

  private boolean isPotentiallyAssignable(SemanticType expected, SemanticType actual) {
    if (expected.equals(SemanticType.DYNAMIC) || actual.equals(SemanticType.DYNAMIC)) return true;
    if (actual.equals(SemanticType.NULL)) return expected.mayContainNull();
    if (expected.equals(SemanticType.NULL)) return false;
    if (actual.isNullable() && !expected.isNullable()) return false;
    if (!expected.nonNullable().identity().equals(actual.nonNullable().identity())
        || expected.arguments().size() != actual.arguments().size()) {
      return isAssignable(expected, actual);
    }
    for (int index = 0; index < expected.arguments().size(); index++) {
      if (!isPotentiallyAssignable(
          expected.arguments().get(index), actual.arguments().get(index))) {
        return false;
      }
    }
    return true;
  }

  private static int callCompatibilityScore(
      SemanticType pattern,
      SemanticType parameter,
      SemanticType actual,
      Set<String> callableParameterIds) {
    int score;
    if (actual.equals(SemanticType.NULL)) {
      score = containsTypeParameter(pattern, callableParameterIds) ? 4 : 2;
    } else if (containsDynamic(actual)) {
      score = 4;
    } else {
      score = parameter.equals(actual) ? 0 : 1;
      if (containsTypeParameter(pattern, callableParameterIds)) score += 3;
    }
    return score;
  }

  private static boolean containsDynamic(SemanticType type) {
    if (type.equals(SemanticType.DYNAMIC)) return true;
    return type.arguments().stream().anyMatch(Analyzer::containsDynamic);
  }

  private static boolean containsTypeParameter(SemanticType type, Set<String> identities) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER && identities.contains(type.identity())) {
      return true;
    }
    return type.arguments().stream()
        .anyMatch(argument -> containsTypeParameter(argument, identities));
  }

  private void reportInferenceFailures(SourceCallCandidate candidate, SourceSpan span) {
    for (String name : candidate.missingTypeArguments()) {
      diagnostics.error(INVALID_CALL, "cannot infer type argument '" + name + "'", span);
    }
    for (InferenceConflict conflict : candidate.conflicts()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + conflict.name()
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          span);
    }
    for (BoundViolation violation : candidate.boundViolations()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type argument '"
              + violation.actual().displayName()
              + "' does not satisfy bound '"
              + violation.bound().displayName()
              + "' for '"
              + violation.name()
              + "'",
          span);
    }
  }

  private Symbol selectBuiltinOverload(List<Symbol> candidates, Syntax.Call call, SourceSpan span) {
    OverloadResolver.Candidate selected =
        overloads.select(
            candidates.stream()
                .map(candidate -> new OverloadResolver.Candidate(candidate, candidate.parameters()))
                .toList(),
            call,
            span);
    return selected == null ? null : (Symbol) selected.target();
  }

  private ArgumentBinding validateArguments(Syntax.Call call, List<ParameterInfo> parameters) {
    List<Integer> parameterIndices = overloads.argumentIndices(call, parameters, true);
    for (int index = 0; index < call.arguments().size(); index++) {
      Syntax.CallArgument argument = call.arguments().get(index);
      int parameterIndex = parameterIndices.get(index);
      if (parameterIndex >= 0) {
        ParameterInfo parameter = parameters.get(parameterIndex);
        requireAssignable(
            parameter.type(), typeOf(argument.value(), parameter.type()), argument.span());
      } else {
        typeOf(argument.value(), null);
      }
    }
    return new ArgumentBinding(parameterIndices);
  }

  private SemanticType recordCall(
      Syntax.Call call,
      SourceSpan calleeSpan,
      ResolvedCall.Kind kind,
      SymbolId target,
      List<ParameterInfo> parameters,
      List<SemanticType> reifiedArguments,
      SemanticType result) {
    ArgumentBinding arguments = validateArguments(call, parameters);
    if (arguments.parameterIndices().stream()
        .anyMatch(index -> index < 0 || index >= parameters.size())) {
      return result;
    }
    resolvedCalls.put(
        call.span(),
        new ResolvedCall(
            kind, target, calleeSpan, arguments, parameters, reifiedArguments, result));
    return result;
  }

  private void analyzeArguments(List<Syntax.CallArgument> arguments) {
    for (Syntax.CallArgument argument : arguments) {
      typeOf(argument.value(), null);
    }
  }

  private void validateType(Syntax.TypeRef type, boolean allowVoid) {
    String name = type.displayName();
    SymbolId typeParameter = activeTypeParameterSymbols.get(type.name());
    if (typeParameter != null) {
      bindings.put(type.span(), typeParameter);
    } else {
      SymbolId alias = importedAlias(type.name());
      if (alias != null) {
        bindings.put(type.span(), alias);
      } else {
        typeSymbol(type.name()).ifPresent(symbol -> bindings.put(type.span(), symbol.id()));
      }
    }
    int arity =
        type.name().equals("Function") ? type.arguments().size() : declaredTypeArity(type.name());
    if (activeTypeParameters.containsKey(type.name())) arity = 0;
    if (type.name().equals("Function") && type.arguments().isEmpty()) {
      diagnostics.error(TYPE_MISMATCH, "Function requires a complete call signature", type.span());
      return;
    }
    if (arity < 0 && !activeTypeParameters.containsKey(type.name())) {
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
    if (!type.name().equals("Function") && arity != type.arguments().size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type '"
              + type.name()
              + "' requires "
              + arity
              + " type argument(s), found "
              + type.arguments().size(),
          type.span());
    }
    for (int index = 0; index < type.arguments().size(); index++) {
      validateType(type.arguments().get(index), type.name().equals("Function") && index == 0);
    }
    validateDeclaredTypeBounds(type);
  }

  private void validateDeclaredTypeBounds(Syntax.TypeRef reference) {
    List<Syntax.TypeParameter> parameters;
    Map<String, SemanticType> declared;
    Object declaration;
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(reference.name());
    Syntax.ClassDecl classDecl = resolveClass(reference.name());
    Syntax.EnumDecl enumDecl = resolveEnum(reference.name());
    if (interfaceDecl != null) {
      parameters = interfaceDecl.typeParameters();
      declared = interfaceTypeParameters(interfaceDecl);
      declaration = interfaceDecl;
    } else if (classDecl != null) {
      parameters = classDecl.typeParameters();
      declared = classTypeParameters(classDecl);
      declaration = classDecl;
    } else if (enumDecl != null) {
      parameters = enumDecl.typeParameters();
      declared = enumTypeParameters(enumDecl);
      declaration = enumDecl;
    } else {
      return;
    }
    if (parameters.size() != reference.arguments().size()) return;
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    List<SemanticType> actualArguments =
        reference.arguments().stream()
            .map(argument -> resolveType(argument, activeTypeParameters))
            .toList();
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
      if (!isAssignable(bound, actual)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type argument '"
                + actual.displayName()
                + "' does not satisfy bound '"
                + bound.displayName()
                + "' for '"
                + parameter.name()
                + "'",
            reference.arguments().get(index).span());
      }
    }
  }

  private void requireBoth(
      SemanticType expected, SemanticType left, SemanticType right, SourceSpan span) {
    requireType(expected, left, span);
    requireType(expected, right, span);
  }

  private void requireType(SemanticType expected, SemanticType actual, SourceSpan span) {
    requireAssignable(expected, actual, span);
  }

  private void requireAssignable(SemanticType expected, SemanticType actual, SourceSpan span) {
    if (!isAssignable(expected, actual)) {
      DiagnosticCode code =
          actual.mayContainNull() && !expected.mayContainNull()
              ? NULLABILITY_MISMATCH
              : TYPE_MISMATCH;
      diagnostics.error(
          code, "expected " + expected.displayName() + " but found " + actual.displayName(), span);
    }
  }

  private boolean isAssignable(SemanticType expected, SemanticType actual) {
    return typeRelations.isAssignable(expected, actual);
  }

  private Optional<SemanticType> commonType(SemanticType left, SemanticType right) {
    Optional<SemanticType> direct = TypeRelations.commonType(left, right);
    if (direct.isPresent()) return direct;
    SemanticType leftValue = left.nonNullable();
    SemanticType rightValue = right.nonNullable();
    if (!isAssignable(STRINGABLE, leftValue) || !isAssignable(STRINGABLE, rightValue)) {
      return Optional.empty();
    }
    return Optional.of(
        left.isNullable() || right.isNullable() ? STRINGABLE.nullable() : STRINGABLE);
  }

  private boolean isNominallyAssignable(SemanticType expected, SemanticType actual) {
    if (actual.isNullable() && !expected.isNullable()) return false;
    if (expected.isNullable() && !actual.isNullable()) {
      return isAssignable(expected.nonNullable(), actual);
    }
    SemanticType bound =
        actual.kind() == SemanticType.Kind.TYPE_PARAMETER
            ? typeParameterBounds.get(actual.identity())
            : null;
    if (bound != null && isAssignable(expected, bound)) return true;
    Syntax.InterfaceDecl required = resolveInterface(expected.nonNullable());
    for (SemanticType conformance : builtins.protocolConformances(actual.nonNullable())) {
      if (expected.nonNullable().equals(conformance)) return true;
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration != null) {
        Map<String, SemanticType> inherited = new LinkedHashMap<>();
        collectConformances(declaration, conformance, inherited, currentProgram.span());
        if (expected.nonNullable().equals(inherited.get(expected.identity()))) return true;
      }
    }
    Syntax.ClassDecl concrete = resolveClass(actual.nonNullable());
    if (required == null || concrete == null) return false;
    Syntax.Program previous = currentProgram;
    currentProgram = declarationPrograms.get(concrete);
    Map<String, SemanticType> substitutions = classSubstitutions(concrete, actual.nonNullable());
    Map<String, SemanticType> classParameters = classTypeParameters(concrete);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : concrete.implementedInterfaces()) {
      SemanticType conformance =
          resolveType(interfaceRef, classParameters).substitute(substitutions);
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration != null) {
        collectConformances(declaration, conformance, conformances, interfaceRef.span());
      }
    }
    currentProgram = previous;
    return expected.nonNullable().equals(conformances.get(expected.identity()));
  }

  private void declareExisting(String name, SemanticType type, SourceSpan span, SymbolId id) {
    ScopeFrame scope = scopes.getFirst();
    if (scope.symbols().putIfAbsent(name, new ScopedSymbol(type, id)) != null) {
      diagnostics.error(DUPLICATE_NAME, "name '" + name + "' is already declared", span);
    } else {
      scope.declarations().add(id);
      flowTypes.put(id, type);
    }
  }

  private void declareSelf(SemanticType type, SourceSpan span) {
    SymbolId id = SymbolId.source(span.source().id(), nextSymbolId++);
    Symbol symbol =
        new Symbol(
            id,
            "this",
            SymbolKind.SELF,
            type,
            Optional.empty(),
            Optional.ofNullable(currentCallable),
            List.of(),
            List.of(),
            "");
    symbols.put(id, symbol);
    declareExisting("this", type, span, symbol.id());
  }

  private SemanticType lookup(String name, SourceSpan span) {
    for (ScopeFrame scope : scopes) {
      ScopedSymbol symbol = scope.symbols().get(name);
      if (symbol != null) {
        bindings.put(span, symbol.id());
        return flowTypes.getOrDefault(symbol.id(), symbol.declaredType());
      }
    }
    diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
    return SemanticType.DYNAMIC;
  }

  private SemanticType lookupDeclared(String name, SourceSpan span) {
    ScopedSymbol symbol = findScoped(name);
    if (symbol == null) {
      diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
      return SemanticType.DYNAMIC;
    }
    bindings.put(span, symbol.id());
    return symbol.declaredType();
  }

  private ScopedSymbol findScoped(String name) {
    for (ScopeFrame scope : scopes) {
      ScopedSymbol symbol = scope.symbols().get(name);
      if (symbol != null) return symbol;
    }
    return null;
  }

  private void invalidateNarrowing(String name) {
    ScopedSymbol symbol = findScoped(name);
    if (symbol == null) return;
    flowTypes.put(symbol.id(), symbol.declaredType());
  }

  private Map<String, SemanticType> narrowingsFor(Syntax.Expression condition, boolean truth) {
    if (condition instanceof Syntax.Unary unary && unary.operator() == TokenKind.BANG) {
      return narrowingsFor(unary.operand(), !truth);
    }
    if (condition instanceof Syntax.Binary binary) {
      if ((binary.operator() == TokenKind.AND_AND && truth)
          || (binary.operator() == TokenKind.OR_OR && !truth)) {
        Map<String, SemanticType> result = new LinkedHashMap<>();
        result.putAll(narrowingsFor(binary.left(), truth));
        result.putAll(narrowingsFor(binary.right(), truth));
        return result;
      }
      if (binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL) {
        Syntax.Name name = nullComparedName(binary);
        boolean nonNull =
            (binary.operator() == TokenKind.BANG_EQUAL && truth)
                || (binary.operator() == TokenKind.EQUAL_EQUAL && !truth);
        if (name != null && nonNull) {
          ScopedSymbol scoped = findScoped(name.value());
          if (scoped != null
              && flowTypes.getOrDefault(scoped.id(), scoped.declaredType()).isNullable()
              && isFlowNarrowable(scoped.id())) {
            return Map.of(
                name.value(),
                flowTypes.getOrDefault(scoped.id(), scoped.declaredType()).nonNullable());
          }
        }
      }
    }
    return Map.of();
  }

  private static Syntax.Name nullComparedName(Syntax.Binary binary) {
    if (binary.left() instanceof Syntax.Name name && binary.right() instanceof Syntax.NullLiteral)
      return name;
    if (binary.right() instanceof Syntax.Name name && binary.left() instanceof Syntax.NullLiteral)
      return name;
    return null;
  }

  private boolean isFlowNarrowable(SymbolId id) {
    Symbol symbol = symbols.get(id);
    return symbol != null
        && (symbol.kind() == SymbolKind.LOCAL_VARIABLE || symbol.kind() == SymbolKind.PARAMETER);
  }

  private void applyNarrowings(Map<String, SemanticType> narrowings) {
    for (Map.Entry<String, SemanticType> entry : narrowings.entrySet()) {
      ScopedSymbol symbol = findScoped(entry.getKey());
      if (symbol != null) {
        flowTypes.put(symbol.id(), entry.getValue());
      }
    }
  }

  private Map<SymbolId, SemanticType> analyzeBranch(
      List<Syntax.Statement> statements,
      Map<String, SemanticType> narrowings,
      Map<SymbolId, SemanticType> incoming) {
    replaceFlow(incoming);
    pushScope(scopeSpan(statements));
    applyNarrowings(narrowings);
    analyzeStatements(statements);
    popScope();
    return new HashMap<>(flowTypes);
  }

  private Map<SymbolId, SemanticType> mergeFlows(
      Map<SymbolId, SemanticType> incoming,
      Map<SymbolId, SemanticType> left,
      Map<SymbolId, SemanticType> right) {
    Map<SymbolId, SemanticType> result = new HashMap<>();
    for (Map.Entry<SymbolId, SemanticType> entry : incoming.entrySet()) {
      SemanticType leftType = left.getOrDefault(entry.getKey(), entry.getValue());
      SemanticType rightType = right.getOrDefault(entry.getKey(), entry.getValue());
      SemanticType merged = entry.getValue();
      if (leftType.equals(rightType)) {
        merged = leftType;
      } else if (leftType.nonNullable().equals(rightType.nonNullable())) {
        merged = leftType.nonNullable().nullable();
      }
      result.put(entry.getKey(), merged);
    }
    return result;
  }

  private void replaceFlow(Map<SymbolId, SemanticType> values) {
    flowTypes.clear();
    flowTypes.putAll(values);
  }

  private void validateContinue(SourceSpan span) {
    if (controls.stream().noneMatch(context -> context.kind() == ControlKind.LOOP)) {
      diagnostics.error(INVALID_CONTROL, "continue is only valid inside for", span);
    }
  }

  private void pushScope(SourceSpan span) {
    scopes.addFirst(new ScopeFrame(new HashMap<>(), new ArrayList<>(), span, scopes.size()));
  }

  private void popScope() {
    ScopeFrame scope = scopes.removeFirst();
    scope.declarations().forEach(flowTypes::remove);
    semanticScopes.add(new SemanticScope(scope.span(), scope.depth(), scope.declarations()));
  }

  private SourceSpan scopeSpan(List<Syntax.Statement> statements) {
    if (statements.isEmpty()) return currentProgram.span();
    return statements.getFirst().span().cover(statements.getLast().span());
  }

  private Symbol register(
      Object declaration,
      String name,
      SymbolKind kind,
      SemanticType type,
      SourceSpan nameSpan,
      SymbolId owner,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> parameters) {
    SymbolId id = SymbolId.source(nameSpan.source().id(), nextSymbolId++);
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
    symbols.put(id, symbol);
    declarationSymbols.put(declaration, id);
    bindings.put(nameSpan, id);
    return symbol;
  }

  private List<TypeParameterInfo> symbolTypeParameters(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> semanticTypes) {
    return parameters.stream()
        .map(
            parameter -> {
              SemanticType type = semanticTypes.get(parameter.name());
              Optional<SemanticType> bound =
                  parameter.upperBound().map(value -> resolveType(value, semanticTypes));
              return new TypeParameterInfo(parameter.name(), type, bound);
            })
        .toList();
  }

  private void registerTypeParameters(
      List<Syntax.TypeParameter> parameters,
      SymbolId owner,
      Map<String, SemanticType> semanticTypes) {
    for (Syntax.TypeParameter parameter : parameters) {
      SymbolId id = SymbolId.source(parameter.nameSpan().source().id(), nextSymbolId++);
      Symbol symbol =
          new Symbol(
              id,
              parameter.name(),
              SymbolKind.TYPE_PARAMETER,
              semanticTypes.get(parameter.name()),
              Optional.of(parameter.nameSpan().location()),
              Optional.of(owner),
              List.of(),
              List.of(),
              "");
      symbols.put(id, symbol);
      declarationSymbols.put(parameter, id);
      bindings.put(parameter.nameSpan(), id);
    }
  }

  private Map<String, SymbolId> typeParameterSymbols(
      Syntax.FunctionDecl function, Syntax.ClassDecl owner) {
    Map<String, SymbolId> result = new LinkedHashMap<>();
    if (owner != null) {
      owner
          .typeParameters()
          .forEach(parameter -> result.put(parameter.name(), declarationSymbols.get(parameter)));
    }
    function
        .typeParameters()
        .forEach(parameter -> result.put(parameter.name(), declarationSymbols.get(parameter)));
    return Map.copyOf(result);
  }

  private Map<String, SymbolId> typeParameterSymbols(List<Syntax.TypeParameter> parameters) {
    Map<String, SymbolId> result = new LinkedHashMap<>();
    parameters.forEach(
        parameter -> result.put(parameter.name(), declarationSymbols.get(parameter)));
    return Map.copyOf(result);
  }

  private void addMember(SymbolId owner, SymbolId member) {
    members.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(member);
  }

  private Optional<Symbol> typeSymbol(String name) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null)
      return Optional.ofNullable(symbols.get(declarationSymbols.get(interfaceDecl)));
    Syntax.ClassDecl classDecl = resolveClass(name);
    if (classDecl != null)
      return Optional.ofNullable(symbols.get(declarationSymbols.get(classDecl)));
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    if (enumDecl != null) return Optional.ofNullable(symbols.get(declarationSymbols.get(enumDecl)));
    return builtins.type(name);
  }

  private List<ParameterInfo> parameters(List<Syntax.Parameter> parameters) {
    return parameters(parameters, Map.of(), activeTypeParameters);
  }

  private List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters, Map<String, SemanticType> substitutions) {
    return parameters(parameters, substitutions, activeTypeParameters);
  }

  private List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarations) {
    return parameters.stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    resolveType(parameter.type(), declarations).substitute(substitutions)))
        .toList();
  }

  private List<ParameterInfo> parametersOf(
      Syntax.FunctionDecl function, Map<String, SemanticType> substitutions) {
    return function.parameters().stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    resolveDeclarationType(
                            parameter.type(), function, typeParameters(function, ownerOf(function)))
                        .substitute(substitutions)))
        .toList();
  }

  private static boolean definitelyReturns(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && definitelyReturns(conditional.thenBody())
          && definitelyReturns(conditional.elseBody())) {
        return true;
      }
    }
    return false;
  }

  private List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields, Map<String, SemanticType> substitutions) {
    return fieldParameters(fields, substitutions, activeTypeParameters);
  }

  private List<ParameterInfo> fieldParameters(
      Syntax.ClassDecl classDecl, Map<String, SemanticType> substitutions) {
    return classDecl.fields().stream()
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    resolveDeclarationType(field.type(), field, classTypeParameters(classDecl))
                        .substitute(substitutions)))
        .toList();
  }

  private List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarations) {
    return fields.stream()
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    resolveType(field.type(), declarations).substitute(substitutions)))
        .toList();
  }

  private SemanticType appliedType(String name, List<Syntax.TypeRef> arguments, SourceSpan span) {
    int arity = declaredTypeArity(name);
    if (arity < 0) return sourceType(name, List.of());
    if (arity != arguments.size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type '" + name + "' requires " + arity + " type argument(s), found " + arguments.size(),
          span);
    }
    List<SemanticType> resolved =
        arguments.stream()
            .map(argument -> resolveCheckedType(argument, activeTypeParameters))
            .toList();
    if (builtins.isType(name)) return builtins.instantiate(name, resolved);
    return sourceType(name, resolved);
  }

  private SemanticType constructedType(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    if (!name.diamond()) return appliedType(name.value(), name.typeArguments(), name.span());
    String typeName = name.value();
    Symbol builtin = builtins.type(typeName).orElse(null);
    Syntax.ClassDecl source = resolveClass(typeName);
    List<TypeParameterInfo> parameters;
    SemanticType prototype;
    List<ParameterInfo> constructorParameters;
    if (builtin != null) {
      parameters = builtin.typeParameters();
      prototype =
          builtins.instantiate(typeName, parameters.stream().map(TypeParameterInfo::type).toList());
      constructorParameters = builtins.constructorParameters(prototype).orElse(List.of());
    } else if (source != null) {
      Map<String, SemanticType> declared = classTypeParameters(source);
      parameters =
          source.typeParameters().stream()
              .map(
                  parameter ->
                      new TypeParameterInfo(parameter.name(), declared.get(parameter.name())))
              .toList();
      prototype =
          sourceType(source.name(), parameters.stream().map(TypeParameterInfo::type).toList());
      constructorParameters = fieldParameters(source, Map.of());
    } else {
      diagnostics.error(UNKNOWN_NAME, "cannot find type '" + typeName + "'", name.span());
      return SemanticType.DYNAMIC;
    }
    if (parameters.isEmpty()) {
      diagnostics.error(INVALID_CALL, "diamond requires a generic type constructor", name.span());
      return prototype;
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(parameters.stream().map(TypeParameterInfo::type).toList());
    if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
      constrainInference(solver, prototype, expected);
    }
    Map<String, SemanticType> contextualSubstitutions = solver.solve().substitutions();
    List<Integer> indices = overloads.argumentIndices(call, constructorParameters, false);
    if (indices != null) {
      for (int index = 0; index < call.arguments().size(); index++) {
        Syntax.Expression argument = call.arguments().get(index).value();
        SemanticType inferencePattern = constructorParameters.get(indices.get(index)).type();
        SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
        TypeProbe probe =
            probeType(
                argument,
                containsTypeParameter(pattern, solverVariables(parameters)) ? null : pattern);
        constrainInference(solver, inferencePattern, probe.type());
      }
    }
    TypeConstraintSolver.Solution solution = solver.solve();
    Map<String, String> parameterNames =
        parameters.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    parameter -> parameter.type().identity(),
                    TypeParameterInfo::name,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (String missing : solution.missing()) {
      diagnostics.error(
          INVALID_CALL,
          "cannot infer type argument '" + parameterNames.get(missing) + "'",
          name.span());
    }
    for (TypeConstraintSolver.Conflict conflict : solution.conflicts()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + parameterNames.get(conflict.variable())
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          name.span());
    }
    List<SemanticType> arguments =
        parameters.stream()
            .map(
                parameter ->
                    solution
                        .substitutions()
                        .getOrDefault(parameter.type().identity(), SemanticType.DYNAMIC))
            .toList();
    return builtin != null
        ? builtins.instantiate(typeName, arguments)
        : sourceType(typeName, arguments);
  }

  private static Set<String> solverVariables(List<TypeParameterInfo> parameters) {
    return parameters.stream()
        .map(parameter -> parameter.type().identity())
        .collect(java.util.stream.Collectors.toSet());
  }

  private SemanticType resolveType(Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    SemanticType parameter = typeParameters.get(type.name());
    if (parameter != null) return type.nullable() ? parameter.nullable() : parameter;
    if (type.name().equals("Void")) {
      return type.nullable() ? SemanticType.DYNAMIC : SemanticType.VOID;
    }
    List<SemanticType> arguments =
        type.arguments().stream().map(argument -> resolveType(argument, typeParameters)).toList();
    if (type.name().equals("Function") && !arguments.isEmpty()) {
      SemanticType function =
          SemanticType.function(arguments.getFirst(), arguments.subList(1, arguments.size()));
      return type.nullable() ? function.nullable() : function;
    }
    SemanticType resolved =
        builtins.isType(type.name())
            ? builtins.instantiate(type.name(), arguments)
            : sourceType(type.name(), arguments);
    return type.nullable() ? resolved.nullable() : resolved;
  }

  private SemanticType resolveCheckedType(
      Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    validateType(type, false);
    return resolveType(type, typeParameters);
  }

  private SemanticType resolveDeclarationType(
      Syntax.TypeRef type, Object declaration, Map<String, SemanticType> typeParameters) {
    Syntax.Program previous = currentProgram;
    currentProgram = declarationPrograms.getOrDefault(declaration, previous);
    try {
      return resolveType(type, typeParameters);
    } finally {
      currentProgram = previous;
    }
  }

  private void validatePublicType(Syntax.TypeRef type) {
    if (activeTypeParameters.containsKey(type.name())) return;
    Syntax.ClassDecl classDecl = resolveClass(type.name());
    Syntax.EnumDecl enumDecl = resolveEnum(type.name());
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(type.name());
    boolean privateType =
        interfaceDecl != null && interfaceDecl.visibility() == Syntax.Visibility.PRIVATE
            || classDecl != null && classDecl.visibility() == Syntax.Visibility.PRIVATE
            || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE;
    if (privateType) {
      diagnostics.error(
          TYPE_MISMATCH,
          "private type '" + type.name() + "' cannot appear in a public signature",
          type.span());
    }
    type.arguments().forEach(this::validatePublicType);
  }

  private void validateTypeArgumentCount(
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

  private void bindDeclarationUse(SourceSpan span, String localName, Object declaration) {
    for (Syntax.ImportDecl imported : currentProgram.imports()) {
      if (imported.alias().isPresent() && imported.localName().equals(localName)) {
        bindings.put(span, importAliases.get(imported));
        return;
      }
    }
    bindings.put(span, declarationSymbols.get(declaration));
  }

  private SymbolId importedAlias(String localName) {
    if (currentProgram == null) return null;
    for (Syntax.ImportDecl imported : currentProgram.imports()) {
      if (imported.alias().isPresent() && imported.localName().equals(localName)) {
        return importAliases.get(imported);
      }
    }
    return null;
  }

  private SemanticType sourceType(String name, List<SemanticType> arguments) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    Syntax.ClassDecl classDecl = resolveClass(name);
    if (classDecl == null) classDecl = resolveImportedClassByDeclaredName(name);
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    Object declaration =
        interfaceDecl != null ? interfaceDecl : classDecl != null ? classDecl : enumDecl;
    Syntax.Program owner =
        declaration == null ? currentProgram : declarationPrograms.get(declaration);
    String declaredName =
        interfaceDecl != null
            ? interfaceDecl.name()
            : classDecl != null ? classDecl.name() : enumDecl != null ? enumDecl.name() : name;
    String identity = qualifiedName(owner == null ? "" : owner.packageName(), declaredName);
    if (interfaceDecl != null && interfaceDecl.visibility() == Syntax.Visibility.PRIVATE
        || classDecl != null && classDecl.visibility() == Syntax.Visibility.PRIVATE
        || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE) {
      identity = fileLocalIdentity(identity, owner);
    }
    ValueCategory category =
        interfaceDecl != null
            ? ValueCategory.POLYMORPHIC
            : classDecl != null ? ValueCategory.IDENTITY : ValueCategory.VALUE;
    return SemanticType.declared(identity, declaredName, arguments, category);
  }

  private int declaredTypeArity(String name) {
    int builtinArity = builtins.typeArity(name);
    if (builtinArity >= 0) return builtinArity;
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null) return interfaceDecl.typeParameters().size();
    Syntax.ClassDecl classDecl = resolveClass(name);
    if (classDecl != null) return classDecl.typeParameters().size();
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    return enumDecl == null ? -1 : enumDecl.typeParameters().size();
  }

  private Map<String, SemanticType> typeParameters(
      Syntax.FunctionDecl function, Syntax.ClassDecl owner) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    if (owner != null) result.putAll(classTypeParameters(owner));
    result.putAll(functionTypeParameters(function));
    return Map.copyOf(result);
  }

  private Map<String, SemanticType> classTypeParameters(Syntax.ClassDecl classDecl) {
    return declarationTypeParameters(
        declarationPrograms.getOrDefault(classDecl, currentProgram),
        "class/" + classDecl.name(),
        classDecl.typeParameters());
  }

  private Map<String, SemanticType> interfaceTypeParameters(Syntax.InterfaceDecl declaration) {
    return declarationTypeParameters(
        declarationPrograms.getOrDefault(declaration, currentProgram),
        "interface/" + declaration.name(),
        declaration.typeParameters());
  }

  private SemanticType interfaceSelfType(Syntax.InterfaceDecl declaration) {
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    return sourceType(
        declaration.name(),
        declaration.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  private Map<String, SemanticType> withTypeParameters(
      Map<String, SemanticType> base,
      List<Syntax.TypeParameter> parameters,
      Syntax.Program program,
      String owner) {
    Map<String, SemanticType> result = new LinkedHashMap<>(base);
    result.putAll(declarationTypeParameters(program, owner, parameters));
    return Map.copyOf(result);
  }

  private SemanticType classSelfType(Syntax.ClassDecl classDecl) {
    Map<String, SemanticType> parameters = classTypeParameters(classDecl);
    return sourceType(
        classDecl.name(),
        classDecl.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  private Map<String, SemanticType> enumTypeParameters(Syntax.EnumDecl enumDecl) {
    return declarationTypeParameters(
        declarationPrograms.getOrDefault(enumDecl, currentProgram),
        "enum/" + enumDecl.name(),
        enumDecl.typeParameters());
  }

  private SemanticType enumSelfType(Syntax.EnumDecl enumDecl) {
    Map<String, SemanticType> parameters = enumTypeParameters(enumDecl);
    return sourceType(
        enumDecl.name(),
        enumDecl.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  private Map<String, SemanticType> functionTypeParameters(Syntax.FunctionDecl function) {
    return declarationTypeParameters(
        declarationPrograms.getOrDefault(function, currentProgram),
        "function/" + function.name(),
        function.typeParameters());
  }

  private Map<String, SemanticType> declarationTypeParameters(
      Syntax.Program program, String owner, List<Syntax.TypeParameter> parameters) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      result.putIfAbsent(
          parameter.name(),
          SemanticType.parameter(
              program.span().source().id() + "/" + owner + "/" + index, parameter.name()));
    }
    return Map.copyOf(result);
  }

  private void validateTypeParameterNames(List<Syntax.TypeParameter> parameters) {
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

  private Map<String, SemanticType> classSubstitutions(
      Syntax.ClassDecl classDecl, SemanticType instance) {
    Map<String, SemanticType> declarations = classTypeParameters(classDecl);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(classDecl.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = declarations.get(classDecl.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return result;
  }

  private Map<String, SemanticType> enumSubstitutions(
      Syntax.EnumDecl enumDecl, SemanticType instance) {
    Map<String, SemanticType> declarations = enumTypeParameters(enumDecl);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(enumDecl.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = declarations.get(enumDecl.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return result;
  }

  private Map<String, SemanticType> inferBuiltinTypeArguments(
      Symbol symbol,
      List<Syntax.TypeRef> explicitArguments,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span) {
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    if (!explicitArguments.isEmpty()) {
      validateTypeArgumentCount(
          symbol.name(), symbol.typeParameters().size(), explicitArguments, span);
      for (int index = 0;
          index < Math.min(explicitArguments.size(), symbol.typeParameters().size());
          index++) {
        SemanticType parameter = symbol.typeParameters().get(index).type();
        if (parameter != null) {
          substitutions.put(
              parameter.identity(),
              resolveCheckedType(explicitArguments.get(index), activeTypeParameters));
        }
      }
      for (int index = symbol.typeParameters().size(); index < explicitArguments.size(); index++) {
        resolveCheckedType(explicitArguments.get(index), activeTypeParameters);
      }
    } else {
      TypeConstraintSolver solver =
          new TypeConstraintSolver(
              symbol.typeParameters().stream().map(TypeParameterInfo::type).toList());
      Set<String> variables = solverVariables(symbol.typeParameters());
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        constrainInference(solver, symbol.type(), expected);
      }
      Map<String, SemanticType> contextualSubstitutions = solver.solve().substitutions();
      List<Integer> indices = overloads.argumentIndices(call, symbol.parameters(), false);
      if (indices != null) {
        for (int index = 0; index < call.arguments().size(); index++) {
          Syntax.CallArgument argument = call.arguments().get(index);
          SemanticType inferencePattern = symbol.parameters().get(indices.get(index)).type();
          SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
          SemanticType argumentExpected =
              containsTypeParameter(pattern, variables)
                      && !(argument.value() instanceof Syntax.Lambda && pattern.isFunction())
                  ? null
                  : pattern;
          constrainInference(solver, inferencePattern, typeOf(argument.value(), argumentExpected));
        }
      }
      TypeConstraintSolver.Solution solution = solver.solve();
      substitutions.putAll(solution.substitutions());
      Map<String, String> parameterNames =
          symbol.typeParameters().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      parameter -> parameter.type().identity(),
                      TypeParameterInfo::name,
                      (left, right) -> left,
                      LinkedHashMap::new));
      for (TypeConstraintSolver.Conflict conflict : solution.conflicts()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type parameter '"
                + parameterNames.get(conflict.variable())
                + "' inferred as both "
                + conflict.first().displayName()
                + " and "
                + conflict.second().displayName(),
            span);
      }
    }
    for (TypeParameterInfo parameterInfo : symbol.typeParameters()) {
      String name = parameterInfo.name();
      SemanticType parameter = parameterInfo.type();
      if (parameter != null && !substitutions.containsKey(parameter.identity())) {
        diagnostics.error(INVALID_CALL, "cannot infer type argument '" + name + "'", span);
        substitutions.put(parameter.identity(), SemanticType.DYNAMIC);
      }
    }
    return substitutions;
  }

  private static SemanticType findTypeParameter(SemanticType type, String name) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER && type.name().equals(name)) return type;
    for (SemanticType argument : type.arguments()) {
      SemanticType result = findTypeParameter(argument, name);
      if (result != null) return result;
    }
    return null;
  }

  private static Syntax.Program merge(List<Syntax.Program> programs, Syntax.Program entryProgram) {
    List<Syntax.EnumDecl> enums = new ArrayList<>();
    List<Syntax.InterfaceDecl> interfaces = new ArrayList<>();
    List<Syntax.ClassDecl> classes = new ArrayList<>();
    List<Syntax.FunctionDecl> functions = new ArrayList<>();
    for (Syntax.Program program : programs) {
      enums.addAll(program.enums());
      interfaces.addAll(program.interfaces());
      classes.addAll(program.classes());
      functions.addAll(program.functions());
    }
    return new Syntax.Program(
        entryProgram.packageName(),
        entryProgram.imports(),
        enums,
        interfaces,
        classes,
        functions,
        entryProgram.span());
  }

  private static boolean definitelyYields(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement
          || statement instanceof Syntax.BreakStatement broken && broken.value() != null) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && definitelyYields(conditional.thenBody())
          && definitelyYields(conditional.elseBody())) {
        return true;
      }
    }
    return false;
  }

  private final class SemanticPatternDomain implements PatternCoverage.Domain<SemanticType> {
    @Override
    public List<PatternCoverage.Constructor<SemanticType>> constructors(SemanticType type) {
      if (type.isNullable()) {
        return List.of(
            new PatternCoverage.Constructor<>("$null", List.of()),
            new PatternCoverage.Constructor<>("$value", List.of(type.nonNullable())));
      }
      if (type.equals(SemanticType.BOOLEAN)) {
        return List.of(
            new PatternCoverage.Constructor<>("boolean:false", List.of()),
            new PatternCoverage.Constructor<>("boolean:true", List.of()));
      }
      Syntax.EnumDecl declaration = resolveEnum(type);
      if (declaration == null) return List.of();
      Map<String, SemanticType> substitutions = enumSubstitutions(declaration, type);
      Map<String, SemanticType> parameters = enumTypeParameters(declaration);
      return declaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.name(),
                      variant.parameters().stream()
                          .map(
                              field ->
                                  resolveDeclarationType(field.type(), field, parameters)
                                      .substitute(substitutions))
                          .toList()))
          .toList();
    }

    @Override
    public PatternCoverage.Constructor<SemanticType> openConstructor(
        SemanticType type, String key) {
      return constructors(type).isEmpty()
          ? new PatternCoverage.Constructor<>(key, List.of())
          : null;
    }
  }

  private enum ControlKind {
    LOOP,
    SWITCH
  }

  private static final class ControlContext {
    private final ControlKind kind;
    private SemanticType resultType;

    private ControlContext(ControlKind kind, SemanticType resultType) {
      this.kind = kind;
      this.resultType = resultType;
    }

    static ControlContext loop() {
      return new ControlContext(ControlKind.LOOP, null);
    }

    static ControlContext switchExpression(SemanticType resultType) {
      return new ControlContext(ControlKind.SWITCH, resultType);
    }

    ControlKind kind() {
      return kind;
    }

    SemanticType resultType() {
      return resultType;
    }

    void setResultType(SemanticType resultType) {
      this.resultType = resultType;
    }
  }

  private void indexDeclarationPrograms() {
    for (Syntax.Program program : programs) {
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        declarationPrograms.put(declaration, program);
        for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
          declarationPrograms.put(method, program);
        }
      }
      for (Syntax.EnumDecl declaration : program.enums())
        declarationPrograms.put(declaration, program);
      for (Syntax.ClassDecl declaration : program.classes()) {
        declarationPrograms.put(declaration, program);
        for (Syntax.FieldDecl field : declaration.fields()) declarationPrograms.put(field, program);
        for (Syntax.FunctionDecl method : declaration.methods())
          declarationPrograms.put(method, program);
      }
      for (Syntax.FunctionDecl declaration : program.functions())
        declarationPrograms.put(declaration, program);
    }
  }

  private Syntax.FunctionDecl resolveFunction(String name) {
    List<Syntax.FunctionDecl> candidates = resolveFunctions(name);
    return candidates.isEmpty() ? null : candidates.getFirst();
  }

  private List<Syntax.FunctionDecl> resolveFunctions(String name) {
    if (currentProgram == null) return List.of();
    List<Syntax.FunctionDecl> visible = new ArrayList<>();
    List<Syntax.FunctionDecl> localPrivate =
        functions.get(
            fileLocalIdentity(qualifiedName(currentProgram.packageName(), name), currentProgram));
    if (localPrivate != null) visible.addAll(localPrivate);
    List<Syntax.FunctionDecl> samePackage =
        functions.get(qualifiedName(currentProgram.packageName(), name));
    if (samePackage != null) visible.addAll(samePackage);
    if (!visible.isEmpty()) return List.copyOf(visible);
    for (Syntax.ImportDecl imported : currentProgram.imports()) {
      if (!imported.localName().equals(name)) continue;
      List<Syntax.FunctionDecl> candidates = functions.get(imported.qualifiedName());
      if (candidates == null) return List.of();
      return candidates.stream().filter(candidate -> canImport(currentProgram, candidate)).toList();
    }
    return List.of();
  }

  private Map<SymbolId, List<SymbolId>> callableGroups() {
    Map<SymbolId, List<SymbolId>> result = new LinkedHashMap<>();
    for (List<Syntax.FunctionDecl> declarations : functions.values()) {
      List<SymbolId> group = declarations.stream().map(declarationSymbols::get).toList();
      group.forEach(id -> result.put(id, group));
    }
    return Map.copyOf(result);
  }

  private Map<String, List<SemanticType>> interfaceParentTypes() {
    Map<String, List<SemanticType>> result = new LinkedHashMap<>();
    Syntax.Program previous = currentProgram;
    for (Syntax.InterfaceDecl declaration : interfaces.values()) {
      currentProgram = declarationPrograms.get(declaration);
      Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
      String identity = symbols.get(declarationSymbols.get(declaration)).type().identity();
      result.put(
          identity,
          declaration.extendedInterfaces().stream()
              .map(parent -> resolveType(parent, parameters))
              .toList());
    }
    currentProgram = previous;
    return Map.copyOf(result);
  }

  private Syntax.ClassDecl resolveClass(String name) {
    return resolveDeclaration(name, classes);
  }

  private Syntax.ClassDecl resolveImportedClassByDeclaredName(String name) {
    if (currentProgram == null) return null;
    for (Syntax.ImportDecl imported : currentProgram.imports()) {
      Syntax.ClassDecl candidate = classes.get(imported.qualifiedName());
      if (candidate != null
          && candidate.name().equals(name)
          && canImport(currentProgram, candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private Syntax.ClassDecl resolveClass(SemanticType type) {
    for (Syntax.ClassDecl candidate : classes.values()) {
      Syntax.Program owner = declarationPrograms.get(candidate);
      String identity = qualifiedName(owner.packageName(), candidate.name());
      if (candidate.visibility() == Syntax.Visibility.PRIVATE) {
        identity = fileLocalIdentity(identity, owner);
      }
      if (identity.equals(type.identity())) return candidate;
    }
    return null;
  }

  private Syntax.ClassDecl ownerOf(Syntax.FunctionDecl method) {
    for (Syntax.Program program : programs) {
      for (Syntax.ClassDecl classDecl : program.classes()) {
        if (classDecl.methods().stream().anyMatch(candidate -> candidate == method)) {
          return classDecl;
        }
      }
    }
    return null;
  }

  private Syntax.EnumDecl resolveEnum(String name) {
    return resolveDeclaration(name, enums);
  }

  private Syntax.InterfaceDecl resolveInterface(String name) {
    return resolveDeclaration(name, interfaces);
  }

  private Syntax.InterfaceDecl resolveInterface(SemanticType type) {
    for (Syntax.InterfaceDecl candidate : interfaces.values()) {
      Syntax.Program owner = declarationPrograms.get(candidate);
      String identity = qualifiedName(owner.packageName(), candidate.name());
      if (candidate.visibility() == Syntax.Visibility.PRIVATE) {
        identity = fileLocalIdentity(identity, owner);
      }
      if (identity.equals(type.identity())) return candidate;
    }
    return null;
  }

  private Syntax.EnumDecl resolveEnum(SemanticType type) {
    for (Syntax.EnumDecl candidate : enums.values()) {
      Syntax.Program owner = declarationPrograms.get(candidate);
      String identity = qualifiedName(owner.packageName(), candidate.name());
      if (candidate.visibility() == Syntax.Visibility.PRIVATE) {
        identity = fileLocalIdentity(identity, owner);
      }
      if (identity.equals(type.identity())) return candidate;
    }
    return null;
  }

  private <T> T resolveDeclaration(String name, Map<String, T> declarations) {
    if (currentProgram == null) return null;
    T localPrivate =
        declarations.get(
            fileLocalIdentity(qualifiedName(currentProgram.packageName(), name), currentProgram));
    if (localPrivate != null) return localPrivate;
    T samePackage = declarations.get(qualifiedName(currentProgram.packageName(), name));
    if (samePackage != null) return samePackage;
    for (Syntax.ImportDecl imported : currentProgram.imports()) {
      if (imported.localName().equals(name)) {
        T declaration = declarations.get(imported.qualifiedName());
        return declaration != null && canImport(currentProgram, declaration) ? declaration : null;
      }
    }
    return null;
  }

  private boolean canImport(Syntax.Program importer, Object declaration) {
    Syntax.Program owner = declarationPrograms.get(declaration);
    return owner != null
        && (owner.packageName().equals(importer.packageName())
            || exportedSources.contains(owner.span().source().id()));
  }

  private static String declarationKey(
      Syntax.Program program, String name, Syntax.Visibility visibility) {
    String qualified = qualifiedName(program.packageName(), name);
    return visibility == Syntax.Visibility.PRIVATE
        ? fileLocalIdentity(qualified, program)
        : qualified;
  }

  private static String fileLocalIdentity(String qualified, Syntax.Program program) {
    return qualified + "@" + program.span().source().id().uri();
  }

  private static String callableSignature(Syntax.FunctionDecl function) {
    Map<String, String> typeParameters = new HashMap<>();
    for (int index = 0; index < function.typeParameters().size(); index++) {
      typeParameters.put(function.typeParameters().get(index).name(), "$" + index);
    }
    return function.name()
        + "("
        + function.parameters().stream()
            .map(parameter -> normalizedType(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  private static String interfaceMethodSignature(Syntax.InterfaceMethodDecl method) {
    Map<String, String> typeParameters = new HashMap<>();
    for (int index = 0; index < method.typeParameters().size(); index++) {
      typeParameters.put(method.typeParameters().get(index).name(), "$" + index);
    }
    return method.name()
        + "("
        + method.parameters().stream()
            .map(parameter -> normalizedType(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  private static String normalizedType(Syntax.TypeRef type, Map<String, String> typeParameters) {
    String name = typeParameters.getOrDefault(type.name(), type.name());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> normalizedType(argument, typeParameters))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"));
    return name + arguments + (type.nullable() ? "?" : "");
  }

  private static String qualifiedName(String packageName, String name) {
    return packageName.isEmpty() ? name : packageName + "." + name;
  }

  private record SourceCallResolution(
      Syntax.FunctionDecl declaration,
      List<ParameterInfo> parameters,
      List<SemanticType> reifiedArguments,
      SemanticType result) {
    private SourceCallResolution {
      parameters = List.copyOf(parameters);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  private record FunctionReferenceResolution(
      Syntax.FunctionDecl declaration, List<SemanticType> reifiedArguments) {
    private FunctionReferenceResolution {
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  private record InterfaceRequirement(
      Syntax.InterfaceDecl owner,
      SemanticType receiver,
      Syntax.InterfaceMethodDecl method,
      List<ParameterInfo> parameters,
      SemanticType result,
      String key,
      String signature) {
    private InterfaceRequirement {
      parameters = List.copyOf(parameters);
    }
  }

  private record InterfaceCallResolution(
      List<ParameterInfo> parameters, SemanticType result, List<SemanticType> reifiedArguments) {
    private InterfaceCallResolution {
      parameters = List.copyOf(parameters);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  private record SourceCallCandidate(
      SourceCallResolution resolution,
      List<String> missingTypeArguments,
      List<InferenceConflict> conflicts,
      List<BoundViolation> boundViolations,
      boolean assignable,
      int score) {
    private SourceCallCandidate {
      missingTypeArguments = List.copyOf(missingTypeArguments);
      conflicts = List.copyOf(conflicts);
      boundViolations = List.copyOf(boundViolations);
    }

    private boolean applicable() {
      return missingTypeArguments.isEmpty()
          && conflicts.isEmpty()
          && boundViolations.isEmpty()
          && assignable;
    }

    private List<ParameterInfo> parameters() {
      return resolution.parameters();
    }
  }

  private record InferenceConflict(String name, SemanticType first, SemanticType second) {}

  private record BoundViolation(String name, SemanticType bound, SemanticType actual) {}

  private record TypeProbe(SemanticType type, boolean hasErrors) {}

  private record AnalysisCheckpoint(
      Map<SourceSpan, SymbolId> bindings,
      Map<SourceSpan, SemanticType> semanticTypes,
      Map<SourceSpan, ResolvedCall> resolvedCalls,
      Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments,
      Map<SourceSpan, ResolvedIteration> iterations,
      Map<SourceSpan, ResolvedIndex> indexes,
      Map<SymbolId, SemanticType> flowTypes,
      int semanticScopeCount,
      int diagnosticMark) {}

  private record ScopedSymbol(SemanticType declaredType, SymbolId id) {}

  private record ScopeFrame(
      Map<String, ScopedSymbol> symbols, List<SymbolId> declarations, SourceSpan span, int depth) {}
}
