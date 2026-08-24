package dev.w0fv1.norm.core;

public record CoreTypeCapture(int typeParameterIndex, int localIndex) {
  public CoreTypeCapture {
    if (typeParameterIndex < 0 || localIndex < 0) {
      throw new IllegalArgumentException("type capture indices must not be negative");
    }
  }
}
