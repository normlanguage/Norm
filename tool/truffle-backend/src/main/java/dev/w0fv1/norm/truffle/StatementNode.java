package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;

abstract class StatementNode extends Node implements RuntimeLocation {
  private SourceSection sourceSection;
  private DefinitionOccurrenceId occurrence;
  private int nodeIndex;

  abstract void executeVoid(VirtualFrame frame);

  final StatementNode at(SourceSection section, DefinitionOccurrenceId occurrence, int nodeIndex) {
    sourceSection = section;
    this.occurrence = occurrence;
    this.nodeIndex = nodeIndex;
    return this;
  }

  @Override
  public final DefinitionOccurrenceId occurrence() {
    return occurrence;
  }

  @Override
  public final int nodeIndex() {
    return nodeIndex;
  }

  @Override
  public final SourceSection getSourceSection() {
    return sourceSection;
  }
}
