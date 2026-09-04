using System.ComponentModel;

namespace Norm.Launcher;

internal static class Program
{
    public static int Main(string[] arguments)
    {
        try
        {
            return BootstrapApplication.Create().Run(arguments);
        }
        catch (Exception error) when (error is IOException or InvalidDataException or UnauthorizedAccessException or InvalidOperationException or Win32Exception)
        {
            Console.Error.WriteLine($"norm: {error.Message}");
            return 1;
        }
    }
}
