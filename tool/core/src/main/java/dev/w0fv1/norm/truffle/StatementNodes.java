package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.Iterator;

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

  static final class Continue extends StatementNode {
    @Override
    void executeVoid(VirtualFrame frame) {
      throw ControlFlow.Continue.INSTANCE;
    }
  }

  static final class For extends StatementNode {
    private final FrameBinding iteratorBinding;
    @Child private ExpressionNode iterable;
    @Child private LoopNode loop;
    private final IntrinsicId iteratorIntrinsic;
    private final ExecutionContext context;

    For(
        FrameBinding iteratorBinding,
        FrameBinding variableBinding,
        ExpressionNode iterable,
        StatementNode body,
        IntrinsicId iteratorIntrinsic,
        ExecutionContext context) {
      this.iteratorBinding = iteratorBinding;
      this.iterable = iterable;
      this.iteratorIntrinsic = iteratorIntrinsic;
      this.context = context;
      loop =
          Truffle.getRuntime()
              .createLoopNode(new Repeating(iteratorBinding, variableBinding, body, context));
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      if (context.cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", this);
      }
      iteratorBinding.write(
          frame,
          IntrinsicDispatcher.execute(
              iteratorIntrinsic, iterable.execute(frame), new Object[0], null, context, this));
      loop.execute(frame);
      frame.clear(iteratorBinding.slot());
    }
  }

  private static final class Repeating extends Node implements RepeatingNode {
    private final FrameBinding iteratorBinding;
    private final FrameBinding variableBinding;
    @Child private StatementNode body;
    private final ExecutionContext context;

    Repeating(
        FrameBinding iteratorBinding,
        FrameBinding variableBinding,
        StatementNode body,
        ExecutionContext context) {
      this.iteratorBinding = iteratorBinding;
      this.variableBinding = variableBinding;
      this.body = body;
      this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean executeRepeating(VirtualFrame frame) {
      if (context.cancellation().getAsBoolean()) {
        throw new NormGuestException(RuntimeErrorCode.CANCELLED, "execution cancelled", body);
      }
      Iterator<Object> iterator = (Iterator<Object>) iteratorBinding.read(frame);
      if (!iterator.hasNext()) return false;
      variableBinding.write(frame, RuntimeValues.copy(iterator.next()));
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

  static final class IntrinsicWrite extends StatementNode {
    private final IntrinsicId intrinsic;
    private final ExecutionContext context;
    @Child private ExpressionNode receiver;
    @Children private final ExpressionNode[] arguments;

    IntrinsicWrite(
        IntrinsicId intrinsic,
        ExpressionNode receiver,
        ExecutionContext context,
        ExpressionNode... arguments) {
      this.intrinsic = intrinsic;
      this.receiver = receiver;
      this.context = context;
      this.arguments = arguments;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      Object target = receiver.execute(frame);
      Object[] values = new Object[arguments.length];
      for (int index = 0; index < arguments.length; index++) {
        values[index] = arguments[index].execute(frame);
      }
      IntrinsicDispatcher.execute(intrinsic, target, values, null, context, this);
    }
  }
}
