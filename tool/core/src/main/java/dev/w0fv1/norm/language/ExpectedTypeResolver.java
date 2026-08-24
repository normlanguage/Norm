package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Optional;

final class ExpectedTypeResolver {
  private final CallSiteResolver callSites = new CallSiteResolver();
  private final TypeReferenceResolver typeReferences = new TypeReferenceResolver();

  Optional<SemanticType> resolve(DocumentSemanticModel document, int offset) {
    SemanticModel model = document.semanticModel();
    TokenKind previous = previousToken(document.tokens(), offset);
    for (Syntax.FunctionDecl function : document.syntax().functions()) {
      if (!contains(function.span(), offset)) continue;
      Optional<SemanticType> expected =
          inStatements(
              model,
              function.body(),
              model.typeOf(function.returnType()),
              offset,
              previous == TokenKind.BREAK);
      if (expected.isPresent()) return expected;
      if (previous == TokenKind.RETURN) {
        return model.typeOf(function.returnType());
      }
    }
    for (Syntax.ClassDecl declaration : document.syntax().classes()) {
      for (Syntax.FunctionDecl method : declaration.methods()) {
        if (!contains(method.span(), offset)) continue;
        Optional<SemanticType> expected =
            inStatements(
                model,
                method.body(),
                model.typeOf(method.returnType()),
                offset,
                previous == TokenKind.BREAK);
        if (expected.isPresent()) return expected;
        if (previous == TokenKind.RETURN) {
          return model.typeOf(method.returnType());
        }
      }
    }
    Optional<CallSite> call = callSites.resolve(document, offset);
    if (call.isPresent()) {
      CallSite site = call.orElseThrow();
      if (!site.callable().parameters().isEmpty()) {
        return Optional.of(site.callable().parameters().get(site.activeParameter()).type());
      }
    }
    return typeReferences.beforeIncompleteInitializer(document, offset);
  }

  private Optional<SemanticType> inStatements(
      SemanticModel model,
      List<Syntax.Statement> statements,
      Optional<SemanticType> returnType,
      int offset,
      boolean incompleteBreak) {
    return inStatements(model, statements, returnType, Optional.empty(), offset, incompleteBreak);
  }

  private Optional<SemanticType> inStatements(
      SemanticModel model,
      List<Syntax.Statement> statements,
      Optional<SemanticType> returnType,
      Optional<SemanticType> breakType,
      int offset,
      boolean incompleteBreak) {
    for (Syntax.Statement statement : statements) {
      Optional<SemanticType> expected =
          inStatement(model, statement, returnType, breakType, offset, incompleteBreak);
      if (expected.isPresent()) return expected;
    }
    return Optional.empty();
  }

