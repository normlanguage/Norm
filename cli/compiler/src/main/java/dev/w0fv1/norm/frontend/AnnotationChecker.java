package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.semantic.AnnotationApplication;
import dev.w0fv1.norm.semantic.AnnotationDeclarationReference;
import dev.w0fv1.norm.semantic.AnnotationParameterInfo;
import dev.w0fv1.norm.semantic.AnnotationSchema;
import dev.w0fv1.norm.semantic.AnnotationSite;
import dev.w0fv1.norm.semantic.AnnotationValue;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnnotationAbi;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AnnotationChecker {
  private final SemanticAnalysisContext context;
  private final TypeSystem typeSystem;
  private final ExpressionTyping expressions;
  private final Set<AnnotationApplicationKey> indexedAnnotationApplications = new HashSet<>();

  AnnotationChecker(
      SemanticAnalysisContext context, TypeSystem typeSystem, ExpressionTyping expressions) {
    this.context = context;
    this.typeSystem = typeSystem;
    this.expressions = expressions;
  }

  final void validateAnnotationSchemas() {
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        List<SemanticType> views =
            typeSystem.nominalViews(typeSystem.aggregateSelfType(declaration));
        List<AnnotationPolicy> policies = views.stream().map(this::annotationPolicy).toList();
        boolean policyType = policies.stream().anyMatch(AnnotationPolicy::policyInterface);
        if (declaration.kind() != Syntax.AggregateKind.ANNOTATION) {
          if (policyType) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "annotation policy interfaces can only be implemented by annotation types",
                declaration.nameSpan());
          }
          continue;
        }
        if (!declaration.typeParameters().isEmpty()) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "annotation cannot declare type parameters",
              declaration.typeParameters().getFirst().nameSpan());
        }
        if (declaration.extendedClass().isPresent()) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "annotation cannot extend a class",
              declaration.extendedClass().orElseThrow().span());
        }
        Set<AnnotationTarget> targets =
            policies.stream()
                .map(AnnotationPolicy::target)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<AnnotationRetention> retentions =
            policies.stream()
                .map(AnnotationPolicy::retention)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<AnnotationTarget> interceptors =
            policies.stream()
                .map(AnnotationPolicy::interceptor)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        boolean repeatable = policies.stream().anyMatch(AnnotationPolicy::repeatable);
        boolean inherited = policies.stream().anyMatch(AnnotationPolicy::inherited);
        Map<AnnotationTarget, List<SemanticType>> typedTargets = new LinkedHashMap<>();
        for (int index = 0; index < views.size(); index++) {
          SemanticType view = views.get(index);
          Optional<AnnotationTarget> interceptor = policies.get(index).interceptor();
          if (interceptor.isPresent()
              && (interceptor.orElseThrow() == AnnotationTarget.FIELD
                  || interceptor.orElseThrow() == AnnotationTarget.PARAMETER)
              && view.arguments().size() == 1) {
            typedTargets
                .computeIfAbsent(interceptor.orElseThrow(), ignored -> new ArrayList<>())
                .add(view.arguments().getFirst());
          }
        }
        if (targets.isEmpty()) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "annotation must implement at least one annotation target interface",
              declaration.nameSpan());
        }
        if (retentions.size() != 1) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "annotation must implement exactly one annotation retention interface",
              declaration.nameSpan());
        }
        if (repeatable && !interceptors.isEmpty()) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "repeatable annotations cannot be invocation interceptors",
              declaration.nameSpan());
        }
        for (AnnotationTarget target :
            List.of(AnnotationTarget.FIELD, AnnotationTarget.PARAMETER)) {
          List<SemanticType> types = typedTargets.getOrDefault(target, List.of());
          if (interceptors.contains(target) && types.stream().distinct().count() != 1) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "annotation must implement "
                    + (target == AnnotationTarget.FIELD
                        ? "FieldInterceptor"
                        : "ParameterInterceptor")
                    + " with one concrete type",
                declaration.nameSpan());
          }
        }
        List<AnnotationParameterInfo> parameters = new ArrayList<>();
        List<?> constructorParameters =
            declaration.constructors().isEmpty()
                ? declaration.fields()
                : declaration.constructors().getFirst().parameters();
        for (Object parameter : constructorParameters) {
          Syntax.TypeRef typeRef =
              parameter instanceof Syntax.FieldDecl field
                  ? field.type()
                  : ((Syntax.Parameter) parameter).type();
          String name =
              parameter instanceof Syntax.FieldDecl field
                  ? field.name()
                  : ((Syntax.Parameter) parameter).name();
          SourceSpan nameSpan =
              parameter instanceof Syntax.FieldDecl field
                  ? field.nameSpan()
                  : ((Syntax.Parameter) parameter).nameSpan();
          typeSystem.validateType(typeRef, false);
          SemanticType type = typeSystem.resolveType(typeRef, Map.of());
          SemanticType scalar = type.nonNullable();
          if (!isAnnotationValueType(scalar)) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "annotation constructor parameters must be metadata value types",
                typeRef.span());
          }
          parameters.add(
              new AnnotationParameterInfo(
                  Optional.ofNullable(context.model.declarationSymbols().get(parameter))
                      .orElseGet(() -> context.model.bindings().get(nameSpan)),
                  name,
                  type));
        }
        SymbolId symbol = context.model.declarationSymbols().get(declaration);
        context.model.putAnnotationSchema(
            symbol,
            new AnnotationSchema(
                symbol,
                declaration.name(),
                targets,
                retentions.stream().findFirst().orElse(AnnotationRetention.SOURCE),
                repeatable,
                inherited,
                interceptors,
                typedTargets.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().distinct().count() == 1)
                    .collect(
                        java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getFirst(),
                            (left, right) -> left,
                            LinkedHashMap::new)),
                parameters));
      }
    }
  }

  private boolean isAnnotationValueType(SemanticType type) {
    if (type.isFunction()) return true;
    Syntax.EnumDecl enumeration = typeSystem.resolveEnum(type.nonNullable());
    if (enumeration != null) {
      return enumeration.variants().stream().allMatch(variant -> variant.parameters().isEmpty());
    }
    if (type.arguments().isEmpty()) {
      return Set.of("Boolean", "CodePoint", "Integer", "Long", "Float", "Double", "String")
          .contains(type.name());
    }
    return type.name().equals("Class") && type.arguments().size() == 1
        || type.name().equals("Field") && type.arguments().size() == 2
        || type.name().equals("List")
            && type.arguments().size() == 1
            && isAnnotationValueType(type.arguments().getFirst().nonNullable());
  }

  private AnnotationPolicy annotationPolicy(SemanticType type) {
    Syntax.InterfaceDecl declaration = typeSystem.resolveInterface(type);
    if (declaration == null) return AnnotationPolicy.NONE;
    Syntax.Program owner = context.declarations.owner(declaration);
    ModuleCoordinate module = context.scope.coordinate(owner.span().source().id()).module();
    return new AnnotationPolicy(
        AnnotationAbi.target(module, owner.packageName(), declaration.name()),
        AnnotationAbi.retention(module, owner.packageName(), declaration.name()),
        AnnotationAbi.interceptor(module, owner.packageName(), declaration.name()),
        AnnotationAbi.isRepeatableAnnotation(module, owner.packageName(), declaration.name()),
        AnnotationAbi.isInheritedAnnotation(module, owner.packageName(), declaration.name()),
        AnnotationAbi.isPolicyInterface(module, owner.packageName(), declaration.name()));
  }

  final void validateAnnotationApplications() {
    indexedAnnotationApplications.clear();
    for (Syntax.Program program : context.programs) {
      context.resolution.currentProgram = program;
      validateAnnotationUses(
          program.packageAnnotations(),
          new AnnotationSite.Package(
              context.scope.coordinate(program.span().source().id()).module(),
              program.packageName(),
              program.span().source().id()));
      for (Syntax.EnumDecl declaration : program.enums()) {
        validateDeclarationAnnotations(
            declaration.annotations(), AnnotationTarget.TYPE, declaration);
      }
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        validateDeclarationAnnotations(
            declaration.annotations(), AnnotationTarget.TYPE, declaration);
        for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
          validateCallableAnnotations(method.annotations(), method.parameters(), method);
          method.body().ifPresent(this::validateLocalAnnotations);
        }
      }
      for (Syntax.AggregateDecl declaration : program.aggregates()) {
        validateDeclarationAnnotations(
            declaration.annotations(), AnnotationTarget.TYPE, declaration);
        for (Syntax.FieldDecl field : declaration.fields()) {
          validateDeclarationAnnotations(field.annotations(), AnnotationTarget.FIELD, field);
        }
        for (Syntax.ConstructorDecl constructor : declaration.constructors()) {
          validateCallableAnnotations(
              constructor.annotations(),
              constructor.parameters(),
              constructor,
              AnnotationTarget.CONSTRUCTOR);
          validateLocalAnnotations(constructor.body());
        }
        for (Syntax.FunctionDecl method : declaration.methods()) {
          validateCallableAnnotations(method.annotations(), method.parameters(), method);
          validateLocalAnnotations(method.body());
        }
      }
      for (Syntax.FunctionDecl function : program.functions()) {
        validateCallableAnnotations(function.annotations(), function.parameters(), function);
        validateLocalAnnotations(function.body());
      }
    }
  }

  final void validateDeclarationAnnotations(
      List<Syntax.AnnotationUse> annotations, AnnotationTarget target, Object declaration) {
    if (!annotations.isEmpty()) {
      annotationSite(target, declaration)
          .ifPresent(site -> validateAnnotationUses(annotations, site));
    }
  }

  private Optional<AnnotationValue> annotationConstant(
      Syntax.Expression expression, SemanticType expected) {
    if (expression instanceof Syntax.ArrayLiteral list) {
      SemanticType collection = expected.nonNullable();
      if (!collection.name().equals("List") || collection.arguments().size() != 1) {
        context.diagnostics.error(
            TYPE_MISMATCH, "annotation list requires a List metadata type", expression.span());
        expressions.typeOf(expression, expected);
        return Optional.empty();
      }
      context.model.putType(expression.span(), collection);
      typeSystem.requireAssignable(expected, collection, expression.span());
      List<AnnotationValue> values = new ArrayList<>();
      boolean complete = true;
      for (Syntax.Expression element : list.elements()) {
        Optional<AnnotationValue> value =
            annotationConstant(element, collection.arguments().getFirst());
        if (value.isEmpty()) complete = false;
        else values.add(value.orElseThrow());
      }
      return complete
          ? Optional.of(new AnnotationValue(expected, new AnnotationValue.ListValue(values)))
          : Optional.empty();
    }
    boolean literal =
        expression instanceof Syntax.BooleanLiteral
            || expression instanceof Syntax.CodePointLiteral
            || expression instanceof Syntax.IntegerLiteral
            || expression instanceof Syntax.DecimalLiteral
            || expression instanceof Syntax.StringLiteralExpr
            || expression instanceof Syntax.NullLiteral;
    boolean potentialDeclarationReference = expression instanceof Syntax.Member;
    if (!literal && !potentialDeclarationReference) {
      context.diagnostics.error(
          TYPE_MISMATCH,
          "annotation argument must be a compile-time constant or declaration reference",
          expression.span());
      return Optional.empty();
    }
    SemanticType actual = expressions.typeOf(expression, expected);
    typeSystem.requireAssignable(expected, actual, expression.span());
    Optional<AnnotationDeclarationReference> reference =
        potentialDeclarationReference
            ? declarationReference((Syntax.Member) expression, actual)
            : Optional.empty();
    if (potentialDeclarationReference && reference.isEmpty()) {
      context.diagnostics.error(
          TYPE_MISMATCH,
          "annotation argument must be a compile-time constant or declaration reference",
          expression.span());
      return Optional.empty();
    }
    AnnotationValue.Content value =
        potentialDeclarationReference
            ? reference.orElseThrow()
            : switch (expression) {
              case Syntax.BooleanLiteral item -> new AnnotationValue.Literal(item.value());
              case Syntax.CodePointLiteral item -> new AnnotationValue.Literal(item.value());
              case Syntax.IntegerLiteral item -> new AnnotationValue.Literal(item.value());
              case Syntax.DecimalLiteral item -> new AnnotationValue.Literal(item.value());
              case Syntax.StringLiteralExpr item -> new AnnotationValue.Literal(item.value());
              case Syntax.NullLiteral ignored -> AnnotationValue.Null.INSTANCE;
              default -> throw new IllegalStateException("annotation constant is not a literal");
            };
    return Optional.of(new AnnotationValue(expected, value));
  }

  private Optional<AnnotationDeclarationReference> declarationReference(
      Syntax.Member member, SemanticType actualType) {
    SymbolId target = context.model.bindings().get(member.nameSpan());
    if (target == null) return Optional.empty();
    Symbol symbol = context.model.symbols().get(target);
    if (symbol != null && symbol.kind() == SymbolKind.ENUM_VARIANT) {
      if (!symbol.parameters().isEmpty()) {
        context.diagnostics.error(
            TYPE_MISMATCH, "annotation enum value must not have a payload", member.span());
        return Optional.empty();
      }
      return Optional.of(
          new AnnotationDeclarationReference(
              AnnotationDeclarationReference.Kind.ENUM_VARIANT, target, actualType, member.span()));
    }
    AnnotationDeclarationReference.Kind kind;
    switch (member.name()) {
      case "class" -> kind = AnnotationDeclarationReference.Kind.CLASS;
      case "function" -> kind = AnnotationDeclarationReference.Kind.CALLABLE;
      case "field" -> kind = AnnotationDeclarationReference.Kind.FIELD;
      default -> {
        return Optional.empty();
      }
    }
    return Optional.of(new AnnotationDeclarationReference(kind, target, actualType, member.span()));
  }

  private void validateCallableAnnotations(
      List<Syntax.AnnotationUse> annotations,
      List<Syntax.Parameter> parameters,
      Object declaration) {
    validateCallableAnnotations(annotations, parameters, declaration, AnnotationTarget.FUNCTION);
  }

  private void validateCallableAnnotations(
      List<Syntax.AnnotationUse> annotations,
      List<Syntax.Parameter> parameters,
      Object declaration,
      AnnotationTarget callableTarget) {
    validateDeclarationAnnotations(annotations, callableTarget, declaration);
    for (Syntax.Parameter parameter : parameters) {
      validateDeclarationAnnotations(
          parameter.annotations(), AnnotationTarget.PARAMETER, parameter);
    }
    SymbolId callableId = context.model.declarationSymbols().get(declaration);
    List<AnnotationApplication> interceptors =
        context.model.annotationApplications().stream()
            .filter(
                application ->
                    application.target() instanceof AnnotationSite.Symbol site
                        && site.kind() == AnnotationTarget.FUNCTION
                        && site.symbol().equals(callableId)
                        && isInterceptor(application, AnnotationTarget.FUNCTION))
            .toList();
    Set<SymbolId> parameterSymbols =
        parameters.stream()
            .map(parameter -> context.model.declarationSymbols().get(parameter))
            .collect(java.util.stream.Collectors.toSet());
    List<AnnotationApplication> parameterInterceptors =
        context.model.annotationApplications().stream()
            .filter(
                application ->
                    application.target() instanceof AnnotationSite.Symbol site
                        && site.kind() == AnnotationTarget.PARAMETER
                        && parameterSymbols.contains(site.symbol())
                        && isInterceptor(application, AnnotationTarget.PARAMETER))
            .toList();
    if (interceptors.isEmpty() && parameterInterceptors.isEmpty()) return;
    if (declaration instanceof Syntax.InterfaceMethodDecl) {
      interceptors.forEach(
          application ->
              context.diagnostics.error(
                  TYPE_MISMATCH,
                  "FunctionInterceptor annotation requires a concrete function or method",
                  application.span()));
      parameterInterceptors.forEach(
          application ->
              context.diagnostics.error(
                  TYPE_MISMATCH,
                  "ParameterInterceptor annotation requires a concrete callable parameter",
                  application.span()));
    }
    Symbol callable = context.model.symbols().get(callableId);
    boolean containsReference =
        callable.type().containsReference()
            || callable.parameters().stream()
                .anyMatch(parameter -> parameter.type().containsReference());
    if (containsReference) {
      interceptors.forEach(
          application ->
              context.diagnostics.error(
                  TYPE_MISMATCH,
                  "FunctionInterceptor annotation cannot intercept a ref signature",
                  application.span()));
    }
    parameterInterceptors.forEach(
        application -> {
          AnnotationSite.Symbol site = (AnnotationSite.Symbol) application.target();
          if (context.model.symbols().get(site.symbol()).type().isReference()) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "ParameterInterceptor annotation cannot intercept a ref parameter",
                application.span());
          }
        });
  }

  private Optional<AnnotationSite> annotationSite(AnnotationTarget target, Object declaration) {
    SymbolId symbol = context.model.declarationSymbols().get(declaration);
    if (symbol == null && declaration instanceof Syntax.VariableDecl variable) {
      symbol = context.model.bindings().get(variable.nameSpan());
    }
    return symbol == null
        ? Optional.empty()
        : Optional.of(
            new AnnotationSite.Symbol(
                target, symbol, context.resolution.currentProgram.span().source().id()));
  }

  private void validateLocalAnnotations(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      switch (statement) {
        case Syntax.VariableDecl variable -> {
          validateDeclarationAnnotations(variable.annotations(), AnnotationTarget.LOCAL, variable);
          validateLocalAnnotations(variable.initializer());
        }
        case Syntax.Assignment assignment -> {
          validateLocalAnnotations(assignment.target());
          validateLocalAnnotations(assignment.value());
        }
        case Syntax.ExpressionStatement expression ->
            validateLocalAnnotations(expression.expression());
        case Syntax.IfStatement conditional -> {
          validateLocalAnnotations(conditional.condition());
          validateLocalAnnotations(conditional.thenBody());
          validateLocalAnnotations(conditional.elseBody());
        }
        case Syntax.ConditionalForStatement loop -> {
          validateLocalAnnotations(loop.condition());
          validateLocalAnnotations(loop.body());
        }
        case Syntax.ForStatement loop -> {
          validateLocalAnnotations(loop.iterable());
          validateLocalAnnotations(loop.body());
        }
        case Syntax.TryStatement tried -> {
          validateLocalAnnotations(tried.body());
          tried.catches().forEach(value -> validateLocalAnnotations(value.body()));
          tried.finallyClause().ifPresent(value -> validateLocalAnnotations(value.body()));
        }
        case Syntax.ThrowStatement thrown -> validateLocalAnnotations(thrown.exception());
        case Syntax.ReturnStatement returned -> validateLocalAnnotations(returned.value());
        case Syntax.BreakStatement broken -> validateLocalAnnotations(broken.value());
        case Syntax.ContinueStatement ignored -> {}
      }
    }
  }

  private void validateLocalAnnotations(Syntax.Expression expression) {
    if (expression == null) return;
    switch (expression) {
      case Syntax.ArrayLiteral array -> array.elements().forEach(this::validateLocalAnnotations);
      case Syntax.Unary unary -> validateLocalAnnotations(unary.operand());
      case Syntax.Binary binary -> {
        validateLocalAnnotations(binary.left());
        validateLocalAnnotations(binary.right());
      }
      case Syntax.Call call -> {
        validateLocalAnnotations(call.callee());
        call.arguments().forEach(value -> validateLocalAnnotations(value.value()));
      }
      case Syntax.Member member -> validateLocalAnnotations(member.receiver());
      case Syntax.Lambda lambda -> validateLocalAnnotations(lambda.body());
      case Syntax.Index index -> {
        validateLocalAnnotations(index.receiver());
        validateLocalAnnotations(index.index());
      }
      case Syntax.SwitchExpression switched -> {
        validateLocalAnnotations(switched.value());
        switched.cases().forEach(value -> validateLocalAnnotations(value.body()));
      }
      case Syntax.IntegerLiteral ignored -> {}
      case Syntax.DecimalLiteral ignored -> {}
      case Syntax.CodePointLiteral ignored -> {}
      case Syntax.BooleanLiteral ignored -> {}
      case Syntax.NullLiteral ignored -> {}
      case Syntax.StringLiteralExpr ignored -> {}
      case Syntax.InterpolatedStringExpr interpolation ->
          interpolation.expressions().forEach(this::validateLocalAnnotations);
      case Syntax.Name ignored -> {}
    }
  }

  private void validateAnnotationUses(List<Syntax.AnnotationUse> uses, AnnotationSite target) {
    for (Syntax.AnnotationUse use : uses) {
      Syntax.AggregateDecl declaration = typeSystem.resolveAnnotation(use.name());
      if (declaration == null) {
        context.diagnostics.error(
            UNKNOWN_NAME, "cannot find annotation '" + use.name() + "'", use.nameSpan());
        use.arguments()
            .forEach(argument -> annotationConstant(argument.value(), SemanticType.DYNAMIC));
        continue;
      }
      SymbolId annotation = context.model.declarationSymbols().get(declaration);
      context.model.putBinding(use.nameSpan(), annotation);
      AnnotationSchema schema = context.model.annotationSchemas().get(annotation);
      if (schema == null) continue;
      boolean duplicate = !indexedAnnotationApplications.add(applicationKey(annotation, target));
      if (duplicate && !schema.repeatable()) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "duplicate annotation '" + use.name() + "' on the same target",
            use.span());
      }
      if (!schema.targets().contains(target.kind())) {
        context.diagnostics.error(
            TYPE_MISMATCH,
            "annotation '"
                + use.name()
                + "' does not allow target '"
                + target.kind().keyword()
                + "'",
            use.span());
      }
      Map<String, Syntax.CallArgument> supplied = new LinkedHashMap<>();
      boolean validArguments = true;
      for (int index = 0; index < use.arguments().size(); index++) {
        Syntax.CallArgument argument = use.arguments().get(index);
        String label;
        if (argument.label().isEmpty()) {
          boolean hasValue =
              schema.parameters().stream().anyMatch(parameter -> parameter.name().equals("value"));
          if (index != 0 || !hasValue) {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "only the first annotation argument may omit the 'value' label",
                argument.span());
            annotationConstant(argument.value(), SemanticType.DYNAMIC);
            validArguments = false;
            continue;
          }
          label = "value";
        } else {
          label = argument.label().orElseThrow().name();
        }
        if (supplied.putIfAbsent(label, argument) != null) {
          context.diagnostics.error(
              TYPE_MISMATCH, "duplicate annotation parameter '" + label + "'", argument.span());
          validArguments = false;
        }
      }
      List<AnnotationValue> values = new ArrayList<>();
      boolean complete = validArguments;
      if (target instanceof AnnotationSite.Symbol site
          && site.kind() == AnnotationTarget.FIELD
          && schema.targetType(AnnotationTarget.FIELD).isPresent()) {
        SemanticType fieldType = context.model.symbols().get(site.symbol()).type();
        SemanticType targetType = schema.targetType(AnnotationTarget.FIELD).orElseThrow();
        if (!fieldType.equals(targetType)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "FieldInterceptor type '"
                  + targetType.displayName()
                  + "' does not match field type '"
                  + fieldType.displayName()
                  + "'",
              use.span());
          complete = false;
        }
      }
      if (target instanceof AnnotationSite.Symbol site
          && site.kind() == AnnotationTarget.PARAMETER
          && schema.targetType(AnnotationTarget.PARAMETER).isPresent()) {
        SemanticType parameterType = context.model.symbols().get(site.symbol()).type();
        SemanticType targetType = schema.targetType(AnnotationTarget.PARAMETER).orElseThrow();
        if (parameterType.isReference()) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "ParameterInterceptor annotation cannot intercept a ref parameter",
              use.span());
          complete = false;
        } else if (!parameterType.equals(targetType)) {
          context.diagnostics.error(
              TYPE_MISMATCH,
              "ParameterInterceptor type '"
                  + targetType.displayName()
                  + "' does not match parameter type '"
                  + parameterType.displayName()
                  + "'",
              use.span());
          complete = false;
        }
      }
      for (AnnotationParameterInfo parameter : schema.parameters()) {
        Syntax.CallArgument argument = supplied.remove(parameter.name());
        if (argument == null) {
          if (parameter.type().isNullable()) {
            values.add(new AnnotationValue(parameter.type(), AnnotationValue.Null.INSTANCE));
          } else {
            context.diagnostics.error(
                TYPE_MISMATCH,
                "required annotation parameter '" + parameter.name() + "' is missing",
                use.span());
            complete = false;
          }
          continue;
        }
        argument
            .label()
            .ifPresent(label -> context.model.putBinding(label.span(), parameter.symbol()));
        Optional<AnnotationValue> value = annotationConstant(argument.value(), parameter.type());
        if (value.isEmpty()) complete = false;
        else values.add(value.orElseThrow());
      }
      for (Syntax.CallArgument argument : supplied.values()) {
        String label = argument.label().map(Syntax.ArgumentLabel::name).orElse("value");
        context.diagnostics.error(
            TYPE_MISMATCH, "unknown annotation parameter '" + label + "'", argument.span());
        annotationConstant(argument.value(), SemanticType.DYNAMIC);
        complete = false;
      }
      if ((!duplicate || schema.repeatable())
          && complete
          && values.size() == schema.parameters().size()) {
        context.model.addAnnotation(
            new AnnotationApplication(annotation, target, values, use.span()));
      }
    }
  }

  private AnnotationApplicationKey applicationKey(SymbolId annotation, AnnotationSite target) {
    Object identity =
        switch (target) {
          case AnnotationSite.Package site ->
              new PackageIdentity(site.module(), site.packageName());
          case AnnotationSite.Symbol site -> site.symbol();
        };
    return new AnnotationApplicationKey(annotation, target.kind(), identity);
  }

  private boolean isInterceptor(AnnotationApplication application, AnnotationTarget target) {
    AnnotationSchema schema = context.model.annotationSchemas().get(application.annotation());
    return schema != null && schema.intercepts(target);
  }

  private record AnnotationApplicationKey(
      SymbolId annotation, AnnotationTarget target, Object identity) {}

  private record AnnotationPolicy(
      Optional<AnnotationTarget> target,
      Optional<AnnotationRetention> retention,
      Optional<AnnotationTarget> interceptor,
      boolean repeatable,
      boolean inherited,
      boolean policyInterface) {
    private static final AnnotationPolicy NONE =
        new AnnotationPolicy(
            Optional.empty(), Optional.empty(), Optional.empty(), false, false, false);
  }

  private record PackageIdentity(ModuleCoordinate module, String packageName) {}
}
