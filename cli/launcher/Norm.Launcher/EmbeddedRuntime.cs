using System.Reflection;
using System.Security.Cryptography;
using System.Text;

namespace Norm.Launcher;

internal sealed class EmbeddedRuntime(BootstrapPaths paths, string identity)
{
    public BootstrapPaths Paths => paths;

    public static EmbeddedRuntime Create()
    {
        using Stream digestPayload = typeof(EmbeddedRuntime).Assembly.GetManifestResourceStream("Norm.Runtime.sha256")
            ?? throw new InvalidOperationException("This executable does not identify its Norm runtime");
        using StreamReader digestReader = new(digestPayload, Encoding.ASCII);
        string digest = digestReader.ReadToEnd().Trim();
        return new EmbeddedRuntime(BootstrapPaths.ForCurrentUser(BuildVersion.Current, digest), BuildVersion.Current + ":" + digest);
    }

    public string EnsureAvailable()
    {
        Directory.CreateDirectory(paths.ProductRoot);
        string mutexName = "Norm.Runtime." + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(paths.RuntimeDirectory)));
        using Mutex mutex = new(false, mutexName);
        bool acquired = false;
        try
        {
            try
            {
                acquired = mutex.WaitOne(TimeSpan.FromMinutes(2));
            }
            catch (AbandonedMutexException)
            {
                acquired = true;
            }
            if (!acquired)
            {
                throw new IOException("Timed out while preparing the Norm runtime");
            }
            using Stream payload = typeof(EmbeddedRuntime).Assembly.GetManifestResourceStream("Norm.Runtime.zip")
                ?? throw new InvalidOperationException("This executable does not contain a Norm runtime");
            RuntimeExtractor.Extract(payload, paths.RuntimeDirectory, identity);
            return paths.RuntimeDirectory;
        }
        finally
        {
            if (acquired)
            {
                mutex.ReleaseMutex();
            }
        }
    }
}
