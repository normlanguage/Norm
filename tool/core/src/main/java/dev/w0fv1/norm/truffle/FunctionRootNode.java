package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

final class FunctionRootNode extends RootNode {
  private final String name;
  private final FrameBinding[] parameters;
  private final SourceSection sourceSection;
  @Child private StatementNode body;

  FunctionRootNode(
      Language language,
      String name,
      FrameDescriptor frameDescriptor,
      FrameBinding[] parameters,
      SourceSection sourceSection) {
    super(language, frameDescriptor);
    this.name = name;
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
      parameters[index].write(frame, arguments[index]);
    }
    try {
      body.executeVoid(frame);
      return name.equals("main") ? 0L : null;
    } catch (ControlFlow.Return returned) {
      return RuntimeValues.copy(returned.value);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }
}
