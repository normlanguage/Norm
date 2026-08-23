package dev.w0fv1.norm.syntax;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LanguageSyntax {
  private static final Map<String, TokenKind> RESERVED_WORDS = createReservedWords();
  private static final List<String> COMPLETION_KEYWORDS =
      List.of(
          "class",
          "enum",
          "package",
          "import",
          "as",
          "public",
          "private",
          "if",
          "else",
          "for",
          "return",
          "break",
          "continue",
          "true",
          "false");

  private LanguageSyntax() {}

  public static List<String> completionKeywords() {
    return COMPLETION_KEYWORDS;
  }

  public static boolean isIdentifier(String value) {
    if (value == null || value.isEmpty() || RESERVED_WORDS.containsKey(value)) return false;
    int first = value.codePointAt(0);
    if (first != '_' && !Character.isUnicodeIdentifierStart(first)) return false;
    for (int offset = Character.charCount(first); offset < value.length(); ) {
      int character = value.codePointAt(offset);
      if (!Character.isUnicodeIdentifierPart(character)) return false;
      offset += Character.charCount(character);
    }
    return true;
  }

  public static TokenKind tokenKind(String lexeme) {
    return RESERVED_WORDS.getOrDefault(lexeme, TokenKind.IDENTIFIER);
  }

  public static Set<String> reservedWords() {
    return RESERVED_WORDS.keySet();
  }

  private static Map<String, TokenKind> createReservedWords() {
    Map<String, TokenKind> words = new LinkedHashMap<>();
    words.put("class", TokenKind.CLASS);
    words.put("enum", TokenKind.ENUM);
    words.put("package", TokenKind.PACKAGE);
    words.put("import", TokenKind.IMPORT);
    words.put("as", TokenKind.AS);
    words.put("public", TokenKind.PUBLIC);
    words.put("private", TokenKind.PRIVATE);
    words.put("if", TokenKind.IF);
    words.put("else", TokenKind.ELSE);
    words.put("for", TokenKind.FOR);
    words.put("return", TokenKind.RETURN);
    words.put("break", TokenKind.BREAK);
    words.put("continue", TokenKind.CONTINUE);
    words.put("true", TokenKind.TRUE);
    words.put("false", TokenKind.FALSE);
    return Map.copyOf(words);
  }
}
