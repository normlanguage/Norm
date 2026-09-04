using System.Runtime.InteropServices;

namespace Norm.Launcher;

internal interface IUserEnvironment
{
    string? Path { get; set; }

    void NotifyChanged();
}

internal sealed class WindowsUserEnvironment : IUserEnvironment
{
    private const uint EnvironmentChanged = 0x001A;
    private const uint AbortIfHung = 0x0002;
    private static readonly IntPtr BroadcastWindow = new(0xffff);

    public string? Path
    {
        get => Environment.GetEnvironmentVariable("Path", EnvironmentVariableTarget.User);
        set => Environment.SetEnvironmentVariable("Path", value, EnvironmentVariableTarget.User);
    }

    public void NotifyChanged()
    {
        if (!OperatingSystem.IsWindows())
        {
            return;
        }
        SendMessageTimeout(
            BroadcastWindow,
            EnvironmentChanged,
            IntPtr.Zero,
            "Environment",
            AbortIfHung,
            5000,
            out _);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr SendMessageTimeout(
        IntPtr window,
        uint message,
        IntPtr word,
        string parameter,
        uint flags,
        uint timeout,
        out IntPtr result);
}
