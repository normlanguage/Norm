@SuppressWarnings("module")
module dev.w0fv1.norm.cli {
  requires dev.w0fv1.norm.core;
  requires dev.w0fv1.norm.execution;
  requires dev.w0fv1.norm.platform.jdk;
  requires dev.w0fv1.norm.project;
  requires dev.w0fv1.norm.truffle;
  requires org.eclipse.lsp4j;
  requires org.eclipse.lsp4j.jsonrpc;
  requires com.google.gson;

  exports dev.w0fv1.norm.cli;
  exports dev.w0fv1.norm.cli.value;

  opens dev.w0fv1.norm.cli.component to
      org.eclipse.lsp4j.jsonrpc;
}
