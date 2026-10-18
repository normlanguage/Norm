# File Agent

Run from an isolated copy of this directory. The `agent@1` dependency in `module.norm` is resolved, downloaded, cached, and integrity-checked by the Norm package manager.

Set `OPENAI_API_KEY` and `OPENAI_MODEL`. `OPENAI_BASE_URL` optionally supplies the complete Responses or Chat Completions endpoint URL. The application starts by asking the Agent to complete one console multi-turn dialogue:

When running a copy outside this repository, set `NORM_DOCUMENTATION_DIRECTORY` to `docs/zh` and `NORM_STDLIB_DIRECTORY` to `norm/stdlib/std` in the source checkout, so the Agent can read current syntax and API definitions.

```text
norm run main.norm
```

Entry points:

- [main.norm](main.norm): model configuration, the one-shot console dialogue instruction and the file tool catalog.
- [tools.norm](tools.norm): the initial file tools available to the Agent.
- [module.norm](module.norm): dependency declarations.
- [Agent library](https://github.com/normlanguage/agent): request loop, session storage, Skill loading, tools and MCP.
- [Language reference](../../../docs/zh/spec/language-spec.md): source syntax and semantics.
- [Standard library](../../../docs/zh/stdlib/overview.md): available capabilities.

Only functions in the catalog are submitted as model tools. Test output must identify whether the model service was real or a deterministic local protocol fixture.
