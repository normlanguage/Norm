package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.AnnotationApplication;
import dev.w0fv1.norm.semantic.AnnotationParameterInfo;
import dev.w0fv1.norm.semantic.AnnotationSchema;
import dev.w0fv1.norm.semantic.AnnotationSite;
import dev.w0fv1.norm.semantic.AnnotationValue;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

abstract class AnalyzerAnnotations extends AnalyzerExpressions {
  private final Set<AnnotationApplicationKey> indexedAnnotationApplications = new HashSet<>();

  AnalyzerAnnotations(
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

  final void validateAnnotationSchemas() {
    for (Syntax.Program program : programs) {
      currentProgram = program;
      for (Syntax.AnnotationDecl declaration : program.annotationDeclarations()) {
        Set<String> names = new HashSet<>();
        List<AnnotationParameterInfo> parameters = new ArrayList<>();
        for (Syntax.AnnotationParameter parameter : declaration.parameters()) {
          validateType(parameter.type(), false);
          SemanticType type = resolveType(parameter.type(), Map.of());
          if (!isAnnotationFieldType(type)) {
            diagnostics.error(
                TYPE_MISMATCH,
                "annotation field type must be a scalar literal type",
                parameter.type().span());
          }
          if (!names.add(parameter.name())) {
            diagnostics.error(
                DUPLICATE_NAME,
                "annotation parameter '" + parameter.name() + "' is already declared",
                parameter.nameSpan());
          }
          Optional<AnnotationValue> defaultValue =
              parameter.defaultValue().flatMap(value -> annotationConstant(value, type));
          parameters.add(
              new AnnotationParameterInfo(
                  declarationSymbols.get(parameter), parameter.name(), type, defaultValue));
        }
        SymbolId symbol = declarationSymbols.get(declaration);
        annotationSchemas.put(
            symbol,
            new AnnotationSchema(
                symbol,
                declaration.name(),
                declaration.targets(),
                declaration.retention(),
                parameters));
      }
    }
  }

  final void validateAnnotationApplications() {
    indexedAnnotationApplications.clear();
    for (Syntax.Program program : programs) {
      currentProgram = program;
      validateAnnotationUses(
          program.packageAnnotations(),
          new AnnotationSite.Package(
              scope.coordinate(program.span().source().id()).module(),
              program.packageName(),
              program.span().source().id()));
      for (Syntax.AnnotationDecl declaration : program.annotationDeclarations()) {
        validateDeclarationAnnotations(
            declaration.annotations(), AnnotationTarget.TYPE, declaration);
        for (Syntax.AnnotationParameter parameter : declaration.parameters()) {
          validateDeclarationAnnotations(
              parameter.annotations(), AnnotationTarget.FIELD, parameter);
        }
      }
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
          validateDeclarationAnnotations(
              constructor.annotations(), AnnotationTarget.CONSTRUCTOR, constructor);
          for (Syntax.Parameter parameter : constructor.parameters()) {
            validateDeclarationAnnotations(
                parameter.annotations(), AnnotationTarget.PARAMETER, parameter);
          }
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
      validateAnnotationUses(annotations, annotationSite(target, declaration));
    }
  }

  private boolean isAnnotationFieldType(SemanticType type) {
    SemanticType value = type.nonNullable();
    return value.arguments().isEmpty()
        && Set.of("Boolean", "CodePoint", "Integer", "Long", "Float", "Double", "String")
            .contains(value.name());
  }

  private Optional<AnnotationValue> annotationConstant(
      Syntax.Expression expression, SemanticType expected) {
    boolean literal =
        expression instanceof Syntax.BooleanLiteral
            || expression instanceof Syntax.CodePointLiteral
            || expression instanceof Syntax.IntegerLiteral
            || expression instanceof Syntax.DecimalLiteral
            || expression instanceof Syntax.StringLiteralExpr
            || expression instanceof Syntax.NullLiteral;
    if (!literal) {
      diagnostics.error(
          TYPE_MISMATCH, "annotation argument must be a compile-time constant", expression.span());
      return Optional.empty();
    }
    SemanticType actual = typeOf(expression, expected);
    requireAssignable(expected, actual, expression.span());
    Object value =
        switch (expression) {
          case Syntax.BooleanLiteral item -> item.value();
          case Syntax.CodePointLiteral item -> item.value();
          case Syntax.IntegerLiteral item -> item.value();
          case Syntax.DecimalLiteral item -> item.value();
          case Syntax.StringLiteralExpr item -> item.value();
          case Syntax.NullLiteral ignored -> null;
          default -> throw new IllegalStateException("annotation constant is not a literal");
        };
    return Optional.of(new AnnotationValue(expected, value));
  }

  private void validateCallableAnnotations(
      List<Syntax.AnnotationUse> annotations,
      List<Syntax.Parameter> parameters,
      Object declaration) {
    validateDeclarationAnnotations(annotations, AnnotationTarget.FUNCTION, declaration);
    for (Syntax.Parameter parameter : parameters) {
      validateDeclarationAnnotations(
          parameter.annotations(), AnnotationTarget.PARAMETER, parameter);
    }
  }

  private AnnotationSite annotationSite(AnnotationTarget target, Object declaration) {
    SymbolId symbol = declarationSymbols.get(declaration);
    if (symbol == null && declaration instanceof Syntax.VariableDecl variable) {
      symbol = bindings.get(variable.nameSpan());
    }
    return new AnnotationSite.Symbol(
        target, java.util.Objects.requireNonNull(symbol), currentProgram.span().source().id());
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
      case Syntax.MethodReference reference -> validateLocalAnnotations(reference.receiver());
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
      case Syntax.Name ignored -> {}
    }
  }

