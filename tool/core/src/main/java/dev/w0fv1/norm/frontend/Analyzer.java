package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.semantic.ArgumentBinding;
import dev.w0fv1.norm.semantic.BuiltinSymbols;
import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
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

  private static final SemanticType INT = new SemanticType("int");
  private static final SemanticType BOOL = new SemanticType("bool");
  private static final SemanticType STRING = new SemanticType("String");
  private final Syntax.Program syntax;
  private final List<Syntax.Program> programs;
  private final Syntax.Program entryProgram;
  private final DiagnosticBag diagnostics;
  private final boolean requireEntryPoint;
  private final Set<DocumentId> exportedSources;
  private final Map<String, Syntax.FunctionDecl> functions = new HashMap<>();
  private final Map<String, Syntax.ClassDecl> classes = new HashMap<>();
  private final Map<String, Syntax.EnumDecl> enums = new HashMap<>();
  private final BuiltinSymbols builtins = new BuiltinSymbols();
  private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
  private final Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
  private final Map<SourceSpan, SemanticType> semanticTypes = new LinkedHashMap<>();
  private final Map<SourceSpan, ArgumentBinding> argumentBindings = new LinkedHashMap<>();
  private final Map<SourceSpan, List<SemanticType>> callTypeArguments = new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedIteration> iterations = new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedIndex> indexes = new LinkedHashMap<>();
  private final Map<SymbolId, List<SymbolId>> members = new LinkedHashMap<>();
  private final Map<Object, SymbolId> declarationSymbols = new IdentityHashMap<>();
  private final Map<Object, Syntax.Program> declarationPrograms = new IdentityHashMap<>();
  private final Map<String, SymbolId> copyMethods = new HashMap<>();
  private final Map<Syntax.ImportDecl, SymbolId> importAliases = new IdentityHashMap<>();
  private final Map<SymbolId, SymbolId> aliasTargets = new LinkedHashMap<>();
  private final Deque<ScopeFrame> scopes = new ArrayDeque<>();
  private final List<SemanticScope> semanticScopes = new ArrayList<>();
  private int nextSymbolId;
  private SymbolId currentCallable;
  private SemanticType expectedReturnType = SemanticType.VOID;
  private Map<String, SemanticType> activeTypeParameters = Map.of();
  private Map<String, SymbolId> activeTypeParameterSymbols = Map.of();
  private Syntax.Program currentProgram;
  private Syntax.ClassDecl currentClass;
  private int loopDepth;

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
    this.requireEntryPoint = requireEntryPoint;
    this.exportedSources = Set.copyOf(exportedSources);
    symbols.putAll(builtins.symbols());
    builtins.members().forEach((owner, values) -> members.put(owner, new ArrayList<>(values)));
  }

  AnalysisResult analyze() {
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
      diagnostics.error(MISSING_MAIN, "program must declare 'void main()'", syntax.span());
    } else if (main != null
        && (!resolveDeclarationType(main.returnType(), main, functionTypeParameters(main))
                .equals(SemanticType.VOID)
            || !main.typeParameters().isEmpty()
            || !main.parameters().isEmpty())) {
      diagnostics.error(TYPE_MISMATCH, "entry function must be 'void main()'", main.span());
    }

    for (Syntax.Program program : programs) {
      currentProgram = program;
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
    List<dev.w0fv1.norm.diagnostic.Diagnostic> snapshot = diagnostics.snapshot();
    SemanticModel semanticModel =
        new SemanticModel(
            syntax.span().source(),
            syntax,
            symbols,
            bindings,
            semanticTypes,
            argumentBindings,
            callTypeArguments,
            iterations,
            indexes,
            members,
            aliasTargets,
            semanticScopes,
            snapshot,
            importableSymbols());
    Optional<dev.w0fv1.norm.bound.BoundProgram> boundProgram =
        snapshot.stream()
                .anyMatch(
                    diagnostic ->
                        diagnostic.severity() == dev.w0fv1.norm.diagnostic.DiagnosticSeverity.ERROR)
            ? Optional.empty()
            : Optional.of(new Binder(programs, semanticModel).bind(main));
    return new AnalysisResult(semanticModel, Optional.ofNullable(main), boundProgram, snapshot);
  }

  private void collectDeclarations() {
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        if (enums.putIfAbsent(
                    declarationKey(program, enumDecl.name(), enumDecl.visibility()), enumDecl)
                != null
            || builtins.isType(enumDecl.name())) {
          diagnostics.error(
              DUPLICATE_NAME,
              "type '" + enumDecl.name() + "' is already declared",
              enumDecl.span());
        }
        Set<String> members = new HashSet<>();
        for (Syntax.EnumMember member : enumDecl.members()) {
          if (!members.add(member.name())) {
            diagnostics.error(
                DUPLICATE_NAME,
                "enum member '" + member.name() + "' is already declared",
                member.nameSpan());
          }
        }
        if (enumDecl.members().isEmpty()) {
          diagnostics.error(
              TYPE_MISMATCH, "enum must declare at least one member", enumDecl.span());
        }
        Symbol type =
            register(
                enumDecl,
                enumDecl.name(),
                SymbolKind.TYPE,
                sourceType(enumDecl.name(), List.of()),
                enumDecl.nameSpan(),
                null,
                List.of(),
                List.of());
        for (Syntax.EnumMember member : enumDecl.members()) {
          Symbol value =
              register(
                  member,
                  member.name(),
                  SymbolKind.ENUM_MEMBER,
                  sourceType(enumDecl.name(), List.of()),
                  member.nameSpan(),
                  type.id(),
                  List.of(),
                  List.of());
          addMember(type.id(), value.id());
        }
      }
      for (Syntax.ClassDecl classDecl : program.classes()) {
        if (classes.putIfAbsent(
                    declarationKey(program, classDecl.name(), classDecl.visibility()), classDecl)
                != null
            || resolveEnum(classDecl.name()) != null
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
                classDecl.typeParameters().stream().map(Syntax.TypeParameter::name).toList(),
                List.of());
        registerTypeParameters(
            classDecl.typeParameters(), type.id(), classTypeParameters(classDecl));
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
        copyMethods.put(classDecl.name(), copyId);
        for (Syntax.FunctionDecl method : classDecl.methods()) {
          validateTypeParameterNames(method.typeParameters());
          if (!method.typeParameters().isEmpty()) {
            diagnostics.error(
                TYPE_MISMATCH, "generic methods are not supported in Norm 0.2", method.nameSpan());
          }
          Symbol symbol =
              register(
                  method,
                  method.name(),
                  SymbolKind.METHOD,
                  resolveDeclarationType(
                      method.returnType(), method, typeParameters(method, classDecl)),
                  method.nameSpan(),
                  type.id(),
                  method.typeParameters().stream().map(Syntax.TypeParameter::name).toList(),
                  parametersOf(method, Map.of()));
          registerTypeParameters(
              method.typeParameters(), symbol.id(), functionTypeParameters(method));
          if (method.visibility() == Syntax.Visibility.PUBLIC) {
            addMember(type.id(), symbol.id());
          }
        }
      }
      for (Syntax.FunctionDecl function : program.functions()) {
        validateTypeParameterNames(function.typeParameters());
        if (functions.putIfAbsent(
                declarationKey(program, function.name(), function.visibility()), function)
            != null) {
          diagnostics.error(
              DUPLICATE_NAME,
              "function '" + function.name() + "' is already declared",
              function.span());
        }
        Symbol symbol =
            register(
                function,
                function.name(),
                SymbolKind.FUNCTION,
                resolveDeclarationType(
                    function.returnType(), function, functionTypeParameters(function)),
                function.nameSpan(),
                null,
                function.typeParameters().stream().map(Syntax.TypeParameter::name).toList(),
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
        Object declaration = functions.get(imported.qualifiedName());
        if (declaration == null) declaration = classes.get(imported.qualifiedName());
        if (declaration == null) declaration = enums.get(imported.qualifiedName());
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
          aliasTargets.put(alias.id(), target.id());
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
      if (!methods.add(method.name())) {
        diagnostics.error(
            DUPLICATE_NAME, "method '" + method.name() + "' is already declared", method.span());
      }
    }
    activeTypeParameters = Map.of();
    activeTypeParameterSymbols = Map.of();
  }

  private void analyzeFunction(Syntax.FunctionDecl function, Syntax.ClassDecl owner) {
    activeTypeParameters = typeParameters(function, owner);
    activeTypeParameterSymbols = typeParameterSymbols(function, owner);
    validateType(function.returnType(), true);
    expectedReturnType = resolveType(function.returnType(), activeTypeParameters);
    currentClass = owner;
    if (function.visibility() == Syntax.Visibility.PUBLIC
        && (owner == null || owner.visibility() == Syntax.Visibility.PUBLIC)) {
      validatePublicType(function.returnType());
      function.parameters().forEach(parameter -> validatePublicType(parameter.type()));
    }
    currentCallable = declarationSymbols.get(function);
    scopes.clear();
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
      declareSynthetic("this", classSelfType(owner), owner.nameSpan());
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
    if (!expectedReturnType.equals(SemanticType.VOID) && !definitelyReturns(function.body())) {
      diagnostics.error(
          INVALID_CONTROL,
          "function '" + function.name() + "' must return " + expectedReturnType.displayName(),
          function.span());
    }
    popScope();
    currentCallable = null;
    currentClass = null;
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
        validateType(variable.type(), false);
        SemanticType requested = resolveType(variable.type(), activeTypeParameters);
        SemanticType actual = typeOf(variable.initializer(), requested);
        requireAssignable(requested, actual, variable.initializer().span());
        SemanticType declaredType = actual.equals(SemanticType.DYNAMIC) ? requested : actual;
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
      }
      case Syntax.Assignment assignment -> {
        SemanticType target = assignmentTargetType(assignment.target());
        SemanticType value = typeOf(assignment.value(), target);
        requireAssignable(target, value, assignment.value().span());
      }
      case Syntax.ExpressionStatement expression -> typeOf(expression.expression(), null);
      case Syntax.IfStatement ifStatement -> {
        requireType(BOOL, typeOf(ifStatement.condition(), BOOL), ifStatement.condition().span());
        analyzeNested(ifStatement.thenBody());
        analyzeNested(ifStatement.elseBody());
      }
      case Syntax.ForStatement forStatement -> {
        SemanticType iterableType = typeOf(forStatement.iterable(), null);
        Optional<dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable> iterable =
            builtins.resolveIterable(iterableType);
        iterable.ifPresent(
            capability ->
                iterations.put(
                    forStatement.iterable().span(),
                    new ResolvedIteration(capability.elementType(), capability.intrinsic())));
        if (iterable.isEmpty()) {
          diagnostics.error(
              TYPE_MISMATCH, "for requires an iterable value", forStatement.iterable().span());
        }
        SemanticType variableType;
        if (forStatement.variableType().isPresent()) {
          Syntax.TypeRef explicitType = forStatement.variableType().orElseThrow();
          validateType(explicitType, false);
          variableType = resolveType(explicitType, activeTypeParameters);
          iterable
              .map(dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable::elementType)
              .ifPresent(
                  elementType ->
                      requireAssignable(
                          variableType, elementType, forStatement.variableNameSpan()));
        } else {
          Optional<SemanticType> elementType =
              iterable.map(dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIterable::elementType);
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
        loopDepth++;
        analyzeStatements(forStatement.body());
        loopDepth--;
        popScope();
      }
      case Syntax.ReturnStatement returnStatement -> {
        SemanticType actual =
            returnStatement.value() == null
                ? SemanticType.VOID
                : typeOf(returnStatement.value(), expectedReturnType);
        requireAssignable(expectedReturnType, actual, returnStatement.span());
      }
      case Syntax.BreakStatement breakStatement -> validateLoopControl(breakStatement.span());
      case Syntax.ContinueStatement continueStatement ->
          validateLoopControl(continueStatement.span());
    }
  }

  private SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    SemanticType type =
        switch (expression) {
          case Syntax.IntegerLiteral ignored -> INT;
          case Syntax.BooleanLiteral ignored -> BOOL;
          case Syntax.StringLiteralExpr ignored -> STRING;
          case Syntax.ArrayLiteral array -> analyzeArray(array, expected);
          case Syntax.Name name -> lookup(name.value(), name.span());
          case Syntax.Unary unary -> analyzeUnary(unary);
          case Syntax.Binary binary -> analyzeBinary(binary);
          case Syntax.Call call -> analyzeCall(call, expected);
          case Syntax.Member member -> memberType(member);
          case Syntax.Index index -> analyzeIndex(index);
        };
    if (expected != null && type.equals(SemanticType.DYNAMIC)) {
      type = expected;
    }
    semanticTypes.put(expression.span(), type);
    return type;
  }

  private SemanticType analyzeArray(Syntax.ArrayLiteral array, SemanticType expected) {
    SemanticType expectedArray = expected;
    SemanticType expectedElement =
        expectedArray != null
                && expectedArray.name().equals("Array")
                && expectedArray.arguments().size() == 1
            ? expectedArray.arguments().getFirst()
            : null;
    SemanticType elementType = array.elements().isEmpty() ? expectedElement : null;
    for (Syntax.Expression element : array.elements()) {
      SemanticType current = typeOf(element, expectedElement);
      if (elementType == null) {
        elementType = current;
      } else if (!elementType.equals(current)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "array elements must have one invariant type; found "
                + elementType.displayName()
                + " and "
                + current.displayName(),
            element.span());
      }
    }
    return builtins.instantiate(
        "Array", List.of(elementType == null ? SemanticType.DYNAMIC : elementType));
  }

  private SemanticType analyzeUnary(Syntax.Unary unary) {
    SemanticType operand = typeOf(unary.operand(), null);
    SemanticType required = unary.operator() == TokenKind.BANG ? BOOL : INT;
    requireType(required, operand, unary.span());
    return required;
  }

  private SemanticType analyzeBinary(Syntax.Binary binary) {
    SemanticType left = typeOf(binary.left(), null);
    SemanticType right = typeOf(binary.right(), left);
    return switch (binary.operator()) {
      case PLUS -> {
        if (left.equals(STRING) && right.equals(STRING)) {
          yield STRING;
        }
        requireBoth(INT, left, right, binary.span());
        yield INT;
      }
      case MINUS, STAR, SLASH, PERCENT -> {
        requireBoth(INT, left, right, binary.span());
        yield INT;
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        requireBoth(INT, left, right, binary.span());
        yield BOOL;
      }
      case AND_AND, OR_OR -> {
        requireBoth(BOOL, left, right, binary.span());
        yield BOOL;
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        requireAssignable(left, right, binary.span());
        yield BOOL;
      }
      default -> SemanticType.DYNAMIC;
    };
  }

  private SemanticType analyzeCall(Syntax.Call call, SemanticType expected) {
    if (call.callee() instanceof Syntax.Name name) {
      return analyzeNamedCall(name, call, expected);
    }
    if (call.callee() instanceof Syntax.Member member) {
      return analyzeMethodCall(member, call);
    }
    diagnostics.error(INVALID_CALL, "expression is not callable", call.callee().span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private SemanticType analyzeNamedCall(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    String callee = name.value();
    builtins
        .global(callee)
        .or(() -> builtins.type(callee))
        .ifPresent(symbol -> bindings.put(name.span(), symbol.id()));
    Optional<Symbol> builtinFunction = builtins.global(callee);
    if (builtinFunction.isPresent()) {
      Symbol symbol = builtinFunction.orElseThrow();
      validateTypeArgumentCount(callee, 0, name.typeArguments(), name.span());
      name.typeArguments().forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
      validateArguments(call, symbol.parameters());
      return symbol.type();
    }
    SemanticType constructedType = appliedType(callee, name.typeArguments(), name.span());
    Optional<List<ParameterInfo>> constructor = builtins.constructorParameters(constructedType);
    if (constructor.isPresent()) {
      validateArguments(call, constructor.orElseThrow());
      return constructedType;
    }
    Syntax.ClassDecl classDecl = resolveClass(callee);
    if (classDecl != null) {
      bindDeclarationUse(name.span(), callee, classDecl);
      Map<String, SemanticType> substitutions = classSubstitutions(classDecl, constructedType);
      validateArguments(call, fieldParameters(classDecl, substitutions));
      return constructedType;
    }
    Syntax.FunctionDecl function = resolveFunction(callee);
    if (function != null) {
      bindDeclarationUse(name.span(), callee, function);
      Map<String, SemanticType> substitutions = inferTypeArguments(function, name, call, expected);
      Map<String, SemanticType> declarations = functionTypeParameters(function);
      callTypeArguments.put(
          call.span(),
          function.typeParameters().stream()
              .map(parameter -> substitutions.get(declarations.get(parameter.name()).identity()))
              .toList());
      validateArguments(call, parametersOf(function, substitutions));
      return resolveDeclarationType(function.returnType(), function, declarations)
          .substitute(substitutions);
    }
    diagnostics.error(UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private SemanticType analyzeMethodCall(Syntax.Member member, Syntax.Call call) {
    SemanticType receiver = typeOf(member.receiver(), null);
    if (member.name().isEmpty()) {
      analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    Optional<Symbol> builtinMethod = builtins.member(receiver, member.name());
    if (builtinMethod.isPresent()) {
      Optional<Symbol> resolved = builtinMethod;
      if (resolved.orElseThrow().kind() != SymbolKind.METHOD) {
        diagnostics.error(
            UNKNOWN_NAME,
            "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
            call.span());
        analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      Symbol symbol = resolved.orElseThrow();
      bindings.put(member.nameSpan(), symbol.id());
      validateArguments(call, symbol.parameters());
      return symbol.type();
    }
    if (builtins.isType(receiver.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
          member.span());
      analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    Syntax.ClassDecl classDecl = resolveClass(receiver);
    if (classDecl != null) {
      if (member.name().equals("copy")) {
        bindings.put(member.nameSpan(), copyMethods.get(receiver.name()));
        validateArguments(call, List.of());
        return receiver;
      }
      Syntax.FunctionDecl method =
          classDecl.methods().stream()
              .filter(candidate -> candidate.name().equals(member.name()))
              .findFirst()
              .orElse(null);
      if (method == null) {
        diagnostics.error(
            UNKNOWN_NAME,
            "class '" + receiver.displayName() + "' has no method '" + member.name() + "'",
            member.span());
        analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      if (method.visibility() == Syntax.Visibility.PRIVATE && currentClass != classDecl) {
        diagnostics.error(
            UNKNOWN_NAME,
            "method '" + member.name() + "' is private in class '" + classDecl.name() + "'",
            member.nameSpan());
      }
      bindings.put(member.nameSpan(), declarationSymbols.get(method));
      Map<String, SemanticType> substitutions = classSubstitutions(classDecl, receiver);
      validateArguments(call, parametersOf(method, substitutions));
      return resolveDeclarationType(method.returnType(), method, classTypeParameters(classDecl))
          .substitute(substitutions);
    }
    diagnostics.error(
        TYPE_MISMATCH, "type '" + receiver.displayName() + "' has no methods", member.span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  private SemanticType memberType(Syntax.Member member) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = resolveEnum(enumName.value());
      if (enumDecl != null) {
        bindings.put(enumName.span(), declarationSymbols.get(enumDecl));
        enumDecl.members().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .map(declarationSymbols::get)
            .ifPresent(id -> bindings.put(member.nameSpan(), id));
        if (enumDecl.members().stream().noneMatch(value -> value.name().equals(member.name()))) {
          diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no member '" + member.name() + "'",
              member.span());
        }
        return sourceType(enumDecl.name(), List.of());
      }
    }
    SemanticType receiverType = typeOf(member.receiver(), null);
    if (member.name().isEmpty()) return SemanticType.DYNAMIC;
    Optional<Symbol> builtinMember = builtins.member(receiverType, member.name());
    if (builtinMember.isPresent() && builtinMember.orElseThrow().kind() != SymbolKind.METHOD) {
      Symbol symbol = builtinMember.orElseThrow();
      bindings.put(member.nameSpan(), symbol.id());
      return symbol.type();
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
        return resolveDeclarationType(field.type(), field, classTypeParameters(classDecl))
            .substitute(classSubstitutions(classDecl, receiverType));
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
      case Syntax.Name name -> lookup(name.value(), name.span());
      case Syntax.Member member -> memberType(member);
      case Syntax.Index index -> analyzeIndex(index);
      default -> {
        diagnostics.error(TYPE_MISMATCH, "invalid assignment target", target.span());
        yield SemanticType.DYNAMIC;
      }
    };
  }

  private void validateArguments(Syntax.Call call, List<ParameterInfo> parameters) {
    expectArgumentCount(call, parameters.size());
    List<Integer> parameterIndices = new ArrayList<>();
    boolean[] supplied = new boolean[parameters.size()];
    for (int index = 0; index < call.arguments().size(); index++) {
      Syntax.CallArgument argument = call.arguments().get(index);
      int parameterIndex = -1;
      if (argument.label().isPresent()) {
        String label = argument.label().orElseThrow().name();
        for (int candidate = 0; candidate < parameters.size(); candidate++) {
          if (parameters.get(candidate).name().equals(label)) {
            parameterIndex = candidate;
            break;
          }
        }
        if (parameterIndex < 0) {
          diagnostics.error(
              INVALID_CALL,
              "unknown named argument '" + label + "'",
              argument.label().orElseThrow().span());
        }
      } else if (parameters.size() <= 1 && index < parameters.size()) {
        parameterIndex = index;
      } else if (index < parameters.size()
          && argument.value() instanceof Syntax.Name shorthand
          && shorthand.value().equals(parameters.get(index).name())) {
        parameterIndex = index;
      } else {
        diagnostics.error(
            INVALID_CALL,
            "argument '"
                + (index < parameters.size() ? parameters.get(index).name() : index)
                + "' must be named",
            argument.span());
      }
      parameterIndices.add(parameterIndex);
      if (parameterIndex >= 0 && supplied[parameterIndex]) {
        diagnostics.error(
            INVALID_CALL,
            "argument '" + parameters.get(parameterIndex).name() + "' is supplied more than once",
            argument.span());
      }
      if (parameterIndex >= 0) {
        supplied[parameterIndex] = true;
        ParameterInfo parameter = parameters.get(parameterIndex);
        requireAssignable(
            parameter.type(), typeOf(argument.value(), parameter.type()), argument.span());
      } else {
        typeOf(argument.value(), null);
      }
    }
    for (int index = 0; index < supplied.length; index++) {
      if (!supplied[index]) {
        diagnostics.error(
            INVALID_CALL, "missing argument '" + parameters.get(index).name() + "'", call.span());
      }
    }
    argumentBindings.put(call.span(), new ArgumentBinding(parameterIndices));
  }

  private void analyzeArguments(List<Syntax.CallArgument> arguments) {
    for (Syntax.CallArgument argument : arguments) {
      typeOf(argument.value(), null);
    }
  }

  private void expectArgumentCount(Syntax.Call call, int count) {
    if (call.arguments().size() != count) {
      diagnostics.error(
          INVALID_CALL,
          "call expects " + count + " argument(s), found " + call.arguments().size(),
          call.span());
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
    int arity = declaredTypeArity(type.name());
    if (activeTypeParameters.containsKey(type.name())) arity = 0;
    if ((!allowVoid && type.name().equals("void"))
        || (arity < 0 && !activeTypeParameters.containsKey(type.name()))) {
      diagnostics.error(UNKNOWN_NAME, "unknown or invalid type '" + name + "'", type.span());
      return;
    }
    if (arity != type.arguments().size()) {
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
    for (Syntax.TypeRef argument : type.arguments()) {
      validateType(argument, false);
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
    if (!TypeRelations.isAssignable(expected, actual)) {
      diagnostics.error(
          TYPE_MISMATCH,
          "expected " + expected.displayName() + " but found " + actual.displayName(),
          span);
    }
  }

  private void declareExisting(String name, SemanticType type, SourceSpan span, SymbolId id) {
    ScopeFrame scope = scopes.getFirst();
    if (scope.symbols().putIfAbsent(name, new ScopedSymbol(type, id)) != null) {
      diagnostics.error(DUPLICATE_NAME, "name '" + name + "' is already declared", span);
    } else {
      scope.declarations().add(id);
    }
  }

  private void declareSynthetic(String name, SemanticType type, SourceSpan span) {
    SymbolId id = SymbolId.source(span.source().id(), nextSymbolId++);
    Symbol symbol =
        new Symbol(
            id,
            name,
            SymbolKind.LOCAL_VARIABLE,
            type,
            Optional.empty(),
            Optional.ofNullable(currentCallable),
            List.of(),
            List.of(),
            "");
    symbols.put(id, symbol);
    declareExisting(name, type, span, symbol.id());
  }

  private SemanticType lookup(String name, SourceSpan span) {
    for (ScopeFrame scope : scopes) {
      ScopedSymbol symbol = scope.symbols().get(name);
      if (symbol != null) {
        bindings.put(span, symbol.id());
        return symbol.type();
      }
    }
    diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
    return SemanticType.DYNAMIC;
  }

  private void analyzeNested(List<Syntax.Statement> statements) {
    pushScope(scopeSpan(statements));
    analyzeStatements(statements);
    popScope();
  }

  private void validateLoopControl(SourceSpan span) {
    if (loopDepth == 0) {
      diagnostics.error(INVALID_CONTROL, "break/continue is only valid inside for", span);
    }
  }

  private void pushScope(SourceSpan span) {
    scopes.addFirst(new ScopeFrame(new HashMap<>(), new ArrayList<>(), span, scopes.size()));
  }

  private void popScope() {
    ScopeFrame scope = scopes.removeFirst();
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
      List<String> typeParameters,
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

  private SemanticType resolveType(Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    SemanticType parameter = typeParameters.get(type.name());
    if (parameter != null) return parameter;
    if (type.name().equals("void")) return SemanticType.VOID;
    List<SemanticType> arguments =
        type.arguments().stream().map(argument -> resolveType(argument, typeParameters)).toList();
    if (builtins.isType(type.name())) return builtins.instantiate(type.name(), arguments);
    return sourceType(type.name(), arguments);
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
    boolean privateType =
        classDecl != null && classDecl.visibility() == Syntax.Visibility.PRIVATE
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
    Syntax.ClassDecl classDecl = resolveClass(name);
    if (classDecl == null) classDecl = resolveImportedClassByDeclaredName(name);
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    Object declaration = classDecl != null ? classDecl : enumDecl;
    Syntax.Program owner =
        declaration == null ? currentProgram : declarationPrograms.get(declaration);
    String declaredName =
        classDecl != null ? classDecl.name() : enumDecl != null ? enumDecl.name() : name;
    String identity = qualifiedName(owner == null ? "" : owner.packageName(), declaredName);
    if (classDecl != null && classDecl.visibility() == Syntax.Visibility.PRIVATE
        || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE) {
      identity += "@" + owner.span().source().id();
    }
    ValueCategory category = classDecl != null ? ValueCategory.IDENTITY : ValueCategory.VALUE;
    return SemanticType.declared(identity, declaredName, arguments, category);
  }

  private int declaredTypeArity(String name) {
    int builtinArity = builtins.typeArity(name);
    if (builtinArity >= 0) return builtinArity;
    Syntax.ClassDecl classDecl = resolveClass(name);
    if (classDecl != null) return classDecl.typeParameters().size();
    return resolveEnum(name) != null ? 0 : -1;
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

  private SemanticType classSelfType(Syntax.ClassDecl classDecl) {
    Map<String, SemanticType> parameters = classTypeParameters(classDecl);
    return sourceType(
        classDecl.name(),
        classDecl.typeParameters().stream()
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

  private Map<String, SemanticType> inferTypeArguments(
      Syntax.FunctionDecl function, Syntax.Name name, Syntax.Call call, SemanticType expected) {
    Map<String, SemanticType> declarations = functionTypeParameters(function);
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    if (!name.typeArguments().isEmpty()) {
      if (name.typeArguments().size() != function.typeParameters().size()) {
        diagnostics.error(
            INVALID_CALL,
            "function '"
                + function.name()
                + "' requires "
                + function.typeParameters().size()
                + " type argument(s), found "
                + name.typeArguments().size(),
            name.span());
      }
      for (int index = 0;
          index < Math.min(name.typeArguments().size(), function.typeParameters().size());
          index++) {
        SemanticType parameter = declarations.get(function.typeParameters().get(index).name());
        substitutions.put(
            parameter.identity(),
            resolveCheckedType(name.typeArguments().get(index), activeTypeParameters));
      }
    } else {
      for (int index = 0; index < call.arguments().size(); index++) {
        Syntax.CallArgument argument = call.arguments().get(index);
        int parameterIndex = index;
        if (argument.label().isPresent()) {
          String label = argument.label().orElseThrow().name();
          parameterIndex = -1;
          for (int candidate = 0; candidate < function.parameters().size(); candidate++) {
            if (function.parameters().get(candidate).name().equals(label)) {
              parameterIndex = candidate;
              break;
            }
          }
        }
        if (parameterIndex >= 0 && parameterIndex < function.parameters().size()) {
          SemanticType pattern =
              resolveType(function.parameters().get(parameterIndex).type(), declarations);
          infer(pattern, typeOf(argument.value(), null), substitutions, argument.span());
        }
      }
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        infer(
            resolveType(function.returnType(), declarations), expected, substitutions, name.span());
      }
    }
    for (Syntax.TypeParameter parameterSyntax : function.typeParameters()) {
      SemanticType parameter = declarations.get(parameterSyntax.name());
      if (!substitutions.containsKey(parameter.identity())) {
        diagnostics.error(
            INVALID_CALL,
            "cannot infer type argument '" + parameterSyntax.name() + "'",
            name.span());
        substitutions.put(parameter.identity(), SemanticType.DYNAMIC);
      }
    }
    return substitutions;
  }

  private void infer(
      SemanticType pattern,
      SemanticType actual,
      Map<String, SemanticType> substitutions,
      SourceSpan span) {
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType previous = substitutions.putIfAbsent(pattern.identity(), actual);
      if (previous != null && !previous.equals(actual)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type parameter '"
                + pattern.name()
                + "' inferred as both "
                + previous.displayName()
                + " and "
                + actual.displayName(),
            span);
      }
      return;
    }
    if (!pattern.identity().equals(actual.identity())
        || pattern.arguments().size() != actual.arguments().size()) return;
    for (int index = 0; index < pattern.arguments().size(); index++) {
      infer(pattern.arguments().get(index), actual.arguments().get(index), substitutions, span);
    }
  }

  private static Syntax.Program merge(List<Syntax.Program> programs, Syntax.Program entryProgram) {
    List<Syntax.EnumDecl> enums = new ArrayList<>();
    List<Syntax.ClassDecl> classes = new ArrayList<>();
    List<Syntax.FunctionDecl> functions = new ArrayList<>();
    for (Syntax.Program program : programs) {
      enums.addAll(program.enums());
      classes.addAll(program.classes());
      functions.addAll(program.functions());
    }
    return new Syntax.Program(
        entryProgram.packageName(),
        entryProgram.imports(),
        enums,
        classes,
        functions,
        entryProgram.span());
  }

  private void indexDeclarationPrograms() {
    for (Syntax.Program program : programs) {
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
    return resolveDeclaration(name, functions);
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
        identity += "@" + owner.span().source().id();
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

  private <T> T resolveDeclaration(String name, Map<String, T> declarations) {
    if (currentProgram == null) return null;
    T localPrivate =
        declarations.get(
            qualifiedName(currentProgram.packageName(), name)
                + "@"
                + currentProgram.span().source().id());
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
        ? qualified + "@" + program.span().source().id()
        : qualified;
  }

  private static String qualifiedName(String packageName, String name) {
    return packageName.isEmpty() ? name : packageName + "." + name;
  }

  private record ScopedSymbol(SemanticType type, SymbolId id) {}

  private record ScopeFrame(
      Map<String, ScopedSymbol> symbols, List<SymbolId> declarations, SourceSpan span, int depth) {}
}
