package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.Objects;
import java.util.Optional;

public sealed interface BoundStatement extends BoundNode
    permits BoundStatement.LocalDeclaration,
        BoundStatement.LocalAssignment,
        BoundStatement.FieldAssignment,
        BoundStatement.IntrinsicAssignment,
        BoundStatement.ExpressionStatement,
        BoundStatement.IfStatement,
        BoundStatement.ConditionalForStatement,
        BoundStatement.ForStatement,
        BoundStatement.ReturnStatement,
        BoundStatement.BreakStatement,
        BoundStatement.ContinueStatement {
  record LocalDeclaration(
      BoundLocalId local,
      String name,
      SemanticType type,
      BoundExpression initializer,
      BoundValueTransfer transfer,
      SourceSpan span)
      implements BoundStatement {
    public LocalDeclaration {
      Objects.requireNonNull(local, "local");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(initializer, "initializer");
      Objects.requireNonNull(transfer, "transfer");
      Objects.requireNonNull(span, "span");
    }
  }

  record LocalAssignment(
      BoundLocalId local, BoundExpression value, BoundValueTransfer transfer, SourceSpan span)
      implements BoundStatement {
    public LocalAssignment {
      Objects.requireNonNull(local, "local");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(transfer, "transfer");
      Objects.requireNonNull(span, "span");
    }
  }

  record FieldAssignment(
      BoundExpression receiver,
      BoundFieldId field,
      int ordinal,
      BoundExpression value,
      BoundValueTransfer transfer,
      SourceSpan span)
      implements BoundStatement {
    public FieldAssignment {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      if (ordinal < 0) throw new IllegalArgumentException("field ordinal must be non-negative");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(transfer, "transfer");
      Objects.requireNonNull(span, "span");
    }
  }

  record IntrinsicAssignment(
      IntrinsicId intrinsic,
      BoundExpression receiver,
      Optional<BoundExpression> index,
      BoundExpression value,
      BoundValueTransfer transfer,
      SourceSpan span)
      implements BoundStatement {
    public IntrinsicAssignment {
      Objects.requireNonNull(intrinsic, "intrinsic");
      Objects.requireNonNull(receiver, "receiver");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(transfer, "transfer");
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
      BoundExpression iterable,
      BoundBlock body,
      IntrinsicId iterationIntrinsic,
      BoundValueTransfer transfer,
      SourceSpan span)
      implements BoundStatement {
    public ForStatement {
      Objects.requireNonNull(iterator, "iterator");
      Objects.requireNonNull(variable, "variable");
      Objects.requireNonNull(variableName, "variableName");
      Objects.requireNonNull(variableType, "variableType");
      Objects.requireNonNull(iterable, "iterable");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(iterationIntrinsic, "iterationIntrinsic");
      Objects.requireNonNull(transfer, "transfer");
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

  record ReturnStatement(
      Optional<BoundExpression> value, BoundValueTransfer transfer, SourceSpan span)
      implements BoundStatement {
    public ReturnStatement {
      value = Objects.requireNonNull(value, "value");
      Objects.requireNonNull(transfer, "transfer");
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
