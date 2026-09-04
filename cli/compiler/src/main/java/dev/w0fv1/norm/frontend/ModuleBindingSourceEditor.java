package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.JarTarget;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.Sha256Digest;
import dev.w0fv1.norm.value.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ModuleBindingSourceEditor {
  public String withDigest(SourceFile source, JarTarget target, Sha256Digest digest) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(digest, "digest");
    Syntax.Program program = parse(source);
    List<Syntax.Call> matches =
        calls(program).stream().filter(call -> matches(call, target)).toList();
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "module.norm must contain exactly one direct declaration for the resolved JAR target");
    }
    Syntax.Call call = matches.getFirst();
    String label = target instanceof LocalJarTarget ? "integrity" : "resolution";
    String expression = "sha256(\"" + digest.value() + "\")";
    return replaceDigest(source.text(), call, label, expression);
  }

  private static Syntax.Program parse(SourceFile source) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    var tokens = new Lexer(source, diagnostics).lex();
    Syntax.Program program = new Parser(source, tokens, diagnostics).parse();
    if (diagnostics.hasErrors()) {
      throw new IllegalArgumentException("module.norm contains syntax errors");
    }
    return program;
  }

  private static String replaceDigest(
      String text, Syntax.Call call, String label, String expression) {
    Optional<Syntax.CallArgument> existing = argument(call, label, -1);
    if (existing.isPresent()) {
      var span = existing.orElseThrow().value().span();
      return text.substring(0, span.startOffset()) + expression + text.substring(span.endOffset());
    }
    int insertion =
        call.arguments().isEmpty()
            ? call.span().endOffset() - 1
            : call.arguments().getLast().span().endOffset();
    String prefix = call.arguments().isEmpty() ? "" : ", ";
    return text.substring(0, insertion)
        + prefix
        + label
        + ": "
        + expression
        + text.substring(insertion);
  }

  private static boolean matches(Syntax.Call call, JarTarget target) {
    if (!(call.callee() instanceof Syntax.Name name)) return false;
    return switch (target) {
      case LocalJarTarget local ->
          name.value().equals("localJar")
              && stringArgument(call, "path", 0).filter(local.path()::equals).isPresent();
      case MavenJarTarget maven ->
          name.value().equals("mavenJar")
              && stringArgument(call, "group", 0)
                  .filter(maven.coordinate().group()::equals)
                  .isPresent()
              && stringArgument(call, "artifact", 1)
                  .filter(maven.coordinate().artifact()::equals)
                  .isPresent()
              && stringArgument(call, "version", 2)
                  .filter(maven.coordinate().version()::equals)
                  .isPresent();
    };
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

  private static List<Syntax.Call> calls(Syntax.Program program) {
    List<Syntax.Call> calls = new ArrayList<>();
    for (Syntax.FunctionDecl function : program.functions()) {
      collectStatements(function.body(), calls);
    }
    return calls;
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
          value.catches().forEach(catchClause -> collectStatements(catchClause.body(), calls));
          value.finallyClause().ifPresent(clause -> collectStatements(clause.body(), calls));
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
          value.elements().forEach(element -> collectExpression(element, calls));
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
        value.cases().forEach(switchCase -> collectStatements(switchCase.body(), calls));
      }
      case Syntax.IntegerLiteral ignored -> {}
      case Syntax.DecimalLiteral ignored -> {}
      case Syntax.CodePointLiteral ignored -> {}
      case Syntax.BooleanLiteral ignored -> {}
      case Syntax.NullLiteral ignored -> {}
      case Syntax.StringLiteralExpr ignored -> {}
      case Syntax.InterpolatedStringExpr interpolation ->
          interpolation.expressions().forEach(value -> collectExpression(value, calls));
      case Syntax.Name ignored -> {}
    }
  }
}
