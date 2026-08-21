package dev.w0fv1.norm.value;

import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.Objects;

public record TypedProgram(SemanticModel semanticModel, Syntax.FunctionDecl entryPoint) {
  public TypedProgram {
    Objects.requireNonNull(semanticModel, "semanticModel");
    Objects.requireNonNull(entryPoint, "entryPoint");
  }

  public Syntax.Program syntax() {
    return semanticModel.syntax();
  }
}
