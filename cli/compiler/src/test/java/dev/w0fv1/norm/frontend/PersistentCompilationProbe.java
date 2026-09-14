package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;

public final class PersistentCompilationProbe {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    String text =
        "T identity<T>(T value) { value } Integer stable() { identity(2) } Void main() { printLine(stable()) }";
    boolean edited = !args[1].equals("original");
    if (edited) text = text.replace("identity(2)", "identity(3)");
    boolean calleeEdited = args[1].equals("callee-edited");
    if (calleeEdited) text = text.replace("{ value }", "{ printLine(value); value }");
    try (var compiler = CompilerSession.persistent(root.resolve("cache"))) {
      var result = compiler.compile(SourceFile.of(root.resolve("main.norm"), text));
      if (!result.isSuccess()) throw new IllegalStateException(result.diagnostics().toString());
      var core = result.output().orElseThrow().state().buildReport();
      if (core.convertedDefinitions() != (edited ? 1 : 3)
          || core.relinkedDefinitions() != (calleeEdited ? 2 : edited ? 1 : 0)
          || core.reusedDefinitions() != (calleeEdited ? 0 : edited ? 1 : 0))
        throw new IllegalStateException(core.toString());
      var report = result.output().orElseThrow().state().analysisReport();
      if (report.analyzedDeclarations() != (edited ? 1 : 3)
          || report.reusedDeclarations() != (edited ? 2 : 0))
        throw new IllegalStateException(report.toString());
      var printed = new java.io.StringWriter();
      new dev.w0fv1.norm.runtime.NormRuntime()
          .run(result.output().orElseThrow().artifact(), new java.io.PrintWriter(printed));
      var expected = calleeEdited ? "3" + System.lineSeparator() + "3" : edited ? "3" : "2";
      if (!printed.toString().equals(expected + System.lineSeparator()))
        throw new IllegalStateException("unexpected execution: " + printed);
    }
  }
}
