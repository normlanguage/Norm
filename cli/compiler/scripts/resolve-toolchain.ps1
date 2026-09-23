param(
  [string]$NormHome = (Join-Path $PSScriptRoot '../../..'),
  [switch]$BuildOutput
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $NormHome).Path
[xml]$pom = Get-Content -LiteralPath (Join-Path $root 'pom.xml') -Raw
$sourceRevision = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='properties']/*[local-name()='revision']").InnerText
if ($sourceRevision -notmatch '^\d+\.\d+\.\d+(-SNAPSHOT)?$') { throw 'Root pom.xml has no semantic revision.' }
$selection = Join-Path $root '.tmp/active-toolchain.json'
$manifest = $null
if (!$BuildOutput -and (Test-Path -LiteralPath $selection -PathType Leaf)) {
  $manifest = Get-Content -LiteralPath $selection -Raw | ConvertFrom-Json
  $toolchainHome = (Resolve-Path -LiteralPath $manifest.Home).Path
  $version = $manifest.Version
  if ($manifest.SourceRevision -ne $sourceRevision) { throw 'Selected toolchain source revision does not match this checkout.' }
  $expected = $manifest.CompilerSha256
} else {
  $metadata = Join-Path $root 'cli/compiler/target/maven-archiver/pom.properties'
  $properties = ConvertFrom-StringData (Get-Content -LiteralPath $metadata -Raw)
  if ($properties.groupId -ne 'dev.w0fv1.norm' -or $properties.artifactId -ne 'compiler' -or
      $properties.version -notmatch '^\d+\.\d+\.\d+(-SNAPSHOT)?$') {
    throw "Invalid Maven compiler package metadata: $metadata"
  }
  $version = $properties.version
  $toolchainHome = Join-Path $root 'cli/compiler/target/norm-runtime'
  $built = Join-Path $root ('cli/compiler/target/compiler-' + $version + '.jar')
  if (!(Test-Path -LiteralPath $built -PathType Leaf)) { throw "Missing compiler build output: $built" }
  $expected = (Get-FileHash -LiteralPath $built -Algorithm SHA256).Hash
}
$compiler = Join-Path $toolchainHome ('lib/compiler-' + $version + '.jar')
$java = Join-Path $toolchainHome 'runtime/bin/java.exe'
$launcher = Join-Path $toolchainHome 'bin/norm.bat'
foreach ($file in @($compiler, $java, $launcher)) {
  if (!(Test-Path -LiteralPath $file -PathType Leaf)) {
    throw "Missing toolchain file: $file. Build the Maven compiler package and select the toolchain again."
  }
}
$actual = (Get-FileHash -LiteralPath $compiler -Algorithm SHA256).Hash
if ($actual -ne $expected) { throw 'Compiler SHA256 differs from the selected build output.' }
if ($manifest) {
  if ((Get-FileHash -LiteralPath (Join-Path $toolchainHome 'runtime/lib/modules') -Algorithm SHA256).Hash -ne $manifest.RuntimeModulesSha256) { throw 'Selected Java runtime modules changed.' }
  $actualLibraries = @(Get-ChildItem -LiteralPath (Join-Path $toolchainHome 'lib') -File -Filter '*.jar' | Select-Object -ExpandProperty Name | Sort-Object)
  $expectedLibraries = @($manifest.Libraries | Select-Object -ExpandProperty Name | Sort-Object)
  if (Compare-Object $actualLibraries $expectedLibraries) { throw 'Selected toolchain library set changed.' }
  foreach ($library in $manifest.Libraries) {
    if ([IO.Path]::GetFileName($library.Name) -ne $library.Name) { throw 'Invalid toolchain library name.' }
    $path = Join-Path $toolchainHome ('lib/' + $library.Name)
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $library.Sha256) {
      throw "Selected toolchain library SHA256 changed: $($library.Name)"
    }
  }
  if ((Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash -ne $manifest.JavaSha256) { throw 'Selected Java executable changed.' }
  if ((Get-FileHash -LiteralPath $launcher -Algorithm SHA256).Hash -ne $manifest.LauncherSha256) { throw 'Selected compiler launcher changed.' }
}
[pscustomobject]@{
  Home = $toolchainHome
  Version = $version
  SourceRevision = $sourceRevision
  Compiler = $compiler
  CompilerSha256 = $actual
  ClassPath = (Join-Path $toolchainHome 'lib/*')
  Java = $java
  Launcher = $launcher
  Selection = $(if ($manifest) { $selection } else { 'build-output' })
}
