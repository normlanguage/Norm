package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceSpan;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@SuppressWarnings("try")
final class BodyAnalysisStateTest {
  @Test
  void rollbackPreservesTheControlFrameOwnedByTheEnclosingSwitch() {
    var state = new BodyAnalysisState();
    var control = BodyAnalysisState.ControlContext.switchExpression(SemanticType.INTEGER);
    try (var frame = state.enterControl(control)) {
      var checkpoint = state.checkpoint();
      control.setResultType(SemanticType.STRING);
      state.restore(checkpoint);
      assertSame(control, state.currentControl());
      assertEquals(SemanticType.INTEGER, control.resultType());
    }
    assertNull(state.currentControl());
  }

  @Test
  void rollbackPreservesLiveLambdaAndFlowScopeHandles() {
    var state = new BodyAnalysisState();
    var source = SourceFile.of(Path.of("scopes.norm"), "Void main() {}");
    var span = SourceSpan.at(source, 0);
    var local = SymbolId.source(source.id(), 10);
    try (var callable = state.enterCallable(local, SemanticType.VOID, false, null);
        var root = state.scopes().enter(span)) {
      assertTrue(state.scopes().declare("value", SemanticType.INTEGER, local));
      try (var lambda = state.enterLambda(span, SemanticType.STRING)) {
        var checkpoint = state.checkpoint();
        state.declareLambdaLocal(local);
        state.recordAssignment(local);
        state.scopes().update(state.scopes().find("value"), SemanticType.BOOLEAN);
        state.restore(checkpoint);
        assertTrue(state.externalToLambda(local));
        assertFalse(state.assigned(local));
        assertEquals(SemanticType.INTEGER, state.scopes().type(state.scopes().find("value")));
      }
      assertFalse(state.inLambda());
      assertEquals(SemanticType.VOID, state.expectedReturnType());
    }
    assertNull(state.currentCallable());
    assertEquals(2, state.scopes().semanticScopes().size());
  }
}
