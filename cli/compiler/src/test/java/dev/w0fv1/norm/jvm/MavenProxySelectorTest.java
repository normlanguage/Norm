package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

final class MavenProxySelectorTest {
  private static final RemoteRepository REPOSITORY =
      new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/")
          .build();

  @Test
  void adaptsTheSharedHttpProxySelectionWithoutChangingJvmGlobalState() {
    ProxySelector previous = ProxySelector.getDefault();
    var selector =
        new MavenProxySelector(
            ProxySelector.of(InetSocketAddress.createUnresolved("proxy.example", 3128)));
    var proxy = selector.getProxy(REPOSITORY);
    assertEquals("http", proxy.getType());
    assertEquals("proxy.example", proxy.getHost());
    assertEquals(3128, proxy.getPort());
    assertEquals(previous, ProxySelector.getDefault());
  }

  @Test
  void honorsDirectRoutingForTheRequestedRepository() {
    var selector =
        new MavenProxySelector(
            new ProxySelector() {
              @Override
              public List<Proxy> select(URI uri) {
                assertEquals(REPOSITORY.getUrl(), uri.toString());
                return List.of(Proxy.NO_PROXY);
              }

              @Override
              public void connectFailed(URI uri, SocketAddress address, IOException error) {}
            });
    assertNull(selector.getProxy(REPOSITORY));
  }
}
