import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const packageTrees = new Set(['bin', 'server']);

export function packageIgnore(baseIgnore, excludedDirectory) {
  if (excludedDirectory === undefined) return baseIgnore;
  if (!packageTrees.has(excludedDirectory)) {
    throw new Error(`Invalid excluded package directory: ${excludedDirectory}`);
  }
  return `${baseIgnore}\n${excludedDirectory}/**\n`;
}

export function packageVsix({ extensionRoot, destination, excludedDirectory, version }) {
  const packagingRoot = mkdtempSync(join(tmpdir(), 'norm-vscode-package-'));
  const ignoreFile = join(packagingRoot, '.vscodeignore');
  writeFileSync(
    ignoreFile,
    packageIgnore(readFileSync(join(extensionRoot, '.vscodeignore'), 'utf8'), excludedDirectory),
  );
  const vsce = join(
    extensionRoot,
    'node_modules',
    '.bin',
    process.platform === 'win32' ? 'vsce.cmd' : 'vsce',
  );
  const args = ['package'];
  if (version) args.push(version, '--no-update-package-json');
  args.push('--no-dependencies', '--skip-license', '--ignoreFile', ignoreFile, '--out', destination);
  const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : vsce;
  try {
    const result = spawnSync(
      command,
      process.platform === 'win32' ? ['/d', '/c', 'call', vsce, ...args] : args,
      { cwd: extensionRoot, stdio: 'inherit' },
    );
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`VSIX packaging exited with ${result.status}`);
  } finally {
    rmSync(packagingRoot, { recursive: true, force: true });
  }
}
