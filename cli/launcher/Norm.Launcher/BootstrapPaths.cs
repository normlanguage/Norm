namespace Norm.Launcher;

internal sealed record BootstrapPaths(
    string ProductRoot,
    string InstallDirectory,
    string RuntimeDirectory,
    string CacheDirectory)
{
    public string InstalledExecutable => Path.Combine(InstallDirectory, "norm.exe");

    public static BootstrapPaths ForCurrentUser(string version)
    {
        string productRoot = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs",
            "Norm");
        return new BootstrapPaths(
            productRoot,
            Path.Combine(productRoot, "bin"),
            Path.Combine(productRoot, "runtimes", version, "norm"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".norm", "cache"));
    }
}
