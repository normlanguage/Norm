import { chmodSync, cpSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { packageVsix } from './vsce-package.mjs';

const targetsPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  '..',
  'compiler',
  'release-targets.json',
);
export const releaseTargets = JSON.parse(readFileSync(targetsPath, 'utf8'));
if (
  !Array.isArray(releaseTargets) ||
  releaseTargets.length === 0 ||
  releaseTargets.some(
    ({ target, launcher }) =>
      typeof target !== 'string' || !target || typeof launcher !== 'string' || !launcher,
  ) ||
  new Set(releaseTargets.map(({ target }) => target)).size !== releaseTargets.length
) {
  throw new Error(`Invalid release target manifest: ${targetsPath}`);
}
const targetLaunchers = new Map(releaseTargets.map(({ target, launcher }) => [target, launcher]));

export function releaseVersion(value) {
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)) {
    throw new Error(`Invalid release version: ${value}`);
  }
  return value;
}

export function targetLauncher(target) {
  const launcher = targetLaunchers.get(target);
  if (!launcher) throw new Error(`Unsupported release target: ${target}`);
  return join('norm', launcher);
}

export function targetRuntimeJava(target) {
  if (!targetLaunchers.has(target)) throw new Error(`Unsupported release target: ${target}`);
  return join(
    'norm',
    'runtime',
    'bin',
    target === 'win32-x64' ? 'java.exe' : 'java',
  );
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
  return releaseTargets.map(({ target, launcher }) => {
    const source = join(resolve(binaries), `runtime-${target}`, 'norm');
    const destination = join(bin, target, 'norm');
    mkdirSync(dirname(destination), { recursive: true });
    cpSync(source, destination, { recursive: true, errorOnExist: true });
    chmodSync(join(destination, launcher), 0o755);
    if (target !== 'win32-x64') {
      chmodSync(join(bin, target, targetRuntimeJava(target)), 0o755);
    }
    return destination;
  });
}

function packageExtension(version, binaries, output) {
  const extensionRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  stageCliBundle(binaries, extensionRoot);
  const destination = resolve(output);
  mkdirSync(dirname(destination), { recursive: true });
  packageVsix({
    extensionRoot,
    destination,
    excludedDirectory: 'server',
    version: releaseVersion(version),
  });
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 5) {
    throw new Error('Usage: release-package.mjs <version> <binaries> <output>');
  }
  packageExtension(...process.argv.slice(2));
}
