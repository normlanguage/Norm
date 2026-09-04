using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace Norm.Launcher;

internal sealed record ApplicationPayload(string Executable, long Offset, long Length, string Digest)
{
    private static readonly byte[] Magic = Encoding.ASCII.GetBytes("NORMAPP1");
    private const int FooterLength = sizeof(long) + 32 + 8;

    public static ApplicationPayload? Read(string executable)
    {
        using FileStream stream = File.OpenRead(executable);
        if (stream.Length < FooterLength)
        {
            return null;
        }
        stream.Seek(-FooterLength, SeekOrigin.End);
        byte[] footer = new byte[FooterLength];
        stream.ReadExactly(footer);
        if (!footer.AsSpan(FooterLength - Magic.Length).SequenceEqual(Magic))
        {
            return null;
        }
        long length = BinaryPrimitives.ReadInt64BigEndian(footer.AsSpan(0, sizeof(long)));
        long offset = stream.Length - FooterLength - length;
        if (length <= 0 || offset < 0)
        {
            throw new InvalidDataException("The embedded application payload is incomplete");
        }
        return new ApplicationPayload(
            executable,
            offset,
            length,
            Convert.ToHexString(footer.AsSpan(sizeof(long), 32)).ToLowerInvariant());
    }

    public void CopyTo(string destination)
    {
        using FileStream input = File.OpenRead(Executable);
        using FileStream output = File.Create(destination);
        using IncrementalHash hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        input.Position = Offset;
        byte[] buffer = new byte[128 * 1024];
        long remaining = Length;
        while (remaining > 0)
        {
            int read = input.Read(buffer, 0, (int)Math.Min(buffer.Length, remaining));
            if (read == 0)
            {
                throw new InvalidDataException("The embedded application payload is truncated");
            }
            output.Write(buffer, 0, read);
            hash.AppendData(buffer, 0, read);
            remaining -= read;
        }
        string actual = Convert.ToHexString(hash.GetHashAndReset()).ToLowerInvariant();
        if (!string.Equals(actual, Digest, StringComparison.Ordinal))
        {
            throw new InvalidDataException("The embedded application payload failed its integrity check");
        }
    }
}
