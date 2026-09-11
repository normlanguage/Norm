package dev.w0fv1.norm.syntax;

import java.util.ArrayList;
import java.util.List;

public final class BlockResults {
  private BlockResults() {}

  public static List<Syntax.Statement> returning(
      List<Syntax.Statement> body, boolean returnsValue) {
    if (!returnsValue || body.isEmpty()) return body;
    return transform(body, false);
  }

  public static List<Syntax.Statement> yielding(List<Syntax.Statement> body) {
    return transform(body, true);
  }

  private static List<Syntax.Statement> transform(List<Syntax.Statement> body, boolean yields) {
    if (body.isEmpty()) return body;
    Syntax.Statement last = body.getLast();
    Syntax.Statement result =
        switch (last) {
          case Syntax.ExpressionStatement expression ->
              yields
                  ? new Syntax.BreakStatement(expression.expression(), expression.span())
                  : new Syntax.ReturnStatement(expression.expression(), expression.span());
          case Syntax.IfStatement conditional ->
              new Syntax.IfStatement(
                  conditional.condition(),
                  transform(conditional.thenBody(), yields),
                  transform(conditional.elseBody(), yields),
                  conditional.span());
          default -> last;
        };
    if (result == last) return body;
    List<Syntax.Statement> statements = new ArrayList<>(body);
    statements.set(statements.size() - 1, result);
    return List.copyOf(statements);
  }
}
