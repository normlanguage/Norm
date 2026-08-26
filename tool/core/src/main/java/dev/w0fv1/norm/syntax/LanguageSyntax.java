package dev.w0fv1.norm.syntax;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class LanguageSyntax {
  private static final Map<String, TokenKind> RESERVED_WORDS = createReservedWords();

  private LanguageSyntax() {}

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
    words.put("interface", TokenKind.INTERFACE);
    words.put("implements", TokenKind.IMPLEMENTS);
    words.put("extends", TokenKind.EXTENDS);
    words.put("package", TokenKind.PACKAGE);
    words.put("import", TokenKind.IMPORT);
    words.put("as", TokenKind.AS);
    words.put("public", TokenKind.PUBLIC);
    words.put("private", TokenKind.PRIVATE);
    words.put("if", TokenKind.IF);
    words.put("else", TokenKind.ELSE);
    words.put("switch", TokenKind.SWITCH);
    words.put("case", TokenKind.CASE);
    words.put("for", TokenKind.FOR);
    words.put("return", TokenKind.RETURN);
    words.put("break", TokenKind.BREAK);
    words.put("continue", TokenKind.CONTINUE);
    words.put("super", TokenKind.SUPER);
    words.put("ref", TokenKind.REF);
    words.put("true", TokenKind.TRUE);
    words.put("false", TokenKind.FALSE);
    words.put("null", TokenKind.NULL);
    words.put("var", TokenKind.VAR);
    return Map.copyOf(words);
  }
}
