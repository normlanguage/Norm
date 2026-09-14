namespace Norm.Launcher.Tests;

public sealed class BootstrapPathsTest
{
    [Fact]
    public void RuntimeContentIdentitiesHaveIndependentDirectoriesWithinTheSameVersion()
    {
        string first = new('a', 64);
        string second = new('b', 64);
        BootstrapPaths previous = BootstrapPaths.ForCurrentUser("0.23.0", first);
        BootstrapPaths next = BootstrapPaths.ForCurrentUser("0.23.0", second);

        Assert.NotEqual(previous.RuntimeDirectory, next.RuntimeDirectory);
        Assert.Equal(previous.InstalledExecutable, next.InstalledExecutable);
        Assert.Equal(previous.CacheDirectory, next.CacheDirectory);
        Assert.EndsWith(Path.Combine("runtimes", "0.23.0", first, "norm"), previous.RuntimeDirectory);
        Assert.EndsWith(Path.Combine("runtimes", "0.23.0", second, "norm"), next.RuntimeDirectory);
    }
}
