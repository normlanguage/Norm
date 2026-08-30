package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CompilationOutput;
import dev.w0fv1.norm.core.CompilationState;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.value.SourceFile;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CoreIrArchitectureTest {
  @Test
  void typedProgramContainsOnlyCompilationOutput() {
    var components = dev.w0fv1.norm.value.TypedProgram.class.getRecordComponents();

    assertEquals(1, components.length);
    assertEquals(CompilationOutput.class, components[0].getType());
  }

  @Test
  void compilationOutputSeparatesArtifactFromCompilerState() {
    var components = CompilationOutput.class.getRecordComponents();

    assertEquals(2, components.length);
    assertEquals(CoreArtifact.class, components[0].getType());
    assertEquals(CompilationState.class, components[1].getType());
  }

  @Test
  void compilerAndRuntimeShareOnePhysicalModule() throws Exception {
    ModuleDescriptor core = moduleDescriptor(CoreArtifact.class);
    ModuleDescriptor runtimeFromContract = moduleDescriptor(ExecutionBackend.class);
    ModuleDescriptor runtimeFromLanguage = moduleDescriptor(Language.class);

    assertEquals("dev.w0fv1.norm", core.name());
    assertEquals(core.name(), runtimeFromContract.name());
    assertEquals(runtimeFromContract.name(), runtimeFromLanguage.name());
  }

  @Test
  void boundIrIsNotPartOfThePublicModuleApi() throws Exception {
    ModuleDescriptor descriptor = moduleDescriptor(CompilationOutput.class);

    assertFalse(
        descriptor.exports().stream()
            .map(ModuleDescriptor.Exports::source)
            .anyMatch("dev.w0fv1.norm.bound"::equals));
  }

  @Test
  void corePreservesCanonicalTargetsArgumentOrderAndFieldOrdinals() {
    var source =
        SourceFile.of(
            Path.of("core.norm"),
            "class Box { Integer value Void set(Integer next) { value = next } } "
                + "Integer choose(Integer first, Integer second) { return first } "
                + "Void main() { Box box = Box(value: 1) box.set(next: choose(second: 2, first: 3)) }");

    CompilationOutput compilation =
        new CompilerSession().compile(source).program().orElseThrow().compilation();

    assertTrue(compilation.artifact().program().callables().size() >= 3);
    var main =
        (CoreDefinition.Callable)
            compilation
                .artifact()
                .program()
                .definition(compilation.artifact().entryDefinition())
                .orElseThrow();
    var set =
        (CoreExpression.Call)
            ((CoreStatement.ExpressionStatement) main.body().statements().get(1)).expression();
    var choose = (CoreExpression.Call) set.arguments().getFirst().value();

    assertEquals(
        java.util.List.of(1, 0),
        choose.arguments().stream().map(dev.w0fv1.norm.core.CoreArgument::parameterIndex).toList());
    var box =
        (CoreDefinition.Aggregate)
            compilation
                .artifact()
                .program()
                .definition(compilation.artifact().namespace().definition("", "Box").orElseThrow())
                .orElseThrow();
    assertEquals(0, box.fields().getFirst().ordinal());

    var boxId = compilation.artifact().namespace().definition("", "Box").orElseThrow();
    var setTarget = assertInstanceOf(DefinitionReference.class, set.target());
    var setId =
        compilation
            .artifact()
            .program()
            .resolve(compilation.artifact().entryDefinition(), setTarget);
    var setDefinition =
        (CoreDefinition.Callable) compilation.artifact().program().definition(setId).orElseThrow();
    var assignment = (CoreStatement.FieldAssignment) setDefinition.body().statements().getFirst();
    var owner = assertInstanceOf(DefinitionReference.class, assignment.field().owner());
    assertEquals(boxId, compilation.artifact().program().resolve(setId, owner));
  }

  private static ModuleDescriptor moduleDescriptor(Class<?> type) throws Exception {
    Path aggregates = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    return ModuleFinder.of(aggregates).findAll().stream().findFirst().orElseThrow().descriptor();
  }
}
