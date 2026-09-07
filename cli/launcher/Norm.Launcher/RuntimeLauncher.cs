using System.Diagnostics;

namespace Norm.Launcher;

internal sealed class RuntimeLauncher
{
    public int Run(string runtimeDirectory, IReadOnlyList<string> arguments)
    {
        ProcessStartInfo start = CreateStartInfo(runtimeDirectory, arguments);
        return ApplicationProcess.Run(start);
    }

    public int RunApplication(string runtimeDirectory, EmbeddedApplication application)
    {
        return ApplicationProcess.Run(CreateApplicationStartInfo(runtimeDirectory, application));
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
        if (Environment.ProcessPath is string executable)
        {
            start.Environment["NORM_LAUNCHER_PATH"] = executable;
        }
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

    internal static ProcessStartInfo CreateApplicationStartInfo(string runtimeDirectory, EmbeddedApplication application)
    {
        ProcessStartInfo start = CreateStartInfo(runtimeDirectory, ["run", application.Entry]);
        start.Environment["NORM_APPLICATION_BUNDLE"] = application.Root;
        start.Environment["NORM_APPLICATION_EXECUTABLE"] = Environment.ProcessPath;
        return start;
    }
}
