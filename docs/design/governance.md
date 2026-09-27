# Project governance

Governance makes language rules traceable. An implementation detail does not become a de facto standard merely because it landed in a commit.

## Decision areas

- Language syntax and semantics are decided through language proposals.
- Public standard-library APIs use library proposals.
- Maintainers review internal compiler refactors, but those changes must not alter observable semantics.
- Documentation corrections can be submitted directly. Changes to rules must link the corresponding proposal.

## Roles

Maintainers handle merges, releases, and security response. Domain reviewers provide specialist review for the type system, runtime, standard library, or toolchain. Contributors can submit issues, proposals, implementations, and tests. Roles are based on sustained contribution, not employment.

## Decision records

Each accepted proposal retains a stable number, status, date, decision maker, and summary of objections. Discussion may occur in issues or meetings, but final rules must be recorded in the repository rather than only in a chat log.

## Conflicts of interest

Reviewers should disclose commercial relationships that may benefit directly from a decision and recuse themselves from the final decision when necessary. A security issue may be fixed privately first, with a public record added after the fix is released.

The project is still small, so core maintainers currently make the final decisions. As the maintainer group grows, approval counts, appeals, and terms should be defined in a separate charter.
