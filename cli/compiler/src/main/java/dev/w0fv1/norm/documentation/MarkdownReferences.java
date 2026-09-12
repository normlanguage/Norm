package dev.w0fv1.norm.documentation;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.parser.beta.InlineContentParser;
import org.commonmark.parser.beta.InlineContentParserFactory;
import org.commonmark.parser.beta.ParsedInline;
import org.commonmark.parser.beta.Scanner;

public final class MarkdownReferences {
  private static final Pattern TARGET =
      Pattern.compile(
          "([A-Za-z_][A-Za-z_0-9]*(?:\\.[A-Za-z_][A-Za-z_0-9]*)+)(\\([^\\r\\n#{}]*\\))?#([1-9][0-9]*)");
  private final Parser parser =
      Parser.builder()
          .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
          .customInlineContentParserFactory(new ReferenceParserFactory())
          .build();

  public List<Reference> parse(SourceFile source) {
    var references = new ArrayList<Reference>();
    parser
        .parse(source.text())
        .accept(
            new AbstractVisitor() {
              @Override
              public void visit(CustomNode node) {
                if (node instanceof ReferenceNode reference) {
                  var spans = node.getSourceSpans();
                  var first = spans.getFirst();
                  var last = spans.getLast();
                  int start = source.offsetAt(first.getLineIndex(), first.getColumnIndex());
                  int end =
                      source.offsetAt(
                          last.getLineIndex(), last.getColumnIndex() + last.getLength());
                  references.add(new Reference(reference.text, new SourceSpan(source, start, end)));
                }
                visitChildren(node);
              }
            });
    return List.copyOf(references);
  }

  public record Reference(String text, SourceSpan span) {
    public Target target() {
      if (span.text().startsWith("@{") && !span.text().endsWith("}")) {
        throw new IllegalArgumentException("unclosed Markdown declaration reference; expected '}'");
      }
      var matcher = TARGET.matcher(text);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(
            "expected @module.file.declaration#version or @github.module.file.declaration#version with a positive integer version");
      }
      int version;
      try {
        version = Integer.parseInt(matcher.group(3));
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("reference version exceeds the supported integer range");
      }
      String path = matcher.group(1) + (matcher.group(2) == null ? "" : matcher.group(2));
      return path.startsWith("github.")
          ? new Target(Optional.of(ModuleRepositoryId.GITHUB), path.substring(7), version)
          : new Target(Optional.empty(), path, version);
    }
  }

  public record Target(Optional<ModuleRepositoryId> repository, String path, int version) {}

  private static final class ReferenceNode extends CustomNode {
    private final String text;

    private ReferenceNode(String text) {
      this.text = text;
    }
  }

  private static final class ReferenceParserFactory implements InlineContentParserFactory {
    @Override
    public Set<Character> getTriggerCharacters() {
      return Set.of('@');
    }

    @Override
    public InlineContentParser create() {
      return state -> {
        Scanner scanner = state.scanner();
        int previous = scanner.peekPreviousCodePoint();
        if (previous >= 'a' && previous <= 'z'
            || previous >= 'A' && previous <= 'Z'
            || previous >= '0' && previous <= '9'
            || previous == '_'
            || previous == '@') {
          return ParsedInline.none();
        }
        scanner.next();
        boolean braced = scanner.next('{');
        var start = scanner.position();
        var end = start;
        if (braced) {
          while (scanner.peek() != Scanner.END && scanner.peek() != '\n' && scanner.peek() != '}')
            scanner.next();
          end = scanner.position();
          scanner.next('}');
        } else {
          while (true) {
            char c = scanner.peek();
            if (!(c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9'
                || c == '_'
                || c == '.'
                || c == '#')) break;
            scanner.next();
            if (c != '.') end = scanner.position();
          }
          scanner.setPosition(end);
        }
        String text = scanner.getSource(start, end).getContent();
        if (!text.contains(".")) return ParsedInline.none();
        return ParsedInline.of(new ReferenceNode(text), scanner.position());
      };
    }
  }
}
