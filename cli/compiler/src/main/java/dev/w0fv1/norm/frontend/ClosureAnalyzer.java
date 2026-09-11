package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.BlockResults;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ClosureAnalyzer {
  private final BodyAnalysisState body;
  private final SemanticModelBuilder model;
  private final TypeResolutionState resolution;
  private final DiagnosticBag diagnostics;
  private final TypeResolver typeResolver;
  private final DeclarationAnalyzer declarationAnalyzer;
  private final FlowAnalyzer flow;
  private final ExpressionTyping expressions;
  private final java.util.function.Consumer<Syntax.Statement> statements;

  ClosureAnalyzer(
      BodyAnalysisState body,
      SemanticModelBuilder model,
      TypeResolutionState resolution,
      DiagnosticBag diagnostics,
      TypeResolver typeResolver,
      DeclarationAnalyzer declarationAnalyzer,
      FlowAnalyzer flow,
      ExpressionTyping expressions,
      java.util.function.Consumer<Syntax.Statement> statements) {
    this.body = body;
    this.model = model;
    this.resolution = resolution;
    this.diagnostics = diagnostics;
    this.typeResolver = typeResolver;
    this.declarationAnalyzer = declarationAnalyzer;
    this.flow = flow;
    this.expressions = expressions;
    this.statements = statements;
  }

  SemanticType analyzeLambda(
      Syntax.Lambda lambda, SemanticType expected, List<String> contextualNames) {
    SemanticType expectedFunction = expected != null && expected.isFunction() ? expected : null;
    boolean implicit = lambda.parameters().isEmpty() && !contextualNames.isEmpty();
    int parameterCount = implicit ? contextualNames.size() : lambda.parameters().size();
    if (expectedFunction != null
        && expectedFunction.functionParameterTypes().size() != parameterCount) {
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
    if (implicit) {
      for (int index = 0; index < parameterCount; index++) {
        parameterTypes.add(
            expectedFunction == null
                ? SemanticType.DYNAMIC
                : expectedFunction.functionParameterTypes().get(index));
      }
    }
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      SemanticType contextual =
          expectedFunction == null ? null : expectedFunction.functionParameterTypes().get(index);
      SemanticType explicit =
          parameter
              .type()
              .map(
                  type -> {
                    typeResolver.validateType(type, false);
                    SemanticType resolved = typeResolver.resolveType(type, resolution.parameters());
                    return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                  })
              .orElse(null);
      if (explicit != null && contextual != null)
        typeResolver.requireType(contextual, explicit, parameter.span());
      SemanticType resolved = explicit != null ? explicit : contextual;
      if (resolved == null) {
        diagnostics.error(TYPE_MISMATCH, "cannot infer lambda parameter type", parameter.span());
        resolved = SemanticType.DYNAMIC;
      }
      parameterTypes.add(resolved);
    }
    SemanticType declaredContextualReturn =
        lambda
            .returnType()
            .map(
                type -> {
                  typeResolver.validateType(type, true);
                  SemanticType resolved = typeResolver.resolveType(type, resolution.parameters());
                  return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                })
            .orElse(expectedFunction == null ? null : expectedFunction.functionReturnType());
    SemanticType contextualReturn =
        declaredContextualReturn != null
                && declaredContextualReturn.kind() == SemanticType.Kind.TYPE_PARAMETER
                && !resolution.parameters().containsValue(declaredContextualReturn)
            ? null
            : declaredContextualReturn;
    if (lambda.returnType().isPresent() && expectedFunction != null) {
      typeResolver.requireType(
          expectedFunction.functionReturnType(),
          declaredContextualReturn,
          lambda.returnType().orElseThrow().span());
    }
    SemanticType result = contextualReturn;
    try (var lambdaScope =
        this.body.enterLambda(
            lambda.span(), contextualReturn == null ? SemanticType.DYNAMIC : contextualReturn)) {
      if (implicit) {
        for (int index = 0; index < parameterCount; index++) {
          var anchor = SourceSpan.at(lambda.span().source(), lambda.span().startOffset());
          SymbolId id = model.allocate(anchor.source().id());
          Symbol symbol =
              new Symbol(
                  id,
                  contextualNames.get(index),
                  SymbolKind.PARAMETER,
                  parameterTypes.get(index),
                  Optional.empty(),
                  Optional.ofNullable(this.body.currentCallable()),
                  List.of(),
                  List.of(),
                  "");
          model.putSymbol(id, symbol);
          flow.declareExisting(symbol.name(), symbol.type(), anchor, id);
          this.body.declareLambdaLocal(id);
        }
      }
      for (int index = 0; index < lambda.parameters().size(); index++) {
        Syntax.LambdaParameter parameter = lambda.parameters().get(index);
        Symbol symbol =
            declarationAnalyzer.register(
                parameter,
                parameter.name(),
                SymbolKind.PARAMETER,
                parameterTypes.get(index),
                parameter.nameSpan(),
                this.body.currentCallable(),
                List.of(),
                List.of());
        flow.declareExisting(
            parameter.name(), parameterTypes.get(index), parameter.nameSpan(), symbol.id());
        this.body.declareLambdaLocal(symbol.id());
      }
      if (contextualReturn != null && !contextualReturn.equals(SemanticType.VOID)) {
        List<Syntax.Statement> body = BlockResults.returning(lambda.body(), true);
        body.forEach(statements);
        if (!StatementFlow.definitelyExits(body)) {
          diagnostics.error(
              INVALID_CONTROL,
              "lambda must return " + contextualReturn.displayName(),
              lambda.span());
        }
      } else {
        int last = lambda.body().size() - 1;
        for (int index = 0; index < lambda.body().size(); index++) {
          Syntax.Statement statement = lambda.body().get(index);
          if (index == last && statement instanceof Syntax.ExpressionStatement expression) {
            result = expressions.typeOf(expression.expression(), contextualReturn);
            if (contextualReturn != null)
              typeResolver.requireAssignable(contextualReturn, result, expression.span());
          } else {
            statements.accept(statement);
          }
        }
      }
    }
    if (result == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "lambda return type requires an expected type or a final expression",
          lambda.span());
      result = SemanticType.DYNAMIC;
    }
    if (result.containsReference()) {
      diagnostics.error(TYPE_MISMATCH, "lambda return type cannot contain ref", lambda.span());
      result = SemanticType.DYNAMIC;
    }
    return SemanticType.function(result, parameterTypes);
  }

  SemanticType analyzeBuilderLambda(
      Syntax.Lambda lambda, SemanticType expected, SemanticType builder) {
    var protocol =
        typeResolver.nominalViews(builder).stream()
            .filter(
                type ->
                    type.identity().equals(dev.w0fv1.norm.value.AnnotationAbi.RESULT_BUILDER)
                        && type.arguments().size() == 2)
            .findFirst();
    if (expected == null
        || !expected.isFunction()
        || !expected.functionParameterTypes().isEmpty()
        || !lambda.parameters().isEmpty()
        || protocol.isEmpty()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "BuildWith requires a zero-parameter callback and a ResultBuilder implementation",
          lambda.span());
      return expected == null ? SemanticType.DYNAMIC : expected;
    }
    try {
      var lowered = ResultBuilderLowering.lower(lambda, builder);
      model.putResultBuilder(lambda.span(), builder);
      return analyzeLambda(
          lowered,
          SemanticType.function(protocol.orElseThrow().arguments().get(1), List.of()),
          List.of());
    } catch (ResultBuilderLowering.InvalidControl failure) {
      diagnostics.error(INVALID_CONTROL, failure.getMessage(), failure.span());
      return expected;
    }
  }

  void reportMutableCapture(SymbolId symbol, SourceSpan span) {
    if (this.body.markCaptureReported(symbol)) {
      diagnostics.error(
          INVALID_CONTROL,
          "captured local '" + model.symbols().get(symbol).name() + "' must be effectively final",
          span);
    }
  }

  void read(Symbol symbol, SourceSpan span) {
    if (!body.externalToLambda(symbol.id())
        || (symbol.kind() != SymbolKind.LOCAL_VARIABLE
            && symbol.kind() != SymbolKind.PARAMETER
            && symbol.kind() != SymbolKind.SELF)) return;
    if (symbol.type().isReference())
      diagnostics.error(TYPE_MISMATCH, "ref cannot be captured by a lambda", span);
    body.recordCapture(symbol.id());
    if (body.assigned(symbol.id())) reportMutableCapture(symbol.id(), span);
  }

  void assign(Symbol symbol, SourceSpan span) {
    if (symbol.kind() != SymbolKind.LOCAL_VARIABLE && symbol.kind() != SymbolKind.PARAMETER) return;
    if (body.externalToLambda(symbol.id())) {
      body.recordCapture(symbol.id());
      reportMutableCapture(symbol.id(), span);
    }
    body.recordAssignment(symbol.id());
    if (body.captured(symbol.id())) reportMutableCapture(symbol.id(), span);
  }
}
