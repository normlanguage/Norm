package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class ExpressionNodes {
  private ExpressionNodes() {}

  static final class Intrinsic extends ExpressionNode {
    private final IntrinsicId intrinsic;
    private final int[] parameterIndices;
    private final boolean nullSafe;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    @Child private ExpressionNode type;

    Intrinsic(
        IntrinsicId intrinsic,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode type,
        boolean nullSafe) {
      this.intrinsic = intrinsic;
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
      this.type = type;
      this.nullSafe = nullSafe;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object receiverValue = receiver == null ? null : receiver.execute(frame);
      if (nullSafe && receiverValue == RuntimeValues.NullValue.INSTANCE) {
        return RuntimeValues.NullValue.INSTANCE;
      }
      return IntrinsicDispatcher.execute(
          intrinsic,
          receiverValue,
          evaluateArguments(arguments, parameterIndices, frame),
          type == null ? null : (CoreType) type.execute(frame),
          ExecutionContextAccess.get(frame),
          this);
    }
  }

  static final class Literal extends ExpressionNode {
    private final Object value;

    Literal(Object value) {
      this.value = value;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return value;
    }
  }

  static final class NullLiteral extends ExpressionNode {
    @Override
    Object execute(VirtualFrame frame) {
      return RuntimeValues.NullValue.INSTANCE;
    }
  }

  static final class ArrayLiteral extends ExpressionNode {
    @Children private final ExpressionNode[] elements;
    @Child private ExpressionNode type;

    ArrayLiteral(ExpressionNode[] elements, ExpressionNode type) {
      this.elements = elements;
      this.type = type;
    }

    @Override
    Object execute(VirtualFrame frame) {
      List<Object> values = new ArrayList<>(elements.length);
      for (ExpressionNode element : elements) {
        values.add(RuntimeValues.copy(element.execute(frame)));
      }
      return new RuntimeValues.ArrayValue((CoreType) type.execute(frame), values);
    }
  }

  static final class TypeDescriptor extends ExpressionNode {
    private final CoreType template;
    private final int[] parameterIndices;
    private final FrameBinding[] bindings;

    TypeDescriptor(CoreType template, int[] parameterIndices, FrameBinding[] bindings) {
      this.template = template;
      this.parameterIndices = parameterIndices;
      this.bindings = bindings;
    }

    @Override
    Object execute(VirtualFrame frame) {
      if (bindings.length == 0) return template;
      HashMap<Integer, CoreType> substitutions = new HashMap<>();
      for (int index = 0; index < bindings.length; index++) {
        substitutions.put(parameterIndices[index], (CoreType) bindings[index].read(frame));
      }
      return template.substitute(substitutions::get);
    }
  }

  static final class ReadLocal extends ExpressionNode {
    private final FrameBinding binding;

    ReadLocal(FrameBinding binding) {
      this.binding = binding;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return binding.read(frame);
    }
  }

  abstract static class Unary extends ExpressionNode {
    @Child protected ExpressionNode operand;

    Unary(ExpressionNode operand) {
      this.operand = operand;
    }
  }

  static final class Negate extends Unary {
    Negate(ExpressionNode operand) {
      super(operand);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return -(Long) operand.execute(frame);
    }
  }

  static final class Not extends Unary {
    Not(ExpressionNode operand) {
      super(operand);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return !(Boolean) operand.execute(frame);
    }
  }

  abstract static class Binary extends ExpressionNode {
    @Child protected ExpressionNode left;
    @Child protected ExpressionNode right;

    Binary(ExpressionNode left, ExpressionNode right) {
      this.left = left;
      this.right = right;
    }
  }

  static final class Add extends Binary {
    Add(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) + (Long) right.execute(frame);
    }
  }

  static final class StringConcat extends Binary {
    StringConcat(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (String) left.execute(frame) + RuntimeValues.stringify(right.execute(frame));
    }
  }

  static final class Subtract extends Binary {
    Subtract(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) - (Long) right.execute(frame);
    }
  }

  static final class Multiply extends Binary {
    Multiply(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) * (Long) right.execute(frame);
    }
  }

  static final class Divide extends Binary {
    Divide(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      long dividend = (Long) left.execute(frame);
      long divisor = (Long) right.execute(frame);
      if (divisor == 0) {
        throw new NormGuestException(RuntimeErrorCode.DIVISION_BY_ZERO, "division by zero", this);
      }
      return dividend / divisor;
    }
  }

  static final class Remainder extends Binary {
    Remainder(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      long dividend = (Long) left.execute(frame);
      long divisor = (Long) right.execute(frame);
      if (divisor == 0) {
        throw new NormGuestException(RuntimeErrorCode.DIVISION_BY_ZERO, "division by zero", this);
      }
      return dividend % divisor;
    }
  }

  static final class Less extends Binary {
    Less(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) < (Long) right.execute(frame);
    }
  }

  static final class LessEqual extends Binary {
    LessEqual(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) <= (Long) right.execute(frame);
    }
  }

  static final class Greater extends Binary {
    Greater(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) > (Long) right.execute(frame);
    }
  }

  static final class GreaterEqual extends Binary {
    GreaterEqual(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) >= (Long) right.execute(frame);
    }
  }

  static final class Equal extends Binary {
    Equal(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return RuntimeValues.equal(left.execute(frame), right.execute(frame));
    }
  }

  static final class NotEqual extends Binary {
    NotEqual(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return !RuntimeValues.equal(left.execute(frame), right.execute(frame));
    }
  }

  static final class And extends Binary {
    And(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Boolean) left.execute(frame) && (Boolean) right.execute(frame);
    }
  }

  static final class Or extends Binary {
    Or(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Boolean) left.execute(frame) || (Boolean) right.execute(frame);
    }
  }

  static final class Coalesce extends Binary {
    Coalesce(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object value = left.execute(frame);
      return value == RuntimeValues.NullValue.INSTANCE ? right.execute(frame) : value;
    }
  }

  static final class FunctionCall extends ExpressionNode {
    @Child private DirectCallNode call;
    @Children private final ExpressionNode[] arguments;
    @Children private final ExpressionNode[] typeArguments;
    private final int[] parameterIndices;

    FunctionCall(
        CallTarget target,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode[] typeArguments) {
      call = DirectCallNode.create(target);
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
      this.typeArguments = typeArguments;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object[] values = evaluateArguments(arguments, parameterIndices, frame);
      Object[] complete = new Object[values.length + typeArguments.length + 1];
      complete[0] = ExecutionContextAccess.get(frame);
      System.arraycopy(values, 0, complete, 1, values.length);
      for (int index = 0; index < typeArguments.length; index++) {
        complete[values.length + index + 1] = typeArguments[index].execute(frame);
      }
      return call.call(complete);
    }
  }

  static final class MethodCall extends ExpressionNode {
    @Child private DirectCallNode call;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    @Children private final ExpressionNode[] typeArguments;
    private final int[] parameterIndices;
    private final boolean nullSafe;

    MethodCall(
        CallTarget target,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode[] typeArguments,
        boolean nullSafe) {
      call = DirectCallNode.create(target);
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
      this.typeArguments = typeArguments;
      this.nullSafe = nullSafe;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object receiverValue = receiver.execute(frame);
      if (nullSafe && receiverValue == RuntimeValues.NullValue.INSTANCE) {
        return RuntimeValues.NullValue.INSTANCE;
      }
      Object[] bound = evaluateArguments(arguments, parameterIndices, frame);
      int ownerTypeArgumentCount =
          receiverValue instanceof RuntimeValues.ObjectValue object
                  && object.type instanceof CoreType.Declared declared
              ? declared.arguments().size()
              : 0;
      Object[] values =
          new Object[bound.length + ownerTypeArgumentCount + typeArguments.length + 2];
      values[0] = ExecutionContextAccess.get(frame);
      values[1] = receiverValue;
      System.arraycopy(bound, 0, values, 2, bound.length);
      if (receiverValue instanceof RuntimeValues.ObjectValue object
          && object.type instanceof CoreType.Declared declared) {
        for (int index = 0; index < declared.arguments().size(); index++) {
          values[bound.length + index + 2] = declared.arguments().get(index);
        }
      }
      for (int index = 0; index < typeArguments.length; index++) {
        values[bound.length + ownerTypeArgumentCount + index + 2] =
            typeArguments[index].execute(frame);
      }
      return call.call(values);
    }
  }

  static final class Construct extends ExpressionNode {
    private final RuntimeValues.ClassInfo classInfo;
    @Child private ExpressionNode type;
    @Children private final ExpressionNode[] fields;
    private final int[] fieldIndices;

    Construct(
        RuntimeValues.ClassInfo classInfo,
        ExpressionNode type,
        ExpressionNode[] fields,
        int[] fieldIndices) {
      this.classInfo = classInfo;
      this.type = type;
      this.fields = fields;
      this.fieldIndices = fieldIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      RuntimeValues.ObjectValue object =
          new RuntimeValues.ObjectValue(classInfo, (CoreType) type.execute(frame));
      Object[] values = evaluateArguments(fields, fieldIndices, frame);
      System.arraycopy(values, 0, object.fields, 0, values.length);
      return object;
    }
  }

  static final class CopyObject extends ExpressionNode {
    @Child private ExpressionNode receiver;
    private final boolean nullSafe;

    CopyObject(ExpressionNode receiver, boolean nullSafe) {
      this.receiver = receiver;
      this.nullSafe = nullSafe;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object value = receiver.execute(frame);
      if (nullSafe && value == RuntimeValues.NullValue.INSTANCE) return value;
      return RuntimeValues.copyObject((RuntimeValues.ObjectValue) value);
    }
  }

  static final class EnumMember extends ExpressionNode {
    private final RuntimeValues.EnumValue value;

    EnumMember(DefinitionId definition, int memberOrdinal, String enumName, String member) {
      value = new RuntimeValues.EnumValue(definition, memberOrdinal, enumName, member);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return value;
    }
  }

  static final class ReadField extends ExpressionNode {
    @Child private ExpressionNode receiver;
    private final int field;
    private final boolean nullSafe;

    ReadField(ExpressionNode receiver, int field, boolean nullSafe) {
      this.receiver = receiver;
      this.field = field;
      this.nullSafe = nullSafe;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object receiverValue = receiver.execute(frame);
      if (nullSafe && receiverValue == RuntimeValues.NullValue.INSTANCE) return receiverValue;
      return ((RuntimeValues.ObjectValue) receiverValue).fields[field];
    }
  }

  private static Object[] evaluateArguments(
      ExpressionNode[] arguments, int[] parameterIndices, VirtualFrame frame) {
    Object[] values = new Object[arguments.length];
    for (int sourceIndex = 0; sourceIndex < arguments.length; sourceIndex++) {
      values[parameterIndices[sourceIndex]] =
          RuntimeValues.copy(arguments[sourceIndex].execute(frame));
    }
    return values;
  }
}
