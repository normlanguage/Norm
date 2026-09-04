using System.Reflection;

namespace Norm.Launcher;

internal static class BuildVersion
{
    public static string Current
    {
        get
        {
            AssemblyInformationalVersionAttribute? attribute =
                typeof(BuildVersion).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>();
            return attribute?.InformationalVersion.Split('+')[0]
                ?? throw new InvalidOperationException("The launcher version is unavailable");
        }
    }
}
