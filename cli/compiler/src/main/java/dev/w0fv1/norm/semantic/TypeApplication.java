package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;
import java.util.function.Supplier;

public final class TypeApplication {
  private TypeApplication() {}

  public static SemanticType resolve(
      Syntax.TypeRef reference, List<SemanticType> arguments, Supplier<SemanticType> declaration) {
    if (reference.isWildcard()) return SemanticType.EXISTENTIAL;
    if (reference.name().equals("ref")) {
      if (reference.nullable() || arguments.size() != 1) return SemanticType.DYNAMIC;
      SemanticType target = arguments.getFirst();
      return target.containsReference()
              || target.isNullable()
              || target.category() != ValueCategory.VALUE
          ? SemanticType.DYNAMIC
          : SemanticType.reference(target);
    }
    if (reference.name().equals("Void")) {
      return reference.nullable() ? SemanticType.DYNAMIC : SemanticType.VOID;
    }
    if (arguments.stream().anyMatch(SemanticType::containsReference)) return SemanticType.DYNAMIC;
    SemanticType resolved =
        reference.name().equals("Function") && !arguments.isEmpty()
            ? SemanticType.function(arguments.getFirst(), arguments.subList(1, arguments.size()))
            : declaration.get();
    return reference.nullable() ? resolved.nullable() : resolved;
  }
}
