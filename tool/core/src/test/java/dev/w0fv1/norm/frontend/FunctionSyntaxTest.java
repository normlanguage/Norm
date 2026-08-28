package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FunctionSyntaxTest {
  @Test
  void parsesOmittedTopLevelAndMethodReturnTypes() {
    Syntax.Program program =
        parse("class Counter { add(Integer amount) { } Void clear() { } } main() { }");

    assertTrue(program.functions().getFirst().returnType().isEmpty());
    assertTrue(program.aggregates().getFirst().methods().getFirst().returnType().isEmpty());
    assertEquals(
        "Void",
        program.aggregates().getFirst().methods().get(1).returnType().orElseThrow().displayName());
  }

  @Test
  void parsesFunctionTypesAndContextTypedLambdas() {
    Syntax.Program program =
        parse("Void main() { Function<Integer(Integer)> doubled = (value) { value * 2 } }");

    Syntax.VariableDecl declaration =
        (Syntax.VariableDecl) program.functions().getFirst().body().getFirst();
    assertEquals("Function<Integer(Integer)>", declaration.type().orElseThrow().displayName());
    Syntax.Lambda lambda = assertInstanceOf(Syntax.Lambda.class, declaration.initializer());
    assertEquals("value", lambda.parameters().getFirst().name());
    assertFalse(lambda.parameters().getFirst().type().isPresent());
  }

  @Test
  void parsesTypedLambdasAndBoundMethodReferences() {
    Syntax.Program program =
        parse(
            "Void main() { var doubled = (Integer value) { value * 2 } "
                + "var add = counter::add }");

    Syntax.VariableDecl lambdaDeclaration =
        (Syntax.VariableDecl) program.functions().getFirst().body().getFirst();
    Syntax.Lambda lambda = assertInstanceOf(Syntax.Lambda.class, lambdaDeclaration.initializer());
    assertEquals("Integer", lambda.parameters().getFirst().type().orElseThrow().displayName());
    Syntax.VariableDecl referenceDeclaration =
        (Syntax.VariableDecl) program.functions().getFirst().body().get(1);
    Syntax.MethodReference reference =
        assertInstanceOf(Syntax.MethodReference.class, referenceDeclaration.initializer());
    assertEquals("add", reference.name());
  }

  @Test
  void parsesExtensionFunctionsAsDistinctTopLevelDeclarations() {
    Syntax.Program program =
        parse("public extension String display<T>(T value) { return value.toString() }");

    Syntax.FunctionDecl function = program.functions().getFirst();
    assertEquals(Syntax.FunctionKind.EXTENSION, function.kind());
    assertEquals("value", function.parameters().getFirst().name());
  }

  private Syntax.Program parse(String text) {
    SourceFile source = SourceFile.of(Path.of("functions.norm"), text);
    DiagnosticBag diagnostics = new DiagnosticBag();
    Syntax.Program program =
        new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics).parse();
    assertFalse(diagnostics.hasErrors(), () -> diagnostics.snapshot().toString());
    return program;
  }
}
