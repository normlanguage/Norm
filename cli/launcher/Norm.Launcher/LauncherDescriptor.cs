using System.Text.Json;

namespace Norm.Launcher;

internal sealed record LauncherDescriptor(string Module, IReadOnlyList<string> JvmArguments)
{
    public static LauncherDescriptor Read(string json)
    {
        LauncherContract? contract = JsonSerializer.Deserialize(json, LauncherJsonContext.Default.LauncherContract);
        if (string.IsNullOrWhiteSpace(contract?.Module) || contract.JvmArguments is null)
        {
            throw new InvalidDataException("The embedded runtime launcher contract is incomplete");
        }
        return new LauncherDescriptor(contract.Module, contract.JvmArguments);
    }
}

internal sealed record LauncherContract(
    [property: System.Text.Json.Serialization.JsonPropertyName("module")] string? Module,
    [property: System.Text.Json.Serialization.JsonPropertyName("jvmArguments")] string[]? JvmArguments);
