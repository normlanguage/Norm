import { chmodSync, copyFileSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const targetsPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  '..',
  'release-targets.json',
);
export const releaseTargets = JSON.parse(readFileSync(targetsPath, 'utf8'));
if (
  !Array.isArray(releaseTargets) ||
  releaseTargets.length === 0 ||
  releaseTargets.some(
    ({ target, executable }) =>
      typeof target !== 'string' || !target || typeof executable !== 'string' || !executable,
  ) ||
  new Set(releaseTargets.map(({ target }) => target)).size !== releaseTargets.length
) {
  throw new Error(`Invalid release target manifest: ${targetsPath}`);
}
const targetExecutables = new Map(
  releaseTargets.map(({ target, executable }) => [target, executable]),
);

export function releaseVersion(value) {
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)) {
    throw new Error(`Invalid release version: ${value}`);
  }
  return value;
}

export function targetExecutable(target) {
  const executable = targetExecutables.get(target);
  if (!executable) throw new Error(`Unsupported release target: ${target}`);
  return executable;
}

export function verifyCliVersion(binary, version) {
  const expected = `norm ${releaseVersion(version)}`;
  const commandScript = process.platform === 'win32' && /\.(?:bat|cmd)$/i.test(binary);
  const result = spawnSync(
    commandScript ? (process.env.ComSpec ?? 'cmd.exe') : binary,
    commandScript ? ['/d', '/c', 'call', binary, '--version'] : ['--version'],
    { encoding: 'utf8' },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`Norm CLI version check failed with exit code ${result.status}`);
  }
  const actual = result.stdout.trim();
  if (actual !== expected) {
    throw new Error(`Norm CLI version mismatch: expected "${expected}", received "${actual}"`);
  }
}

export function stageCliBundle(binaries, extensionRoot) {
  const bin = join(extensionRoot, 'bin');
  rmSync(bin, { recursive: true, force: true });
  return releaseTargets.map(({ target, executable }) => {
    const source = join(resolve(binaries), `native-${target}`, executable);
    const destination = join(bin, target, executable);
    mkdirSync(dirname(destination), { recursive: true });
    copyFileSync(source, destination);
    chmodSync(destination, 0o755);
    return destination;
  });
}

function packageExtension(version, binaries, output) {
  const extensionRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  stageCliBundle(binaries, extensionRoot);
  const destination = resolve(output);
  mkdirSync(dirname(destination), { recursive: true });
  const vsce = join(
    extensionRoot,
    'node_modules',
    '.bin',
    process.platform === 'win32' ? 'vsce.cmd' : 'vsce',
  );
  const args = [
    'package',
    releaseVersion(version),
    '--no-update-package-json',
    '--no-dependencies',
    '--out',
    destination,
  ];
  const commandScript = process.platform === 'win32';
  const result = spawnSync(
    commandScript ? (process.env.ComSpec ?? 'cmd.exe') : vsce,
    commandScript ? ['/d', '/c', 'call', vsce, ...args] : args,
    { cwd: extensionRoot, stdio: 'inherit' },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 5) {
    throw new Error('Usage: release-package.mjs <version> <binaries> <output>');
  }
  packageExtension(...process.argv.slice(2));
}
