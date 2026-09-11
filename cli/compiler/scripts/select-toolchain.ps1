param(
  [string]$NormHome = (Join-Path $PSScriptRoot '../../..'),
  [string]$BuildRoot = $NormHome,
  [string]$SourceManifest = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $NormHome).Path
$build = (Resolve-Path -LiteralPath $BuildRoot).Path
$toolchain = & (Join-Path $PSScriptRoot 'resolve-toolchain.ps1') -NormHome $build -BuildOutput
$libraries = @(Get-ChildItem -LiteralPath (Join-Path $toolchain.Home 'lib') -File -Filter '*.jar' | Sort-Object Name | ForEach-Object {
  [pscustomobject]@{ Name = $_.Name; Sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})
$javaHash = (Get-FileHash -LiteralPath $toolchain.Java -Algorithm SHA256).Hash
$launcherHash = (Get-FileHash -LiteralPath $toolchain.Launcher -Algorithm SHA256).Hash
$moduleHash = (Get-FileHash -LiteralPath (Join-Path $toolchain.Home 'runtime/lib/modules') -Algorithm SHA256).Hash
$identityText = ($libraries | ConvertTo-Json -Depth 4 -Compress) + $javaHash + $launcherHash + $moduleHash
$hasher = [Security.Cryptography.SHA256]::Create()
try { $identity = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($identityText))).Replace('-', '') } finally { $hasher.Dispose() }
$directory = Join-Path $env:LOCALAPPDATA ('Programs/Norm/toolchains/' + $toolchain.Version + '-' + $identity.Substring(0, 16).ToLowerInvariant())
if (!(Test-Path -LiteralPath $directory)) {
  New-Item -ItemType Directory -Force (Split-Path $directory) | Out-Null
  Copy-Item -LiteralPath $toolchain.Home -Destination $directory -Recurse
}
$manifest = [ordered]@{
  Version = $toolchain.Version
  Home = $directory
  CompilerSha256 = $toolchain.CompilerSha256
  ToolchainSha256 = $identity
  JavaSha256 = $javaHash
  LauncherSha256 = $launcherHash
  RuntimeModulesSha256 = $moduleHash
  Libraries = $libraries
  BuildRoot = $build
  SelectedAt = [DateTime]::UtcNow.ToString('o')
}
if ($SourceManifest) {
  $manifest.SourceManifest = (Resolve-Path -LiteralPath $SourceManifest).Path
  $manifest.SourceManifestSha256 = (Get-FileHash -LiteralPath $SourceManifest -Algorithm SHA256).Hash
}
$selection = Join-Path $root '.tmp/active-toolchain.json'
New-Item -ItemType Directory -Force (Split-Path $selection) | Out-Null
$previous = if (Test-Path -LiteralPath $selection) { [IO.File]::ReadAllText($selection) } else { $null }
$temporary = $selection + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
try {
  [IO.File]::WriteAllText($temporary, ($manifest | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
  Move-Item -LiteralPath $temporary -Destination $selection -Force
  & (Join-Path $PSScriptRoot 'resolve-toolchain.ps1') -NormHome $root
} catch {
  if ($null -ne $previous) { [IO.File]::WriteAllText($selection, $previous, [Text.UTF8Encoding]::new($false)) }
  else { Remove-Item -LiteralPath $selection -ErrorAction SilentlyContinue }
  throw
} finally {
  if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}
