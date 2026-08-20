# Norm Configuration System

Configuration sources:

- files
- environment variables
- secrets providers
- command line

Configuration should be typed.

Example:
```norm
DatabaseConfig config = Config.load<DatabaseConfig>()
```
