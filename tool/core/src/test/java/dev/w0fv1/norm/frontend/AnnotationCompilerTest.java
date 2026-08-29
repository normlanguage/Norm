package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationReference;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AnnotationCompilerTest {
  @Test
  void storesTypedDeclarationReferencesInAnnotationMetadata() {
    CompilationResult result =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Document implements TypeTarget, RuntimeRetention { "
                + "String description List<Class<?>>? types List<Function<?>>? functions "
                + "List<Field<?, ?>>? fields } "
                + "class User { public String name } "
                + "String lookup(String value) { return value } "
                + "@Document(description: \"API\", types: [User.class], "
                + "functions: [lookup.function], fields: [User.name.field]) "
                + "class Api {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    List<dev.w0fv1.norm.core.CoreAnnotationValue> values =
        result
            .program()
            .orElseThrow()
            .compilation()
            .artifact()
            .metadata()
            .annotations()
            .getFirst()
            .values();
    CoreAnnotationValue.ListValue types =
        assertInstanceOf(CoreAnnotationValue.ListValue.class, values.get(1).value());
    CoreAnnotationValue.ListValue functions =
        assertInstanceOf(CoreAnnotationValue.ListValue.class, values.get(2).value());
    CoreAnnotationValue.ListValue fields =
        assertInstanceOf(CoreAnnotationValue.ListValue.class, values.get(3).value());
    assertInstanceOf(
        CoreAnnotationReference.ClassReference.class, types.values().getFirst().value());
    assertInstanceOf(
        CoreAnnotationReference.CallableReference.class, functions.values().getFirst().value());
    assertInstanceOf(
        CoreAnnotationReference.FieldReference.class, fields.values().getFirst().value());
  }

  @Test
  void rejectsMissingDocumentDeclarationReferences() {
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention")
            + "annotation Document implements TypeTarget, RuntimeRetention { "
            + "List<Class<?>> types } "
            + "@Document(types: [Missing.class]) class Api {} Void main() {}",
        "unknown type");
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention")
            + "annotation Document implements TypeTarget, RuntimeRetention { "
            + "List<Function<?>> functions } "
            + "@Document(functions: [missing.function]) class Api {} Void main() {}",
        "cannot find function");
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention")
            + "annotation Document implements TypeTarget, RuntimeRetention { "
            + "List<Field<?, ?>> fields } class User {} "
            + "@Document(fields: [User.missing.field]) class Api {} Void main() {}",
        "field");
  }

  @Test
  void suppliesNullForOmittedNullableMetadataLists() {
    CompilationResult result =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Document implements TypeTarget, RuntimeRetention { "
                + "String description List<Class<?>>? types List<Function<?>>? functions "
                + "List<Field<?, ?>>? fields } "
                + "@Document(description: \"API\") class Api {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    List<dev.w0fv1.norm.core.CoreAnnotationValue> values =
        result
            .program()
            .orElseThrow()
            .compilation()
            .artifact()
            .metadata()
            .annotations()
            .getFirst()
            .values();
    assertEquals(4, values.size());
    values.subList(1, 4).stream()
        .map(CoreAnnotationValue::value)
        .forEach(value -> assertEquals(CoreAnnotationValue.Null.INSTANCE, value));
  }

  @Test
  void rejectsArrayMetadataParameters() {
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention")
            + "annotation Links implements TypeTarget, RuntimeRetention { "
            + "Array<Class<?>> types }",
        "metadata value types");
  }

  @Test
  void storesNestedListMetadataConstants() {
    CompilationResult result =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Matrix implements TypeTarget, RuntimeRetention { "
                + "List<List<String>> rows } "
                + "@Matrix(rows: [[\"first\"], [\"second\"]]) class Api {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsNonNullCallableReferencesForNullableExactMetadataFields() {
    CompilationResult result =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Link implements TypeTarget, RuntimeRetention { "
                + "Function<String(Integer)>? function } "
                + "String lookup(Integer value) { return value.toString() } "
                + "@Link(function: lookup.function) class Api {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void compilesAnnotationAsIdentityAggregateWithMethodsConstructorAndInterfaces() {
    CompilationResult result =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Label implements TypeTarget, RuntimeRetention { "
                + "String text Label(String initial) { this.text = initial } "
                + "String display() { return text } } "
                + "@Label(initial: \"point\") class Point {} "
                + "Void main() { Label label = Label(initial: \"direct\") "
                + "label.text = \"changed\" printLine(label.display()) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    CoreDefinition definition =
        result
            .program()
            .orElseThrow()
            .compilation()
            .artifact()
            .program()
            .definition(definition(result, "Label"))
            .orElseThrow();
    assertEquals(CoreAggregateKind.ANNOTATION, ((CoreDefinition.Aggregate) definition).kind());
  }

  @Test
  void derivesTargetsAndRetentionFromNominalInterfaceConformance() {
    CompilationResult result =
        compile(
            policies("AnnotationTarget", "TypeTarget", "AnnotationRetention", "RuntimeRetention")
                + "interface ModelTarget extends TypeTarget {} "
                + "annotation Label implements ModelTarget, RuntimeRetention { String text } "
                + "@Label(text: \"point\") value Point {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    assertEquals(
        1, result.program().orElseThrow().compilation().artifact().metadata().annotations().size());
  }

  @Test
  void requiresOneTargetAndExactlyOneRetention() {
    assertDiagnostic(
        policies("RuntimeRetention") + "annotation MissingTarget implements RuntimeRetention {}",
        "target interface");
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention", "BinaryRetention")
            + "annotation Ambiguous implements TypeTarget, RuntimeRetention, BinaryRetention {}",
        "exactly one");
    assertDiagnostic(
        policies("TypeTarget") + "annotation MissingRetention implements TypeTarget {}",
        "retention interface");
  }

  @Test
  void reservesPolicyInterfacesForAnnotationTypes() {
    assertDiagnostic(
        policies("TypeTarget") + "class Invalid implements TypeTarget {}",
        "only be implemented by annotation");
  }

  @Test
  void validatesApplicationTargetArgumentsConstantsAndDuplicates() {
    String prefix =
        policies("TypeTarget", "FieldTarget", "RuntimeRetention")
            + "annotation Label implements FieldTarget, RuntimeRetention { String text } ";
    assertDiagnostic(prefix + "@Label(text: \"x\") class Box {}", "target");
    assertDiagnostic(prefix + "class Box { @Label(value: \"x\") Integer value }", "parameter");
    assertDiagnostic(prefix + "class Box { @Label() Integer value }", "required");
    assertDiagnostic(
        prefix
            + "String make() { return \"x\" } class Box { "
            + "@Label(text: make()) Integer value }",
        "constant");
    assertDiagnostic(
        prefix + "class Box { @Label(text: \"a\") @Label(text: \"b\") Integer value }",
        "duplicate");
  }

  @Test
  void compilesPassiveFieldMetadataWithoutAnInterceptor() {
    CompilationResult result =
        compile(
            "package std.annotation "
                + "public interface AnnotationTarget {} "
                + "public interface FieldTarget extends AnnotationTarget {} "
                + "public interface AnnotationRetention {} "
                + "public interface RuntimeRetention extends AnnotationRetention {} "
                + "annotation Label implements FieldTarget, RuntimeRetention { String text } "
                + "value User { @Label(text: \"user_name\") String name } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    var artifact = result.program().orElseThrow().compilation().artifact();
    assertEquals(1, artifact.metadata().annotations().size());
    CoreDefinition.Aggregate user =
        (CoreDefinition.Aggregate)
            artifact.program().definition(definition(result, "User")).orElseThrow();
    assertTrue(user.fields().getFirst().interceptors().isEmpty());
  }

  @Test
  void rejectsGenericAnnotationsAndNonMetadataApplicationConstructors() {
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention")
            + "annotation Label<T> implements TypeTarget, RuntimeRetention { String text }",
        "type parameters");
    assertDiagnostic(
        policies("TypeTarget", "RuntimeRetention")
            + "annotation Label implements TypeTarget, RuntimeRetention { "
            + "Array<String> values Label(Array<String> values) { this.values = values } }",
        "metadata value types");
  }

  @Test
  void rejectsFunctionInterceptorOnRefSignaturesAndInterfaceRequirements() {
    String prefix = functionPolicies();
    assertDiagnostic(
        prefix
            + "annotation Log implements FunctionInterceptor, RuntimeRetention {} "
            + "@Log() Void mutate(ref<Integer> value) {}",
        "ref signature");
    assertDiagnostic(
        prefix
            + "annotation Log implements FunctionInterceptor, RuntimeRetention {} "
            + "interface Reader { @Log() String read() }",
        "concrete function");
  }

  @Test
  void compilesTypedParameterInterceptorIntoTheParameterModel() {
    CompilationResult result =
        compile(
            parameterPolicies()
                + "annotation Normalize implements ParameterInterceptor<String>, RuntimeRetention { "
                + "String before(ParameterContext context, String value) { return value } } "
                + "String echo(@Normalize() String value) { return value } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    CoreDefinition.Callable callable =
        (CoreDefinition.Callable)
            result
                .program()
                .orElseThrow()
                .compilation()
                .artifact()
                .program()
                .definition(definition(result, "echo"))
                .orElseThrow();
    assertEquals("value", callable.parameters().getFirst().name());
    assertEquals(1, callable.parameters().getFirst().interceptors().size());
  }

  @Test
  void rejectsParameterInterceptorTypeMismatchReferenceAndInterfaceRequirement() {
    String prefix =
        parameterPolicies()
            + "annotation Normalize implements ParameterInterceptor<String>, RuntimeRetention {} ";
    assertDiagnostic(
        prefix + "Integer echo(@Normalize() Integer value) { return value } Void main() {}",
        "does not match parameter type");
    assertDiagnostic(
        prefix + "Void mutate(@Normalize() ref<String> value) {} Void main() {}", "ref parameter");
    assertDiagnostic(
        prefix + "interface Reader { String read(@Normalize() String value) } Void main() {}",
        "concrete callable parameter");
  }

  @Test
  void compilesTypedFieldInterceptorIntoTheFieldModel() {
    CompilationResult result =
        compile(
            fieldPolicies()
                + "annotation Normalize implements FieldInterceptor<String>, RuntimeRetention { "
                + "String before(FieldContext context, String value) { return value } } "
                + "class Box { @Normalize() String value } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    CoreDefinition.Aggregate aggregate =
        (CoreDefinition.Aggregate)
            result
                .program()
                .orElseThrow()
                .compilation()
                .artifact()
                .program()
                .definition(definition(result, "Box"))
                .orElseThrow();
    assertEquals("value", aggregate.fields().getFirst().name());
    assertEquals(1, aggregate.fields().getFirst().interceptors().size());
  }

  @Test
  void rejectsFieldInterceptorTypeMismatch() {
    String prefix =
        fieldPolicies()
            + "annotation Normalize implements FieldInterceptor<String>, RuntimeRetention {} ";
    assertDiagnostic(
        prefix + "class Box { @Normalize() Integer value } Void main() {}",
        "does not match field type");
  }

  @Test
  void allowsParameterInterceptorBesideAnUnannotatedReferenceParameter() {
    CompilationResult result =
        compile(
            parameterPolicies()
                + "annotation Normalize implements ParameterInterceptor<String>, RuntimeRetention {} "
                + "Void mutate(ref<Integer> count, @Normalize() String value) {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void storesBinaryAndRuntimeMetadataButKeepsSourceInterceptorsInCoreCallable() {
    CompilationResult result =
        compile(
            functionPolicies()
                + "public interface TypeTarget {} "
                + "public interface SourceRetention {} "
                + "public interface BinaryRetention {} "
                + "annotation SourceOnly implements FunctionInterceptor, SourceRetention {} "
                + "annotation BinaryOnly implements TypeTarget, BinaryRetention {} "
                + "annotation RuntimeVisible implements TypeTarget, RuntimeRetention {} "
                + "@SourceOnly() Void run() {} "
                + "@BinaryOnly() @RuntimeVisible() value Point {} Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    var artifact = result.program().orElseThrow().compilation().artifact();
    assertEquals(2, artifact.metadata().annotations().size());
    CoreDefinition.Callable callable =
        (CoreDefinition.Callable)
            artifact.program().definition(definition(result, "run")).orElseThrow();
    assertEquals(1, callable.interceptors().size());
  }

  @Test
  void storesFunctionMetadataOnInterfaceRequirements() {
    CompilationResult result =
        compile(
            policies("FunctionTarget", "BinaryRetention")
                + "annotation Document implements FunctionTarget, BinaryRetention { "
                + "String description } interface Reader { "
                + "@Document(description: \"Reads the next value.\") String read() } "
                + "Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    assertEquals(
        1, result.program().orElseThrow().compilation().artifact().metadata().annotations().size());
  }

  @Test
  void applicationsDoNotChangeTargetIdentityButConformanceChangesAnnotationIdentity() {
    CompilationResult first =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Label implements TypeTarget, RuntimeRetention { String text } "
                + "@Label(text: \"first\") value Point {} Void main() {}");
    CompilationResult second =
        compile(
            policies("TypeTarget", "RuntimeRetention")
                + "annotation Label implements TypeTarget, RuntimeRetention { String text } "
                + "@Label(text: \"second\") value Point {} Void main() {}");
    CompilationResult binary =
        compile(
            policies("TypeTarget", "BinaryRetention")
                + "annotation Label implements TypeTarget, BinaryRetention { String text } "
                + "value Point {} Void main() {}");

    assertEquals(definition(first, "Point"), definition(second, "Point"));
    assertNotEquals(definition(first, "Label"), definition(binary, "Label"));
  }

  @Test
  void requiresAnnotationTypeForReflectionQuery() {
    assertDiagnostic(
        "class Box {} Void main() { Class<Box> type = Box.class "
            + "Box? value = type.annotation<Box>() }",
        "annotation type");
  }

  @Test
  void annotationRemainsAvailableAsIdentifier() {
    CompilationResult result =
        compile(
            "class annotation {} annotation make() { return annotation() } "
                + "Void main() { printLine(make()) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void annotationRemainsAvailableAsAGenericFunctionReturnType() {
    CompilationResult result =
        compile(
            "class annotation {} annotation make<T>(T ignored) { return annotation() } "
                + "Void main() { printLine(make(ignored: 1)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void doesNotRecognizePolicyNamesOutsideTheStdModule() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("foreign.norm"),
                    "package std.annotation public interface TypeTarget {} "
                        + "public interface RuntimeRetention {} "
                        + "annotation Label implements TypeTarget, RuntimeRetention {} "
                        + "Void main() {}"));

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(value -> value.message().contains("annotation target interface")),
        () -> result.diagnostics().toString());
  }

  private static String policies(String... names) {
    return "package std.annotation public interface AnnotationTarget {} "
        + "public interface AnnotationRetention {} "
        + java.util.Arrays.stream(names)
            .filter(name -> !name.equals("AnnotationTarget") && !name.equals("AnnotationRetention"))
            .map(
                name ->
                    "public interface "
                        + name
                        + " extends "
                        + (name.endsWith("Retention") ? "AnnotationRetention" : "AnnotationTarget")
                        + " {} ")
            .collect(java.util.stream.Collectors.joining());
  }

  private static String functionPolicies() {
    return "package std.annotation "
        + "public interface AnnotationTarget {} "
        + "public interface FunctionTarget extends AnnotationTarget {} "
        + "public interface FunctionInterceptor extends FunctionTarget { "
        + "Void before(FunctionContext context) {} "
        + "R around<R>(FunctionInvocation<R> invocation) { return invocation.proceed() } "
        + "Void after(FunctionContext context, FunctionCompletion completion) {} } "
        + "public interface AnnotationRetention {} "
        + "public interface RuntimeRetention extends AnnotationRetention {} ";
  }

  private static String parameterPolicies() {
    return "package std.annotation "
        + "public interface AnnotationTarget {} "
        + "public interface ParameterTarget extends AnnotationTarget {} "
        + "public interface ParameterInterceptor<T> extends ParameterTarget { "
        + "T before(ParameterContext context, T value) { return value } "
        + "Void after(ParameterContext context, FunctionCompletion completion) {} } "
        + "public interface AnnotationRetention {} "
        + "public interface RuntimeRetention extends AnnotationRetention {} ";
  }

  private static String fieldPolicies() {
    return "package std.annotation "
        + "public interface AnnotationTarget {} "
        + "public interface FieldTarget extends AnnotationTarget {} "
        + "public interface FieldInterceptor<T> extends FieldTarget { "
        + "T before(FieldContext context, T value) { return value } "
        + "Void after(FieldContext context, FunctionCompletion completion) {} } "
        + "public interface AnnotationRetention {} "
        + "public interface RuntimeRetention extends AnnotationRetention {} ";
  }

  private static void assertDiagnostic(String source, String fragment) {
    CompilationResult result = compile(source);
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream().anyMatch(value -> value.message().contains(fragment)),
        () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    SourceFile source = SourceFile.of(Path.of("annotations.norm"), text);
    return new CompilerSession()
        .compile(
            new CompilationRequest(
                new CompilationUnitId(source.id().uri()),
                CompilationScope.module(
                    new ModuleCoordinate("std", 1), Map.of(source.id(), "annotations.norm")),
                source.id(),
                List.of(source),
                Set.of()));
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
