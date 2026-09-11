package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.TYPE_MISMATCH;

import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.CollectionElement;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

final class CollectionAnalyzer {
  private final BodyAnalysisState body;
  private final dev.w0fv1.norm.builtin.BuiltinSymbols builtins;
  private final DiagnosticBag diagnostics;
  private final SemanticModelBuilder model;

  CollectionAnalyzer(
      BodyAnalysisState body,
      dev.w0fv1.norm.builtin.BuiltinSymbols builtins,
      DiagnosticBag diagnostics,
      SemanticModelBuilder model,
      TypeResolver typeResolver,
      FlowAnalyzer flow,
      ExpressionTyping expressions,
      java.util.function.BiConsumer<Syntax.ForStatement, Runnable> iterations) {
    this.body = body;
    this.builtins = builtins;
    this.diagnostics = diagnostics;
    this.model = model;
    this.typeResolver = typeResolver;
    this.flow = flow;
    this.expressions = expressions;
    this.iterations = iterations;
  }

  private final TypeResolver typeResolver;
  private final FlowAnalyzer flow;
  private final ExpressionTyping expressions;
  private final BiConsumer<Syntax.ForStatement, Runnable> iterations;

  SemanticType analyze(Syntax.ArrayLiteral array, SemanticType expected) {
    SemanticType expectedArray =
        expected == null
            ? null
            : builtins.resolveCollectionLiteral(expected).map(value -> value.type()).orElse(null);
    SemanticType expectedElement =
        expectedArray != null && expectedArray.arguments().size() == 1
            ? expectedArray.arguments().getFirst()
            : null;
    boolean inferElements =
        expectedElement == null || CallResolver.containsDynamic(expectedElement);
    SemanticType elementType = inferElements ? null : expectedElement;
    List<Contribution> contributions = new ArrayList<>();
    for (CollectionElement element : array.elements()) {
      analyzeCollectionElement(element, expectedElement, contributions);
    }
    for (var contribution : contributions) {
      SourceSpan elementSpan = contribution.span();
      SemanticType current = contribution.type();
      if (elementType == null && !CallResolver.containsDynamic(current)) {
        elementType = current;
      } else if (elementType != null && !CallResolver.containsDynamic(current)) {
        if (!inferElements) {
          if (!typeResolver.isAssignable(elementType, current)) {
            diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                elementSpan);
          }
        } else {
          SemanticType common = typeResolver.commonType(elementType, current).orElse(null);
          if (common == null) {
            diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                elementSpan);
          } else {
            elementType = common;
          }
        }
      }
    }
    SemanticType inferredElement = elementType == null ? SemanticType.DYNAMIC : elementType;
    if (inferredElement.containsReference()) {
      diagnostics.error(TYPE_MISMATCH, "collection element type cannot contain ref", array.span());
      return SemanticType.DYNAMIC;
    }
    return expectedArray == null
        ? builtins.instantiate("Array", List.of(inferredElement))
        : new SemanticType(
            expectedArray.kind(),
            expectedArray.identity(),
            expectedArray.name(),
            List.of(inferredElement),
            expectedArray.category(),
            expectedArray.nullability());
  }

  private void analyzeCollectionElement(
      CollectionElement element, SemanticType expected, List<Contribution> contributions) {
    switch (element) {
      case Syntax.Expression expression ->
          contributions.add(
              new Contribution(expression.span(), expressions.typeOf(expression, expected)));
      case CollectionElement.Spread spread -> {
        SemanticType expectedIterable = typeResolver.expectedIterable(expected);
        SemanticType iterable =
            typeResolver.receiverType(
                expressions.typeOf(spread.iterable(), expectedIterable),
                false,
                spread.iterable().span());
        Optional<ResolvedIteration> iteration = typeResolver.resolveIteration(iterable);
        if (iteration.isEmpty()
            || CallResolver.containsDynamic(iteration.orElseThrow().elementType())) {
          diagnostics.error(TYPE_MISMATCH, "spread requires an iterable value", spread.span());
        } else {
          model.putIteration(spread.iterable().span(), iteration.orElseThrow());
          contributions.add(new Contribution(spread.span(), iteration.orElseThrow().elementType()));
        }
      }
      case CollectionElement.Conditional conditional -> {
        typeResolver.requireType(
            SemanticType.BOOLEAN,
            expressions.typeOf(conditional.condition(), SemanticType.BOOLEAN),
            conditional.condition().span());
        FlowScopes.FlowState incoming = this.body.scopes().snapshot();
        FlowScopes.FlowState thenFlow =
            flow.analyzeBranch(
                conditional.thenElement().span(),
                flow.narrowingsFor(conditional.condition(), true),
                incoming,
                () -> analyzeCollectionElement(conditional.thenElement(), expected, contributions));
        FlowScopes.FlowState elseFlow =
            flow.analyzeBranch(
                conditional.elseElement().map(CollectionElement::span).orElse(conditional.span()),
                flow.narrowingsFor(conditional.condition(), false),
                incoming,
                () ->
                    conditional
                        .elseElement()
                        .ifPresent(
                            value -> analyzeCollectionElement(value, expected, contributions)));
        flow.replaceFlow(flow.mergeFlows(incoming, thenFlow, elseFlow));
      }
      case CollectionElement.Repeated repeated ->
          iterations.accept(
              (Syntax.ForStatement) repeated.statement(),
              () -> analyzeCollectionElement(repeated.element(), expected, contributions));
    }
  }

  private record Contribution(SourceSpan span, SemanticType type) {}
}
