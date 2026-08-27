package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.Map;

final class StatementNodes {
  private StatementNodes() {}

  static final class Block extends StatementNode {
    @Children private final StatementNode[] statements;

    Block(StatementNode[] statements) {
      this.statements = statements;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      for (StatementNode statement : statements) {
        statement.executeVoid(frame);
      }
    }
  }

  static final class WriteLocal extends StatementNode {
    private final FrameBinding binding;
    @Child private ExpressionNode value;

    WriteLocal(FrameBinding binding, ExpressionNode value) {
      this.binding = binding;
      this.value = value;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      binding.write(frame, RuntimeValues.copy(value.execute(frame)));
    }
  }

  static final class ExpressionStatement extends StatementNode {
    @Child private ExpressionNode expression;

    ExpressionStatement(ExpressionNode expression) {
      this.expression = expression;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      expression.execute(frame);
    }
  }

  static final class If extends StatementNode {
    @Child private ExpressionNode condition;
    @Child private StatementNode thenBody;
    @Child private StatementNode elseBody;

    If(ExpressionNode condition, StatementNode thenBody, StatementNode elseBody) {
      this.condition = condition;
      this.thenBody = thenBody;
      this.elseBody = elseBody;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      if ((Boolean) condition.execute(frame)) {
        thenBody.executeVoid(frame);
      } else {
        elseBody.executeVoid(frame);
      }
    }
  }

  static final class Return extends StatementNode {
    @Child private ExpressionNode value;

    Return(ExpressionNode value) {
      this.value = value;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      throw new ControlFlow.Return(value == null ? null : RuntimeValues.copy(value.execute(frame)));
    }
  }

  static final class Break extends StatementNode {
    @Override
    void executeVoid(VirtualFrame frame) {
      throw ControlFlow.Break.INSTANCE;
    }
  }

  static final class Yield extends StatementNode {
    @Child private ExpressionNode value;

    Yield(ExpressionNode value) {
      this.value = value;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      throw new ControlFlow.Yield(RuntimeValues.copy(value.execute(frame)));
    }
  }

  static final class Continue extends StatementNode {
    @Override
    void executeVoid(VirtualFrame frame) {
      throw ControlFlow.Continue.INSTANCE;
    }
  }

  static final class Throw extends StatementNode {
    @Child private ExpressionNode exception;

    Throw(ExpressionNode exception) {
      this.exception = exception;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      throw new NormThrownException((RuntimeValues.ObjectValue) exception.execute(frame), this);
    }
  }

  static final class Try extends StatementNode {
    @Child private StatementNode body;
    @Children private final CatchHandler[] catches;
    @Child private StatementNode finallyBlock;

    Try(StatementNode body, CatchHandler[] catches, StatementNode finallyBlock) {
      this.body = body;
      this.catches = catches;
      this.finallyBlock = finallyBlock;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      try {
        try {
          body.executeVoid(frame);
        } catch (NormThrownException thrown) {
          for (CatchHandler handler : catches) {
            if (handler.matches(thrown.value)) {
              handler.execute(frame, thrown.value);
              return;
            }
          }
          throw thrown;
        }
      } finally {
        if (finallyBlock != null) finallyBlock.executeVoid(frame);
      }
    }
  }

  static final class CatchHandler extends Node {
    private final DefinitionId type;
    private final FrameBinding binding;
    @Child private StatementNode body;

    CatchHandler(DefinitionId type, FrameBinding binding, StatementNode body) {
      this.type = type;
      this.binding = binding;
      this.body = body;
    }

    boolean matches(RuntimeValues.ObjectValue value) {
      return value.aggregateInfo.ancestors().contains(type);
    }

    void execute(VirtualFrame frame, RuntimeValues.ObjectValue value) {
      binding.write(frame, value);
      try {
        body.executeVoid(frame);
      } finally {
        frame.clear(binding.slot());
      }
    }
  }

  static final class For extends StatementNode {
    private final FrameBinding iteratorBinding;
    private final java.util.Optional<FrameBinding> indexBinding;
    @Child private ExpressionNode iterable;
    @Child private IteratorFactoryNode iteratorFactory;
    @Child private LoopNode loop;

