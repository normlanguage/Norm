package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModuleDependencySourceEditorTest {
  @Test
  void updatesPinnedAndUnpinnedDependenciesWithoutReformattingTheModule() {
    SourceFile source =
        SourceFile.of(
            DocumentId.of("file:///module.norm"),
            "Module module() { return module(dependencies: [\n"
                + "  dependency(repository: \"github\", name: \"alpha\", version: 1),\n"
                + "  dependency(repository: \"github\", name: \"beta\")]) }\n");
    String updated =
        new ModuleDependencySourceEditor()
            .withVersions(
                source,
                List.of(
                    new ModuleDependencySourceEditor.Update("github", "alpha", 3),
                    new ModuleDependencySourceEditor.Update("github", "beta", 2)));
    assertEquals(
        "Module module() { return module(dependencies: [\n"
            + "  dependency(repository: \"github\", name: \"alpha\", version: 3),\n"
            + "  dependency(repository: \"github\", name: \"beta\", version: 2)]) }\n",
        updated);
  }

  @Test
  void updatesDependenciesDeclaredInStandaloneHelpers() {
    String code =
        "ModuleDependency dep() { dependency(repository: \"github\", name: \"alpha\", version: 1) } Module module() { module(dependencies: [dep()]) }";
    SourceFile source = SourceFile.of(DocumentId.of("file:///module.norm"), code);
    assertEquals(
        code.replace("version: 1", "version: 2"),
        new ModuleDependencySourceEditor()
            .withVersions(
                source, List.of(new ModuleDependencySourceEditor.Update("github", "alpha", 2))));
  }

  @Test
  void updatesOnlyConfigurationAndPreservesProgramCalls() {
    String call = "dependency(repository: \"github\", name: \"alpha\", version: 1)";
    String program = "Void main() { " + call + " }\n";
    String config = "Module module() { module(dependencies: [" + call + "]) }";
    SourceFile source = SourceFile.of(DocumentId.of("file:///main.norm"), program + config);
    String updated =
        new ModuleDependencySourceEditor()
            .withVersions(
                source, List.of(new ModuleDependencySourceEditor.Update("github", "alpha", 2)));
    assertEquals(program + config.replace("version: 1", "version: 2"), updated);
  }
}
