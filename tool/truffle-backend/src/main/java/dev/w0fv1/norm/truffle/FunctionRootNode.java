package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;

final class FunctionRootNode extends RootNode implements RuntimeLocation {
  private final String name;
  private final FrameBinding[] parameters;
  private final SourceSection sourceSection;
  private final DefinitionOccurrenceId occurrence;
  @Child private StatementNode body;

  FunctionRootNode(
      Language language,
      String name,
      DefinitionOccurrenceId occurrence,
      FrameDescriptor frameDescriptor,
      FrameBinding[] parameters,
      SourceSection sourceSection) {
    super(language, frameDescriptor);
    this.name = name;
    this.occurrence = occurrence;
    this.parameters = parameters;
    this.sourceSection = sourceSection;
  }

  void initialize(StatementNode body) {
    if (this.body != null) throw new IllegalStateException("function is already initialized");
    this.body = insert(body);
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    for (int index = 0; index < parameters.length; index++) {
      parameters[index].write(frame, arguments[index + 1]);
    }
    try {
      body.executeVoid(frame);
      return 0L;
    } catch (ControlFlow.Return returned) {
      return RuntimeValues.copy(returned.value);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public DefinitionOccurrenceId occurrence() {
    return occurrence;
  }

  @Override
  public int nodeIndex() {
    return 0;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }
}
