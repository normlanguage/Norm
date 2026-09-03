package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EnvironmentProxySelectorTest {
  private static final Proxy DIRECT = Proxy.NO_PROXY;
  private static final ProxySelector DIRECT_SELECTOR =
      new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
          return List.of(DIRECT);
        }

        @Override
        public void connectFailed(
            URI uri, java.net.SocketAddress address, java.io.IOException error) {}
      };

  @Test
  void selectsSchemeSpecificProxyFromStandardEnvironmentVariables() {
    ProxySelector selector =
        EnvironmentProxySelector.from(
            Map.of(
                "HTTPS_PROXY", "http://proxy.example:8443",
                "HTTP_PROXY", "http://proxy.example:8080"),
            DIRECT_SELECTOR);

    assertEquals(
        new InetSocketAddress("proxy.example", 8443),
        selector.select(URI.create("https://github.com/module")).getFirst().address());
    assertEquals(
        new InetSocketAddress("proxy.example", 8080),
        selector.select(URI.create("http://example.com/module")).getFirst().address());
  }

  @Test
  void bypassesProxyForNoProxyHostsAndDelegatesWithoutConfiguration() {
    ProxySelector selector =
        EnvironmentProxySelector.from(
            Map.of(
                "HTTPS_PROXY", "http://proxy.example:8443",
                "NO_PROXY", "localhost,.internal.example"),
            DIRECT_SELECTOR);

    assertEquals(DIRECT, selector.select(URI.create("https://localhost/module")).getFirst());
    assertEquals(
        DIRECT, selector.select(URI.create("https://api.internal.example/module")).getFirst());
    assertEquals(
        DIRECT,
        EnvironmentProxySelector.from(Map.of(), DIRECT_SELECTOR)
            .select(URI.create("https://github.com/module"))
            .getFirst());
  }
}
