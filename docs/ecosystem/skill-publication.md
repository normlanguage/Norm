---
title: Repository Skill Publication
description: Publish agent guidance with the library that owns the API
---

# Repository Skill Publication

Every public Norm library publishes its agent Skill from the same repository as
its `module.norm` and source declarations. The repository root `SKILL.md` is the
release entry point; a module repository may also expose generated module
Skills beside each exported module when one repository contains more than one
module.

The Skill is generated with the repository's pinned Norm toolchain:

```text
norm docs path/to/module --output build/api --skill SKILL.md --strict
```

Declarations and `@Document` metadata are the only API source of truth. The
checked-in `SKILL.md` is a reproducible release artifact. Repository CI must
regenerate it from a clean checkout and fail when the result differs. A Skill
must include the module identity and version, supported imports, public
signatures, parameter meaning, lifecycle and ownership rules, failure behavior,
and verified examples. It must link to the repository's source and tests when
an agent needs details beyond the generated API.

Registry entries contain only `name`, `owner`, and `repository`. An agent uses
the registry to find the owning repository, then reads the immutable release
Skill from that repository:

```text
https://raw.githubusercontent.com/{owner}/{repository}/v{version}/SKILL.md
```

The `main` URL is for development inspection. Consumers use a tag or commit
URL for reproducible builds. Aggregators may index repository Skills, but they
do not copy or become the owner of a library's Skill.
