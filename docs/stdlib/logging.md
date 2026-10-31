# Application logging

Import the log functions from `std.logging`. The host sets the default minimum severity with `NORM_LOG_LEVEL`, accepting `debug`, `info`, `warn`, `error` or `off`. The default is `info`.

Console timestamps use ISO 8601 in UTC, for example `2026-10-31T11:34:52Z`.

`NORM_LOG_OUTPUTS` accepts a JSON array. Each entry names the fully qualified implementation in `name`. Optional `level` overrides the default threshold, `source` selects a package and its descendants, and `options` supplies string configuration to the implementation.

`norm run --debug` applies the debug threshold to the command, application, Norm packages and Java dependencies using SLF4J for that invocation. This explicit host option overrides `NORM_LOG_LEVEL` and every output entry's `level`, including `off`. Output destinations and `source` filters still select where events go. A `--debug` argument after `--` is passed to the application.

```json
[
  {"name": "std.logging.ConsoleLogOutputer", "level": "warn"},
  {"name": "std.logging.FileLogOutputer", "source": "shop", "options": {"directory": "logs"}}
]
```

[Log functions and routing](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/log.norm) · [File output options](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/file.norm) · [Provider discovery](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/application/application.norm)
