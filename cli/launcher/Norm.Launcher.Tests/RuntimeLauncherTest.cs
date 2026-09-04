using System.Diagnostics;

namespace Norm.Launcher.Tests;

public sealed class RuntimeLauncherTest : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "norm-runtime-launcher-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public void PreservesEveryJvmAndUserArgument()
    {
        string bin = Path.Combine(root, "bin");
        Directory.CreateDirectory(bin);
        File.WriteAllText(
            Path.Combine(bin, "launcher.json"),
            """{"module":"norm/main","jvmArguments":["--native","value with spaces"]}""");

        ProcessStartInfo start = RuntimeLauncher.CreateStartInfo(root, ["web.norm", "user value"]);

        Assert.Equal(Path.Combine(root, "runtime", "bin", "java.exe"), start.FileName);
        Assert.Equal(
            ["--native", "value with spaces", "--module-path", Path.Combine(root, "lib"), "--module", "norm/main", "web.norm", "user value"],
            start.ArgumentList);
    }

    [Fact]
    public void LaunchesAnEmbeddedApplicationWithItsOfflineBundle()
    {
        string bin = Path.Combine(root, "bin");
        Directory.CreateDirectory(bin);
        File.WriteAllText(
            Path.Combine(bin, "launcher.json"),
            """{"module":"norm/main","jvmArguments":[]}""");
        EmbeddedApplication application = new(Path.Combine(root, "application"), Path.Combine(root, "application", "source", "web.norm"));

        ProcessStartInfo start = RuntimeLauncher.CreateApplicationStartInfo(root, application);

        Assert.Equal(["--module-path", Path.Combine(root, "lib"), "--module", "norm/main", "run", application.Entry], start.ArgumentList);
        Assert.Equal(application.Root, start.Environment["NORM_APPLICATION_BUNDLE"]);
    }

    public void Dispose()
    {
        if (Directory.Exists(root))
        {
            Directory.Delete(root, true);
        }
    }
}
