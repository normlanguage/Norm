package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CoreAnnotationProgramTest {
  @Test
  void verifiesNormalizedRuntimeApplications() {
    CoreArtifact artifact =
        compile(
            "annotation Marker targets(type) retention(runtime) { String text } "
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
            "annotation Marker targets(type) retention(runtime) { String text } "
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
  void sourceRetentionApplicationsAreAbsent() {
    CoreArtifact artifact =
        compile(
            "annotation Marker targets(type) retention(source) { String text } "
                + "@Marker(text: \"value\") value Point {} Void main() {}");

    assertEquals(List.of(), artifact.metadata().annotations());
  }

  private static CoreArtifact compile(String source) {
    return new CompilerSession()
        .compile(SourceFile.of(Path.of("metadata.norm"), source))
        .program()
        .orElseThrow()
        .compilation()
        .artifact();
  }
}
