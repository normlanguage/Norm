package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import java.util.List;

abstract class CollectionElementNode extends Node {
  abstract void collect(VirtualFrame frame, List<Object> values);

  static final class Value extends CollectionElementNode {
    @Child private ExpressionNode expression;

    Value(ExpressionNode expression) {
      this.expression = expression;
    }

    @Override
    void collect(VirtualFrame frame, List<Object> values) {
      values.add(RuntimeValues.copy(expression.execute(frame)));
    }
  }

  static final class Conditional extends CollectionElementNode {
    @Child private ExpressionNode condition;
    @Child private CollectionElementNode thenElement;
    @Child private CollectionElementNode elseElement;

    Conditional(
        ExpressionNode condition,
        CollectionElementNode thenElement,
        CollectionElementNode elseElement) {
      this.condition = condition;
      this.thenElement = thenElement;
      this.elseElement = elseElement;
    }

    @Override
    void collect(VirtualFrame frame, List<Object> values) {
      if ((Boolean) condition.execute(frame)) thenElement.collect(frame, values);
      else if (elseElement != null) elseElement.collect(frame, values);
    }
  }

  static final class Repeated extends CollectionElementNode {
    @Child private IterationLoopNode<List<Object>> loop;

    Repeated(IterationLoopNode<List<Object>> loop) {
      this.loop = loop;
    }

    @Override
    void collect(VirtualFrame frame, List<Object> values) {
      loop.execute(frame, values);
    }
  }

  static final class Body extends IterationLoopNode.Body<List<Object>> {
    @Child private CollectionElementNode element;

    Body(CollectionElementNode element) {
      this.element = element;
    }

    @Override
    void execute(VirtualFrame frame, List<Object> values) {
      element.collect(frame, values);
    }
  }
}
