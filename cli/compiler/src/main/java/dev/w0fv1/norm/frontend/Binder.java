package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.bound.BoundAggregate;
import dev.w0fv1.norm.bound.BoundAggregateId;
import dev.w0fv1.norm.bound.BoundAnnotationApplication;
import dev.w0fv1.norm.bound.BoundAnnotationReference;
import dev.w0fv1.norm.bound.BoundAnnotationTarget;
import dev.w0fv1.norm.bound.BoundAnnotationValue;
import dev.w0fv1.norm.bound.BoundArgument;
import dev.w0fv1.norm.bound.BoundBinaryOperator;
import dev.w0fv1.norm.bound.BoundBlock;
import dev.w0fv1.norm.bound.BoundBuiltinConformance;
import dev.w0fv1.norm.bound.BoundCall;
import dev.w0fv1.norm.bound.BoundCallable;
import dev.w0fv1.norm.bound.BoundCallableId;
import dev.w0fv1.norm.bound.BoundCatchClause;
import dev.w0fv1.norm.bound.BoundClosure;
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
import dev.w0fv1.norm.bound.BoundInterceptor;
import dev.w0fv1.norm.bound.BoundInterface;
import dev.w0fv1.norm.bound.BoundInterfaceId;
import dev.w0fv1.norm.bound.BoundInterfaceMethod;
import dev.w0fv1.norm.bound.BoundInterfaceMethodId;
import dev.w0fv1.norm.bound.BoundIntrinsic;
import dev.w0fv1.norm.bound.BoundInvoke;
import dev.w0fv1.norm.bound.BoundIteration;
import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.bound.BoundMethodDispatch;
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
import dev.w0fv1.norm.semantic.AnnotationApplication;
import dev.w0fv1.norm.semantic.AnnotationDeclarationReference;
import dev.w0fv1.norm.semantic.AnnotationValue;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.AnnotationTarget;
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
  private final Map<String, BoundAggregate> aggregates = new LinkedHashMap<>();
  private final Map<String, BoundEnum> enums = new LinkedHashMap<>();
  private final Map<String, BoundInterface> interfaces = new LinkedHashMap<>();
  private final Map<String, BoundCallable> callables = new LinkedHashMap<>();
  private final Map<String, BoundField> fields = new LinkedHashMap<>();
  private Map<String, BoundLocalId> reifiedLocals = Map.of();
  private List<BoundTypeParameter> activeTypeParameters = List.of();
  private BoundLocalId thisLocal;
  private SemanticType thisType;
  private Map<BoundLocalId, SemanticType> lambdaCaptures;
  private java.util.Set<BoundLocalId> lambdaLocals = java.util.Set.of();
  private BoundCallableId currentCallableId;
  private boolean implicitSelfReturn;
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
                        program.aggregates().stream().map(this::aggregateId).toList(),
                        sourceCallables(program)))
            .toList();
    return new BoundProgram(
        sources,
        List.copyOf(enums.values()),
        List.copyOf(interfaces.values()),
        bindBuiltinConformances(),
        List.copyOf(aggregates.values()),
        List.copyOf(callables.values()),
        bindAnnotationApplications(),
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
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        Symbol symbol = symbol(declaration.nameSpan());
        int fieldOffset = inheritedFieldCount(declaration);
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
                  fieldOffset + ordinal,
                  interceptors(AnnotationTarget.FIELD, fieldSymbol.id()));
          fields.put(bound.id().value(), bound);
          boundFields.add(bound);
        }
        BoundAggregate value =
            new BoundAggregate(
                BoundAggregateId.of(symbol.id()),
                switch (declaration.kind()) {
                  case CLASS -> dev.w0fv1.norm.bound.BoundAggregateKind.CLASS;
                  case VALUE -> dev.w0fv1.norm.bound.BoundAggregateKind.VALUE;
                  case ANNOTATION -> dev.w0fv1.norm.bound.BoundAggregateKind.ANNOTATION;
                },
                declaration.name(),
                declaration.visibility() == Syntax.Visibility.PUBLIC
                    ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
                    : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
                symbol.type(),
                bindTypeParameters(declaration.typeParameters()),
                semantics.aggregateParent(aggregateSelfType(declaration)),
                fieldOffset + boundFields.size(),
                boundFields,
                declaration.methods().stream().map(this::callableId).toList(),
                bindDispatch(declaration),
                constructorIds(declaration),
                bindConformances(declaration),
                declaration.span());
        aggregates.put(value.id().value(), value);
      }
    }
  }

  private void bindCallables() {
    for (Syntax.Program program : programs) {
      for (Syntax.FunctionDecl function : program.functions()) bindCallable(function, null);
      for (Syntax.AggregateDecl owner : program.aggregates()) {
        if (owner.constructors().isEmpty()) {
          bindConstructor(owner, Optional.empty());
        } else {
          for (Syntax.ConstructorDecl constructor : owner.constructors()) {
            bindConstructor(owner, Optional.of(constructor));
          }
        }
        for (Syntax.FunctionDecl method : owner.methods()) bindCallable(method, owner);
      }
      for (Syntax.InterfaceDecl owner : program.interfaces()) {
        for (Syntax.InterfaceMethodDecl method : owner.methods()) {
          if (method.body().isPresent()) bindDefaultMethod(method, owner);
        }
      }
    }
  }

  private List<BoundAnnotationApplication> bindAnnotationApplications() {
    return semantics.annotations().applications().stream()
        .filter(
            application ->
                semantics.annotations().schema(application.annotation()).orElseThrow().retention()
                    != dev.w0fv1.norm.value.AnnotationRetention.SOURCE)
        .map(
            application ->
                new BoundAnnotationApplication(
                    BoundAggregateId.of(application.annotation()),
                    bindAnnotationTarget(application.target()),
                    application.values().stream().map(this::bindAnnotationValue).toList(),
                    application.span()))
        .toList();
  }

  private BoundAnnotationTarget bindAnnotationTarget(
      dev.w0fv1.norm.semantic.AnnotationSite target) {
    if (target instanceof dev.w0fv1.norm.semantic.AnnotationSite.Package site) {
      return new BoundAnnotationTarget.Package(site.document(), site.packageName());
    }
    dev.w0fv1.norm.semantic.AnnotationSite.Symbol site =
        (dev.w0fv1.norm.semantic.AnnotationSite.Symbol) target;
    Symbol symbol = semantics.symbol(site.symbol()).orElseThrow();
    return switch (site.kind()) {
      case TYPE, CONSTRUCTOR, FUNCTION ->
          new BoundAnnotationTarget.Definition(target.kind(), symbol.id().value());
      case FIELD -> new BoundAnnotationTarget.Field(BoundFieldId.of(symbol.id()));
      case PARAMETER -> {
        Symbol owner = semantics.symbol(symbol.owner().orElseThrow()).orElseThrow();
        int ordinal =
            java.util.stream.IntStream.range(0, owner.parameters().size())
                .filter(index -> owner.parameters().get(index).name().equals(symbol.name()))
                .findFirst()
                .orElseThrow();
        yield new BoundAnnotationTarget.Parameter(owner.id().value(), ordinal);
      }
      case LOCAL ->
          new BoundAnnotationTarget.Local(
              BoundCallableId.of(symbol.owner().orElseThrow()), BoundLocalId.of(symbol.id()));
      case PACKAGE -> throw new IllegalStateException("package annotation target is invalid");
    };
  }

  private void bindDefaultMethod(
      Syntax.InterfaceMethodDecl declaration, Syntax.InterfaceDecl owner) {
    Symbol requirement = symbol(declaration.nameSpan());
    BoundCallableId id = new BoundCallableId(requirement.id().value() + "/default");
    BoundCallableId previousCallableId = currentCallableId;
    List<BoundTypeParameter> previousTypeParameters = activeTypeParameters;
    currentCallableId = id;
    thisLocal = new BoundLocalId(id.value() + "/this");
    Symbol ownerSymbol = symbol(owner.nameSpan());
    thisType =
        SemanticType.declared(
            ownerSymbol.type().identity(),
            owner.name(),
            owner.typeParameters().stream()
                .map(parameter -> symbol(parameter.nameSpan()).type())
                .toList(),
            ownerSymbol.type().category());
    Map<String, BoundLocalId> activeReified = new LinkedHashMap<>();
    List<BoundReifiedArgument> reified = new ArrayList<>();
    addReified(owner.typeParameters(), id, activeReified, reified);
    addReified(declaration.typeParameters(), id, activeReified, reified);
    reifiedLocals = Map.copyOf(activeReified);
    List<BoundParameter> parameters = new ArrayList<>();
    for (int ordinal = 0; ordinal < declaration.parameters().size(); ordinal++) {
      Syntax.Parameter parameter = declaration.parameters().get(ordinal);
      parameters.add(bindParameter(parameter, ordinal));
    }
    List<BoundTypeParameter> typeParameters = new ArrayList<>();
    typeParameters.addAll(bindTypeParameters(owner.typeParameters()));
    typeParameters.addAll(bindTypeParameters(declaration.typeParameters()));
    activeTypeParameters = List.copyOf(typeParameters);
    callables.put(
        id.value(),
        new BoundCallable(
            id,
            dev.w0fv1.norm.bound.BoundCallableKind.METHOD,
            declaration.name(),
            dev.w0fv1.norm.bound.BoundVisibility.PUBLIC,
            Optional.empty(),
            Optional.of(thisType),
            Optional.of(thisLocal),
            List.of(),
            parameters,
            typeParameters,
            reified,
            List.of(),
            requirement.type(),
            bindBlock(declaration.body().orElseThrow(), declaration.span()),
            declaration.span()));
    reifiedLocals = Map.of();
    thisLocal = null;
    thisType = null;
    activeTypeParameters = previousTypeParameters;
    currentCallableId = previousCallableId;
  }

  private void bindCallable(Syntax.FunctionDecl declaration, Syntax.AggregateDecl owner) {
    Symbol callable = symbol(declaration.nameSpan());
    BoundCallableId id = BoundCallableId.of(callable.id());
    BoundCallableId previousCallableId = currentCallableId;
    List<BoundTypeParameter> previousTypeParameters = activeTypeParameters;
    currentCallableId = id;
    BoundAggregateId ownerId = owner == null ? null : aggregateId(owner);
    boolean previousImplicitSelfReturn = implicitSelfReturn;
    implicitSelfReturn = owner != null && declaration.returnType().isEmpty();
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
    activeTypeParameters = bindCallableTypeParameters(declaration, owner);
    List<BoundParameter> parameters = new ArrayList<>();
    for (int ordinal = 0; ordinal < declaration.parameters().size(); ordinal++) {
      Syntax.Parameter parameter = declaration.parameters().get(ordinal);
      parameters.add(bindParameter(parameter, ordinal));
    }
    BoundBlock body = bindBlock(declaration.body(), declaration.span());
    if (implicitSelfReturn) {
      List<BoundStatement> statements = new ArrayList<>(body.statements());
      statements.add(
          new BoundStatement.ReturnStatement(
              Optional.of(thisRead(declaration.span())), declaration.span()));
      body = new BoundBlock(statements, body.span());
    }
    BoundCallable bound =
        new BoundCallable(
            id,
            owner == null
                ? declaration.kind() == Syntax.FunctionKind.EXTENSION
                    ? dev.w0fv1.norm.bound.BoundCallableKind.EXTENSION
                    : dev.w0fv1.norm.bound.BoundCallableKind.FUNCTION
                : dev.w0fv1.norm.bound.BoundCallableKind.METHOD,
            declaration.name(),
            declaration.visibility() == Syntax.Visibility.PUBLIC
                ? dev.w0fv1.norm.bound.BoundVisibility.PUBLIC
                : dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
            Optional.ofNullable(ownerId),
            Optional.ofNullable(thisType),
            Optional.ofNullable(thisLocal),
            List.of(),
            parameters,
            activeTypeParameters,
            reified,
            interceptors(AnnotationTarget.FUNCTION, callable.id()),
            callable.type(),
            body,
            declaration.span());
    callables.put(id.value(), bound);
    reifiedLocals = Map.of();
    thisLocal = null;
    thisType = null;
    activeTypeParameters = previousTypeParameters;
    currentCallableId = previousCallableId;
    implicitSelfReturn = previousImplicitSelfReturn;
  }

  private void bindConstructor(
      Syntax.AggregateDecl owner, Optional<Syntax.ConstructorDecl> declaration) {
    BoundCallableId id =
        declaration.map(this::constructorId).orElseGet(() -> syntheticConstructorId(owner));
    BoundCallableId previousCallableId = currentCallableId;
    List<BoundTypeParameter> previousTypeParameters = activeTypeParameters;
    currentCallableId = id;
    thisLocal = new BoundLocalId(id.value() + "/this");
    thisType = aggregateSelfType(owner);
    Map<String, BoundLocalId> activeReified = new LinkedHashMap<>();
    List<BoundReifiedArgument> reified = new ArrayList<>();
    addReified(owner.typeParameters(), id, activeReified, reified);
    reifiedLocals = Map.copyOf(activeReified);
    activeTypeParameters = bindTypeParameters(owner.typeParameters());
    List<BoundParameter> parameters = new ArrayList<>();
    List<BoundStatement> statements = new ArrayList<>();
    if (declaration.isEmpty()) {
      for (int ordinal = 0; ordinal < owner.fields().size(); ordinal++) {
        Syntax.FieldDecl field = owner.fields().get(ordinal);
        BoundField boundField = field(symbol(field.nameSpan()));
        BoundLocalId local = new BoundLocalId(id.value() + "/parameter/" + ordinal);
        parameters.add(new BoundParameter(local, field.name(), boundField.type(), ordinal));
        statements.add(
            new BoundStatement.FieldAssignment(
                thisRead(field.span()),
                boundField.id(),
                boundField.ordinal(),
                new BoundExpression.LocalRead(local, boundField.type(), field.span()),
                field.span()));
      }
    } else {
      Syntax.ConstructorDecl constructor = declaration.orElseThrow();
      for (int ordinal = 0; ordinal < constructor.parameters().size(); ordinal++) {
        Syntax.Parameter parameter = constructor.parameters().get(ordinal);
        parameters.add(bindParameter(parameter, ordinal));
      }
      constructor
          .superCall()
          .ifPresent(
              superCall -> {
                ResolvedCall resolution = semantics.callOf(superCall.span()).orElseThrow();
                Syntax.Call call =
                    new Syntax.Call(
                        new Syntax.Name("super", superCall.span()),
                        superCall.arguments(),
                        superCall.span());
                Symbol target = semantics.symbol(resolution.target()).orElseThrow();
                BoundCallableId initializer =
                    target.kind() == SymbolKind.CONSTRUCTOR
                        ? BoundCallableId.of(target.id())
                        : new BoundCallableId(target.id().value() + "/constructor");
                SemanticType parent = semantics.aggregateParent(thisType).orElseThrow();
                statements.add(
                    new BoundStatement.ExpressionStatement(
                        new BoundCall(
                            initializer,
                            Optional.of(thisRead(superCall.span())),
                            bindArguments(call, resolution),
                            List.of(),
                            parent.arguments().stream().map(this::runtimeType).toList(),
                            false,
                            false,
                            SemanticType.VOID,
                            superCall.span()),
                        superCall.span()));
              });
      statements.addAll(bindBlock(constructor.body(), constructor.span()).statements());
    }
    SourceSpan span = declaration.map(Syntax.ConstructorDecl::span).orElse(owner.span());
    callables.put(
        id.value(),
        new BoundCallable(
            id,
            dev.w0fv1.norm.bound.BoundCallableKind.CONSTRUCTOR,
            owner.name(),
            dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
            Optional.of(aggregateId(owner)),
            Optional.of(thisType),
            Optional.of(thisLocal),
            List.of(),
            parameters,
            activeTypeParameters,
            reified,
            List.of(),
            SemanticType.VOID,
            new BoundBlock(statements, span),
            span));
    reifiedLocals = Map.of();
    thisLocal = null;
    thisType = null;
    activeTypeParameters = previousTypeParameters;
    currentCallableId = previousCallableId;
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

  private BoundParameter bindParameter(Syntax.Parameter parameter, int ordinal) {
    Symbol symbol = symbol(parameter.nameSpan());
    return new BoundParameter(
        BoundLocalId.of(symbol.id()),
        parameter.name(),
        symbol.type(),
        ordinal,
        interceptors(AnnotationTarget.PARAMETER, symbol.id()));
  }

  private List<BoundInterceptor> interceptors(
      AnnotationTarget target, dev.w0fv1.norm.semantic.SymbolId symbol) {
    return semantics.annotations().applications().stream()
        .filter(
            application ->
                application.target() instanceof dev.w0fv1.norm.semantic.AnnotationSite.Symbol site
                    && site.kind() == target
                    && site.symbol().equals(symbol)
                    && semantics
                        .annotations()
                        .schema(application.annotation())
                        .orElseThrow()
                        .intercepts(target))
        .map(this::bindInterceptor)
        .toList();
  }

  private BoundInterceptor bindInterceptor(AnnotationApplication application) {
    return new BoundInterceptor(
        BoundAggregateId.of(application.annotation()),
        application.values().stream().map(this::bindAnnotationValue).toList());
  }

  private BoundAnnotationValue bindAnnotationValue(AnnotationValue value) {
    return new BoundAnnotationValue(value.type(), bindAnnotationContent(value.value()));
  }

  private BoundAnnotationValue.Content bindAnnotationContent(AnnotationValue.Content value) {
    return switch (value) {
      case AnnotationValue.Literal literal -> new BoundAnnotationValue.Literal(literal.value());
      case AnnotationValue.Null ignored -> BoundAnnotationValue.Null.INSTANCE;
      case AnnotationValue.ListValue list ->
          new BoundAnnotationValue.ListValue(
              list.values().stream().map(this::bindAnnotationValue).toList());
      case AnnotationDeclarationReference reference -> bindAnnotationReference(reference);
    };
  }

  private BoundAnnotationReference bindAnnotationReference(
      AnnotationDeclarationReference reference) {
    Symbol target = semantics.symbol(reference.target()).orElseThrow();
    return switch (reference.kind()) {
      case CLASS ->
          new BoundAnnotationReference.ClassReference(
              reference.actualType().arguments().getFirst());
      case CALLABLE -> {
        SemanticType receiver =
            target.kind() == SymbolKind.METHOD
                ? reference.actualType().functionParameterTypes().getFirst()
                : null;
        yield new BoundAnnotationReference.CallableReference(
            BoundCallableId.of(target.id()),
            receiver == null ? List.of() : methodReceiverType(receiver, target),
            semantics.functionReferenceTypeArguments(reference.span()),
            isVirtualMethod(target));
      }
      case FIELD -> {
        BoundField field = field(target);
        yield new BoundAnnotationReference.FieldReference(
            field.id(),
            field.ordinal(),
            reference.actualType().arguments().get(0),
            reference.actualType().arguments().get(1));
      }
    };
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
        BoundExpression initializer = bindExpression(variable.initializer());
        if (lambdaCaptures != null) lambdaLocals.add(BoundLocalId.of(symbol.id()));
        yield new BoundStatement.LocalDeclaration(
            BoundLocalId.of(symbol.id()),
            variable.name(),
            symbol.type(),
            initializer,
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
        BoundExpression iterable = bindExpression(loop.iterable());
        if (lambdaCaptures != null) {
          lambdaLocals.add(BoundLocalId.of(variable.id()));
          loop.index()
              .ifPresent(index -> lambdaLocals.add(BoundLocalId.of(symbol(index.nameSpan()).id())));
        }
        yield new BoundStatement.ForStatement(
            new BoundLocalId(variable.id().value() + "/iterator/" + syntheticId++),
            BoundLocalId.of(variable.id()),
            loop.variableName(),
            variable.type(),
            loop.index().map(index -> BoundLocalId.of(symbol(index.nameSpan()).id())),
            iterable,
            bindBlock(loop.body(), loop.span()),
            bindIteration(semantics.iterationOf(loop.iterable().span()).orElseThrow()),
            loop.span());
      }
      case Syntax.TryStatement tried ->
          new BoundStatement.TryStatement(
              bindBlock(tried.body(), tried.span()),
              tried.catches().stream().map(this::bindCatchClause).toList(),
              tried.finallyClause().map(clause -> bindBlock(clause.body(), clause.span())),
              tried.span());
      case Syntax.ThrowStatement thrown ->
          new BoundStatement.ThrowStatement(bindExpression(thrown.exception()), thrown.span());
      case Syntax.ReturnStatement returned ->
          new BoundStatement.ReturnStatement(
              implicitSelfReturn && returned.value() == null
                  ? Optional.of(thisRead(returned.span()))
                  : Optional.ofNullable(returned.value()).map(this::bindExpression),
              returned.span());
      case Syntax.BreakStatement broken ->
          broken.value() == null
              ? new BoundStatement.BreakStatement(broken.span())
              : new BoundStatement.YieldStatement(bindExpression(broken.value()), broken.span());
      case Syntax.ContinueStatement continued ->
          new BoundStatement.ContinueStatement(continued.span());
    };
  }

  private BoundCatchClause bindCatchClause(Syntax.CatchClause clause) {
    Symbol symbol = symbol(clause.nameSpan());
    BoundLocalId local = BoundLocalId.of(symbol.id());
    if (lambdaCaptures != null) lambdaLocals.add(local);
    return new BoundCatchClause(
        symbol.type(), local, bindBlock(clause.body(), clause.span()), clause.span());
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
      case Syntax.Unary unary when unary.operator() == TokenKind.STAR ->
          new BoundStatement.ReferenceAssignment(
              bindExpression(unary.operand()), value, assignment.span());
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
      case Syntax.Unary unary -> bindUnary(unary, type);
      case Syntax.Binary binary ->
          new BoundExpression.Binary(
              bindExpression(binary.left()),
              binaryOperator(binary.operator(), type),
              bindExpression(binary.right()),
              type,
              binary.span());
      case Syntax.Call call -> bindCall(call, type);
      case Syntax.Member member -> bindMember(member, type);
      case Syntax.Lambda lambda -> bindLambda(lambda, type);
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

  private BoundExpression bindUnary(Syntax.Unary unary, SemanticType type) {
    if (unary.operator() == TokenKind.AMPERSAND) return bindAddress(unary, type);
    if (unary.operator() == TokenKind.STAR) {
      return new BoundExpression.Dereference(bindExpression(unary.operand()), type, unary.span());
    }
    return new BoundExpression.Unary(
        unary.operator() == TokenKind.BANG ? BoundUnaryOperator.NOT : BoundUnaryOperator.NEGATE,
        bindExpression(unary.operand()),
        type,
        unary.span());
  }

  private BoundExpression bindAddress(Syntax.Unary unary, SemanticType type) {
    return switch (unary.operand()) {
      case Syntax.Name name -> {
        Symbol target = symbol(name.span());
        if (target.kind() == SymbolKind.FIELD) {
          BoundField field = field(target);
          yield new BoundExpression.AddressField(
              thisRead(name.span()), field.id(), field.ordinal(), type, unary.span());
        }
        yield new BoundExpression.AddressLocal(BoundLocalId.of(target.id()), type, unary.span());
      }
      case Syntax.Member member -> {
        BoundField field = field(symbol(member.nameSpan()));
        yield new BoundExpression.AddressField(
            bindExpression(member.receiver()), field.id(), field.ordinal(), type, unary.span());
      }
      default -> throw new IllegalStateException("invalid checked address target");
    };
  }

  private BoundExpression bindName(Syntax.Name name, SemanticType type) {
    Symbol symbol = symbol(name.span());
    if (symbol.kind() == SymbolKind.FUNCTION
        || symbol.kind() == SymbolKind.EXTENSION
        || symbol.kind() == SymbolKind.METHOD) {
      return new BoundClosure(
          BoundCallableId.of(symbol.id()),
          Optional.empty(),
          List.of(),
          semantics.functionReferenceTypeArguments(name.span()).stream()
              .map(this::runtimeType)
              .toList(),
          type,
          name.span());
    }
    if (symbol.kind() == SymbolKind.SELF) return thisRead(name.span());
    if (symbol.kind() == SymbolKind.FIELD) {
      BoundField field = field(symbol);
      return new BoundExpression.FieldRead(
          thisRead(name.span()), field.id(), field.ordinal(), type, name.span());
    }
    BoundLocalId local = BoundLocalId.of(symbol.id());
    recordCapture(local, type);
    return new BoundExpression.LocalRead(local, type, name.span());
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
        if (lambdaCaptures != null) lambdaLocals.add(BoundLocalId.of(symbol.id()));
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
    List<BoundArgument> arguments = bindArguments(call, resolution);
    if (resolution.kind() == ResolvedCall.Kind.INVOKE) {
      return new BoundInvoke(bindExpression(call.callee()), arguments, type, call.span());
    }
    Symbol target =
        semantics
            .symbol(resolution.target())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "resolved call target is absent from the semantic model: "
                            + resolution.target()));
    Syntax.Member member = call.callee() instanceof Syntax.Member value ? value : null;
    if (resolution.kind() == ResolvedCall.Kind.EXTENSION) {
      if (member == null) throw new IllegalStateException("extension call has no receiver");
      List<BoundArgument> complete = new ArrayList<>(arguments.size() + 1);
      complete.add(new BoundArgument(bindExpression(member.receiver()), 0));
      complete.addAll(arguments);
      arguments = List.copyOf(complete);
    }
    BoundExpression receiver =
        member == null
                || resolution.kind() == ResolvedCall.Kind.EXTENSION
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
                          .intrinsic(target.id())
                          .filter(IntrinsicId::requiresResultRuntimeType)
                          .isPresent()
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
              target.kind() == SymbolKind.CONSTRUCTOR
                  ? BoundAggregateId.of(target.owner().orElseThrow())
                  : BoundAggregateId.of(target.id()),
              target.kind() == SymbolKind.CONSTRUCTOR
                  ? BoundCallableId.of(target.id())
                  : aggregates.get(target.id().value()).constructors().getFirst(),
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
      case CALLABLE, EXTENSION ->
          new BoundCall(
              BoundCallableId.of(target.id()),
              Optional.ofNullable(receiver),
              arguments,
              resolution.callableTypeArguments().stream().map(this::runtimeType).toList(),
              receiver == null
                  ? List.of()
                  : methodReceiverType(receiver.type(), target).stream()
                      .map(this::runtimeType)
                      .toList(),
              resolution.kind() == ResolvedCall.Kind.CALLABLE && isVirtualMethod(target),
              nullSafe,
              type,
              call.span());
      case INVOKE -> throw new IllegalStateException("function invocation was bound eagerly");
      case SUPER -> throw new IllegalStateException("super calls are bound by constructors");
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
        .aggregates()
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
    if (member.name().equals("class")
        && (target.kind() == SymbolKind.TYPE
            || target.kind() == SymbolKind.INTERFACE
            || target.kind() == SymbolKind.TYPE_PARAMETER)) {
      return new BoundIntrinsic(
          IntrinsicId.CLASS_LITERAL,
          Optional.empty(),
          List.of(),
          Optional.of(runtimeType(type)),
          type,
          member.span());
    }
    if (member.name().equals("field") && target.kind() == SymbolKind.FIELD) {
      BoundField field = field(target);
      BoundExpression ordinal =
          new BoundExpression.Literal(field.ordinal(), SemanticType.INTEGER, member.nameSpan());
      return new BoundIntrinsic(
          IntrinsicId.FIELD_LITERAL,
          Optional.empty(),
          List.of(new BoundArgument(ordinal, 0)),
          Optional.of(runtimeType(type)),
          type,
          member.span());
    }
    if (member.name().equals("function")
        && (target.kind() == SymbolKind.FUNCTION
            || target.kind() == SymbolKind.EXTENSION
            || target.kind() == SymbolKind.METHOD)) {
      SemanticType unboundReceiver =
          target.kind() == SymbolKind.METHOD ? type.functionParameterTypes().getFirst() : null;
      return new BoundClosure(
          BoundCallableId.of(target.id()),
          Optional.empty(),
          List.of(),
          semantics.functionReferenceTypeArguments(member.span()).stream()
              .map(this::runtimeType)
              .toList(),
          unboundReceiver == null
              ? List.of()
              : methodReceiverType(unboundReceiver, target).stream()
                  .map(this::runtimeType)
                  .toList(),
          isVirtualMethod(target),
          type,
          member.span());
    }
    if (target.kind() == SymbolKind.METHOD) {
      BoundExpression receiver = bindExpression(member.receiver());
      return new BoundClosure(
          BoundCallableId.of(target.id()),
          Optional.of(receiver),
          List.of(),
          semantics.functionReferenceTypeArguments(member.span()).stream()
              .map(this::runtimeType)
              .toList(),
          methodReceiverType(receiver.type(), target).stream().map(this::runtimeType).toList(),
          isVirtualMethod(target),
          type,
          member.span());
    }
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
      Syntax.FunctionDecl declaration, Syntax.AggregateDecl owner) {
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

  private List<BoundConformance> bindConformances(Syntax.AggregateDecl declaration) {
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
      for (Syntax.InterfaceMethodDecl requirement : contract.methods()) {
        if (requirement.body().isEmpty()
            || witnesses.stream()
                .anyMatch(
                    value ->
                        value
                            .requirement()
                            .equals(
                                BoundInterfaceMethodId.of(symbol(requirement.nameSpan()).id())))) {
          continue;
        }
        witnesses.add(
            new BoundWitness(
                BoundInterfaceMethodId.of(symbol(requirement.nameSpan()).id()),
                new BoundWitness.Target.Callable(
                    new BoundCallableId(
                        symbol(requirement.nameSpan()).id().value() + "/default"))));
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
      Syntax.AggregateDecl declaration,
      SemanticType interfaceType,
      List<BoundWitness> witnesses,
      java.util.Set<String> visited) {
    if (!visited.add(interfaceType.identity())) return;
    Syntax.InterfaceDecl contract = interfaceDeclaration(interfaceType);
    if (contract == null) return;
    for (Syntax.InterfaceMethodDecl requirement : contract.methods()) {
      Symbol aggregateSymbol = symbol(declaration.nameSpan());
      Symbol requirementSymbol = symbol(requirement.nameSpan());
      var implementationId =
          semantics
              .witness(aggregateSymbol.id(), requirementSymbol.id())
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

  private Syntax.AggregateDecl aggregateDeclaration(SemanticType type) {
    for (Syntax.Program program : programs) {
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        if (symbol(declaration.nameSpan()).type().identity().equals(type.identity())) {
          return declaration;
        }
      }
    }
    return null;
  }

  private SemanticType aggregateSelfType(Syntax.AggregateDecl declaration) {
    Symbol symbol = symbol(declaration.nameSpan());
    return SemanticType.declared(
        symbol.type().identity(),
        declaration.name(),
        declaration.typeParameters().stream()
            .map(value -> symbol(value.nameSpan()).type())
            .toList(),
        symbol.type().category());
  }

  private int inheritedFieldCount(Syntax.AggregateDecl declaration) {
    SemanticType parent = semantics.aggregateParent(aggregateSelfType(declaration)).orElse(null);
    if (parent == null) return 0;
    Syntax.AggregateDecl parentDeclaration = aggregateDeclaration(parent);
    if (parentDeclaration == null) return 0;
    return inheritedFieldCount(parentDeclaration) + parentDeclaration.fields().size();
  }

  private List<BoundMethodDispatch> bindDispatch(Syntax.AggregateDecl declaration) {
    SemanticType self = aggregateSelfType(declaration);
    List<BoundMethodDispatch> result = new ArrayList<>();
    SemanticType parent = semantics.aggregateParent(self).orElse(null);
    if (parent != null) {
      Syntax.AggregateDecl parentDeclaration = aggregateDeclaration(parent);
      if (parentDeclaration != null) {
        Map<String, SemanticType> substitutions = aggregateSubstitutions(parentDeclaration, parent);
        bindDispatch(parentDeclaration).stream()
            .map(
                dispatch ->
                    new BoundMethodDispatch(
                        dispatch.slot(),
                        dispatch.implementation(),
                        dispatch.receiverType().substitute(substitutions)))
            .forEach(result::add);
      }
    }
    for (Syntax.FunctionDecl method : declaration.methods()) {
      if (method.visibility() != Syntax.Visibility.PUBLIC) continue;
      BoundCallableId methodId = callableId(method);
      semantics
          .overriddenMethod(symbol(method.nameSpan()).id())
          .ifPresent(
              overridden -> {
                BoundCallableId parentMethod = BoundCallableId.of(overridden);
                for (int index = 0; index < result.size(); index++) {
                  BoundMethodDispatch inherited = result.get(index);
                  if (inherited.implementation().equals(parentMethod)) {
                    result.set(index, new BoundMethodDispatch(inherited.slot(), methodId, self));
                  }
                }
              });
      result.add(new BoundMethodDispatch(methodId, methodId, self));
    }
    return List.copyOf(result);
  }

  private Map<String, SemanticType> aggregateSubstitutions(
      Syntax.AggregateDecl declaration, SemanticType instance) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(declaration.typeParameters().size(), instance.arguments().size());
        index++) {
      result.put(
          symbol(declaration.typeParameters().get(index).nameSpan()).type().identity(),
          instance.arguments().get(index));
    }
    return Map.copyOf(result);
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

  private BoundExpression bindLambda(Syntax.Lambda lambda, SemanticType type) {
    BoundCallableId lambdaId =
        new BoundCallableId(currentCallableId.value() + "/lambda@" + lambda.span().startOffset());
    Map<BoundLocalId, SemanticType> previousCaptures = lambdaCaptures;
    java.util.Set<BoundLocalId> previousLocals = lambdaLocals;
    BoundCallableId previousCallable = currentCallableId;
    boolean previousImplicitSelfReturn = implicitSelfReturn;
    Map<String, BoundLocalId> previousReifiedLocals = reifiedLocals;
    List<BoundRuntimeType> closureReifiedArguments =
        activeTypeParameters.stream().map(BoundTypeParameter::type).map(this::runtimeType).toList();
    Map<String, BoundLocalId> lambdaReifiedLocals = new LinkedHashMap<>();
    List<BoundReifiedArgument> lambdaReifiedParameters = new ArrayList<>();
    for (BoundTypeParameter parameter : activeTypeParameters) {
      BoundLocalId local =
          new BoundLocalId(lambdaId.value() + "/type/" + parameter.type().identity());
      lambdaReifiedLocals.put(parameter.type().identity(), local);
      lambdaReifiedParameters.add(new BoundReifiedArgument(parameter.type().identity(), local));
    }
    Map<BoundLocalId, SemanticType> captures = new LinkedHashMap<>();
    java.util.Set<BoundLocalId> locals = new java.util.LinkedHashSet<>();
    List<BoundParameter> parameters = new ArrayList<>();
    for (int ordinal = 0; ordinal < lambda.parameters().size(); ordinal++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(ordinal);
      Symbol symbol = symbol(parameter.nameSpan());
      BoundLocalId id = BoundLocalId.of(symbol.id());
      locals.add(id);
      parameters.add(new BoundParameter(id, parameter.name(), symbol.type(), ordinal));
    }
    lambdaCaptures = captures;
    lambdaLocals = locals;
    reifiedLocals = Map.copyOf(lambdaReifiedLocals);
    currentCallableId = lambdaId;
    implicitSelfReturn = false;
    BoundBlock body = bindLambdaBlock(lambda);
    List<BoundParameter> captureParameters = new ArrayList<>();
    int ordinal = 0;
    for (Map.Entry<BoundLocalId, SemanticType> capture : captures.entrySet()) {
      captureParameters.add(
          new BoundParameter(capture.getKey(), "$capture" + ordinal, capture.getValue(), ordinal));
      ordinal++;
    }
    callables.put(
        lambdaId.value(),
        new BoundCallable(
            lambdaId,
            dev.w0fv1.norm.bound.BoundCallableKind.LAMBDA,
            "$lambda",
            dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            captureParameters,
            parameters,
            activeTypeParameters,
            lambdaReifiedParameters,
            List.of(),
            type.functionReturnType(),
            body,
            lambda.span()));
    lambdaCaptures = previousCaptures;
    lambdaLocals = previousLocals;
    reifiedLocals = previousReifiedLocals;
    currentCallableId = previousCallable;
    implicitSelfReturn = previousImplicitSelfReturn;
    List<BoundExpression> capturedValues =
        captures.entrySet().stream()
            .map(
                capture -> {
                  recordCapture(capture.getKey(), capture.getValue());
                  return (BoundExpression)
                      new BoundExpression.LocalRead(
                          capture.getKey(), capture.getValue(), lambda.span());
                })
            .toList();
    return new BoundClosure(
        lambdaId, Optional.empty(), capturedValues, closureReifiedArguments, type, lambda.span());
  }

  private BoundBlock bindLambdaBlock(Syntax.Lambda lambda) {
    List<BoundStatement> statements = new ArrayList<>();
    for (int index = 0; index < lambda.body().size(); index++) {
      Syntax.Statement statement = lambda.body().get(index);
      if (index == lambda.body().size() - 1
          && statement instanceof Syntax.ExpressionStatement expression
          && !semantics
              .typeOf(lambda.span())
              .orElseThrow()
              .functionReturnType()
              .equals(SemanticType.VOID)) {
        statements.add(
            new BoundStatement.ReturnStatement(
                Optional.of(bindExpression(expression.expression())), expression.span()));
      } else {
        statements.add(bindStatement(statement));
      }
    }
    return new BoundBlock(statements, lambda.span());
  }

  private void recordCapture(BoundLocalId local, SemanticType type) {
    if (lambdaCaptures != null && !lambdaLocals.contains(local)) {
      lambdaCaptures.putIfAbsent(local, type);
    }
  }

  private BoundExpression thisRead(SourceSpan span) {
    if (thisLocal == null) throw new IllegalStateException("implicit field access outside method");
    recordCapture(thisLocal, thisType);
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

  private List<SemanticType> methodReceiverType(SemanticType receiver, Symbol target) {
    if (target.owner().isEmpty()) return List.of();
    Symbol owner = semantics.symbol(target.owner().orElseThrow()).orElse(null);
    if (owner == null || owner.kind() != SymbolKind.TYPE) return List.of();
    SemanticType view = receiver.nonNullable();
    java.util.Set<String> visited = new java.util.HashSet<>();
    while (visited.add(view.identity())) {
      if (view.identity().equals(owner.type().identity())) return view.arguments();
      Optional<SemanticType> parent = semantics.aggregateParent(view);
      if (parent.isEmpty()) break;
      view = parent.orElseThrow();
    }
    return List.of();
  }

  private boolean isVirtualMethod(Symbol target) {
    if (target.kind() != SymbolKind.METHOD) return false;
    for (Syntax.Program program : programs) {
      for (Syntax.AggregateDecl aggregate : program.aggregates()) {
        for (Syntax.FunctionDecl method : aggregate.methods()) {
          if (symbol(method.nameSpan()).id().equals(target.id())) {
            return method.visibility() == Syntax.Visibility.PUBLIC;
          }
        }
      }
    }
    return false;
  }

  private Symbol symbol(SourceSpan span) {
    return semantics
        .symbolOf(span)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "symbol is absent for '" + span.text() + "' at " + span.location()));
  }

  private static boolean isBuiltin(Symbol symbol) {
    return symbol.id().value().startsWith("builtin/");
  }

  private BoundCallableId callableId(Syntax.FunctionDecl declaration) {
    return BoundCallableId.of(symbol(declaration.nameSpan()).id());
  }

  private List<BoundCallableId> constructorIds(Syntax.AggregateDecl declaration) {
    if (!declaration.constructors().isEmpty()) {
      return declaration.constructors().stream().map(this::constructorId).toList();
    }
    return List.of(syntheticConstructorId(declaration));
  }

  private BoundCallableId constructorId(Syntax.ConstructorDecl declaration) {
    return BoundCallableId.of(symbol(declaration.nameSpan()).id());
  }

  private BoundCallableId syntheticConstructorId(Syntax.AggregateDecl declaration) {
    return new BoundCallableId(symbol(declaration.nameSpan()).id().value() + "/constructor");
  }

  private BoundAggregateId aggregateId(Syntax.AggregateDecl declaration) {
    return BoundAggregateId.of(symbol(declaration.nameSpan()).id());
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
    return callables.values().stream()
        .filter(value -> value.span().source().id().equals(program.span().source().id()))
        .map(BoundCallable::id)
        .toList();
  }
}
