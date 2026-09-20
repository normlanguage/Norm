using System.Diagnostics;
using System.Reflection.PortableExecutable;

namespace Norm.Launcher.Tests;

public sealed class ApplicationProcessTest
{
    [Fact]
    public void RecognizesTheWindowsSubsystemFromAnActualPeImage()
    {
        string path = Path.Combine(Path.GetTempPath(), "norm-subsystem-" + Guid.NewGuid().ToString("N") + ".exe");
        try
        {
            File.Copy(typeof(ApplicationProcessTest).Assembly.Location, path);
            using (FileStream file = File.Open(path, FileMode.Open, FileAccess.ReadWrite))
            {
                using PEReader reader = new(file, PEStreamOptions.LeaveOpen);
                int offset = reader.PEHeaders.PEHeaderStartOffset + 68;
                file.Position = offset;
                file.WriteByte(2);
                file.WriteByte(0);
            }
            Assert.True(ApplicationProcess.IsWindowed(path));
        }
        finally { File.Delete(path); }
    }
}
