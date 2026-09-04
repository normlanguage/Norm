namespace Norm.Launcher;

internal sealed record EmbeddedApplication(string Root, string Entry)
{
    public static EmbeddedApplication Prepare(BootstrapPaths paths, ApplicationPayload payload)
    {
        string applications = Path.Combine(paths.ProductRoot, "applications");
        string root = Path.Combine(applications, payload.Digest);
        string marker = Path.Combine(root, ".complete");
        if (!File.Exists(marker) || File.ReadAllText(marker) != payload.Digest)
        {
            Directory.CreateDirectory(applications);
            string archive = Path.Combine(applications, ".payload-" + Guid.NewGuid().ToString("N") + ".zip");
            try
            {
                payload.CopyTo(archive);
                using FileStream stream = File.OpenRead(archive);
                RuntimeExtractor.Extract(stream, root, payload.Digest);
            }
            finally
            {
                File.Delete(archive);
            }
        }
        ApplicationDescriptor descriptor = ApplicationDescriptor.Read(
            File.ReadAllText(Path.Combine(root, "application.json")));
        string entry = Path.GetFullPath(Path.Combine(root, descriptor.Entry.Replace('/', Path.DirectorySeparatorChar)));
        string prefix = Path.GetFullPath(root) + Path.DirectorySeparatorChar;
        if (!entry.StartsWith(prefix, StringComparison.OrdinalIgnoreCase) || !File.Exists(entry))
        {
            throw new InvalidDataException("The embedded application entry is unavailable");
        }
        return new EmbeddedApplication(root, entry);
    }
}
