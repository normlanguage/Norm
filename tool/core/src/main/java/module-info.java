@SuppressWarnings("module")
module dev.w0fv1.norm.core {
  requires com.github.benmanes.caffeine;
  requires org.graalvm.polyglot;
  requires org.graalvm.truffle;

  provides com.oracle.truffle.api.provider.TruffleLanguageProvider with
      dev.w0fv1.norm.truffle.LanguageProvider;

  exports dev.w0fv1.norm.diagnostic;
  exports dev.w0fv1.norm.builtin;
  exports dev.w0fv1.norm.core;
  exports dev.w0fv1.norm.core.store;
  exports dev.w0fv1.norm.frontend;
  exports dev.w0fv1.norm.language;
  exports dev.w0fv1.norm.syntax;
  exports dev.w0fv1.norm.execution;
  exports dev.w0fv1.norm.semantic;
  exports dev.w0fv1.norm.utils;
  exports dev.w0fv1.norm.value;
}
