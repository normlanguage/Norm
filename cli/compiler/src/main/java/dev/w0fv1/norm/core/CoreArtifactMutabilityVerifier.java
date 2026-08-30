package dev.w0fv1.norm.core;

final class CoreArtifactMutabilityVerifier {
  private CoreArtifactMutabilityVerifier() {}

  static void verify(CoreProgram program, CoreAuthoringMap authoring) {
    for (CoreDefinitionOccurrence occurrence : authoring.occurrences()) {
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      if (definition instanceof CoreDefinition.Callable callable) {
        verifyBlock(program, occurrence, callable.body());
      }
    }
  }

  private static void verifyBlock(
      CoreProgram program, CoreDefinitionOccurrence occurrence, CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      switch (statement) {
        case CoreStatement.FieldAssignment assignment ->
            verifyFieldAssignment(program, occurrence, assignment);
        case CoreStatement.IfStatement conditional -> {
          verifyBlock(program, occurrence, conditional.thenBlock());
          verifyBlock(program, occurrence, conditional.elseBlock());
        }
        case CoreStatement.ConditionalForStatement loop ->
            verifyBlock(program, occurrence, loop.body());
        case CoreStatement.ForStatement loop -> verifyBlock(program, occurrence, loop.body());
        case CoreStatement.TryStatement tried -> {
          verifyBlock(program, occurrence, tried.body());
          tried.catches().forEach(value -> verifyBlock(program, occurrence, value.body()));
          tried.finallyBlock().ifPresent(value -> verifyBlock(program, occurrence, value));
        }
        case CoreStatement.LocalDeclaration ignored -> {}
        case CoreStatement.LocalAssignment ignored -> {}
        case CoreStatement.IntrinsicAssignment ignored -> {}
        case CoreStatement.ReferenceAssignment ignored -> {}
        case CoreStatement.ExpressionStatement ignored -> {}
        case CoreStatement.ThrowStatement ignored -> {}
        case CoreStatement.ReturnStatement ignored -> {}
        case CoreStatement.YieldStatement ignored -> {}
        case CoreStatement.BreakStatement ignored -> {}
        case CoreStatement.ContinueStatement ignored -> {}
      }
    }
  }

  private static void verifyFieldAssignment(
      CoreProgram program,
      CoreDefinitionOccurrence occurrence,
      CoreStatement.FieldAssignment assignment) {
    CoreType receiver =
        CoreTypes.absolute(assignment.receiver().type(), occurrence.id().representative(), program);
    if (!(receiver instanceof CoreType.Declared declared)
        || declared.category() != CoreValueCategory.VALUE) {
      return;
    }
    if (occurrence.role() != CoreDefinitionRole.CONSTRUCTOR
        || !(assignment.receiver() instanceof CoreExpression.LocalRead local)
        || local.localIndex() != 0) {
      throw new IllegalArgumentException("value field mutation is invalid");
    }
  }
}
