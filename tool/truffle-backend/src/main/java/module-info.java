@SuppressWarnings("module")
module dev.w0fv1.norm.truffle {
  requires transitive dev.w0fv1.norm.core;
  requires transitive dev.w0fv1.norm.execution;
  requires dev.w0fv1.norm.platform.jdk;
  requires dev.w0fv1.norm.project;
  requires org.graalvm.polyglot;
  requires org.graalvm.truffle;

  provides com.oracle.truffle.api.provider.TruffleLanguageProvider with
      dev.w0fv1.norm.truffle.LanguageProvider;

  exports dev.w0fv1.norm.runtime;
  exports dev.w0fv1.norm.utils;
}
