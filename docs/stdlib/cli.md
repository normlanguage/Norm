# Command Line and Processes

`std.cli` implements argument parsing in Norm. `std.application`, standard streams, and `std.process` reuse the system runtime without adding third-party dependencies.

## Arguments and execution environment

```norm
import std.application.arguments
import std.cli.CommandLine
import std.cli.Option
import std.cli.OptionKind

Void main() {
  CommandLine command = CommandLine(name: "gait", description: "Git assistant", options: [
    Option(name: "json", description: "JSON output", kind: OptionKind.Flag),
    Option(name: "repo", description: "Repository directory", kind: OptionKind.Value)
  ])
  var parsed = command.parse(arguments: arguments(), stopAtPositional: true)
  printLine(parsed.value(name: "repo") ?? ".")
}
```

When running sources, use `norm run application.norm -- --repo "my repo" create-branch`. A generated application receives arguments directly. The parser consumes an existing argument array, preserving spaces and empty strings; definitions, help text, and parsing rules share the same option set.

See [`std.cli`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/cli/arguments.norm) for public declarations and failure types. [`std.application`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/application/application.norm) defines environment variables, working directory, arguments, and completion codes. `setExitCode` sets the code returned after normal completion without interrupting resource cleanup.

## Standard streams and child processes

[`std.io.console`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/console.norm) provides borrowed standard input and text output; applications cannot close the host standard streams. Byte reads and UTF-8 conversion reuse the [I/O protocol](/stdlib/io).

On a Windows console, native applications read and write through Unicode APIs and convert input to UTF-8 bytes without changing the terminal code page. Stdout and stderr detect console handles independently and emit UTF-8 when redirected to a file or pipe. See [NativeStandardStreams](../../cli/compiler/src/main/java/dev/w0fv1/norm/runtime/NativeStandardStreams.java) for the host implementation.

[`std.process`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/process/process.norm) starts child processes with an executable and argument array, without a shell. Nonzero exit, timeout, and cancellation are results; invalid requests, startup failures, and I/O failures are catchable exceptions. Separate output budgets apply to stdout and stderr; excess output is drained and the result marks it as truncated.

Process exit does not roll back external effects. Cancellation and timeout terminate the managed process and observed descendants; this is not operating-system-level process isolation. Callers must still verify the actual result of external operations such as Git.

Acceptance entry points: [arguments and standard streams](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/test/java/dev/w0fv1/norm/stdlib) and [real child processes](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/platform/jdk/JdkProcessRunnerTest.java).