  private Optional<SemanticType> inStatement(
      SemanticModel model,
      Syntax.Statement statement,
      Optional<SemanticType> returnType,
      Optional<SemanticType> breakType,
      int offset,
      boolean incompleteBreak) {
    if (statement instanceof Syntax.VariableDecl variable
        && contains(variable.initializer().span(), offset)) {
      Optional<SemanticType> expected = variable.type().flatMap(model::typeOf);
      return inExpression(
              model, variable.initializer(), returnType, expected, offset, incompleteBreak)
          .or(() -> expected);
    }
    if (statement instanceof Syntax.Assignment assignment
        && contains(assignment.value().span(), offset)) {
      Optional<SemanticType> expected = model.typeOf(assignment.target().span());
      return inExpression(model, assignment.value(), returnType, expected, offset, incompleteBreak)
          .or(() -> expected);
    }
    if (statement instanceof Syntax.ReturnStatement returned
        && returned.value() != null
        && contains(returned.value().span(), offset)) {
      return inExpression(model, returned.value(), returnType, returnType, offset, incompleteBreak)
          .or(() -> returnType);
    }
    if (statement instanceof Syntax.BreakStatement broken
        && broken.value() != null
        && contains(broken.value().span(), offset)) {
      return inExpression(model, broken.value(), returnType, breakType, offset, incompleteBreak)
          .or(() -> breakType);
    }
    if (statement instanceof Syntax.BreakStatement broken
        && broken.value() == null
        && incompleteBreak
        && broken.span().endOffset() <= offset) {
      return breakType;
    }
    if (statement instanceof Syntax.ExpressionStatement expression) {
      return inExpression(
          model, expression.expression(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (statement instanceof Syntax.IfStatement conditional) {
      Optional<SemanticType> nested =
          inStatements(
              model, conditional.thenBody(), returnType, breakType, offset, incompleteBreak);
      if (nested.isPresent()) return nested;
      return inStatements(
          model, conditional.elseBody(), returnType, breakType, offset, incompleteBreak);
    }
    if (statement instanceof Syntax.ForStatement loop) {
      return inStatements(
          model, loop.body(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (statement instanceof Syntax.ConditionalForStatement loop) {
      return inStatements(
          model, loop.body(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    return Optional.empty();
  }

  private Optional<SemanticType> inExpression(
      SemanticModel model,
      Syntax.Expression expression,
      Optional<SemanticType> returnType,
      Optional<SemanticType> expectedType,
      int offset,
      boolean incompleteBreak) {
    if (!contains(expression.span(), offset)) return Optional.empty();
    if (expression instanceof Syntax.SwitchExpression switched) {
      Optional<SemanticType> nested =
          inExpression(
              model, switched.value(), returnType, Optional.empty(), offset, incompleteBreak);
      if (nested.isPresent()) return nested;
      for (Syntax.SwitchCase branch : switched.cases()) {
        if (!contains(branch.span(), offset)) continue;
        nested =
            inStatements(model, branch.body(), returnType, expectedType, offset, incompleteBreak);
        if (nested.isPresent()) return nested;
      }
      return Optional.empty();
    }
    if (expression instanceof Syntax.Call call) {
      for (int argumentIndex = 0; argumentIndex < call.arguments().size(); argumentIndex++) {
        int currentArgument = argumentIndex;
        Syntax.CallArgument argument = call.arguments().get(argumentIndex);
        if (!contains(argument.value().span(), offset)) continue;
        Optional<dev.w0fv1.norm.semantic.ResolvedCall> resolved = model.callOf(call.span());
        Optional<SemanticType> parameterType = Optional.empty();
        if (resolved.isPresent()) {
          int parameterIndex =
              currentArgument < resolved.orElseThrow().arguments().parameterIndices().size()
                  ? resolved.orElseThrow().arguments().parameterIndices().get(currentArgument)
                  : currentArgument;
          List<dev.w0fv1.norm.semantic.ParameterInfo> parameters =
              resolved.orElseThrow().parameters();
          if (parameterIndex < parameters.size()) {
            parameterType = Optional.of(parameters.get(parameterIndex).type());
          }
        }
        Optional<SemanticType> nested =
            inExpression(
                model, argument.value(), returnType, parameterType, offset, incompleteBreak);
        return nested.isPresent() ? nested : parameterType;
      }
      return inExpression(
          model, call.callee(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (expression instanceof Syntax.Unary unary) {
      return inExpression(
          model, unary.operand(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (expression instanceof Syntax.Binary binary) {
      Optional<SemanticType> nested =
          inExpression(model, binary.left(), returnType, Optional.empty(), offset, incompleteBreak);
      return nested.isPresent()
          ? nested
          : inExpression(
              model, binary.right(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (expression instanceof Syntax.Member member) {
      return inExpression(
          model, member.receiver(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (expression instanceof Syntax.Index index) {
      Optional<SemanticType> nested =
          inExpression(
              model, index.receiver(), returnType, Optional.empty(), offset, incompleteBreak);
      return nested.isPresent()
          ? nested
          : inExpression(
              model, index.index(), returnType, Optional.empty(), offset, incompleteBreak);
    }
    if (expression instanceof Syntax.ArrayLiteral array) {
      for (Syntax.Expression element : array.elements()) {
        Optional<SemanticType> nested =
            inExpression(model, element, returnType, Optional.empty(), offset, incompleteBreak);
        if (nested.isPresent()) return nested;
      }
    }
    return Optional.empty();
  }

  private static TokenKind previousToken(List<Token> tokens, int offset) {
    return tokens.stream()
        .filter(token -> token.span().endOffset() <= offset)
        .filter(token -> token.kind() != TokenKind.END_OF_FILE)
        .reduce((first, second) -> second)
        .map(Token::kind)
        .orElse(null);
  }

  private static boolean contains(SourceSpan span, int offset) {
    return span.startOffset() <= offset && offset <= span.endOffset();
  }
}
