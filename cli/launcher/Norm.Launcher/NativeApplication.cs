using System.Diagnostics;

namespace Norm.Launcher;

internal static class NativeApplication
{
    public static string Prepare(ApplicationPayload payload, string cache)
    {
        string root = Path.Combine(cache, "native-applications", payload.Digest);
        string marker = Path.Combine(root, ".complete");
        if (!File.Exists(marker) || File.ReadAllText(marker) != payload.Digest)
        {
            Directory.CreateDirectory(cache);
            string archive = Path.Combine(cache, ".native-" + Guid.NewGuid().ToString("N") + ".zip");
            try
            {
                payload.CopyTo(archive);
                using FileStream input = File.OpenRead(archive);
                RuntimeExtractor.Extract(input, root, payload.Digest);
            }
            finally
            {
                File.Delete(archive);
            }
        }
        string entry = Path.Combine(root, "application.exe");
        if (!File.Exists(entry)) throw new InvalidDataException("The native application entry is unavailable");
        return entry;
    }

    internal static ProcessStartInfo CreateStartInfo(string entry, string executable, IReadOnlyList<string> arguments)
    {
        ProcessStartInfo start = new() { FileName = entry, UseShellExecute = false };
        start.Environment["NORM_APPLICATION_EXECUTABLE"] = Path.GetFullPath(executable);
        foreach (string argument in arguments) start.ArgumentList.Add(argument);
        return start;
    }
}
