# Language evolution

A language change must begin with concrete syntax, static rules, and runtime behavior. Goals such as “more modern” or “more concise” alone are not enough to enter the specification.

## Proposal contents

A language proposal includes at least:

1. A code example that demonstrates the problem.
2. New syntax and changes to the grammar.
3. Type-checking and name-resolution rules.
4. Evaluation order and error behavior.
5. Interaction with class identity, `ref<T>`, nullability, and generics.
6. Alternatives considered and rejected.
7. Migration and compatibility effects.
8. Required compiler, formatter, LSP, and documentation work.

## Stages

`idea` discusses the problem. `draft` supplies complete rules. `accepted` approves the design for implementation. `implemented` means a reference implementation and tests exist. Only `stable` receives a compatibility commitment.

## Experimental features

Experimental syntax must be gated by an explicit language version or feature flag; it must not silently activate in ordinary files. Diagnostics should name the feature and link its proposal.

## Removing features

Removing a stable feature requires a deprecation period, automated migration tooling, or mechanical replacement rules, plus an explanation of why continuing to maintain it would harm language coherence. Norm does not aim to preserve design errors forever, but migration cost must be measured.
