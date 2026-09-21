package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import org.junit.jupiter.api.Test;

final class CommandLineTest {
  @Test
  void preservesIntentArgumentsAndParsesOnlyLeadingOptions() {
    assertOutput(
        """
        import std.cli.CommandLine
        import std.cli.Option
        import std.cli.OptionKind
        Void main() {
          CommandLine cli = CommandLine(name: "gait", description: "Git intent", options: [
            Option(name: "repo", description: "Repository", kind: OptionKind.Value),
            Option(name: "stdin", description: "Read input", kind: OptionKind.Flag)
          ])
          var parsed = cli.parse(arguments: ["--repo", "中文 repo", "commit", "--stdin", "a  b"], stopAtPositional: true)
          printLine(parsed.value(name: "repo") ?? "missing")
          printLine(parsed.has(name: "stdin"))
          printLine(parsed.positionals[1])
          printLine(parsed.positionals[2])
          printLine(cli.help().contains(value: "--repo"))
        }
        """,
        "中文 repo",
        "false",
        "--stdin",
        "a  b",
        "true");
  }

  @Test
  void reportsInputErrorsAndHonorsEndOfOptions() {
    assertOutput(
        """
        import std.cli.CommandLine
        import std.cli.Option
        import std.cli.OptionKind
        import std.cli.CliException
        Void main() {
          CommandLine cli = CommandLine(name: "gait", description: "Git intent", options: [
            Option(name: "repo", description: "Repository", kind: OptionKind.Value),
            Option(name: "stdin", description: "Read input", kind: OptionKind.Flag)
          ])
          for List<String> args : [["--repo"], ["--unknown"], ["--stdin", "--stdin"], ["--stdin=yes"]] {
            try { cli.parse(arguments: args, stopAtPositional: true) }
            catch CliException error { printLine(error.code) }
          }
          var parsed = cli.parse(arguments: ["--repo=x y", "--", "--stdin"], stopAtPositional: false)
          printLine(parsed.value(name: "repo") ?? "missing")
          printLine(parsed.positionals[0])
          printLine(cli.parse(arguments: [], stopAtPositional: true).positionals.size())
        }
        """,
        "NORM-CLI-MISSING-VALUE",
        "NORM-CLI-UNKNOWN-OPTION",
        "NORM-CLI-DUPLICATE-OPTION",
        "NORM-CLI-UNEXPECTED-VALUE",
        "x y",
        "--stdin",
        "0");
  }
}
