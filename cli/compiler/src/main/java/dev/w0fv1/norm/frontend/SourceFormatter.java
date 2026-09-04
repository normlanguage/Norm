package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.AstNode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceFile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SourceFormatter {
  private static final int WIDTH = 100;

  public SourceFormatter() {}

  public Optional<String> format(SourceFile source) {
    java.util.Objects.requireNonNull(source, "source");
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens = new Lexer(source, diagnostics).lex();
    Syntax.Program program = new Parser(source, tokens, diagnostics).parse();
    if (diagnostics.hasErrors()) return Optional.empty();
    String formatted = Renderer.render(program(program), WIDTH);
    return Optional.of(formatted.isEmpty() ? formatted : formatted + "\n");
  }

  private Doc program(Syntax.Program program) {
    List<Doc> sections = new ArrayList<>();
    if (!program.packageName().isEmpty()) {
      sections.add(
          annotated(
              program.packageAnnotations(), Docs.text("package " + program.packageName()), false));
    }
    if (!program.imports().isEmpty()) {
      sections.add(
          Docs.join(
              Docs.hardLine(), program.imports().stream().map(this::importDeclaration).toList()));
    }
    List<AstNode> declarations = new ArrayList<>();
    declarations.addAll(program.enums());
    declarations.addAll(program.interfaces());
    declarations.addAll(program.aggregates());
    declarations.addAll(program.functions());
    declarations.sort(Comparator.comparingInt(value -> value.span().startOffset()));
    if (!declarations.isEmpty()) {
      sections.add(
          Docs.join(Docs.blankLine(), declarations.stream().map(this::declaration).toList()));
    }
    return Docs.join(Docs.blankLine(), sections);
  }

  private Doc importDeclaration(Syntax.ImportDecl declaration) {
    return Docs.text(
        "import "
            + declaration.qualifiedName()
            + declaration.alias().map(value -> " as " + value).orElse(""));
  }

  private Doc declaration(AstNode declaration) {
    return switch (declaration) {
      case Syntax.EnumDecl value -> enumDeclaration(value);
      case Syntax.InterfaceDecl value -> interfaceDeclaration(value);
      case Syntax.AggregateDecl value -> aggregateDeclaration(value);
      case Syntax.FunctionDecl value -> functionDeclaration(value);
      default -> throw new IllegalArgumentException("unsupported declaration " + declaration);
    };
  }

  private Doc enumDeclaration(Syntax.EnumDecl declaration) {
    List<Doc> variants = declaration.variants().stream().map(this::enumVariant).toList();
    Doc body =
        variants.isEmpty()
            ? Docs.text("{}")
            : Docs.group(
                Docs.concat(
                    Docs.text("{"),
                    Docs.nest(2, Docs.concat(Docs.line(), Docs.join(Docs.commaLine(), variants))),
                    Docs.line(),
                    Docs.text("}")));
    return annotated(
        declaration.annotations(),
        Docs.concat(
            visibility(declaration.visibility()),
            Docs.text("enum " + declaration.name()),
            typeParameters(declaration.typeParameters()),
            Docs.text(" "),
            body),
        false);
  }

  private Doc enumVariant(Syntax.EnumVariant variant) {
    if (variant.parameters().isEmpty()) return Docs.text(variant.name());
    return Docs.concat(
        Docs.text(variant.name()),
        delimited("(", ")", variant.parameters().stream().map(this::parameter).toList()));
  }

  private Doc interfaceDeclaration(Syntax.InterfaceDecl declaration) {
    Doc header =
        Docs.concat(
            visibility(declaration.visibility()),
            Docs.text("interface " + declaration.name()),
            typeParameters(declaration.typeParameters()),
            declaration.extendedInterfaces().isEmpty()
                ? Docs.empty()
                : Docs.concat(
                    Docs.text(" extends "),
                    Docs.group(
                        Docs.join(
                            Docs.commaLine(),
                            declaration.extendedInterfaces().stream().map(this::type).toList()))),
            Docs.text(" "));
    List<Doc> methods = declaration.methods().stream().map(this::interfaceMethod).toList();
    return annotated(
        declaration.annotations(), Docs.concat(header, declarationBody(methods)), false);
  }

  private Doc interfaceMethod(Syntax.InterfaceMethodDecl method) {
    Doc signature =
        Docs.concat(
            type(method.returnType()),
            Docs.text(" " + method.name()),
            typeParameters(method.typeParameters()),
            parameters(method.parameters()));
    Doc value =
        method
            .body()
            .map(body -> Docs.concat(signature, Docs.text(" "), block(body)))
            .orElse(signature);
    return annotated(method.annotations(), value, false);
  }

  private Doc aggregateDeclaration(Syntax.AggregateDecl declaration) {
    Doc header =
        Docs.concat(
            visibility(declaration.visibility()),
            Docs.text(declaration.kind().keyword() + " " + declaration.name()),
            typeParameters(declaration.typeParameters()),
            declaration
                .extendedClass()
                .map(value -> Docs.concat(Docs.text(" extends "), type(value)))
                .orElse(Docs.empty()),
            declaration.implementedInterfaces().isEmpty()
                ? Docs.empty()
                : Docs.concat(
                    Docs.text(" implements "),
                    Docs.group(
                        Docs.join(
                            Docs.commaLine(),
                            declaration.implementedInterfaces().stream()
                                .map(this::type)
                                .toList()))),
            Docs.text(" "));
    List<AstNode> members = new ArrayList<>();
    members.addAll(declaration.fields());
    members.addAll(declaration.constructors());
    members.addAll(declaration.methods());
    members.sort(Comparator.comparingInt(value -> value.span().startOffset()));
    List<Doc> formatted = members.stream().map(this::aggregateMember).toList();
    return annotated(
        declaration.annotations(), Docs.concat(header, declarationBody(formatted)), false);
  }

  private Doc aggregateMember(AstNode member) {
    return switch (member) {
      case Syntax.FieldDecl field ->
          annotated(
              field.annotations(),
              Docs.concat(
                  visibility(field.visibility()),
                  type(field.type()),
                  Docs.text(" " + field.name()),
                  field
                      .defaultValue()
                      .map(value -> Docs.concat(Docs.text(" = "), expression(value)))
                      .orElse(Docs.empty())),
              false);
      case Syntax.ConstructorDecl constructor -> constructorDeclaration(constructor);
      case Syntax.FunctionDecl method -> functionDeclaration(method);
      default -> throw new IllegalArgumentException("unsupported aggregate member " + member);
    };
  }

  private Doc constructorDeclaration(Syntax.ConstructorDecl declaration) {
    List<Doc> body = new ArrayList<>();
    declaration
        .superCall()
        .ifPresent(
            call ->
                body.add(
                    Docs.concat(
                        Docs.text("super"),
                        delimited(
                            "(", ")", call.arguments().stream().map(this::argument).toList()))));
    body.addAll(declaration.body().stream().map(this::statement).toList());
    return annotated(
        declaration.annotations(),
        Docs.concat(
            Docs.text(declaration.name()),
            parameters(declaration.parameters()),
            Docs.text(" "),
            blockDocs(body)),
        false);
  }

  private Doc declarationBody(List<Doc> members) {
    if (members.isEmpty()) return Docs.text("{}");
    return Docs.concat(
        Docs.text("{"),
        Docs.nest(2, Docs.concat(Docs.hardLine(), Docs.join(Docs.blankLine(), members))),
        Docs.hardLine(),
        Docs.text("}"));
  }

  private Doc functionDeclaration(Syntax.FunctionDecl declaration) {
    return annotated(
        declaration.annotations(),
        Docs.concat(
            visibility(declaration.visibility()),
            declaration.kind() == Syntax.FunctionKind.EXTENSION
                ? Docs.text("extension ")
                : Docs.empty(),
            declaration
                .returnType()
                .map(value -> Docs.concat(type(value), Docs.text(" ")))
                .orElse(Docs.empty()),
            Docs.text(declaration.name()),
            typeParameters(declaration.typeParameters()),
            parameters(declaration.parameters()),
            Docs.text(" "),
            block(declaration.body())),
        false);
  }

  private Doc visibility(Syntax.Visibility visibility) {
    return visibility == Syntax.Visibility.PRIVATE ? Docs.text("private ") : Docs.empty();
  }

  private Doc typeParameters(List<Syntax.TypeParameter> parameters) {
    if (parameters.isEmpty()) return Docs.empty();
    return delimited(
        "<",
        ">",
        parameters.stream()
            .map(
                parameter ->
                    Docs.concat(
                        Docs.text(parameter.name()),
                        parameter
                            .upperBound()
                            .map(bound -> Docs.concat(Docs.text(" extends "), type(bound)))
                            .orElse(Docs.empty()),
                        parameter
                            .defaultType()
                            .map(value -> Docs.concat(Docs.text(" = "), type(value)))
                            .orElse(Docs.empty())))
            .toList());
  }

  private Doc parameters(List<Syntax.Parameter> parameters) {
    return delimited("(", ")", parameters.stream().map(this::parameter).toList());
  }

  private Doc parameter(Syntax.Parameter parameter) {
    if (parameter.callableParameters().isPresent() && parameter.type().name().equals("Function")) {
      return annotated(
          parameter.annotations(),
          Docs.concat(
              type(parameter.type().arguments().getFirst()),
              Docs.text(" " + parameter.name()),
              parameters(parameter.callableParameters().orElseThrow()),
              defaultValue(parameter)),
          true);
    }
    return annotated(
        parameter.annotations(),
        Docs.concat(
            type(parameter.type()), Docs.text(" " + parameter.name()), defaultValue(parameter)),
        true);
  }

  private Doc defaultValue(Syntax.Parameter parameter) {
    return parameter
        .defaultValue()
        .map(value -> Docs.concat(Docs.text(" = "), expression(value)))
        .orElse(Docs.empty());
  }

  private Doc type(Syntax.TypeRef type) {
    if (type.isWildcard()) return Docs.text("?");
    Doc base;
    if (type.name().equals("Function") && !type.arguments().isEmpty()) {
      if (type.arguments().size() == 1 && type.arguments().getFirst().isWildcard()) {
        base = Docs.text("Function<?>");
        return type.nullable() ? Docs.concat(base, Docs.text("?")) : base;
      }
      base =
          Docs.concat(
              Docs.text("Function<"),
              this.type(type.arguments().getFirst()),
              delimited("(", ")", type.arguments().stream().skip(1).map(this::type).toList()),
              Docs.text(">"));
    } else {
      base =
          Docs.concat(
              Docs.text(type.name()),
              type.arguments().isEmpty()
                  ? Docs.empty()
                  : delimited("<", ">", type.arguments().stream().map(this::type).toList()));
    }
    return type.nullable() ? Docs.concat(base, Docs.text("?")) : base;
  }

  private Doc block(List<Syntax.Statement> statements) {
    return blockDocs(statements.stream().map(this::statement).toList());
  }

  private Doc blockDocs(List<Doc> statements) {
    if (statements.isEmpty()) return Docs.text("{}");
    return Docs.concat(
        Docs.text("{"),
        Docs.nest(2, Docs.concat(Docs.hardLine(), Docs.join(Docs.hardLine(), statements))),
        Docs.hardLine(),
        Docs.text("}"));
  }

  private Doc statement(Syntax.Statement statement) {
    return switch (statement) {
      case Syntax.VariableDecl value -> variableDeclaration(value);
      case Syntax.Assignment value ->
          Docs.group(
              Docs.concat(expression(value.target()), Docs.text(" = "), expression(value.value())));
      case Syntax.ExpressionStatement value -> expression(value.expression());
      case Syntax.IfStatement value -> ifStatement(value);
      case Syntax.ForStatement value -> forStatement(value);
      case Syntax.ConditionalForStatement value ->
          Docs.concat(
              Docs.text("for "),
              expression(value.condition()),
              Docs.text(" "),
              block(value.body()));
      case Syntax.TryStatement value -> tryStatement(value);
      case Syntax.ThrowStatement value ->
          Docs.concat(Docs.text("throw "), expression(value.exception()));
      case Syntax.ReturnStatement value ->
          value.value() == null
              ? Docs.text("return")
              : Docs.concat(Docs.text("return "), expression(value.value()));
      case Syntax.BreakStatement value ->
          value.value() == null
              ? Docs.text("break")
              : Docs.concat(Docs.text("break "), expression(value.value()));
      case Syntax.ContinueStatement ignored -> Docs.text("continue");
    };
  }

  private Doc tryStatement(Syntax.TryStatement statement) {
    Doc result = Docs.concat(Docs.text("try "), block(statement.body()));
    for (Syntax.CatchClause clause : statement.catches()) {
      result =
          Docs.concat(
              result,
              Docs.text(" catch "),
              type(clause.type()),
              Docs.text(" " + clause.name() + " "),
              block(clause.body()));
    }
    if (statement.finallyClause().isEmpty()) return result;
    return Docs.concat(
        result, Docs.text(" finally "), block(statement.finallyClause().orElseThrow().body()));
  }

  private Doc variableDeclaration(Syntax.VariableDecl declaration) {
    Doc prefix;
    if (declaration.callableParameters().isPresent()
        && declaration.type().isPresent()
        && declaration.type().orElseThrow().name().equals("Function")) {
      Syntax.TypeRef function = declaration.type().orElseThrow();
      prefix =
          Docs.concat(
              type(function.arguments().getFirst()),
              Docs.text(" " + declaration.name()),
              parameters(declaration.callableParameters().orElseThrow()));
    } else {
      prefix =
          Docs.concat(
              declaration
                  .type()
                  .map(value -> Docs.concat(type(value), Docs.text(" ")))
                  .orElse(Docs.text("var ")),
              Docs.text(declaration.name()));
    }
    return annotated(
        declaration.annotations(),
        Docs.concat(prefix, Docs.text(" = "), expression(declaration.initializer())),
        false);
  }

  private Doc annotated(List<Syntax.AnnotationUse> annotations, Doc target, boolean inline) {
    if (annotations.isEmpty()) return target;
    Doc separator = inline ? Docs.text(" ") : Docs.hardLine();
    return Docs.concat(
        Docs.join(separator, annotations.stream().map(this::annotationUse).toList()),
        separator,
        target);
  }

  private Doc annotationUse(Syntax.AnnotationUse annotation) {
    return Docs.concat(
        Docs.text("@" + annotation.name()),
        delimited("(", ")", annotation.arguments().stream().map(this::argument).toList()));
  }

  private Doc ifStatement(Syntax.IfStatement statement) {
    Doc result =
        Docs.concat(
            Docs.text("if "),
            expression(statement.condition()),
            Docs.text(" "),
            block(statement.thenBody()));
    if (statement.elseBody().isEmpty()) return result;
    if (statement.elseBody().size() == 1
        && statement.elseBody().getFirst() instanceof Syntax.IfStatement nested) {
      return Docs.concat(result, Docs.text(" else "), ifStatement(nested));
    }
    return Docs.concat(result, Docs.text(" else "), block(statement.elseBody()));
  }

  private Doc forStatement(Syntax.ForStatement statement) {
    return Docs.concat(
        Docs.text("for "),
        statement
            .variableType()
            .map(value -> Docs.concat(type(value), Docs.text(" ")))
            .orElse(Docs.empty()),
        Docs.text(statement.variableName()),
        statement.index().map(value -> Docs.text(", " + value.name())).orElse(Docs.empty()),
        Docs.text(" : "),
        expression(statement.iterable()),
        Docs.text(" "),
        block(statement.body()));
  }

  private Doc expression(Syntax.Expression expression) {
    return expression(expression, 0, false, null);
  }

  private Doc expression(
      Syntax.Expression expression, int parentPrecedence, boolean right, TokenKind parentOperator) {
    int precedence = precedence(expression);
    Doc value = expressionValue(expression, precedence);
    boolean parentheses = precedence < parentPrecedence;
    if (precedence == parentPrecedence
        && expression instanceof Syntax.Binary
        && parentOperator != null) {
      parentheses = parentOperator == TokenKind.QUESTION_QUESTION ? !right : right;
    }
    return parentheses ? Docs.concat(Docs.text("("), value, Docs.text(")")) : value;
  }

  private Doc expressionValue(Syntax.Expression expression, int precedence) {
    return switch (expression) {
      case Syntax.IntegerLiteral value -> Docs.text(value.value().toString());
      case Syntax.DecimalLiteral value -> Docs.text(value.value().toString());
      case Syntax.CodePointLiteral value ->
          Docs.text(codePointLiteral(value.value(), value.span().text()));
      case Syntax.BooleanLiteral value -> Docs.text(Boolean.toString(value.value()));
      case Syntax.NullLiteral ignored -> Docs.text("null");
      case Syntax.StringLiteralExpr value -> Docs.text(stringLiteral(value.value()));
      case Syntax.InterpolatedStringExpr value -> {
        List<Doc> parts = new ArrayList<>();
        parts.add(Docs.text("\""));
        for (int index = 0; index < value.expressions().size(); index++) {
          parts.add(Docs.text(stringContent(value.text().get(index))));
          parts.add(Docs.text("${"));
          parts.add(expression(value.expressions().get(index)));
          parts.add(Docs.text("}"));
        }
        parts.add(Docs.text(stringContent(value.text().getLast())));
        parts.add(Docs.text("\""));
        yield Docs.concat(parts);
      }
      case Syntax.ArrayLiteral value ->
          delimited("[", "]", value.elements().stream().map(this::expression).toList());
      case Syntax.Name value -> name(value);
      case Syntax.Unary value ->
          Docs.concat(
              Docs.text(operator(value.operator())),
              expression(value.operand(), precedence, true, value.operator()));
      case Syntax.Binary value ->
          Docs.group(
              Docs.concat(
                  expression(value.left(), precedence, false, value.operator()),
                  Docs.text(" " + operator(value.operator())),
                  Docs.nest(
                      2,
                      Docs.concat(
                          Docs.line(),
                          expression(value.right(), precedence, true, value.operator())))));
      case Syntax.Call value -> call(value);
      case Syntax.Member value -> member(value);
      case Syntax.Lambda value -> lambda(value);
      case Syntax.Index value ->
          Docs.concat(
              expression(value.receiver(), precedence, false, null),
              Docs.text("["),
              expression(value.index()),
              Docs.text("]"));
      case Syntax.SwitchExpression value -> switchExpression(value);
    };
  }

  private Doc name(Syntax.Name name) {
    if (name.diamond()) return Docs.text(name.value() + "<>");
    if (name.typeArguments().isEmpty()) return Docs.text(name.value());
    return Docs.concat(
        Docs.text(name.value()),
        delimited("<", ">", name.typeArguments().stream().map(this::type).toList()));
  }

  private Doc call(Syntax.Call call) {
    return Docs.concat(
        expression(call.callee(), 9, false, null),
        delimited("(", ")", call.arguments().stream().map(this::argument).toList()));
  }

  private Doc argument(Syntax.CallArgument argument) {
    return Docs.concat(
        argument.label().map(label -> Docs.text(label.name() + ": ")).orElse(Docs.empty()),
        expression(argument.value()));
  }

  private Doc member(Syntax.Member member) {
    return Docs.concat(
        expression(member.receiver(), 9, false, null),
        Docs.text(member.nullSafe() ? "?." : "."),
        Docs.text(member.name()),
        member.typeArguments().isEmpty()
            ? Docs.empty()
            : delimited("<", ">", member.typeArguments().stream().map(this::type).toList()));
  }

  private Doc lambda(Syntax.Lambda lambda) {
    return Docs.concat(
        lambda
            .returnType()
            .map(value -> Docs.concat(type(value), Docs.text(" ")))
            .orElse(Docs.empty()),
        delimited("(", ")", lambda.parameters().stream().map(this::lambdaParameter).toList()),
        Docs.text(" "),
        block(lambda.body()));
  }

  private Doc lambdaParameter(Syntax.LambdaParameter parameter) {
    return Docs.concat(
        parameter
            .type()
            .map(value -> Docs.concat(type(value), Docs.text(" ")))
            .orElse(Docs.empty()),
        Docs.text(parameter.name()));
  }

  private Doc switchExpression(Syntax.SwitchExpression expression) {
    List<Doc> cases = expression.cases().stream().map(this::switchCase).toList();
    if (cases.isEmpty()) {
      return Docs.concat(Docs.text("switch "), expression(expression.value()), Docs.text(" {}"));
    }
    return Docs.concat(
        Docs.text("switch "),
        expression(expression.value()),
        Docs.text(" {"),
        Docs.nest(2, Docs.concat(Docs.hardLine(), Docs.join(Docs.hardLine(), cases))),
        Docs.hardLine(),
        Docs.text("}"));
  }

  private Doc switchCase(Syntax.SwitchCase value) {
    return Docs.concat(
        Docs.text("case "), pattern(value.pattern()), Docs.text(" "), block(value.body()));
  }

  private Doc pattern(Syntax.Pattern pattern) {
    return switch (pattern) {
      case Syntax.VariantPattern value ->
          value.arguments().isEmpty()
              ? Docs.text(value.name())
              : Docs.concat(
                  Docs.text(value.name()),
                  delimited("(", ")", value.arguments().stream().map(this::pattern).toList()));
      case Syntax.BindingPattern value ->
          Docs.concat(type(value.type()), Docs.text(" " + value.name()));
      case Syntax.WildcardPattern ignored -> Docs.text("_");
      case Syntax.IntegerPattern value -> Docs.text(value.value().toString());
      case Syntax.DecimalPattern value -> Docs.text(value.value().toString());
      case Syntax.CodePointPattern value ->
          Docs.text(codePointLiteral(value.value(), value.span().text()));
      case Syntax.BooleanPattern value -> Docs.text(Boolean.toString(value.value()));
      case Syntax.StringPattern value -> Docs.text(stringLiteral(value.value()));
      case Syntax.NullPattern ignored -> Docs.text("null");
    };
  }

  private Doc delimited(String opening, String closing, List<Doc> values) {
    if (values.isEmpty()) return Docs.text(opening + closing);
    return Docs.group(
        Docs.concat(
            Docs.text(opening),
            Docs.nest(2, Docs.concat(Docs.breakLine(), Docs.join(Docs.commaLine(), values))),
            Docs.breakLine(),
            Docs.text(closing)));
  }

  private static int precedence(Syntax.Expression expression) {
    if (expression instanceof Syntax.Lambda || expression instanceof Syntax.SwitchExpression)
      return 0;
    if (expression instanceof Syntax.Binary binary) {
      return switch (binary.operator()) {
        case QUESTION_QUESTION -> 1;
        case OR_OR -> 2;
        case AND_AND -> 3;
        case EQUAL_EQUAL, BANG_EQUAL -> 4;
        case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> 5;
        case PLUS, MINUS -> 6;
        case STAR, SLASH, PERCENT -> 7;
        default ->
            throw new IllegalArgumentException("unsupported binary operator " + binary.operator());
      };
    }
    if (expression instanceof Syntax.Unary) return 8;
    if (expression instanceof Syntax.Call
        || expression instanceof Syntax.Member
        || expression instanceof Syntax.Index) return 9;
    return 10;
  }

  private static String operator(TokenKind operator) {
    return switch (operator) {
      case QUESTION_QUESTION -> "??";
      case OR_OR -> "||";
      case AND_AND -> "&&";
      case EQUAL_EQUAL -> "==";
      case BANG_EQUAL -> "!=";
      case LESS -> "<";
      case LESS_EQUAL -> "<=";
      case GREATER -> ">";
      case GREATER_EQUAL -> ">=";
      case PLUS -> "+";
      case MINUS -> "-";
      case STAR -> "*";
      case AMPERSAND -> "&";
      case SLASH -> "/";
      case PERCENT -> "%";
      case BANG -> "!";
      default -> throw new IllegalArgumentException("unsupported operator " + operator);
    };
  }

  private static String stringLiteral(String value) {
    return "\"" + stringContent(value) + "\"";
  }

  private static String stringContent(String value) {
    StringBuilder result = new StringBuilder();
    for (int offset = 0; offset < value.length(); ) {
      int character = value.codePointAt(offset);
      if (character == '$' && offset + 1 < value.length() && value.charAt(offset + 1) == '{') {
        result.append("\\$");
      } else {
        appendEscaped(result, character, false);
      }
      offset += Character.charCount(character);
    }
    return result.toString();
  }

  private static String codePointLiteral(int value, String original) {
    StringBuilder result = new StringBuilder("'");
    if (!appendEscaped(result, value, true)) return original;
    return result.append('\'').toString();
  }

  private static boolean appendEscaped(StringBuilder result, int character, boolean codePoint) {
    switch (character) {
      case '\n' -> result.append("\\n");
      case '\r' -> result.append("\\r");
      case '\t' -> result.append("\\t");
      case '\\' -> result.append("\\\\");
      case '"' -> result.append(codePoint ? "\"" : "\\\"");
      case '\'' -> result.append(codePoint ? "\\'" : "'");
      default -> {
        if (Character.isISOControl(character)) return false;
        result.appendCodePoint(character);
      }
    }
    return true;
  }

  private sealed interface Doc permits Text, Line, Concat, Nest, Group {}

  private record Text(String value) implements Doc {}

  private record Line(boolean hard, boolean space) implements Doc {}

  private record Concat(List<Doc> values) implements Doc {
    private Concat {
      values = List.copyOf(values);
    }
  }

  private record Nest(int amount, Doc value) implements Doc {}

  private record Group(Doc value) implements Doc {}

  private static final class Docs {
    private static final Doc EMPTY = new Text("");
    private static final Doc LINE = new Line(false, true);
    private static final Doc BREAK_LINE = new Line(false, false);
    private static final Doc HARD_LINE = new Line(true, false);

    private Docs() {}

    private static Doc empty() {
      return EMPTY;
    }

    private static Doc text(String value) {
      return value.isEmpty() ? EMPTY : new Text(value);
    }

    private static Doc line() {
      return LINE;
    }

    private static Doc hardLine() {
      return HARD_LINE;
    }

    private static Doc breakLine() {
      return BREAK_LINE;
    }

    private static Doc blankLine() {
      return concat(HARD_LINE, HARD_LINE);
    }

    private static Doc commaLine() {
      return concat(text(","), LINE);
    }

    private static Doc concat(Doc... values) {
      return concat(List.of(values));
    }

    private static Doc concat(List<Doc> values) {
      List<Doc> flattened = new ArrayList<>();
      for (Doc value : values) {
        if (value == EMPTY) continue;
        if (value instanceof Concat nested) flattened.addAll(nested.values());
        else flattened.add(value);
      }
      if (flattened.isEmpty()) return EMPTY;
      if (flattened.size() == 1) return flattened.getFirst();
      return new Concat(flattened);
    }

    private static Doc join(Doc separator, List<Doc> values) {
      if (values.isEmpty()) return EMPTY;
      List<Doc> result = new ArrayList<>();
      for (int index = 0; index < values.size(); index++) {
        if (index > 0) result.add(separator);
        result.add(values.get(index));
      }
      return concat(result);
    }

    private static Doc nest(int amount, Doc value) {
      return new Nest(amount, value);
    }

    private static Doc group(Doc value) {
      return new Group(value);
    }
  }

  private static final class Renderer {
    private Renderer() {}

    private static String render(Doc document, int width) {
      StringBuilder result = new StringBuilder();
      ArrayDeque<Command> commands = new ArrayDeque<>();
      commands.push(new Command(0, Mode.BREAK, document));
      int column = 0;
      int pendingIndent = 0;
      boolean lineStart = true;
      while (!commands.isEmpty()) {
        Command command = commands.pop();
        switch (command.document()) {
          case Text text -> {
            if (lineStart && !text.value().isEmpty()) {
              result.append(" ".repeat(pendingIndent));
              lineStart = false;
            }
            result.append(text.value());
            column += text.value().length();
          }
          case Line line -> {
            if (!line.hard() && command.mode() == Mode.FLAT) {
              if (line.space()) {
                result.append(' ');
                column++;
              }
            } else {
              result.append('\n');
              column = command.indent();
              pendingIndent = command.indent();
              lineStart = true;
            }
          }
          case Concat concat -> push(commands, command.indent(), command.mode(), concat.values());
          case Nest nest ->
              commands.push(
                  new Command(command.indent() + nest.amount(), command.mode(), nest.value()));
          case Group group -> {
            Command flat = new Command(command.indent(), Mode.FLAT, group.value());
            commands.push(
                fits(width - column, flat, new ArrayDeque<>())
                    ? flat
                    : new Command(command.indent(), Mode.BREAK, group.value()));
          }
        }
      }
      return result.toString();
    }

    private static boolean fits(int remaining, Command first, ArrayDeque<Command> rest) {
      ArrayDeque<Command> commands = new ArrayDeque<>(rest);
      commands.push(first);
      while (remaining >= 0 && !commands.isEmpty()) {
        Command command = commands.pop();
        switch (command.document()) {
          case Text text -> remaining -= text.value().length();
          case Line line -> {
            if (line.hard() || command.mode() == Mode.BREAK) return true;
            if (line.space()) remaining--;
          }
          case Concat concat -> push(commands, command.indent(), command.mode(), concat.values());
          case Nest nest ->
              commands.push(
                  new Command(command.indent() + nest.amount(), command.mode(), nest.value()));
          case Group group ->
              commands.push(new Command(command.indent(), Mode.FLAT, group.value()));
        }
      }
      return remaining >= 0;
    }

    private static void push(
        ArrayDeque<Command> commands, int indent, Mode mode, List<Doc> values) {
      for (int index = values.size() - 1; index >= 0; index--) {
        commands.push(new Command(indent, mode, values.get(index)));
      }
    }

    private enum Mode {
      FLAT,
      BREAK
    }

    private record Command(int indent, Mode mode, Doc document) {}
  }
}
