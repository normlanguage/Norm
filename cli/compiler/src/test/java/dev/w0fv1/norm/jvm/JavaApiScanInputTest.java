package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JavaApiScanInputTest {
  @Test
  void ownsTheCompleteMetadataInputWithoutPreparingAnEnvironment() {
    var digest = Sha256Digest.compute(new byte[0]);
    var root =
        new ResolvedJarArtifact(new LocalJarIdentity(digest), Path.of("library.jar"), digest);
    var support =
        new ResolvedJarArtifact(new JdkModuleIdentity("java.base"), Path.of("jdk.jar"), digest);
    var graph = new ResolvedJarGraph(root, List.of(root), List.of());
    var base = new ResolvedJarGraph(support, List.of(support), List.of());
    var input =
        new JavaApiScanInput(
            graph,
            List.of(base, new ResolvedJarGraph(support, List.of(support), List.of()), graph));
    assertEquals(List.of(root, support), input.artifacts());
    assertEquals(List.of(base), input.supportingGraphs());
    assertThrows(UnsupportedOperationException.class, () -> input.supportingGraphs().clear());
  }
}
