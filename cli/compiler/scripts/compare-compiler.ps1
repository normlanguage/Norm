param(
  [Parameter(Mandatory)][string]$BaselineLibraries,
  [Parameter(Mandatory)][string]$CandidateLibraries,
  [Parameter(Mandatory)][string]$OutputDirectory,
  [string]$JavaHome = $env:JAVA_HOME,
  [ValidateRange(1,20)][int]$Forks = 3
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$java = Join-Path $JavaHome 'bin/java.exe'
$benchmark = Join-Path $root 'cli/compiler/src/test/java/dev/w0fv1/norm/testing/CompilerBenchmark.java'
$workload = Join-Path $PSScriptRoot 'fixtures/compiler-benchmark.norm'
$baseline = (Resolve-Path -LiteralPath $BaselineLibraries).Path
$candidate = (Resolve-Path -LiteralPath $CandidateLibraries).Path
$output = (New-Item -ItemType Directory -Force $OutputDirectory).FullName
$results = [Collections.Generic.List[object]]::new()
for ($fork = 0; $fork -lt $Forks; $fork++) {
  $order = if($fork % 2 -eq 0){@('baseline','candidate')}else{@('candidate','baseline')}
  foreach($label in $order) {
    $libraries = if($label -eq 'baseline'){$baseline}else{$candidate}
    $stem = Join-Path $output ($label + '-' + $fork)
    $arguments = @('--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED', '-Xms256m', '-Xmx1g', '-cp', ('"' + (Join-Path $libraries '*') + '"'), ('"' + $benchmark + '"'), ('"' + $workload + '"'))
    $process = Start-Process -FilePath $java -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput ($stem+'.json') -RedirectStandardError ($stem+'.log')
    $peak = 0L
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while(!$process.WaitForExit(100)) {
      $process.Refresh()
      $peak = [Math]::Max($peak, $process.PeakWorkingSet64)
      if($watch.Elapsed.TotalMinutes -gt 5) { $process.Kill($true); throw "Benchmark timed out: $stem" }
    }
    $process.WaitForExit()
    if($process.ExitCode -ne 0){throw "Benchmark failed: $stem.log"}
    $result = Get-Content ($stem+'.json') -Raw | ConvertFrom-Json
    $results.Add([pscustomobject]@{label=$label;fork=$fork;observedPeakWorkingSetBytes=$peak;wallSeconds=$watch.Elapsed.TotalSeconds;measurement=$result})
    $results | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'results.json')
  }
}
Write-Output (Join-Path $output 'results.json')
