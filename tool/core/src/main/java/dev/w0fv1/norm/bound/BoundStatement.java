package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface BoundStatement extends BoundNode
    permits BoundStatement.LocalDeclaration,
        BoundStatement.LocalAssignment,
        BoundStatement.FieldAssignment,
        BoundStatement.IntrinsicAssignment,
        BoundStatement.ReferenceAssignment,
        BoundStatement.ExpressionStatement,
        BoundStatement.IfStatement,
        BoundStatement.ConditionalForStatement,
        BoundStatement.ForStatement,
        BoundStatement.TryStatement,
        BoundStatement.ThrowStatement,
        BoundStatement.ReturnStatement,
        BoundStatement.YieldStatement,
        BoundStatement.BreakStatement,
        BoundStatement.ContinueStatement {
  record LocalDeclaration(
      BoundLocalId local,
      String name,
      SemanticType type,
      BoundExpression initializer,
      SourceSpan span)
      implements BoundStatement {
    public LocalDeclaration {
      Objects.requireNonNull(local, "local");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(initializer, "initializer");
      Objects.requireNonNull(span, "span");
    }
  }

  record LocalAssignment(BoundLocalId local, BoundExpression value, SourceSpan span)
      implements BoundStatement {
    public LocalAssignment {
      Objects.requireNonNull(local, "local");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  record FieldAssignment(
      BoundExpression receiver,
      BoundFieldId field,
      int ordinal,
      BoundExpression value,
      SourceSpan span)
      implements BoundStatement {
    public FieldAssignment {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      if (ordinal < 0) throw new IllegalArgumentException("field ordinal must be non-negative");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  record IntrinsicAssignment(
      IntrinsicId intrinsic,
      BoundExpression receiver,
      Optional<BoundExpression> index,
      BoundExpression value,
      SourceSpan span)
      implements BoundStatement {
    public IntrinsicAssignment {
      Objects.requireNonNull(intrinsic, "intrinsic");
      Objects.requireNonNull(receiver, "receiver");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  record ReferenceAssignment(BoundExpression reference, BoundExpression value, SourceSpan span)
      implements BoundStatement {
    public ReferenceAssignment {
      Objects.requireNonNull(reference, "reference");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  record ExpressionStatement(BoundExpression expression, SourceSpan span)
      implements BoundStatement {
    public ExpressionStatement {
      Objects.requireNonNull(expression, "expression");
      Objects.requireNonNull(span, "span");
    }
  }

  record IfStatement(
      BoundExpression condition, BoundBlock thenBlock, BoundBlock elseBlock, SourceSpan span)
      implements BoundStatement {
    public IfStatement {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(thenBlock, "thenBlock");
      Objects.requireNonNull(elseBlock, "elseBlock");
      Objects.requireNonNull(span, "span");
    }
  }

  record ForStatement(
      BoundLocalId iterator,
      BoundLocalId variable,
      String variableName,
      SemanticType variableType,
      Optional<BoundLocalId> index,
      BoundExpression iterable,
      BoundBlock body,
      BoundIteration iteration,
      SourceSpan span)
      implements BoundStatement {
    public ForStatement {
      Objects.requireNonNull(iterator, "iterator");
      Objects.requireNonNull(variable, "variable");
      Objects.requireNonNull(variableName, "variableName");
      Objects.requireNonNull(variableType, "variableType");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(iteration, "iteration");
      Objects.requireNonNull(span, "span");
    }
  }

  record ConditionalForStatement(BoundExpression condition, BoundBlock body, SourceSpan span)
      implements BoundStatement {
    public ConditionalForStatement {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(span, "span");
    }
  }

  record TryStatement(
      BoundBlock body,
      List<BoundCatchClause> catches,
      Optional<BoundBlock> finallyBlock,
      SourceSpan span)
      implements BoundStatement {
    public TryStatement {
      Objects.requireNonNull(body, "body");
      catches = List.copyOf(catches);
      finallyBlock = Objects.requireNonNull(finallyBlock, "finallyBlock");
      Objects.requireNonNull(span, "span");
    }
  }

  record ThrowStatement(BoundExpression exception, SourceSpan span) implements BoundStatement {
    public ThrowStatement {
      Objects.requireNonNull(exception, "exception");
      Objects.requireNonNull(span, "span");
    }
  }

  record ReturnStatement(Optional<BoundExpression> value, SourceSpan span)
      implements BoundStatement {
    public ReturnStatement {
      value = Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  record YieldStatement(BoundExpression value, SourceSpan span) implements BoundStatement {
    public YieldStatement {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(span, "span");
    }
  }

  record BreakStatement(SourceSpan span) implements BoundStatement {
    public BreakStatement {
      Objects.requireNonNull(span, "span");
    }
  }

  record ContinueStatement(SourceSpan span) implements BoundStatement {
    public ContinueStatement {
      Objects.requireNonNull(span, "span");
    }
  }
}
