package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.Objects;
import java.util.Optional;

public sealed interface CoreStatement extends CoreNode
    permits CoreStatement.LocalDeclaration,
        CoreStatement.LocalAssignment,
        CoreStatement.FieldAssignment,
        CoreStatement.IntrinsicAssignment,
        CoreStatement.ExpressionStatement,
        CoreStatement.IfStatement,
        CoreStatement.ConditionalForStatement,
        CoreStatement.ForStatement,
        CoreStatement.ReturnStatement,
        CoreStatement.BreakStatement,
        CoreStatement.ContinueStatement {
  record LocalDeclaration(int nodeIndex, int localIndex, CoreExpression initializer)
      implements CoreStatement {
    public LocalDeclaration {
      requireNode(nodeIndex);
      requireLocal(localIndex);
      Objects.requireNonNull(initializer, "initializer");
    }
  }

  record LocalAssignment(int nodeIndex, int localIndex, CoreExpression value)
      implements CoreStatement {
    public LocalAssignment {
      requireNode(nodeIndex);
      requireLocal(localIndex);
      Objects.requireNonNull(value, "value");
    }
  }

  record FieldAssignment(
      int nodeIndex, CoreExpression receiver, CoreFieldReference field, CoreExpression value)
      implements CoreStatement {
    public FieldAssignment {
      requireNode(nodeIndex);
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(value, "value");
    }
  }

  record IntrinsicAssignment(
      int nodeIndex,
      IntrinsicId intrinsic,
      CoreExpression receiver,
      Optional<CoreExpression> index,
      CoreExpression value)
      implements CoreStatement {
    public IntrinsicAssignment {
      requireNode(nodeIndex);
      Objects.requireNonNull(intrinsic, "intrinsic");
      Objects.requireNonNull(receiver, "receiver");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(value, "value");
    }
  }

  record ExpressionStatement(int nodeIndex, CoreExpression expression) implements CoreStatement {
    public ExpressionStatement {
      requireNode(nodeIndex);
      Objects.requireNonNull(expression, "expression");
    }
  }

  record IfStatement(
      int nodeIndex, CoreExpression condition, CoreBlock thenBlock, CoreBlock elseBlock)
      implements CoreStatement {
    public IfStatement {
      requireNode(nodeIndex);
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(thenBlock, "thenBlock");
      Objects.requireNonNull(elseBlock, "elseBlock");
    }
  }

  record ConditionalForStatement(int nodeIndex, CoreExpression condition, CoreBlock body)
      implements CoreStatement {
    public ConditionalForStatement {
      requireNode(nodeIndex);
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(body, "body");
    }
  }

  record ForStatement(
      int nodeIndex,
      int iteratorLocal,
      int variableLocal,
      CoreExpression iterable,
      CoreBlock body,
      IntrinsicId iterationIntrinsic)
      implements CoreStatement {
    public ForStatement {
      requireNode(nodeIndex);
      requireLocal(iteratorLocal);
      requireLocal(variableLocal);
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(iterationIntrinsic, "iterationIntrinsic");
    }
  }

  record ReturnStatement(int nodeIndex, Optional<CoreExpression> value) implements CoreStatement {
    public ReturnStatement {
      requireNode(nodeIndex);
      value = Objects.requireNonNull(value, "value");
    }
  }

  record BreakStatement(int nodeIndex) implements CoreStatement {
    public BreakStatement {
      requireNode(nodeIndex);
    }
  }

  record ContinueStatement(int nodeIndex) implements CoreStatement {
    public ContinueStatement {
      requireNode(nodeIndex);
    }
  }

  private static void requireNode(int index) {
    if (index < 0) throw new IllegalArgumentException("node index must not be negative");
  }

  private static void requireLocal(int index) {
    if (index < 0) throw new IllegalArgumentException("local index must not be negative");
  }
}
