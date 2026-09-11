package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;
import java.util.Optional;

final class PropertyAccess {
  private PropertyAccess() {}

  static Syntax.Member implicit(Syntax.Name name) {
    SourceSpan receiver =
        new SourceSpan(name.span().source(), name.span().startOffset(), name.span().startOffset());
    return new Syntax.Member(
        new Syntax.Name("this", List.of(), false, receiver),
        name.value(),
        name.span(),
        name.typeArguments(),
        false,
        name.span());
  }

  static Syntax.Call read(Syntax.Member member) {
    return new Syntax.Call(member, List.of(), member.span());
  }

  static Syntax.Call write(Syntax.Assignment assignment) {
    return new Syntax.Call(
        assignment.target() instanceof Syntax.Name name ? implicit(name) : assignment.target(),
        List.of(
            new Syntax.CallArgument(
                Optional.empty(), assignment.value(), assignment.value().span())),
        assignment.span());
  }
}
