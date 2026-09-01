package dev.w0fv1.norm.cli.controller;

import java.io.PrintWriter;
import java.util.Objects;

public final class CliController {
  private final CommandRouter router;

  public CliController() {
    router = new CommandRouter();
    router.register(new HelpCommand(router));
    router.register(new VersionCommand());
    router.register(new RunCommand());
    router.register(new TestCommand());
    router.register(new ResolveCommand());
    router.register(new PackageCommand());
    router.register(new DocsCommand());
    router.register(new LspCommand());
  }

  public int run(String[] arguments, PrintWriter out, PrintWriter err) {
    Objects.requireNonNull(arguments, "arguments");
    try {
      return router.route(arguments.clone(), out, err);
    } finally {
      out.flush();
      err.flush();
    }
  }
}
