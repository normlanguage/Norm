using System.Diagnostics;

namespace Norm.Launcher;

internal sealed class RuntimeLauncher
{
    public int Run(string runtimeDirectory, IReadOnlyList<string> arguments)
    {
        ProcessStartInfo start = CreateStartInfo(runtimeDirectory, arguments);
        using Process process = Process.Start(start)
            ?? throw new InvalidOperationException("The Norm runtime could not be started");
        using WindowsProcessJob job = WindowsProcessJob.Attach(process);
        ConsoleCancelEventHandler cancel = (_, eventArguments) =>
        {
            eventArguments.Cancel = true;
            try
            {
                process.Kill(true);
            }
            catch (InvalidOperationException)
            {
            }
        };
        Console.CancelKeyPress += cancel;
        try
        {
            process.WaitForExit();
            return process.ExitCode;
        }
        finally
        {
            Console.CancelKeyPress -= cancel;
        }
    }

    internal static ProcessStartInfo CreateStartInfo(string runtimeDirectory, IReadOnlyList<string> arguments)
    {
        string descriptorPath = Path.Combine(runtimeDirectory, "bin", "launcher.json");
        LauncherDescriptor descriptor = LauncherDescriptor.Read(File.ReadAllText(descriptorPath));
        ProcessStartInfo start = new()
        {
            FileName = Path.Combine(runtimeDirectory, "runtime", "bin", "java.exe"),
            UseShellExecute = false
        };
        foreach (string argument in descriptor.JvmArguments)
        {
            start.ArgumentList.Add(argument);
        }
        start.ArgumentList.Add("--module-path");
        start.ArgumentList.Add(Path.Combine(runtimeDirectory, "lib"));
        start.ArgumentList.Add("--module");
        start.ArgumentList.Add(descriptor.Module);
        foreach (string argument in arguments)
        {
            start.ArgumentList.Add(argument);
        }
        return start;
    }
}
