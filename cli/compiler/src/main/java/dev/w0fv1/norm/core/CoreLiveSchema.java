package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CoreLiveSchema {
  private final Map<DefinitionOccurrenceId, String> keys;
  private final Map<String, Contract> contracts;
  private final Set<DefinitionId> structures;
  private final MetadataId metadata;
  private final String entry;

  private CoreLiveSchema(CoreArtifact artifact) {
    var indexedKeys = new HashMap<DefinitionOccurrenceId, String>();
    var indexedContracts = new HashMap<String, Contract>();
    var indexedStructures = new HashSet<DefinitionId>();
    var indexedBindings =
        artifact.namespace().bindings().stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    CoreBinding::occurrence,
                    java.util.stream.Collectors.mapping(
                        CoreNamespace::canonicalBinding, java.util.stream.Collectors.toList())));
    for (var occurrence : artifact.authoring().occurrences()) {
      var owner = occurrence.id().representative();
      var definition = artifact.program().definition(owner).orElseThrow();
      if (!(definition instanceof CoreDefinition.Callable callable)) {
        indexedStructures.addAll(occurrence.representedDefinitions());
        continue;
      }
      String key =
          occurrence
              .origin()
              .declarationIdentity()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Live execution requires a declaration identity: "
                              + occurrence.origin().definitionName()));
      indexedKeys.put(occurrence.id(), key);
      var signature =
          new CoreDefinition.Callable(
              callable.receiverType(),
              callable.typeParameters(),
              callable.captureTypes(),
              callable.captureLocals(),
              callable.parameters(),
              callable.reifiedTypeLocals(),
              callable.interceptors(),
              callable.returnType(),
              callable.locals(),
              new CoreBlock(0, List.of()));
      byte[] encoded =
          CoreCodec.encodeDefinition(
              signature,
              link ->
                  new DefinitionReference.External(
                      artifact.program().resolve(owner, (DefinitionReference) link)));
      var shape = new Shape(owner, artifact.program());
      shape.walkExecution(callable);
      var bindingContracts =
          indexedBindings.getOrDefault(occurrence.id(), List.of()).stream()
              .sorted(Arrays::compareUnsigned)
              .toList();
      var bindingWriter = new CanonicalWriter().writeInt(bindingContracts.size());
      bindingContracts.forEach(bindingWriter::writeBytes);
      if (indexedContracts.putIfAbsent(
              key,
              new Contract(
                  encoded,
                  List.copyOf(shape.parts),
                  occurrence.origin().localNames(),
                  bindingWriter.toByteArray()))
          != null) {
        throw new IllegalArgumentException("Ambiguous live declaration identity: " + key);
      }
    }
    keys = Map.copyOf(indexedKeys);
    contracts = Map.copyOf(indexedContracts);
    structures = Set.copyOf(indexedStructures);
    metadata = MetadataId.forArtifact(artifact);
    entry = key(artifact.entryPoint());
  }

  public static CoreLiveSchema create(CoreArtifact artifact) {
    return new CoreLiveSchema(Objects.requireNonNull(artifact));
  }

  public String key(DefinitionOccurrenceId occurrence) {
    return Objects.requireNonNull(keys.get(occurrence), "live callable identity");
  }

  public void requireCompatible(CoreLiveSchema candidate) {
    Objects.requireNonNull(candidate);
    if (!entry.equals(candidate.entry))
      throw new IllegalArgumentException("Live update changes the entry declaration");
    if (!structures.equals(candidate.structures))
      throw new IllegalArgumentException("Live update changes runtime type or object structure");
    if (!metadata.equals(candidate.metadata))
      throw new IllegalArgumentException("Live update changes runtime annotations");
    contracts.forEach(
        (key, contract) -> {
          Contract next = candidate.contracts.get(key);
          if (next == null)
            throw new IllegalArgumentException("Live update removes a declaration: " + key);
          if (!Arrays.equals(contract.signature(), next.signature())
              || !Arrays.equals(contract.bindings(), next.bindings())
              || !contract.localNames().equals(next.localNames())) {
            throw new IllegalArgumentException(
                "Live update changes a callable or local state layout: " + key);
          }
          if (!contract.shape().equals(next.shape())) {
            throw new IllegalArgumentException("Live update changes execution structure: " + key);
          }
        });
  }

  private record Contract(
      byte[] signature, List<Object> shape, Map<Integer, String> localNames, byte[] bindings) {}

  private static final class Shape extends CoreWalker {
    private final DefinitionId owner;
    private final CoreProgram program;
    private final List<Object> parts = new ArrayList<>();

    private Shape(DefinitionId owner, CoreProgram program) {
      this.owner = owner;
      this.program = program;
    }

    @Override
    protected void visitBlock(CoreBlock block) {
      parts.add(CoreBlock.class);
      parts.add(block.statements().size());
    }

    @Override
    protected void visitStatement(CoreStatement statement) {
      parts.add(statement.getClass());
      switch (statement) {
        case CoreStatement.LocalDeclaration local -> parts.add(local.localIndex());
        case CoreStatement.LocalAssignment local -> parts.add(local.localIndex());
        case CoreStatement.ForStatement loop -> {
          parts.add(loop.iteratorLocal());
          parts.add(loop.variableLocal());
          parts.add(loop.indexLocal());
          parts.add(loop.iteration());
        }
        case CoreStatement.TryStatement tried -> {
          parts.add(tried.catches().size());
          parts.add(tried.finallyBlock().isPresent());
          tried
              .catches()
              .forEach(
                  clause -> {
                    parts.add(clause.localIndex());
                    parts.add(CoreTypes.absolute(clause.type(), owner, program));
                  });
        }
        case CoreStatement.ReturnStatement returned -> parts.add(returned.value().isPresent());
        case CoreStatement.IntrinsicAssignment assignment ->
            parts.add(assignment.index().isPresent());
        case CoreStatement.FieldAssignment ignored -> {}
        case CoreStatement.ReferenceAssignment ignored -> {}
        case CoreStatement.ExpressionStatement ignored -> {}
        case CoreStatement.IfStatement ignored -> {}
        case CoreStatement.ConditionalForStatement ignored -> {}
        case CoreStatement.ThrowStatement ignored -> {}
        case CoreStatement.YieldStatement ignored -> {}
        case CoreStatement.BreakStatement ignored -> {}
        case CoreStatement.ContinueStatement ignored -> {}
      }
    }

    @Override
    protected void visitExpression(CoreExpression expression) {
      parts.add(expression.getClass());
      parts.add(CoreTypes.absolute(expression.type(), owner, program));
      switch (expression) {
        case CoreExpression.Let let -> parts.add(let.localIndex());
        case CoreExpression.CollectionLiteral collection -> parts.add(collection.elements().size());
        case CoreExpression.Switch switched -> parts.add(switched.cases().size());
        case CoreExpression.EnumConstruct construct -> arguments(construct.arguments());
        case CoreExpression.Construct construct -> arguments(construct.arguments());
        case CoreExpression.Invoke invoke -> arguments(invoke.arguments());
        case CoreExpression.Call call -> {
          parts.add(call.receiver().isPresent());
          parts.add(call.virtual());
          parts.add(call.reifiedArguments().size());
          parts.add(call.receiverTypeArguments().size());
          arguments(call.arguments());
        }
        case CoreExpression.InterfaceCall call -> {
          parts.add(call.reifiedArguments().size());
          arguments(call.arguments());
        }
        case CoreExpression.Intrinsic intrinsic -> {
          parts.add(intrinsic.receiver().isPresent());
          arguments(intrinsic.arguments());
        }
        case CoreExpression.Closure closure -> {
          parts.add(closure.receiver().isPresent());
          parts.add(closure.captures().size());
          parts.add(closure.virtual());
          parts.add(closure.reifiedArguments().size());
          parts.add(closure.receiverTypeArguments().size());
        }
        case CoreExpression.Literal ignored -> {}
        case CoreExpression.NullLiteral ignored -> {}
        case CoreExpression.LocalRead ignored -> {}
        case CoreExpression.FieldRead ignored -> {}
        case CoreExpression.AddressLocal ignored -> {}
        case CoreExpression.AddressField ignored -> {}
        case CoreExpression.Dereference ignored -> {}
        case CoreExpression.Unary ignored -> {}
        case CoreExpression.Binary ignored -> {}
        case CoreExpression.Index ignored -> {}
        case CoreExpression.CopyObject ignored -> {}
      }
    }

    private void arguments(List<CoreArgument> arguments) {
      parts.add(arguments.stream().map(CoreArgument::parameterIndex).toList());
    }

    @Override
    protected void visitCollectionElement(CoreCollectionElement element) {
      if (element instanceof CoreExpression) return;
      parts.add(element.getClass());
      switch (element) {
        case CoreCollectionElement.Conditional conditional ->
            parts.add(conditional.elseElement().isPresent());
        case CoreCollectionElement.Repeated repeated -> {
          parts.add(repeated.iteratorLocal());
          parts.add(repeated.variableLocal());
          parts.add(repeated.indexLocal());
          parts.add(repeated.iteration());
        }
        case CoreExpression ignored -> {}
      }
    }

    @Override
    protected void visitPattern(CorePattern pattern) {
      parts.add(pattern.getClass());
      switch (pattern) {
        case CorePattern.Variant variant -> parts.add(variant.arguments().size());
        case CorePattern.Binding binding -> {
          parts.add(binding.localIndex());
          parts.add(CoreTypes.absolute(binding.type(), owner, program));
        }
        case CorePattern.Literal literal ->
            parts.add(CoreTypes.absolute(literal.type(), owner, program));
        case CorePattern.Wildcard ignored -> {}
        case CorePattern.Null ignored -> {}
      }
    }
  }
}
