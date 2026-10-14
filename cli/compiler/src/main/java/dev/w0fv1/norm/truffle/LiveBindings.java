package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.LinkedHashMap;
import java.util.Map;

final class LiveBindings {
  record Key(String declaration, int ordinal) {}

  record Version(Map<Key, StatementNode> statements, Map<Key, ExpressionNode> expressions) {
    Version {
      statements = Map.copyOf(statements);
      expressions = Map.copyOf(expressions);
    }
  }

  static final class Builder {
    private final Map<Key, StatementNode> statements = new LinkedHashMap<>();
    private final Map<Key, ExpressionNode> expressions = new LinkedHashMap<>();

    StatementNode statement(Key key, StatementNode node) {
      if (statements.put(key, node) != null)
        throw new IllegalStateException("duplicate statement binding");
      return new StatementBinding(key, node);
    }

    ExpressionNode expression(Key key, ExpressionNode node) {
      if (expressions.put(key, node) != null)
        throw new IllegalStateException("duplicate expression binding");
      return new ExpressionBinding(key, node);
    }

    Version build() {
      return new Version(statements, expressions);
    }
  }

  private volatile Version version;
  private final ThreadLocal<Version> expressionVersion = new ThreadLocal<>();

  LiveBindings(Version version) {
    this.version = version;
  }

  void publish(Version candidate) {
    if (!candidate.statements().keySet().containsAll(version.statements().keySet())
        || !candidate.expressions().keySet().containsAll(version.expressions().keySet())) {
      throw new IllegalArgumentException("live execution node bindings changed");
    }
    version = candidate;
  }

  private static final class StatementBinding extends StatementNode {
    private final Key key;
    @Child private StatementNode initial;

    StatementBinding(Key key, StatementNode initial) {
      this.key = key;
      this.initial = initial;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      if (bindings == null) {
        initial.executeVoid(frame);
        return;
      }
      Version previous = bindings.expressionVersion.get();
      bindings.expressionVersion.remove();
      try {
        bindings.version.statements().get(key).executeVoid(frame);
      } finally {
        if (previous == null) bindings.expressionVersion.remove();
        else bindings.expressionVersion.set(previous);
      }
    }
  }

  private static final class ExpressionBinding extends ExpressionNode {
    private final Key key;
    @Child private ExpressionNode initial;

    ExpressionBinding(Key key, ExpressionNode initial) {
      this.key = key;
      this.initial = initial;
    }

    @Override
    Object execute(VirtualFrame frame) {
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      if (bindings == null) return initial.execute(frame);
      Version previous = bindings.expressionVersion.get();
      Version selected = previous == null ? bindings.version : previous;
      if (previous == null) bindings.expressionVersion.set(selected);
      try {
        return selected.expressions().get(key).execute(frame);
      } finally {
        if (previous == null) bindings.expressionVersion.remove();
      }
    }
  }
}
