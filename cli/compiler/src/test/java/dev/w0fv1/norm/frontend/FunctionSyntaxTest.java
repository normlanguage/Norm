package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.syntax.Syntax;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FunctionSyntaxTest {
  @Test
  void distinguishesClassMethodDeclarationsFromEmptyImplementations() {
    var owner =
        parse(
                """
                class Repository<T> {
                  T? find(Long id)
                  Void clear();
                  Void close() {}
                  T identity(T value) { value }
                }
                """)
            .aggregates()
            .getFirst();
    assertFalse(owner.methods().get(0).hasBody());
    assertFalse(owner.methods().get(1).hasBody());
    assertTrue(owner.methods().get(2).hasBody());
    assertTrue(owner.methods().get(3).hasBody());
    assertTrue(owner.methods().get(2).body().isEmpty());
    assertEquals("T?", owner.methods().get(0).returnType().orElseThrow().displayName());
  }

  @Test
  void parsesComputedPropertyAccessorsWithoutStorageFields() {
    var program =
        parse(
            "class Counter { private Integer stored Integer value { get { return stored } set(next)"
                + " { stored = next } } }");
    var owner = program.aggregates().getFirst();
    assertEquals(1, owner.fields().size());
    assertEquals(2, owner.methods().size());
    var getter = owner.methods().getFirst();
    var setter = owner.methods().getLast();
    assertEquals(Syntax.FunctionKind.GETTER, getter.kind());
    assertEquals(Syntax.FunctionKind.SETTER, setter.kind());
    assertEquals("value", getter.name());
    assertEquals("value", setter.name());
    assertEquals("Integer", getter.returnType().orElseThrow().displayName());
    assertEquals("Void", setter.returnType().orElseThrow().displayName());
    assertEquals("Integer", setter.parameters().getFirst().type().displayName());
    assertEquals("next", setter.parameters().getFirst().name());
  }

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
  void parsesTypedLambdasAndBoundMethodValues() {
    Syntax.Program program =
        parse(
            "Void main() { var doubled = (Integer value) { value * 2 } "
                + "var add = counter.add }");

    Syntax.VariableDecl lambdaDeclaration =
        (Syntax.VariableDecl) program.functions().getFirst().body().getFirst();
    Syntax.Lambda lambda = assertInstanceOf(Syntax.Lambda.class, lambdaDeclaration.initializer());
    assertEquals("Integer", lambda.parameters().getFirst().type().orElseThrow().displayName());
    Syntax.VariableDecl referenceDeclaration =
        (Syntax.VariableDecl) program.functions().getFirst().body().get(1);
    Syntax.Member reference =
        assertInstanceOf(Syntax.Member.class, referenceDeclaration.initializer());
    assertEquals("add", reference.name());
  }

  @Test
  void parsesExistentialReflectionTypes() {
    Syntax.Program program =
        parse(
            "class User {} Void inspect(Class<?> type, Function<?> function, "
                + "Field<User, ?> field) {}");

    Syntax.FunctionDecl inspect = program.functions().getFirst();
    assertEquals("Class<?>", inspect.parameters().get(0).type().displayName());
    assertEquals("Function<?>", inspect.parameters().get(1).type().displayName());
    assertEquals("Field<User, ?>", inspect.parameters().get(2).type().displayName());
  }

  @Test
  void parsesDeclarationReflectionLiterals() {
    parse(
        "class User { String name String find(Integer id) { return name } } "
            + "Void main() { var type = User.class var field = User.name.field "
            + "var function = User.find.function }");
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
