package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class ExpressionNodes {
  private ExpressionNodes() {}

  static final class Intrinsic extends ExpressionNode {
    private final IntrinsicId intrinsic;
    private final int[] parameterIndices;
    private final ExecutionContext context;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    @Child private ExpressionNode type;

    Intrinsic(
        IntrinsicId intrinsic,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode type,
        ExecutionContext context) {
      this.intrinsic = intrinsic;
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
      this.type = type;
      this.context = context;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return IntrinsicDispatcher.execute(
          intrinsic,
          receiver == null ? null : receiver.execute(frame),
          evaluateArguments(arguments, parameterIndices, frame),
          type == null ? null : (SemanticType) type.execute(frame),
          context,
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
      return new RuntimeValues.ArrayValue((SemanticType) type.execute(frame), values);
    }
  }

  static final class TypeDescriptor extends ExpressionNode {
    private final SemanticType template;
    private final String[] identities;
    private final FrameBinding[] bindings;

    TypeDescriptor(SemanticType template, String[] identities, FrameBinding[] bindings) {
      this.template = template;
      this.identities = identities;
      this.bindings = bindings;
    }

    @Override
    Object execute(VirtualFrame frame) {
      if (bindings.length == 0) return template;
      LinkedHashMap<String, SemanticType> substitutions = new LinkedHashMap<>();
      for (int index = 0; index < bindings.length; index++) {
        substitutions.put(identities[index], (SemanticType) bindings[index].read(frame));
      }
      return template.substitute(substitutions);
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
      Object[] complete = new Object[values.length + typeArguments.length];
      System.arraycopy(values, 0, complete, 0, values.length);
      for (int index = 0; index < typeArguments.length; index++) {
        complete[values.length + index] = typeArguments[index].execute(frame);
      }
      return call.call(complete);
    }
  }

  static final class MethodCall extends ExpressionNode {
    @Child private DirectCallNode call;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    private final int[] parameterIndices;

    MethodCall(
        CallTarget target,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices) {
      call = DirectCallNode.create(target);
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object receiverValue = receiver.execute(frame);
      Object[] bound = evaluateArguments(arguments, parameterIndices, frame);
      Object[] values = new Object[bound.length + 1];
      values[0] = receiverValue;
      System.arraycopy(bound, 0, values, 1, bound.length);
      if (receiverValue instanceof RuntimeValues.ObjectValue object
          && !object.type.arguments().isEmpty()) {
        Object[] complete = new Object[values.length + object.type.arguments().size()];
        System.arraycopy(values, 0, complete, 0, values.length);
        for (int index = 0; index < object.type.arguments().size(); index++) {
          complete[values.length + index] = object.type.arguments().get(index);
        }
        return call.call(complete);
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
          new RuntimeValues.ObjectValue(classInfo, (SemanticType) type.execute(frame));
      Object[] values = evaluateArguments(fields, fieldIndices, frame);
      System.arraycopy(values, 0, object.fields, 0, values.length);
      return object;
    }
  }

  static final class CopyObject extends ExpressionNode {
    @Child private ExpressionNode receiver;

    CopyObject(ExpressionNode receiver) {
      this.receiver = receiver;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return RuntimeValues.copyObject((RuntimeValues.ObjectValue) receiver.execute(frame));
    }
  }

  static final class EnumMember extends ExpressionNode {
    private final RuntimeValues.EnumValue value;

    EnumMember(String enumName, String member) {
      value = new RuntimeValues.EnumValue(enumName, member);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return value;
    }
  }

  static final class ReadField extends ExpressionNode {
    @Child private ExpressionNode receiver;
    private final int field;

    ReadField(ExpressionNode receiver, int field) {
      this.receiver = receiver;
      this.field = field;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object value = ((RuntimeValues.ObjectValue) receiver.execute(frame)).fields[field];
      return value;
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
