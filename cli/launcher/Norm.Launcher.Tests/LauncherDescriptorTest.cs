namespace Norm.Launcher.Tests;

public sealed class LauncherDescriptorTest
{
    [Fact]
    public void ReadsTheGeneratedRuntimeContract()
    {
        const string json = """
            {"module":"dev.w0fv1.norm/dev.w0fv1.norm.cli.Main","jvmArguments":["--first","value with spaces"]}
            """;

        LauncherDescriptor descriptor = LauncherDescriptor.Read(json);

        Assert.Equal("dev.w0fv1.norm/dev.w0fv1.norm.cli.Main", descriptor.Module);
        Assert.Equal(["--first", "value with spaces"], descriptor.JvmArguments);
    }

    [Fact]
    public void RejectsAnIncompleteRuntimeContract()
    {
        Assert.Throws<InvalidDataException>(() => LauncherDescriptor.Read("{}"));
    }
}
