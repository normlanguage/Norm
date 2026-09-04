using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace Norm.Launcher.Tests;

public sealed class ApplicationPayloadTest : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "norm-application-payload-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public void ReadsAndVerifiesAnAppendedApplication()
    {
        Directory.CreateDirectory(root);
        string executable = Path.Combine(root, "application.exe");
        byte[] launcher = [1, 2, 3, 4];
        byte[] payload = Encoding.UTF8.GetBytes("application payload");
        byte[] digest = SHA256.HashData(payload);
        using (FileStream stream = File.Create(executable))
        {
            stream.Write(launcher);
            stream.Write(payload);
            byte[] length = new byte[sizeof(long)];
            BinaryPrimitives.WriteInt64BigEndian(length, payload.Length);
            stream.Write(length);
            stream.Write(digest);
            stream.Write(Encoding.ASCII.GetBytes("NORMAPP1"));
        }

        ApplicationPayload application = Assert.IsType<ApplicationPayload>(ApplicationPayload.Read(executable));
        string extracted = Path.Combine(root, "payload.zip");
        application.CopyTo(extracted);

        Assert.Equal(payload, File.ReadAllBytes(extracted));
        Assert.Equal(Convert.ToHexString(digest).ToLowerInvariant(), application.Digest);
    }

    [Fact]
    public void IgnoresTheOrdinaryNormLauncher()
    {
        Directory.CreateDirectory(root);
        string executable = Path.Combine(root, "norm.exe");
        File.WriteAllText(executable, "ordinary launcher");

        Assert.Null(ApplicationPayload.Read(executable));
    }

    public void Dispose()
    {
        if (Directory.Exists(root))
        {
            Directory.Delete(root, true);
        }
    }
}
