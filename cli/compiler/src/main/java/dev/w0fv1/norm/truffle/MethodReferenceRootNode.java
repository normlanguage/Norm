package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import java.util.Arrays;
import java.util.Map;

final class MethodReferenceRootNode extends RootNode implements RuntimeLocation {
  private final DefinitionOccurrenceId occurrence;
  private final int receiverTypeArgumentCount;
  private final int methodTypeArgumentCount;
  @Child private MethodDispatchNode dispatch;

  MethodReferenceRootNode(
      DefinitionId slot,
      DefinitionOccurrenceId occurrence,
      int receiverTypeArgumentCount,
      int methodTypeArgumentCount) {
    super(null);
    this.occurrence = occurrence;
    this.receiverTypeArgumentCount = receiverTypeArgumentCount;
    this.methodTypeArgumentCount = methodTypeArgumentCount;
    dispatch = new MethodDispatchNode(slot, Map.of());
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object[] values = frame.getArguments();
    int methodTypes = values.length - methodTypeArgumentCount;
    return dispatch.execute(
        frame,
        values[1],
        Arrays.copyOfRange(values, 2, methodTypes - receiverTypeArgumentCount),
        Arrays.copyOfRange(values, methodTypes, values.length),
        this);
  }

  @Override
  public DefinitionOccurrenceId occurrence() {
    return occurrence;
  }

  @Override
  public int nodeIndex() {
    return 0;
  }
}
