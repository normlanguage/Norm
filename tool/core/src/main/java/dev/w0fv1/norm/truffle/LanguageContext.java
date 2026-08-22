package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import dev.w0fv1.norm.execution.ExecutionContext;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class LanguageContext {
  private final ExecutionContext execution;

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
}
