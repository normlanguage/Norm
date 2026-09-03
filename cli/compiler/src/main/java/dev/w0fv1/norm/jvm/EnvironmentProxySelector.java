package dev.w0fv1.norm.jvm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class EnvironmentProxySelector extends ProxySelector {
  private final Map<String, String> environment;
  private final ProxySelector fallback;

  static ProxySelector system() {
    ProxySelector fallback = ProxySelector.getDefault();
    return from(System.getenv(), fallback == null ? direct() : fallback);
  }

  static ProxySelector from(Map<String, String> environment, ProxySelector fallback) {
    return new EnvironmentProxySelector(environment, fallback);
  }

  private EnvironmentProxySelector(Map<String, String> environment, ProxySelector fallback) {
    this.environment = Map.copyOf(environment);
    this.fallback = Objects.requireNonNull(fallback, "fallback");
  }

  @Override
  public List<Proxy> select(URI uri) {
    Objects.requireNonNull(uri, "uri");
    String host = uri.getHost();
    if (host == null || bypasses(host, uri.getPort())) return fallback.select(uri);
    String configured = configuredProxy(uri.getScheme());
    if (configured == null || configured.isBlank()) return fallback.select(uri);
    URI proxy = parseProxy(configured);
    int port = proxy.getPort();
    if (port < 0) port = proxy.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    return List.of(
        new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxy.getHost(), port)));
  }

  @Override
  public void connectFailed(URI uri, SocketAddress address, IOException exception) {
    fallback.connectFailed(uri, address, exception);
  }

  private String configuredProxy(String scheme) {
    String name = scheme.equalsIgnoreCase("https") ? "HTTPS_PROXY" : "HTTP_PROXY";
    String value = environment.get(name);
    return value == null ? environment.get(name.toLowerCase(Locale.ROOT)) : value;
  }

  private boolean bypasses(String host, int port) {
    String value = environment.get("NO_PROXY");
    if (value == null) value = environment.get("no_proxy");
    if (value == null || value.isBlank()) return false;
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    for (String entry : value.split(",")) {
      String rule = entry.trim().toLowerCase(Locale.ROOT);
      if (rule.equals("*")) return true;
      int separator = rule.lastIndexOf(':');
      if (separator > 0 && rule.indexOf(':') == separator) {
        int configuredPort;
        try {
          configuredPort = Integer.parseInt(rule.substring(separator + 1));
        } catch (NumberFormatException ignored) {
          configuredPort = -1;
        }
        if (configuredPort >= 0) {
          if (configuredPort != port) continue;
          rule = rule.substring(0, separator);
        }
      }
      String suffix = rule.startsWith(".") ? rule : "." + rule;
      if (normalizedHost.equals(rule) || normalizedHost.endsWith(suffix)) return true;
    }
    return false;
  }

  private static URI parseProxy(String value) {
    URI proxy = URI.create(value.contains("://") ? value : "http://" + value);
    if (proxy.getHost() == null) throw new IllegalArgumentException("invalid proxy URI");
    return proxy;
  }

  private static ProxySelector direct() {
    return new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        return List.of(Proxy.NO_PROXY);
      }

      @Override
      public void connectFailed(URI uri, SocketAddress address, IOException exception) {}
    };
  }
}
