package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.semantic.SemanticType;
import org.junit.jupiter.api.Test;

final class BodyAnalysisStateTest {
  @Test
  void rollbackPreservesTheControlFrameOwnedByTheEnclosingSwitch() {
    var state = new BodyAnalysisState();
    var control = BodyAnalysisState.ControlContext.switchExpression(SemanticType.INTEGER);
    state.controls.addFirst(control);
    var checkpoint = state.checkpoint();
    control.setResultType(SemanticType.STRING);
    state.restore(checkpoint);
    assertSame(control, state.controls.getFirst());
    assertEquals(SemanticType.INTEGER, control.resultType());
  }
}
