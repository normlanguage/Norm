using System.IO.Compression;

namespace Norm.Launcher.Tests;

public sealed class RuntimeExtractorTest : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "norm-launcher-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public void ExtractsACompleteRuntimeAtomically()
    {
        using MemoryStream payload = Payload(("bin/launcher.json", "{}"), ("runtime/bin/java.exe", "java"));
        string destination = Path.Combine(root, "runtime");

        RuntimeExtractor.Extract(payload, destination, "0.19.1");

        Assert.Equal("{}", File.ReadAllText(Path.Combine(destination, "bin", "launcher.json")));
        Assert.Equal("0.19.1", File.ReadAllText(Path.Combine(destination, ".complete")));
        Assert.Empty(Directory.GetDirectories(root, ".extract-*"));
    }

    [Fact]
    public void RejectsEntriesOutsideTheRuntime()
    {
        using MemoryStream payload = Payload(("../outside.txt", "bad"));
        string destination = Path.Combine(root, "runtime");

        InvalidDataException error = Assert.Throws<InvalidDataException>(() => RuntimeExtractor.Extract(payload, destination, "0.19.1"));

        Assert.Contains("outside", error.Message, StringComparison.OrdinalIgnoreCase);
        Assert.False(File.Exists(Path.Combine(root, "outside.txt")));
        Assert.False(Directory.Exists(destination));
    }

    [Fact]
    public void KeepsAnExistingCompleteRuntime()
    {
        string destination = Path.Combine(root, "runtime");
        Directory.CreateDirectory(destination);
        File.WriteAllText(Path.Combine(destination, ".complete"), "0.19.1");
        File.WriteAllText(Path.Combine(destination, "kept.txt"), "existing");
        using MemoryStream invalidPayload = new([1, 2, 3]);

        RuntimeExtractor.Extract(invalidPayload, destination, "0.19.1");

        Assert.Equal("existing", File.ReadAllText(Path.Combine(destination, "kept.txt")));
    }

    public void Dispose()
    {
        if (Directory.Exists(root))
        {
            Directory.Delete(root, true);
        }
    }

    private static MemoryStream Payload(params (string Name, string Contents)[] entries)
    {
        MemoryStream stream = new();
        using (ZipArchive archive = new(stream, ZipArchiveMode.Create, true))
        {
            foreach ((string name, string contents) in entries)
            {
                using StreamWriter writer = new(archive.CreateEntry(name).Open());
                writer.Write(contents);
            }
        }
        stream.Position = 0;
        return stream;
    }
}
