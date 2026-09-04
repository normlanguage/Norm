using System.Reflection;
using System.Security.Cryptography;
using System.Text;

namespace Norm.Launcher;

internal sealed class EmbeddedRuntime(BootstrapPaths paths, string version)
{
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
            RuntimeExtractor.Extract(payload, paths.RuntimeDirectory, version);
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
