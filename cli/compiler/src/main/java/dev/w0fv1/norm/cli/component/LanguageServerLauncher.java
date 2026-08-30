package dev.w0fv1.norm.cli.component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import org.eclipse.lsp4j.launch.LSPLauncher;

public final class LanguageServerLauncher {
  private LanguageServerLauncher() {}

  public static void run(InputStream input, OutputStream output) throws InterruptedException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(output, "output");
    LanguageServer server = new LanguageServer(System::exit);
    var launcher = LSPLauncher.createServerLauncher(server, input, output);
    server.connect(launcher.getRemoteProxy());
    try {
      launcher.startListening().get();
    } catch (java.util.concurrent.ExecutionException exception) {
      throw new IllegalStateException("LSP transport stopped unexpectedly", exception.getCause());
    }
  }
}
