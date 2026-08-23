package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
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
    for (Syntax.FunctionDecl function : document.syntax().functions()) {
      if (!contains(function.span(), offset)) continue;
      Optional<SemanticType> expected =
          inStatements(model, function.body(), model.typeOf(function.returnType()), offset);
      if (expected.isPresent()) return expected;
      if (previousToken(document.tokens(), offset) == TokenKind.RETURN) {
        return model.typeOf(function.returnType());
      }
    }
    for (Syntax.ClassDecl declaration : document.syntax().classes()) {
      for (Syntax.FunctionDecl method : declaration.methods()) {
        if (!contains(method.span(), offset)) continue;
        Optional<SemanticType> expected =
            inStatements(model, method.body(), model.typeOf(method.returnType()), offset);
        if (expected.isPresent()) return expected;
        if (previousToken(document.tokens(), offset) == TokenKind.RETURN) {
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
      int offset) {
    for (Syntax.Statement statement : statements) {
      Optional<SemanticType> expected = inStatement(model, statement, returnType, offset);
      if (expected.isPresent()) return expected;
    }
    return Optional.empty();
  }

  private Optional<SemanticType> inStatement(
      SemanticModel model,
      Syntax.Statement statement,
      Optional<SemanticType> returnType,
      int offset) {
    if (statement instanceof Syntax.VariableDecl variable
        && contains(variable.initializer().span(), offset)) {
      return model.typeOf(variable.type());
    }
    if (statement instanceof Syntax.Assignment assignment
        && contains(assignment.value().span(), offset)) {
      return model.typeOf(assignment.target().span());
    }
    if (statement instanceof Syntax.ReturnStatement returned
        && returned.value() != null
        && contains(returned.value().span(), offset)) {
      return returnType;
    }
    if (statement instanceof Syntax.ExpressionStatement expression) {
      return inExpression(model, expression.expression(), offset);
    }
    if (statement instanceof Syntax.IfStatement conditional) {
      Optional<SemanticType> nested =
          inStatements(model, conditional.thenBody(), returnType, offset);
      if (nested.isPresent()) return nested;
      return inStatements(model, conditional.elseBody(), returnType, offset);
    }
    if (statement instanceof Syntax.ForStatement loop) {
      return inStatements(model, loop.body(), returnType, offset);
    }
    return Optional.empty();
  }

  private Optional<SemanticType> inExpression(
      SemanticModel model, Syntax.Expression expression, int offset) {
    if (!(expression instanceof Syntax.Call call) || !contains(call.span(), offset)) {
      return Optional.empty();
    }
    for (int argumentIndex = 0; argumentIndex < call.arguments().size(); argumentIndex++) {
      int currentArgument = argumentIndex;
      Syntax.CallArgument argument = call.arguments().get(argumentIndex);
      if (!contains(argument.value().span(), offset)) continue;
      Optional<SemanticType> nested = inExpression(model, argument.value(), offset);
      if (nested.isPresent()) return nested;
      Optional<Symbol> callable = callable(model, call.callee());
      if (callable.isEmpty()) return Optional.empty();
      int parameterIndex =
          model
              .argumentsOf(call.span())
              .filter(binding -> currentArgument < binding.parameterIndices().size())
              .map(binding -> binding.parameterIndices().get(currentArgument))
              .orElse(currentArgument);
      List<dev.w0fv1.norm.semantic.ParameterInfo> parameters = callable.orElseThrow().parameters();
      return parameterIndex < parameters.size()
          ? Optional.of(parameters.get(parameterIndex).type())
          : Optional.empty();
    }
    return Optional.empty();
  }

  private static Optional<Symbol> callable(SemanticModel model, Syntax.Expression expression) {
    if (expression instanceof Syntax.Member member) {
      return model.symbolAt(member.nameSpan().startOffset()).map(model::resolveAlias);
    }
    return model.symbolAt(expression.span().startOffset()).map(model::resolveAlias);
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
