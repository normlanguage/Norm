package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.syntax.Syntax;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class BlockCallChainSyntaxTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "produce {} map {}",
        "produce{}map{}",
        "produce {}\tmap\t{}",
        "produce(config: value) {} map {}",
        "produce<Integer> {} map {}",
        "object.produce {} map {}",
        "produce {}.map {} finish {}"
      })
  void parsesOrdinaryMemberCallsWithRealNameRanges(String text) {
    var statements = body(text);
    assertEquals(1, statements.size());
    var call =
        assertInstanceOf(
            Syntax.Call.class,
            assertInstanceOf(Syntax.ExpressionStatement.class, statements.getFirst()).expression());
    var member = assertInstanceOf(Syntax.Member.class, call.callee());
    assertInstanceOf(Syntax.Call.class, member.receiver());
    assertFalse(member.nullSafe());
    assertTrue(member.typeArguments().isEmpty());
    assertEquals(
        member.name(),
        member
            .nameSpan()
            .source()
            .text()
            .substring(member.nameSpan().startOffset(), member.nameSpan().endOffset()));
    assertEquals(1, call.arguments().size());
    assertTrue(call.arguments().getFirst().trailing());
  }

  @Test
  void associatesEachStageWithThePreviousCall() {
    var call =
        (Syntax.Call)
            ((Syntax.ExpressionStatement) body("produce {} map {} finish {}").getFirst())
                .expression();
    var finish = assertInstanceOf(Syntax.Member.class, call.callee());
    assertEquals("finish", finish.name());
    var mapped = assertInstanceOf(Syntax.Call.class, finish.receiver());
    var map = assertInstanceOf(Syntax.Member.class, mapped.callee());
    assertEquals("map", map.name());
    var produced = assertInstanceOf(Syntax.Call.class, map.receiver());
    assertEquals("produce", assertInstanceOf(Syntax.Name.class, produced.callee()).value());
    assertEquals(1, produced.arguments().size());
    assertEquals(1, mapped.arguments().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "produce {}\nmap {}",
        "produce {}\r\nmap {}",
        "produce {}\rmap {}",
        "produce {} map\n{}",
        "produce {} map\r\n{}",
        "produce {} map\r{}",
        "produce {}; map {}",
        "produce() map {}",
        "(produce {}) map {}",
        "() {} map {}",
        "produce {}[0] map {}",
        "produce {}.value map {}",
        "produce {} map<Integer> {}",
        "produce {} map(option: value) {}",
        "if true {} map {}"
      })
  void preservesIndependentStatementsOutsideTheContract(String text) {
    var statements = body(text);
    assertEquals(2, statements.size());
    var last = assertInstanceOf(Syntax.ExpressionStatement.class, statements.getLast());
    var call = assertInstanceOf(Syntax.Call.class, last.expression());
    assertEquals("map", assertInstanceOf(Syntax.Name.class, call.callee()).value());
  }

  @Test
  void retainsPostfixPrecedenceAndNestedLambdaScopes() {
    var expression =
        ((Syntax.ExpressionStatement)
                body("left + produce { nested {} map {} } map { item in item }").getFirst())
            .expression();
    var binary = assertInstanceOf(Syntax.Binary.class, expression);
    var mapped = assertInstanceOf(Syntax.Call.class, binary.right());
    var member = assertInstanceOf(Syntax.Member.class, mapped.callee());
    assertEquals("map", member.name());
    var lambda = assertInstanceOf(Syntax.Lambda.class, mapped.arguments().getFirst().value());
    assertEquals("item", lambda.parameters().getFirst().name());
    var produced = assertInstanceOf(Syntax.Call.class, member.receiver());
    var work = (Syntax.Lambda) produced.arguments().getFirst().value();
    assertEquals(1, work.body().size());
    var inner = (Syntax.Call) ((Syntax.ExpressionStatement) work.body().getFirst()).expression();
    assertInstanceOf(Syntax.Member.class, inner.callee());
  }

  @Test
  void protectsControlStructureBoundaries() {
    var conditional =
        assertInstanceOf(
            Syntax.IfStatement.class,
            body("if (probe { true } check { result }) { proceed() } else { stop() }").getFirst());
    var call = assertInstanceOf(Syntax.Call.class, conditional.condition());
    assertEquals("check", assertInstanceOf(Syntax.Member.class, call.callee()).name());
    assertEquals(1, conditional.thenBody().size());
    assertEquals(1, conditional.elseBody().size());
    var ordinary =
        assertInstanceOf(Syntax.IfStatement.class, body("if probe {} else {}").getFirst());
    assertInstanceOf(Syntax.Name.class, ordinary.condition());
  }

  @Test
  void doesNotIntroduceGeneralInfixCalls() {
    var parsed =
        SourceParser.parse(SourceFile.of(Path.of("invalid.norm"), "Void main() { task map {} }"));
    assertFalse(parsed.diagnostics().isEmpty());
  }

  @Test
  void preservesTheSingleTrailingLambdaDiagnostic() {
    var parsed =
        SourceParser.parse(SourceFile.of(Path.of("invalid.norm"), "Void main() { produce {} {} }"));
    assertTrue(
        parsed.diagnostics().stream()
            .anyMatch(d -> d.message().contains("only one trailing lambda")));
  }

  @Test
  void doesNotConsumeAnIncompletePrefixOrTheFollowingStatement() {
    var statements = body("produce {} th\nvar kept = next()");
    assertEquals(3, statements.size());
    assertInstanceOf(
        Syntax.Name.class, ((Syntax.ExpressionStatement) statements.get(1)).expression());
    var next = (Syntax.Call) ((Syntax.VariableDecl) statements.getLast()).initializer();
    assertEquals("next", ((Syntax.Name) next.callee()).value());
  }

  private static List<Syntax.Statement> body(String text) {
    var parsed =
        SourceParser.parse(SourceFile.of(Path.of("chain.norm"), "Void main() { " + text + " }"));
    assertTrue(parsed.diagnostics().isEmpty(), () -> parsed.diagnostics().toString());
    return parsed.syntax().functions().getFirst().body();
  }
}
