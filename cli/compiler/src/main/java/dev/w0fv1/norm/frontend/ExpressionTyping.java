package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.syntax.Syntax;

interface ExpressionTyping {
  SemanticType typeOf(Syntax.Expression expression, SemanticType expected);

  TypeProbe probeType(Syntax.Expression expression, SemanticType expected);
}
