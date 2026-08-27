package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnnotationCompilerTest {
  @Test
  void compilesExplicitPoliciesDefaultsTargetsAndTypedReflection() {
    CompilationResult result =
        compile(
            "annotation Label targets(type, field, function, parameter, local) retention(runtime) { "
                + "String text String? replacement = null } "
                + "@Label(text: \"point\") value Point { "
                + "@Label(text: \"x\") Integer x } "
                + "@Label(text: \"read\") String read("
                + "@Label(text: \"value\") String value) { "
                + "@Label(text: \"copy\") String copy = value return copy } "
                + "Void main() { Type<Point> type = reflect<Point>() "
                + "Label? label = type.annotation<Label>() printLine(type.name()) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void validatesTargetArgumentsConstantsAndDuplicates() {
    assertDiagnostic(
        "annotation Label targets(field) retention(runtime) { String text } "
            + "@Label(text: \"x\") class Box { Integer value } Void main() {}",
        "target");
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "@Label(value: \"x\") class Box {} Void main() {}",
        "parameter");
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "@Label() class Box {} Void main() {}",
        "required");
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "String make() { return \"x\" } @Label(text: make()) class Box {} Void main() {}",
        "constant");
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "@Label(text: \"a\") @Label(text: \"b\") class Box {} Void main() {}",
        "duplicate");
  }

  @Test
  void rejectsInvalidAnnotationDeclarationsAndDirectConstruction() {
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { List<String> values } Void main() {}",
        "field type");
    assertDiagnostic(
        "annotation Label<T> targets(type) retention(runtime) { String text } Void main() {}",
        "type parameters");
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "Void main() { Label label = Label(text: \"x\") }",
        "metadata");
  }

  @Test
  void requiresAnnotationTypeForReflectionQuery() {
    assertDiagnostic(
        "class Box {} Void main() { Type<Box> type = reflect<Box>() "
            + "Box? value = type.annotation<Box>() }",
        "annotation type");
  }

  @Test
  void reflectionRequiresNominalTypes() {
    assertDiagnostic(
        "Void main() { Type<ref<Integer>> type = reflect<ref<Integer>>() }", "nominal type");
  }

  @Test
  void annotationRemainsAvailableAsAnIdentifier() {
    CompilationResult result =
        compile(
            "class annotation {} annotation make() { return annotation() } "
                + "Void main() { printLine(make()) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsAnnotationFieldMutation() {
    assertDiagnostic(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "@Label(text: \"point\") value Point {} Void main() { "
            + "Label? label = reflect<Point>().annotation<Label>() "
            + "if (label != null) { label.text = \"changed\" } }",
        "cannot be assigned");
  }

  @Test
  void compilesEveryDeclaredTargetIncludingPackageAndConstructor() {
    CompilationResult result =
        compile(
            "@Marker(text: \"package\") package sample "
                + "annotation Marker targets(package, type, field, constructor, function, "
                + "parameter, local) retention(binary) { String text } "
                + "@Marker(text: \"box\") class Box { "
                + "@Marker(text: \"field\") Integer value "
                + "@Marker(text: \"constructor\") Box("
                + "@Marker(text: \"parameter\") Integer value) { this.value = value } "
                + "@Marker(text: \"method\") Integer read("
                + "@Marker(text: \"method parameter\") Integer fallback) { "
                + "@Marker(text: \"local\") Integer copy = value return copy } } "
                + "Void main() { Box box = Box(value: 1) printLine(box.read(fallback: 0)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void compilesInterfaceMethodAndParameterAnnotations() {
    CompilationResult result =
        compile(
            "annotation Marker targets(function, parameter) retention(binary) { String text } "
                + "interface Reader { @Marker(text: \"method\") String read("
                + "@Marker(text: \"parameter\") String input) } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void storesOnlyBinaryAndRuntimeApplicationsInCore() {
    CompilationResult result =
        compile(
            "annotation SourceOnly targets(type) retention(source) { String text } "
                + "annotation BinaryOnly targets(type) retention(binary) { String text } "
                + "annotation RuntimeVisible targets(type) retention(runtime) { "
                + "String text String? replacement = null } "
                + "@SourceOnly(text: \"source\") @BinaryOnly(text: \"binary\") "
                + "@RuntimeVisible(text: \"runtime\") value Point { Integer x } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    var applications =
        result.program().orElseThrow().compilation().artifact().metadata().annotations();
    assertEquals(2, applications.size());
    assertTrue(
        applications.stream()
            .allMatch(value -> value.values().size() == 2 || value.values().size() == 1));
    assertTrue(
        applications.stream()
            .anyMatch(
                value -> value.values().size() == 2 && value.values().get(1).value() == null));
  }

  @Test
  void annotationApplicationsDoNotChangeTargetIdentityButPoliciesChangeSchemaIdentity() {
    CompilationResult first =
        compile(
            "annotation Label targets(type) retention(runtime) { String text } "
                + "@Label(text: \"first\") value Point { Integer x } Void main() {}");
    CompilationResult second =
        compile(
            "annotation Label targets(type) retention(runtime) { String text } "
                + "@Label(text: \"second\") value Point { Integer x } Void main() {}");
    CompilationResult binary =
        compile(
            "annotation Label targets(type) retention(binary) { String text } "
                + "value Point { Integer x } Void main() {}");

    assertEquals(definition(first, "Point"), definition(second, "Point"));
    assertNotEquals(definition(first, "Label"), definition(binary, "Label"));
  }

  @Test
  void metadataTargetsExactOccurrencesForIsomorphicCallables() {
    CompilationResult result =
        compile(
            "annotation Marker targets(function, parameter, local) retention(binary) { String text } "
                + "@Marker(text: \"first\") Integer first(@Marker(text: \"p1\") Integer input) { "
                + "@Marker(text: \"l1\") Integer copy = input return copy } "
                + "@Marker(text: \"second\") Integer second(@Marker(text: \"p2\") Integer input) { "
                + "@Marker(text: \"l2\") Integer copy = input return copy } "
                + "Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    var targets =
        result.program().orElseThrow().compilation().artifact().metadata().annotations().stream()
            .map(value -> value.target())
            .filter(dev.w0fv1.norm.core.CoreAnnotationTarget.Definition.class::isInstance)
            .map(dev.w0fv1.norm.core.CoreAnnotationTarget.Definition.class::cast)
            .map(value -> value.occurrence())
            .toList();
    assertEquals(2, targets.size());
    assertEquals(targets.get(0).representative(), targets.get(1).representative());
    assertNotEquals(targets.get(0), targets.get(1));
  }

  @Test
  void distinguishesConstructorAndMethodMetadataWhenTheirCoreBodiesMatch() {
    CompilationResult result =
        compile(
            "annotation Marker targets(constructor, function) retention(binary) {} "
                + "class Box { @Marker() Box() {} @Marker() Void reset() {} } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    assertEquals(
        java.util.Set.of(
            dev.w0fv1.norm.value.AnnotationTarget.CONSTRUCTOR,
            dev.w0fv1.norm.value.AnnotationTarget.FUNCTION),
        result.program().orElseThrow().compilation().artifact().metadata().annotations().stream()
            .map(value -> value.target().kind())
            .collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void incrementalReusePreservesLocalMetadata() {
    CompilerSession session = new CompilerSession();
    SourceFile first =
        SourceFile.of(
            Path.of("incremental-annotations.norm"),
            "annotation Marker targets(local) retention(binary) {} "
                + "Integer kept() { @Marker() Integer value = 1 return value } "
                + "Void main() { printLine(1) }");
    SourceFile changed =
        SourceFile.of(
            Path.of("incremental-annotations.norm"),
            "annotation Marker targets(local) retention(binary) {} "
                + "Integer kept() { @Marker() Integer value = 1 return value } "
                + "Void main() { printLine(2) }");
    assertTrue(session.compile(first).isSuccess());
    CompilationResult result = session.compile(changed);

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    assertEquals(
        1,
        result.program().orElseThrow().compilation().artifact().metadata().annotations().stream()
            .filter(value -> value.target().kind() == dev.w0fv1.norm.value.AnnotationTarget.LOCAL)
            .count());
  }

  @Test
  void packageTargetsAreUniquePerModuleCoordinate() {
    SourceFile library =
        SourceFile.of(
            Path.of("library/Marker.norm"),
            "@Marker() package shared public annotation Marker targets(package) retention(binary) {}");
    SourceFile application =
        SourceFile.of(
            Path.of("application/Main.norm"),
            "@Marker() package shared import shared.Marker Void main() {}");
    dev.w0fv1.norm.value.ModuleCoordinate libraryModule =
        new dev.w0fv1.norm.value.ModuleCoordinate("library", 1);
    dev.w0fv1.norm.value.ModuleCoordinate applicationModule =
        new dev.w0fv1.norm.value.ModuleCoordinate("application", 1);
    dev.w0fv1.norm.value.CompilationScope scope =
        new dev.w0fv1.norm.value.CompilationScope(
            java.util.Map.of(
                library.id(),
                new dev.w0fv1.norm.value.ModuleSourceCoordinate(
                    libraryModule, "shared/Marker.norm"),
                application.id(),
                new dev.w0fv1.norm.value.ModuleSourceCoordinate(
                    applicationModule, "shared/Main.norm")),
            new dev.w0fv1.norm.value.ModuleGraph(
                java.util.Map.of(
                    libraryModule, java.util.Set.of(),
                    applicationModule, java.util.Set.of(libraryModule))));
    dev.w0fv1.norm.value.CompilationRequest request =
        new dev.w0fv1.norm.value.CompilationRequest(
            new dev.w0fv1.norm.value.CompilationUnitId(application.id().uri()),
            scope,
            application.id(),
            List.of(library, application),
            java.util.Set.of(library.id()));
    CompilationResult result = new CompilerSession().compile(request);

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    assertEquals(
        2,
        result.program().orElseThrow().compilation().artifact().metadata().annotations().stream()
            .filter(value -> value.target().kind() == dev.w0fv1.norm.value.AnnotationTarget.PACKAGE)
            .count());
  }

  private static void assertDiagnostic(String source, String fragment) {
    CompilationResult result = compile(source);
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream().anyMatch(value -> value.message().contains(fragment)),
        () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("annotations.norm"), text));
  }

  private static DefinitionId definition(CompilationResult result, String name) {
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    return result.program().orElseThrow().compilation().artifact().namespace().bindings().stream()
        .filter(binding -> binding.name().equals(name))
        .findFirst()
        .orElseThrow()
        .definition();
  }
}
