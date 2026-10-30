package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModuleLoaderTest {
  @Test
  void loadsInternalPackagePeersWithoutExportingThem() throws Exception {
    Map<String, String> resources = new LinkedHashMap<>();
    resources.put(
        "sample/math/Numbers.norm",
        "package sample.math public Integer twice(Integer value) { return helper(value) * 2 }");
    resources.put(
        "sample/math/Helper.norm",
        "package sample.math public Integer helper(Integer value) { return value }");
    resources.put(
        "sample/internal/Hidden.norm",
        "package sample.internal private Integer hidden() { return 1 }");

    ModuleLoader.LoadedModule loaded =
        new ModuleLoader()
            .load(
                new MemoryResolver(resources),
                new ModuleDescriptor("sample", 1, List.of("math.Numbers")));

    assertEquals(3, loaded.sources().size());
    assertEquals(1, loaded.exportedSources().size());
  }

  @Test
  void validatesUnqualifiedAndNamespacedSourcesThroughTheSameLoader() throws Exception {
    var descriptor = new ModuleDescriptor("sample", 1, List.of());
    var loaded =
        new ModuleLoader()
            .load(
                new MemoryResolver(
                    Map.of(
                        "main.norm",
                        "Void main() {}",
                        "sample/Type.norm",
                        "package sample Integer answer() { return 42 }")),
                descriptor);
    assertEquals(2, loaded.sources().size());
    assertEquals(0, loaded.exportedSources().size());
    org.junit.jupiter.api.Assertions.assertThrows(
        IOException.class,
        () ->
            new ModuleLoader()
                .load(
                    new MemoryResolver(
                        Map.of("other/Type.norm", "package other Integer answer() { return 42 }")),
                    descriptor));
    org.junit.jupiter.api.Assertions.assertThrows(
        IOException.class,
        () ->
            new ModuleLoader()
                .load(
                    new MemoryResolver(Map.of("main.norm", "package other Void main() {}")),
                    descriptor));
  }

  private record MemoryResolver(Map<String, String> resources) implements ModuleSourceResolver {
    @Override
    public SourceFile read(String relativePath) throws IOException {
      String text = resources.get(relativePath);
      if (text == null) throw new IOException("missing " + relativePath);
      return SourceFile.of(DocumentId.of("memory:/" + relativePath), text);
    }

    @Override
    public List<String> listSources() {
      return List.copyOf(resources.keySet());
    }
  }
}
