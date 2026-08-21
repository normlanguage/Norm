package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

final class LanguageContext {
  private final PrintWriter output;

  LanguageContext(TruffleLanguage.Env environment) {
    output = new PrintWriter(environment.out(), true, StandardCharsets.UTF_8);
  }

  PrintWriter output() {
    return output;
  }
}
