package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.AstNode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.Sha256Digest;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class SyntaxAuthoringIndex {
  private static final ClassValue<RecordComponent[]> COMPONENTS =
      new ClassValue<>() {
        @Override
        protected RecordComponent[] computeValue(Class<?> type) {
          if (!type.isRecord())
            throw new IllegalArgumentException("Authored syntax must be a record: " + type);
          return type.getRecordComponents();
        }
      };

  private final Map<AstNode, String> locations = new IdentityHashMap<>();
  private final SemanticModel semantics;

  SyntaxAuthoringIndex(List<Syntax.Program> programs, SemanticModel semantics) {
    this.semantics = Objects.requireNonNull(semantics);
    programs.forEach(program -> index(program, ""));
  }

  String location(AstNode node) {
    return Objects.requireNonNull(locations.get(node), "Authored syntax location");
  }

  private void index(AstNode node, String path) {
    if (node instanceof Syntax.Lambda) {
      var previous = locations.putIfAbsent(node, path);
      if (previous != null && !previous.equals(path))
        throw new IllegalArgumentException("Ambiguous authored lambda site: " + node.span());
    }
    for (var component : COMPONENTS.get(node.getClass())) {
      var children = new ArrayList<AstNode>();
      children(read(component, node), children);
      var groups = new LinkedHashMap<String, List<AstNode>>();
      var roles = new ArrayList<String>();
      for (int position = 0; position < children.size(); position++) {
        String role = role(node, component.getName(), children.get(position), position);
        roles.add(role);
        groups.computeIfAbsent(role, ignored -> new ArrayList<>()).add(children.get(position));
      }
      var ordinals = new LinkedHashMap<String, Integer>();
      var identities = new LinkedHashMap<String, String>();
      groups.forEach(
          (role, group) -> identities.put(role, group.size() > 1 ? fingerprint(group) : "unique"));
      for (int position = 0; position < children.size(); position++) {
        String role = roles.get(position);
        String groupIdentity = identities.get(role);
        int ordinal = ordinals.merge(role, 1, Integer::sum) - 1;
        index(
            children.get(position),
            path
                + "/"
                + segment(component.getName())
                + segment(role)
                + segment(groupIdentity)
                + ":"
                + ordinal);
      }
    }
  }

  private String role(AstNode parent, String property, AstNode node, int position) {
    if (parent instanceof Syntax.Call call
        && property.equals("arguments")
        && node instanceof Syntax.CallArgument argument) {
      int parameter =
          semantics
              .callOf(call.span())
              .map(resolved -> resolved.arguments().parameterIndices().get(position))
              .orElse(position);
      return "argument:"
          + argument.label().map(Syntax.ArgumentLabel::name).orElse(Integer.toString(parameter));
    }
    return switch (node) {
      case Syntax.VariableDecl value -> "local:" + value.name();
      case Syntax.FieldDecl value -> "field:" + value.name();
      case Syntax.Parameter value -> "parameter:" + value.name();
      case Syntax.LambdaParameter value -> "lambda-parameter:" + value.name();
      case Syntax.FunctionDecl value -> "function:" + DeclarationIdentity.functionSignature(value);
      case Syntax.AggregateDecl value -> "aggregate:" + value.name();
      case Syntax.InterfaceDecl value -> "interface:" + value.name();
      case Syntax.EnumDecl value -> "enum:" + value.name();
      case Syntax.ExpressionStatement value when value.expression() instanceof Syntax.Call call ->
          "call-statement:" + callee(call.callee());
      case Syntax.Call value -> "call:" + callee(value.callee());
      default -> node.getClass().getSimpleName();
    };
  }

  private static String callee(Syntax.Expression expression) {
    return switch (expression) {
      case Syntax.Name name -> name.value();
      case Syntax.Member member -> callee(member.receiver()) + "." + member.name();
      default -> expression.getClass().getSimpleName();
    };
  }

  private static void children(Object value, List<AstNode> result) {
    if (value instanceof AstNode node) result.add(node);
    else if (value instanceof Optional<?> optional)
      optional.ifPresent(item -> children(item, result));
    else if (value instanceof List<?> list) list.forEach(item -> children(item, result));
  }

  private static Object read(RecordComponent component, AstNode node) {
    try {
      return component.getAccessor().invoke(node);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(
          "Cannot read authored syntax component " + component.getName(), failure);
    }
  }

  private static String fingerprint(List<AstNode> nodes) {
    var canonical = new StringBuilder();
    canonical(nodes, canonical);
    return Sha256Digest.compute(canonical.toString().getBytes(StandardCharsets.UTF_8)).value();
  }

  private static void canonical(Object value, StringBuilder output) {
    if (value instanceof SourceSpan) return;
    if (value == null) {
      output.append("null;");
    } else if (value instanceof AstNode node) {
      output.append(segment(node.getClass().getName()));
      for (var component : COMPONENTS.get(node.getClass())) {
        if (component.getName().equals("span") || component.getType() == SourceSpan.class) continue;
        output.append(segment(component.getName()));
        canonical(read(component, node), output);
      }
    } else if (value instanceof Syntax.ForIndex index) {
      output.append(segment(Syntax.ForIndex.class.getName())).append(segment(index.name()));
    } else if (value instanceof Syntax.TypeParameter parameter) {
      output
          .append(segment(Syntax.TypeParameter.class.getName()))
          .append(segment(parameter.name()));
      canonical(parameter.upperBound(), output);
      canonical(parameter.defaultType(), output);
    } else if (value instanceof Optional<?> optional) {
      output.append(optional.isPresent() ? "present;" : "empty;");
      optional.ifPresent(item -> canonical(item, output));
    } else if (value instanceof List<?> list) {
      output.append("list:").append(list.size()).append(';');
      list.forEach(item -> canonical(item, output));
    } else if (value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Enum<?>) {
      output.append(segment(value.getClass().getName())).append(segment(value.toString()));
    } else {
      throw new IllegalArgumentException("Unsupported authored syntax value: " + value.getClass());
    }
  }

  private static String segment(String value) {
    return value.length() + ":" + value;
  }
}
