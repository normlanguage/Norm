package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.Optional;

public interface SemanticIndex {
  Optional<Symbol> symbolOf(SourceSpan span);

  Optional<Symbol> resolvedSymbolOf(SourceSpan span);

  Optional<SemanticType> typeOf(SourceSpan span);

  Optional<ResolvedCall> callOf(SourceSpan callSpan);

  Optional<ResolvedIteration> iterationOf(SourceSpan iterableSpan);

  Optional<ResolvedIndex> indexOf(SourceSpan indexSpan);
}
