namespace Norm.Launcher.Tests;

public sealed class ApplicationDescriptorTest
{
    [Fact]
    public void ReadsCompiledApplicationDescriptor()
    {
        ApplicationDescriptor descriptor = ApplicationDescriptor.Read("""{"formatVersion":2,"entry":"application.bin"}""");
        Assert.Equal("application.bin", descriptor.Entry);
        Assert.Throws<InvalidDataException>(() => ApplicationDescriptor.Read("""{"formatVersion":1,"entry":"source/main.norm"}"""));
    }
}
