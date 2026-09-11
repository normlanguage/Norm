package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ResultBuilderLowering {
  private int expansion;
  private static final String LOCAL = "$resultBuilder";

  private ResultBuilderLowering() {}

  public static Syntax.Lambda lower(Syntax.Lambda lambda, SemanticType builder) {
    var lowering = new ResultBuilderLowering();
    var body = new ArrayList<Syntax.Statement>();
    var constructor =
        new Syntax.Name(
            builder.identity(),
            builder.arguments().stream().map(type -> lowering.type(type, lambda.span())).toList(),
            false,
            lowering.span(lambda.span()));
    var created = new Syntax.Call(constructor, List.of(), lowering.span(lambda.span()));
    body.add(
        new Syntax.VariableDecl(
            Optional.empty(),
            LOCAL,
            lowering.span(lambda.span()),
            created,
            lowering.span(lambda.span())));
    body.addAll(lowering.statements(lambda.body()));
    var finish = lowering.call("finish", List.of(), lambda.span());
    body.add(new Syntax.ExpressionStatement(finish, finish.span()));
    return new Syntax.Lambda(lambda.returnType(), lambda.parameters(), body, lambda.span());
  }

  private Syntax.TypeRef type(SemanticType type, SourceSpan origin) {
    var name =
        BuiltinCatalog.standard()
            .type(type.name())
            .filter(definition -> definition.symbol().type().identity().equals(type.identity()))
            .map(definition -> definition.symbol().name())
            .orElse(type.identity());
    return new Syntax.TypeRef(
        name,
        type.arguments().stream().map(argument -> type(argument, origin)).toList(),
        type.isNullable(),
        span(origin));
  }

  private SourceSpan span(SourceSpan origin) {
    return new SourceSpan(origin.source(), origin.startOffset(), origin.endOffset(), ++expansion);
  }

  private Syntax.Call call(String method, List<Syntax.CallArgument> arguments, SourceSpan origin) {
    var receiver = new Syntax.Name(LOCAL, List.of(), false, span(origin));
    var member = new Syntax.Member(receiver, method, span(origin), List.of(), false, span(origin));
    return new Syntax.Call(member, arguments, span(origin));
  }

  private List<Syntax.Statement> statements(List<Syntax.Statement> source) {
    var result = new ArrayList<Syntax.Statement>();
    for (var statement : source) {
      result.add(
          switch (statement) {
            case Syntax.ExpressionStatement expression -> {
              var call =
                  call(
                      "add",
                      List.of(
                          new Syntax.CallArgument(
                              Optional.empty(), expression.expression(), expression.span())),
                      expression.span());
              yield new Syntax.ExpressionStatement(call, call.span());
            }
            case Syntax.IfStatement conditional ->
                new Syntax.IfStatement(
                    conditional.condition(),
                    statements(conditional.thenBody()),
                    statements(conditional.elseBody()),
                    conditional.span());
            case Syntax.ForStatement loop ->
                new Syntax.ForStatement(
                    loop.variableType(),
                    loop.variableName(),
                    loop.variableNameSpan(),
                    loop.index(),
                    loop.iterable(),
                    statements(loop.body()),
                    loop.span());
            case Syntax.ConditionalForStatement loop ->
                new Syntax.ConditionalForStatement(
                    loop.condition(), statements(loop.body()), loop.span());
            case Syntax.TryStatement tried ->
                new Syntax.TryStatement(
                    statements(tried.body()),
                    tried.catches().stream()
                        .map(
                            clause ->
                                new Syntax.CatchClause(
                                    clause.type(),
                                    clause.name(),
                                    clause.nameSpan(),
                                    statements(clause.body()),
                                    clause.span()))
                        .toList(),
                    tried
                        .finallyClause()
                        .map(
                            clause ->
                                new Syntax.FinallyClause(statements(clause.body()), clause.span())),
                    tried.span());
            case Syntax.ReturnStatement returned -> throw new InvalidControl(returned.span());
            case Syntax.BreakStatement broken when broken.value() != null ->
                throw new InvalidControl(broken.span());
            default -> statement;
          });
    }
    return List.copyOf(result);
  }

  @SuppressWarnings("serial")
  public static final class InvalidControl extends RuntimeException {
    private final SourceSpan span;

    private InvalidControl(SourceSpan span) {
      super("result builder blocks cannot return or break a value");
      this.span = span;
    }

    public SourceSpan span() {
      return span;
    }
  }
}
