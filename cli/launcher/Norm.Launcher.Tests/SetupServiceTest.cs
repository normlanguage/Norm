namespace Norm.Launcher.Tests;

public sealed class SetupServiceTest : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "norm-setup-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public void InstallsTheExecutableCacheAndPathIdempotently()
    {
        string source = Path.Combine(root, "download", "norm.exe");
        string runtime = Path.Combine(root, "product", "runtimes", "0.19.1", "norm");
        Directory.CreateDirectory(Path.GetDirectoryName(source)!);
        Directory.CreateDirectory(runtime);
        File.WriteAllText(source, "executable");
        BootstrapPaths paths = new(
            Path.Combine(root, "product"),
            Path.Combine(root, "product", "bin"),
            runtime,
            Path.Combine(root, "profile", ".norm", "cache"));
        TestEnvironment environment = new() { Path = Path.Combine(root, "other") };
        SetupService setup = new(paths, environment, source);

        setup.Run(runtime);
        setup.Run(runtime);

        Assert.Equal("executable", File.ReadAllText(paths.InstalledExecutable));
        Assert.True(Directory.Exists(paths.CacheDirectory));
        Assert.Equal(1, environment.Path!.Split(Path.PathSeparator).Count(entry => entry == paths.InstallDirectory));
        Assert.Equal(2, environment.NotificationCount);
    }

    public void Dispose()
    {
        if (Directory.Exists(root))
        {
            Directory.Delete(root, true);
        }
    }

    private sealed class TestEnvironment : IUserEnvironment
    {
        public string? Path { get; set; }

        public int NotificationCount { get; private set; }

        public void NotifyChanged()
        {
            NotificationCount++;
        }
    }
}
