package dev.w0fv1.norm.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkillGeneratorTest {
  @Test
  void rendersTheDocumentationModelAsNormSkillMarkdown() {
    var declaration =
        new ModuleDocumentation.Declaration(
            "function",
            "demo.greet",
            "greet",
            "String greet(String name)",
            "public",
            new ModuleDocumentation.SourceRange(
                new ModuleDocumentation.Position(1, 1), new ModuleDocumentation.Position(1, 1)),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new ModuleDocumentation.Document(
                    "Greets a user.", List.of(), List.of(), List.of(), List.of())),
            List.of());
    var file =
        new ModuleDocumentation.File(
            "greet.norm",
            "greet.md",
            "demo",
            true,
            Optional.of(
                new ModuleDocumentation.Document(
                    "Greeting functions.", List.of(), List.of(), List.of(), List.of())),
            List.of(declaration),
            List.of());

    String skill =
        new SkillGenerator()
            .generate(new ModuleDocumentation(new ModuleCoordinate("demo", 1), List.of(file)));

    assertTrue(skill.contains("# demo Skill"));
    assertTrue(skill.contains("Greeting functions."));
    assertTrue(skill.contains("String greet(String name)"));
  }
}
