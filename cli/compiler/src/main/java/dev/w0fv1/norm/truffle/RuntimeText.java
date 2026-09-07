package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.truffle.RuntimeValues.ArrayValue;
import dev.w0fv1.norm.truffle.RuntimeValues.CodePointValue;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class RuntimeText {
  private static final Pattern GRAPHEME = Pattern.compile("\\X");

  private RuntimeText() {}

  static int byteSize(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  static int codePointSize(String value) {
    return value.codePointCount(0, value.length());
  }

  static int graphemeSize(String value) {
    return Math.toIntExact(GRAPHEME.matcher(value).results().count());
  }

  static ArrayValue codePoints(String value) {
    CoreType type = arrayType(CoreType.CODE_POINT);
    List<Object> values =
        value.codePoints().mapToObj(CodePointValue::new).map(item -> (Object) item).toList();
    return new ArrayValue(type, new ArrayList<>(values));
  }

  static ArrayValue graphemes(String value) {
    CoreType type = arrayType(CoreType.STRING);
    List<Object> values =
        GRAPHEME.matcher(value).results().map(result -> (Object) result.group()).toList();
    return new ArrayValue(type, new ArrayList<>(values));
  }

  static String sliceCodePoints(String value, int start, int end, Node location) {
    int size = value.codePointCount(0, value.length());
    int from = checkedTextIndex(start, size, "code point", location);
    int to = checkedTextIndex(end, size, "code point", location);
    if (from > to) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS, "code point slice start exceeds end", location);
    }
    int fromOffset = value.offsetByCodePoints(0, from);
    int toOffset = value.offsetByCodePoints(0, to);
    return value.substring(fromOffset, toOffset);
  }

  static ArrayValue split(String value, String separator, Node location) {
    if (separator.isEmpty()) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "string separator must not be empty", location);
    }
    CoreType type = arrayType(CoreType.STRING);
    List<Object> parts = new ArrayList<>();
    int start = 0;
    int index;
    while ((index = value.indexOf(separator, start)) >= 0) {
      parts.add(value.substring(start, index));
      start = index + separator.length();
    }
    parts.add(value.substring(start));
    return new ArrayValue(type, parts);
  }

  static String sliceGraphemes(String value, int start, int end, Node location) {
    List<java.util.regex.MatchResult> matches = GRAPHEME.matcher(value).results().toList();
    int from = checkedTextIndex(start, matches.size(), "grapheme", location);
    int to = checkedTextIndex(end, matches.size(), "grapheme", location);
    if (from > to) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS, "grapheme slice start exceeds end", location);
    }
    int fromOffset = from == matches.size() ? value.length() : matches.get(from).start();
    int toOffset = to == matches.size() ? value.length() : matches.get(to).start();
    return value.substring(fromOffset, toOffset);
  }

  static String replaceFirst(String value, String target, String replacement, Node location) {
    requireReplaceTarget(target, location);
    int index = value.indexOf(target);
    if (index < 0) return value;
    return value.substring(0, index) + replacement + value.substring(index + target.length());
  }

  static String replace(String value, String target, String replacement, Node location) {
    requireReplaceTarget(target, location);
    return value.replace(target, replacement);
  }

  static String trim(String value) {
    return trimEnd(trimStart(value));
  }

  static String trimStart(String value) {
    int offset = 0;
    while (offset < value.length()) {
      int codePoint = value.codePointAt(offset);
      if (!isWhitespace(codePoint)) break;
      offset += Character.charCount(codePoint);
    }
    return value.substring(offset);
  }

  static String trimEnd(String value) {
    int offset = value.length();
    while (offset > 0) {
      int codePoint = value.codePointBefore(offset);
      if (!isWhitespace(codePoint)) break;
      offset -= Character.charCount(codePoint);
    }
    return value.substring(0, offset);
  }

  static boolean isWhitespace(int value) {
    return Character.isWhitespace(value) || Character.isSpaceChar(value);
  }

  static String toLowercase(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  static String toUppercase(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  static boolean equalsIgnoreCaseAscii(String left, String right) {
    if (left.length() != right.length()) return false;
    for (int index = 0; index < left.length(); index++) {
      char leftCharacter = asciiLowercase(left.charAt(index));
      char rightCharacter = asciiLowercase(right.charAt(index));
      if (leftCharacter != rightCharacter) return false;
    }
    return true;
  }

  static int compareCodePoints(String left, String right) {
    var leftIterator = left.codePoints().iterator();
    var rightIterator = right.codePoints().iterator();
    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      int leftValue = leftIterator.nextInt();
      int rightValue = rightIterator.nextInt();
      if (leftValue < rightValue) return -1;
      if (leftValue > rightValue) return 1;
    }
    if (leftIterator.hasNext()) return 1;
    if (rightIterator.hasNext()) return -1;
    return 0;
  }

  static String normalizeNfc(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC);
  }

  static String normalizeNfd(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD);
  }

  static String normalizeNfkc(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC);
  }

  static String normalizeNfkd(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKD);
  }

  static boolean isNormalizedNfc(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFC);
  }

  static boolean isNormalizedNfd(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFD);
  }

  static boolean isNormalizedNfkc(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFKC);
  }

  static boolean isNormalizedNfkd(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFKD);
  }

  private static char asciiLowercase(char value) {
    return value >= 'A' && value <= 'Z' ? (char) (value + ('a' - 'A')) : value;
  }

  private static void requireReplaceTarget(String target, Node location) {
    if (target.isEmpty()) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "replace target must not be empty", location);
    }
  }

  private static int checkedTextIndex(int index, int size, String unit, Node location) {
    if (index < 0 || index > size) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS,
          unit + " index " + index + " is outside 0.." + size,
          location);
    }
    return index;
  }

  @TruffleBoundary
  static String stringify(Object value) {
    return value == null ? "Void" : value.toString();
  }

  @TruffleBoundary
  static String concatenate(String left, Object right) {
    return left + stringify(right);
  }

  private static CoreType arrayType(CoreType element) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Array")),
        List.of(element),
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }
}
