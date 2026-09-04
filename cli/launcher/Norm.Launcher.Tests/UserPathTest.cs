namespace Norm.Launcher.Tests;

public sealed class UserPathTest
{
    [Fact]
    public void AddsTheInstallDirectoryOnce()
    {
        string installDirectory = Path.GetFullPath(Path.Combine("root", "Norm", "bin"));
        string otherDirectory = Path.GetFullPath(Path.Combine("root", "Other"));

        string first = UserPath.Include(otherDirectory, installDirectory);
        string second = UserPath.Include(first, installDirectory + Path.DirectorySeparatorChar);

        Assert.Equal(first, second);
        Assert.Equal([otherDirectory, installDirectory], first.Split(Path.PathSeparator));
    }

    [Fact]
    public void PreservesEmptyOrExpandedEntries()
    {
        string installDirectory = Path.GetFullPath(Path.Combine("root", "Norm", "bin"));

        string result = UserPath.Include("%SystemRoot%\\System32", installDirectory);

        Assert.Equal($"%SystemRoot%\\System32{Path.PathSeparator}{installDirectory}", result);
    }
}
