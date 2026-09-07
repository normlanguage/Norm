package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

public final class NativeApplicationArchiveProbe {
  private NativeApplicationArchiveProbe() {}

  public static void main(String[] arguments) throws Exception {
    if (!NativeApplicationArchive.class.getModule().isNamed()) {
      throw new AssertionError("archive probe must run in the compiler module");
    }
    try (var compiler = new CompilerSession()) {
      var compilation =
          compiler.compile(
              SourceFile.of(Path.of("archive.norm"), "Void main() { printLine(\"native\") }"));
      if (!compilation.isSuccess()) throw new AssertionError(compilation.diagnostics());
      var artifact = compilation.output().orElseThrow().artifact();
      Path archive = Path.of(arguments[0]);
      NativeApplicationArchive.write(
          new NativeApplicationData(
              artifact, CoreExecutionPlan.forArtifact(artifact), List.of(), "sample"),
          archive);
      var application = NativeApplicationArchive.read(archive);
      new TruffleExecutionBackend()
          .prepare(application.artifact(), application.execution())
          .execute(ExecutionContext.of(new PrintWriter(System.out, true)));
    }
  }
}
