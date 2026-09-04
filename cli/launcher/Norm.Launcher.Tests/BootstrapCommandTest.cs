namespace Norm.Launcher.Tests;

public sealed class BootstrapCommandTest
{
    [Fact]
    public void SetupIsHandledByTheLauncher()
    {
        BootstrapCommand command = BootstrapCommand.Parse(["setup"]);

        Assert.IsType<BootstrapCommand.Setup>(command);
    }

    [Fact]
    public void AllOtherArgumentsAreForwardedUnchanged()
    {
        string[] arguments = ["web.norm", "value with spaces", "--flag"];

        BootstrapCommand.Run command = Assert.IsType<BootstrapCommand.Run>(BootstrapCommand.Parse(arguments));

        Assert.Equal(arguments, command.Arguments);
    }
}
