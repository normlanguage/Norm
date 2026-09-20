using System.Diagnostics;
using System.Reflection.PortableExecutable;

namespace Norm.Launcher;

internal static class ApplicationProcess
{
    internal static bool IsWindowed(string executable)
    {
        using FileStream file = File.OpenRead(executable);
        using PEReader reader = new(file);
        return reader.PEHeaders.PEHeader?.Subsystem == Subsystem.WindowsGui;
    }

    public static int Run(ProcessStartInfo start)
    {
        bool windowed = OperatingSystem.IsWindows() && Environment.ProcessPath is string executable && IsWindowed(executable);
        string? logs = null;
        if (windowed)
        {
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;
            logs = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Programs", "Norm", "logs", Path.GetFileNameWithoutExtension(Environment.ProcessPath)!,
                DateTime.UtcNow.ToString("yyyyMMddTHHmmssfff") + "-" + Environment.ProcessId);
            Directory.CreateDirectory(logs);
        }
        using FileStream? stdout = logs is null ? null : new FileStream(Path.Combine(logs, "stdout.log"), FileMode.CreateNew, FileAccess.Write, FileShare.Read);
        using FileStream? stderr = logs is null ? null : new FileStream(Path.Combine(logs, "stderr.log"), FileMode.CreateNew, FileAccess.Write, FileShare.Read);
        using Process process = Process.Start(start)
            ?? throw new InvalidOperationException("The application could not be started");
        using WindowsProcessJob job = WindowsProcessJob.Attach(process);
        Task output = stdout is null ? Task.CompletedTask : process.StandardOutput.BaseStream.CopyToAsync(stdout);
        Task errors = stderr is null ? Task.CompletedTask : process.StandardError.BaseStream.CopyToAsync(stderr);
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
            Task.WhenAll(output, errors).GetAwaiter().GetResult();
            return process.ExitCode;
        }
        finally
        {
            Console.CancelKeyPress -= cancel;
        }
    }
}
