namespace Norm.Launcher;

internal static class UserPath
{
    public static string Include(string? current, string directory)
    {
        string normalizedDirectory = Normalize(directory);
        List<string> entries = (current ?? string.Empty)
            .Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .ToList();
        if (!entries.Any(entry => string.Equals(Normalize(entry), normalizedDirectory, StringComparison.OrdinalIgnoreCase)))
        {
            entries.Add(directory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
        }
        return string.Join(Path.PathSeparator, entries);
    }

    private static string Normalize(string path)
    {
        string trimmed = path.Trim().Trim('"').TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        return trimmed.Contains('%') ? trimmed : Path.GetFullPath(trimmed);
    }
}
