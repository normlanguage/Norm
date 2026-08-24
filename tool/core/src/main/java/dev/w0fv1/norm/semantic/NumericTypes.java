package dev.w0fv1.norm.semantic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

public final class NumericTypes {
  private static final BigInteger INTEGER_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
  private static final BigInteger INTEGER_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
  private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
  private static final Set<SemanticType> LEAVES =
      Set.of(SemanticType.INTEGER, SemanticType.LONG, SemanticType.FLOAT, SemanticType.DOUBLE);

  private NumericTypes() {}

  public static boolean isLeaf(SemanticType type) {
    return LEAVES.contains(type.nonNullable());
  }

  public static boolean isNumber(SemanticType type) {
    SemanticType value = type.nonNullable();
    return value.equals(SemanticType.NUMBER) || LEAVES.contains(value);
  }

  public static SemanticType integerLiteralType(BigInteger value, SemanticType expected) {
    SemanticType target = concreteExpected(expected);
    if (target == null) {
      target = fits(value, INTEGER_MIN, INTEGER_MAX) ? SemanticType.INTEGER : SemanticType.LONG;
    }
    requireIntegerRepresentable(value, target);
    return target;
  }

  public static SemanticType decimalLiteralType(BigDecimal value, SemanticType expected) {
    SemanticType target = concreteExpected(expected);
    if (target == null || target.equals(SemanticType.INTEGER) || target.equals(SemanticType.LONG)) {
      target = SemanticType.DOUBLE;
    }
    requireDecimalRepresentable(value, target);
    return target;
  }

  public static Number materialize(BigInteger value, SemanticType type) {
    SemanticType target = type.nonNullable();
    requireIntegerRepresentable(value, target);
    if (target.equals(SemanticType.INTEGER)) return value.intValueExact();
    if (target.equals(SemanticType.LONG)) return value.longValueExact();
    if (target.equals(SemanticType.FLOAT)) return checkedFloat(new BigDecimal(value));
    if (target.equals(SemanticType.DOUBLE)) return checkedDouble(new BigDecimal(value));
    throw new IllegalArgumentException("integer literal requires a concrete numeric leaf type");
  }

  public static Number materialize(BigDecimal value, SemanticType type) {
    SemanticType target = type.nonNullable();
    requireDecimalRepresentable(value, target);
    if (target.equals(SemanticType.FLOAT)) return checkedFloat(value);
    if (target.equals(SemanticType.DOUBLE)) return checkedDouble(value);
    throw new IllegalArgumentException("decimal literal requires Float or Double");
  }

  private static SemanticType concreteExpected(SemanticType expected) {
    if (expected == null) return null;
    SemanticType target = expected.nonNullable();
    return LEAVES.contains(target) ? target : null;
  }

  private static void requireIntegerRepresentable(BigInteger value, SemanticType target) {
    if (target.equals(SemanticType.INTEGER) && !fits(value, INTEGER_MIN, INTEGER_MAX)) {
      throw new ArithmeticException("integer literal is outside Integer range");
    }
    if (target.equals(SemanticType.LONG) && !fits(value, LONG_MIN, LONG_MAX)) {
      throw new ArithmeticException("integer literal is outside Long range");
    }
    if (target.equals(SemanticType.FLOAT)) checkedFloat(new BigDecimal(value));
    if (target.equals(SemanticType.DOUBLE)) checkedDouble(new BigDecimal(value));
    if (!LEAVES.contains(target)) {
      throw new IllegalArgumentException("integer literal requires a concrete numeric leaf type");
    }
  }

  private static void requireDecimalRepresentable(BigDecimal value, SemanticType target) {
    if (target.equals(SemanticType.FLOAT)) {
      checkedFloat(value);
      return;
    }
    if (target.equals(SemanticType.DOUBLE)) {
      checkedDouble(value);
      return;
    }
    throw new IllegalArgumentException("decimal literal requires Float or Double");
  }

  private static float checkedFloat(BigDecimal value) {
    float result = value.floatValue();
    if (!Float.isFinite(result) || result == 0.0f && value.signum() != 0) {
      throw new ArithmeticException("decimal literal is outside Float range");
    }
    return result;
  }

  private static double checkedDouble(BigDecimal value) {
    double result = value.doubleValue();
    if (!Double.isFinite(result) || result == 0.0d && value.signum() != 0) {
      throw new ArithmeticException("decimal literal is outside Double range");
    }
    return result;
  }

  private static boolean fits(BigInteger value, BigInteger minimum, BigInteger maximum) {
    return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
  }
}
