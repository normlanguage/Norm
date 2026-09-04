using System.Security.Cryptography;

namespace Norm.Launcher;

internal sealed class SetupService(BootstrapPaths paths, IUserEnvironment environment, string sourceExecutable)
{
    public int Run(string runtimeDirectory)
    {
        if (!Directory.Exists(runtimeDirectory))
        {
            throw new InvalidOperationException("The Norm runtime is unavailable");
        }
        Directory.CreateDirectory(paths.InstallDirectory);
        Directory.CreateDirectory(paths.CacheDirectory);
        if (!SameFileContents(sourceExecutable, paths.InstalledExecutable))
        {
            string staging = paths.InstalledExecutable + ".installing-" + Guid.NewGuid().ToString("N");
            try
            {
                File.Copy(sourceExecutable, staging, true);
                File.Move(staging, paths.InstalledExecutable, true);
            }
            finally
            {
                if (File.Exists(staging))
                {
                    File.Delete(staging);
                }
            }
        }
        environment.Path = UserPath.Include(environment.Path, paths.InstallDirectory);
        environment.NotifyChanged();
        Console.WriteLine($"Norm {BuildVersion.Current} is ready.");
        Console.WriteLine($"Installed: {paths.InstalledExecutable}");
        Console.WriteLine("Open a new terminal, then run: norm --version");
        return 0;
    }

    private static bool SameFileContents(string left, string right)
    {
        if (!File.Exists(right))
        {
            return false;
        }
        using FileStream leftStream = File.OpenRead(left);
        using FileStream rightStream = File.OpenRead(right);
        return leftStream.Length == rightStream.Length
            && CryptographicOperations.FixedTimeEquals(SHA256.HashData(leftStream), SHA256.HashData(rightStream));
    }
}
