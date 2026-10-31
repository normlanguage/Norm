package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreReachability;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record ApplicationProgramPlan(
    dev.w0fv1.norm.core.CoreReachability.Analysis retention,
    CoreExecutionPlan execution,
    java.util.List<LinkedJarBinding> bindings,
    boolean dynamicBindingLookup,
    String packageName) {
  public ApplicationProgramPlan {
    Objects.requireNonNull(retention, "retention");
    Objects.requireNonNull(execution, "execution");
    bindings = java.util.List.copyOf(bindings);
    Objects.requireNonNull(packageName, "packageName");
  }

  public static ApplicationProgramPlan from(CompiledApplication compilation) {
    Objects.requireNonNull(compilation, "compilation");
    var applicationIndex = compilation.methods();
    var original = compilation.result().output().orElseThrow().artifact();
    Set<String> hostTypes =
        compilation.annotations().stubs().stream()
            .map(stub -> stub.binaryName())
            .collect(java.util.stream.Collectors.toSet());
    var entryPoints = applicationIndex.sourceEntryPoints(original);
    var applicationEntries = new LinkedHashSet<>(entryPoints);
    if (compilation.javaClasspath().artifacts().stream()
        .anyMatch(
            artifact ->
                artifact.identity() instanceof dev.w0fv1.norm.jvm.MavenJarIdentity maven
                    && maven.coordinate().group().equals("org.slf4j")
                    && maven.coordinate().artifact().equals("slf4j-api"))) {
      original
          .namespace()
          .definition("std.logging", "startApplicationLogs")
          .ifPresent(applicationEntries::add);
    }
    Set<DefinitionId> hostEntries = new LinkedHashSet<>(applicationEntries);
    for (var definition : original.program().definitions()) {
      var nominal =
          switch (definition.definition()) {
            case CoreDefinition.Aggregate type -> type.nominalType();
            case CoreDefinition.Interface type -> type.nominalType();
            case CoreDefinition.Enum type -> type.nominalType();
            default -> null;
          };
      if (nominal != null && hostTypes.contains(JavaApplicationTypeName.binaryName(nominal)))
        hostEntries.add(definition.id());
    }
    var retention = CoreReachability.analyze(original, hostEntries);
    var retained = retention.artifact();
    var execution = CoreExecutionPlan.forArtifact(retained, applicationEntries);
    var calls = CoreReachability.jarCalls(retained, execution);
    var bindings =
        compilation.javaBindings().stream()
            .map(LinkedJarBinding::from)
            .map(binding -> calls.map(binding::retainCalls).orElse(binding))
            .toList();
    return new ApplicationProgramPlan(
        retention,
        execution,
        bindings,
        calls.isEmpty(),
        JavaApplicationTypeName.packageName(
            SourceHeader.parse(compilation.input().source()).packageName().orElse("")));
  }

  public dev.w0fv1.norm.runtime.ApplicationProgramData data() {
    return new dev.w0fv1.norm.runtime.ApplicationProgramData(
        retention.artifact(), execution, bindings, packageName);
  }
}
