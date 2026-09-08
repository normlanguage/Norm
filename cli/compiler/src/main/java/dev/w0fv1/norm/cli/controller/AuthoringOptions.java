package dev.w0fv1.norm.cli.controller;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

record AuthoringOptions(
    Path entry,
    String search,
    Optional<String> target,
    Optional<String> at,
    int offset,
    int limit,
    Set<String> expansions,
    boolean overview,
    Optional<String> newName) {
  static AuthoringOptions parse(List<String> arguments, boolean refactor) {
    if (arguments.isEmpty() || arguments.getFirst().startsWith("-"))
      throw new IllegalArgumentException(
          "expected a module directory or source file; use -h for help");
    var values = new LinkedHashMap<String, String>();
    var flags = new HashSet<String>();
    String target = null;
    for (int index = 1; index < arguments.size(); index++) {
      String key = arguments.get(index);
      if (!key.startsWith("-") && target == null) {
        target = key;
        continue;
      }
      if (Set.of("--source", "--references", "--dependencies", "--tests", "--preview")
          .contains(key)) {
        if (!flags.add(key)) throw new IllegalArgumentException("duplicate option: " + key);
        continue;
      }
      if (!Set.of("--search", "--offset", "--limit", "--to", "--at").contains(key)
          || index + 1 == arguments.size()
          || arguments.get(index + 1).startsWith("--"))
        throw new IllegalArgumentException(
            "unexpected or incomplete option: " + key + "; use -h for help");
      if (values.putIfAbsent(key, arguments.get(++index)) != null)
        throw new IllegalArgumentException("duplicate option: " + key);
    }
    if (refactor) {
      if (target == null
          || !values.containsKey("--to")
          || values.containsKey("--search")
          || values.containsKey("--offset")
          || values.containsKey("--limit")
          || flags.stream().anyMatch(flag -> !flag.equals("--preview")))
        throw new IllegalArgumentException(
            "refactor name requires <qualified-name> and --to <name>; use -h for help");
    } else if (values.containsKey("--to")
        || flags.contains("--preview")
        || target != null && values.containsKey("--search")
        || target == null && !flags.isEmpty()) {
      throw new IllegalArgumentException(
          "query expansions require a qualified name; search and exact selection are exclusive");
    }
    if (values.containsKey("--at") && target == null)
      throw new IllegalArgumentException("--at requires a qualified name");
    int offset = Integer.parseInt(values.getOrDefault("--offset", "0"));
    int limit = Integer.parseInt(values.getOrDefault("--limit", "20"));
    if (offset < 0 || limit < 1 || limit > 1000)
      throw new IllegalArgumentException(
          "offset must be nonnegative and limit must be between 1 and 1000");
    return new AuthoringOptions(
        Path.of(arguments.getFirst()),
        values.getOrDefault("--search", ""),
        Optional.ofNullable(target),
        Optional.ofNullable(values.get("--at")),
        offset,
        limit,
        Set.copyOf(flags),
        target == null && !values.containsKey("--search"),
        Optional.ofNullable(values.get("--to")));
  }
}
