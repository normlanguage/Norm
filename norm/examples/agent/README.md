# Self-extending Agent

Run from an isolated copy of this directory. Place the [OpenAI module](../../libraries/openai) in the workspace's `dependencies/openai` directory; dependency lookup follows the normal Norm module resolver.

Set `OPENAI_API_KEY` and `OPENAI_MODEL`. `OPENAI_BASE_URL` optionally supplies the complete Responses or Chat Completions endpoint URL. The application task is the first argument:

When running a copy outside this repository, set `NORM_DOCUMENTATION_DIRECTORY` to `docs/zh` and `NORM_STDLIB_DIRECTORY` to `norm/stdlib/std` in the source checkout, so the Agent can read current syntax and API definitions.

```text
norm run main.norm -- "Read your source and add the tool needed to complete this task."
```

Entry points:

- [main.norm](main.norm): model configuration, persistent session and runtime publication feedback.
- [tools.norm](tools.norm): initial file tools and the live tool catalog the Agent edits.
- [module.norm](module.norm): dependency declarations.
- [OpenAI Agent](../../libraries/openai/agent.norm): request and tool lifecycle.
- [Runtime publication API](../../stdlib/std/runtime/updates.norm): update transactions and diagnostics.
- [Language reference](../../../docs/zh/spec/language-spec.md): source syntax and semantics.
- [Standard library](../../../docs/zh/stdlib/overview.md): available capabilities.

Only functions in the live catalog are submitted as model tools. Test output must identify whether the model service was real or a deterministic local protocol fixture.
