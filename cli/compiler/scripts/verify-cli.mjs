import { existsSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
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

const bindingDirectory = mkdtempSync(resolve(tmpdir(), 'norm-java-binding-'));
try {
  const bindingSource = resolve(bindingDirectory, 'binding.norm');
  writeFileSync(
    bindingSource,
    `package release.binding

import commons.lang.stringUtilsReverse

Module module() {
  return module(
    name: "release.binding",
    version: 1,
    dependencies: [
      dependency(repository: "github", name: "commons.lang", version: 1)
    ]
  )
}

Void main() {
  printLine(stringUtilsReverse("Norm") ?? "")
}
`,
  );
  verify(['run', bindingSource], 'mroN\n', bindingDirectory);
  if (process.platform === 'win32' && cli.toLowerCase().endsWith('.exe')) {
    verify(['build', bindingSource], undefined, bindingDirectory);
    const application = `${bindingSource}.exe`;
    if (!existsSync(application)) {
      throw new Error(`Application executable was not created: ${application}`);
    }
    const offlineRoot = resolve(bindingDirectory, 'offline');
    const result = spawnSync(application, [], {
      cwd: bindingDirectory,
      encoding: 'utf8',
      env: {
        ...process.env,
        USERPROFILE: resolve(offlineRoot, 'profile'),
        LOCALAPPDATA: resolve(offlineRoot, 'local'),
        APPDATA: resolve(offlineRoot, 'roaming'),
        HTTP_PROXY: 'http://127.0.0.1:1',
        HTTPS_PROXY: 'http://127.0.0.1:1',
      },
    });
    if (result.error) throw result.error;
    if (
      result.status !== 0
      || result.stderr
      || result.stdout.replaceAll('\r\n', '\n') !== 'mroN\n'
    ) {
      throw new Error(`Built application verification failed: ${result.stderr || result.stdout}`);
    }
  }
} finally {
  rmSync(bindingDirectory, { recursive: true, force: true });
}

let count = 0;
for (const group of ['base', 'algorithms', 'class', 'generics', 'stdlib']) {
  const directory = resolve(repository, 'norm', 'tests', group);
  const cases = readdirSync(directory, { recursive: true })
    .filter((path) => path.endsWith('.norm'))
    .sort();
  if (!cases.length) throw new Error(`No acceptance programs found in ${directory}`);
  for (const file of cases) {
    const path = resolve(directory, file);
    verify(['run', path], undefined, dirname(path));
    count += 1;
  }
}

console.log(`Norm CLI verified with ${count} acceptance programs.`);

function verify(args, expected, workingDirectory = repository) {
  const commandScript = process.platform === 'win32' && /\.(?:bat|cmd)$/i.test(cli);
  const result = spawnSync(
    commandScript ? (process.env.ComSpec ?? 'cmd.exe') : cli,
    commandScript ? ['/d', '/c', 'call', cli, ...args] : args,
    { cwd: workingDirectory, encoding: 'utf8' },
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
