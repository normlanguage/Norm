package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;
import java.util.Optional;

interface ExpressionTyping {
  SemanticType typeOf(Syntax.Expression expression, SemanticType expected);

  TypeProbe probeType(Syntax.Expression expression, SemanticType expected);

  SemanticType typeOf(
      Syntax.Expression expression, SemanticType expected, List<String> contextualNames);

  TypeProbe probeType(
      Syntax.Expression expression, SemanticType expected, List<String> contextualNames);

  SemanticType typeOf(
      Syntax.Expression expression,
      SemanticType expected,
      List<String> contextualNames,
      Optional<SemanticType> builder);

  TypeProbe probeType(
      Syntax.Expression expression,
      SemanticType expected,
      List<String> contextualNames,
      Optional<SemanticType> builder);
}
