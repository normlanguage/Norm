package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.BlockResults;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;

final class IfExpressionLowering {
  private IfExpressionLowering() {}

  static Syntax.IfStatement statement(Syntax.IfExpression expression) {
    return new Syntax.IfStatement(
        expression.condition(),
        BlockResults.yielding(expression.thenBody()),
        BlockResults.yielding(expression.elseBody()),
        expression.span());
  }

  static Syntax.SwitchExpression selection(Syntax.IfExpression expression) {
    Syntax.IfStatement statement = statement(expression);
    return new Syntax.SwitchExpression(
        expression.condition(),
        List.of(
            new Syntax.SwitchCase(
                new Syntax.BooleanPattern(true, expression.condition().span()),
                statement.thenBody(),
                expression.span()),
            new Syntax.SwitchCase(
                new Syntax.BooleanPattern(false, expression.condition().span()),
                statement.elseBody(),
                expression.span())),
        expression.span());
  }
}
