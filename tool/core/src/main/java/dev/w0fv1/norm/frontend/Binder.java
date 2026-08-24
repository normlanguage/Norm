package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundArgument;
import dev.w0fv1.norm.bound.BoundBinaryOperator;
import dev.w0fv1.norm.bound.BoundBlock;
import dev.w0fv1.norm.bound.BoundBuiltinConformance;
import dev.w0fv1.norm.bound.BoundCall;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundCallableId;
import dev.w0fv1.norm.bound.BoundClass;
import dev.w0fv1.norm.bound.BoundClassId;
import dev.w0fv1.norm.bound.BoundConformance;
import dev.w0fv1.norm.bound.BoundConstruct;
import dev.w0fv1.norm.bound.BoundEnum;
import dev.w0fv1.norm.bound.BoundEnumField;
import dev.w0fv1.norm.bound.BoundEnumId;
import dev.w0fv1.norm.bound.BoundEnumVariant;
import dev.w0fv1.norm.bound.BoundEnumVariantId;
import dev.w0fv1.norm.bound.BoundExpression;
import dev.w0fv1.norm.bound.BoundField;
import dev.w0fv1.norm.bound.BoundFieldId;
import dev.w0fv1.norm.bound.BoundInterface;
import dev.w0fv1.norm.bound.BoundInterfaceId;
import dev.w0fv1.norm.bound.BoundInterfaceMethod;
import dev.w0fv1.norm.bound.BoundInterfaceMethodId;
import dev.w0fv1.norm.bound.BoundIntrinsic;
import dev.w0fv1.norm.bound.BoundIteration;
import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.bound.BoundParameter;
import dev.w0fv1.norm.bound.BoundPattern;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.bound.BoundReifiedArgument;
import dev.w0fv1.norm.bound.BoundRuntimeType;
import dev.w0fv1.norm.bound.BoundSource;
import dev.w0fv1.norm.bound.BoundStatement;
import dev.w0fv1.norm.bound.BoundSwitchCase;
import dev.w0fv1.norm.bound.BoundTypeParameter;
import dev.w0fv1.norm.bound.BoundUnaryOperator;
import dev.w0fv1.norm.bound.BoundWitness;
import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.semantic.ResolvedCall;
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
  private final Map<String, BoundInterface> interfaces = new LinkedHashMap<>();
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
                        program.interfaces().stream().map(this::interfaceId).toList(),
                        program.classes().stream().map(this::classId).toList(),
                        sourceCallables(program)))
            .toList();
    return new BoundProgram(
        sources,
        List.copyOf(enums.values()),
        List.copyOf(interfaces.values()),
        bindBuiltinConformances(),
        List.copyOf(classes.values()),
        List.copyOf(callables.values()),
        Optional.ofNullable(entryPoint).map(this::callableId));
  }

  private void bindTypes() {
    for (Syntax.Program program : programs) {
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        Symbol symbol = symbol(declaration.nameSpan());
        BoundInterface value =
            new BoundInterface(
                BoundInterfaceId.of(symbol.id()),
                declaration.name(),
                visibility(declaration.visibility()),
                symbol.type(),
                bindTypeParameters(declaration.typeParameters()),
                declaration.extendedInterfaces().stream()
                    .map(type -> semantics.typeOf(type).orElseThrow())
                    .toList(),
                declaration.methods().stream().map(this::bindInterfaceMethod).toList(),
                declaration.span());
        interfaces.put(value.id().value(), value);
      }
      for (Syntax.EnumDecl declaration : program.enums()) {
        Symbol symbol = symbol(declaration.nameSpan());
        BoundEnum value =
            new BoundEnum(
                BoundEnumId.of(symbol.id()),
                declaration.name(),
                declaration.visibility() == Syntax.Visibility.PUBLIC
                    ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
                    : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
                symbol.type(),
                bindTypeParameters(declaration.typeParameters()),
                bindEnumVariants(declaration),
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
                  BoundFieldId.of(fieldSymbol.id()),
                  field.name(),
                  field.visibility() == Syntax.Visibility.PUBLIC
                      ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
                      : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
                  fieldSymbol.type(),
                  ordinal);
          fields.put(bound.id().value(), bound);
          boundFields.add(bound);
        }
        BoundClass value =
            new BoundClass(
                BoundClassId.of(symbol.id()),
                declaration.name(),
                declaration.visibility() == Syntax.Visibility.PUBLIC
                    ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
                    : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
                symbol.type(),
                bindTypeParameters(declaration.typeParameters()),
                boundFields,
                declaration.methods().stream().map(this::callableId).toList(),
                bindConformances(declaration),
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
            bindCallableTypeParameters(declaration, owner),
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
      case Syntax.ConditionalForStatement loop ->
          new BoundStatement.ConditionalForStatement(
              bindExpression(loop.condition()), bindBlock(loop.body(), loop.span()), loop.span());
      case Syntax.ForStatement loop -> {
        Symbol variable = symbol(loop.variableNameSpan());
        yield new BoundStatement.ForStatement(
            new BoundLocalId(variable.id().value() + "/iterator/" + syntheticId++),
            BoundLocalId.of(variable.id()),
            loop.variableName(),
            variable.type(),
            loop.index().map(index -> BoundLocalId.of(symbol(index.nameSpan()).id())),
            bindExpression(loop.iterable()),
            bindBlock(loop.body(), loop.span()),
            bindIteration(semantics.iterationOf(loop.iterable().span()).orElseThrow()),
            loop.span());
      }
      case Syntax.ReturnStatement returned ->
          new BoundStatement.ReturnStatement(
              Optional.ofNullable(returned.value()).map(this::bindExpression), returned.span());
      case Syntax.BreakStatement broken ->
          broken.value() == null
              ? new BoundStatement.BreakStatement(broken.span())
              : new BoundStatement.YieldStatement(bindExpression(broken.value()), broken.span());
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
              thisRead(name.span()), field.id(), field.ordinal(), value, assignment.span());
        }
        yield new BoundStatement.LocalAssignment(
            BoundLocalId.of(target.id()), value, assignment.span());
      }
      case Syntax.Member member -> {
        Symbol target = symbol(member.nameSpan());
        if (isBuiltin(target)) {
          yield new BoundStatement.IntrinsicAssignment(
              builtins.writeIntrinsic(target.id()).orElseThrow(),
              bindExpression(member.receiver()),
              Optional.empty(),
              value,
              assignment.span());
        }
        BoundField field = field(target);
        yield new BoundStatement.FieldAssignment(
            bindExpression(member.receiver()),
            field.id(),
            field.ordinal(),
            value,
            assignment.span());
      }
      case Syntax.Index index -> {
        var resolved = semantics.indexOf(index.span()).orElseThrow();
        yield new BoundStatement.IntrinsicAssignment(
            resolved.writeIntrinsic().orElseThrow(),
            bindExpression(index.receiver()),
            Optional.of(bindExpression(index.index())),
            value,
            assignment.span());
      }
      default -> throw new IllegalStateException("invalid checked assignment target");
    };
  }

  private BoundExpression bindExpression(Syntax.Expression expression) {
    SemanticType type = semantics.typeOf(expression.span()).orElseThrow();
    return switch (expression) {
      case Syntax.IntegerLiteral integer ->
          new BoundExpression.Literal(
              dev.w0fv1.norm.semantic.NumericTypes.materialize(integer.value(), type),
              type,
              integer.span());
      case Syntax.DecimalLiteral decimal ->
          new BoundExpression.Literal(
              dev.w0fv1.norm.semantic.NumericTypes.materialize(decimal.value(), type),
              type,
              decimal.span());
      case Syntax.CodePointLiteral codePoint ->
          new BoundExpression.Literal(codePoint.value(), type, codePoint.span());
      case Syntax.BooleanLiteral bool ->
          new BoundExpression.Literal(bool.value(), type, bool.span());
      case Syntax.NullLiteral literal -> new BoundExpression.NullLiteral(type, literal.span());
      case Syntax.StringLiteralExpr string ->
          new BoundExpression.Literal(string.value(), type, string.span());
      case Syntax.ArrayLiteral array ->
          new BoundExpression.CollectionLiteral(
              array.elements().stream().map(this::bindExpression).toList(),
              builtins.collectionLiteral(type).orElseThrow(),
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
      case Syntax.SwitchExpression switched ->
          new BoundExpression.Switch(
              bindExpression(switched.value()),
              switched.cases().stream().map(this::bindSwitchCase).toList(),
              type,
              switched.span());
    };
  }

  private BoundExpression bindName(Syntax.Name name, SemanticType type) {
    Symbol symbol = symbol(name.span());
    if (symbol.kind() == SymbolKind.SELF) return thisRead(name.span());
    if (symbol.kind() == SymbolKind.FIELD) {
      BoundField field = field(symbol);
      return new BoundExpression.FieldRead(
          thisRead(name.span()), field.id(), field.ordinal(), type, name.span());
    }
    return new BoundExpression.LocalRead(BoundLocalId.of(symbol.id()), type, name.span());
  }

  private BoundSwitchCase bindSwitchCase(Syntax.SwitchCase switchCase) {
    return new BoundSwitchCase(
        bindPattern(switchCase.pattern()),
        bindBlock(switchCase.body(), switchCase.span()),
        switchCase.span());
  }

  private BoundPattern bindPattern(Syntax.Pattern pattern) {
    return switch (pattern) {
      case Syntax.VariantPattern variant -> {
        Symbol symbol = symbol(variant.nameSpan());
        Symbol owner = semantics.symbol(symbol.owner().orElseThrow()).orElseThrow();
        yield new BoundPattern.Variant(
            BoundEnumId.of(owner.id()),
            variant.name(),
            variant.arguments().stream().map(this::bindPattern).toList(),
            variant.span());
      }
      case Syntax.BindingPattern binding -> {
        Symbol symbol = symbol(binding.nameSpan());
        yield new BoundPattern.Binding(BoundLocalId.of(symbol.id()), symbol.type(), binding.span());
      }
      case Syntax.WildcardPattern wildcard -> new BoundPattern.Wildcard(wildcard.span());
      case Syntax.IntegerPattern integer ->
          new BoundPattern.Literal(
              dev.w0fv1.norm.semantic.NumericTypes.materialize(
                  integer.value(), semantics.typeOf(integer.span()).orElseThrow()),
              semantics.typeOf(integer.span()).orElseThrow(),
              integer.span());
      case Syntax.DecimalPattern decimal ->
          new BoundPattern.Literal(
              dev.w0fv1.norm.semantic.NumericTypes.materialize(
                  decimal.value(), semantics.typeOf(decimal.span()).orElseThrow()),
              semantics.typeOf(decimal.span()).orElseThrow(),
              decimal.span());
      case Syntax.CodePointPattern codePoint ->
          new BoundPattern.Literal(codePoint.value(), SemanticType.CODE_POINT, codePoint.span());
      case Syntax.BooleanPattern bool ->
          new BoundPattern.Literal(bool.value(), SemanticType.BOOLEAN, bool.span());
      case Syntax.StringPattern string ->
          new BoundPattern.Literal(string.value(), SemanticType.STRING, string.span());
      case Syntax.NullPattern nil ->
          new BoundPattern.Null(semantics.typeOf(nil.span()).orElse(SemanticType.NULL), nil.span());
    };
  }

  private BoundExpression bindCall(Syntax.Call call, SemanticType type) {
    ResolvedCall resolution = semantics.callOf(call.span()).orElseThrow();
    if (!resolution.resultType().equals(type)) {
      throw new IllegalStateException("resolved call result differs from expression type");
    }
    Symbol target = semantics.symbol(resolution.target()).orElseThrow();
    List<BoundArgument> arguments = bindArguments(call, resolution);
    Syntax.Member member = call.callee() instanceof Syntax.Member value ? value : null;
    BoundExpression receiver =
        member == null
                || target.kind() == SymbolKind.TYPE_METHOD
                || target.kind() == SymbolKind.ENUM_VARIANT
            ? null
            : bindExpression(member.receiver());
    boolean nullSafe = member != null && member.nullSafe();
    return switch (resolution.kind()) {
      case INTRINSIC ->
          new BoundIntrinsic(
              builtins.intrinsic(target.id()).orElseThrow(),
              Optional.ofNullable(receiver),
              arguments,
              target.kind() == SymbolKind.TYPE_METHOD
                      || builtins
                          .type(target.id())
                          .flatMap(BuiltinCatalog.TypeDefinition::constructor)
                          .isPresent()
                  ? Optional.of(runtimeType(type))
                  : Optional.empty(),
              nullSafe,
              type,
              call.span());
      case CONSTRUCT ->
          new BoundConstruct(
              new BoundClassId(target.id().value()),
              runtimeType(type),
              arguments,
              type,
              call.span());
      case ENUM_CONSTRUCT -> {
        BoundEnumVariant variant =
            enums.values().stream()
                .flatMap(value -> value.variants().stream())
                .filter(value -> value.id().value().equals(target.id().value()))
                .findFirst()
                .orElseThrow();
        BoundEnum owner =
            enums.values().stream()
                .filter(value -> value.variants().contains(variant))
                .findFirst()
                .orElseThrow();
        yield new BoundExpression.EnumConstruct(
            owner.id(),
            variant.id(),
            owner.name(),
            variant.name(),
            arguments,
            runtimeType(type),
            type,
            call.span());
      }
      case COPY ->
          new BoundExpression.CopyObject(
              java.util.Objects.requireNonNull(receiver), nullSafe, type, call.span());
      case CALLABLE ->
          new BoundCall(
              BoundCallableId.of(target.id()),
              Optional.ofNullable(receiver),
              arguments,
              resolution.callableTypeArguments().stream().map(this::runtimeType).toList(),
              nullSafe,
              type,
              call.span());
      case INTERFACE_CALL ->
          new BoundExpression.InterfaceCall(
              BoundInterfaceMethodId.of(target.id()),
              receiverInterfaceType(java.util.Objects.requireNonNull(receiver).type()),
              receiver,
              arguments,
              resolution.callableTypeArguments().stream().map(this::runtimeType).toList(),
              nullSafe,
              type,
              call.span());
    };
  }

  private SemanticType receiverInterfaceType(SemanticType receiver) {
    if (receiver.kind() != SemanticType.Kind.TYPE_PARAMETER) return receiver.nonNullable();
    for (Syntax.Program program : programs) {
      for (Syntax.TypeParameter parameter : allTypeParameters(program)) {
        Symbol symbol = symbol(parameter.nameSpan());
        if (symbol.type().identity().equals(receiver.identity())
            && parameter.upperBound().isPresent()) {
          return semantics.typeOf(parameter.upperBound().orElseThrow()).orElseThrow();
        }
      }
    }
    throw new IllegalStateException("interface call receiver has no interface bound");
  }

  private static List<Syntax.TypeParameter> allTypeParameters(Syntax.Program program) {
    List<Syntax.TypeParameter> result = new ArrayList<>();
    program.enums().forEach(value -> result.addAll(value.typeParameters()));
    program
        .interfaces()
        .forEach(
            value -> {
              result.addAll(value.typeParameters());
              value.methods().forEach(method -> result.addAll(method.typeParameters()));
            });
    program
        .classes()
        .forEach(
            value -> {
              result.addAll(value.typeParameters());
              value.methods().forEach(method -> result.addAll(method.typeParameters()));
            });
    program.functions().forEach(value -> result.addAll(value.typeParameters()));
    return List.copyOf(result);
  }

  private BoundExpression bindMember(Syntax.Member member, SemanticType type) {
    Symbol target = symbol(member.nameSpan());
    if (target.kind() == SymbolKind.ENUM_VARIANT) {
      Symbol owner = semantics.symbol(target.owner().orElseThrow()).orElseThrow();
      return new BoundExpression.EnumConstruct(
          BoundEnumId.of(owner.id()),
          BoundEnumVariantId.of(target.id()),
          owner.name(),
          target.name(),
          List.of(),
          runtimeType(type),
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
          member.nullSafe(),
          type,
          member.span());
    }
    BoundField field = field(target);
    return new BoundExpression.FieldRead(
        receiver, field.id(), field.ordinal(), member.nullSafe(), type, member.span());
  }

  private List<BoundArgument> bindArguments(Syntax.Call call, ResolvedCall resolution) {
    List<Integer> indices = resolution.arguments().parameterIndices();
    List<BoundArgument> result = new ArrayList<>();
    for (int index = 0; index < call.arguments().size(); index++) {
      result.add(
          new BoundArgument(
              bindExpression(call.arguments().get(index).value()), indices.get(index)));
    }
    return result;
  }

  private BoundRuntimeType runtimeType(SemanticType type) {
    List<BoundReifiedArgument> captures = new ArrayList<>();
    collectCaptures(type, captures);
    return new BoundRuntimeType(type, captures);
  }

  private static BoundIteration bindIteration(dev.w0fv1.norm.semantic.ResolvedIteration iteration) {
    return switch (iteration.strategy()) {
      case dev.w0fv1.norm.semantic.ResolvedIteration.Strategy.Builtin builtin ->
          new BoundIteration.Builtin(builtin.intrinsic());
      case dev.w0fv1.norm.semantic.ResolvedIteration.Strategy.Interface protocol ->
          new BoundIteration.Interface(
              protocol.iterableInterfaceType(),
              BoundInterfaceMethodId.of(protocol.iteratorRequirement()),
              protocol.iteratorInterfaceType(),
              BoundInterfaceMethodId.of(protocol.hasNextRequirement()),
              BoundInterfaceMethodId.of(protocol.nextRequirement()));
    };
  }

  private List<BoundTypeParameter> bindCallableTypeParameters(
      Syntax.FunctionDecl declaration, Syntax.ClassDecl owner) {
    List<BoundTypeParameter> result = new ArrayList<>();
    if (owner != null) result.addAll(bindTypeParameters(owner.typeParameters()));
    result.addAll(bindTypeParameters(declaration.typeParameters()));
    return List.copyOf(result);
  }

  private List<BoundTypeParameter> bindTypeParameters(List<Syntax.TypeParameter> parameters) {
    return parameters.stream()
        .map(
            parameter ->
                new BoundTypeParameter(
                    symbol(parameter.nameSpan()).type(),
                    parameter.upperBound().map(type -> semantics.typeOf(type).orElseThrow())))
        .toList();
  }

  private BoundInterfaceMethod bindInterfaceMethod(Syntax.InterfaceMethodDecl declaration) {
    Symbol method = symbol(declaration.nameSpan());
    return new BoundInterfaceMethod(
        BoundInterfaceMethodId.of(method.id()),
        declaration.name(),
        bindTypeParameters(declaration.typeParameters()),
        java.util.stream.IntStream.range(0, method.parameters().size())
            .mapToObj(
                index -> {
                  var parameter = method.parameters().get(index);
                  return new BoundParameter(
                      new BoundLocalId(method.id().value() + "/parameter/" + index),
                      parameter.name(),
                      parameter.type(),
                      index);
                })
            .toList(),
        method.type(),
        declaration.span());
  }

  private List<BoundConformance> bindConformances(Syntax.ClassDecl declaration) {
    return declaration.implementedInterfaces().stream()
        .map(
            type -> {
              SemanticType interfaceType = semantics.typeOf(type).orElseThrow();
              List<BoundWitness> witnesses = new ArrayList<>();
              collectWitnesses(declaration, interfaceType, witnesses, new java.util.HashSet<>());
              return new BoundConformance(interfaceType, witnesses);
            })
        .toList();
  }

  private List<BoundBuiltinConformance> bindBuiltinConformances() {
    List<BoundBuiltinConformance> result = new ArrayList<>();
    for (BuiltinCatalog.ProtocolConformance conformance : builtins.protocolConformances()) {
      Syntax.InterfaceDecl contract = interfaceDeclaration(conformance.interfaceType());
      if (contract == null) continue;
      List<BoundWitness> witnesses = new ArrayList<>();
      for (var entry : conformance.witnesses().entrySet()) {
        Syntax.InterfaceMethodDecl requirement =
            contract.methods().stream()
                .filter(method -> method.name().equals(entry.getKey()))
                .findFirst()
                .orElseThrow();
        witnesses.add(
            new BoundWitness(
                BoundInterfaceMethodId.of(symbol(requirement.nameSpan()).id()),
                new BoundWitness.Target.Intrinsic(entry.getValue().intrinsic())));
      }
      result.add(
          new BoundBuiltinConformance(
              conformance.typeParameters().stream()
                  .map(type -> new BoundTypeParameter(type, Optional.empty()))
                  .toList(),
              conformance.concreteType(),
              conformance.interfaceType(),
              witnesses,
              contract.span()));
    }
    return List.copyOf(result);
  }

  private void collectWitnesses(
      Syntax.ClassDecl declaration,
      SemanticType interfaceType,
      List<BoundWitness> witnesses,
      java.util.Set<String> visited) {
    if (!visited.add(interfaceType.identity())) return;
    Syntax.InterfaceDecl contract = interfaceDeclaration(interfaceType);
    if (contract == null) return;
    for (Syntax.InterfaceMethodDecl requirement : contract.methods()) {
      Symbol classSymbol = symbol(declaration.nameSpan());
      Symbol requirementSymbol = symbol(requirement.nameSpan());
      var implementationId =
          semantics
              .witness(classSymbol.id(), requirementSymbol.id())
              .orElseThrow(
                  () -> new IllegalStateException("validated interface witness is absent"));
      BoundWitness witness =
          new BoundWitness(
              BoundInterfaceMethodId.of(requirementSymbol.id()),
              new BoundWitness.Target.Callable(BoundCallableId.of(implementationId)));
      if (witnesses.stream()
          .noneMatch(existing -> existing.requirement().equals(witness.requirement()))) {
        witnesses.add(witness);
      }
    }
    for (Syntax.TypeRef parent : contract.extendedInterfaces()) {
      SemanticType parentType = semantics.typeOf(parent).orElseThrow();
      collectWitnesses(declaration, parentType, witnesses, visited);
    }
  }

  private Syntax.InterfaceDecl interfaceDeclaration(SemanticType type) {
    for (Syntax.Program program : programs) {
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        Symbol symbol = symbol(declaration.nameSpan());
        if (symbol.type().identity().equals(type.identity())) return declaration;
      }
    }
    return null;
  }

  private static dev.w0fv1.norm.bound.BoundVisibility visibility(Syntax.Visibility visibility) {
    return visibility == Syntax.Visibility.PUBLIC
        ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
        : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE;
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
      case QUESTION_QUESTION -> BoundBinaryOperator.COALESCE;
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

  private BoundInterfaceId interfaceId(Syntax.InterfaceDecl declaration) {
    return BoundInterfaceId.of(symbol(declaration.nameSpan()).id());
  }

  private BoundEnumId enumId(Syntax.EnumDecl declaration) {
    return BoundEnumId.of(symbol(declaration.nameSpan()).id());
  }

  private List<BoundEnumVariant> bindEnumVariants(Syntax.EnumDecl declaration) {
    List<BoundEnumVariant> result = new ArrayList<>();
    for (Syntax.EnumVariant variant : declaration.variants()) {
      result.add(
          new BoundEnumVariant(
              BoundEnumVariantId.of(symbol(variant.nameSpan()).id()),
              variant.name(),
              java.util.stream.IntStream.range(0, variant.parameters().size())
                  .mapToObj(
                      index -> {
                        Syntax.Parameter parameter = variant.parameters().get(index);
                        return new BoundEnumField(
                            parameter.name(),
                            semantics.typeOf(parameter.type()).orElseThrow(),
                            index);
                      })
                  .toList()));
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
