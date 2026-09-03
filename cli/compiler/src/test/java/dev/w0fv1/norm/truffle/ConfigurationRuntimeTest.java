package dev.w0fv1.norm.truffle;

import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class ConfigurationRuntimeTest {
  @Test
  void mapsTypedConfigurationValuesToFlatHostProperties() {
    var checked =
        compile(
                """
                import std.collections.MutableMap
                import std.configuration.ConfigurationKey
                import std.configuration.ConfigurationValue
                import std.configuration.configurationProperties
                import std.serialization.Serializable

                @Serializable()
                value Server { String host Integer port }

                @Serializable()
                value StaticResource {
                  @ConfigurationKey()
                  String name
                  String mapping
                  List<String> paths
                }

                @Serializable()
                value Router { List<StaticResource> staticResources }

                @Serializable()
                value SecurityAccess {
                  @ConfigurationValue()
                  String expression
                }

                @Serializable()
                value SecurityRule { String pattern List<SecurityAccess> access }

                @Serializable()
                value Security {
                  List<SecurityRule> interceptUrlMap
                  String? token
                }

                @Serializable()
                value Micronaut { Server server Router router Security security }

                @Serializable()
                value Config { Micronaut micronaut }

                Void main() {
                  MutableMap<String?, Any?> properties = configurationProperties(value: Config(
                    micronaut: Micronaut(
                      server: Server(host: "127.0.0.1", port: 8080),
                      router: Router(staticResources: [
                        StaticResource(
                          name: "bbs",
                          mapping: "/**",
                          paths: ["classpath:public", "file:public"]
                        )
                      ]),
                      security: Security(
                        interceptUrlMap: [
                          SecurityRule(
                            pattern: "/**",
                            access: [SecurityAccess(expression: "isAnonymous()")]
                          )
                        ],
                        token: null
                      )
                    )
                  ))
                  printLine(properties.get(key: "micronaut.server.host") ?? "missing")
                  printLine(properties.get(key: "micronaut.server.port") ?? -1)
                  printLine(properties.get(
                    key: "micronaut.router.static-resources.bbs.mapping"
                  ) ?? "missing")
                  printLine(properties.get(
                    key: "micronaut.router.static-resources.bbs.paths[0]"
                  ) ?? "missing")
                  printLine(properties.get(
                    key: "micronaut.router.static-resources.bbs.paths[1]"
                  ) ?? "missing")
                  printLine(properties.get(
                    key: "micronaut.security.intercept-url-map[0].pattern"
                  ) ?? "missing")
                  printLine(properties.get(
                    key: "micronaut.security.intercept-url-map[0].access[0]"
                  ) ?? "missing")
                  printLine(properties.containsKey(key: "micronaut.security.token"))
                  printLine(properties.size())
                }
                """)
            .program()
            .orElseThrow();
    ExecutableProgram executable = new Lowerer(null).lower(checked.compilation().artifact());
    StringWriter output = new StringWriter();

    executable.execute(ExecutionContext.of(new PrintWriter(output)));

    assertEquals(
        String.join(
                System.lineSeparator(),
                "127.0.0.1",
                "8080",
                "/**",
                "classpath:public",
                "file:public",
                "/**",
                "isAnonymous()",
                "false",
                "7")
            + System.lineSeparator(),
        output.toString());
    assertEquals(8, executable.annotations().configuration().cachedPlanCount());
  }

  @Test
  void rejectsAConfigurationValueThatWouldDiscardStoredFields() {
    var checked =
        compile(
                """
                import std.configuration.ConfigurationValue
                import std.configuration.configurationProperties
                import std.serialization.Serializable

                @Serializable()
                value InvalidScalar {
                  @ConfigurationValue()
                  String value
                  String discarded
                }

                @Serializable()
                value Config { InvalidScalar scalar }

                Void main() {
                  configurationProperties(value: Config(
                    scalar: InvalidScalar(value: "kept", discarded: "lost")
                  ))
                }
                """)
            .program()
            .orElseThrow();
    ExecutableProgram executable = new Lowerer(null).lower(checked.compilation().artifact());

    NormGuestException failure =
        assertThrows(
            NormGuestException.class,
            () -> executable.execute(ExecutionContext.of(new PrintWriter(new StringWriter()))));

    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, failure.code());
  }
}
