param(
    [Parameter(Mandatory)] [string] $Source,
    [Parameter(Mandatory)] [string] $Output
)

$compiler = Get-Command rc.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if ($null -eq $compiler) {
    $sdkRoot = if ($env:WindowsSdkDir) { $env:WindowsSdkDir } else { 'C:\Program Files (x86)\Windows Kits\10' }
    $compiler = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'bin') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'x64\rc.exe' } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
}
if ($null -eq $compiler) {
    throw 'Windows SDK resource compiler rc.exe was not found'
}

New-Item -ItemType Directory -Path (Split-Path $Output) -Force | Out-Null
& $compiler /nologo "/fo$Output" $Source
if ($LASTEXITCODE -ne 0) {
    throw "rc.exe failed with exit code $LASTEXITCODE"
}
