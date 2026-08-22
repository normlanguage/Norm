package dev.w0fv1.norm.value;

import dev.w0fv1.norm.bound.BoundProgram;
import java.util.Objects;

public record TypedProgram(BoundProgram boundProgram) {
  public TypedProgram {
    Objects.requireNonNull(boundProgram, "boundProgram");
  }
}
