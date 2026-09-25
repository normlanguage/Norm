import { spawnSync } from 'node:child_process';
import { chmodSync, closeSync, existsSync, mkdirSync, openSync, readSync, readdirSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { verifiedReleaseAsset } from './release-assets.mjs';

function execute(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) throw new Error(`${command} failed: ${result.error?.message ?? result.stderr}`);
  return result.stdout;
}

export function stagePrivateRuntime(version, assetsDirectory, stage) {
  const { path: archive } = verifiedReleaseAsset(version, 'linux-x64', assetsDirectory);
  const entries = execute('tar', ['-tzf', archive]).trim().split(/\r?\n/);
  if (!entries.length || entries.some(entry => !entry.startsWith('norm/') || entry.split('/').includes('..') || entry.includes('\\'))) {
    throw new Error('Invalid Linux release archive layout');
  }
  execute('tar', ['--same-permissions', '-xzf', archive, '-C', stage]);
  const extracted = join(stage, 'norm');
  if (!existsSync(join(extracted, 'bin', 'norm')) || !existsSync(join(extracted, 'runtime', 'bin', 'java')) || !existsSync(join(extracted, 'lib'))) {
    throw new Error('Incomplete Linux release runtime');
  }
  const runtime = join(stage, 'usr', 'lib', 'normlang');
  mkdirSync(dirname(runtime), { recursive: true });
  renameSync(extracted, runtime);
  const bin = join(stage, 'usr', 'bin');
  mkdirSync(bin, { recursive: true });
  for (const directory of [join(stage, 'usr'), dirname(runtime), bin]) chmodSync(directory, 0o755);
  const wrapper = join(bin, 'norm');
  writeFileSync(wrapper, '#!/bin/sh\nexec /usr/lib/normlang/bin/norm "$@"\n');
  chmodSync(wrapper, 0o755);
  chmodSync(join(runtime, 'bin', 'norm'), 0o755);
  const reportedVersion = execute(join(runtime, 'bin', 'norm'), ['--version']).trim();
  if (reportedVersion !== `norm ${version}`) throw new Error(`Release runtime version mismatch: ${reportedVersion}`);
  return runtime;
}

export function elfFiles(root) {
  const files = [];
  const directories = new Set();
  const visit = directory => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) visit(path);
      else if (entry.isFile()) {
        const descriptor = openSync(path, 'r');
        const signature = Buffer.alloc(4);
        const size = readSync(descriptor, signature, 0, 4, 0);
        closeSync(descriptor);
        if (size === 4 && signature.equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46]))) {
          files.push(path);
          directories.add(dirname(path));
        }
      }
    }
  };
  visit(root);
  return { files, directories: [...directories] };
}
