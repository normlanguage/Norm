package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;

final class TextIntrinsicDispatcher {
  private TextIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case TO_STRING ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.stringify(receiver);
          };
      case STRING_BUILDER_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.BuilderValue(type);
          };
      case BUILDER_APPEND ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                RuntimeValues.BuilderValue builder = (RuntimeValues.BuilderValue) receiver;
                builder.value.append(RuntimeText.stringify(first));
                return builder;
              });
      case BUILDER_TO_STRING ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.BuilderValue) receiver).value.toString();
          };
      case STRING_BYTE_SIZE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.byteSize((String) receiver);
          };
      case STRING_CODE_POINT_SIZE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.codePointSize((String) receiver);
          };
      case STRING_GRAPHEME_SIZE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.graphemeSize((String) receiver);
          };
      case STRING_CODE_POINTS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.codePoints((String) receiver);
          };
      case STRING_GRAPHEMES ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.graphemes((String) receiver);
          };
      case STRING_SLICE_CODE_POINTS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return RuntimeText.sliceCodePoints(
                (String) receiver, (Integer) first, (Integer) second, location);
          };
      case STRING_SPLIT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeText.split((String) receiver, (String) first, location);
          };
      case STRING_IS_BLANK ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              ((String) receiver).isBlank();
      case STRING_IS_EMPTY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((String) receiver).isEmpty();
          };
      case STRING_CONTAINS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return ((String) receiver).contains((String) first);
          };
      case STRING_STARTS_WITH ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return ((String) receiver).startsWith((String) first);
          };
      case STRING_ENDS_WITH ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return ((String) receiver).endsWith((String) first);
          };
      case STRING_SLICE_GRAPHEMES ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return RuntimeText.sliceGraphemes(
                (String) receiver, (Integer) first, (Integer) second, location);
          };
      case STRING_REPLACE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return RuntimeText.replace(
                (String) receiver, (String) first, (String) second, location);
          };
      case STRING_REPLACE_FIRST ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return RuntimeText.replaceFirst(
                (String) receiver, (String) first, (String) second, location);
          };
      case STRING_TRIM ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.trim((String) receiver);
          };
      case STRING_TRIM_START ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.trimStart((String) receiver);
          };
      case STRING_TRIM_END ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.trimEnd((String) receiver);
          };
      case STRING_TO_LOWERCASE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.toLowercase((String) receiver);
          };
      case STRING_TO_UPPERCASE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.toUppercase((String) receiver);
          };
      case STRING_EQUALS_IGNORE_CASE_ASCII ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeText.equalsIgnoreCaseAscii((String) receiver, (String) first);
          };
      case STRING_COMPARE_CODE_POINTS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeText.compareCodePoints((String) receiver, (String) first);
          };
      case STRING_NORMALIZE_NFC ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.normalizeNfc((String) receiver);
          };
      case STRING_NORMALIZE_NFD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.normalizeNfd((String) receiver);
          };
      case STRING_NORMALIZE_NFKC ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.normalizeNfkc((String) receiver);
          };
      case STRING_NORMALIZE_NFKD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.normalizeNfkd((String) receiver);
          };
      case STRING_IS_NORMALIZED_NFC ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.isNormalizedNfc((String) receiver);
          };
      case STRING_IS_NORMALIZED_NFD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.isNormalizedNfd((String) receiver);
          };
      case STRING_IS_NORMALIZED_NFKC ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.isNormalizedNfkc((String) receiver);
          };
      case STRING_IS_NORMALIZED_NFKD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.isNormalizedNfkd((String) receiver);
          };
      case CODE_POINT_SCALAR_VALUE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.CodePointValue) receiver).value();
          };
      case CODE_POINT_IS_DECIMAL_DIGIT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return Character.isDigit(((RuntimeValues.CodePointValue) receiver).value());
          };
      case CODE_POINT_IS_LETTER ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return Character.isLetter(((RuntimeValues.CodePointValue) receiver).value());
          };
      case CODE_POINT_IS_WHITESPACE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeText.isWhitespace(((RuntimeValues.CodePointValue) receiver).value());
          };
      case CODE_POINT_IS_UPPERCASE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return Character.isUpperCase(((RuntimeValues.CodePointValue) receiver).value());
          };
      case CODE_POINT_IS_LOWERCASE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return Character.isLowerCase(((RuntimeValues.CodePointValue) receiver).value());
          };
      case CODE_POINT_IS_ASCII_DIGIT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            int value = ((RuntimeValues.CodePointValue) receiver).value();
            return value >= '0' && value <= '9';
          };
      case CODE_POINT_ASCII_DIGIT_VALUE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            int value = ((RuntimeValues.CodePointValue) receiver).value();
            if (value < '0' || value > '9') {
              throw new NormGuestException(
                  RuntimeErrorCode.INVALID_ARGUMENT, "code point is not an ASCII digit", location);
            }
            return value - '0';
          };
      default -> throw new IllegalArgumentException("Unsupported text intrinsic: " + intrinsic);
    };
  }
}
