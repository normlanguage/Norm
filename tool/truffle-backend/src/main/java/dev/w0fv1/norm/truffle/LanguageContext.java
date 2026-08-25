package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class LanguageContext {
  private final ExecutionContext execution;
  private final CompilerSession compiler = new CompilerSession();

  LanguageContext(TruffleLanguage.Env environment) {
    execution =
        new ExecutionContext(
            new InputStreamReader(environment.in(), StandardCharsets.UTF_8),
            new PrintWriter(environment.out(), true, StandardCharsets.UTF_8),
            List.of(environment.getApplicationArguments()),
            () -> false);
  }

  ExecutionContext execution() {
    return execution;
  }

  CompilerSession compiler() {
    return compiler;
  }

  void close() {
    compiler.close();
  }
}
