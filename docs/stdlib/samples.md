# Library samples

Each linked repository owns its sample source and instructions. Browse by use case below, then follow the linked repository README for package preparation and commands. Some examples require locally built development packages; the [library sample setup](/tooling/library-samples) explains the shared prerequisites. Short lessons cover [JSON roundtrips](/learn/json-roundtrip), [file I/O](/learn/file-write), [file failures](/learn/file-failures), and [Commons Lang first use](/learn/commons-lang).

## Text and documents

| Library | Use case | Samples |
| --- | --- | --- |
| [commons-lang](https://github.com/normlanguage/commons-lang) | Normalize and transform text | [Samples](https://github.com/normlanguage/commons-lang/blob/main/samples/README.md) |
| [commons-io](https://github.com/normlanguage/commons-io) | File operations and revision comparison | [Samples](https://github.com/normlanguage/commons-io/blob/main/samples/README.md) |
| [jsoup-jsoup](https://github.com/normlanguage/jsoup-jsoup) | Select links and clean HTML | [Samples](https://github.com/normlanguage/jsoup-jsoup/blob/main/samples/README.md) |

## Data and collections

| Library | Use case | Samples |
| --- | --- | --- |
| [org-json](https://github.com/normlanguage/org-json) | Parse and update JSON objects | [Samples](https://github.com/normlanguage/org-json/blob/main/samples/README.md) |
| [joda-time](https://github.com/normlanguage/joda-time) | Calculate dates across calendar boundaries | [Samples](https://github.com/normlanguage/joda-time/blob/main/samples/README.md) |
| [fastutil-collections](https://github.com/normlanguage/fastutil-collections) | Count with primitive collections | [Samples](https://github.com/normlanguage/fastutil-collections/blob/main/samples/README.md) |
| [eclipse-collections](https://github.com/normlanguage/eclipse-collections) | Filter and aggregate collections | [Samples](https://github.com/normlanguage/eclipse-collections/blob/main/samples/README.md) |
| [guava-core](https://github.com/normlanguage/guava-core) | Split and join text collections | [Samples](https://github.com/normlanguage/guava-core/blob/main/samples/README.md) |
| [caffeine-cache](https://github.com/normlanguage/caffeine-cache) | Bounded caching and invalidation | [Samples](https://github.com/normlanguage/caffeine-cache/blob/main/samples/README.md) |

## Applications and integration

| Library | Use case | Samples |
| --- | --- | --- |
| [di](https://github.com/normlanguage/di) | Assemble injected services | [Samples](https://github.com/normlanguage/di/blob/main/samples/README.md) |
| [junit-jupiter](https://github.com/normlanguage/junit-jupiter) | Run a JUnit-backed Norm test | [Samples](https://github.com/normlanguage/junit-jupiter/blob/main/samples/README.md) |
| [micronaut-test](https://github.com/normlanguage/micronaut-test) | Test a Micronaut context and injected replacement | [Samples](https://github.com/normlanguage/micronaut-test/blob/main/samples/README.md) |
| [micronaut-aop](https://github.com/normlanguage/micronaut-aop) | Intercept a service call | [Samples](https://github.com/normlanguage/micronaut-aop/blob/main/samples/README.md) |
| [micronaut-serde-jackson](https://github.com/normlanguage/micronaut-serde-jackson) | Serialize a typed message | [Samples](https://github.com/normlanguage/micronaut-serde-jackson/blob/main/samples/README.md) |
| [micronaut-validation](https://github.com/normlanguage/micronaut-validation) | Validate invalid input | [Samples](https://github.com/normlanguage/micronaut-validation/blob/main/samples/README.md) |
| [micronaut-views-jstachio](https://github.com/normlanguage/micronaut-views-jstachio) | Render an HTML template | [Samples](https://github.com/normlanguage/micronaut-views-jstachio/blob/main/samples/README.md) |
| [orm](https://github.com/normlanguage/orm) | Save, find, and update an H2 entity | [Samples](https://github.com/normlanguage/orm/blob/main/samples/README.md) |
| [micronaut-data-jdbc](https://github.com/normlanguage/micronaut-data-jdbc) | Insert and query H2 records through a repository | [Samples](https://github.com/normlanguage/micronaut-data-jdbc/blob/main/samples/README.md) |
| [reactor-core](https://github.com/normlanguage/reactor-core) | Transform a finite reactive stream | [Samples](https://github.com/normlanguage/reactor-core/blob/main/samples/README.md) |
| [ui](https://github.com/normlanguage/ui) | Create a counter window | [Samples](https://github.com/normlanguage/ui/blob/main/samples/README.md) |

The UI counter needs a graphical session. Follow its sample guide for dependencies and the run command.

## HTTP and agents

| Library | Use case | Samples |
| --- | --- | --- |
| [micronaut-web](https://github.com/normlanguage/micronaut-web) | Serve local HTTP routes and forms | [Samples](https://github.com/normlanguage/micronaut-web/blob/main/samples/README.md) |
| [micronaut-websocket](https://github.com/normlanguage/micronaut-websocket) | Exchange a loopback WebSocket message | [Samples](https://github.com/normlanguage/micronaut-websocket/blob/main/samples/README.md) |
| [micronaut-security](https://github.com/normlanguage/micronaut-security) | Compare allowed and rejected anonymous requests | [Samples](https://github.com/normlanguage/micronaut-security/blob/main/samples/README.md) |
| [micronaut-http-client](https://github.com/normlanguage/micronaut-http-client) | Call a local HTTP service | [Samples](https://github.com/normlanguage/micronaut-http-client/blob/main/samples/README.md) |
| [micronaut-management](https://github.com/normlanguage/micronaut-management) | Query a health endpoint | [Samples](https://github.com/normlanguage/micronaut-management/blob/main/samples/README.md) |
| [okhttp-client](https://github.com/normlanguage/okhttp-client) | Call a local HTTP fixture | [Samples](https://github.com/normlanguage/okhttp-client/blob/main/samples/README.md) |
| [openai](https://github.com/normlanguage/openai) | Call a local OpenAI-compatible fixture and dispatch tools | [Samples](https://github.com/normlanguage/openai/blob/main/samples/README.md) |

The complete GUI Todo and Web guestbook are generated by [`norm hello`](/tooling/#start-with-examples).
