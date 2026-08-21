package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class Parser {
  private static final DiagnosticCode EXPECTED_TOKEN = new DiagnosticCode("NORM-PARSER-0001");
  private static final DiagnosticCode EXPECTED_EXPRESSION = new DiagnosticCode("NORM-PARSER-0002");
  private static final DiagnosticCode INVALID_ASSIGNMENT = new DiagnosticCode("NORM-PARSER-0003");

  private final SourceFile source;
  private final List<Token> tokens;
  private final DiagnosticBag diagnostics;
  private int current;

  Parser(SourceFile source, List<Token> tokens, DiagnosticBag diagnostics) {
    this.source = Objects.requireNonNull(source, "source");
    this.tokens = List.copyOf(tokens);
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    if (tokens.isEmpty() || tokens.getLast().kind() != TokenKind.END_OF_FILE) {
      throw new IllegalArgumentException("token stream must end with END_OF_FILE");
    }
  }

  Syntax.Program parse() {
    List<Syntax.EnumDecl> enums = new ArrayList<>();
    List<Syntax.ClassDecl> classes = new ArrayList<>();
    List<Syntax.FunctionDecl> functions = new ArrayList<>();
    while (!isAtEnd()) {
      try {
        if (match(TokenKind.ENUM)) {
          enums.add(parseEnum(previous()));
        } else if (match(TokenKind.CLASS)) {
          classes.add(parseClass(previous()));
        } else {
          functions.add(parseFunction());
        }
      } catch (ParseError ignored) {
        synchronizeTopLevel();
      }
    }
    return new Syntax.Program(
        enums, classes, functions, new SourceSpan(source, 0, source.length()));
  }

  private Syntax.EnumDecl parseEnum(Token enumKeyword) {
    Token name = consume(TokenKind.IDENTIFIER, "expected enum name");
    consume(TokenKind.LEFT_BRACE, "expected '{' before enum body");
    List<Syntax.EnumMember> members = new ArrayList<>();
    if (!check(TokenKind.RIGHT_BRACE)) {
      do {
        Token member = consume(TokenKind.IDENTIFIER, "expected enum member");
        members.add(new Syntax.EnumMember(member.lexeme(), member.span()));
      } while (match(TokenKind.COMMA) && !check(TokenKind.RIGHT_BRACE));
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after enum body");
    match(TokenKind.SEMICOLON);
    return new Syntax.EnumDecl(
        name.lexeme(), name.span(), members, enumKeyword.span().cover(closing.span()));
  }

  private Syntax.ClassDecl parseClass(Token classKeyword) {
    Token name = consume(TokenKind.IDENTIFIER, "expected class name");
    consume(TokenKind.LEFT_BRACE, "expected '{' before class body");
    List<Syntax.FieldDecl> fields = new ArrayList<>();
    List<Syntax.FunctionDecl> methods = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      Syntax.TypeRef type = parseType();
      Token memberName = consume(TokenKind.IDENTIFIER, "expected field or method name");
      if (check(TokenKind.LEFT_PAREN)) {
        methods.add(parseFunctionRest(type, memberName));
      } else {
        match(TokenKind.SEMICOLON);
        fields.add(
            new Syntax.FieldDecl(
                type,
                memberName.lexeme(),
                memberName.span(),
                type.span().cover(memberName.span())));
      }
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after class body");
    return new Syntax.ClassDecl(
        name.lexeme(), name.span(), fields, methods, classKeyword.span().cover(closing.span()));
  }

  private Syntax.FunctionDecl parseFunction() {
    Syntax.TypeRef returnType = parseType();
    Token name = consume(TokenKind.IDENTIFIER, "expected function name");
    return parseFunctionRest(returnType, name);
  }

  private Syntax.FunctionDecl parseFunctionRest(Syntax.TypeRef returnType, Token name) {
    consume(TokenKind.LEFT_PAREN, "expected '(' after function name");
    List<Syntax.Parameter> parameters = new ArrayList<>();
    if (!check(TokenKind.RIGHT_PAREN)) {
      do {
        Syntax.TypeRef type = parseType();
        Token parameterName = consume(TokenKind.IDENTIFIER, "expected parameter name");
        parameters.add(
            new Syntax.Parameter(
                type,
                parameterName.lexeme(),
                parameterName.span(),
                type.span().cover(parameterName.span())));
      } while (match(TokenKind.COMMA));
    }
    consume(TokenKind.RIGHT_PAREN, "expected ')' after parameters");
    Block block = parseBlock();
    return new Syntax.FunctionDecl(
        returnType,
        name.lexeme(),
        name.span(),
        parameters,
        block.statements(),
        returnType.span().cover(block.span()));
  }

  private Syntax.TypeRef parseType() {
    if (match(TokenKind.ARRAY_TYPE)) {
      Token array = previous();
      return new Syntax.TypeRef("Array", null, array.span());
    }
    if (match(
        TokenKind.INT_TYPE,
        TokenKind.BOOL_TYPE,
        TokenKind.STRING_TYPE,
        TokenKind.VOID,
        TokenKind.IDENTIFIER)) {
      Token type = previous();
      return new Syntax.TypeRef(type.lexeme(), null, type.span());
    }
    throw error(peek(), "expected type name");
  }

  private Block parseBlock() {
    Token opening = consume(TokenKind.LEFT_BRACE, "expected '{'");
    List<Syntax.Statement> statements = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      try {
        statements.add(parseStatement());
      } catch (ParseError ignored) {
        synchronizeStatement();
      }
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after block");
    return new Block(statements, opening.span().cover(closing.span()));
  }

  private Syntax.Statement parseStatement() {
    if (match(TokenKind.IF)) {
      return parseIf(previous());
    }
    if (match(TokenKind.FOR)) {
      return parseFor(previous());
    }
    if (match(TokenKind.RETURN)) {
      return parseReturn(previous());
    }
    if (match(TokenKind.BREAK)) {
      Token keyword = previous();
      match(TokenKind.SEMICOLON);
      return new Syntax.BreakStatement(keyword.span());
    }
    if (match(TokenKind.CONTINUE)) {
      Token keyword = previous();
      match(TokenKind.SEMICOLON);
      return new Syntax.ContinueStatement(keyword.span());
    }
    if (looksLikeVariableDeclaration()) {
      return parseVariableDeclaration();
    }

    Syntax.Expression expression = parseExpression();
    if (match(TokenKind.EQUAL)) {
      if (!(expression instanceof Syntax.Name
          || expression instanceof Syntax.Member
          || expression instanceof Syntax.Index)) {
        diagnostics.error(INVALID_ASSIGNMENT, "invalid assignment target", expression.span());
      }
      Syntax.Expression value = parseExpression();
      match(TokenKind.SEMICOLON);
      return new Syntax.Assignment(expression, value, expression.span().cover(value.span()));
    }
    match(TokenKind.SEMICOLON);
    return new Syntax.ExpressionStatement(expression, expression.span());
  }

  private Syntax.Statement parseVariableDeclaration() {
    Syntax.TypeRef type = parseType();
    Token name = consume(TokenKind.IDENTIFIER, "expected variable name");
    consume(TokenKind.EQUAL, "variables must have an initializer");
    Syntax.Expression initializer = parseExpression();
    match(TokenKind.SEMICOLON);
    return new Syntax.VariableDecl(
        type, name.lexeme(), name.span(), initializer, type.span().cover(initializer.span()));
  }

  private Syntax.IfStatement parseIf(Token keyword) {
    Syntax.Expression condition = parseExpression();
    Block thenBlock = parseBlock();
    List<Syntax.Statement> elseBody = List.of();
    SourceSpan end = thenBlock.span();
    if (match(TokenKind.ELSE)) {
      if (match(TokenKind.IF)) {
        Syntax.IfStatement nested = parseIf(previous());
        elseBody = List.of(nested);
        end = nested.span();
      } else {
        Block elseBlock = parseBlock();
        elseBody = elseBlock.statements();
        end = elseBlock.span();
      }
    }
    return new Syntax.IfStatement(
        condition, thenBlock.statements(), elseBody, keyword.span().cover(end));
  }

  private Syntax.ForStatement parseFor(Token keyword) {
    Optional<Syntax.TypeRef> type;
    Token name;
    if (check(TokenKind.IDENTIFIER) && checkNext(TokenKind.COLON)) {
      type = Optional.empty();
      name = advance();
    } else {
      type = Optional.of(parseType());
      name = consume(TokenKind.IDENTIFIER, "expected loop variable name");
    }
    consume(TokenKind.COLON, "expected ':' after loop variable");
    Syntax.Expression iterable = parseExpression();
    Block body = parseBlock();
    return new Syntax.ForStatement(
        type,
        name.lexeme(),
        name.span(),
        iterable,
        body.statements(),
        keyword.span().cover(body.span()));
  }

  private Syntax.ReturnStatement parseReturn(Token keyword) {
    Syntax.Expression value = null;
    if (!check(TokenKind.RIGHT_BRACE) && !check(TokenKind.SEMICOLON)) {
      value = parseExpression();
    }
    match(TokenKind.SEMICOLON);
    return new Syntax.ReturnStatement(
        value, value == null ? keyword.span() : keyword.span().cover(value.span()));
  }

  private Syntax.Expression parseExpression() {
    return parseOr();
  }

  private Syntax.Expression parseOr() {
    Syntax.Expression expression = parseAnd();
    while (match(TokenKind.OR_OR)) {
      Token operator = previous();
      Syntax.Expression right = parseAnd();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
  }

  private Syntax.Expression parseAnd() {
    Syntax.Expression expression = parseEquality();
    while (match(TokenKind.AND_AND)) {
      Token operator = previous();
      Syntax.Expression right = parseEquality();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
  }

  private Syntax.Expression parseEquality() {
    Syntax.Expression expression = parseComparison();
    while (match(TokenKind.EQUAL_EQUAL, TokenKind.BANG_EQUAL)) {
      Token operator = previous();
      Syntax.Expression right = parseComparison();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
  }

  private Syntax.Expression parseComparison() {
    Syntax.Expression expression = parseTerm();
    while (match(
        TokenKind.LESS, TokenKind.LESS_EQUAL, TokenKind.GREATER, TokenKind.GREATER_EQUAL)) {
      Token operator = previous();
      Syntax.Expression right = parseTerm();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
  }

  private Syntax.Expression parseTerm() {
    Syntax.Expression expression = parseFactor();
    while (match(TokenKind.PLUS, TokenKind.MINUS)) {
      Token operator = previous();
      Syntax.Expression right = parseFactor();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
  }

  private Syntax.Expression parseFactor() {
    Syntax.Expression expression = parseUnary();
    while (match(TokenKind.STAR, TokenKind.SLASH, TokenKind.PERCENT)) {
      Token operator = previous();
      Syntax.Expression right = parseUnary();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
  }

  private Syntax.Expression parseUnary() {
    if (match(TokenKind.BANG, TokenKind.MINUS)) {
      Token operator = previous();
      Syntax.Expression operand = parseUnary();
      return new Syntax.Unary(operator.kind(), operand, operator.span().cover(operand.span()));
    }
    return parsePostfix();
  }

  private Syntax.Expression parsePostfix() {
    Syntax.Expression expression = parsePrimary();
    while (true) {
      if (match(TokenKind.LEFT_PAREN)) {
        List<Syntax.CallArgument> arguments = new ArrayList<>();
        if (!check(TokenKind.RIGHT_PAREN)) {
          do {
            Optional<Syntax.ArgumentLabel> label = Optional.empty();
            Token argumentStart = peek();
            if (check(TokenKind.IDENTIFIER) && checkNext(TokenKind.COLON)) {
              Token name = advance();
              label = Optional.of(new Syntax.ArgumentLabel(name.lexeme(), name.span()));
              advance();
            }
            Syntax.Expression value = parseExpression();
            arguments.add(
                new Syntax.CallArgument(label, value, argumentStart.span().cover(value.span())));
          } while (match(TokenKind.COMMA));
        }
        Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after arguments");
        expression =
            new Syntax.Call(expression, arguments, expression.span().cover(closing.span()));
      } else if (match(TokenKind.DOT)) {
        Token name;
        if (check(TokenKind.IDENTIFIER)) {
          name = advance();
        } else {
          error(peek(), "expected member name after '.'");
          name =
              Token.simple(
                  TokenKind.IDENTIFIER, "", SourceSpan.at(source, peek().span().startOffset()));
        }
        expression =
            new Syntax.Member(
                expression, name.lexeme(), name.span(), expression.span().cover(name.span()));
      } else if (match(TokenKind.LEFT_BRACKET)) {
        Syntax.Expression index = parseExpression();
        Token closing = consume(TokenKind.RIGHT_BRACKET, "expected ']' after index");
        expression = new Syntax.Index(expression, index, expression.span().cover(closing.span()));
      } else {
        return expression;
      }
    }
  }

  private Syntax.Expression parsePrimary() {
    if (match(TokenKind.INTEGER)) {
      Token token = previous();
      try {
        return new Syntax.IntegerLiteral(Long.parseLong(token.value()), token.span());
      } catch (NumberFormatException exception) {
        throw error(token, "integer literal is outside the supported range");
      }
    }
    if (match(TokenKind.TRUE, TokenKind.FALSE)) {
      Token token = previous();
      return new Syntax.BooleanLiteral(token.kind() == TokenKind.TRUE, token.span());
    }
    if (match(TokenKind.STRING)) {
      Token token = previous();
      return new Syntax.StringLiteralExpr(token.value(), token.span());
    }
    if (match(TokenKind.IDENTIFIER, TokenKind.ARRAY_TYPE)) {
      Token token = previous();
      return new Syntax.Name(token.lexeme(), token.span());
    }
    if (match(TokenKind.LEFT_PAREN)) {
      Syntax.Expression expression = parseExpression();
      consume(TokenKind.RIGHT_PAREN, "expected ')' after expression");
      return expression;
    }
    if (match(TokenKind.LEFT_BRACKET)) {
      Token opening = previous();
      List<Syntax.Expression> elements = new ArrayList<>();
      if (!check(TokenKind.RIGHT_BRACKET)) {
        do {
          elements.add(parseExpression());
        } while (match(TokenKind.COMMA));
      }
      Token closing = consume(TokenKind.RIGHT_BRACKET, "expected ']' after array literal");
      return new Syntax.ArrayLiteral(elements, opening.span().cover(closing.span()));
    }
    throw error(peek(), "expected expression");
  }

  private boolean looksLikeVariableDeclaration() {
    if (check(TokenKind.INT_TYPE)
        || check(TokenKind.BOOL_TYPE)
        || check(TokenKind.STRING_TYPE)
        || check(TokenKind.ARRAY_TYPE)) {
      return true;
    }
    return check(TokenKind.IDENTIFIER) && checkNext(TokenKind.IDENTIFIER);
  }

  private Token consume(TokenKind kind, String message) {
    if (check(kind)) {
      return advance();
    }
    throw error(peek(), message);
  }

  private ParseError error(Token token, String message) {
    diagnostics.error(
        message.equals("expected expression") ? EXPECTED_EXPRESSION : EXPECTED_TOKEN,
        message,
        token.span());
    return new ParseError();
  }

  private void synchronizeTopLevel() {
    while (!isAtEnd()) {
      if (check(TokenKind.CLASS) || check(TokenKind.ENUM) || isTypeToken(peek().kind())) {
        return;
      }
      advance();
    }
  }

  private void synchronizeStatement() {
    while (!isAtEnd() && !check(TokenKind.RIGHT_BRACE)) {
      if (match(TokenKind.SEMICOLON)) {
        return;
      }
      advance();
    }
  }

  private boolean match(TokenKind... kinds) {
    for (TokenKind kind : kinds) {
      if (check(kind)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenKind kind) {
    return peek().kind() == kind;
  }

  private boolean checkNext(TokenKind kind) {
    return current + 1 < tokens.size() && tokens.get(current + 1).kind() == kind;
  }

  private Token advance() {
    if (!isAtEnd()) {
      current++;
    }
    return previous();
  }

  private boolean isAtEnd() {
    return peek().kind() == TokenKind.END_OF_FILE;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(Math.max(0, current - 1));
  }

  private static boolean isTypeToken(TokenKind kind) {
    return kind == TokenKind.INT_TYPE
        || kind == TokenKind.BOOL_TYPE
        || kind == TokenKind.STRING_TYPE
        || kind == TokenKind.ARRAY_TYPE
        || kind == TokenKind.VOID
        || kind == TokenKind.IDENTIFIER;
  }

  private record Block(List<Syntax.Statement> statements, SourceSpan span) {
    private Block {
      statements = List.copyOf(statements);
    }
  }

  @SuppressWarnings("serial")
  private static final class ParseError extends RuntimeException {
    private ParseError() {
      super(null, null, false, false);
    }
  }
}
