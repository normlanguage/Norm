using System.Diagnostics;

namespace Norm.Launcher;

internal sealed class LauncherUpdate(string executable)
{
    public int Run(ProcessStartInfo start)
    {
        string request = Path.Combine(Path.GetTempPath(), "norm-update-request-" + Guid.NewGuid().ToString("N"));
        start.Environment["NORM_UPDATE_REQUEST"] = request;
        try
        {
            int status = ApplicationProcess.Run(start);
            if (!File.Exists(request)) return status;
            string staged = Path.GetFullPath(File.ReadAllText(request));
            string target = Path.GetFullPath(executable);
            if (!string.Equals(Path.GetDirectoryName(staged), Path.GetDirectoryName(target), StringComparison.OrdinalIgnoreCase)
                || !Path.GetFileName(staged).StartsWith("norm-update-", StringComparison.Ordinal)
                || !staged.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("The CLI update file is outside the installation directory");
            string backup = target + ".previous";
            File.Move(target, backup, true);
            try
            {
                File.Move(staged, target);
            }
            catch
            {
                File.Move(backup, target);
                throw;
            }
            Console.WriteLine("Norm CLI installation completed. Run 'norm --version' to verify.");
            return status;
        }
        finally
        {
            File.Delete(request);
        }
    }
}
