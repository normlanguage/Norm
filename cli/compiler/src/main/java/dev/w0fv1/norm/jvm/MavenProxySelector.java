package dev.w0fv1.norm.jvm;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Objects;
import org.eclipse.aether.repository.RemoteRepository;

final class MavenProxySelector implements org.eclipse.aether.repository.ProxySelector {
  private final java.net.ProxySelector selector;

  MavenProxySelector(java.net.ProxySelector selector) {
    this.selector = Objects.requireNonNull(selector, "selector");
  }

  @Override
  public org.eclipse.aether.repository.Proxy getProxy(RemoteRepository repository) {
    for (Proxy proxy : selector.select(URI.create(repository.getUrl()))) {
      if (proxy.type() == Proxy.Type.DIRECT) return null;
      if (proxy.type() == Proxy.Type.HTTP && proxy.address() instanceof InetSocketAddress address) {
        return new org.eclipse.aether.repository.Proxy(
            "http", address.getHostString(), address.getPort());
      }
    }
    return null;
  }
}
