package dev.w0fv1.norm.language;

public sealed interface CompletionContext {
  record TopLevel() implements CompletionContext {}

  record Import(int qualifiedNameStart) implements CompletionContext {}

  record Type() implements CompletionContext {}

  record TypeArgument() implements CompletionContext {}

  record InterfaceType() implements CompletionContext {}

  record Statement() implements CompletionContext {}

  record Expression() implements CompletionContext {}

  record Member(int dotOffset) implements CompletionContext {}

  record ArgumentLabel() implements CompletionContext {}

  record None() implements CompletionContext {}
}
