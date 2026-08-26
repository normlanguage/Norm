package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.SourceFile;
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
