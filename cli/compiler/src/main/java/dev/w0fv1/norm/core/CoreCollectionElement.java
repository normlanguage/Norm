package dev.w0fv1.norm.core;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public sealed interface CoreCollectionElement extends CoreNode
    permits CoreExpression, CoreCollectionElement.Conditional, CoreCollectionElement.Repeated {
  record Conditional(
      int nodeIndex,
      CoreExpression condition,
      CoreCollectionElement thenElement,
      Optional<CoreCollectionElement> elseElement)
      implements CoreCollectionElement {
    public Conditional {
      if (nodeIndex < 0) throw new IllegalArgumentException("negative node index");
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(thenElement, "thenElement");
      elseElement = Objects.requireNonNull(elseElement, "elseElement");
    }
  }

  record Repeated(
      int nodeIndex,
      int iteratorLocal,
      int variableLocal,
      OptionalInt indexLocal,
      CoreExpression iterable,
      CoreIteration iteration,
      CoreCollectionElement element)
      implements CoreCollectionElement {
    public Repeated {
      if (nodeIndex < 0 || iteratorLocal < 0 || variableLocal < 0)
        throw new IllegalArgumentException("negative collection node or local index");
      indexLocal = Objects.requireNonNull(indexLocal, "indexLocal");
      if (indexLocal.isPresent() && indexLocal.orElseThrow() < 0)
        throw new IllegalArgumentException("negative index local");
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(iteration, "iteration");
      Objects.requireNonNull(element, "element");
    }
  }
}
