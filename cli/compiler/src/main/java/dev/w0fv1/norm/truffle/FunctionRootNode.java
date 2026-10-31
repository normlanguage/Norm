package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;

final class FunctionRootNode extends RootNode implements RuntimeLocation {
  private final String name;
  private final String packageName;
  private final FrameBinding[] parameters;
  private final SourceSection sourceSection;
  private final DefinitionOccurrenceId occurrence;
  private final java.util.Optional<dev.w0fv1.norm.core.CoreConstructorParameters> constructor;
  @Child private StatementNode body;

  FunctionRootNode(
      com.oracle.truffle.api.TruffleLanguage<?> language,
      String name,
      String packageName,
      DefinitionOccurrenceId occurrence,
      FrameDescriptor frameDescriptor,
      FrameBinding[] parameters,
      java.util.Optional<dev.w0fv1.norm.core.CoreConstructorParameters> constructor,
      SourceSection sourceSection) {
    super(language, frameDescriptor);
    this.name = name;
    this.packageName = packageName;
    this.occurrence = occurrence;
    this.parameters = parameters;
    this.constructor = constructor;
    this.sourceSection = sourceSection;
  }

  String packageName() {
    return packageName;
  }

  void initialize(StatementNode body) {
    if (this.body != null) throw new IllegalStateException("function is already initialized");
    this.body = insert(body);
  }

  @Override
  public Object execute(VirtualFrame frame) {
    try (var valueScope = RuntimeValueExecution.enter(ExecutionContextAccess.state(frame))) {
      Object[] arguments = frame.getArguments();
      if (constructor.isPresent())
        ConstructionArguments.from(frame).bind(frame, parameters, constructor.orElseThrow());
      else
        for (int index = 0; index < parameters.length; index++)
          parameters[index].write(frame, arguments[index + 1]);
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      if (bindings != null) bindings.enter(frame);
      try {
        body.executeVoid(frame);
        return 0L;
      } catch (ControlFlow.Return returned) {
        return RuntimeValues.copy(returned.value);
      } finally {
        if (bindings != null) bindings.leave(frame);
      }
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
