using System.IO.Compression;

namespace Norm.Launcher.Tests;

public sealed class NativeApplicationTest : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "norm-native-tests-" + Guid.NewGuid().ToString("N"));

    [Fact]
    public void VerifiesAndCachesCompleteNativeDelivery()
    {
        ApplicationPayload payload = CreatePayload();
        string cache = Path.Combine(root, "cache");
        string entry = NativeApplication.Prepare(payload, cache);
        Assert.Equal("native", File.ReadAllText(entry));
        Assert.Equal("library", File.ReadAllText(Path.Combine(Path.GetDirectoryName(entry)!, "java.dll")));
        File.Delete(payload.Executable);
        Assert.Equal(entry, NativeApplication.Prepare(payload, cache));
        Assert.Single(Directory.GetFileSystemEntries(cache));
    }

    [Fact]
    public void CorruptPayloadCannotPublishCacheOrLeaveTemporaryArchive()
    {
        ApplicationPayload payload = CreatePayload();
        using (FileStream stream = File.OpenWrite(payload.Executable)) stream.WriteByte(0);
        string cache = Path.Combine(root, "cache");
        Assert.Throws<InvalidDataException>(() => NativeApplication.Prepare(payload, cache));
        Assert.Empty(Directory.GetFileSystemEntries(cache));
    }

    private ApplicationPayload CreatePayload()
    {
        Directory.CreateDirectory(root);
        using MemoryStream buffer = new();
        using (ZipArchive archive = new(buffer, ZipArchiveMode.Create, true))
        {
            using (StreamWriter image = new(archive.CreateEntry("application.exe").Open())) image.Write("native");
            using (StreamWriter library = new(archive.CreateEntry("java.dll").Open())) library.Write("library");
        }
        byte[] bytes = buffer.ToArray();
        string path = Path.Combine(root, "app.exe");
        using (FileStream file = File.Create(path))
        {
            file.Write(bytes);
            byte[] length = new byte[sizeof(long)];
            System.Buffers.Binary.BinaryPrimitives.WriteInt64BigEndian(length, bytes.Length);
            file.Write(length);
            file.Write(System.Security.Cryptography.SHA256.HashData(bytes));
            file.Write(System.Text.Encoding.ASCII.GetBytes("NORMAPP1"));
        }
        return ApplicationPayload.Read(path)!;
    }

    [Fact]
    public async Task ConcurrentExtractionReusesImmutableContent()
    {
        Directory.CreateDirectory(root);
        byte[] zip;
        using (MemoryStream buffer = new())
        {
            using (ZipArchive archive = new(buffer, ZipArchiveMode.Create, true))
            {
                using StreamWriter entry = new(archive.CreateEntry("app.exe").Open());
                entry.Write("native executable");
            }
            zip = buffer.ToArray();
        }
        string destination = Path.Combine(root, "cached");
        await Task.WhenAll(Enumerable.Range(0, 8).Select(_ => Task.Run(() =>
        {
            using MemoryStream stream = new(zip);
            RuntimeExtractor.Extract(stream, destination, "digest");
        })));
        Assert.Equal("native executable", File.ReadAllText(Path.Combine(destination, "app.exe")));
        Assert.Single(Directory.GetDirectories(root));
    }

    [Fact]
    public void PreservesArgumentsAndWorkingDirectoryWithoutJava()
    {
        string entry = Path.Combine(root, "app.exe");
        string outer = Path.Combine(root, "deployed", "web.norm.exe");
        var start = NativeApplication.CreateStartInfo(entry, outer, ["a b", "中文", "--port=0"]);
        Assert.Equal(entry, start.FileName);
        Assert.Equal(new[] { "a b", "中文", "--port=0" }, start.ArgumentList);
        Assert.False(start.UseShellExecute);
        Assert.True(string.IsNullOrEmpty(start.WorkingDirectory));
        Assert.Equal(outer, start.Environment["NORM_APPLICATION_EXECUTABLE"]);
    }

    public void Dispose()
    {
        if (Directory.Exists(root)) Directory.Delete(root, true);
    }
}
