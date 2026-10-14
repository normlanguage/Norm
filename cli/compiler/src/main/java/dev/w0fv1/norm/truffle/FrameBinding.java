package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlotKind;

record FrameBinding(int slot, FrameSlotKind kind, String identity) {
  private static final Object TRANSACTION = new Object();

  FrameBinding(int slot, FrameSlotKind kind) {
    this(slot, kind, null);
  }

  boolean initialized(Frame frame) {
    Object pending =
        frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(TRANSACTION));
    if (pending instanceof Transaction transaction && transaction.values.containsKey(identity))
      return true;
    return identity == null
        || frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(identity))
            instanceof Cell;
  }

  boolean sameLocation(FrameBinding other) {
    return identity == null
        ? other.identity == null && slot == other.slot
        : identity.equals(other.identity);
  }

  int locationHash() {
    return identity == null ? slot : identity.hashCode();
  }

  void clear(Frame frame) {
    if (identity == null) frame.clear(slot);
    else frame.setAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(identity), null);
  }

  Object read(Frame frame) {
    if (identity != null) {
      Object pending =
          frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(TRANSACTION));
      if (pending instanceof Transaction transaction && transaction.values.containsKey(identity))
        return transaction.values.get(identity);
      Object state =
          frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(identity));
      if (!(state instanceof Cell cell))
        throw new IllegalStateException("Local state has not been initialized: " + identity);
      return cell.value;
    }
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
    if (identity != null) {
      Object pending =
          frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(TRANSACTION));
      if (pending instanceof Transaction transaction) {
        transaction.values.put(identity, value);
        return;
      }
      int index = frame.getFrameDescriptor().findOrAddAuxiliarySlot(identity);
      Object state = frame.getAuxiliarySlot(index);
      if (state instanceof Cell cell) cell.value = value;
      else frame.setAuxiliarySlot(index, new Cell(value));
      return;
    }
    switch (kind) {
      case Int -> frame.setInt(slot, (Integer) value);
      case Long -> frame.setLong(slot, (Long) value);
      case Float -> frame.setFloat(slot, (Float) value);
      case Double -> frame.setDouble(slot, (Double) value);
      case Boolean -> frame.setBoolean(slot, (Boolean) value);
      default -> frame.setObject(slot, value);
    }
  }

  private static final class Cell {
    private Object value;

    private Cell(Object value) {
      this.value = value;
    }
  }

  static final class Transaction implements AutoCloseable {
    private final Frame frame;
    private final java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    private final int slot;

    Transaction(Frame frame) {
      this.frame = frame;
      slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(TRANSACTION);
      if (frame.getAuxiliarySlot(slot) != null)
        throw new IllegalStateException("Nested state migration");
      frame.setAuxiliarySlot(slot, this);
    }

    void commit() {
      values.forEach(
          (identity, value) -> {
            int index = frame.getFrameDescriptor().findOrAddAuxiliarySlot(identity);
            Object retained = frame.getAuxiliarySlot(index);
            if (retained instanceof Cell cell) cell.value = value;
            else frame.setAuxiliarySlot(index, new Cell(value));
          });
    }

    @Override
    public void close() {
      frame.setAuxiliarySlot(slot, null);
    }
  }
}
