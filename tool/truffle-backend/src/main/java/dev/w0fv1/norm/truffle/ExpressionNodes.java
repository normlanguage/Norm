package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class ExpressionNodes {
  private ExpressionNodes() {}

  private static Number negate(Number value) {
    return switch (value) {
      case Integer integer -> -integer;
      case Long integer -> -integer;
      case Float decimal -> -decimal;
      case Double decimal -> -decimal;
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static Number add(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer + right.intValue();
      case Long integer -> integer + right.longValue();
      case Float decimal -> decimal + right.floatValue();
      case Double decimal -> decimal + right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static Number subtract(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer - right.intValue();
      case Long integer -> integer - right.longValue();
      case Float decimal -> decimal - right.floatValue();
      case Double decimal -> decimal - right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static Number multiply(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer * right.intValue();
      case Long integer -> integer * right.longValue();
      case Float decimal -> decimal * right.floatValue();
      case Double decimal -> decimal * right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static Number divide(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer / right.intValue();
      case Long integer -> integer / right.longValue();
      case Float decimal -> decimal / right.floatValue();
      case Double decimal -> decimal / right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static Number remainder(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer % right.intValue();
      case Long integer -> integer % right.longValue();
      case Float decimal -> decimal % right.floatValue();
      case Double decimal -> decimal % right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static boolean less(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer < right.intValue();
      case Long integer -> integer < right.longValue();
      case Float decimal -> decimal < right.floatValue();
      case Double decimal -> decimal < right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static boolean lessEqual(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer <= right.intValue();
      case Long integer -> integer <= right.longValue();
      case Float decimal -> decimal <= right.floatValue();
      case Double decimal -> decimal <= right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static boolean greater(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer > right.intValue();
      case Long integer -> integer > right.longValue();
      case Float decimal -> decimal > right.floatValue();
      case Double decimal -> decimal > right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  private static boolean greaterEqual(Number left, Number right) {
    return switch (left) {
      case Integer integer -> integer >= right.intValue();
      case Long integer -> integer >= right.longValue();
      case Float decimal -> decimal >= right.floatValue();
      case Double decimal -> decimal >= right.doubleValue();
      default -> throw new IllegalStateException("unsupported numeric value");
    };
  }

  static final class Switch extends ExpressionNode {
    @Child private ExpressionNode value;
    @Children private final PatternNode[] patterns;
    @Children private final StatementNode[] bodies;

    Switch(ExpressionNode value, PatternNode[] patterns, StatementNode[] bodies) {
      this.value = value;
      this.patterns = patterns;
      this.bodies = bodies;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object matchedValue = value.execute(frame);
      for (int index = 0; index < patterns.length; index++) {
        if (!patterns[index].matches(matchedValue, frame)) continue;
        try {
          bodies[index].executeVoid(frame);
          return null;
        } catch (ControlFlow.Yield yielded) {
          return yielded.value;
        }
      }
      throw new IllegalStateException("verified switch has no matching case");
    }
  }

  static final class Intrinsic extends ExpressionNode {
    private final IntrinsicId intrinsic;
    private final int[] parameterIndices;
    private final boolean nullSafe;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    @Child private ExpressionNode type;
    private final AnnotationRuntime annotations;

    Intrinsic(
        IntrinsicId intrinsic,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode type,
        boolean nullSafe,
        AnnotationRuntime annotations) {
      this.intrinsic = intrinsic;
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
      this.type = type;
      this.nullSafe = nullSafe;
      this.annotations = annotations;
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
          this,
          annotations,
          ExecutionContextAccess.state(frame));
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

  static final class CollectionLiteral extends ExpressionNode {
    private final IntrinsicId materializer;
    @Children private final ExpressionNode[] elements;
    @Child private ExpressionNode type;

    CollectionLiteral(IntrinsicId materializer, ExpressionNode[] elements, ExpressionNode type) {
      this.materializer = materializer;
      this.elements = elements;
      this.type = type;
    }

    @Override
    Object execute(VirtualFrame frame) {
      List<Object> values = new ArrayList<>(elements.length);
      for (ExpressionNode element : elements) {
        values.add(RuntimeValues.copy(element.execute(frame)));
      }
      CoreType runtimeType = (CoreType) type.execute(frame);
      return switch (materializer) {
        case ARRAY_CONSTRUCT -> new RuntimeValues.ArrayValue(runtimeType, values);
        case LIST_CONSTRUCT -> new RuntimeValues.ListValue(runtimeType, values);
        default -> throw new IllegalStateException("unsupported collection literal materializer");
      };
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

  static final class AddressLocal extends ExpressionNode {
    private final FrameBinding binding;

    AddressLocal(FrameBinding binding) {
      this.binding = binding;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return new RuntimeValues.LocalReference(frame.materialize(), binding);
    }
  }

  static final class AddressField extends ExpressionNode {
    @Child private ExpressionNode receiver;
    private final int field;

    AddressField(ExpressionNode receiver, int field) {
      this.receiver = receiver;
      this.field = field;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return new RuntimeValues.FieldReference(
          (RuntimeValues.ObjectValue) receiver.execute(frame), field);
    }
  }

  static final class Dereference extends ExpressionNode {
    @Child private ExpressionNode reference;

    Dereference(ExpressionNode reference) {
      this.reference = reference;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return RuntimeValues.copy(((RuntimeValues.ReferenceValue) reference.execute(frame)).read());
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
      return negate((Number) operand.execute(frame));
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
      return add((Number) left.execute(frame), (Number) right.execute(frame));
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
      return subtract((Number) left.execute(frame), (Number) right.execute(frame));
    }
  }

  static final class Multiply extends Binary {
    Multiply(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return multiply((Number) left.execute(frame), (Number) right.execute(frame));
    }
  }

  static final class Divide extends Binary {
    Divide(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      Number dividend = (Number) left.execute(frame);
      Number divisor = (Number) right.execute(frame);
      if ((divisor instanceof Integer && divisor.intValue() == 0)
          || (divisor instanceof Long && divisor.longValue() == 0)) {
        throw new NormGuestException(RuntimeErrorCode.DIVISION_BY_ZERO, "division by zero", this);
      }
      return divide(dividend, divisor);
    }
  }

  static final class Remainder extends Binary {
    Remainder(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      Number dividend = (Number) left.execute(frame);
      Number divisor = (Number) right.execute(frame);
      if ((divisor instanceof Integer && divisor.intValue() == 0)
          || (divisor instanceof Long && divisor.longValue() == 0)) {
        throw new NormGuestException(RuntimeErrorCode.DIVISION_BY_ZERO, "division by zero", this);
      }
      return remainder(dividend, divisor);
    }
  }

  static final class Less extends Binary {
    Less(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return less((Number) left.execute(frame), (Number) right.execute(frame));
    }
  }

  static final class LessEqual extends Binary {
    LessEqual(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return lessEqual((Number) left.execute(frame), (Number) right.execute(frame));
    }
  }

  static final class Greater extends Binary {
    Greater(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return greater((Number) left.execute(frame), (Number) right.execute(frame));
    }
  }

  static final class GreaterEqual extends Binary {
    GreaterEqual(ExpressionNode left, ExpressionNode right) {
      super(left, right);
    }

    @Override
    Object execute(VirtualFrame frame) {
      return greaterEqual((Number) left.execute(frame), (Number) right.execute(frame));
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
      complete[0] = ExecutionContextAccess.state(frame);
      System.arraycopy(values, 0, complete, 1, values.length);
      for (int index = 0; index < typeArguments.length; index++) {
        complete[values.length + index + 1] = typeArguments[index].execute(frame);
      }
      return call.call(complete);
    }
  }

  static final class Closure extends ExpressionNode {
    private final CallTarget target;
    private final DefinitionId virtualSlot;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] captures;
    @Children private final ExpressionNode[] reifiedArguments;
    @Children private final ExpressionNode[] receiverTypeArguments;

    Closure(
        CallTarget target,
        DefinitionId virtualSlot,
        ExpressionNode receiver,
        ExpressionNode[] captures,
        ExpressionNode[] reifiedArguments,
        ExpressionNode[] receiverTypeArguments) {
      this.target = target;
      this.virtualSlot = virtualSlot;
      this.receiver = receiver;
      this.captures = captures;
      this.reifiedArguments = reifiedArguments;
      this.receiverTypeArguments = receiverTypeArguments;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object[] values = new Object[captures.length];
      for (int index = 0; index < captures.length; index++) {
        values[index] = RuntimeValues.copy(captures[index].execute(frame));
      }
      Object[] reified = new Object[reifiedArguments.length];
      for (int index = 0; index < reifiedArguments.length; index++) {
        reified[index] = reifiedArguments[index].execute(frame);
      }
      Object receiverValue = receiver == null ? null : receiver.execute(frame);
      CallTarget resolvedTarget = target;
      Object[] ownerArguments = new Object[receiverTypeArguments.length];
      for (int index = 0; index < receiverTypeArguments.length; index++) {
        ownerArguments[index] = receiverTypeArguments[index].execute(frame);
      }
      if (virtualSlot != null) {
        RuntimeValues.ObjectValue object = (RuntimeValues.ObjectValue) receiverValue;
        RuntimeValues.DispatchTarget.Callable dispatch =
            (RuntimeValues.DispatchTarget.Callable) object.objectInfo.dispatch().get(virtualSlot);
        resolvedTarget = dispatch.target();
        List<CoreType> concreteArguments =
            object.type instanceof CoreType.Declared declared ? declared.arguments() : List.of();
        ownerArguments =
            dispatch.receiverTypeArguments().stream()
                .map(type -> type.substitute(concreteArguments::get))
                .toArray();
      }
      return new RuntimeValues.Closure(
          resolvedTarget, receiverValue, values, ownerArguments, reified);
    }
  }

  static final class Invoke extends ExpressionNode {
    @Child private IndirectCallNode call = IndirectCallNode.create();
    @Child private ExpressionNode callee;
    @Children private final ExpressionNode[] arguments;
    private final int[] parameterIndices;

    Invoke(ExpressionNode callee, ExpressionNode[] arguments, int[] parameterIndices) {
      this.callee = callee;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      RuntimeValues.Closure closure = (RuntimeValues.Closure) callee.execute(frame);
      Object[] values = evaluateArguments(arguments, parameterIndices, frame);
      return call.call(
          closure.target(),
          RuntimeValues.invocationArguments(ExecutionContextAccess.state(frame), closure, values));
    }
  }

  static final class MethodCall extends ExpressionNode {
    @Child private DirectCallNode call;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    @Children private final ExpressionNode[] typeArguments;
    @Children private final ExpressionNode[] receiverTypeArguments;
    private final int[] parameterIndices;
    private final boolean nullSafe;

    MethodCall(
        CallTarget target,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode[] typeArguments,
        ExpressionNode[] receiverTypeArguments,
        boolean nullSafe) {
      call = DirectCallNode.create(target);
      this.receiver = receiver;
      this.arguments = arguments;
      this.parameterIndices = parameterIndices;
      this.typeArguments = typeArguments;
      this.receiverTypeArguments = receiverTypeArguments;
      this.nullSafe = nullSafe;
    }

    @Override
    Object execute(VirtualFrame frame) {
      Object receiverValue = receiver.execute(frame);
      if (nullSafe && receiverValue == RuntimeValues.NullValue.INSTANCE) {
        return RuntimeValues.NullValue.INSTANCE;
      }
      Object[] bound = evaluateArguments(arguments, parameterIndices, frame);
      int ownerTypeArgumentCount = receiverTypeArguments.length;
      Object[] values =
          new Object[bound.length + ownerTypeArgumentCount + typeArguments.length + 2];
      values[0] = ExecutionContextAccess.state(frame);
      values[1] = receiverValue;
      System.arraycopy(bound, 0, values, 2, bound.length);
      for (int index = 0; index < receiverTypeArguments.length; index++) {
        values[bound.length + index + 2] = receiverTypeArguments[index].execute(frame);
      }
      for (int index = 0; index < typeArguments.length; index++) {
        values[bound.length + ownerTypeArgumentCount + index + 2] =
            typeArguments[index].execute(frame);
      }
      return call.call(values);
    }
  }

  static final class DispatchedCall extends ExpressionNode {
    private final int[] parameterIndices;
    private final boolean nullSafe;
    @Child private MethodDispatchNode dispatch;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;
    @Children private final ExpressionNode[] typeArguments;

    DispatchedCall(
        DefinitionId requirement,
        ExpressionNode receiver,
        ExpressionNode[] arguments,
        int[] parameterIndices,
        ExpressionNode[] typeArguments,
        boolean nullSafe,
        java.util.Map<
                dev.w0fv1.norm.core.BuiltinTypeId,
                java.util.Map<DefinitionId, RuntimeValues.DispatchTarget>>
            builtinDispatch) {
      dispatch = new MethodDispatchNode(requirement, builtinDispatch);
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
      Object[] reified = new Object[typeArguments.length];
      for (int index = 0; index < typeArguments.length; index++) {
        reified[index] = typeArguments[index].execute(frame);
      }
      return dispatch.execute(frame, receiverValue, bound, reified, this);
    }
  }

  static final class Construct extends ExpressionNode {
    private final RuntimeValues.AggregateInfo aggregateInfo;
    @Child private DirectCallNode initializer;
    @Child private ExpressionNode type;
    @Children private final ExpressionNode[] fields;
    private final int[] fieldIndices;

    Construct(
        RuntimeValues.AggregateInfo aggregateInfo,
        CallTarget initializer,
        ExpressionNode type,
        ExpressionNode[] fields,
        int[] fieldIndices) {
      this.aggregateInfo = aggregateInfo;
      this.initializer = DirectCallNode.create(initializer);
      this.type = type;
      this.fields = fields;
      this.fieldIndices = fieldIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      RuntimeValues.ObjectValue object =
          new RuntimeValues.ObjectValue(aggregateInfo, (CoreType) type.execute(frame));
      Object[] values = evaluateArguments(fields, fieldIndices, frame);
      List<CoreType> ownerArguments =
          object.type instanceof CoreType.Declared declared ? declared.arguments() : List.of();
      Object[] callArguments = new Object[values.length + ownerArguments.size() + 2];
      callArguments[0] = ExecutionContextAccess.state(frame);
      callArguments[1] = object;
      System.arraycopy(values, 0, callArguments, 2, values.length);
      for (int index = 0; index < ownerArguments.size(); index++) {
        callArguments[values.length + index + 2] = ownerArguments.get(index);
      }
      initializer.call(callArguments);
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

  static final class EnumConstruct extends ExpressionNode {
    private final DefinitionId definition;
    private final String enumName;
    private final String variantKey;
    @Child private ExpressionNode type;
    @Children private final ExpressionNode[] fields;
    private final int[] fieldIndices;

    EnumConstruct(
        DefinitionId definition,
        String enumName,
        String variantKey,
        ExpressionNode type,
        ExpressionNode[] fields,
        int[] fieldIndices) {
      this.definition = definition;
      this.enumName = enumName;
      this.variantKey = variantKey;
      this.type = type;
      this.fields = fields;
      this.fieldIndices = fieldIndices;
    }

    @Override
    Object execute(VirtualFrame frame) {
      return new RuntimeValues.EnumValue(
          definition,
          (CoreType) type.execute(frame),
          enumName,
          variantKey,
          java.util.Arrays.asList(evaluateArguments(fields, fieldIndices, frame)));
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
      RuntimeValues.ObjectValue object = (RuntimeValues.ObjectValue) receiverValue;
      Object value = object.fields[field];
      return object.type instanceof CoreType.Declared declared
              && declared.category() == dev.w0fv1.norm.core.CoreValueCategory.VALUE
          ? RuntimeValues.copy(value)
          : value;
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
