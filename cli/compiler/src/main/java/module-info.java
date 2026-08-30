@SuppressWarnings("module")
module dev.w0fv1.norm {
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.dataformat.yaml;
  requires com.ctc.wstx;
  requires com.google.gson;
  requires java.net.http;
  requires java.xml;
  requires org.eclipse.lsp4j;
  requires org.eclipse.lsp4j.jsonrpc;
  requires org.graalvm.polyglot;
  requires org.graalvm.truffle;

  uses javax.xml.stream.XMLInputFactory;
  uses javax.xml.stream.XMLOutputFactory;

  provides com.oracle.truffle.api.provider.TruffleLanguageProvider with
      dev.w0fv1.norm.truffle.LanguageProvider;

  opens dev.w0fv1.norm.cli.component to
      org.eclipse.lsp4j.jsonrpc;
}