    For(
        FrameBinding iteratorBinding,
        FrameBinding variableBinding,
        java.util.Optional<FrameBinding> indexBinding,
        ExpressionNode iterable,
        StatementNode body,
        IteratorFactoryNode iteratorFactory,
        IteratorCursorNode iteratorCursor) {
      this.iteratorBinding = iteratorBinding;
      this.indexBinding = java.util.Objects.requireNonNull(indexBinding, "indexBinding");
      this.iterable = iterable;
      this.iteratorFactory = iteratorFactory;
      loop =
          Truffle.getRuntime()
              .createLoopNode(
                  new Repeating(
                      iteratorBinding, variableBinding, indexBinding, body, iteratorCursor));
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      if (ExecutionContextAccess.get(frame).cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", this);
      }
      iteratorBinding.write(
          frame, new IterationState(iteratorFactory.create(frame, iterable.execute(frame), this)));
      try {
        loop.execute(frame);
      } finally {
        frame.clear(iteratorBinding.slot());
        indexBinding.ifPresent(binding -> frame.clear(binding.slot()));
      }
    }
  }

  abstract static class IteratorFactoryNode extends Node {
    abstract Object create(VirtualFrame frame, Object iterable, Node location);
  }

  static final class BuiltinIteratorFactory extends IteratorFactoryNode {
    private final IntrinsicId intrinsic;

    BuiltinIteratorFactory(IntrinsicId intrinsic) {
      this.intrinsic = intrinsic;
    }

    @Override
    Object create(VirtualFrame frame, Object iterable, Node location) {
      return IntrinsicDispatcher.execute(
          intrinsic, iterable, new Object[0], null, ExecutionContextAccess.get(frame), location);
    }
  }

  static final class InterfaceIteratorFactory extends IteratorFactoryNode {
    @Child private MethodDispatchNode dispatch;

    InterfaceIteratorFactory(
        DefinitionId requirement,
        Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>> builtinDispatch) {
      dispatch = new MethodDispatchNode(requirement, builtinDispatch);
    }

    @Override
    Object create(VirtualFrame frame, Object iterable, Node location) {
      return dispatch.execute(frame, iterable, new Object[0], new Object[0], location);
    }
  }

  abstract static class IteratorCursorNode extends Node {
    abstract boolean hasNext(VirtualFrame frame, Object iterator, Node location);

    abstract Object next(VirtualFrame frame, Object iterator, Node location);
  }

  static final class BuiltinIteratorCursor extends IteratorCursorNode {
    @Override
    boolean hasNext(VirtualFrame frame, Object iterator, Node location) {
      return (Boolean)
          IntrinsicDispatcher.execute(
              IntrinsicId.ITERATOR_HAS_NEXT,
              iterator,
              new Object[0],
              null,
              ExecutionContextAccess.get(frame),
              location);
    }

    @Override
    Object next(VirtualFrame frame, Object iterator, Node location) {
      return IntrinsicDispatcher.execute(
          IntrinsicId.ITERATOR_NEXT,
          iterator,
          new Object[0],
          null,
          ExecutionContextAccess.get(frame),
          location);
    }
  }

  static final class InterfaceIteratorCursor extends IteratorCursorNode {
    @Child private MethodDispatchNode hasNext;
    @Child private MethodDispatchNode next;

    InterfaceIteratorCursor(
        DefinitionId hasNextRequirement,
        DefinitionId nextRequirement,
        Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>> builtinDispatch) {
      hasNext = new MethodDispatchNode(hasNextRequirement, builtinDispatch);
      next = new MethodDispatchNode(nextRequirement, builtinDispatch);
    }

    @Override
    boolean hasNext(VirtualFrame frame, Object iterator, Node location) {
      return (Boolean) hasNext.execute(frame, iterator, new Object[0], new Object[0], location);
    }

    @Override
    Object next(VirtualFrame frame, Object iterator, Node location) {
      return next.execute(frame, iterator, new Object[0], new Object[0], location);
    }
  }

  static final class ConditionalFor extends StatementNode {
    @Child private LoopNode loop;

