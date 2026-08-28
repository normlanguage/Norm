@SuppressWarnings("module")
module dev.w0fv1.norm.truffle {
  requires transitive dev.w0fv1.norm.core;
  requires transitive dev.w0fv1.norm.execution;
  requires dev.w0fv1.norm.platform.jdk;
  requires dev.w0fv1.norm.project;
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.dataformat.yaml;
  requires com.ctc.wstx;
  requires java.xml;
  requires org.graalvm.polyglot;
  requires org.graalvm.truffle;

  uses javax.xml.stream.XMLInputFactory;
  uses javax.xml.stream.XMLOutputFactory;

  provides com.oracle.truffle.api.provider.TruffleLanguageProvider with
      dev.w0fv1.norm.truffle.LanguageProvider;

  exports dev.w0fv1.norm.runtime;
  exports dev.w0fv1.norm.utils;
}
