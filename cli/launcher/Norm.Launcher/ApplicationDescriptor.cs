using System.Text.Json;

namespace Norm.Launcher;

internal sealed record ApplicationDescriptor(string Entry)
{
    public static ApplicationDescriptor Read(string json)
    {
        ApplicationContract? contract = JsonSerializer.Deserialize(json, LauncherJsonContext.Default.ApplicationContract);
        if (contract?.FormatVersion != 1 || string.IsNullOrWhiteSpace(contract.Entry))
        {
            throw new InvalidDataException("The embedded application descriptor is incomplete");
        }
        return new ApplicationDescriptor(contract.Entry);
    }
}

internal sealed record ApplicationContract(
    [property: System.Text.Json.Serialization.JsonPropertyName("formatVersion")] int FormatVersion,
    [property: System.Text.Json.Serialization.JsonPropertyName("entry")] string? Entry);
