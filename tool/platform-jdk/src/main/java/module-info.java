@SuppressWarnings("module")
module dev.w0fv1.norm.platform.jdk {
  requires transitive dev.w0fv1.norm.execution;
  requires transitive java.net.http;

  exports dev.w0fv1.norm.platform.jdk;
}
