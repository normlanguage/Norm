package dev.w0fv1.norm.core;

import java.util.List;

final class CoreValidation {
  private CoreValidation() {}

  static void requireResolved(List<CoreDefinition> definitions) {
    for (CoreDefinition definition : definitions) {
      for (CoreDefinitionLink link : CoreTree.links(definition)) {
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("definition group contains a pending reference");
        }
        if (reference instanceof DefinitionReference.RecursiveMember recursive
            && recursive.memberIndex() >= definitions.size()) {
          throw new IllegalArgumentException("recursive reference is outside its definition group");
        }
      }
    }
  }
}