    ConditionalFor(ExpressionNode condition, StatementNode body) {
      loop = Truffle.getRuntime().createLoopNode(new ConditionalRepeating(condition, body));
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      if (ExecutionContextAccess.get(frame).cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", this);
      }
      loop.execute(frame);
    }
  }

  private static final class ConditionalRepeating extends Node implements RepeatingNode {
    @Child private ExpressionNode condition;
    @Child private StatementNode body;

    ConditionalRepeating(ExpressionNode condition, StatementNode body) {
      this.condition = condition;
      this.body = body;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
      if (ExecutionContextAccess.get(frame).cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", body);
      }
      if (!(Boolean) condition.execute(frame)) return false;
      try {
        body.executeVoid(frame);
      } catch (ControlFlow.Continue ignored) {
        return true;
      } catch (ControlFlow.Break ignored) {
        return false;
      }
      return true;
    }
  }

  private static final class Repeating extends Node implements RepeatingNode {
    private final FrameBinding iteratorBinding;
    private final FrameBinding variableBinding;
    private final java.util.Optional<FrameBinding> indexBinding;
    @Child private StatementNode body;
    @Child private IteratorCursorNode iteratorCursor;

    Repeating(
        FrameBinding iteratorBinding,
        FrameBinding variableBinding,
        java.util.Optional<FrameBinding> indexBinding,
        StatementNode body,
        IteratorCursorNode iteratorCursor) {
      this.iteratorBinding = iteratorBinding;
      this.variableBinding = variableBinding;
      this.indexBinding = java.util.Objects.requireNonNull(indexBinding, "indexBinding");
      this.body = body;
      this.iteratorCursor = iteratorCursor;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
      if (ExecutionContextAccess.get(frame).cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", body);
      }
      IterationState state = (IterationState) iteratorBinding.read(frame);
      if (!iteratorCursor.hasNext(frame, state.iterator, this)) return false;
      variableBinding.write(
          frame, RuntimeValues.copy(iteratorCursor.next(frame, state.iterator, this)));
      indexBinding.ifPresent(binding -> binding.write(frame, state.index));
      state.index++;
      try {
        body.executeVoid(frame);
      } catch (ControlFlow.Continue ignored) {
        return true;
      } catch (ControlFlow.Break ignored) {
        return false;
      }
      return true;
    }
  }

  private static final class IterationState {
    private final Object iterator;
    private int index;

    private IterationState(Object iterator) {
      this.iterator = iterator;
    }
  }

  static final class WriteField extends StatementNode {
    @Child private ExpressionNode receiver;
    @Child private ExpressionNode value;
    private final int field;

    WriteField(ExpressionNode receiver, int field, ExpressionNode value) {
      this.receiver = receiver;
      this.field = field;
      this.value = value;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      RuntimeValues.ObjectValue object = (RuntimeValues.ObjectValue) receiver.execute(frame);
      object.fields[field] = RuntimeValues.copy(value.execute(frame));
    }
  }

  static final class WriteReference extends StatementNode {
    @Child private ExpressionNode reference;
    @Child private ExpressionNode value;

    WriteReference(ExpressionNode reference, ExpressionNode value) {
      this.reference = reference;
      this.value = value;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      RuntimeValues.ReferenceValue target = (RuntimeValues.ReferenceValue) reference.execute(frame);
      target.write(RuntimeValues.copy(value.execute(frame)));
    }
  }

  static final class IntrinsicWrite extends StatementNode {
    private final IntrinsicId intrinsic;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;

    IntrinsicWrite(IntrinsicId intrinsic, ExpressionNode receiver, ExpressionNode... arguments) {
      this.intrinsic = intrinsic;
      this.receiver = receiver;
      this.arguments = arguments;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      Object target = receiver.execute(frame);
      Object[] values = new Object[arguments.length];
      for (int index = 0; index < arguments.length; index++) {
        values[index] = arguments[index].execute(frame);
      }
      IntrinsicDispatcher.execute(
          intrinsic, target, values, null, ExecutionContextAccess.get(frame), this);
    }
  }
}
