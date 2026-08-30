package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record CoreAnnotationPolicy(Set<AnnotationTarget> targets, AnnotationRetention retention) {
  public CoreAnnotationPolicy {
    targets = Set.copyOf(targets);
    if (targets.isEmpty()) throw new IllegalArgumentException("annotation targets are empty");
    java.util.Objects.requireNonNull(retention, "retention");
  }

  public static CoreAnnotationPolicy resolve(
      CoreProgram program, DefinitionId owner, CoreDefinition.Aggregate annotation) {
    if (annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("annotation policy requires an annotation aggregate");
    }
    Policies policies = policies(program, owner, annotation);
    if (policies.retentions().size() != 1) {
      throw new IllegalArgumentException("annotation requires exactly one retention policy");
    }
    return new CoreAnnotationPolicy(policies.targets(), policies.retentions().iterator().next());
  }

  static boolean usesPolicyInterfaces(
      CoreProgram program, DefinitionId owner, CoreDefinition.Aggregate aggregate) {
    return policies(program, owner, aggregate).usesPolicyInterface();
  }

  private static Policies policies(
      CoreProgram program, DefinitionId owner, CoreDefinition.Aggregate aggregate) {
    Set<AnnotationTarget> targets = new LinkedHashSet<>();
    Set<AnnotationRetention> retentions = new LinkedHashSet<>();
    Map<DefinitionId, CoreType.Declared> interfaces = new LinkedHashMap<>();
    CoreInterfaceHierarchy hierarchy = new CoreInterfaceHierarchy(program);
    for (CoreConformance conformance : aggregate.conformances()) {
      hierarchy.collect(owner, conformance.interfaceType(), interfaces);
    }
    boolean usesPolicyInterface = false;
    for (DefinitionId interfaceId : interfaces.keySet()) {
      CoreDefinition.Interface declaration =
          (CoreDefinition.Interface) program.definition(interfaceId).orElseThrow();
      CoreNominalTypeKey nominal = declaration.nominalType();
      AnnotationAbi.target(nominal.module(), nominal.packageName(), nominal.name())
          .ifPresent(targets::add);
      AnnotationAbi.retention(nominal.module(), nominal.packageName(), nominal.name())
          .ifPresent(retentions::add);
      usesPolicyInterface |=
          AnnotationAbi.isPolicyInterface(nominal.module(), nominal.packageName(), nominal.name());
    }
    return new Policies(targets, retentions, usesPolicyInterface);
  }

  private record Policies(
      Set<AnnotationTarget> targets,
      Set<AnnotationRetention> retentions,
      boolean usesPolicyInterface) {}
}
