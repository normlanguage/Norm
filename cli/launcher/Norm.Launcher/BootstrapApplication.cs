namespace Norm.Launcher;

internal sealed class BootstrapApplication(
    EmbeddedRuntime runtime,
    RuntimeLauncher launcher,
    SetupService setup)
{
    public static BootstrapApplication Create()
    {
        string version = BuildVersion.Current;
        BootstrapPaths paths = BootstrapPaths.ForCurrentUser(version);
        EmbeddedRuntime runtime = new(paths, version);
        string executable = Environment.ProcessPath
            ?? throw new InvalidOperationException("The launcher executable path is unavailable");
        return new BootstrapApplication(
            runtime,
            new RuntimeLauncher(),
            new SetupService(paths, new WindowsUserEnvironment(), executable));
    }

    public int Run(string[] arguments)
    {
        return BootstrapCommand.Parse(arguments) switch
        {
            BootstrapCommand.Setup => setup.Run(runtime.EnsureAvailable()),
            BootstrapCommand.Run run => launcher.Run(runtime.EnsureAvailable(), run.Arguments),
            _ => throw new InvalidOperationException("Unknown launcher command")
        };
    }
}
