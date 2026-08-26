package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleBootstrap {
  private static final DocumentId DOCUMENT = DocumentId.of("bootstrap:/module.norm");
  private static final ModuleCoordinate COORDINATE = new ModuleCoordinate("norm.bootstrap", 1);

  private ModuleBootstrap() {}

  public static SourceFile source() {
    try (InputStream stream =
        ModuleBootstrap.class.getModule().getResourceAsStream("bootstrap/module.norm")) {
      if (stream == null) throw new IllegalStateException("missing module bootstrap resource");
      return SourceFile.of(DOCUMENT, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new IllegalStateException("cannot load module bootstrap resource", exception);
    }
  }

  public static ModuleCoordinate coordinate() {
    return COORDINATE;
  }

  public static CompilationPrelude prelude() {
    SourceFile source = source();
    return new CompilationPrelude(
        List.of(source),
        Set.of(),
        CompilationScope.module(COORDINATE, Map.of(source.id(), "bootstrap/module.norm")));
  }
}
