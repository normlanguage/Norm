package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.language.DocumentRevision;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

record AuthoringOptions(
    Path entry,
    String search,
    Optional<SymbolId> symbol,
    Optional<DocumentRevision> revision,
    int offset,
    int limit,
    boolean source,
    boolean overview,
    Optional<String> newName) {
  static AuthoringOptions parse(List<String> arguments, boolean rename) {
    if (arguments.isEmpty() || arguments.getFirst().startsWith("--"))
      throw new IllegalArgumentException("expected a module directory or source file");
    var values = new LinkedHashMap<String, String>();
    boolean source = false;
    for (int index = 1; index < arguments.size(); index++) {
      String key = arguments.get(index);
      if (key.equals("--source") && !source) {
        source = true;
        continue;
      }
      if (!Set.of("--search", "--symbol", "--document", "--revision", "--offset", "--limit", "--to")
              .contains(key)
          || index + 1 == arguments.size())
        throw new IllegalArgumentException("unexpected or incomplete option: " + key);
      if (values.putIfAbsent(key, arguments.get(++index)) != null)
        throw new IllegalArgumentException("duplicate option: " + key);
    }
    boolean selected = values.containsKey("--symbol");
    if (rename != values.containsKey("--to")
        || rename
            && (!selected
                || source
                || values.containsKey("--offset")
                || values.containsKey("--limit")))
      throw new IllegalArgumentException(
          "rename requires a full symbol selector and --to, without source or paging options");
    if (selected != values.containsKey("--document")
        || selected != values.containsKey("--revision")
        || selected && values.containsKey("--search")
        || source && !selected)
      throw new IllegalArgumentException(
          "context requires --symbol, --document and --revision; search and context are mutually exclusive");
    int offset = Integer.parseInt(values.getOrDefault("--offset", "0"));
    int limit = Integer.parseInt(values.getOrDefault("--limit", "20"));
    if (offset < 0 || limit < 1 || limit > 1000)
      throw new IllegalArgumentException(
          "offset must be nonnegative and limit must be between 1 and 1000");
    return new AuthoringOptions(
        Path.of(arguments.getFirst()),
        values.getOrDefault("--search", ""),
        Optional.ofNullable(values.get("--symbol")).map(SymbolId::new),
        selected
            ? Optional.of(
                new DocumentRevision(
                    DocumentId.of(values.get("--document")),
                    Sha256Digest.parse(values.get("--revision"))))
            : Optional.empty(),
        offset,
        limit,
        source,
        !selected && !values.containsKey("--search"),
        Optional.ofNullable(values.get("--to")));
  }
}
