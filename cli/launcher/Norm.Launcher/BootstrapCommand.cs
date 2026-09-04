namespace Norm.Launcher;

internal abstract record BootstrapCommand
{
    public sealed record Setup : BootstrapCommand;

    public sealed record Run(IReadOnlyList<string> Arguments) : BootstrapCommand;

    public static BootstrapCommand Parse(string[] arguments)
    {
        return arguments is ["setup"] ? new Setup() : new Run(arguments);
    }
}
