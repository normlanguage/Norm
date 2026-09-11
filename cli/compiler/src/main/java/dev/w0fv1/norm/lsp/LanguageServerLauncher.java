package dev.w0fv1.norm.lsp;

import dev.w0fv1.norm.workspace.Workspace;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.eclipse.lsp4j.launch.LSPLauncher;

public final class LanguageServerLauncher {
  private LanguageServerLauncher() {}

  public static int run(Workspace workspace, InputStream input, OutputStream output)
      throws IOException, InterruptedException {
    Objects.requireNonNull(workspace, "workspace");
    try (workspace) {
      Objects.requireNonNull(output, "output");
      LanguageServer server = new LanguageServer(workspace);
      try (var workers =
              Executors.newCachedThreadPool(Thread.ofPlatform().name("norm-lsp-", 0).factory());
          var transport = new SessionInput(input, server::exited)) {
        var launcher =
            LSPLauncher.createServerLauncher(
                server, transport, output, workers, consumer -> consumer);
        server.connect(launcher.getRemoteProxy());
        var listening = launcher.startListening();
        try {
          listening.get();
        } catch (ExecutionException exception) {
          throw new IllegalStateException(
              "LSP transport stopped unexpectedly", exception.getCause());
        } finally {
          listening.cancel(true);
          workers.shutdownNow();
        }
        if (transport.failure != null) throw transport.failure;
        return server.exitCode();
      }
    }
  }

  private static final class SessionInput extends FilterInputStream {
    private final BooleanSupplier stopped;
    private volatile IOException failure;

    SessionInput(InputStream input, BooleanSupplier stopped) {
      super(Objects.requireNonNull(input, "input"));
      this.stopped = stopped;
    }

    @Override
    public int read() throws IOException {
      if (stopped.getAsBoolean()) return -1;
      try {
        return in.read();
      } catch (IOException exception) {
        failure = exception;
        throw exception;
      }
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (length == 0) return 0;
      if (stopped.getAsBoolean()) return -1;
      try {
        return in.read(bytes, offset, length);
      } catch (IOException exception) {
        failure = exception;
        throw exception;
      }
    }
  }
}
