using System.ComponentModel;

namespace Norm.Launcher;

internal static class Program
{
    public static int Main(string[] arguments)
    {
        try
        {
            string executable = Environment.ProcessPath
                ?? throw new InvalidOperationException("The executable path is unavailable");
            ApplicationPayload payload = ApplicationPayload.Read(executable)
                ?? throw new InvalidDataException("The native application payload is unavailable");
            string cache = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".norm", "cache");
            string entry = NativeApplication.Prepare(payload, cache);
            return ApplicationProcess.Run(NativeApplication.CreateStartInfo(entry, executable, arguments));
        }
        catch (Exception error) when (error is IOException or InvalidDataException or UnauthorizedAccessException or InvalidOperationException or Win32Exception)
        {
            Console.Error.WriteLine($"norm: {error.Message}");
            return 1;
        }
    }
}
