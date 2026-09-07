using System.IO.Compression;

namespace Norm.Launcher;

internal static class RuntimeExtractor
{
    public static void Extract(Stream payload, string destination, string version)
    {
        string identity = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(
            System.Text.Encoding.UTF8.GetBytes(Path.GetFullPath(destination).ToUpperInvariant())));
        using Mutex mutex = new(false, "Norm.Extract." + identity);
        try
        {
            if (!mutex.WaitOne(TimeSpan.FromMinutes(2))) throw new IOException("Timed out waiting for application extraction");
        }
        catch (AbandonedMutexException)
        {
        }
        try
        {
            string marker = Path.Combine(destination, ".complete");
            if (File.Exists(marker) && File.ReadAllText(marker) == version)
            {
                return;
            }

            string parent = Path.GetDirectoryName(destination)
                ?? throw new InvalidOperationException("The runtime destination has no parent directory");
            Directory.CreateDirectory(parent);
            string staging = Path.Combine(parent, ".extract-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(staging);
            try
            {
                using ZipArchive archive = new(payload, ZipArchiveMode.Read, true);
                string stagingPrefix = Path.GetFullPath(staging) + Path.DirectorySeparatorChar;
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    string relativePath = entry.FullName.Replace('/', Path.DirectorySeparatorChar).Replace('\\', Path.DirectorySeparatorChar);
                    string outputPath = Path.GetFullPath(Path.Combine(staging, relativePath));
                    StringComparison comparison = OperatingSystem.IsWindows()
                        ? StringComparison.OrdinalIgnoreCase
                        : StringComparison.Ordinal;
                    if (!outputPath.StartsWith(stagingPrefix, comparison))
                    {
                        throw new InvalidDataException($"Runtime entry points outside the destination: {entry.FullName}");
                    }
                    if (entry.FullName.EndsWith('/') || entry.FullName.EndsWith('\\'))
                    {
                        Directory.CreateDirectory(outputPath);
                        continue;
                    }
                    Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);
                    entry.ExtractToFile(outputPath, true);
                }
                File.WriteAllText(Path.Combine(staging, ".complete"), version);
                if (Directory.Exists(destination)) Directory.Delete(destination, true);
                Directory.Move(staging, destination);
            }
            finally
            {
                if (Directory.Exists(staging))
                {
                    Directory.Delete(staging, true);
                }
            }
        }
        finally
        {
            mutex.ReleaseMutex();
        }
    }
}
