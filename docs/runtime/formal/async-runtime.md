# Async Runtime

Async operations are based on explicit tasks and futures.

## Design Goals

- predictable execution
- explicit IO waiting
- integration with web servers

## Runtime Model

Task -> Scheduler -> Executor
