package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.List;

public final class JarBindingSourceGenerator {
  private final BindingPlanner planner = new BindingPlanner();
  private final BindingSourceRenderer renderer = new BindingSourceRenderer();

  public GeneratedJarBinding generate(
      ModuleCoordinate module, List<String> exports, Sha256Digest graphId, JarApiSchema schema) {
    return renderer.render(planner.plan(module, exports, graphId, schema));
  }

  public GeneratedJarBinding generateSurface(
      ModuleCoordinate module,
      List<JarBindingType> api,
      Sha256Digest graphId,
      JarApiSchema schema) {
    return renderer.render(planner.planSurface(module, api, graphId, schema));
  }

  public GeneratedJarBinding generateSurface(
      ModuleCoordinate module,
      List<String> exports,
      List<JarBindingType> api,
      Sha256Digest graphId,
      JarApiSchema schema) {
    return renderer.render(planner.planSurface(module, exports, api, graphId, schema));
  }
}
