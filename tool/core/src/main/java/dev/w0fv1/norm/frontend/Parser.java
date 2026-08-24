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
    String packageName = parsePackageDeclaration().orElse("");
    List<Syntax.ImportDecl> imports = new ArrayList<>();
    List<Syntax.EnumDecl> enums = new ArrayList<>();
    List<Syntax.ClassDecl> classes = new ArrayList<>();
    List<Syntax.FunctionDecl> functions = new ArrayList<>();
    while (match(TokenKind.IMPORT)) {
      Token start = previous();
      QualifiedName qualifiedName = parseQualifiedNameWithSpan("expected import name");
      Optional<String> alias = Optional.empty();
      Optional<SourceSpan> aliasSpan = Optional.empty();
      if (match(TokenKind.AS)) {
        Token aliasToken = consume(TokenKind.IDENTIFIER, "expected import alias");
        alias = Optional.of(aliasToken.lexeme());
        aliasSpan = Optional.of(aliasToken.span());
      }
      SourceSpan span = start.span().cover(previous().span());
      match(TokenKind.SEMICOLON);
      imports.add(
          new Syntax.ImportDecl(
              qualifiedName.value(), qualifiedName.lastSegmentSpan(), alias, aliasSpan, span));
    }
    while (!isAtEnd()) {
      try {
        Syntax.Visibility visibility = parseVisibility();
        if (match(TokenKind.ENUM)) {
          enums.add(parseEnum(previous(), visibility));
        } else if (match(TokenKind.CLASS)) {
          classes.add(parseClass(previous(), visibility));
        } else {
          functions.add(parseFunction(visibility));
        }
      } catch (ParseError ignored) {
        synchronizeTopLevel();
      }
    }
    return new Syntax.Program(
        packageName,
        imports,
        enums,
        classes,
        functions,
        new SourceSpan(source, 0, source.length()));
  }

  Optional<String> parsePackageDeclaration() {
    if (!match(TokenKind.PACKAGE)) return Optional.empty();
    try {
      String packageName = parseQualifiedName("expected package name");
      match(TokenKind.SEMICOLON);
      return Optional.of(packageName);
    } catch (ParseError ignored) {
      return Optional.empty();
    }
  }

  Optional<Syntax.Expression> parseExpressionDocument() {
    try {
      Syntax.Expression expression = parseExpression();
      match(TokenKind.SEMICOLON);
      consume(TokenKind.END_OF_FILE, "unexpected content after expression");
      return Optional.of(expression);
    } catch (ParseError ignored) {
      return Optional.empty();
    }
  }

  private Syntax.Visibility parseVisibility() {
    if (match(TokenKind.PRIVATE)) return Syntax.Visibility.PRIVATE;
    match(TokenKind.PUBLIC);
    return Syntax.Visibility.PUBLIC;
  }

  private String parseQualifiedName(String message) {
    return parseQualifiedNameWithSpan(message).value();
  }

  private QualifiedName parseQualifiedNameWithSpan(String message) {
    Token segment = consume(TokenKind.IDENTIFIER, message);
    StringBuilder result = new StringBuilder(segment.lexeme());
    while (match(TokenKind.DOT)) {
      segment = consume(TokenKind.IDENTIFIER, message);
      result.append('.').append(segment.lexeme());
    }
    return new QualifiedName(result.toString(), segment.span());
  }

  private Syntax.EnumDecl parseEnum(Token enumKeyword, Syntax.Visibility visibility) {
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
        visibility, name.lexeme(), name.span(), members, enumKeyword.span().cover(closing.span()));
  }

  private Syntax.ClassDecl parseClass(Token classKeyword, Syntax.Visibility visibility) {
    Token name = consume(TokenKind.IDENTIFIER, "expected class name");
    List<Syntax.TypeParameter> typeParameters = parseTypeParameters();
    consume(TokenKind.LEFT_BRACE, "expected '{' before class body");
    List<Syntax.FieldDecl> fields = new ArrayList<>();
    List<Syntax.FunctionDecl> methods = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      Syntax.Visibility memberVisibility = parseVisibility();
      Syntax.TypeRef type = parseType();
      Token memberName = consume(TokenKind.IDENTIFIER, "expected field or method name");
      if (check(TokenKind.LEFT_PAREN) || check(TokenKind.LESS)) {
        methods.add(parseFunctionRest(type, memberName, memberVisibility));
      } else {
        match(TokenKind.SEMICOLON);
        fields.add(
            new Syntax.FieldDecl(
                memberVisibility,
                type,
                memberName.lexeme(),
                memberName.span(),
                type.span().cover(memberName.span())));
      }
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after class body");
    return new Syntax.ClassDecl(
        visibility,
        name.lexeme(),
        name.span(),
        typeParameters,
        fields,
        methods,
        classKeyword.span().cover(closing.span()));
  }

  private Syntax.FunctionDecl parseFunction(Syntax.Visibility visibility) {
    Syntax.TypeRef returnType = parseType();
    Token name = consume(TokenKind.IDENTIFIER, "expected function name");
    return parseFunctionRest(returnType, name, visibility);
  }

  private Syntax.FunctionDecl parseFunctionRest(
      Syntax.TypeRef returnType, Token name, Syntax.Visibility visibility) {
    List<Syntax.TypeParameter> typeParameters = parseTypeParameters();
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
        visibility,
        returnType,
        name.lexeme(),
        name.span(),
        typeParameters,
        parameters,
        block.statements(),
        returnType.span().cover(block.span()));
  }

  private Syntax.TypeRef parseType() {
    if (match(TokenKind.IDENTIFIER)) {
      Token type = previous();
      List<Syntax.TypeRef> arguments = parseTypeArguments();
      SourceSpan span =
          arguments.isEmpty()
              ? type.span()
              : type.span().cover(arguments.getLast().span()).cover(previous().span());
      boolean nullable = match(TokenKind.QUESTION);
      if (nullable) span = span.cover(previous().span());
      return new Syntax.TypeRef(type.lexeme(), arguments, nullable, span);
    }
    throw error(peek(), "expected type name");
  }

  private List<Syntax.TypeParameter> parseTypeParameters() {
    if (!match(TokenKind.LESS)) return List.of();
    List<Syntax.TypeParameter> parameters = new ArrayList<>();
    do {
      Token name = consume(TokenKind.IDENTIFIER, "expected type parameter name");
      parameters.add(new Syntax.TypeParameter(name.lexeme(), name.span()));
    } while (match(TokenKind.COMMA));
    consume(TokenKind.GREATER, "expected '>' after type parameters");
    return List.copyOf(parameters);
  }

  private List<Syntax.TypeRef> parseTypeArguments() {
    if (!match(TokenKind.LESS)) return List.of();
    List<Syntax.TypeRef> arguments = new ArrayList<>();
    do {
      arguments.add(parseType());
    } while (match(TokenKind.COMMA));
    consume(TokenKind.GREATER, "expected '>' after type arguments");
    return List.copyOf(arguments);
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
    Token closing = peek();
    if (isAtEnd()) {
      diagnostics.error(EXPECTED_TOKEN, "expected '}' after block", closing.span());
    } else {
      closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after block");
    }
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

  private Syntax.Statement parseFor(Token keyword) {
    boolean inferredIteration = check(TokenKind.IDENTIFIER) && checkNext(TokenKind.COLON);
    int afterType = tokenAfterType(current);
    boolean explicitIteration =
        afterType >= 0
            && afterType + 1 < tokens.size()
            && tokens.get(afterType).kind() == TokenKind.IDENTIFIER
            && tokens.get(afterType + 1).kind() == TokenKind.COLON;
    if (!inferredIteration && !explicitIteration) {
      Syntax.Expression condition = parseExpression();
      Block body = parseBlock();
      return new Syntax.ConditionalForStatement(
          condition, body.statements(), keyword.span().cover(body.span()));
    }
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
    return parseCoalescing();
  }

  private Syntax.Expression parseCoalescing() {
    Syntax.Expression expression = parseOr();
    if (match(TokenKind.QUESTION_QUESTION)) {
      Token operator = previous();
      Syntax.Expression right = parseCoalescing();
      return new Syntax.Binary(
          expression, operator.kind(), right, expression.span().cover(right.span()));
    }
    return expression;
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
      } else if (match(TokenKind.DOT, TokenKind.QUESTION_DOT)) {
        boolean nullSafe = previous().kind() == TokenKind.QUESTION_DOT;
        Token name;
        if (check(TokenKind.IDENTIFIER)) {
          name = advance();
        } else {
          error(peek(), "expected member name after '.'");
          name =
              Token.simple(
                  TokenKind.IDENTIFIER, "", SourceSpan.at(source, peek().span().startOffset()));
        }
        List<Syntax.TypeRef> typeArguments =
            check(TokenKind.LESS) && looksLikeTypeApplication() ? parseTypeArguments() : List.of();
        SourceSpan memberSpan =
            typeArguments.isEmpty()
                ? expression.span().cover(name.span())
                : expression.span().cover(previous().span());
        expression =
            new Syntax.Member(
                expression, name.lexeme(), name.span(), typeArguments, nullSafe, memberSpan);
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
    if (match(TokenKind.NULL)) {
      return new Syntax.NullLiteral(previous().span());
    }
    if (match(TokenKind.CODE_POINT)) {
      Token token = previous();
      return new Syntax.CodePointLiteral(Integer.parseInt(token.value()), token.span());
    }
    if (match(TokenKind.TRUE, TokenKind.FALSE)) {
      Token token = previous();
      return new Syntax.BooleanLiteral(token.kind() == TokenKind.TRUE, token.span());
    }
    if (match(TokenKind.STRING)) {
      Token token = previous();
      return new Syntax.StringLiteralExpr(token.value(), token.span());
    }
    if (match(TokenKind.IDENTIFIER)) {
      Token token = previous();
      List<Syntax.TypeRef> typeArguments =
          check(TokenKind.LESS) && looksLikeTypeApplication() ? parseTypeArguments() : List.of();
      SourceSpan span =
          typeArguments.isEmpty() ? token.span() : token.span().cover(previous().span());
      return new Syntax.Name(token.lexeme(), typeArguments, span);
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
    if (!check(TokenKind.IDENTIFIER)) return false;
    int next = tokenAfterType(current);
    return next >= 0 && next < tokens.size() && tokens.get(next).kind() == TokenKind.IDENTIFIER;
  }

  private boolean looksLikeTypeApplication() {
    int next = tokenAfterType(current - 1);
    return next >= 0 && next < tokens.size() && tokens.get(next).kind() == TokenKind.LEFT_PAREN;
  }

  private int tokenAfterType(int start) {
    if (start < 0 || start >= tokens.size() || !isTypeToken(tokens.get(start).kind())) return -1;
    int index = start + 1;
    if (index >= tokens.size() || tokens.get(index).kind() != TokenKind.LESS) {
      return skipNullable(index);
    }
    int depth = 0;
    for (; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LESS) {
        depth++;
      } else if (kind == TokenKind.GREATER) {
        depth--;
        if (depth == 0) return skipNullable(index + 1);
      } else if (kind != TokenKind.COMMA && kind != TokenKind.QUESTION && !isTypeToken(kind)) {
        return -1;
      }
    }
    return -1;
  }

  private int skipNullable(int index) {
    return index < tokens.size() && tokens.get(index).kind() == TokenKind.QUESTION
        ? index + 1
        : index;
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
    return kind == TokenKind.IDENTIFIER;
  }

  private record Block(List<Syntax.Statement> statements, SourceSpan span) {
    private Block {
      statements = List.copyOf(statements);
    }
  }

  private record QualifiedName(String value, SourceSpan lastSegmentSpan) {}

  @SuppressWarnings("serial")
  private static final class ParseError extends RuntimeException {
    private ParseError() {
      super(null, null, false, false);
    }
  }
}
