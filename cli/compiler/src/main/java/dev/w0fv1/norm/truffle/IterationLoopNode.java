package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.Optional;

final class IterationLoopNode<C> extends Node {
  private final FrameBinding iteratorBinding;
  private final Optional<FrameBinding> indexBinding;
  @Child private ExpressionNode iterable;
  @Child private StatementNodes.IteratorFactoryNode factory;
  @Child private LoopNode loop;

  IterationLoopNode(
      FrameBinding iteratorBinding,
      FrameBinding variableBinding,
      Optional<FrameBinding> indexBinding,
      ExpressionNode iterable,
      Body<C> body,
      StatementNodes.IteratorFactoryNode factory,
      StatementNodes.IteratorCursorNode cursor) {
    this.iteratorBinding = iteratorBinding;
    this.indexBinding = indexBinding;
    this.iterable = iterable;
    this.factory = factory;
    loop =
        Truffle.getRuntime()
            .createLoopNode(
                new Repeating<>(iteratorBinding, variableBinding, indexBinding, body, cursor));
  }

  void execute(VirtualFrame frame, C context) {
    if (ExecutionContextAccess.get(frame).cancellation().getAsBoolean()) {
      throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", this);
    }
    iteratorBinding.write(
        frame, new State<>(factory.create(frame, iterable.execute(frame), this), context));
    try {
      loop.execute(frame);
    } finally {
      frame.clear(iteratorBinding.slot());
      indexBinding.ifPresent(binding -> frame.clear(binding.slot()));
    }
  }

  abstract static class Body<C> extends Node {
    abstract void execute(VirtualFrame frame, C context);
  }

  static final class StatementBody extends Body<Void> {
    @Child private StatementNode statement;

    StatementBody(StatementNode statement) {
      this.statement = statement;
    }

    @Override
    void execute(VirtualFrame frame, Void context) {
      statement.executeVoid(frame);
    }
  }

  private static final class Repeating<C> extends Node implements RepeatingNode {
    private final FrameBinding iteratorBinding;
    private final FrameBinding variableBinding;
    private final Optional<FrameBinding> indexBinding;
    @Child private Body<C> body;
    @Child private StatementNodes.IteratorCursorNode cursor;

    Repeating(
        FrameBinding iteratorBinding,
        FrameBinding variableBinding,
        Optional<FrameBinding> indexBinding,
        Body<C> body,
        StatementNodes.IteratorCursorNode cursor) {
      this.iteratorBinding = iteratorBinding;
      this.variableBinding = variableBinding;
      this.indexBinding = indexBinding;
      this.body = body;
      this.cursor = cursor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean executeRepeating(VirtualFrame frame) {
      if (ExecutionContextAccess.get(frame).cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", body);
      }
      State<C> state = (State<C>) iteratorBinding.read(frame);
      if (!cursor.hasNext(frame, state.iterator, this)) return false;
      variableBinding.write(frame, RuntimeValues.copy(cursor.next(frame, state.iterator, this)));
      indexBinding.ifPresent(binding -> binding.write(frame, state.index));
      state.index++;
      try {
        body.execute(frame, state.context);
      } catch (ControlFlow.Continue ignored) {
        return true;
      } catch (ControlFlow.Break ignored) {
        return false;
      }
      return true;
    }
  }

  private static final class State<C> {
    private final Object iterator;
    private final C context;
    private int index;

    State(Object iterator, C context) {
      this.iterator = iterator;
      this.context = context;
    }
  }
}
