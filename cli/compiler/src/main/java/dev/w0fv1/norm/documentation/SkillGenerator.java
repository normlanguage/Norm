package dev.w0fv1.norm.documentation;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.Objects;

public final class SkillGenerator {
  public String generate(ModuleDocumentation documentation) {
    Objects.requireNonNull(documentation, "documentation");
    StringBuilder result = new StringBuilder();
    ModuleCoordinate module = documentation.module();
    result.append("# ").append(module.name()).append(" Skill\n\n");
    result
        .append("Use this skill to work with Norm module `")
        .append(module.name())
        .append("` version ")
        .append(module.version())
        .append(". Use only declarations listed below.\n\n");
    for (ModuleDocumentation.File file : documentation.files()) {
      if (!file.exported() || file.declarations().isEmpty()) continue;
      result.append("## `").append(file.packageName()).append("`\n\n");
      file.document().ifPresent(document -> appendDescription(result, document.description()));
      for (ModuleDocumentation.Declaration declaration : file.declarations()) {
        appendDeclaration(result, declaration, 3);
      }
    }
    return result.toString();
  }

  private static void appendDeclaration(
      StringBuilder result, ModuleDocumentation.Declaration declaration, int level) {
    result
        .append("#".repeat(Math.min(level, 6)))
        .append(" `")
        .append(declaration.name())
        .append("`\n\n");
    declaration.document().ifPresent(document -> appendDescription(result, document.description()));
    result.append("```norm\n").append(declaration.signature()).append("\n```\n\n");
    for (ModuleDocumentation.Declaration member : declaration.members()) {
      appendDeclaration(result, member, Math.min(level + 1, 6));
    }
  }

  private static void appendDescription(StringBuilder result, String description) {
    if (!description.isBlank()) result.append(description.trim()).append("\n\n");
  }
}
