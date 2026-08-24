package dev.w0fv1.norm.syntax;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Syntax {
  private Syntax() {}

  public record Program(
      String packageName,
      List<ImportDecl> imports,
      List<EnumDecl> enums,
      List<ClassDecl> classes,
      List<FunctionDecl> functions,
      SourceSpan span)
      implements AstNode {
    public Program {
      Objects.requireNonNull(packageName, "packageName");
      imports = List.copyOf(imports);
      enums = List.copyOf(enums);
      classes = List.copyOf(classes);
      functions = List.copyOf(functions);
      Objects.requireNonNull(span, "span");
    }
  }

  public record ImportDecl(
      String qualifiedName,
      SourceSpan nameSpan,
      Optional<String> alias,
      Optional<SourceSpan> aliasSpan,
      SourceSpan span)
      implements AstNode {
    public ImportDecl {
      Objects.requireNonNull(qualifiedName, "qualifiedName");
      Objects.requireNonNull(nameSpan, "nameSpan");
      alias = Objects.requireNonNull(alias, "alias");
      aliasSpan = Objects.requireNonNull(aliasSpan, "aliasSpan");
      if (alias.isPresent() != aliasSpan.isPresent()) {
        throw new IllegalArgumentException("alias and alias span must be present together");
      }
      Objects.requireNonNull(span, "span");
    }

    public String localName() {
      int separator = qualifiedName.lastIndexOf('.');
      return alias.orElse(separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1));
    }
  }

  public enum Visibility {
    PUBLIC,
    PRIVATE
  }

  public record EnumMember(String name, SourceSpan nameSpan) {
    public EnumMember {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
    }
  }

  public record EnumDecl(
      Visibility visibility,
      String name,
      SourceSpan nameSpan,
      List<EnumMember> members,
      SourceSpan span)
      implements AstNode {
    public EnumDecl {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      members = List.copyOf(members);
      Objects.requireNonNull(span, "span");
    }
  }

  public record TypeRef(String name, List<TypeRef> arguments, boolean nullable, SourceSpan span)
      implements AstNode {
    public TypeRef {
      Objects.requireNonNull(name, "name");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(span, "span");
    }

    public TypeRef(String name, List<TypeRef> arguments, SourceSpan span) {
      this(name, arguments, false, span);
    }

    public String displayName() {
      String base =
          arguments.isEmpty()
              ? name
              : name
                  + "<"
                  + arguments.stream()
                      .map(TypeRef::displayName)
                      .collect(java.util.stream.Collectors.joining(", "))
                  + ">";
      return nullable ? base + "?" : base;
    }
  }

  public record TypeParameter(String name, SourceSpan nameSpan) {
    public TypeParameter {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
    }
  }

  public record Parameter(TypeRef type, String name, SourceSpan nameSpan, SourceSpan span)
      implements AstNode {
    public Parameter {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      Objects.requireNonNull(span, "span");
    }
  }

  public record FieldDecl(
      Visibility visibility, TypeRef type, String name, SourceSpan nameSpan, SourceSpan span)
      implements AstNode {
    public FieldDecl {
      Objects.requireNonNull(visibility, "visibility");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      Objects.requireNonNull(span, "span");
    }
  }

  public record FunctionDecl(
      Visibility visibility,
      TypeRef returnType,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      List<Parameter> parameters,
      List<Statement> body,
      SourceSpan span)
      implements AstNode {
    public FunctionDecl {
      Objects.requireNonNull(returnType, "returnType");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      parameters = List.copyOf(parameters);
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record ClassDecl(
      Visibility visibility,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      List<FieldDecl> fields,
      List<FunctionDecl> methods,
      SourceSpan span)
      implements AstNode {
    public ClassDecl {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      fields = List.copyOf(fields);
      methods = List.copyOf(methods);
      Objects.requireNonNull(span, "span");
    }
  }

  public sealed interface Statement extends AstNode
      permits VariableDecl,
          Assignment,
          ExpressionStatement,
          IfStatement,
          ConditionalForStatement,
          ForStatement,
          ReturnStatement,
          BreakStatement,
          ContinueStatement {}

  public record VariableDecl(
      TypeRef type, String name, SourceSpan nameSpan, Expression initializer, SourceSpan span)
      implements Statement {
    public VariableDecl {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      Objects.requireNonNull(initializer, "initializer");
      Objects.requireNonNull(span, "span");
    }
  }

  public record Assignment(Expression target, Expression value, SourceSpan span)
      implements Statement {
    public Assignment {
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record ExpressionStatement(Expression expression, SourceSpan span) implements Statement {
    public ExpressionStatement {
      Objects.requireNonNull(expression, "expression");
      Objects.requireNonNull(span, "span");
    }
  }

  public record IfStatement(
      Expression condition, List<Statement> thenBody, List<Statement> elseBody, SourceSpan span)
      implements Statement {
    public IfStatement {
      Objects.requireNonNull(condition, "condition");
      thenBody = List.copyOf(thenBody);
      elseBody = List.copyOf(elseBody);
      Objects.requireNonNull(span, "span");
    }
  }

  public record ForStatement(
      Optional<TypeRef> variableType,
      String variableName,
      SourceSpan variableNameSpan,
      Expression iterable,
      List<Statement> body,
      SourceSpan span)
      implements Statement {
    public ForStatement {
      Objects.requireNonNull(variableType, "variableType");
      Objects.requireNonNull(variableName, "variableName");
      Objects.requireNonNull(variableNameSpan, "variableNameSpan");
      Objects.requireNonNull(iterable, "iterable");
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record ConditionalForStatement(Expression condition, List<Statement> body, SourceSpan span)
      implements Statement {
    public ConditionalForStatement {
      Objects.requireNonNull(condition, "condition");
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record ReturnStatement(Expression value, SourceSpan span) implements Statement {
    public ReturnStatement {
      Objects.requireNonNull(span, "span");
    }
  }

  public record BreakStatement(SourceSpan span) implements Statement {
    public BreakStatement {
      Objects.requireNonNull(span, "span");
    }
  }

  public record ContinueStatement(SourceSpan span) implements Statement {
    public ContinueStatement {
      Objects.requireNonNull(span, "span");
    }
  }

  public sealed interface Expression extends AstNode
      permits IntegerLiteral,
          CodePointLiteral,
          BooleanLiteral,
          NullLiteral,
          StringLiteralExpr,
          ArrayLiteral,
          Name,
          Unary,
          Binary,
          Call,
          Member,
          Index {}

  public record IntegerLiteral(long value, SourceSpan span) implements Expression {
    public IntegerLiteral {
      Objects.requireNonNull(span, "span");
    }
  }

  public record CodePointLiteral(int value, SourceSpan span) implements Expression {
    public CodePointLiteral {
      Objects.requireNonNull(span, "span");
    }
  }

  public record BooleanLiteral(boolean value, SourceSpan span) implements Expression {
    public BooleanLiteral {
      Objects.requireNonNull(span, "span");
    }
  }

  public record NullLiteral(SourceSpan span) implements Expression {
    public NullLiteral {
      Objects.requireNonNull(span, "span");
    }
  }

  public record StringLiteralExpr(String value, SourceSpan span) implements Expression {
    public StringLiteralExpr {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record ArrayLiteral(List<Expression> elements, SourceSpan span) implements Expression {
    public ArrayLiteral {
      elements = List.copyOf(elements);
      Objects.requireNonNull(span, "span");
    }
  }

  public record Name(String value, List<TypeRef> typeArguments, SourceSpan span)
      implements Expression {
    public Name {
      Objects.requireNonNull(value, "value");
      typeArguments = List.copyOf(typeArguments);
      Objects.requireNonNull(span, "span");
    }

    public Name(String value, SourceSpan span) {
      this(value, List.of(), span);
    }
  }

  public record Unary(TokenKind operator, Expression operand, SourceSpan span)
      implements Expression {
    public Unary {
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(operand, "operand");
      Objects.requireNonNull(span, "span");
    }
  }

  public record Binary(Expression left, TokenKind operator, Expression right, SourceSpan span)
      implements Expression {
    public Binary {
      Objects.requireNonNull(left, "left");
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(right, "right");
      Objects.requireNonNull(span, "span");
    }
  }

  public record ArgumentLabel(String name, SourceSpan span) implements AstNode {
    public ArgumentLabel {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(span, "span");
    }
  }

  public record CallArgument(Optional<ArgumentLabel> label, Expression value, SourceSpan span)
      implements AstNode {
    public CallArgument {
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record Call(Expression callee, List<CallArgument> arguments, SourceSpan span)
      implements Expression {
    public Call {
      Objects.requireNonNull(callee, "callee");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(span, "span");
    }
  }

  public record Member(
      Expression receiver,
      String name,
      SourceSpan nameSpan,
      List<TypeRef> typeArguments,
      boolean nullSafe,
      SourceSpan span)
      implements Expression {
    public Member {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeArguments = List.copyOf(typeArguments);
      Objects.requireNonNull(span, "span");
    }

    public Member(Expression receiver, String name, SourceSpan nameSpan, SourceSpan span) {
      this(receiver, name, nameSpan, List.of(), false, span);
    }
  }

  public record Index(Expression receiver, Expression index, SourceSpan span)
      implements Expression {
    public Index {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(index, "index");
      Objects.requireNonNull(span, "span");
    }
  }
}