  private void validateAnnotationUses(List<Syntax.AnnotationUse> uses, AnnotationSite target) {
    for (Syntax.AnnotationUse use : uses) {
      Syntax.AnnotationDecl declaration = resolveAnnotation(use.name());
      if (declaration == null) {
        diagnostics.error(
            UNKNOWN_NAME, "cannot find annotation '" + use.name() + "'", use.nameSpan());
        use.arguments()
            .forEach(argument -> annotationConstant(argument.value(), SemanticType.DYNAMIC));
        continue;
      }
      SymbolId annotation = declarationSymbols.get(declaration);
      bindings.put(use.nameSpan(), annotation);
      AnnotationSchema schema = annotationSchemas.get(annotation);
      if (schema == null) continue;
      boolean duplicate = !indexedAnnotationApplications.add(applicationKey(annotation, target));
      if (duplicate) {
        diagnostics.error(
            TYPE_MISMATCH,
            "duplicate annotation '" + use.name() + "' on the same target",
            use.span());
      }
      if (!schema.targets().contains(target.kind())) {
        diagnostics.error(
            TYPE_MISMATCH,
            "annotation '"
                + use.name()
                + "' does not allow target '"
                + target.kind().keyword()
                + "'",
            use.span());
      }
      Map<String, Syntax.CallArgument> supplied = new LinkedHashMap<>();
      for (Syntax.CallArgument argument : use.arguments()) {
        if (argument.label().isEmpty()) {
          diagnostics.error(TYPE_MISMATCH, "annotation arguments must be named", argument.span());
          continue;
        }
        String label = argument.label().orElseThrow().name();
        if (supplied.putIfAbsent(label, argument) != null) {
          diagnostics.error(
              TYPE_MISMATCH, "duplicate annotation parameter '" + label + "'", argument.span());
        }
      }
      List<AnnotationValue> values = new ArrayList<>();
      boolean complete = true;
      for (AnnotationParameterInfo parameter : schema.parameters()) {
        Syntax.CallArgument argument = supplied.remove(parameter.name());
        if (argument == null) {
          if (parameter.defaultValue().isEmpty()) {
            diagnostics.error(
                TYPE_MISMATCH,
                "required annotation parameter '" + parameter.name() + "' is missing",
                use.span());
            complete = false;
          } else {
            values.add(parameter.defaultValue().orElseThrow());
          }
          continue;
        }
        bindings.put(argument.label().orElseThrow().span(), parameter.symbol());
        Optional<AnnotationValue> value = annotationConstant(argument.value(), parameter.type());
        if (value.isEmpty()) complete = false;
        else values.add(value.orElseThrow());
      }
      for (Syntax.CallArgument argument : supplied.values()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "unknown annotation parameter '" + argument.label().orElseThrow().name() + "'",
            argument.span());
        annotationConstant(argument.value(), SemanticType.DYNAMIC);
        complete = false;
      }
      if (!duplicate && complete && values.size() == schema.parameters().size()) {
        annotationApplications.add(
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

  private record AnnotationApplicationKey(
      SymbolId annotation, AnnotationTarget target, Object identity) {}

  private record PackageIdentity(
      dev.w0fv1.norm.value.ModuleCoordinate module, String packageName) {}
}
