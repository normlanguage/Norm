package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundArgument;
import dev.w0fv1.norm.bound.BoundBinaryOperator;
import dev.w0fv1.norm.bound.BoundBlock;
import dev.w0fv1.norm.bound.BoundCall;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundCallableId;
import dev.w0fv1.norm.bound.BoundClass;
import dev.w0fv1.norm.bound.BoundClassId;
import dev.w0fv1.norm.bound.BoundConstruct;
import dev.w0fv1.norm.bound.BoundEnum;
import dev.w0fv1.norm.bound.BoundEnumId;
import dev.w0fv1.norm.bound.BoundEnumMember;
import dev.w0fv1.norm.bound.BoundEnumMemberId;
import dev.w0fv1.norm.bound.BoundExpression;
import dev.w0fv1.norm.bound.BoundField;
import dev.w0fv1.norm.bound.BoundFieldId;
import dev.w0fv1.norm.bound.BoundIntrinsic;
import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.bound.BoundParameter;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.bound.BoundReifiedArgument;
import dev.w0fv1.norm.bound.BoundRuntimeType;
import dev.w0fv1.norm.bound.BoundSource;
import dev.w0fv1.norm.bound.BoundStatement;
import dev.w0fv1.norm.bound.BoundUnaryOperator;
import dev.w0fv1.norm.bound.BoundValueTransfer;
import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Binder {
  private final List<Syntax.Program> programs;
  private final SemanticModel semantics;
  private final BuiltinCatalog builtins = BuiltinCatalog.standard();
  private final Map<String, BoundClass> classes = new LinkedHashMap<>();
  private final Map<String, BoundEnum> enums = new LinkedHashMap<>();
  private final Map<String, BoundCallable> callables = new LinkedHashMap<>();
  private final Map<String, BoundField> fields = new LinkedHashMap<>();
  private Map<String, BoundLocalId> reifiedLocals = Map.of();
  private BoundLocalId thisLocal;
  private SemanticType thisType;
  private int syntheticId;

  Binder(List<Syntax.Program> programs, SemanticModel semantics) {
    this.programs = List.copyOf(programs);
    this.semantics = semantics;
  }

  BoundProgram bind(Syntax.FunctionDecl entryPoint) {
    bindTypes();
    bindCallables();
    List<BoundSource> sources =
        programs.stream()
            .map(
                program ->
                    new BoundSource(
                        program.span().source(),
                        program.packageName(),
                        program.enums().stream().map(this::enumId).toList(),
                        program.classes().stream().map(this::classId).toList(),
                        sourceCallables(program)))
            .toList();
    return new BoundProgram(
        sources,
        List.copyOf(enums.values()),
        List.copyOf(classes.values()),
        List.copyOf(callables.values()),
        Optional.ofNullable(entryPoint).map(this::callableId));
  }

  private void bindTypes() {
    for (Syntax.Program program : programs) {
      for (Syntax.EnumDecl declaration : program.enums()) {
        Symbol symbol = symbol(declaration.nameSpan());
        BoundEnum value =
            new BoundEnum(
                BoundEnumId.of(symbol.id()),
                declaration.name(),
                symbol.type(),
                bindEnumMembers(declaration),
                declaration.span());
        enums.put(value.id().value(), value);
      }
      for (Syntax.ClassDecl declaration : program.classes()) {
        Symbol symbol = symbol(declaration.nameSpan());
        List<BoundField> boundFields = new ArrayList<>();
        for (int ordinal = 0; ordinal < declaration.fields().size(); ordinal++) {
          Syntax.FieldDecl field = declaration.fields().get(ordinal);
          Symbol fieldSymbol = symbol(field.nameSpan());
          BoundField bound =
              new BoundField(
                  BoundFieldId.of(fieldSymbol.id()), field.name(), fieldSymbol.type(), ordinal);
          fields.put(bound.id().value(), bound);
          boundFields.add(bound);
        }
        BoundClass value =
            new BoundClass(
                BoundClassId.of(symbol.id()),
                declaration.name(),
                symbol.type(),
                boundFields,
                declaration.methods().stream().map(this::callableId).toList(),
                declaration.span());
        classes.put(value.id().value(), value);
      }
    }
  }

  private void bindCallables() {
    for (Syntax.Program program : programs) {
      for (Syntax.FunctionDecl function : program.functions()) bindCallable(function, null);
      for (Syntax.ClassDecl owner : program.classes()) {
        for (Syntax.FunctionDecl method : owner.methods()) bindCallable(method, owner);
      }
    }
  }

  private void bindCallable(Syntax.FunctionDecl declaration, Syntax.ClassDecl owner) {
    Symbol callable = symbol(declaration.nameSpan());
    BoundCallableId id = BoundCallableId.of(callable.id());
    BoundClassId ownerId = owner == null ? null : classId(owner);
    thisLocal = owner == null ? null : new BoundLocalId(id.value() + "/this");
    thisType =
        owner == null
            ? null
            : SemanticType.declared(
                symbol(owner.nameSpan()).type().identity(),
                owner.name(),
                owner.typeParameters().stream()
                    .map(parameter -> symbol(parameter.nameSpan()).type())
                    .toList(),
                symbol(owner.nameSpan()).type().category());
    Map<String, BoundLocalId> activeReified = new LinkedHashMap<>();
    List<BoundReifiedArgument> reified = new ArrayList<>();
    if (owner != null) {
      addReified(owner.typeParameters(), id, activeReified, reified);
    }
    addReified(declaration.typeParameters(), id, activeReified, reified);
    reifiedLocals = Map.copyOf(activeReified);
    List<BoundParameter> parameters = new ArrayList<>();
    for (int ordinal = 0; ordinal < declaration.parameters().size(); ordinal++) {
      Syntax.Parameter parameter = declaration.parameters().get(ordinal);
      Symbol symbol = symbol(parameter.nameSpan());
      parameters.add(
          new BoundParameter(
              BoundLocalId.of(symbol.id()), parameter.name(), symbol.type(), ordinal));
    }
    BoundCallable bound =
        new BoundCallable(
            id,
            declaration.name(),
            declaration.visibility() == Syntax.Visibility.PUBLIC
                ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
                : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
            Optional.ofNullable(ownerId),
            Optional.ofNullable(thisLocal),
            parameters,
            reified,
            callable.type(),
            bindBlock(declaration.body(), declaration.span()),
            declaration.span());
    callables.put(id.value(), bound);
    reifiedLocals = Map.of();
    thisLocal = null;
    thisType = null;
  }

  private void addReified(
      List<Syntax.TypeParameter> parameters,
      BoundCallableId callable,
      Map<String, BoundLocalId> active,
      List<BoundReifiedArgument> result) {
    for (Syntax.TypeParameter parameter : parameters) {
      SemanticType type = symbol(parameter.nameSpan()).type();
      BoundLocalId local = new BoundLocalId(callable.value() + "/type/" + type.identity());
      active.put(type.identity(), local);
      result.add(new BoundReifiedArgument(type.identity(), local));
    }
  }

  private BoundBlock bindBlock(List<Syntax.Statement> statements, SourceSpan fallback) {
    SourceSpan span =
        statements.isEmpty()
            ? fallback
            : statements.getFirst().span().cover(statements.getLast().span());
    return new BoundBlock(statements.stream().map(this::bindStatement).toList(), span);
  }

  private BoundStatement bindStatement(Syntax.Statement statement) {
    return switch (statement) {
      case Syntax.VariableDecl variable -> {
        Symbol symbol = symbol(variable.nameSpan());
        yield new BoundStatement.LocalDeclaration(
            BoundLocalId.of(symbol.id()),
            variable.name(),
            symbol.type(),
            bindExpression(variable.initializer()),
            BoundValueTransfer.COPY,
            variable.span());
      }
      case Syntax.Assignment assignment -> bindAssignment(assignment);
      case Syntax.ExpressionStatement expression ->
          new BoundStatement.ExpressionStatement(
              bindExpression(expression.expression()), expression.span());
      case Syntax.IfStatement conditional ->
          new BoundStatement.IfStatement(
              bindExpression(conditional.condition()),
              bindBlock(conditional.thenBody(), conditional.span()),
              bindBlock(conditional.elseBody(), conditional.span()),
              conditional.span());
      case Syntax.ForStatement loop -> {
        Symbol variable = symbol(loop.variableNameSpan());
        yield new BoundStatement.ForStatement(
            new BoundLocalId(variable.id().value() + "/iterator/" + syntheticId++),
            BoundLocalId.of(variable.id()),
            loop.variableName(),
            variable.type(),
            bindExpression(loop.iterable()),
            bindBlock(loop.body(), loop.span()),
            semantics.iterationOf(loop.iterable().span()).orElseThrow().intrinsic(),
            BoundValueTransfer.COPY,
            loop.span());
      }
      case Syntax.ReturnStatement returned ->
          new BoundStatement.ReturnStatement(
              Optional.ofNullable(returned.value()).map(this::bindExpression),
              BoundValueTransfer.COPY,
              returned.span());
      case Syntax.BreakStatement broken -> new BoundStatement.BreakStatement(broken.span());
      case Syntax.ContinueStatement continued ->
          new BoundStatement.ContinueStatement(continued.span());
    };
  }

  private BoundStatement bindAssignment(Syntax.Assignment assignment) {
    BoundExpression value = bindExpression(assignment.value());
    return switch (assignment.target()) {
      case Syntax.Name name -> {
        Symbol target = symbol(name.span());
        if (target.kind() == SymbolKind.FIELD) {
          BoundField field = field(target);
          yield new BoundStatement.FieldAssignment(
              thisRead(name.span()),
              field.id(),
              field.ordinal(),
              value,
              BoundValueTransfer.COPY,
              assignment.span());
        }
        yield new BoundStatement.LocalAssignment(
            BoundLocalId.of(target.id()), value, BoundValueTransfer.COPY, assignment.span());
      }
      case Syntax.Member member -> {
        Symbol target = symbol(member.nameSpan());
        if (isBuiltin(target)) {
          yield new BoundStatement.IntrinsicAssignment(
              builtins.writeIntrinsic(target.id()).orElseThrow(),
              bindExpression(member.receiver()),
              Optional.empty(),
              value,
              BoundValueTransfer.COPY,
              assignment.span());
        }
        BoundField field = field(target);
        yield new BoundStatement.FieldAssignment(
            bindExpression(member.receiver()),
            field.id(),
            field.ordinal(),
            value,
            BoundValueTransfer.COPY,
            assignment.span());
      }
      case Syntax.Index index -> {
        var resolved = semantics.indexOf(index.span()).orElseThrow();
        yield new BoundStatement.IntrinsicAssignment(
            resolved.writeIntrinsic().orElseThrow(),
            bindExpression(index.receiver()),
            Optional.of(bindExpression(index.index())),
            value,
            BoundValueTransfer.COPY,
            assignment.span());
      }
      default -> throw new IllegalStateException("invalid checked assignment target");
    };
  }

  private BoundExpression bindExpression(Syntax.Expression expression) {
    SemanticType type = semantics.typeOf(expression.span()).orElseThrow();
    return switch (expression) {
      case Syntax.IntegerLiteral integer ->
          new BoundExpression.Literal(integer.value(), type, integer.span());
      case Syntax.BooleanLiteral bool ->
          new BoundExpression.Literal(bool.value(), type, bool.span());
      case Syntax.StringLiteralExpr string ->
          new BoundExpression.Literal(string.value(), type, string.span());
      case Syntax.ArrayLiteral array ->
          new BoundExpression.ArrayLiteral(
              array.elements().stream().map(this::bindExpression).toList(),
              runtimeType(type),
              type,
              array.span());
      case Syntax.Name name -> bindName(name, type);
      case Syntax.Unary unary ->
          new BoundExpression.Unary(
              unary.operator() == TokenKind.BANG
                  ? BoundUnaryOperator.NOT
                  : BoundUnaryOperator.NEGATE,
              bindExpression(unary.operand()),
              type,
              unary.span());
      case Syntax.Binary binary ->
          new BoundExpression.Binary(
              bindExpression(binary.left()),
              binaryOperator(binary.operator(), type),
              bindExpression(binary.right()),
              type,
              binary.span());
      case Syntax.Call call -> bindCall(call, type);
      case Syntax.Member member -> bindMember(member, type);
      case Syntax.Index index -> {
        var resolved = semantics.indexOf(index.span()).orElseThrow();
        yield new BoundExpression.Index(
            bindExpression(index.receiver()),
            bindExpression(index.index()),
            resolved.readIntrinsic(),
            resolved.writeIntrinsic(),
            type,
            index.span());
      }
    };
  }

  private BoundExpression bindName(Syntax.Name name, SemanticType type) {
    Symbol symbol = symbol(name.span());
    if (symbol.kind() == SymbolKind.FIELD) {
      BoundField field = field(symbol);
      return new BoundExpression.FieldRead(
          thisRead(name.span()), field.id(), field.ordinal(), type, name.span());
    }
    return new BoundExpression.LocalRead(BoundLocalId.of(symbol.id()), type, name.span());
  }

  private BoundExpression bindCall(Syntax.Call call, SemanticType type) {
    List<BoundArgument> arguments = bindArguments(call);
    if (call.callee() instanceof Syntax.Name name) {
      Symbol target = semantics.resolvedSymbolOf(name.span()).orElseThrow();
      if (isBuiltin(target)) {
        return new BoundIntrinsic(
            builtins.intrinsic(target.id()).orElseThrow(),
            Optional.empty(),
            arguments,
            builtins
                    .type(target.id())
                    .flatMap(BuiltinCatalog.TypeDefinition::constructor)
                    .isPresent()
                ? Optional.of(runtimeType(type))
                : Optional.empty(),
            type,
            call.span());
      }
      if (target.kind() == SymbolKind.TYPE) {
        return new BoundConstruct(
            new BoundClassId(target.id().value()), runtimeType(type), arguments, type, call.span());
      }
      return new BoundCall(
          BoundCallableId.of(target.id()),
          Optional.empty(),
          arguments,
          semantics.typeArgumentsOf(call.span()).stream().map(this::runtimeType).toList(),
          type,
          call.span());
    }
    Syntax.Member member = (Syntax.Member) call.callee();
    Symbol target = symbol(member.nameSpan());
    BoundExpression receiver = bindExpression(member.receiver());
    if (isBuiltin(target)) {
      return new BoundIntrinsic(
          builtins.intrinsic(target.id()).orElseThrow(),
          Optional.of(receiver),
          arguments,
          Optional.empty(),
          type,
          call.span());
    }
    if (target.name().equals("copy") && !callables.containsKey(target.id().value())) {
      return new BoundExpression.CopyObject(receiver, type, call.span());
    }
    return new BoundCall(
        BoundCallableId.of(target.id()),
        Optional.of(receiver),
        arguments,
        List.of(),
        type,
        call.span());
  }

  private BoundExpression bindMember(Syntax.Member member, SemanticType type) {
    Symbol target = symbol(member.nameSpan());
    if (target.kind() == SymbolKind.ENUM_MEMBER) {
      Symbol owner = semantics.symbol(target.owner().orElseThrow()).orElseThrow();
      return new BoundExpression.EnumMember(
          BoundEnumId.of(owner.id()),
          BoundEnumMemberId.of(target.id()),
          owner.name(),
          target.name(),
          type,
          member.span());
    }
    BoundExpression receiver = bindExpression(member.receiver());
    if (isBuiltin(target)) {
      return new BoundIntrinsic(
          builtins.intrinsic(target.id()).orElseThrow(),
          Optional.of(receiver),
          List.of(),
          Optional.empty(),
          type,
          member.span());
    }
    BoundField field = field(target);
    return new BoundExpression.FieldRead(
        receiver, field.id(), field.ordinal(), type, member.span());
  }

  private List<BoundArgument> bindArguments(Syntax.Call call) {
    List<Integer> indices = semantics.argumentsOf(call.span()).orElseThrow().parameterIndices();
    List<BoundArgument> result = new ArrayList<>();
    for (int index = 0; index < call.arguments().size(); index++) {
      result.add(
          new BoundArgument(
              bindExpression(call.arguments().get(index).value()),
              indices.get(index),
              BoundValueTransfer.COPY));
    }
    return result;
  }

  private BoundRuntimeType runtimeType(SemanticType type) {
    List<BoundReifiedArgument> captures = new ArrayList<>();
    collectCaptures(type, captures);
    return new BoundRuntimeType(type, captures);
  }

  private void collectCaptures(SemanticType type, List<BoundReifiedArgument> captures) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      BoundLocalId local = reifiedLocals.get(type.identity());
      if (local != null
          && captures.stream()
              .noneMatch(value -> value.typeParameterIdentity().equals(type.identity()))) {
        captures.add(new BoundReifiedArgument(type.identity(), local));
      }
    }
    type.arguments().forEach(argument -> collectCaptures(argument, captures));
  }

  private BoundExpression thisRead(SourceSpan span) {
    if (thisLocal == null) throw new IllegalStateException("implicit field access outside method");
    return new BoundExpression.LocalRead(thisLocal, thisType, span);
  }

  private static BoundBinaryOperator binaryOperator(TokenKind operator, SemanticType resultType) {
    return switch (operator) {
      case PLUS ->
          resultType.name().equals("String")
              ? BoundBinaryOperator.STRING_CONCAT
              : BoundBinaryOperator.ADD;
      case MINUS -> BoundBinaryOperator.SUBTRACT;
      case STAR -> BoundBinaryOperator.MULTIPLY;
      case SLASH -> BoundBinaryOperator.DIVIDE;
      case PERCENT -> BoundBinaryOperator.REMAINDER;
      case LESS -> BoundBinaryOperator.LESS;
      case LESS_EQUAL -> BoundBinaryOperator.LESS_EQUAL;
      case GREATER -> BoundBinaryOperator.GREATER;
      case GREATER_EQUAL -> BoundBinaryOperator.GREATER_EQUAL;
      case EQUAL_EQUAL -> BoundBinaryOperator.EQUAL;
      case BANG_EQUAL -> BoundBinaryOperator.NOT_EQUAL;
      case AND_AND -> BoundBinaryOperator.AND;
      case OR_OR -> BoundBinaryOperator.OR;
      default -> throw new IllegalStateException("unsupported checked binary operator " + operator);
    };
  }

  private BoundField field(Symbol symbol) {
    BoundField field = fields.get(symbol.id().value());
    if (field == null) throw new IllegalStateException("bound field is absent: " + symbol.id());
    return field;
  }

  private Symbol symbol(SourceSpan span) {
    return semantics
        .symbolOf(span)
        .orElseThrow(() -> new IllegalStateException("symbol is absent"));
  }

  private static boolean isBuiltin(Symbol symbol) {
    return symbol.id().value().startsWith("builtin/");
  }

  private BoundCallableId callableId(Syntax.FunctionDecl declaration) {
    return BoundCallableId.of(symbol(declaration.nameSpan()).id());
  }

  private BoundClassId classId(Syntax.ClassDecl declaration) {
    return BoundClassId.of(symbol(declaration.nameSpan()).id());
  }

  private BoundEnumId enumId(Syntax.EnumDecl declaration) {
    return BoundEnumId.of(symbol(declaration.nameSpan()).id());
  }

  private List<BoundEnumMember> bindEnumMembers(Syntax.EnumDecl declaration) {
    List<BoundEnumMember> result = new ArrayList<>();
    for (int ordinal = 0; ordinal < declaration.members().size(); ordinal++) {
      Syntax.EnumMember member = declaration.members().get(ordinal);
      result.add(
          new BoundEnumMember(
              BoundEnumMemberId.of(symbol(member.nameSpan()).id()), member.name(), ordinal));
    }
    return List.copyOf(result);
  }

  private List<BoundCallableId> sourceCallables(Syntax.Program program) {
    List<BoundCallableId> result = new ArrayList<>();
    program.functions().forEach(value -> result.add(callableId(value)));
    program
        .classes()
        .forEach(owner -> owner.methods().forEach(value -> result.add(callableId(value))));
    return List.copyOf(result);
  }
}
