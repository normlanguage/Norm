package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlotKind;

record FrameBinding(int slot, FrameSlotKind kind) {
  Object read(Frame frame) {
    return switch (kind) {
      case Int -> frame.getInt(slot);
      case Long -> frame.getLong(slot);
      case Float -> frame.getFloat(slot);
      case Double -> frame.getDouble(slot);
      case Boolean -> frame.getBoolean(slot);
      default -> frame.getObject(slot);
    };
  }

  void write(Frame frame, Object value) {
    switch (kind) {
      case Int -> frame.setInt(slot, (Integer) value);
      case Long -> frame.setLong(slot, (Long) value);
      case Float -> frame.setFloat(slot, (Float) value);
      case Double -> frame.setDouble(slot, (Double) value);
      case Boolean -> frame.setBoolean(slot, (Boolean) value);
      default -> frame.setObject(slot, value);
    }
  }
}
