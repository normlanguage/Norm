package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.syntax.Syntax;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SyntaxAuthoringIndexTest {
  @Test
  void permitsSharedTypeSyntaxWithoutTreatingItAsAnAuthoredLambdaSite() {
    var parsed =
        SourceParser.parse(
            SourceFile.of(
                Path.of("shared.norm"),
                "Void work(Integer left, Integer right) { Function<String()> retained = () { \"value\" } } Void main() {}"));
    var function = parsed.syntax().functions().getFirst();
    var first = function.parameters().getFirst();
    var second = function.parameters().get(1);
    var shared =
        new Syntax.Parameter(
            second.annotations(),
            first.type(),
            second.name(),
            second.nameSpan(),
            second.callableParameters(),
            second.defaultValue(),
            second.span());
    var replacement =
        new Syntax.FunctionDecl(
            function.annotations(),
            function.visibility(),
            function.kind(),
            function.returnType(),
            function.name(),
            function.nameSpan(),
            function.typeParameters(),
            List.of(first, shared),
            function.implementation(),
            function.span());
    var program = parsed.syntax();
    var replaced =
        new Syntax.Program(
            program.packageName(),
            program.packageAnnotations(),
            program.imports(),
            program.enums(),
            program.interfaces(),
            program.aggregates(),
            List.of(replacement),
            program.span());
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(parsed.source());
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      var index = new SyntaxAuthoringIndex(List.of(replaced), analysis.semanticModel());
      var lambda = ((Syntax.VariableDecl) function.body().getFirst()).initializer();
      assertNotNull(index.location(lambda));
    }
  }

  @Test
  void preservesNamedLocalLambdaIdentityAcrossSiblingInsertionAndFormatting() {
    String original =
        "Void main() { Function<String()> retained = () { \"old\" } printLine(retained()) }";
    String updated =
        "Void main() { Function<String()> unrelated = () { \"other\" }; Function<String()> retained = () { \"new\" } printLine(retained()) }";
    assertEquals(localLambda(original, "retained"), localLambda(updated, "retained"));
    assertEquals(
        localLambda(original, "retained"),
        localLambda("\n\n" + original.replace("old", "longer-string"), "retained"));
    assertNotEquals(localLambda(updated, "unrelated"), localLambda(updated, "retained"));
  }

  @Test
  void keepsShadowedNamedLocalLambdasInSeparateStructuralScopes() {
    String source =
        "Void main() { Function<String()> retained = () { \"outer\" }; if (true) { Function<String()> retained = () { \"inner\" }; printLine(retained()) } printLine(retained()) }";
    var parsed = SourceParser.parse(SourceFile.of(Path.of("scopes.norm"), source));
    var statements = parsed.syntax().functions().getFirst().body();
    var outer = (Syntax.Lambda) ((Syntax.VariableDecl) statements.getFirst()).initializer();
    var inner =
        (Syntax.Lambda)
            ((Syntax.VariableDecl) ((Syntax.IfStatement) statements.get(1)).thenBody().getFirst())
                .initializer();
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(parsed.source());
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      var index = new SyntaxAuthoringIndex(List.of(parsed.syntax()), analysis.semanticModel());
      assertNotEquals(index.location(outer), index.location(inner));
    }
  }

  @Test
  void declinesToReuseAnonymousSiblingAddressesWhenTheirGroupChanges() {
    String source =
        "Void consume(Function<String()> callback) {} Void main() { consume(() { \"first\" }); consume(() { \"second\" }) }";
    String swapped =
        source
            .replace("first", "temporary")
            .replace("second", "first")
            .replace("temporary", "second");
    assertNotEquals(argumentLambda(source, 0), argumentLambda(swapped, 0));
    assertEquals(argumentLambda(source, 0), argumentLambda("\n" + source, 0));
  }

  private String localLambda(String source, String name) {
    var parsed = SourceParser.parse(SourceFile.of(Path.of("locals.norm"), source));
    var lambda =
        (Syntax.Lambda)
            parsed.syntax().functions().getFirst().body().stream()
                .filter(
                    statement ->
                        statement instanceof Syntax.VariableDecl variable
                            && variable.name().equals(name))
                .map(statement -> ((Syntax.VariableDecl) statement).initializer())
                .findFirst()
                .orElseThrow();
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(parsed.source());
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      return new SyntaxAuthoringIndex(List.of(parsed.syntax()), analysis.semanticModel())
          .location(lambda);
    }
  }

  private String argumentLambda(String source, int position) {
    var parsed = SourceParser.parse(SourceFile.of(Path.of("arguments.norm"), source));
    var main =
        parsed.syntax().functions().stream()
            .filter(function -> function.name().equals("main"))
            .findFirst()
            .orElseThrow();
    var call = (Syntax.Call) ((Syntax.ExpressionStatement) main.body().get(position)).expression();
    var lambda = (Syntax.Lambda) call.arguments().getFirst().value();
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(parsed.source());
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      return new SyntaxAuthoringIndex(List.of(parsed.syntax()), analysis.semanticModel())
          .location(lambda);
    }
  }
}
