package dev.w0fv1.norm.cli.controller;

import java.io.PrintWriter;
import java.util.Objects;

public final class CliController {
  private final CommandRouter router;

  public CliController() {
    router = new CommandRouter();
    router.register(new HelpCommand(router));
    router.register(new VersionCommand());
    router.register(new SetupCommand());
    router.register(new BuildCommand());
    router.register(new RunCommand());
    router.register(new VerificationCommand(VerificationCommand.Kind.TEST));
    router.register(new VerificationCommand(VerificationCommand.Kind.CHECK));
    router.register(new AuthoringCommand(AuthoringCommand.Kind.QUERY));
    router.register(new AuthoringCommand(AuthoringCommand.Kind.RENAME));
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
