using System.Text.Json.Serialization;

namespace Norm.Launcher;

[JsonSerializable(typeof(LauncherContract))]
[JsonSerializable(typeof(ApplicationContract))]
internal partial class LauncherJsonContext : JsonSerializerContext;
