import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

if (process.argv.length !== 4) {
  throw new Error('Usage: verify-cli.mjs <norm-cli> <version>');
}

const repository = resolve(import.meta.dirname, '..', '..', '..');
const cli = resolve(process.argv[2]);
const version = process.argv[3];
verify(['--version'], `norm ${version}\n`);
if (process.platform === 'win32' && cli.toLowerCase().endsWith('.exe')) {
  const script = `
Add-Type -AssemblyName System.Drawing
$icon = [System.Drawing.Icon]::ExtractAssociatedIcon($env:NORM_ICON_TARGET)
if ($null -eq $icon) { throw 'No executable icon found' }
$bitmap = $icon.ToBitmap()
$brandPixels = 0
for ($x = 0; $x -lt $bitmap.Width; $x++) {
  for ($y = 0; $y -lt $bitmap.Height; $y++) {
    $pixel = $bitmap.GetPixel($x, $y)
    if ([Math]::Abs($pixel.R - 49) -le 2 -and [Math]::Abs($pixel.G - 120) -le 2 -and [Math]::Abs($pixel.B - 198) -le 2) { $brandPixels++ }
  }
}
$bitmap.Dispose()
$icon.Dispose()
if ($brandPixels -lt 400) { throw "Executable icon does not contain the Norm brand mark: $brandPixels matching pixels" }
`;
  const result = spawnSync(
    'powershell.exe',
    ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', script],
    { encoding: 'utf8', env: { ...process.env, NORM_ICON_TARGET: cli } },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`Windows executable icon verification failed: ${result.stderr}`);
  }
}
verify(['run', resolve(repository, 'docs', 'examples', 'hello.norm')], 'Hello from Norm\n');

let count = 0;
for (const group of ['base', 'algorithms', 'class', 'generics', 'stdlib']) {
  const directory = resolve(repository, 'norm', 'tests', group);
  const cases = readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.norm'))
    .map((entry) => entry.name)
    .sort();
  if (!cases.length) throw new Error(`No acceptance programs found in ${directory}`);
  for (const file of cases) {
    verify(['run', resolve(directory, file)]);
    count += 1;
  }
}

console.log(`Norm CLI verified with ${count} acceptance programs.`);

function verify(args, expected) {
  const commandScript = process.platform === 'win32' && /\.(?:bat|cmd)$/i.test(cli);
  const result = spawnSync(
    commandScript ? (process.env.ComSpec ?? 'cmd.exe') : cli,
    commandScript ? ['/d', '/c', 'call', cli, ...args] : args,
    { cwd: repository, encoding: 'utf8' },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${args.join(' ')} exited with ${result.status}: ${result.stderr}`);
  }
  if (result.stderr) {
    throw new Error(`${args.join(' ')} wrote to stderr: ${result.stderr}`);
  }
  const actual = result.stdout.replaceAll('\r\n', '\n');
  if (expected !== undefined && actual !== expected) {
    throw new Error(
      `${args.join(' ')} output mismatch\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`,
    );
  }
}
