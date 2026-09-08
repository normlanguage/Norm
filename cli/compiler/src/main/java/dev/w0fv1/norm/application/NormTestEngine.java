package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreTestIndex;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

final class NormTestEngine implements TestEngine {
  private final CompiledApplication application;
  private final ExecutionBackend backend;
  private final ExecutionContext context;
  private final List<CoreTestIndex.Test> tests;

  NormTestEngine(
      CompiledApplication application,
      ExecutionBackend backend,
      ExecutionContext context,
      Optional<String> filter) {
    this.application = application;
    this.backend = backend;
    this.context = context;
    var request = application.input().request();
    var root = request.scope().coordinate(request.entryDocument()).module();
    tests =
        CoreTestIndex.from(application.result().output().orElseThrow().artifact()).tests().stream()
            .filter(test -> request.scope().coordinates().containsKey(test.source().source().id()))
            .filter(
                test ->
                    request.scope().coordinate(test.source().source().id()).module().equals(root))
            .filter(
                test ->
                    filter.isEmpty()
                        || test.name().equals(filter.orElseThrow())
                        || test.name().startsWith(filter.orElseThrow() + "."))
            .toList();
  }

  @Override
  public String getId() {
    return "norm";
  }

  @Override
  public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId id) {
    var root = new EngineDescriptor(id, "Norm");
    for (var test : tests) {
      root.addChild(
          new Descriptor(
              id.append(
                  "test",
                  application.input().request().scope().sourcePath(test.source().source().id())
                      + "::"
                      + test.name()),
              test));
    }
    return root;
  }

  @Override
  public void execute(ExecutionRequest request) {
    var listener = request.getEngineExecutionListener();
    var root = request.getRootTestDescriptor();
    listener.executionStarted(root);
    for (var child : root.getChildren()) {
      var test = ((Descriptor) child).test;
      listener.executionStarted(child);
      TestExecutionResult result;
      try (var runtime = application.openRuntime()) {
        var output = new StringWriter();
        var expected = new StringWriter();
        var execution =
            context
                .withOutput(new PrintWriter(output), new PrintWriter(expected))
                .withJarBindingRuntime(runtime)
                .withWorkingDirectory(test.source().source().path().getParent());
        var artifact =
            application
                .result()
                .output()
                .orElseThrow()
                .artifact()
                .withEntryPoint(test.occurrence());
        backend.execute(
            artifact,
            CoreExecutionPlan.forArtifact(artifact, application.methods().entryPoints()),
            execution);
        if (!expected.toString().isEmpty() && !expected.toString().equals(output.toString())) {
          throw new AssertionError("Expected output:\n" + expected + "Actual output:\n" + output);
        }
        context.output().print(output);
        result = TestExecutionResult.successful();
      } catch (Exception | AssertionError failure) {
        result = TestExecutionResult.failed(failure);
      }
      listener.executionFinished(child, result);
    }
    listener.executionFinished(root, TestExecutionResult.successful());
  }

  private static final class Descriptor extends AbstractTestDescriptor {
    private final CoreTestIndex.Test test;

    private Descriptor(UniqueId id, CoreTestIndex.Test test) {
      super(id, test.name());
      this.test = test;
    }

    @Override
    public Type getType() {
      return Type.TEST;
    }
  }
}
