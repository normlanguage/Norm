using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace Norm.Launcher;

internal static class ProcessOutput
{
    public static async Task ForwardAsync(Stream source, bool error)
    {
        using Stream destination = error ? Console.OpenStandardError() : Console.OpenStandardOutput();
        IntPtr handle = OperatingSystem.IsWindows() ? GetStdHandle(error ? -12 : -11) : IntPtr.Zero;
        await CopyAsync(source, handle, destination);
    }

    internal static async Task CopyAsync(Stream source, IntPtr handle, Stream destination)
    {
        if (!OperatingSystem.IsWindows() || !GetConsoleMode(handle, out _))
        {
            await source.CopyToAsync(destination);
            return;
        }
        using StreamReader reader = new(source, new UTF8Encoding(false, true), false, 4096, leaveOpen: true);
        char[] buffer = new char[4096];
        for (int count; (count = await reader.ReadAsync(buffer.AsMemory())) != 0;)
        {
            string text = new(buffer, 0, count);
            while (text.Length != 0)
            {
                if (!WriteConsoleW(handle, text, (uint)text.Length, out uint written, IntPtr.Zero))
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot write application output to the console");
                if (written == 0) throw new IOException("The console did not accept application output");
                text = text[(int)written..];
            }
        }
    }

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetStdHandle(int standardHandle);

    [DllImport("kernel32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetConsoleMode(IntPtr handle, out uint mode);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool WriteConsoleW(IntPtr handle, string text, uint count, out uint written, IntPtr reserved);
}
