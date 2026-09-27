# Time API

The time library separates UTC instants, fixed lengths, clock capabilities, and future calendar types instead of mixing their semantics in one DateTime type.

## Fundamental types

`Instant` identifies one point on the UTC timeline using an epoch second and a nanosecond adjustment in 0..999,999,999. `Duration` represents a fixed number of seconds with the same nanosecond adjustment range. The `instant` and `duration` factories respectively create canonical values; an invalid adjustment throws `TimeException`.

```norm
Duration timeout = duration(seconds: 5, nanoseconds: 0)
Instant epoch = instant(epochSecond: 0, nanosecond: 0)
```

[`std.time`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/time/core.norm) contains the complete declarations.

## Clock

Business functions receive an explicit `Clock`. The application composition root obtains a host clock through `systemClock()`. Tests can inject a fixed clock without changing business code or global state.

```norm
Clock clock = systemClock()
Instant now = clock.now()
```

Clock-read failures throw `TimeException` with stable code, operation, and reason. Calendar dates, time zones, `Period`, formatting, and parsing will reuse these foundational types as their respective APIs are implemented.
