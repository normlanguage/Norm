package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;

final class PatternNodes {
  private PatternNodes() {}

  static final class Variant extends PatternNode {
    private final String variantKey;
    @Children private final PatternNode[] arguments;

    Variant(String variantKey, PatternNode[] arguments) {
      this.variantKey = variantKey;
      this.arguments = arguments;
    }

    @Override
    boolean matches(Object value, VirtualFrame frame) {
      if (!(value instanceof RuntimeValues.EnumValue enumValue)
          || !enumValue.variantKey().equals(variantKey)
          || enumValue.fieldCount() != arguments.length) {
        return false;
      }
      for (int index = 0; index < arguments.length; index++) {
        if (!arguments[index].matches(enumValue.field(index), frame)) return false;
      }
      return true;
    }
  }

  static final class Binding extends PatternNode {
    private final FrameBinding binding;

    Binding(FrameBinding binding) {
      this.binding = binding;
    }

    @Override
    boolean matches(Object value, VirtualFrame frame) {
      if (value == RuntimeValues.NullValue.INSTANCE) return false;
      binding.write(frame, RuntimeValues.copy(value));
      return true;
    }
  }

  static final class Wildcard extends PatternNode {
    @Override
    boolean matches(Object value, VirtualFrame frame) {
      return true;
    }
  }

  static final class Literal extends PatternNode {
    private final Object expected;

    Literal(Object expected) {
      this.expected = expected;
    }

    @Override
    boolean matches(Object value, VirtualFrame frame) {
      return RuntimeValues.equal(expected, value);
    }
  }

  static final class Null extends PatternNode {
    @Override
    boolean matches(Object value, VirtualFrame frame) {
      return value == RuntimeValues.NullValue.INSTANCE;
    }
  }
}
