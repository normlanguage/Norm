package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreCompilation;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CoreIrArchitectureTest {
  @Test
  void typedProgramContainsOnlyCoreCompilation() {
    var components = dev.w0fv1.norm.value.TypedProgram.class.getRecordComponents();

    assertEquals(1, components.length);
    assertEquals(CoreCompilation.class, components[0].getType());
  }

  @Test
  void backendConsumesOnlyCore() throws Exception {
    String lowerer =
        Files.readString(
            Path.of("src/main/java/dev/w0fv1/norm/truffle/Lowerer.java").toAbsolutePath());
    String backend =
        Files.readString(
            Path.of("src/main/java/dev/w0fv1/norm/execution/ExecutionBackend.java")
                .toAbsolutePath());
    String module = Files.readString(Path.of("src/main/java/module-info.java").toAbsolutePath());

    assertFalse(lowerer.contains("dev.w0fv1.norm.bound"));
    assertFalse(lowerer.contains("dev.w0fv1.norm.semantic"));
    assertFalse(backend.contains("dev.w0fv1.norm.bound"));
    assertFalse(module.contains("exports dev.w0fv1.norm.bound"));
  }

  @Test
  void corePreservesCanonicalTargetsArgumentOrderAndFieldOrdinals() {
    var source =
        SourceFile.of(
            Path.of("core.norm"),
            "class Box { Integer value Void set(Integer next) { value = next } } "
                + "Integer choose(Integer first, Integer second) { return first } "
                + "Void main() { Box box = Box(value: 1) box.set(next: choose(second: 2, first: 3)) }");

    CoreCompilation compilation =
        new Compiler().compile(source).program().orElseThrow().coreCompilation();

    assertTrue(compilation.program().callables().size() >= 3);
    var main =
        (CoreDefinition.Callable)
            compilation.program().definition(compilation.entryDefinition()).orElseThrow();
    var set =
        (CoreExpression.Call)
            ((CoreStatement.ExpressionStatement) main.body().statements().get(1)).expression();
    var choose = (CoreExpression.Call) set.arguments().getFirst().value();

    assertEquals(
        java.util.List.of(1, 0),
        choose.arguments().stream().map(dev.w0fv1.norm.core.CoreArgument::parameterIndex).toList());
    var box =
        (CoreDefinition.Class)
            compilation
                .program()
                .definition(compilation.namespace().definition("", "Box").orElseThrow())
                .orElseThrow();
    assertEquals(0, box.fields().getFirst().ordinal());

    var boxId = compilation.namespace().definition("", "Box").orElseThrow();
    var setTarget = assertInstanceOf(DefinitionReference.class, set.target());
    var setId = compilation.program().resolve(compilation.entryDefinition(), setTarget);
    var setDefinition =
        (CoreDefinition.Callable) compilation.program().definition(setId).orElseThrow();
    var assignment = (CoreStatement.FieldAssignment) setDefinition.body().statements().getFirst();
    var owner = assertInstanceOf(DefinitionReference.class, assignment.field().owner());
    assertEquals(boxId, compilation.program().resolve(setId, owner));
  }
}
