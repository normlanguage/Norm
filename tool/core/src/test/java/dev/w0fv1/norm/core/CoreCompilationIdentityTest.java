package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreCompilationIdentityTest {
  @Test
  void ignoresFileFunctionParameterAndLocalNames() {
    CoreCompilation first =
        compile(
            "first.norm",
            "Integer calculate(Integer input) { Integer result = input + 1 return result } "
                + "Void main() { printLine(calculate(2)) }");
    CoreCompilation renamed =
        compile(
            "moved/renamed.norm",
            "Integer transformed(Integer value) { Integer answer = value + 1 return answer } "
                + "Void main() { printLine(transformed(2)) }");

    assertEquals(first.entryDefinition(), renamed.entryDefinition());
    assertEquals(
        first.namespace().definition("", "calculate"),
        renamed.namespace().definition("", "transformed"));
    assertNotEquals(first.namespace().id(), renamed.namespace().id());
  }

  @Test
  void ignoresGenericCallableAndTypeParameterNames() {
    CoreCompilation first =
        compile(
            "generic-first.norm",
            "T identity<T>(T input) { T result = input return result } "
                + "Void main() { String value = identity(\"Norm\") }");
    CoreCompilation renamed =
        compile(
            "generic-renamed.norm",
            "U transformed<U>(U item) { U answer = item return answer } "
                + "Void main() { String output = transformed(\"Norm\") }");

    assertEquals(first.entryDefinition(), renamed.entryDefinition());
    assertEquals(
        first.namespace().definition("", "identity"),
        renamed.namespace().definition("", "transformed"));
  }

  @Test
  void canonicalizesInferredAndExplicitGenericCallArguments() {
    CoreCompilation inferred =
        compile(
            "generic-inferred.norm",
            "T identity<T>(T value) { return value } "
                + "Void main() { String result = identity(value: \"Norm\") }");
    CoreCompilation explicit =
        compile(
            "generic-explicit.norm",
            "T identity<T>(T value) { return value } "
                + "Void main() { String result = identity<String>(value: \"Norm\") }");
    CoreCompilation different =
        compile(
            "generic-different.norm",
            "T identity<T>(T value) { return value } "
                + "Void main() { Integer result = identity<Integer>(value: 7) }");

    assertEquals(inferred.entryDefinition(), explicit.entryDefinition());
    assertNotEquals(inferred.entryDefinition(), different.entryDefinition());
  }

  @Test
  void canonicalizesInferredAndExplicitNullSafeGenericMethodCalls() {
    String declarations = "class Values { T identity<T>(T value) { return value } } ";
    CoreCompilation inferred =
        compile(
            "nullable-method-inferred.norm",
            declarations
                + "Void main() { Values? values = Values() "
                + "String? result = values?.identity(value: \"Norm\") }");
    CoreCompilation explicit =
        compile(
            "nullable-method-explicit.norm",
            declarations
                + "Void main() { Values? values = Values() "
                + "String? result = values?.identity<String>(value: \"Norm\") }");

    assertEquals(inferred.entryDefinition(), explicit.entryDefinition());
  }

  @Test
  void changesIdentityWhenExecutableContentChanges() {
    CoreCompilation first =
        compile(
            "content.norm",
            "Integer calculate(Integer value) { return value + 1 } "
                + "Void main() { printLine(calculate(2)) }");
    CoreCompilation changed =
        compile(
            "content.norm",
            "Integer calculate(Integer value) { return value + 2 } "
                + "Void main() { printLine(calculate(2)) }");

    assertNotEquals(
        first.namespace().definition("", "calculate"),
        changed.namespace().definition("", "calculate"));
    assertNotEquals(first.entryDefinition(), changed.entryDefinition());
  }

  @Test
  void canonicalizesDeclarationOrderAndMutualRecursion() {
    CoreCompilation first =
        compile(
            "recursive.norm",
            "Boolean even(Integer value) { if value == 0 { return true } return odd(value - 1) } "
                + "Boolean odd(Integer value) { if value == 0 { return false } return even(value - 1) } "
                + "Void main() { printLine(even(8)) }");
    CoreCompilation reorderedAndRenamed =
        compile(
            "other.norm",
            "Boolean beta(Integer item) { if item == 0 { return false } return alpha(item - 1) } "
                + "Boolean alpha(Integer item) { if item == 0 { return true } return beta(item - 1) } "
                + "Void main() { printLine(alpha(8)) }");

    DefinitionId even = first.namespace().definition("", "even").orElseThrow();
    DefinitionId odd = first.namespace().definition("", "odd").orElseThrow();
    DefinitionId alpha = reorderedAndRenamed.namespace().definition("", "alpha").orElseThrow();
    DefinitionId beta = reorderedAndRenamed.namespace().definition("", "beta").orElseThrow();

    assertEquals(even, alpha);
    assertEquals(odd, beta);
    assertEquals(even.group(), odd.group());
    assertEquals(first.entryDefinition(), reorderedAndRenamed.entryDefinition());
  }

  @Test
  void canonicalizesSymmetricMutualRecursion() {
    CoreCompilation first =
        compile(
            "symmetric.norm",
            "Integer first(Integer value) { if value == 0 { return 1 } return second(value - 1) } "
                + "Integer second(Integer value) { if value == 0 { return 1 } return third(value - 1) } "
                + "Integer third(Integer value) { if value == 0 { return 1 } return first(value - 1) } "
                + "Void main() { printLine(first(3)) }");
    CoreCompilation reordered =
        compile(
            "reordered.norm",
            "Integer gamma(Integer value) { if value == 0 { return 1 } return alpha(value - 1) } "
                + "Integer alpha(Integer value) { if value == 0 { return 1 } return beta(value - 1) } "
                + "Integer beta(Integer value) { if value == 0 { return 1 } return gamma(value - 1) } "
                + "Void main() { printLine(alpha(3)) }");

    DefinitionId firstMember = first.namespace().definition("", "first").orElseThrow();
    assertEquals(firstMember, first.namespace().definition("", "second").orElseThrow());
    assertEquals(firstMember, first.namespace().definition("", "third").orElseThrow());
    assertEquals(first.entryDefinition(), reordered.entryDefinition());
    first
        .program()
        .definitions()
        .forEach(
            definition -> assertTrue(!first.authoring().occurrences(definition.id()).isEmpty()));
  }

  @Test
  void keepsSourceOriginsOutsideCanonicalDefinitions() {
    CoreCompilation compilation =
        compile(
            "source/origin.norm",
            "Integer identity(Integer value) { return value } "
                + "Void main() { printLine(identity(3)) }");
    DefinitionId identity = compilation.namespace().definition("", "identity").orElseThrow();
    DefinitionOccurrenceId occurrence =
        compilation.namespace().occurrence("", "identity").orElseThrow();

    assertTrue(
        compilation
            .authoring()
            .origin(occurrence)
            .rootSpan()
            .source()
            .displayName()
            .endsWith("origin.norm"));
    assertEquals("identity", compilation.authoring().origin(occurrence).definitionName());
    assertTrue(compilation.authoring().origin(occurrence).span(0).isPresent());
    assertEquals(
        identity.group(),
        DefinitionHasher.hashGroup(
            compilation.program().group(identity.group()).orElseThrow().canonicalBytes()));
  }

  @Test
  void assignsTheSameOccurrencesRegardlessOfSourceOrder() {
    SourceFile first =
        SourceFile.of(Path.of("a.norm"), "Integer first(Integer value) { return value + 1 }");
    SourceFile second =
        SourceFile.of(Path.of("z.norm"), "Integer second(Integer value) { return value + 1 }");
    SourceFile entry =
        SourceFile.of(
            Path.of("main.norm"), "Void main() { printLine(first(1)) printLine(second(2)) }");

    CoreCompilation forward =
        compile(new CompilationRequest(entry.id(), List.of(first, second, entry)));
    CoreCompilation reverse =
        compile(new CompilationRequest(entry.id(), List.of(second, first, entry)));
    DefinitionId definition = forward.namespace().definition("", "first").orElseThrow();
    DefinitionOccurrenceId forwardOccurrence =
        forward.namespace().occurrence("", "first").orElseThrow();
    DefinitionOccurrenceId reverseOccurrence =
        reverse.namespace().occurrence("", "first").orElseThrow();

    assertEquals(forwardOccurrence, reverseOccurrence);
    assertEquals(
        forward.authoring().origin(forwardOccurrence).rootSpan().source().id(),
        reverse.authoring().origin(reverseOccurrence).rootSpan().source().id());
    assertTrue(forward.authoring().occurrences(definition).size() >= 2);
    assertEquals(
        ArtifactId.forCompilation(forward, "test"), ArtifactId.forCompilation(reverse, "test"));
  }

  @Test
  void canonicalizesNamespaceOverloadOrderIndependentlyOfSourceLocation() {
    CoreCompilation first =
        compile(
            "first-overloads.norm",
            "Integer choose(Integer value) { return value } "
                + "String choose(String value) { return value } Void main() {}");
    CoreCompilation reordered =
        compile(
            "moved/reordered-overloads.norm",
            "String choose(String value) { return value } "
                + "Integer choose(Integer value) { return value } Void main() {}");

    assertEquals(first.namespace().id(), reordered.namespace().id());
  }

  @Test
  void preservesFileLocalNominalTypeIdentity() {
    SourceFile entry =
        SourceFile.of(
            Path.of("private/items/Main.norm"),
            "package items.internal Void main() { printLine(first()) printLine(second()) }");
    SourceFile first =
        SourceFile.of(
            Path.of("private/items/First.norm"),
            "package items.internal private class Hidden { Integer value } "
                + "public Integer first() { Hidden value = Hidden(value: 1) return value.value }");
    SourceFile second =
        SourceFile.of(
            Path.of("private/items/Second.norm"),
            "package items.internal private class Hidden { Integer value } "
                + "public Integer second() { Hidden value = Hidden(value: 2) return value.value }");

    CoreCompilation compilation =
        compile(new CompilationRequest(entry.id(), List.of(entry, first, second)));
    List<DefinitionId> hiddenTypes =
        compilation.namespace().bindings().stream()
            .filter(binding -> binding.kind() == CoreBindingKind.CLASS)
            .filter(binding -> binding.name().equals("Hidden"))
            .map(CoreBinding::definition)
            .distinct()
            .toList();

    assertEquals(2, hiddenTypes.size());
  }

  @Test
  void propagatesNominalTypeChangesThroughCallableSignatures() {
    CoreCompilation first =
        compile(
            "types/signature.norm",
            "class Box { Integer value } Box pass(Box value) { return value } Void main() {}");
    CoreCompilation changed =
        compile(
            "types/signature.norm",
            "class Box { String value } Box pass(Box value) { return value } Void main() {}");
    DefinitionId firstBox = first.namespace().definition("", "Box").orElseThrow();
    DefinitionId changedBox = changed.namespace().definition("", "Box").orElseThrow();
    DefinitionId firstPass = first.namespace().definition("", "pass").orElseThrow();
    DefinitionId changedPass = changed.namespace().definition("", "pass").orElseThrow();

    assertNotEquals(firstBox, changedBox);
    assertNotEquals(firstPass, changedPass);
    assertTrue(first.dependencies().dependenciesOf(firstPass).contains(firstBox));
    assertTrue(changed.dependencies().dependenciesOf(changedPass).contains(changedBox));
  }

  @Test
  void canonicalizesMutuallyRecursiveNominalTypesAsOneGroup() {
    CoreCompilation compilation =
        compile(
            "types/recursive.norm",
            "class Left { Right? right } class Right { Left? left } Void main() {}");
    DefinitionId left = compilation.namespace().definition("", "Left").orElseThrow();
    DefinitionId right = compilation.namespace().definition("", "Right").orElseThrow();

    assertEquals(left.group(), right.group());
    assertTrue(compilation.dependencies().dependenciesOf(left).contains(right));
    assertTrue(compilation.dependencies().dependenciesOf(right).contains(left));
  }

  @Test
  void keepsPrivateNominalIdentityWhenTheProjectRootMoves() {
    SourceFile firstEntry =
        SourceFile.of(
            Path.of("first-root/project/items/Main.norm"), "package items Void main() {}");
    SourceFile firstType =
        SourceFile.of(
            Path.of("first-root/project/items/Hidden.norm"),
            "package items private class Hidden { Integer value }");
    SourceFile movedEntry =
        SourceFile.of(
            Path.of("moved-root/project/items/Main.norm"), "package items Void main() {}");
    SourceFile movedType =
        SourceFile.of(
            Path.of("moved-root/project/items/Hidden.norm"),
            "package items private class Hidden { Integer value }");

    CoreCompilation first =
        compile(new CompilationRequest(firstEntry.id(), List.of(firstEntry, firstType)));
    CoreCompilation moved =
        compile(new CompilationRequest(movedEntry.id(), List.of(movedEntry, movedType)));

    assertEquals(
        first.namespace().definition("items", "Hidden"),
        moved.namespace().definition("items", "Hidden"));
  }

  @Test
  void separatesPublicNominalTypesByModuleVersion() {
    SourceFile first =
        SourceFile.of(
            Path.of("module-v1/sample/Box.norm"),
            "package sample public class Box { Integer value } Void main() {}");
    SourceFile second =
        SourceFile.of(
            Path.of("module-v2/sample/Box.norm"),
            "package sample public class Box { Integer value } Void main() {}");
    CompilationScope firstScope =
        new CompilationScope(
            new ModuleCoordinate("sample", 1), Map.of(first.id(), "sample/Box.norm"));
    CompilationScope secondScope =
        new CompilationScope(
            new ModuleCoordinate("sample", 2), Map.of(second.id(), "sample/Box.norm"));

    CoreCompilation firstCompilation =
        compile(
            new CompilationRequest(
                new CompilationUnitId(first.id().uri()),
                firstScope,
                first.id(),
                List.of(first),
                Set.of(first.id())));
    CoreCompilation secondCompilation =
        compile(
            new CompilationRequest(
                new CompilationUnitId(second.id().uri()),
                secondScope,
                second.id(),
                List.of(second),
                Set.of(second.id())));

    assertNotEquals(
        firstCompilation.namespace().definition("sample", "Box"),
        secondCompilation.namespace().definition("sample", "Box"));
  }

  @Test
  void includesNominalTypeArityInDefinitionIdentity() {
    CoreCompilation plain = compile("plain-box.norm", "class Box {} Void main() {}");
    CoreCompilation generic = compile("generic-box.norm", "class Box<T> {} Void main() {}");

    assertNotEquals(
        plain.namespace().definition("", "Box"), generic.namespace().definition("", "Box"));
  }

  @Test
  void includesFieldVisibilityInTheNamespaceInterface() {
    CoreCompilation publicField =
        compileExported("package sample public class Box { public Integer value } Void main() {}");
    CoreCompilation privateField =
        compileExported("package sample public class Box { private Integer value } Void main() {}");

    assertNotEquals(publicField.namespace().id(), privateField.namespace().id());
  }

  @Test
  void doesNotExportMembersOfPrivateOwners() {
    CoreCompilation compilation =
        compileExported(
            "package sample private class Hidden { public Integer value() { return 1 } } "
                + "Void main() {}");
    CoreBinding method =
        compilation.namespace().bindings().stream()
            .filter(binding -> binding.kind() == CoreBindingKind.METHOD)
            .filter(binding -> binding.name().equals("value"))
            .findFirst()
            .orElseThrow();

    assertEquals(false, method.exported());
  }

  private static CoreCompilation compile(String path, String text) {
    return new Compiler()
        .compile(SourceFile.of(Path.of(path), text))
        .program()
        .orElseThrow()
        .coreCompilation();
  }

  private static CoreCompilation compile(CompilationRequest request) {
    return new Compiler().compile(request).program().orElseThrow().coreCompilation();
  }

  private static CoreCompilation compileExported(String text) {
    SourceFile source = SourceFile.of(Path.of("exported.norm"), text);
    return compile(new CompilationRequest(source.id(), List.of(source), Set.of(source.id())));
  }
}
