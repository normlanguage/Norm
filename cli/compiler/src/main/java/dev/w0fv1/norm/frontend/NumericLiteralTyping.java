package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.semantic.NumericTypes;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;

final class NumericLiteralTyping {
  private final DiagnosticBag diagnostics;

  NumericLiteralTyping(DiagnosticBag diagnostics) {
    this.diagnostics = diagnostics;
  }

  SemanticType numericIntegerType(
      java.math.BigInteger value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.integerLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  SemanticType numericDecimalType(
      java.math.BigDecimal value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.decimalLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  boolean requireNumericLeaves(SemanticType left, SemanticType right, SourceSpan span) {
    if (NumericTypes.isLeaf(left) && left.equals(right)) return true;
    diagnostics.error(
        TYPE_MISMATCH,
        "numeric operands require the same concrete leaf type; found "
            + left.displayName()
            + " and "
            + right.displayName(),
        span);
    return false;
  }
}
