package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.BitSet;

final class ConstructionArguments {
  private final Object[] values;
  private final BitSet provided;
  private final Object[] reified;

  private ConstructionArguments(Object[] values, BitSet provided, Object[] reified) {
    this.values = values;
    this.provided = provided;
    this.reified = reified.clone();
  }

  static ConstructionArguments supplied(
      int count, int[] indices, Object[] inputs, Object[] reified) {
    if (count < 0 || indices.length != inputs.length)
      throw new IllegalArgumentException("invalid constructor argument layout");
    var values = new Object[count];
    var provided = new BitSet(count);
    for (int index = 0; index < indices.length; index++) {
      int parameter = indices[index];
      if (parameter < 0 || parameter >= count || provided.get(parameter))
        throw new IllegalArgumentException("invalid provided constructor parameter");
      provided.set(parameter);
      values[parameter] = inputs[index];
    }
    return new ConstructionArguments(values, provided, reified);
  }

  static ConstructionArguments from(VirtualFrame frame) {
    if (frame.getArguments().length != 3
        || !(frame.getArguments()[2] instanceof ConstructionArguments arguments))
      throw new IllegalStateException("constructor requires typed argument binding");
    return arguments;
  }

  static ConstructionArguments evaluate(
      int count, ExpressionNode[] inputs, int[] indices, Object[] reified, VirtualFrame frame) {
    var values = new Object[inputs.length];
    for (int index = 0; index < inputs.length; index++)
      values[index] = RuntimeValues.copy(inputs[index].execute(frame));
    return supplied(count, indices, values, reified);
  }

  boolean isProvided(int parameter) {
    if (parameter < 0 || parameter >= values.length)
      throw new IllegalArgumentException("constructor parameter is outside its binding");
    return provided.get(parameter);
  }

  Object parameterValue(int parameter) {
    if (!isProvided(parameter))
      throw new IllegalArgumentException("constructor parameter was not provided");
    return values[parameter];
  }

  Object[] typeArguments() {
    return reified.clone();
  }

  ConstructionArguments withParameter(int parameter, Object value) {
    if (!isProvided(parameter))
      throw new IllegalArgumentException("constructor parameter was not provided");
    var changed = values.clone();
    changed[parameter] = value;
    return new ConstructionArguments(changed, (BitSet) provided.clone(), reified);
  }

  void bind(
      VirtualFrame frame,
      FrameBinding[] bindings,
      dev.w0fv1.norm.core.CoreConstructorParameters contract) {
    if (values.length != contract.count() || bindings.length != values.length + reified.length + 1)
      throw new IllegalStateException(
          "constructor argument binding does not match its declaration");
    bindings[0].write(frame, frame.getArguments()[1]);
    for (int index = 0; index < values.length; index++) {
      if (provided.get(index)) bindings[index + 1].write(frame, values[index]);
      else if (!contract.defaulted().contains(index))
        throw new IllegalArgumentException("required constructor parameter is absent");
    }
    for (int index = 0; index < reified.length; index++)
      bindings[values.length + index + 1].write(frame, reified[index]);
  }
}
