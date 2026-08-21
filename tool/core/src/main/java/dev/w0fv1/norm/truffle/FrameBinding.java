package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

record FrameBinding(int slot, FrameSlotKind kind) {
  Object read(VirtualFrame frame) {
    return switch (kind) {
      case Long -> frame.getLong(slot);
      case Boolean -> frame.getBoolean(slot);
      default -> frame.getObject(slot);
    };
  }

  void write(VirtualFrame frame, Object value) {
    switch (kind) {
      case Long -> frame.setLong(slot, (Long) value);
      case Boolean -> frame.setBoolean(slot, (Boolean) value);
      default -> frame.setObject(slot, value);
    }
  }
}
