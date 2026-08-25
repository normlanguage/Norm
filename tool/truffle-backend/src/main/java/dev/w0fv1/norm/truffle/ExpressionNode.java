package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

abstract class ExpressionNode extends Node {
  private SourceSection sourceSection;

  abstract Object execute(VirtualFrame frame);

  final ExpressionNode at(SourceSection section) {
    sourceSection = section;
    return this;
  }

  @Override
  public final SourceSection getSourceSection() {
    return sourceSection;
  }
}
