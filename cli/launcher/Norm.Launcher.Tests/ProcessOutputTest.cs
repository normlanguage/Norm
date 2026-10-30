using System.Runtime.InteropServices;
using System.Text;
using System.Diagnostics;
using Microsoft.Win32.SafeHandles;

namespace Norm.Launcher.Tests;

[Collection("Windows console")]
public sealed class ProcessOutputTest
{
    [WindowsFact]
    public void ClosesDescendantOutputHandlesBeforeWaitingForOutputCompletion()
    {
        ProcessStartInfo start = new("powershell.exe") { UseShellExecute = false, CreateNoWindow = true };
        start.ArgumentList.Add("-NoProfile");
        start.ArgumentList.Add("-Command");
        start.ArgumentList.Add("$child = [Diagnostics.ProcessStartInfo]::new('powershell.exe', '-NoProfile -Command Start-Sleep -Seconds 6'); $child.UseShellExecute = $false; $child.CreateNoWindow = $true; [Diagnostics.Process]::Start($child).Dispose()");
        Stopwatch timer = Stopwatch.StartNew();
        Assert.Equal(0, ApplicationProcess.Run(start));
        Assert.True(timer.Elapsed < TimeSpan.FromSeconds(4), $"Waited for an orphan child: {timer.Elapsed}");
    }

    [Fact]
    public async Task PreservesRedirectedBytesIncludingProtocolHeaders()
    {
        byte[] bytes = [.. Encoding.UTF8.GetBytes("Content-Length: 6\r\n\r\n中文"), 0xff];
        using MemoryStream source = new(bytes);
        using MemoryStream destination = new();
        await ProcessOutput.CopyAsync(source, IntPtr.Zero, destination);
        Assert.Equal(bytes, destination.ToArray());
    }

    [WindowsFact]
    public async Task WritesSplitUtf8ToARealConsoleWithoutChangingItsCodePage()
    {
        bool ownsConsole = GetConsoleOutputCP() == 0;
        if (ownsConsole)
        {
            Assert.True(AllocConsole());
            ShowWindow(GetConsoleWindow(), 0);
        }
        try
        {
            uint codePage = GetConsoleOutputCP();
            using SafeFileHandle console = CreateConsoleScreenBuffer(0xC0000000, 3, IntPtr.Zero, 1, IntPtr.Zero);
            Assert.False(console.IsInvalid, $"Cannot create console buffer: {Marshal.GetLastWin32Error()}");
            const string text = "中文正常 العربية";
            using Stream source = new FragmentedStream(Encoding.UTF8.GetBytes(text));
            using MemoryStream redirected = new();
            await ProcessOutput.CopyAsync(source, console.DangerousGetHandle(), redirected);
            StringBuilder actual = new(128);
            Assert.True(ReadConsoleOutputCharacterW(console, actual, 128, 0, out _));
            Assert.Equal(text, actual.ToString().TrimEnd());
            Assert.Empty(redirected.ToArray());
            Assert.Equal(codePage, GetConsoleOutputCP());
        }
        finally
        {
            if (ownsConsole) FreeConsole();
        }
    }

    private sealed class FragmentedStream(byte[] bytes) : MemoryStream(bytes)
    {
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
            => base.ReadAsync(buffer[..Math.Min(1, buffer.Length)], cancellationToken);
    }

    [DllImport("kernel32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool AllocConsole();

    [DllImport("kernel32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool FreeConsole();

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetConsoleWindow();

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool ShowWindow(IntPtr window, int command);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern SafeFileHandle CreateConsoleScreenBuffer(uint access, uint share, IntPtr security, uint flags, IntPtr data);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool ReadConsoleOutputCharacterW(SafeFileHandle console, StringBuilder text, uint count, uint coordinate, out uint read);

    [DllImport("kernel32.dll")]
    private static extern uint GetConsoleOutputCP();
}

[CollectionDefinition("Windows console", DisableParallelization = true)]
public sealed class WindowsConsoleCollection {}
