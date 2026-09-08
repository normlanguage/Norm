package dev.w0fv1.norm.cli.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

record VerificationOptions(Path entry, Optional<String> filter, boolean json) {
  static VerificationOptions parse(List<String> arguments, boolean tests) {
    Path entry = null;
    String filter = null;
    String format = null;
    for (int index = 0; index < arguments.size(); index++) {
      String argument = arguments.get(index);
      if (argument.equals("--format") && format == null && index + 1 < arguments.size()) {
        format = arguments.get(++index);
        if (!format.equals("json") && !format.equals("text"))
          throw new IllegalArgumentException("format must be text or json");
      } else if (tests
          && argument.equals("--filter")
          && filter == null
          && index + 1 < arguments.size()) {
        filter = arguments.get(++index);
        if (filter.isBlank() || filter.startsWith("--"))
          throw new IllegalArgumentException("test filter must not be empty or an option");
      } else if (entry == null && !argument.startsWith("--")) {
        entry = Path.of(argument);
      } else {
        throw new IllegalArgumentException("unexpected argument: " + argument);
      }
    }
    if (entry == null)
      throw new IllegalArgumentException("expected a module directory or source file");
    return new VerificationOptions(entry, Optional.ofNullable(filter), "json".equals(format));
  }

  static boolean requestsJson(List<String> arguments) {
    for (int index = 0; index + 1 < arguments.size(); index++) {
      if (arguments.get(index).equals("--format") && arguments.get(index + 1).equals("json"))
        return true;
    }
    return false;
  }
}
