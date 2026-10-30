package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ModuleDependencySourceEditor {
  public String withVersions(SourceFile source, List<Update> updates) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(updates, "updates");
    if (updates.isEmpty()) return source.text();
    Syntax.Program program = parse(SourceStructure.configuration(source));
    List<Syntax.Call> dependencies = new ArrayList<>();
    for (Syntax.FunctionDecl function : program.functions())
      collectStatements(function.body(), dependencies);
    String result = source.text();
    List<Replacement> replacements = new ArrayList<>();
    for (Update update : updates) {
      Syntax.Call call =
          dependencies.stream()
              .filter(value -> matches(value, update.repository(), update.name()))
              .skip(update.occurrence())
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException("dependency call not found: " + update.name()));
      Optional<Syntax.CallArgument> version = argument(call, "version", 2);
      if (version.isPresent()) {
        if (!(version.orElseThrow().value() instanceof Syntax.IntegerLiteral literal))
          throw new IllegalArgumentException(
              "dependency version must be an integer: " + update.name());
        replacements.add(
            new Replacement(
                literal.span().startOffset(),
                literal.span().endOffset(),
                Integer.toString(update.version())));
      } else {
        int insertion =
            call.arguments().isEmpty()
                ? call.span().endOffset() - 1
                : call.arguments().getLast().span().endOffset();
        String prefix = call.arguments().isEmpty() ? "" : ", ";
        replacements.add(
            new Replacement(insertion, insertion, prefix + "version: " + update.version()));
      }
    }
    replacements.sort((left, right) -> Integer.compare(right.start(), left.start()));
    for (Replacement replacement : replacements) {
      result =
          result.substring(0, replacement.start())
              + replacement.text()
              + result.substring(replacement.end());
    }
    return result;
  }

  public record Update(String repository, String name, int version, int occurrence) {
    public Update {
      Objects.requireNonNull(repository, "repository");
      Objects.requireNonNull(name, "name");
      if (version < 1 || occurrence < 0)
        throw new IllegalArgumentException("invalid dependency update");
    }

    public Update(String repository, String name, int version) {
      this(repository, name, version, 0);
    }
  }

  private static Syntax.Program parse(SourceFile source) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    var tokens = new Lexer(source, diagnostics).lex();
    Syntax.Program program = new Parser(source, tokens, diagnostics).parse();
    if (diagnostics.hasErrors())
      throw new IllegalArgumentException("module.norm contains syntax errors");
    return program;
  }

  private static boolean matches(Syntax.Call call, String repository, String name) {
    if (!(call.callee() instanceof Syntax.Name callee)
        || !(callee.value().equals("dependency") || callee.value().equals("exportedDependency")))
      return false;
    return stringArgument(call, "repository", 0).filter(repository::equals).isPresent()
        && stringArgument(call, "name", 1).filter(name::equals).isPresent();
  }

  private static Optional<String> stringArgument(Syntax.Call call, String label, int position) {
    return argument(call, label, position)
        .map(Syntax.CallArgument::value)
        .filter(Syntax.StringLiteralExpr.class::isInstance)
        .map(Syntax.StringLiteralExpr.class::cast)
        .map(Syntax.StringLiteralExpr::value);
  }

  private static Optional<Syntax.CallArgument> argument(
      Syntax.Call call, String label, int position) {
    Optional<Syntax.CallArgument> named =
        call.arguments().stream()
            .filter(
                value ->
                    value.label().map(Syntax.ArgumentLabel::name).filter(label::equals).isPresent())
            .findFirst();
    if (named.isPresent() || position < 0 || position >= call.arguments().size()) return named;
    Syntax.CallArgument positional = call.arguments().get(position);
    return positional.label().isEmpty() ? Optional.of(positional) : Optional.empty();
  }

  private static void collectStatements(
      List<Syntax.Statement> statements, List<Syntax.Call> calls) {
    for (Syntax.Statement statement : statements) {
      switch (statement) {
        case Syntax.VariableDecl value -> collectExpression(value.initializer(), calls);
        case Syntax.Assignment value -> {
          collectExpression(value.target(), calls);
          collectExpression(value.value(), calls);
        }
        case Syntax.ExpressionStatement value -> collectExpression(value.expression(), calls);
        case Syntax.IfStatement value -> {
          collectExpression(value.condition(), calls);
          collectStatements(value.thenBody(), calls);
          collectStatements(value.elseBody(), calls);
        }
        case Syntax.ForStatement value -> {
          collectExpression(value.iterable(), calls);
          collectStatements(value.body(), calls);
        }
        case Syntax.ConditionalForStatement value -> {
          collectExpression(value.condition(), calls);
          collectStatements(value.body(), calls);
        }
        case Syntax.TryStatement value -> {
          collectStatements(value.body(), calls);
          value.catches().forEach(item -> collectStatements(item.body(), calls));
          value.finallyClause().ifPresent(item -> collectStatements(item.body(), calls));
        }
        case Syntax.ThrowStatement value -> collectExpression(value.exception(), calls);
        case Syntax.ReturnStatement value -> {
          if (value.value() != null) collectExpression(value.value(), calls);
        }
        case Syntax.BreakStatement value -> {
          if (value.value() != null) collectExpression(value.value(), calls);
        }
        case Syntax.ContinueStatement ignored -> {}
      }
    }
  }

  private static void collectExpression(Syntax.Expression expression, List<Syntax.Call> calls) {
    switch (expression) {
      case Syntax.ArrayLiteral value ->
          value
              .elements()
              .forEach(element -> collectStatements(List.of(element.statement()), calls));
      case Syntax.Unary value -> collectExpression(value.operand(), calls);
      case Syntax.Binary value -> {
        collectExpression(value.left(), calls);
        collectExpression(value.right(), calls);
      }
      case Syntax.Call value -> {
        calls.add(value);
        collectExpression(value.callee(), calls);
        value.arguments().forEach(argument -> collectExpression(argument.value(), calls));
      }
      case Syntax.Member value -> collectExpression(value.receiver(), calls);
      case Syntax.Lambda value -> collectStatements(value.body(), calls);
      case Syntax.Index value -> {
        collectExpression(value.receiver(), calls);
        collectExpression(value.index(), calls);
      }
      case Syntax.SwitchExpression value -> {
        collectExpression(value.value(), calls);
        value.cases().forEach(item -> collectStatements(item.body(), calls));
      }
      case Syntax.IfExpression value -> {
        collectExpression(value.condition(), calls);
        collectStatements(value.thenBody(), calls);
        collectStatements(value.elseBody(), calls);
      }
      case Syntax.InterpolatedStringExpr value ->
          value.expressions().forEach(item -> collectExpression(item, calls));
      default -> {}
    }
  }

  private record Replacement(int start, int end, String text) {}
}
