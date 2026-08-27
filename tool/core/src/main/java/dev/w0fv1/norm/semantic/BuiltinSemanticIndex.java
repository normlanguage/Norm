package dev.w0fv1.norm.semantic;

import java.util.List;
import java.util.Optional;

public interface BuiltinSemanticIndex {
  Optional<Symbol> member(SemanticType owner, SymbolId member);

  List<Symbol> typeMembers(String owner);
}
