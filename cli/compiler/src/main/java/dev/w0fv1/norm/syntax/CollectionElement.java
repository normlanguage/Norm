package dev.w0fv1.norm.syntax;

import dev.w0fv1.norm.source.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface CollectionElement extends AstNode
    permits Syntax.Expression,
        CollectionElement.Spread,
        CollectionElement.Conditional,
        CollectionElement.Repeated {
  record Spread(Syntax.Expression iterable, SourceSpan span) implements CollectionElement {
    public Spread {
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(span, "span");
    }
  }

  record Conditional(
      Syntax.Expression condition,
      CollectionElement thenElement,
      Optional<CollectionElement> elseElement,
      SourceSpan span)
      implements CollectionElement {
    public Conditional {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(thenElement, "thenElement");
      elseElement = Objects.requireNonNull(elseElement, "elseElement");
      Objects.requireNonNull(span, "span");
    }
  }

  record Repeated(
      Optional<Syntax.TypeRef> variableType,
      String variableName,
      SourceSpan variableNameSpan,
      Optional<Syntax.ForIndex> index,
      Syntax.Expression iterable,
      CollectionElement element,
      SourceSpan span)
      implements CollectionElement {
    public Repeated {
      variableType = Objects.requireNonNull(variableType, "variableType");
      Objects.requireNonNull(variableName, "variableName");
      Objects.requireNonNull(variableNameSpan, "variableNameSpan");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(element, "element");
      Objects.requireNonNull(span, "span");
    }
  }

  default Syntax.Statement statement() {
    return switch (this) {
      case Syntax.Expression expression -> new Syntax.ExpressionStatement(expression, span());
      case Spread spread -> new Syntax.ExpressionStatement(spread.iterable(), span());
      case Conditional conditional ->
          new Syntax.IfStatement(
              conditional.condition(), List.of(conditional.thenElement().statement()),
              conditional.elseElement().map(value -> List.of(value.statement())).orElse(List.of()),
                  span());
      case Repeated repeated ->
          new Syntax.ForStatement(
              repeated.variableType(),
              repeated.variableName(),
              repeated.variableNameSpan(),
              repeated.index(),
              repeated.iterable(),
              List.of(repeated.element().statement()),
              span());
    };
  }
}
