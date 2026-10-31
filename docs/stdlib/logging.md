# Application logging

Import the log functions from `std.logging`. The host sets the default minimum severity with `NORM_LOG_LEVEL`, accepting `debug`, `info`, `warn`, `error` or `off`. The default is `info`.

`NORM_LOG_OUTPUTS` accepts a JSON array. Each entry names the fully qualified implementation in `name`. Optional `level` overrides the default threshold, `source` selects a package and its descendants, and `options` supplies string configuration to the implementation.

```json
[
  {"name": "std.logging.ConsoleLogOutputer", "level": "warn"},
  {"name": "std.logging.FileLogOutputer", "source": "shop", "options": {"directory": "logs"}}
]
```

[Log functions and routing](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/log.norm) · [File output options](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/file.norm) · [Provider discovery](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/application/application.norm)
