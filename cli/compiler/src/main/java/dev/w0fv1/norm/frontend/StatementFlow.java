package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;

final class StatementFlow {
  private StatementFlow() {}

  static boolean definitelyExits(List<Syntax.Statement> statements) {
    return definitelyTransfers(statements, false);
  }

  static boolean definitelyYields(List<Syntax.Statement> statements) {
    return definitelyTransfers(statements, true);
  }

  private static boolean definitelyTransfers(
      List<Syntax.Statement> statements, boolean valueBreaks) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement
          || statement instanceof Syntax.ThrowStatement
          || valueBreaks
              && statement instanceof Syntax.BreakStatement broken
              && broken.value() != null) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && definitelyTransfers(conditional.thenBody(), valueBreaks)
          && definitelyTransfers(conditional.elseBody(), valueBreaks)) return true;
      if (statement instanceof Syntax.TryStatement tried) {
        if (tried.finallyClause().isPresent()
            && definitelyTransfers(tried.finallyClause().orElseThrow().body(), valueBreaks))
          return true;
        if (definitelyTransfers(tried.body(), valueBreaks)
            && tried.catches().stream()
                .allMatch(clause -> definitelyTransfers(clause.body(), valueBreaks))) return true;
      }
    }
    return false;
  }
}
