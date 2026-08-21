package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

final class ExpressionNodes {
  private ExpressionNodes() {}

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

    ArrayLiteral(ExpressionNode[] elements) {
      this.elements = elements;
    }

    @Override
    Object execute(VirtualFrame frame) {
      List<Object> values = new ArrayList<>(elements.length);
      for (ExpressionNode element : elements) {
        values.add(RuntimeValues.copy(element.execute(frame)));
      }
      return new RuntimeValues.ArrayValue(values);
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
      return (Long) left.execute(frame) / (Long) right.execute(frame);
    }
  }

  static final class Remainder extends Binary {
    Remainder(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return (Long) left.execute(frame) % (Long) right.execute(frame);
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
    private final int[] parameterIndices;

    FunctionCall(CallTarget target, ExpressionNode[] arguments, int[] parameterIndices) {
      call = DirectCallNode.create(target);
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return call.call(evaluateArguments(arguments, parameterIndices, frame));
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
      return call.call(values);
    }
  }

  static final class Print extends ExpressionNode {
    @Child private ExpressionNode value;
    private final PrintWriter output;

    Print(ExpressionNode value, PrintWriter output) {
      this.value = value;
      this.output = output;
    }

    @Override
    Object execute(VirtualFrame frame) {
      print(RuntimeValues.stringify(value.execute(frame)));
      return null;
    }

    @TruffleBoundary
    private void print(String value) {
      output.println(value);
    }
  }

  static final class Range extends ExpressionNode {
    @Children private final ExpressionNode[] arguments;
    private final int[] parameterIndices;

    Range(ExpressionNode[] arguments, int[] parameterIndices) {
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object[] values = evaluateArguments(arguments, parameterIndices, frame);
      return new RuntimeValues.RangeValue((Long) values[0], (Long) values[1]);
    }
  }

  static final class Minimum extends BoundBinary {
    Minimum(ExpressionNode[] arguments, int[] parameterIndices) {
      super(arguments, parameterIndices);
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object[] values = values(frame);
      return Math.min((Long) values[0], (Long) values[1]);
    }
  }

  static final class Maximum extends BoundBinary {
    Maximum(ExpressionNode[] arguments, int[] parameterIndices) {
      super(arguments, parameterIndices);
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object[] values = values(frame);
      return Math.max((Long) values[0], (Long) values[1]);
    }
  }

  static final class Absolute extends Unary {
    Absolute(ExpressionNode operand) {
      super(operand);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return Math.abs((Long) operand.execute(frame));
    }
  }

  enum NewKind {
    ARRAY,
    LIST,
    MAP,
    SET,
    STACK,
    QUEUE,
    DEQUE,
    BUILDER
  }

  static final class NewValue extends ExpressionNode {
    private final NewKind kind;

    NewValue(NewKind kind) {
      this.kind = kind;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return switch (kind) {
        case ARRAY -> new RuntimeValues.ArrayValue(new ArrayList<>());
        case LIST -> new RuntimeValues.ListValue();
        case MAP -> new RuntimeValues.MapValue();
        case SET -> new RuntimeValues.SetValue();
        case STACK -> new RuntimeValues.StackValue();
        case QUEUE -> new RuntimeValues.QueueValue();
        case DEQUE -> new RuntimeValues.DequeValue();
        case BUILDER -> new RuntimeValues.BuilderValue();
      };
    }
  }

  static final class Pair extends BoundBinary {
    Pair(ExpressionNode[] arguments, int[] parameterIndices) {
      super(arguments, parameterIndices);
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object[] values = values(frame);
      return new RuntimeValues.PairValue(values[0], values[1]);
    }
  }

  static final class Construct extends ExpressionNode {
    private final RuntimeValues.ClassInfo type;
    @Children private final ExpressionNode[] fields;
    private final int[] fieldIndices;

    Construct(RuntimeValues.ClassInfo type, ExpressionNode[] fields, int[] fieldIndices) {
      this.type = type;
      this.fields = fields;
      this.fieldIndices = fieldIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      RuntimeValues.ObjectValue object = new RuntimeValues.ObjectValue(type);
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
    private final boolean copy;

    ReadField(ExpressionNode receiver, int field, boolean copy) {
      this.receiver = receiver;
      this.field = field;
      this.copy = copy;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object value = ((RuntimeValues.ObjectValue) receiver.execute(frame)).fields[field];
      return copy ? RuntimeValues.copy(value) : value;
    }
  }

  static final class ReadPair extends ExpressionNode {
    @Child private ExpressionNode receiver;
    private final boolean first;

    ReadPair(ExpressionNode receiver, boolean first) {
      this.receiver = receiver;
      this.first = first;
    }

    @Override
    Object execute(VirtualFrame frame) {
      RuntimeValues.PairValue pair = (RuntimeValues.PairValue) receiver.execute(frame);
      return RuntimeValues.copy(first ? pair.first : pair.second);
    }
  }

  static final class Length extends ExpressionNode {
    @Child private ExpressionNode receiver;

    Length(ExpressionNode receiver) {
      this.receiver = receiver;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return RuntimeValues.length(receiver.execute(frame));
    }
  }

  static final class Index extends ExpressionNode {
    @Child private ExpressionNode receiver;
    @Child private ExpressionNode index;

    Index(ExpressionNode receiver, ExpressionNode index) {
      this.receiver = receiver;
      this.index = index;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object target = receiver.execute(frame);
      Object key = index.execute(frame);
      Object value =
          switch (target) {
            case RuntimeValues.ArrayValue array -> array.values.get(Math.toIntExact((Long) key));
            case RuntimeValues.ListValue list -> list.values.get(Math.toIntExact((Long) key));
            case RuntimeValues.MapValue map -> RuntimeValues.mapGet(map, key);
            default -> throw new IllegalStateException("value is not indexable");
          };
      return RuntimeValues.copy(value);
    }
  }

  enum MethodKind {
    LIST_ADD,
    LIST_GET,
    LIST_REMOVE_AT,
    MAP_PUT,
    MAP_GET,
    MAP_CONTAINS_KEY,
    MAP_REMOVE,
    SET_ADD,
    SET_CONTAINS,
    SET_REMOVE,
    STACK_PUSH,
    STACK_POP,
    STACK_PEEK,
    QUEUE_ADD,
    QUEUE_REMOVE,
    QUEUE_PEEK,
    DEQUE_ADD_FIRST,
    DEQUE_ADD_LAST,
    DEQUE_REMOVE_FIRST,
    DEQUE_REMOVE_LAST,
    DEQUE_PEEK_FIRST,
    DEQUE_PEEK_LAST,
    BUILDER_APPEND,
    BUILDER_TO_STRING,
    IS_EMPTY
  }

  static final class BuiltinMethod extends ExpressionNode {
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    private final MethodKind kind;
    private final int[] parameterIndices;

    BuiltinMethod(
        MethodKind kind,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices) {
      this.kind = kind;
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object target = receiver.execute(frame);
      Object[] values = evaluateArguments(arguments, parameterIndices, frame);
      Object first = values.length == 0 ? null : values[0];
      Object second = values.length < 2 ? null : values[1];
      return switch (kind) {
        case LIST_ADD -> {
          ((RuntimeValues.ListValue) target).values.add(RuntimeValues.copy(first));
          yield null;
        }
        case LIST_GET ->
            RuntimeValues.copy(
                ((RuntimeValues.ListValue) target).values.get(Math.toIntExact((Long) first)));
        case LIST_REMOVE_AT ->
            RuntimeValues.copy(
                ((RuntimeValues.ListValue) target).values.remove(Math.toIntExact((Long) first)));
        case MAP_PUT -> {
          RuntimeValues.mapPut((RuntimeValues.MapValue) target, first, second);
          yield null;
        }
        case MAP_GET ->
            RuntimeValues.copy(RuntimeValues.mapGet((RuntimeValues.MapValue) target, first));
        case MAP_CONTAINS_KEY -> RuntimeValues.mapContains((RuntimeValues.MapValue) target, first);
        case MAP_REMOVE -> RuntimeValues.mapRemove((RuntimeValues.MapValue) target, first);
        case SET_ADD -> RuntimeValues.setAdd((RuntimeValues.SetValue) target, first);
        case SET_CONTAINS -> RuntimeValues.setContains((RuntimeValues.SetValue) target, first);
        case SET_REMOVE -> RuntimeValues.setRemove((RuntimeValues.SetValue) target, first);
        case STACK_PUSH -> {
          ((RuntimeValues.StackValue) target).values.push(RuntimeValues.copy(first));
          yield null;
        }
        case STACK_POP -> RuntimeValues.copy(((RuntimeValues.StackValue) target).values.pop());
        case STACK_PEEK -> RuntimeValues.copy(((RuntimeValues.StackValue) target).values.peek());
        case QUEUE_ADD -> {
          ((RuntimeValues.QueueValue) target).values.addLast(RuntimeValues.copy(first));
          yield null;
        }
        case QUEUE_REMOVE ->
            RuntimeValues.copy(((RuntimeValues.QueueValue) target).values.removeFirst());
        case QUEUE_PEEK ->
            RuntimeValues.copy(((RuntimeValues.QueueValue) target).values.peekFirst());
        case DEQUE_ADD_FIRST -> {
          ((RuntimeValues.DequeValue) target).values.addFirst(RuntimeValues.copy(first));
          yield null;
        }
        case DEQUE_ADD_LAST -> {
          ((RuntimeValues.DequeValue) target).values.addLast(RuntimeValues.copy(first));
          yield null;
        }
        case DEQUE_REMOVE_FIRST ->
            RuntimeValues.copy(((RuntimeValues.DequeValue) target).values.removeFirst());
        case DEQUE_REMOVE_LAST ->
            RuntimeValues.copy(((RuntimeValues.DequeValue) target).values.removeLast());
        case DEQUE_PEEK_FIRST ->
            RuntimeValues.copy(((RuntimeValues.DequeValue) target).values.peekFirst());
        case DEQUE_PEEK_LAST ->
            RuntimeValues.copy(((RuntimeValues.DequeValue) target).values.peekLast());
        case BUILDER_APPEND -> {
          RuntimeValues.BuilderValue builder = (RuntimeValues.BuilderValue) target;
          builder.value.append(RuntimeValues.stringify(first));
          yield builder;
        }
        case BUILDER_TO_STRING -> ((RuntimeValues.BuilderValue) target).value.toString();
        case IS_EMPTY -> isEmpty(target);
      };
    }

    private static boolean isEmpty(Object value) {
      return switch (value) {
        case RuntimeValues.ListValue list -> list.values.isEmpty();
        case RuntimeValues.MapValue map -> map.values.isEmpty();
        case RuntimeValues.SetValue set -> set.values.isEmpty();
        case RuntimeValues.StackValue stack -> stack.values.isEmpty();
        case RuntimeValues.QueueValue queue -> queue.values.isEmpty();
        case RuntimeValues.DequeValue deque -> deque.values.isEmpty();
        default -> throw new IllegalStateException("value has no isEmpty operation");
      };
    }
  }

  abstract static class BoundBinary extends ExpressionNode {
    @Children private final ExpressionNode[] arguments;
    private final int[] parameterIndices;

    BoundBinary(ExpressionNode[] arguments, int[] parameterIndices) {
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
    }

    final Object[] values(VirtualFrame frame) {
      return evaluateArguments(arguments, parameterIndices, frame);
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
