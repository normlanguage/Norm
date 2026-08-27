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
  private static final DiagnosticCode CHAINED_RELATIONAL_OPERATOR =
      new DiagnosticCode("NORM-PARSER-0004");

  private final SourceFile source;
  private final List<Token> tokens;
  private final DiagnosticBag diagnostics;
  private final CompilationGuard guard;
  private int current;

  Parser(SourceFile source, List<Token> tokens, DiagnosticBag diagnostics) {
    this(source, tokens, diagnostics, CompilationGuard.unlimited());
  }

  Parser(SourceFile source, List<Token> tokens, DiagnosticBag diagnostics, CompilationGuard guard) {
    this.source = Objects.requireNonNull(source, "source");
    this.tokens = List.copyOf(tokens);
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    this.guard = Objects.requireNonNull(guard, "guard");
    if (tokens.isEmpty() || tokens.getLast().kind() != TokenKind.END_OF_FILE) {
      throw new IllegalArgumentException("token stream must end with END_OF_FILE");
    }
  }

  Syntax.Program parse() {
    String packageName = parsePackageDeclaration().orElse("");
    List<Syntax.ImportDecl> imports = new ArrayList<>();
    List<Syntax.EnumDecl> enums = new ArrayList<>();
    List<Syntax.InterfaceDecl> interfaces = new ArrayList<>();
    List<Syntax.AggregateDecl> aggregates = new ArrayList<>();
    List<Syntax.FunctionDecl> functions = new ArrayList<>();
    while (match(TokenKind.IMPORT)) {
      Token start = previous();
      QualifiedName qualifiedName = parseQualifiedNameWithSpan("expected import name");
      Optional<String> alias = Optional.empty();
      Optional<SourceSpan> aliasSpan = Optional.empty();
      if (match(TokenKind.AS)) {
        Token aliasToken = consume(TokenKind.IDENTIFIER, "expected import alias");
        alias = Optional.of(aliasToken.value());
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
        } else if (match(TokenKind.INTERFACE)) {
          interfaces.add(parseInterface(previous(), visibility));
        } else if (match(TokenKind.CLASS)) {
          aggregates.add(parseAggregate(previous(), visibility, Syntax.AggregateKind.CLASS));
        } else if (matchValueDeclarationKeyword()) {
          aggregates.add(parseAggregate(previous(), visibility, Syntax.AggregateKind.VALUE));
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
        interfaces,
        aggregates,
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
    StringBuilder result = new StringBuilder(segment.value());
    while (match(TokenKind.DOT)) {
      segment = consume(TokenKind.IDENTIFIER, message);
      result.append('.').append(segment.value());
    }
    return new QualifiedName(result.toString(), segment.span());
  }

  private Syntax.EnumDecl parseEnum(Token enumKeyword, Syntax.Visibility visibility) {
    Token name = consume(TokenKind.IDENTIFIER, "expected enum name");
    List<Syntax.TypeParameter> typeParameters = parseTypeParameters();
    consume(TokenKind.LEFT_BRACE, "expected '{' before enum body");
    List<Syntax.EnumVariant> variants = new ArrayList<>();
    if (!check(TokenKind.RIGHT_BRACE)) {
      do {
        Token variant = consume(TokenKind.IDENTIFIER, "expected enum variant");
        List<Syntax.Parameter> parameters = List.of();
        SourceSpan variantSpan = variant.span();
        if (match(TokenKind.LEFT_PAREN)) {
          parameters = parseParameterList();
          Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after enum data");
          variantSpan = variant.span().cover(closing.span());
        }
        variants.add(
            new Syntax.EnumVariant(variant.value(), variant.span(), parameters, variantSpan));
      } while (match(TokenKind.COMMA) && !check(TokenKind.RIGHT_BRACE));
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after enum body");
    match(TokenKind.SEMICOLON);
    return new Syntax.EnumDecl(
        visibility,
        name.value(),
        name.span(),
        typeParameters,
        variants,
        enumKeyword.span().cover(closing.span()));
  }

  private Syntax.AggregateDecl parseAggregate(
      Token keyword, Syntax.Visibility visibility, Syntax.AggregateKind kind) {
    String declarationKind = kind.keyword();
    Token name = consume(TokenKind.IDENTIFIER, "expected " + declarationKind + " name");
    List<Syntax.TypeParameter> typeParameters = parseTypeParameters();
    Optional<Syntax.TypeRef> extendedClass =
        match(TokenKind.EXTENDS) ? Optional.of(parseType()) : Optional.empty();
    List<Syntax.TypeRef> implementedInterfaces =
        match(TokenKind.IMPLEMENTS) ? parseTypeList() : List.of();
    consume(TokenKind.LEFT_BRACE, "expected '{' before " + declarationKind + " body");
    List<Syntax.FieldDecl> fields = new ArrayList<>();
    List<Syntax.ConstructorDecl> constructors = new ArrayList<>();
    List<Syntax.FunctionDecl> methods = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      boolean hasExplicitVisibility = check(TokenKind.PUBLIC) || check(TokenKind.PRIVATE);
      Syntax.Visibility memberVisibility = parseVisibility();
      SourceSpan visibilitySpan = hasExplicitVisibility ? previous().span() : null;
      if (check(TokenKind.IDENTIFIER)
          && peek().value().equals(name.value())
          && checkNext(TokenKind.LEFT_PAREN)) {
        if (visibilitySpan != null) {
          diagnostics.error(EXPECTED_TOKEN, "constructor visibility is implicit", visibilitySpan);
        }
        constructors.add(parseConstructor(name.value()));
        continue;
      }
      if (looksLikeOmittedReturnFunction()) {
        Token memberName = consume(TokenKind.IDENTIFIER, "expected method name");
        methods.add(parseFunctionRest(Optional.empty(), memberName, memberVisibility));
        continue;
      }
      Syntax.TypeRef type = parseType();
      Token memberName = consume(TokenKind.IDENTIFIER, "expected field or method name");
      if (check(TokenKind.LEFT_PAREN) || check(TokenKind.LESS)) {
        methods.add(parseFunctionRest(Optional.of(type), memberName, memberVisibility));
      } else {
        match(TokenKind.SEMICOLON);
        fields.add(
            new Syntax.FieldDecl(
                memberVisibility,
                type,
                memberName.value(),
                memberName.span(),
                type.span().cover(memberName.span())));
      }
    }
    Token closing =
        consume(TokenKind.RIGHT_BRACE, "expected '}' after " + declarationKind + " body");
    return new Syntax.AggregateDecl(
        kind,
        visibility,
        name.value(),
        name.span(),
        typeParameters,
        extendedClass,
        implementedInterfaces,
        fields,
        constructors,
        methods,
        keyword.span().cover(closing.span()));
  }

  private Syntax.ConstructorDecl parseConstructor(String ownerName) {
    Token name = consume(TokenKind.IDENTIFIER, "expected constructor name");
    consume(TokenKind.LEFT_PAREN, "expected '(' after constructor name");
    List<Syntax.Parameter> parameters = parseParameterList();
    consume(TokenKind.RIGHT_PAREN, "expected ')' after constructor parameters");
    Token opening = consume(TokenKind.LEFT_BRACE, "expected '{' before constructor body");
    Optional<Syntax.SuperCall> superCall = Optional.empty();
    if (match(TokenKind.SUPER)) {
      Token keyword = previous();
      consume(TokenKind.LEFT_PAREN, "expected '(' after super");
      List<Syntax.CallArgument> arguments = parseCallArguments();
      Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after super arguments");
      match(TokenKind.SEMICOLON);
      superCall =
          Optional.of(new Syntax.SuperCall(arguments, keyword.span().cover(closing.span())));
    }
    List<Syntax.Statement> statements = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      try {
        statements.add(parseStatement());
      } catch (ParseError error) {
        synchronizeStatement(error);
      }
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after constructor body");
    return new Syntax.ConstructorDecl(
        ownerName,
        name.span(),
        parameters,
        superCall,
        statements,
        name.span().cover(opening.span()).cover(closing.span()));
  }

  private Syntax.InterfaceDecl parseInterface(
      Token interfaceKeyword, Syntax.Visibility visibility) {
    Token name = consume(TokenKind.IDENTIFIER, "expected interface name");
    List<Syntax.TypeParameter> typeParameters = parseTypeParameters();
    List<Syntax.TypeRef> extendedInterfaces =
        match(TokenKind.EXTENDS) ? parseTypeList() : List.of();
    consume(TokenKind.LEFT_BRACE, "expected '{' before interface body");
    List<Syntax.InterfaceMethodDecl> methods = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      if (match(TokenKind.PUBLIC, TokenKind.PRIVATE)) {
        diagnostics.error(
            EXPECTED_TOKEN, "interface method visibility is implicit", previous().span());
      }
      Syntax.TypeRef returnType = parseType();
      Token methodName = consume(TokenKind.IDENTIFIER, "expected interface method name");
      List<Syntax.TypeParameter> methodTypeParameters = parseTypeParameters();
      if (!match(TokenKind.LEFT_PAREN)) {
        diagnostics.error(EXPECTED_TOKEN, "interfaces may declare methods only", methodName.span());
        match(TokenKind.SEMICOLON);
        continue;
      }
      List<Syntax.Parameter> parameters = parseParameterList();
      Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after parameters");
      SourceSpan span = returnType.span().cover(closing.span());
      Optional<List<Syntax.Statement>> defaultBody = Optional.empty();
      if (check(TokenKind.LEFT_BRACE)) {
        Block body = parseBlock();
        defaultBody = Optional.of(body.statements());
        span = returnType.span().cover(body.span());
      } else {
        match(TokenKind.SEMICOLON);
      }
      methods.add(
          new Syntax.InterfaceMethodDecl(
              returnType,
              methodName.value(),
              methodName.span(),
              methodTypeParameters,
              parameters,
              defaultBody,
              span));
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after interface body");
    return new Syntax.InterfaceDecl(
        visibility,
        name.value(),
        name.span(),
        typeParameters,
        extendedInterfaces,
        methods,
        interfaceKeyword.span().cover(closing.span()));
  }

  private List<Syntax.TypeRef> parseTypeList() {
    List<Syntax.TypeRef> types = new ArrayList<>();
    do {
      types.add(parseType());
    } while (match(TokenKind.COMMA));
    return List.copyOf(types);
  }

  private Syntax.FunctionDecl parseFunction(Syntax.Visibility visibility) {
    if (looksLikeOmittedReturnFunction()) {
      Token name = consume(TokenKind.IDENTIFIER, "expected function name");
      return parseFunctionRest(Optional.empty(), name, visibility);
    }
    Syntax.TypeRef returnType = parseType();
    Token name = consume(TokenKind.IDENTIFIER, "expected function name");
    return parseFunctionRest(Optional.of(returnType), name, visibility);
  }

  private Syntax.FunctionDecl parseFunctionRest(
      Optional<Syntax.TypeRef> returnType, Token name, Syntax.Visibility visibility) {
    List<Syntax.TypeParameter> typeParameters = parseTypeParameters();
    consume(TokenKind.LEFT_PAREN, "expected '(' after function name");
    List<Syntax.Parameter> parameters = parseParameterList();
    consume(TokenKind.RIGHT_PAREN, "expected ')' after parameters");
    Block block = parseBlock();
    return new Syntax.FunctionDecl(
        visibility,
        returnType,
        name.value(),
        name.span(),
        typeParameters,
        parameters,
        block.statements(),
        returnType.map(Syntax.TypeRef::span).orElse(name.span()).cover(block.span()));
  }

  private boolean looksLikeOmittedReturnFunction() {
    if (!check(TokenKind.IDENTIFIER) || current + 1 >= tokens.size()) return false;
    if (tokens.get(current + 1).kind() == TokenKind.LEFT_PAREN) return true;
    if (tokens.get(current + 1).kind() != TokenKind.LESS) return false;
    int depth = 0;
    for (int index = current + 1; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LESS) depth++;
      if (kind == TokenKind.GREATER && --depth == 0) {
        return index + 1 < tokens.size() && tokens.get(index + 1).kind() == TokenKind.LEFT_PAREN;
      }
    }
    return false;
  }

  private List<Syntax.Parameter> parseParameterList() {
    List<Syntax.Parameter> parameters = new ArrayList<>();
    if (!check(TokenKind.RIGHT_PAREN)) {
      do {
        Syntax.TypeRef type = parseType();
        Token parameterName = consume(TokenKind.IDENTIFIER, "expected parameter name");
        Optional<List<Syntax.Parameter>> callableParameters = Optional.empty();
        if (match(TokenKind.LEFT_PAREN)) {
          List<Syntax.Parameter> signature = parseParameterList();
          Token closing =
              consume(TokenKind.RIGHT_PAREN, "expected ')' after function parameter signature");
          List<Syntax.TypeRef> arguments = new ArrayList<>();
          arguments.add(type);
          signature.forEach(parameter -> arguments.add(parameter.type()));
          type =
              new Syntax.TypeRef("Function", arguments, false, type.span().cover(closing.span()));
          callableParameters = Optional.of(signature);
        }
        parameters.add(
            new Syntax.Parameter(
                type,
                parameterName.value(),
                parameterName.span(),
                callableParameters,
                type.span().cover(parameterName.span())));
      } while (match(TokenKind.COMMA));
    }
    return List.copyOf(parameters);
  }

  private Syntax.TypeRef parseType() {
    if (match(TokenKind.IDENTIFIER, TokenKind.REF)) {
      Token type = previous();
      List<Syntax.TypeRef> arguments =
          type.value().equals("Function") && check(TokenKind.LESS)
              ? parseFunctionTypeArguments()
              : parseTypeArguments();
      SourceSpan span =
          arguments.isEmpty()
              ? type.span()
              : type.span().cover(arguments.getLast().span()).cover(previous().span());
      boolean nullable = match(TokenKind.QUESTION);
      if (nullable) span = span.cover(previous().span());
      return new Syntax.TypeRef(type.value(), arguments, nullable, span);
    }
    throw error(peek(), "expected type name");
  }

  private List<Syntax.TypeRef> parseFunctionTypeArguments() {
    consume(TokenKind.LESS, "expected '<' after Function");
    List<Syntax.TypeRef> arguments = new ArrayList<>();
    arguments.add(parseType());
    consume(TokenKind.LEFT_PAREN, "expected '(' after function return type");
    if (!check(TokenKind.RIGHT_PAREN)) {
      do {
        arguments.add(parseType());
      } while (match(TokenKind.COMMA));
    }
    consume(TokenKind.RIGHT_PAREN, "expected ')' after function parameter types");
    consume(TokenKind.GREATER, "expected '>' after function type");
    return List.copyOf(arguments);
  }

  private List<Syntax.TypeParameter> parseTypeParameters() {
    if (!match(TokenKind.LESS)) return List.of();
    List<Syntax.TypeParameter> parameters = new ArrayList<>();
    do {
      Token name = consume(TokenKind.IDENTIFIER, "expected type parameter name");
      Optional<Syntax.TypeRef> upperBound = Optional.empty();
      if (match(TokenKind.EXTENDS)) upperBound = Optional.of(parseType());
      parameters.add(new Syntax.TypeParameter(name.value(), name.span(), upperBound));
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
      } catch (ParseError error) {
        synchronizeStatement(error);
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
    if (match(TokenKind.TRY)) {
      return parseTry(previous());
    }
    if (match(TokenKind.THROW)) {
      Token keyword = previous();
      Syntax.Expression exception = parseExpression();
      match(TokenKind.SEMICOLON);
      return new Syntax.ThrowStatement(exception, keyword.span().cover(exception.span()));
    }
    if (match(TokenKind.RETURN)) {
      return parseReturn(previous());
    }
    if (match(TokenKind.BREAK)) {
      Token keyword = previous();
      Syntax.Expression value = null;
      if (!check(TokenKind.RIGHT_BRACE) && !check(TokenKind.SEMICOLON)) {
        value = parseExpression();
      }
      match(TokenKind.SEMICOLON);
      return new Syntax.BreakStatement(
          value, value == null ? keyword.span() : keyword.span().cover(value.span()));
    }
    if (match(TokenKind.CONTINUE)) {
      Token keyword = previous();
      match(TokenKind.SEMICOLON);
      return new Syntax.ContinueStatement(keyword.span());
    }
    if (looksLikeCallableBinding()) {
      return parseCallableBinding();
    }
    if (looksLikeVariableDeclaration()) {
      return parseVariableDeclaration();
    }

    Syntax.Expression expression = parseExpression();
    if (match(TokenKind.EQUAL)) {
      if (!(expression instanceof Syntax.Name
          || expression instanceof Syntax.Member
          || expression instanceof Syntax.Index
          || expression instanceof Syntax.Unary unary && unary.operator() == TokenKind.STAR)) {
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
    Token start = peek();
    Optional<Syntax.TypeRef> type =
        match(TokenKind.VAR) ? Optional.empty() : Optional.of(parseType());
    Token name = consume(TokenKind.IDENTIFIER, "expected variable name");
    consume(TokenKind.EQUAL, "variables must have an initializer");
    Syntax.Expression initializer = parseExpression();
    match(TokenKind.SEMICOLON);
    return new Syntax.VariableDecl(
        type, name.value(), name.span(), initializer, start.span().cover(initializer.span()));
  }

  private boolean looksLikeCallableBinding() {
    if (!isTypeToken(peek().kind())) return false;
    int nameIndex = tokenAfterType(current);
    if (nameIndex < 0
        || nameIndex + 1 >= tokens.size()
        || tokens.get(nameIndex).kind() != TokenKind.IDENTIFIER
        || tokens.get(nameIndex + 1).kind() != TokenKind.LEFT_PAREN) {
      return false;
    }
    int depth = 0;
    for (int index = nameIndex + 1; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LEFT_PAREN) depth++;
      if (kind == TokenKind.RIGHT_PAREN && --depth == 0) {
        return index + 1 < tokens.size() && tokens.get(index + 1).kind() == TokenKind.EQUAL;
      }
    }
    return false;
  }

  private Syntax.Statement parseCallableBinding() {
    Token start = peek();
    Syntax.TypeRef returnType = parseType();
    Token name = consume(TokenKind.IDENTIFIER, "expected callable binding name");
    consume(TokenKind.LEFT_PAREN, "expected '('");
    List<Syntax.Parameter> parameters = parseParameterList();
    Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after callable signature");
    consume(TokenKind.EQUAL, "callable binding requires a function value");
    Syntax.Expression initializer = parseExpression();
    match(TokenKind.SEMICOLON);
    List<Syntax.TypeRef> signature = new ArrayList<>();
    signature.add(returnType);
    parameters.forEach(parameter -> signature.add(parameter.type()));
    Syntax.TypeRef functionType =
        new Syntax.TypeRef("Function", signature, false, returnType.span().cover(closing.span()));
    return new Syntax.VariableDecl(
        Optional.of(functionType),
        name.value(),
        name.span(),
        Optional.of(parameters),
        initializer,
        start.span().cover(initializer.span()));
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
    boolean inferredIteration =
        check(TokenKind.IDENTIFIER)
            && (checkNext(TokenKind.COLON)
                || current + 3 < tokens.size()
                    && tokens.get(current + 1).kind() == TokenKind.COMMA
                    && tokens.get(current + 2).kind() == TokenKind.IDENTIFIER
                    && tokens.get(current + 3).kind() == TokenKind.COLON);
    int afterType = tokenAfterType(current);
    boolean explicitIteration =
        afterType >= 0
            && afterType + 1 < tokens.size()
            && tokens.get(afterType).kind() == TokenKind.IDENTIFIER
            && (tokens.get(afterType + 1).kind() == TokenKind.COLON
                || afterType + 3 < tokens.size()
                    && tokens.get(afterType + 1).kind() == TokenKind.COMMA
                    && tokens.get(afterType + 2).kind() == TokenKind.IDENTIFIER
                    && tokens.get(afterType + 3).kind() == TokenKind.COLON);
    if (!inferredIteration && !explicitIteration) {
      Syntax.Expression condition = parseExpression();
      Block body = parseBlock();
      return new Syntax.ConditionalForStatement(
          condition, body.statements(), keyword.span().cover(body.span()));
    }
    Optional<Syntax.TypeRef> type;
    Token name;
    if (inferredIteration) {
      type = Optional.empty();
      name = advance();
    } else {
      type = Optional.of(parseType());
      name = consume(TokenKind.IDENTIFIER, "expected loop variable name");
    }
    Optional<Syntax.ForIndex> index = Optional.empty();
    if (match(TokenKind.COMMA)) {
      Token indexName = consume(TokenKind.IDENTIFIER, "expected loop index name");
      index = Optional.of(new Syntax.ForIndex(indexName.value(), indexName.span()));
    }
    consume(TokenKind.COLON, "expected ':' after loop variable");
    Syntax.Expression iterable = parseExpression();
    Block body = parseBlock();
    return new Syntax.ForStatement(
        type,
        name.value(),
        name.span(),
        index,
        iterable,
        body.statements(),
        keyword.span().cover(body.span()));
  }

  private Syntax.TryStatement parseTry(Token keyword) {
    Block body = parseBlock();
    List<Syntax.CatchClause> catches = new ArrayList<>();
    SourceSpan end = body.span();
    while (match(TokenKind.CATCH)) {
      Token catchKeyword = previous();
      Syntax.TypeRef type = parseType();
      Token name = consume(TokenKind.IDENTIFIER, "expected catch binding name");
      Block catchBody = parseBlock();
      end = catchBody.span();
      catches.add(
          new Syntax.CatchClause(
              type,
              name.value(),
              name.span(),
              catchBody.statements(),
              catchKeyword.span().cover(catchBody.span())));
    }
    Optional<Syntax.FinallyClause> finallyClause = Optional.empty();
    if (match(TokenKind.FINALLY)) {
      Token finallyKeyword = previous();
      Block finallyBody = parseBlock();
      end = finallyBody.span();
      finallyClause =
          Optional.of(
              new Syntax.FinallyClause(
                  finallyBody.statements(), finallyKeyword.span().cover(finallyBody.span())));
    }
    if (catches.isEmpty() && finallyClause.isEmpty()) {
      diagnostics.error(EXPECTED_TOKEN, "try requires at least one catch or finally", body.span());
    }
    return new Syntax.TryStatement(
        body.statements(), catches, finallyClause, keyword.span().cover(end));
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
    if (match(TokenKind.EQUAL_EQUAL, TokenKind.BANG_EQUAL)) {
      Token operator = previous();
      Syntax.Expression right = parseComparison();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
      rejectRelationalChain(TokenKind.EQUAL_EQUAL, TokenKind.BANG_EQUAL);
    }
    return expression;
  }

  private Syntax.Expression parseComparison() {
    Syntax.Expression expression = parseTerm();
    if (match(TokenKind.LESS, TokenKind.LESS_EQUAL, TokenKind.GREATER, TokenKind.GREATER_EQUAL)) {
      Token operator = previous();
      Syntax.Expression right = parseTerm();
      expression =
          new Syntax.Binary(
              expression, operator.kind(), right, expression.span().cover(right.span()));
      rejectRelationalChain(
          TokenKind.LESS, TokenKind.LESS_EQUAL, TokenKind.GREATER, TokenKind.GREATER_EQUAL);
    }
    return expression;
  }

  private void rejectRelationalChain(TokenKind... kinds) {
    if (java.util.Arrays.stream(kinds).noneMatch(this::check)) return;
    diagnostics.error(
        CHAINED_RELATIONAL_OPERATOR,
        "equality and comparison operators cannot be chained; use parentheses",
        peek().span());
    throw new ParseError(peek().span().start().line());
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
    if (match(TokenKind.BANG, TokenKind.MINUS, TokenKind.STAR, TokenKind.AMPERSAND)) {
      Token operator = previous();
      Syntax.Expression operand = parseUnary();
      if (operator.kind() == TokenKind.MINUS && operand instanceof Syntax.IntegerLiteral integer) {
        return new Syntax.IntegerLiteral(
            integer.value().negate(), operator.span().cover(integer.span()));
      }
      if (operator.kind() == TokenKind.MINUS && operand instanceof Syntax.DecimalLiteral decimal) {
        return new Syntax.DecimalLiteral(
            decimal.value().negate(), operator.span().cover(decimal.span()));
      }
      return new Syntax.Unary(operator.kind(), operand, operator.span().cover(operand.span()));
    }
    return parsePostfix();
  }

  private Syntax.Expression parsePostfix() {
    Syntax.Expression expression = parsePrimary();
    while (true) {
      if (match(TokenKind.LEFT_PAREN)) {
        List<Syntax.CallArgument> arguments = parseCallArguments();
        Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after arguments");
        expression =
            new Syntax.Call(expression, arguments, expression.span().cover(closing.span()));
      } else if (match(TokenKind.COLON_COLON)) {
        Token name = consume(TokenKind.IDENTIFIER, "expected method name after '::'");
        expression =
            new Syntax.MethodReference(
                expression, name.value(), name.span(), expression.span().cover(name.span()));
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
                expression, name.value(), name.span(), typeArguments, nullSafe, memberSpan);
      } else if (match(TokenKind.LEFT_BRACKET)) {
        Syntax.Expression index = parseExpression();
        Token closing = consume(TokenKind.RIGHT_BRACKET, "expected ']' after index");
        expression = new Syntax.Index(expression, index, expression.span().cover(closing.span()));
      } else {
        break;
      }
    }
    return expression;
  }

  private List<Syntax.CallArgument> parseCallArguments() {
    List<Syntax.CallArgument> arguments = new ArrayList<>();
    if (!check(TokenKind.RIGHT_PAREN)) {
      do {
        Optional<Syntax.ArgumentLabel> label = Optional.empty();
        Token argumentStart = peek();
        if (check(TokenKind.IDENTIFIER) && checkNext(TokenKind.COLON)) {
          Token name = advance();
          label = Optional.of(new Syntax.ArgumentLabel(name.value(), name.span()));
          advance();
        }
        Syntax.Expression value = parseExpression();
        arguments.add(
            new Syntax.CallArgument(label, value, argumentStart.span().cover(value.span())));
      } while (match(TokenKind.COMMA));
    }
    return List.copyOf(arguments);
  }

  private Syntax.Expression parsePrimary() {
    if (looksLikeSelfTypedLambda()) {
      Syntax.TypeRef returnType = parseType();
      return parseLambda(Optional.of(returnType));
    }
    if (check(TokenKind.LEFT_PAREN) && looksLikeLambda()) {
      return parseLambda(Optional.empty());
    }
    if (match(TokenKind.SWITCH)) {
      return parseSwitch(previous());
    }
    if (match(TokenKind.INTEGER)) {
      Token token = previous();
      return new Syntax.IntegerLiteral(integerValue(token), token.span());
    }
    if (match(TokenKind.DECIMAL)) {
      Token token = previous();
      return new Syntax.DecimalLiteral(decimalValue(token), token.span());
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
      boolean diamond = check(TokenKind.LESS) && checkNext(TokenKind.GREATER);
      List<Syntax.TypeRef> typeArguments =
          diamond
              ? parseDiamond()
              : check(TokenKind.LESS) && looksLikeTypeApplication()
                  ? parseTypeArguments()
                  : List.of();
      SourceSpan span =
          typeArguments.isEmpty() && !diamond
              ? token.span()
              : token.span().cover(previous().span());
      return new Syntax.Name(token.value(), typeArguments, diamond, span);
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

  private boolean looksLikeLambda() {
    int depth = 0;
    for (int index = current; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LEFT_PAREN) depth++;
      if (kind == TokenKind.RIGHT_PAREN && --depth == 0) {
        return index + 1 < tokens.size()
            && tokens.get(index + 1).kind() == TokenKind.LEFT_BRACE
            && looksLikeLambdaParameters(current + 1, index);
      }
      if (kind == TokenKind.END_OF_FILE) return false;
    }
    return false;
  }

  private boolean looksLikeLambdaParameters(int start, int end) {
    int index = start;
    if (index == end) return true;
    while (index < end) {
      if (tokens.get(index).kind() != TokenKind.IDENTIFIER) return false;
      int afterName = index + 1;
      if (afterName < end && tokens.get(afterName).kind() != TokenKind.COMMA) {
        int name = tokenAfterType(index);
        if (name < 0 || name >= end || tokens.get(name).kind() != TokenKind.IDENTIFIER) {
          return false;
        }
        afterName = name + 1;
      }
      if (afterName == end) return true;
      if (tokens.get(afterName).kind() != TokenKind.COMMA) return false;
      index = afterName + 1;
    }
    return false;
  }

  private boolean looksLikeSelfTypedLambda() {
    if (!check(TokenKind.IDENTIFIER)
        || peek().value().isEmpty()
        || !Character.isUpperCase(peek().value().codePointAt(0))
        || !checkNext(TokenKind.LEFT_PAREN)) {
      return false;
    }
    int depth = 0;
    for (int index = current + 1; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LEFT_PAREN) depth++;
      if (kind == TokenKind.RIGHT_PAREN && --depth == 0) {
        return index + 1 < tokens.size()
            && tokens.get(index + 1).kind() == TokenKind.LEFT_BRACE
            && looksLikeLambdaParameters(current + 2, index);
      }
    }
    return false;
  }

  private Syntax.Expression parseLambda(Optional<Syntax.TypeRef> returnType) {
    Token opening = consume(TokenKind.LEFT_PAREN, "expected '('");
    List<Syntax.LambdaParameter> parameters = new ArrayList<>();
    if (!check(TokenKind.RIGHT_PAREN)) {
      do {
        Optional<Syntax.TypeRef> type = Optional.empty();
        Token name;
        if (check(TokenKind.IDENTIFIER) && checkNext(TokenKind.IDENTIFIER)) {
          type = Optional.of(parseType());
          name = consume(TokenKind.IDENTIFIER, "expected lambda parameter name");
        } else {
          name = consume(TokenKind.IDENTIFIER, "expected lambda parameter name");
        }
        SourceSpan span = type.map(value -> value.span().cover(name.span())).orElse(name.span());
        parameters.add(new Syntax.LambdaParameter(type, name.value(), name.span(), span));
      } while (match(TokenKind.COMMA));
    }
    consume(TokenKind.RIGHT_PAREN, "expected ')' after lambda parameters");
    Block block = parseBlock();
    return new Syntax.Lambda(
        returnType, parameters, block.statements(), opening.span().cover(block.span()));
  }

  private boolean looksLikeVariableDeclaration() {
    if (check(TokenKind.VAR)) return true;
    if (!isTypeToken(peek().kind())) return false;
    int next = tokenAfterType(current);
    return next >= 0 && next < tokens.size() && tokens.get(next).kind() == TokenKind.IDENTIFIER;
  }

  private List<Syntax.TypeRef> parseDiamond() {
    consume(TokenKind.LESS, "expected '<'");
    consume(TokenKind.GREATER, "expected '>' after diamond");
    return List.of();
  }

  private boolean looksLikeTypeApplication() {
    int next = tokenAfterType(current - 1);
    return next >= 0
        && next < tokens.size()
        && (tokens.get(next).kind() == TokenKind.LEFT_PAREN
            || tokens.get(next).kind() == TokenKind.DOT);
  }

  private Syntax.SwitchExpression parseSwitch(Token keyword) {
    Syntax.Expression value = parseExpression();
    consume(TokenKind.LEFT_BRACE, "expected '{' before switch cases");
    List<Syntax.SwitchCase> cases = new ArrayList<>();
    while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
      Token caseKeyword = consume(TokenKind.CASE, "expected 'case' in switch");
      Syntax.Pattern pattern = parsePattern();
      Block body = parseBlock();
      cases.add(
          new Syntax.SwitchCase(pattern, body.statements(), caseKeyword.span().cover(body.span())));
    }
    Token closing = consume(TokenKind.RIGHT_BRACE, "expected '}' after switch");
    return new Syntax.SwitchExpression(value, cases, keyword.span().cover(closing.span()));
  }

  private Syntax.Pattern parsePattern() {
    if (match(TokenKind.NULL)) return new Syntax.NullPattern(previous().span());
    boolean negative = match(TokenKind.MINUS);
    Token sign = negative ? previous() : null;
    if (match(TokenKind.INTEGER)) {
      Token token = previous();
      java.math.BigInteger value = integerValue(token);
      return new Syntax.IntegerPattern(
          negative ? value.negate() : value,
          negative ? sign.span().cover(token.span()) : token.span());
    }
    if (match(TokenKind.DECIMAL)) {
      Token token = previous();
      java.math.BigDecimal value = decimalValue(token);
      return new Syntax.DecimalPattern(
          negative ? value.negate() : value,
          negative ? sign.span().cover(token.span()) : token.span());
    }
    if (negative) {
      throw error(peek(), "expected numeric literal after '-'");
    }
    if (match(TokenKind.CODE_POINT)) {
      Token token = previous();
      return new Syntax.CodePointPattern(Integer.parseInt(token.value()), token.span());
    }
    if (match(TokenKind.TRUE, TokenKind.FALSE)) {
      Token token = previous();
      return new Syntax.BooleanPattern(token.kind() == TokenKind.TRUE, token.span());
    }
    if (match(TokenKind.STRING)) {
      Token token = previous();
      return new Syntax.StringPattern(token.value(), token.span());
    }
    if (check(TokenKind.IDENTIFIER)
        && peek().value().equals("_")
        && !checkNext(TokenKind.LEFT_PAREN)) {
      return new Syntax.WildcardPattern(advance().span());
    }
    if (!check(TokenKind.IDENTIFIER)) throw error(peek(), "expected pattern");
    int afterType = tokenAfterType(current);
    if (afterType >= 0
        && afterType < tokens.size()
        && tokens.get(afterType).kind() == TokenKind.IDENTIFIER) {
      Syntax.TypeRef type = parseType();
      Token name = consume(TokenKind.IDENTIFIER, "expected pattern binding name");
      return new Syntax.BindingPattern(
          type, name.value(), name.span(), type.span().cover(name.span()));
    }
    Token name = advance();
    List<Syntax.Pattern> arguments = new ArrayList<>();
    SourceSpan span = name.span();
    if (match(TokenKind.LEFT_PAREN)) {
      if (!check(TokenKind.RIGHT_PAREN)) {
        do {
          arguments.add(parsePattern());
        } while (match(TokenKind.COMMA));
      }
      Token closing = consume(TokenKind.RIGHT_PAREN, "expected ')' after variant pattern");
      span = name.span().cover(closing.span());
    }
    return new Syntax.VariantPattern(name.value(), name.span(), arguments, span);
  }

  private int tokenAfterType(int start) {
    if (start < 0 || start >= tokens.size() || !isTypeToken(tokens.get(start).kind())) return -1;
    int index = start + 1;
    if (index >= tokens.size() || tokens.get(index).kind() != TokenKind.LESS) {
      return skipNullable(index);
    }
    int depth = 0;
    int parentheses = 0;
    for (; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LESS) {
        depth++;
      } else if (kind == TokenKind.GREATER) {
        depth--;
        if (depth == 0) return skipNullable(index + 1);
      } else if (kind == TokenKind.LEFT_PAREN) {
        parentheses++;
      } else if (kind == TokenKind.RIGHT_PAREN && parentheses > 0) {
        parentheses--;
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
    if (!isAtEnd() && checkNext(kind)) {
      Token unexpected = advance();
      diagnostics.error(
          EXPECTED_TOKEN,
          "unexpected " + ParserRecovery.display(unexpected) + "; " + message,
          unexpected.span());
      return advance();
    }
    if (ParserRecovery.canInsert(kind, previous(), peek())) {
      diagnostics.error(
          EXPECTED_TOKEN, message, SourceSpan.at(source, peek().span().startOffset()));
      return Token.simple(kind, "", SourceSpan.at(source, peek().span().startOffset()));
    }
    throw error(peek(), message);
  }

  private ParseError error(Token token, String message) {
    diagnostics.error(
        message.equals("expected expression") ? EXPECTED_EXPRESSION : EXPECTED_TOKEN,
        message,
        token.span());
    int recoveryLine =
        current == 0
            ? token.span().start().line()
            : Math.min(token.span().start().line(), previous().span().end().line());
    return new ParseError(recoveryLine);
  }

  private java.math.BigInteger integerValue(Token token) {
    try {
      return new java.math.BigInteger(token.value());
    } catch (NumberFormatException exception) {
      throw error(token, "invalid integer literal");
    }
  }

  private java.math.BigDecimal decimalValue(Token token) {
    try {
      return new java.math.BigDecimal(token.value());
    } catch (NumberFormatException exception) {
      throw error(token, "invalid decimal literal");
    }
  }

  private void synchronizeTopLevel() {
    while (!isAtEnd()) {
      if (check(TokenKind.CLASS)
          || checkValueDeclarationKeyword()
          || check(TokenKind.ENUM)
          || isTypeToken(peek().kind())) {
        return;
      }
      advance();
    }
  }

  private boolean matchContextual(String value) {
    if (!checkContextual(value)) return false;
    advance();
    return true;
  }

  private boolean matchValueDeclarationKeyword() {
    if (!checkValueDeclarationKeyword()) return false;
    advance();
    return true;
  }

  private boolean checkValueDeclarationKeyword() {
    return checkContextual("value") && checkNext(TokenKind.IDENTIFIER);
  }

  private boolean checkContextual(String value) {
    return check(TokenKind.IDENTIFIER) && peek().value().equals(value);
  }

  private void synchronizeStatement(ParseError error) {
    while (!isAtEnd() && !check(TokenKind.RIGHT_BRACE)) {
      if (match(TokenKind.SEMICOLON)) {
        return;
      }
      if (ParserRecovery.canResumeStatement(peek(), error.recoveryLine())) {
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
    guard.checkpoint();
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
    return kind == TokenKind.IDENTIFIER || kind == TokenKind.REF;
  }

  private record Block(List<Syntax.Statement> statements, SourceSpan span) {
    private Block {
      statements = List.copyOf(statements);
    }
  }

  private record QualifiedName(String value, SourceSpan lastSegmentSpan) {}

  @SuppressWarnings("serial")
  private static final class ParseError extends RuntimeException {
    private final int recoveryLine;

    private ParseError(int recoveryLine) {
      super(null, null, false, false);
      this.recoveryLine = recoveryLine;
    }

    private int recoveryLine() {
      return recoveryLine;
    }
  }
}
