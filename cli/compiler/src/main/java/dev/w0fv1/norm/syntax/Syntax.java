package dev.w0fv1.norm.syntax;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Syntax {
  private Syntax() {}

  public record Program(
      String packageName,
      List<AnnotationUse> packageAnnotations,
      List<ImportDecl> imports,
      List<EnumDecl> enums,
      List<InterfaceDecl> interfaces,
      List<AggregateDecl> aggregates,
      List<FunctionDecl> functions,
      SourceSpan span)
      implements AstNode {
    public Program {
      Objects.requireNonNull(packageName, "packageName");
      packageAnnotations = List.copyOf(packageAnnotations);
      imports = List.copyOf(imports);
      enums = List.copyOf(enums);
      interfaces = List.copyOf(interfaces);
      aggregates = List.copyOf(aggregates);
      functions = List.copyOf(functions);
      Objects.requireNonNull(span, "span");
    }
  }

  public record AnnotationUse(
      String name, SourceSpan nameSpan, List<CallArgument> arguments, SourceSpan span)
      implements AstNode {
    public AnnotationUse {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      arguments = List.copyOf(arguments);
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

  public enum AggregateKind {
    CLASS("class"),
    VALUE("value"),
    ANNOTATION("annotation");

    private final String keyword;

    AggregateKind(String keyword) {
      this.keyword = keyword;
    }

    public String keyword() {
      return keyword;
    }
  }

  public record EnumVariant(
      String name, SourceSpan nameSpan, List<Parameter> parameters, SourceSpan span)
      implements AstNode {
    public EnumVariant {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(span, "span");
    }
  }

  public record EnumDecl(
      List<AnnotationUse> annotations,
      Visibility visibility,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      List<EnumVariant> variants,
      SourceSpan span)
      implements AstNode {
    public EnumDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      variants = List.copyOf(variants);
      Objects.requireNonNull(span, "span");
    }
  }

  public record TypeRef(
      Kind kind, String name, List<TypeRef> arguments, boolean nullable, SourceSpan span)
      implements AstNode {
    public TypeRef {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(name, "name");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(span, "span");
      if (kind == Kind.WILDCARD && (!name.equals("?") || !arguments.isEmpty() || nullable)) {
        throw new IllegalArgumentException(
            "wildcard type references cannot have a name, arguments, or nullability");
      }
    }

    public TypeRef(String name, List<TypeRef> arguments, boolean nullable, SourceSpan span) {
      this(Kind.NAMED, name, arguments, nullable, span);
    }

    public TypeRef(String name, List<TypeRef> arguments, SourceSpan span) {
      this(name, arguments, false, span);
    }

    public static TypeRef wildcard(SourceSpan span) {
      return new TypeRef(Kind.WILDCARD, "?", List.of(), false, span);
    }

    public boolean isWildcard() {
      return kind == Kind.WILDCARD;
    }

    public String displayName() {
      if (isWildcard()) return "?";
      if (name.equals("Function") && !arguments.isEmpty()) {
        if (arguments.size() == 1 && arguments.getFirst().isWildcard()) {
          return nullable ? "Function<?>?" : "Function<?>";
        }
        String parameters =
            arguments.stream()
                .skip(1)
                .map(TypeRef::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
        String base = "Function<" + arguments.getFirst().displayName() + "(" + parameters + ")>";
        return nullable ? base + "?" : base;
      }
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

    public enum Kind {
      NAMED,
      WILDCARD
    }
  }

  public record TypeParameter(String name, SourceSpan nameSpan, Optional<TypeRef> upperBound) {
    public TypeParameter {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      upperBound = Objects.requireNonNull(upperBound, "upperBound");
    }
  }

  public record InterfaceMethodDecl(
      List<AnnotationUse> annotations,
      TypeRef returnType,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      List<Parameter> parameters,
      Optional<List<Statement>> body,
      SourceSpan span)
      implements AstNode {
    public InterfaceMethodDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(returnType, "returnType");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      parameters = List.copyOf(parameters);
      body = Objects.requireNonNull(body, "body").map(List::copyOf);
      Objects.requireNonNull(span, "span");
    }

    public InterfaceMethodDecl(
        TypeRef returnType,
        String name,
        SourceSpan nameSpan,
        List<TypeParameter> typeParameters,
        List<Parameter> parameters,
        SourceSpan span) {
      this(
          List.of(),
          returnType,
          name,
          nameSpan,
          typeParameters,
          parameters,
          Optional.empty(),
          span);
    }
  }

  public record InterfaceDecl(
      List<AnnotationUse> annotations,
      Visibility visibility,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      List<TypeRef> extendedInterfaces,
      List<InterfaceMethodDecl> methods,
      SourceSpan span)
      implements AstNode {
    public InterfaceDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(visibility, "visibility");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      extendedInterfaces = List.copyOf(extendedInterfaces);
      methods = List.copyOf(methods);
      Objects.requireNonNull(span, "span");
    }
  }

  public record Parameter(
      List<AnnotationUse> annotations,
      TypeRef type,
      String name,
      SourceSpan nameSpan,
      Optional<List<Parameter>> callableParameters,
      SourceSpan span)
      implements AstNode {
    public Parameter {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      callableParameters =
          Objects.requireNonNull(callableParameters, "callableParameters").map(List::copyOf);
      Objects.requireNonNull(span, "span");
    }

    public Parameter(TypeRef type, String name, SourceSpan nameSpan, SourceSpan span) {
      this(List.of(), type, name, nameSpan, Optional.empty(), span);
    }
  }

  public record FieldDecl(
      List<AnnotationUse> annotations,
      Visibility visibility,
      TypeRef type,
      String name,
      SourceSpan nameSpan,
      SourceSpan span)
      implements AstNode {
    public FieldDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(visibility, "visibility");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      Objects.requireNonNull(span, "span");
    }
  }

  public record FunctionDecl(
      List<AnnotationUse> annotations,
      Visibility visibility,
      FunctionKind kind,
      Optional<TypeRef> returnType,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      List<Parameter> parameters,
      List<Statement> body,
      SourceSpan span)
      implements AstNode {
    public FunctionDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(kind, "kind");
      returnType = Objects.requireNonNull(returnType, "returnType");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      parameters = List.copyOf(parameters);
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public enum FunctionKind {
    REGULAR,
    EXTENSION
  }

  public record ConstructorDecl(
      List<AnnotationUse> annotations,
      String name,
      SourceSpan nameSpan,
      List<Parameter> parameters,
      Optional<SuperCall> superCall,
      List<Statement> body,
      SourceSpan span)
      implements AstNode {
    public ConstructorDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      parameters = List.copyOf(parameters);
      superCall = Objects.requireNonNull(superCall, "superCall");
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record SuperCall(List<CallArgument> arguments, SourceSpan span) implements AstNode {
    public SuperCall {
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(span, "span");
    }
  }

  public record AggregateDecl(
      List<AnnotationUse> annotations,
      AggregateKind kind,
      Visibility visibility,
      String name,
      SourceSpan nameSpan,
      List<TypeParameter> typeParameters,
      Optional<TypeRef> extendedClass,
      List<TypeRef> implementedInterfaces,
      List<FieldDecl> fields,
      List<ConstructorDecl> constructors,
      List<FunctionDecl> methods,
      SourceSpan span)
      implements AstNode {
    public AggregateDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      typeParameters = List.copyOf(typeParameters);
      extendedClass = Objects.requireNonNull(extendedClass, "extendedClass");
      implementedInterfaces = List.copyOf(implementedInterfaces);
      fields = List.copyOf(fields);
      constructors = List.copyOf(constructors);
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
          TryStatement,
          ThrowStatement,
          ReturnStatement,
          BreakStatement,
          ContinueStatement {}

  public record VariableDecl(
      List<AnnotationUse> annotations,
      Optional<TypeRef> type,
      String name,
      SourceSpan nameSpan,
      Optional<List<Parameter>> callableParameters,
      Expression initializer,
      SourceSpan span)
      implements Statement {
    public VariableDecl {
      annotations = List.copyOf(annotations);
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      callableParameters =
          Objects.requireNonNull(callableParameters, "callableParameters").map(List::copyOf);
      Objects.requireNonNull(initializer, "initializer");
      Objects.requireNonNull(span, "span");
    }

    public VariableDecl(
        Optional<TypeRef> type,
        String name,
        SourceSpan nameSpan,
        Expression initializer,
        SourceSpan span) {
      this(List.of(), type, name, nameSpan, Optional.empty(), initializer, span);
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
      Optional<ForIndex> index,
      Expression iterable,
      List<Statement> body,
      SourceSpan span)
      implements Statement {
    public ForStatement {
      Objects.requireNonNull(variableType, "variableType");
      Objects.requireNonNull(variableName, "variableName");
      Objects.requireNonNull(variableNameSpan, "variableNameSpan");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(iterable, "iterable");
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record ForIndex(String name, SourceSpan nameSpan) {
    public ForIndex {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
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

  public record CatchClause(
      TypeRef type, String name, SourceSpan nameSpan, List<Statement> body, SourceSpan span)
      implements AstNode {
    public CatchClause {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record FinallyClause(List<Statement> body, SourceSpan span) implements AstNode {
    public FinallyClause {
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record TryStatement(
      List<Statement> body,
      List<CatchClause> catches,
      Optional<FinallyClause> finallyClause,
      SourceSpan span)
      implements Statement {
    public TryStatement {
      body = List.copyOf(body);
      catches = List.copyOf(catches);
      finallyClause = Objects.requireNonNull(finallyClause, "finallyClause");
      Objects.requireNonNull(span, "span");
    }
  }

  public record ThrowStatement(Expression exception, SourceSpan span) implements Statement {
    public ThrowStatement {
      Objects.requireNonNull(exception, "exception");
      Objects.requireNonNull(span, "span");
    }
  }

  public record ReturnStatement(Expression value, SourceSpan span) implements Statement {
    public ReturnStatement {
      Objects.requireNonNull(span, "span");
    }
  }

  public record BreakStatement(Expression value, SourceSpan span) implements Statement {
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
          DecimalLiteral,
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
          Lambda,
          Index,
          SwitchExpression {}

  public record LambdaParameter(
      Optional<TypeRef> type, String name, SourceSpan nameSpan, SourceSpan span)
      implements AstNode {
    public LambdaParameter {
      type = Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      Objects.requireNonNull(span, "span");
    }
  }

  public record Lambda(
      Optional<TypeRef> returnType,
      List<LambdaParameter> parameters,
      List<Statement> body,
      SourceSpan span)
      implements Expression {
    public Lambda {
      returnType = Objects.requireNonNull(returnType, "returnType");
      parameters = List.copyOf(parameters);
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public sealed interface Pattern extends AstNode
      permits VariantPattern,
          BindingPattern,
          WildcardPattern,
          IntegerPattern,
          DecimalPattern,
          CodePointPattern,
          BooleanPattern,
          StringPattern,
          NullPattern {}

  public record VariantPattern(
      String name, SourceSpan nameSpan, List<Pattern> arguments, SourceSpan span)
      implements Pattern {
    public VariantPattern {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(span, "span");
    }
  }

  public record BindingPattern(TypeRef type, String name, SourceSpan nameSpan, SourceSpan span)
      implements Pattern {
    public BindingPattern {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameSpan, "nameSpan");
      Objects.requireNonNull(span, "span");
    }
  }

  public record WildcardPattern(SourceSpan span) implements Pattern {
    public WildcardPattern {
      Objects.requireNonNull(span, "span");
    }
  }

  public record IntegerPattern(java.math.BigInteger value, SourceSpan span) implements Pattern {
    public IntegerPattern {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record DecimalPattern(java.math.BigDecimal value, SourceSpan span) implements Pattern {
    public DecimalPattern {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record CodePointPattern(int value, SourceSpan span) implements Pattern {
    public CodePointPattern {
      Objects.requireNonNull(span, "span");
    }
  }

  public record BooleanPattern(boolean value, SourceSpan span) implements Pattern {
    public BooleanPattern {
      Objects.requireNonNull(span, "span");
    }
  }

  public record StringPattern(String value, SourceSpan span) implements Pattern {
    public StringPattern {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record NullPattern(SourceSpan span) implements Pattern {
    public NullPattern {
      Objects.requireNonNull(span, "span");
    }
  }

  public record SwitchCase(Pattern pattern, List<Statement> body, SourceSpan span)
      implements AstNode {
    public SwitchCase {
      Objects.requireNonNull(pattern, "pattern");
      body = List.copyOf(body);
      Objects.requireNonNull(span, "span");
    }
  }

  public record SwitchExpression(Expression value, List<SwitchCase> cases, SourceSpan span)
      implements Expression {
    public SwitchExpression {
      Objects.requireNonNull(value, "value");
      cases = List.copyOf(cases);
      Objects.requireNonNull(span, "span");
    }
  }

  public record IntegerLiteral(java.math.BigInteger value, SourceSpan span) implements Expression {
    public IntegerLiteral {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  public record DecimalLiteral(java.math.BigDecimal value, SourceSpan span) implements Expression {
    public DecimalLiteral {
      Objects.requireNonNull(value, "value");
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

  public record Name(String value, List<TypeRef> typeArguments, boolean diamond, SourceSpan span)
      implements Expression {
    public Name {
      Objects.requireNonNull(value, "value");
      typeArguments = List.copyOf(typeArguments);
      if (diamond && !typeArguments.isEmpty()) {
        throw new IllegalArgumentException("diamond cannot contain type arguments");
      }
      Objects.requireNonNull(span, "span");
    }

    public Name(String value, SourceSpan span) {
      this(value, List.of(), false, span);
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
