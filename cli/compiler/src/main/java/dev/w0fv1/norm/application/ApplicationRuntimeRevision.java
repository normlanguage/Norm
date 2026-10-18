package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.store.DirectoryArtifactCache;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.execution.RevisionResources;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.runtime.PreparedApplicationContent;
import java.lang.ref.WeakReference;

final class ApplicationRuntimeRevision {
  private ApplicationRuntimeRevision() {}

  static ExecutionRevision compiled(
      CompiledApplication application, ExecutionContext context, JvmJarBindingRuntime.Realm realm) {
    var assets = application.detachAssets();
    try {
      var runtime = application.openRuntime(assets, realm);
      var revision =
          new ExecutionRevision(
              application.result().output().orElseThrow().artifact(),
              application.context(context).withJarBindingRuntime(runtime),
              RevisionResources.own(new Resources(new WeakReference<>(runtime), assets)));
      application.close();
      return revision;
    } catch (RuntimeException | Error exception) {
      assets.close();
      application.close();
      throw exception;
    }
  }

  static ExecutionRevision prepared(
      PreparedApplicationContent content,
      DirectoryArtifactCache.Lease files,
      ExecutionContext context,
      JvmJarBindingRuntime.Realm realm) {
    var assets = RevisionResources.own(files);
    try {
      var runtime = content.application().openRuntime(files.path(), assets, realm);
      return new ExecutionRevision(
          content.program().artifact(),
          context
              .withApplicationPackage(content.program().packageName())
              .withJarBindingRuntime(runtime),
          RevisionResources.own(new Resources(new WeakReference<>(runtime), assets)));
    } catch (RuntimeException | Error exception) {
      assets.close();
      throw exception;
    }
  }

  private record Resources(
      WeakReference<JvmJarBindingRuntime> runtime, RevisionResources.Lease assets)
      implements AutoCloseable {
    @Override
    public void close() {
      try {
        var retained = runtime.get();
        if (retained != null) retained.close();
      } finally {
        assets.close();
      }
    }
  }
}
