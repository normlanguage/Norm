package dev.w0fv1.norm.build;

import dev.w0fv1.norm.application.CompiledApplication;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreReachability;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import java.util.List;
import java.util.Objects;

record NativeBuildPlan(
    CompiledApplication application,
    CoreReachability.Analysis retention,
    CoreExecutionPlan execution,
    List<LinkedJarBinding> bindings,
    boolean dynamicBindingLookup,
    String packageName) {
  NativeBuildPlan {
    Objects.requireNonNull(application, "application");
    Objects.requireNonNull(retention, "retention");
    Objects.requireNonNull(execution, "execution");
    bindings = List.copyOf(bindings);
    Objects.requireNonNull(packageName, "packageName");
  }
}
