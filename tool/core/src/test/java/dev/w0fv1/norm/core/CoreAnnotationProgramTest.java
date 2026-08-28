package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.frontend.CompilerSession;
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

final class CoreAnnotationProgramTest {
  @Test
  void verifiesNormalizedRuntimeApplications() {
    CoreArtifact artifact =
        compile(
            policies("RuntimeRetention")
                + "annotation Marker implements TypeTarget, RuntimeRetention { String text } "
                + "@Marker(text: \"value\") value Point {} Void main() {}");

    assertEquals(1, artifact.metadata().annotations().size());
    assertDoesNotThrow(
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                artifact.metadata()));
  }

  @Test
  void rejectsMalformedValuesAndDuplicates() {
    CoreArtifact artifact =
        compile(
            policies("RuntimeRetention")
                + "annotation Marker implements TypeTarget, RuntimeRetention { String text } "
                + "@Marker(text: \"value\") value Point {} Void main() {}");
    CoreAnnotationApplication application = artifact.metadata().annotations().getFirst();
    CoreAnnotationApplication malformed =
        new CoreAnnotationApplication(application.annotation(), application.target(), List.of());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                new CoreMetadata(List.of(malformed))));
    assertThrows(
        IllegalArgumentException.class, () -> new CoreMetadata(List.of(application, application)));
  }

  @Test
  void rejectsCallableReferencesWhoseSignatureDoesNotMatchTheAnnotationField() {
    CoreArtifact artifact =
        compile(
            policies("RuntimeRetention")
                + "annotation Link implements TypeTarget, RuntimeRetention { "
                + "Function<String(Integer)> function } "
                + "String selected(Integer value) { return value.toString() } "
                + "String incompatible(String value) { return value } "
                + "@Link(function: selected.function) class Api {} Void main() {}");
    CoreAnnotationApplication application = artifact.metadata().annotations().getFirst();
    CoreAnnotationValue original = application.values().getFirst();
    CoreAnnotationReference.CallableReference reference =
        (CoreAnnotationReference.CallableReference) original.value();
    DefinitionId incompatible =
        artifact.namespace().definition("std.annotation", "incompatible").orElseThrow();
    CoreAnnotationReference.CallableReference malformed =
        new CoreAnnotationReference.CallableReference(
            new DefinitionReference.External(incompatible),
            reference.receiverTypeArguments(),
            reference.reifiedArguments(),
            reference.virtual());
    CoreAnnotationApplication replaced =
        new CoreAnnotationApplication(
            application.annotation(),
            application.target(),
            List.of(new CoreAnnotationValue(original.type(), malformed)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                new CoreMetadata(List.of(replaced))));
  }

  @Test
  void rejectsListMetadataWhoseElementTypeDoesNotMatch() {
    CoreArtifact artifact =
        compile(
            policies("RuntimeRetention")
                + "annotation Links implements TypeTarget, RuntimeRetention { "
                + "List<Class<?>> types } "
                + "class User {} @Links(types: [User.class]) class Api {} Void main() {}");
    CoreAnnotationApplication application = artifact.metadata().annotations().getFirst();
    CoreAnnotationValue original = application.values().getFirst();
    CoreAnnotationValue malformed =
        new CoreAnnotationValue(
            original.type(),
            new CoreAnnotationValue.ListValue(
                List.of(
                    new CoreAnnotationValue(
                        CoreType.STRING, new CoreAnnotationValue.Literal("User")))));
    CoreAnnotationApplication replaced =
        new CoreAnnotationApplication(
            application.annotation(), application.target(), List.of(malformed));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                new CoreMetadata(List.of(replaced))));
  }

  @Test
  void sourceRetentionApplicationsAreAbsent() {
    CoreArtifact artifact =
        compile(
            policies("SourceRetention")
                + "annotation Marker implements TypeTarget, SourceRetention { String text } "
                + "@Marker(text: \"value\") value Point {} Void main() {}");

    assertEquals(List.of(), artifact.metadata().annotations());
  }

  @Test
  void requiresStoredFunctionApplicationsToMatchCallableInterceptors() {
    CoreArtifact artifact =
        compile(
            "package std.annotation public interface AnnotationTarget {} "
                + "public interface FunctionTarget extends AnnotationTarget { "
                + "} public interface FunctionInterceptor extends FunctionTarget { "
                + "Void before(FunctionContext context) {} "
                + "R around<R>(FunctionInvocation<R> invocation) { return invocation.proceed() } "
                + "Void after(FunctionContext context, FunctionCompletion completion) {} } "
                + "public interface AnnotationRetention {} "
                + "public interface RuntimeRetention extends AnnotationRetention {} "
                + "annotation Trace implements FunctionInterceptor, RuntimeRetention {} "
                + "@Trace() Void run() {} Void main() { run() }");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                new CoreMetadata(List.of())));
  }

  @Test
  void rejectsMalformedFunctionInterceptorProtocolAtTheCoreBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            compile(
                "package std.annotation public interface AnnotationTarget {} "
                    + "public interface FunctionTarget extends AnnotationTarget {} "
                    + "public interface FunctionInterceptor extends FunctionTarget {} "
                    + "Void main() {}"));
  }

  @Test
  void requiresStoredParameterApplicationsToMatchParameterInterceptors() {
    CoreArtifact artifact =
        compile(
            "package std.annotation public interface AnnotationTarget {} "
                + "public interface ParameterTarget extends AnnotationTarget {} "
                + "public interface ParameterInterceptor<T> extends ParameterTarget { "
                + "T before(ParameterContext context, T value) { return value } "
                + "Void after(ParameterContext context, FunctionCompletion completion) {} } "
                + "public interface AnnotationRetention {} "
                + "public interface RuntimeRetention extends AnnotationRetention {} "
                + "annotation Normalize implements ParameterInterceptor<String>, RuntimeRetention {} "
                + "String echo(@Normalize() String value) { return value } "
                + "Void main() { echo(value: \"value\") }");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                new CoreMetadata(List.of())));
  }

  @Test
  void rejectsMalformedParameterInterceptorProtocolAtTheCoreBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            compile(
                "package std.annotation public interface AnnotationTarget {} "
                    + "public interface ParameterTarget extends AnnotationTarget {} "
                    + "public interface ParameterInterceptor<T> extends ParameterTarget {} "
                    + "Void main() {}"));
  }

  @Test
  void requiresStoredFieldApplicationsToMatchFieldInterceptors() {
    CoreArtifact artifact =
        compile(
            "package std.annotation public interface AnnotationTarget {} "
                + "public interface FieldTarget extends AnnotationTarget {} "
                + "public interface FieldInterceptor<T> extends FieldTarget { "
                + "T before(FieldContext context, T value) { return value } "
                + "Void after(FieldContext context, FunctionCompletion completion) {} } "
                + "public interface AnnotationRetention {} "
                + "public interface RuntimeRetention extends AnnotationRetention {} "
                + "annotation Normalize implements FieldInterceptor<String>, RuntimeRetention {} "
                + "class Box { @Normalize() String value } "
                + "Void main() { Box(value: \"value\") }");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreArtifact(
                artifact.program(),
                artifact.namespace(),
                artifact.authoring(),
                new CoreMetadata(List.of())));
  }

  @Test
  void rejectsMalformedFieldInterceptorProtocolAtTheCoreBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            compile(
                "package std.annotation public interface AnnotationTarget {} "
                    + "public interface FieldTarget extends AnnotationTarget {} "
                    + "public interface FieldInterceptor<T> extends FieldTarget {} "
                    + "Void main() {}"));
  }

  private static CoreArtifact compile(String source) {
    SourceFile file = SourceFile.of(Path.of("metadata.norm"), source);
    return new CompilerSession()
        .compile(
            new CompilationRequest(
                new CompilationUnitId(file.id().uri()),
                CompilationScope.module(
                    new ModuleCoordinate("std", 1), Map.of(file.id(), "metadata.norm")),
                file.id(),
                List.of(file),
                Set.of()))
        .program()
        .orElseThrow()
        .compilation()
        .artifact();
  }

  private static String policies(String retention) {
    return "package std.annotation public interface AnnotationTarget {} "
        + "public interface TypeTarget extends AnnotationTarget {} "
        + "public interface AnnotationRetention {} public interface "
        + retention
        + " extends AnnotationRetention {} ";
  }
}
