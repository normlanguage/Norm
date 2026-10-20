package dev.w0fv1.norm.pyrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;

final class PythonRuntimeCatalogTest {
  @Test
  void loadsEverySupportedDistributionFromThePublishedManifest() throws IOException {
    PythonRuntimeCatalog catalog = PythonRuntimeCatalog.current();

    assertEquals("3.14.8+20261013", catalog.version());
    for (PythonRuntimePlatform platform : PythonRuntimePlatform.values()) {
      PythonRuntimeDistribution distribution = catalog.distribution(platform);
      assertNotNull(distribution.archive());
      assertEquals(64, distribution.integrity().value().length());
    }
  }
}
