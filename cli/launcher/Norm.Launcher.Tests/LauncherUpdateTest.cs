using System.Diagnostics;

namespace Norm.Launcher.Tests;

public sealed class LauncherUpdateTest : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "norm-update's-test", Guid.NewGuid().ToString("N"));

    [Fact]
    public void InstallsAfterTheRuntimeJobHasClosedAndBeforeReturning()
    {
        Directory.CreateDirectory(root);
        string target = Path.Combine(root, "norm.exe");
        string staged = Path.Combine(root, "norm-update-new.exe");
        File.WriteAllText(target, "old");
        File.WriteAllText(staged, "new");
        ProcessStartInfo start = RuntimeCommand(
            "[IO.File]::WriteAllText($env:NORM_UPDATE_REQUEST, $env:TEST_STAGED_FILE)",
            "printf '%s' \"$TEST_STAGED_FILE\" > \"$NORM_UPDATE_REQUEST\"");
        start.Environment["TEST_STAGED_FILE"] = staged;
        Assert.Equal(0, new LauncherUpdate(target).Run(start));
        Assert.Equal("new", File.ReadAllText(target));
        Assert.Equal("old", File.ReadAllText(target + ".previous"));
        Assert.False(File.Exists(staged));
        Assert.False(File.Exists(start.Environment["NORM_UPDATE_REQUEST"]));
    }

    [Fact]
    public void LeavesTheLauncherUntouchedWhenNoUpdateWasRequested()
    {
        Directory.CreateDirectory(root);
        string target = Path.Combine(root, "norm.exe");
        File.WriteAllText(target, "old");
        ProcessStartInfo start = RuntimeCommand("exit 7", "exit 7");
        Assert.Equal(7, new LauncherUpdate(target).Run(start));
        Assert.Equal("old", File.ReadAllText(target));
        Assert.False(File.Exists(target + ".previous"));
    }

    [Fact]
    public void RestoresTheLauncherWhenTheStagedFileCannotBeInstalled()
    {
        Directory.CreateDirectory(root);
        string target = Path.Combine(root, "norm.exe");
        string staged = Path.Combine(root, "norm-update-missing.exe");
        File.WriteAllText(target, "old");
        ProcessStartInfo start = RuntimeCommand(
            "[IO.File]::WriteAllText($env:NORM_UPDATE_REQUEST, $env:TEST_STAGED_FILE)",
            "printf '%s' \"$TEST_STAGED_FILE\" > \"$NORM_UPDATE_REQUEST\"");
        start.Environment["TEST_STAGED_FILE"] = staged;
        Assert.Throws<FileNotFoundException>(() => new LauncherUpdate(target).Run(start));
        Assert.Equal("old", File.ReadAllText(target));
    }

    private static ProcessStartInfo RuntimeCommand(string windows, string unix)
    {
        bool isWindows = OperatingSystem.IsWindows();
        ProcessStartInfo start = new(isWindows ? "powershell.exe" : "/bin/sh") { UseShellExecute = false, CreateNoWindow = true };
        if (isWindows) start.ArgumentList.Add("-NoProfile");
        start.ArgumentList.Add(isWindows ? "-Command" : "-c");
        start.ArgumentList.Add(isWindows ? windows : unix);
        return start;
    }

    public void Dispose() { if (Directory.Exists(root)) Directory.Delete(root, true); }
}
