$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('norm-toolchain-contract-' + [guid]::NewGuid().ToString('N'))
$distribution = Join-Path $fixture 'cli/compiler/build/install/norm'
$built = Join-Path $fixture 'cli/compiler/build/libs/compiler-0.22.1.jar'
$compiler = Join-Path $distribution 'lib/compiler-0.22.1.jar'
$java = Join-Path $distribution 'runtime/bin/java.exe'
$launcher = Join-Path $distribution 'bin/norm.bat'
$modules = Join-Path $distribution 'runtime/lib/modules'
$resolver = Join-Path $PSScriptRoot 'resolve-toolchain.ps1'
$checks = [Collections.Generic.List[string]]::new()
function Assert-Rejected([scriptblock]$Action, [string]$Name) {
  $rejected = $false
  try { & $Action | Out-Null } catch { $rejected = $true }
  if(!$rejected){throw "Expected rejection: $Name"}
  $checks.Add($Name)
}
try {
  foreach($file in @($built,$compiler,$java,$launcher,$modules)){
    New-Item -ItemType Directory -Force (Split-Path $file) | Out-Null
    [IO.File]::WriteAllText($file,'fixture')
  }
  Set-Content (Join-Path $fixture 'gradle.properties') 'normVersion=0.22.1'
  $resolved = & $resolver -NormHome $fixture
  if($resolved.CompilerSha256 -ne (Get-FileHash $built).Hash){throw 'Build compiler identity was not returned'}
  $checks.Add('matching-build-output')
  [IO.File]::WriteAllText($compiler,'different')
  Assert-Rejected { & $resolver -NormHome $fixture } 'same-version-different-compiler'
  [IO.File]::WriteAllText($compiler,'fixture')
  $manifest = [ordered]@{
    Version='0.22.1';Home=$distribution;CompilerSha256=(Get-FileHash $compiler).Hash
    JavaSha256=(Get-FileHash $java).Hash;LauncherSha256=(Get-FileHash $launcher).Hash
    RuntimeModulesSha256=(Get-FileHash $modules).Hash
    Libraries=@([pscustomobject]@{Name='compiler-0.22.1.jar';Sha256=(Get-FileHash $compiler).Hash})
  }
  New-Item -ItemType Directory (Join-Path $fixture '.tmp') | Out-Null
  $manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $fixture '.tmp/active-toolchain.json')
  & $resolver -NormHome $fixture | Out-Null
  $checks.Add('matching-selected-toolchain')
  [IO.File]::WriteAllText((Join-Path $distribution 'lib/extra.jar'),'extra')
  Assert-Rejected { & $resolver -NormHome $fixture } 'additional-library'
  Remove-Item (Join-Path $distribution 'lib/extra.jar')
  foreach($file in @($compiler,$java,$launcher,$modules)){
    [IO.File]::WriteAllText($file,'tampered')
    Assert-Rejected { & $resolver -NormHome $fixture } ('tampered-' + [IO.Path]::GetFileName($file))
    [IO.File]::WriteAllText($file,'fixture')
  }
  [pscustomobject]@{passed=$true;checks=$checks} | ConvertTo-Json -Depth 4
} finally {
  if(Test-Path $fixture){Remove-Item -LiteralPath $fixture -Recurse -Force}
}
