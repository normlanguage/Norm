package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModuleManifestParserTest {
  private final ModuleManifestParser parser = new ModuleManifestParser();

  @Test
  void parsesExportedSourceNames() {
    SourceFile source =
        SourceFile.of(
            Path.of("module.norm"),
            "Module(exports: [\"math.integer\", \"text.builders\"], "
                + "version: 1, name: \"std\")");

    ModuleManifest manifest = parser.parse(source);

    assertEquals("std", manifest.name());
    assertEquals(1, manifest.version());
    assertEquals(List.of("math.integer", "text.builders"), manifest.exports());
    assertEquals("std/math/integer.norm", manifest.sourcePath("math.integer"));
  }

  @Test
  void rejectsDuplicateAndEscapingPaths() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.parse(
                SourceFile.of(
                    Path.of("module.norm"),
                    "Module(name: \"std\", version: 1, "
                        + "exports: [\"outside..value\", \"outside..value\"])")));
  }

  @Test
  void rejectsTheRemovedPackageField() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.parse(
                SourceFile.of(
                    Path.of("module.norm"), "Module(package: \"std\", version: 1, exports: [])")));
  }

  @Test
  void acceptsPositiveModuleVersions() {
    ModuleManifest manifest =
        parser.parse(
            SourceFile.of(
                Path.of("module.norm"), "Module(name: \"std\", version: 2, exports: [])"));

    assertEquals(2, manifest.version());
  }

  @Test
  void requiresAnExportedSourceToDeclareItsMappedPackage() {
    SourceFile manifestSource =
        SourceFile.of(
            Path.of("module.norm"),
            "Module(name: \"std\", version: 1, exports: [\"math.integer\"])");
    ModuleManifest manifest = parser.parse(manifestSource);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.validateExport(
                manifest,
                "math.integer",
                SourceFile.of(
                    Path.of("std/math/integer.norm"), "package std.text Void value() {}")));
  }
}
