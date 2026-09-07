using System.Diagnostics;

namespace Norm.Launcher;

internal static class ApplicationProcess
{
    public static int Run(ProcessStartInfo start)
    {
        using Process process = Process.Start(start)
            ?? throw new InvalidOperationException("The application could not be started");
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
}
