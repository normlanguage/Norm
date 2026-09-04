using System.Text.Json.Serialization;

namespace Norm.Launcher;

[JsonSerializable(typeof(LauncherContract))]
internal partial class LauncherJsonContext : JsonSerializerContext;
