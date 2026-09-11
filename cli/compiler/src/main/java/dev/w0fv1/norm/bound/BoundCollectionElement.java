package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;
import java.util.Objects;
import java.util.Optional;

public sealed interface BoundCollectionElement extends BoundNode
    permits BoundExpression, BoundCollectionElement.Conditional, BoundCollectionElement.Repeated {
  record Conditional(
      BoundExpression condition,
      BoundCollectionElement thenElement,
      Optional<BoundCollectionElement> elseElement,
      SourceSpan span)
      implements BoundCollectionElement {
    public Conditional {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(thenElement, "thenElement");
      elseElement = Objects.requireNonNull(elseElement, "elseElement");
      Objects.requireNonNull(span, "span");
    }
  }

  record Repeated(
      BoundLocalId iterator,
      BoundLocalId variable,
      SemanticType variableType,
      Optional<BoundLocalId> index,
      BoundExpression iterable,
      BoundIteration iteration,
      BoundCollectionElement element,
      SourceSpan span)
      implements BoundCollectionElement {
    public Repeated {
      Objects.requireNonNull(iterator, "iterator");
      Objects.requireNonNull(variable, "variable");
      Objects.requireNonNull(variableType, "variableType");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(iteration, "iteration");
      Objects.requireNonNull(element, "element");
      Objects.requireNonNull(span, "span");
    }
  }
}
