package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.DocumentId;
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
        "module.norm", "Module(name: \"sample\", version: 1, exports: [\"math.Numbers\"])");
    resources.put(
        "sample/math/Numbers.norm",
        "package sample.math public Integer twice(Integer value) { return helper(value) * 2 }");
    resources.put(
        "sample/math/Helper.norm",
        "package sample.math public Integer helper(Integer value) { return value }");

    ModuleLoader.LoadedModule loaded = new ModuleLoader().load(new MemoryResolver(resources));

    assertEquals(2, loaded.sources().size());
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
    public List<String> list(String relativeDirectory) {
      String prefix = relativeDirectory + "/";
      return resources.keySet().stream()
          .filter(path -> path.startsWith(prefix))
          .filter(path -> path.indexOf('/', prefix.length()) < 0)
          .toList();
    }
  }
}
