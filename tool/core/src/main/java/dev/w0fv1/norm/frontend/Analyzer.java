package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.semantic.ArgumentBinding;
import dev.w0fv1.norm.semantic.BuiltinSymbols;
import dev.w0fv1.norm.semantic.IndexKind;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.AnalysisResult;
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

  private static final String DYNAMIC = "value";
  private final Syntax.Program syntax;
  private final DiagnosticBag diagnostics;
  private final Map<String, Syntax.FunctionDecl> functions = new HashMap<>();
  private final Map<String, Syntax.ClassDecl> classes = new HashMap<>();
  private final Map<String, Syntax.EnumDecl> enums = new HashMap<>();
  private final BuiltinSymbols builtins = new BuiltinSymbols();
  private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
  private final Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
  private final Map<SourceSpan, SemanticType> semanticTypes = new LinkedHashMap<>();
  private final Map<SourceSpan, ArgumentBinding> argumentBindings = new LinkedHashMap<>();
  private final Map<SymbolId, List<SymbolId>> members = new LinkedHashMap<>();
  private final Map<Object, SymbolId> declarationSymbols = new IdentityHashMap<>();
  private final Map<String, SymbolId> copyMethods = new HashMap<>();
  private final Deque<ScopeFrame> scopes = new ArrayDeque<>();
  private final List<SemanticScope> semanticScopes = new ArrayList<>();
  private int nextSymbolId;
  private SymbolId currentCallable;
  private String expectedReturnType = "void";
  private int loopDepth;

  Analyzer(Syntax.Program syntax, DiagnosticBag diagnostics) {
    this.syntax = syntax;
    this.diagnostics = diagnostics;
    symbols.putAll(builtins.symbols());
    builtins.members().forEach((owner, values) -> members.put(owner, new ArrayList<>(values)));
  }

  AnalysisResult analyze() {
    collectDeclarations();
    Syntax.FunctionDecl main = functions.get("main");
    if (main == null) {
      diagnostics.error(MISSING_MAIN, "program must declare 'void main()'", syntax.span());
    } else if (!main.returnType().displayName().equals("void") || !main.parameters().isEmpty()) {
      diagnostics.error(TYPE_MISMATCH, "entry function must be 'void main()'", main.span());
    }

    for (Syntax.FunctionDecl function : syntax.functions()) {
      analyzeFunction(function, null);
    }
    for (Syntax.ClassDecl classDecl : syntax.classes()) {
      validateFields(classDecl);
      for (Syntax.FunctionDecl method : classDecl.methods()) {
        analyzeFunction(method, classDecl);
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
            members,
            semanticScopes,
            snapshot);
    return new AnalysisResult(semanticModel, Optional.ofNullable(main), snapshot);
  }

  private void collectDeclarations() {
    for (Syntax.EnumDecl enumDecl : syntax.enums()) {
      if (enums.putIfAbsent(enumDecl.name(), enumDecl) != null
          || builtins.isType(enumDecl.name())) {
        diagnostics.error(
            DUPLICATE_NAME, "type '" + enumDecl.name() + "' is already declared", enumDecl.span());
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
        diagnostics.error(TYPE_MISMATCH, "enum must declare at least one member", enumDecl.span());
      }
      Symbol type =
          register(
              enumDecl,
              enumDecl.name(),
              SymbolKind.TYPE,
              enumDecl.name(),
              enumDecl.nameSpan(),
              null,
              List.of());
      for (Syntax.EnumMember member : enumDecl.members()) {
        Symbol value =
            register(
                member,
                member.name(),
                SymbolKind.ENUM_MEMBER,
                enumDecl.name(),
                member.nameSpan(),
                type.id(),
                List.of());
        addMember(type.id(), value.id());
      }
    }
    for (Syntax.ClassDecl classDecl : syntax.classes()) {
      if (classes.putIfAbsent(classDecl.name(), classDecl) != null
          || enums.containsKey(classDecl.name())
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
              classDecl.name(),
              classDecl.nameSpan(),
              null,
              List.of());
      for (Syntax.FieldDecl field : classDecl.fields()) {
        Symbol symbol =
            register(
                field,
                field.name(),
                SymbolKind.FIELD,
                field.type().displayName(),
                field.nameSpan(),
                type.id(),
                List.of());
        addMember(type.id(), symbol.id());
      }
      SymbolId copyId = SymbolId.source(syntax.span().source().id(), nextSymbolId++);
      Symbol copy =
          new Symbol(
              copyId,
              "copy",
              SymbolKind.METHOD,
              semanticType(classDecl.name()),
              Optional.empty(),
              Optional.of(type.id()),
              List.of(),
              "Creates a new top-level object identity.");
      symbols.put(copyId, copy);
      addMember(type.id(), copyId);
      copyMethods.put(classDecl.name(), copyId);
      for (Syntax.FunctionDecl method : classDecl.methods()) {
        Symbol symbol =
            register(
                method,
                method.name(),
                SymbolKind.METHOD,
                method.returnType().displayName(),
                method.nameSpan(),
                type.id(),
                parameters(method.parameters()));
        addMember(type.id(), symbol.id());
      }
    }
    for (Syntax.FunctionDecl function : syntax.functions()) {
      if (functions.putIfAbsent(function.name(), function) != null) {
        diagnostics.error(
            DUPLICATE_NAME,
            "function '" + function.name() + "' is already declared",
            function.span());
      }
      register(
          function,
          function.name(),
          SymbolKind.FUNCTION,
          function.returnType().displayName(),
          function.nameSpan(),
          null,
          parameters(function.parameters()));
    }
  }

  private void validateFields(Syntax.ClassDecl classDecl) {
    Set<String> names = new HashSet<>();
    for (Syntax.FieldDecl field : classDecl.fields()) {
      validateType(field.type(), false);
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
  }

  private void analyzeFunction(Syntax.FunctionDecl function, Syntax.ClassDecl owner) {
    validateType(function.returnType(), true);
    expectedReturnType = function.returnType().displayName();
    currentCallable = declarationSymbols.get(function);
    scopes.clear();
    pushScope(function.span());
    if (owner != null) {
      declareSynthetic("this", owner.name(), owner.nameSpan());
      for (Syntax.FieldDecl field : owner.fields()) {
        declareExisting(
            field.name(),
            field.type().displayName(),
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
              parameter.type().displayName(),
              parameter.nameSpan(),
              declarationSymbols.get(function),
              List.of());
      declareExisting(
          parameter.name(), parameter.type().displayName(), parameter.nameSpan(), symbol.id());
    }
    analyzeStatements(function.body());
    if (!expectedReturnType.equals("void") && !containsReturn(function.body())) {
      diagnostics.error(
          INVALID_CONTROL,
          "function '" + function.name() + "' must return " + expectedReturnType,
          function.span());
    }
    popScope();
    currentCallable = null;
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
        String actual = typeOf(variable.initializer(), variable.type().displayName());
        requireAssignable(variable.type().displayName(), actual, variable.initializer().span());
        String declaredType = actual.equals(DYNAMIC) ? variable.type().displayName() : actual;
        Symbol symbol =
            register(
                variable,
                variable.name(),
                SymbolKind.LOCAL_VARIABLE,
                declaredType,
                variable.nameSpan(),
                currentCallable,
                List.of());
        declareExisting(variable.name(), declaredType, variable.nameSpan(), symbol.id());
      }
      case Syntax.Assignment assignment -> {
        String target = assignmentTargetType(assignment.target());
        String value = typeOf(assignment.value(), target);
        requireAssignable(target, value, assignment.value().span());
      }
      case Syntax.ExpressionStatement expression -> typeOf(expression.expression(), null);
      case Syntax.IfStatement ifStatement -> {
        requireType(
            "bool", typeOf(ifStatement.condition(), "bool"), ifStatement.condition().span());
        analyzeNested(ifStatement.thenBody());
        analyzeNested(ifStatement.elseBody());
      }
      case Syntax.ForStatement forStatement -> {
        String iterable = typeOf(forStatement.iterable(), null);
        if (!builtins.isIterable(iterable)) {
          diagnostics.error(
              TYPE_MISMATCH, "for requires an iterable value", forStatement.iterable().span());
        }
        String variableType;
        if (forStatement.variableType().isPresent()) {
          Syntax.TypeRef explicitType = forStatement.variableType().orElseThrow();
          validateType(explicitType, false);
          variableType = explicitType.displayName();
          builtins
              .iterableElementType(iterable)
              .ifPresent(
                  elementType ->
                      requireAssignable(
                          variableType,
                          elementType.displayName(),
                          forStatement.variableNameSpan()));
        } else {
          Optional<SemanticType> elementType = builtins.iterableElementType(iterable);
          if (elementType.isEmpty()) {
            diagnostics.error(
                TYPE_MISMATCH,
                "cannot infer loop variable type from "
                    + iterable
                    + "; declare it explicitly because V0.1 containers do not retain element types",
                forStatement.variableNameSpan());
            variableType = DYNAMIC;
          } else {
            variableType = elementType.orElseThrow().displayName();
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
        String actual =
            returnStatement.value() == null
                ? "void"
                : typeOf(returnStatement.value(), expectedReturnType);
        requireAssignable(expectedReturnType, actual, returnStatement.span());
      }
      case Syntax.BreakStatement breakStatement -> validateLoopControl(breakStatement.span());
      case Syntax.ContinueStatement continueStatement ->
          validateLoopControl(continueStatement.span());
    }
  }

  private String typeOf(Syntax.Expression expression, String expected) {
    String type =
        switch (expression) {
          case Syntax.IntegerLiteral ignored -> "int";
          case Syntax.BooleanLiteral ignored -> "bool";
          case Syntax.StringLiteralExpr ignored -> "String";
          case Syntax.ArrayLiteral array -> analyzeArray(array);
          case Syntax.Name name -> lookup(name.value(), name.span());
          case Syntax.Unary unary -> analyzeUnary(unary);
          case Syntax.Binary binary -> analyzeBinary(binary);
          case Syntax.Call call -> analyzeCall(call);
          case Syntax.Member member -> memberType(member);
          case Syntax.Index index -> analyzeIndex(index);
        };
    if (expected != null && type.equals(DYNAMIC)) {
      type = expected;
    }
    semanticTypes.put(expression.span(), semanticType(type));
    return type;
  }

  private String analyzeArray(Syntax.ArrayLiteral array) {
    for (Syntax.Expression element : array.elements()) {
      typeOf(element, null);
    }
    return "Array";
  }

  private String analyzeUnary(Syntax.Unary unary) {
    String operand = typeOf(unary.operand(), null);
    String required = unary.operator() == TokenKind.BANG ? "bool" : "int";
    requireType(required, operand, unary.span());
    return required;
  }

  private String analyzeBinary(Syntax.Binary binary) {
    String left = typeOf(binary.left(), null);
    String right = typeOf(binary.right(), left);
    return switch (binary.operator()) {
      case PLUS -> {
        if (left.equals("String") && right.equals("String")) {
          yield "String";
        }
        requireBoth("int", left, right, binary.span());
        yield "int";
      }
      case MINUS, STAR, SLASH, PERCENT -> {
        requireBoth("int", left, right, binary.span());
        yield "int";
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        requireBoth("int", left, right, binary.span());
        yield "bool";
      }
      case AND_AND, OR_OR -> {
        requireBoth("bool", left, right, binary.span());
        yield "bool";
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        requireAssignable(left, right, binary.span());
        yield "bool";
      }
      default -> DYNAMIC;
    };
  }

  private String analyzeCall(Syntax.Call call) {
    if (call.callee() instanceof Syntax.Name name) {
      return analyzeNamedCall(name, call);
    }
    if (call.callee() instanceof Syntax.Member member) {
      return analyzeMethodCall(member, call);
    }
    diagnostics.error(INVALID_CALL, "expression is not callable", call.callee().span());
    analyzeArguments(call.arguments());
    return DYNAMIC;
  }

  private String analyzeNamedCall(Syntax.Name name, Syntax.Call call) {
    String callee = name.value();
    builtins
        .global(callee)
        .or(() -> builtins.type(callee))
        .ifPresent(symbol -> bindings.put(name.span(), symbol.id()));
    Optional<Symbol> builtinFunction = builtins.global(callee);
    if (builtinFunction.isPresent()) {
      Symbol symbol = builtinFunction.orElseThrow();
      validateArguments(call, symbol.parameters());
      return symbol.type().displayName();
    }
    Optional<List<ParameterInfo>> constructor = builtins.constructorParameters(callee);
    if (constructor.isPresent()) {
      validateArguments(call, constructor.orElseThrow());
      return callee;
    }
    Syntax.ClassDecl classDecl = classes.get(callee);
    if (classDecl != null) {
      bindings.put(name.span(), declarationSymbols.get(classDecl));
      validateArguments(call, fieldParameters(classDecl.fields()));
      return callee;
    }
    Syntax.FunctionDecl function = functions.get(callee);
    if (function != null) {
      bindings.put(name.span(), declarationSymbols.get(function));
      validateArguments(call, parameters(function.parameters()));
      return function.returnType().displayName();
    }
    diagnostics.error(UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
    analyzeArguments(call.arguments());
    return DYNAMIC;
  }

  private String analyzeMethodCall(Syntax.Member member, Syntax.Call call) {
    String receiverType = typeOf(member.receiver(), null);
    if (member.name().isEmpty()) {
      analyzeArguments(call.arguments());
      return DYNAMIC;
    }
    if (builtins.isContainer(receiverType)) {
      Optional<Symbol> resolved = builtins.member(receiverType, member.name());
      if (resolved.isEmpty() || resolved.orElseThrow().kind() != SymbolKind.METHOD) {
        diagnostics.error(
            UNKNOWN_NAME,
            "type '" + receiverType + "' has no method '" + member.name() + "'",
            call.span());
        analyzeArguments(call.arguments());
        return DYNAMIC;
      }
      Symbol symbol = resolved.orElseThrow();
      bindings.put(member.nameSpan(), symbol.id());
      validateArguments(call, symbol.parameters());
      return symbol.type().displayName();
    }
    Syntax.ClassDecl classDecl = classes.get(receiverType);
    if (classDecl != null) {
      if (member.name().equals("copy")) {
        bindings.put(member.nameSpan(), copyMethods.get(receiverType));
        validateArguments(call, List.of());
        return receiverType;
      }
      Syntax.FunctionDecl method =
          classDecl.methods().stream()
              .filter(candidate -> candidate.name().equals(member.name()))
              .findFirst()
              .orElse(null);
      if (method == null) {
        diagnostics.error(
            UNKNOWN_NAME,
            "class '" + receiverType + "' has no method '" + member.name() + "'",
            member.span());
        analyzeArguments(call.arguments());
        return DYNAMIC;
      }
      bindings.put(member.nameSpan(), declarationSymbols.get(method));
      validateArguments(call, parameters(method.parameters()));
      return method.returnType().displayName();
    }
    diagnostics.error(TYPE_MISMATCH, "type '" + receiverType + "' has no methods", member.span());
    analyzeArguments(call.arguments());
    return DYNAMIC;
  }

  private String memberType(Syntax.Member member) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = enums.get(enumName.value());
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
        return enumDecl.name();
      }
    }
    String receiver = typeOf(member.receiver(), null);
    if (member.name().isEmpty()) return DYNAMIC;
    if (builtins.supportsLength(receiver) && member.name().equals("length")) {
      builtins
          .member(receiver, member.name())
          .ifPresent(symbol -> bindings.put(member.nameSpan(), symbol.id()));
      return "int";
    }
    if (receiver.equals("Pair")
        && (member.name().equals("first") || member.name().equals("second"))) {
      builtins
          .member(receiver, member.name())
          .ifPresent(symbol -> bindings.put(member.nameSpan(), symbol.id()));
      return DYNAMIC;
    }
    Syntax.ClassDecl classDecl = classes.get(receiver);
    if (classDecl != null) {
      Syntax.FieldDecl field =
          classDecl.fields().stream()
              .filter(candidate -> candidate.name().equals(member.name()))
              .findFirst()
              .orElse(null);
      if (field != null) {
        bindings.put(member.nameSpan(), declarationSymbols.get(field));
        return field.type().displayName();
      }
      diagnostics.error(
          UNKNOWN_NAME,
          "class '" + receiver + "' has no field '" + member.name() + "'",
          member.span());
      return DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH,
        "type '" + receiver + "' has no member '" + member.name() + "'",
        member.span());
    return DYNAMIC;
  }

  private String analyzeIndex(Syntax.Index index) {
    String receiver = typeOf(index.receiver(), null);
    String indexType = typeOf(index.index(), null);
    IndexKind indexKind = builtins.indexKind(receiver);
    if (indexKind == IndexKind.VALUE) {
      return DYNAMIC;
    }
    if (indexKind == IndexKind.NONE) {
      diagnostics.error(TYPE_MISMATCH, "only Array, List, and Map can be indexed", index.span());
    }
    requireType("int", indexType, index.index().span());
    return DYNAMIC;
  }

  private String assignmentTargetType(Syntax.Expression target) {
    return switch (target) {
      case Syntax.Name name -> lookup(name.value(), name.span());
      case Syntax.Member member -> memberType(member);
      case Syntax.Index index -> analyzeIndex(index);
      default -> {
        diagnostics.error(TYPE_MISMATCH, "invalid assignment target", target.span());
        yield DYNAMIC;
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
            parameter.type().displayName(),
            typeOf(argument.value(), parameter.type().displayName()),
            argument.span());
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
    typeSymbol(type.name()).ifPresent(symbol -> bindings.put(type.span(), symbol.id()));
    if ((!allowVoid && name.equals("void"))
        || (!builtins.isType(name) && !classes.containsKey(name) && !enums.containsKey(name))) {
      diagnostics.error(UNKNOWN_NAME, "unknown or invalid type '" + name + "'", type.span());
    }
  }

  private void requireBoth(String expected, String left, String right, SourceSpan span) {
    requireType(expected, left, span);
    requireType(expected, right, span);
  }

  private void requireType(String expected, String actual, SourceSpan span) {
    requireAssignable(expected, actual, span);
  }

  private void requireAssignable(String expected, String actual, SourceSpan span) {
    if (!expected.equals(actual) && !expected.equals(DYNAMIC) && !actual.equals(DYNAMIC)) {
      diagnostics.error(TYPE_MISMATCH, "expected " + expected + " but found " + actual, span);
    }
  }

  private void declareExisting(String name, String type, SourceSpan span, SymbolId id) {
    ScopeFrame scope = scopes.getFirst();
    if (scope.symbols().putIfAbsent(name, new ScopedSymbol(type, id)) != null) {
      diagnostics.error(DUPLICATE_NAME, "name '" + name + "' is already declared", span);
    } else {
      scope.declarations().add(id);
    }
  }

  private void declareSynthetic(String name, String type, SourceSpan span) {
    SymbolId id = SymbolId.source(syntax.span().source().id(), nextSymbolId++);
    Symbol symbol =
        new Symbol(
            id,
            name,
            SymbolKind.LOCAL_VARIABLE,
            semanticType(type),
            Optional.empty(),
            Optional.ofNullable(currentCallable),
            List.of(),
            "");
    symbols.put(id, symbol);
    declareExisting(name, type, span, symbol.id());
  }

  private String lookup(String name, SourceSpan span) {
    for (ScopeFrame scope : scopes) {
      ScopedSymbol symbol = scope.symbols().get(name);
      if (symbol != null) {
        bindings.put(span, symbol.id());
        return symbol.type();
      }
    }
    diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
    return DYNAMIC;
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
    if (statements.isEmpty()) return syntax.span();
    return statements.getFirst().span().cover(statements.getLast().span());
  }

  private Symbol register(
      Object declaration,
      String name,
      SymbolKind kind,
      String type,
      SourceSpan nameSpan,
      SymbolId owner,
      List<ParameterInfo> parameters) {
    SymbolId id = SymbolId.source(syntax.span().source().id(), nextSymbolId++);
    Symbol symbol =
        new Symbol(
            id,
            name,
            kind,
            semanticType(type),
            Optional.of(nameSpan.location()),
            Optional.ofNullable(owner),
            parameters,
            "");
    symbols.put(id, symbol);
    declarationSymbols.put(declaration, id);
    bindings.put(nameSpan, id);
    return symbol;
  }

  private void addMember(SymbolId owner, SymbolId member) {
    members.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(member);
  }

  private Optional<Symbol> typeSymbol(String name) {
    Syntax.ClassDecl classDecl = classes.get(name);
    if (classDecl != null)
      return Optional.ofNullable(symbols.get(declarationSymbols.get(classDecl)));
    Syntax.EnumDecl enumDecl = enums.get(name);
    if (enumDecl != null) return Optional.ofNullable(symbols.get(declarationSymbols.get(enumDecl)));
    return builtins.type(name);
  }

  private List<ParameterInfo> parameters(List<Syntax.Parameter> parameters) {
    return parameters.stream()
        .map(
            parameter ->
                new ParameterInfo(parameter.name(), semanticType(parameter.type().displayName())))
        .toList();
  }

  private static boolean containsReturn(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && (containsReturn(conditional.thenBody()) || containsReturn(conditional.elseBody()))) {
        return true;
      }
    }
    return false;
  }

  private List<ParameterInfo> fieldParameters(List<Syntax.FieldDecl> fields) {
    return fields.stream()
        .map(field -> new ParameterInfo(field.name(), semanticType(field.type().displayName())))
        .toList();
  }

  private SemanticType semanticType(String name) {
    boolean identity =
        syntax.classes().stream()
            .anyMatch(classDeclaration -> classDeclaration.name().equals(name));
    return identity ? new SemanticType(name, ValueCategory.IDENTITY) : new SemanticType(name);
  }

  private record ScopedSymbol(String type, SymbolId id) {}

  private record ScopeFrame(
      Map<String, ScopedSymbol> symbols, List<SymbolId> declarations, SourceSpan span, int depth) {}
}
