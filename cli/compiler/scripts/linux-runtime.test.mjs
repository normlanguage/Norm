import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { stagePrivateRuntime } from './linux-runtime.mjs';

test('private package staging makes a restrictive release root traversable', { skip: process.platform === 'win32' }, () => {
  const root = mkdtempSync(join(tmpdir(), 'norm-linux-runtime-'));
  try {
    const source = join(root, 'source');
    const assets = join(root, 'assets');
    const stage = join(root, 'stage');
    mkdirSync(join(source, 'norm', 'bin'), { recursive: true });
    mkdirSync(join(source, 'norm', 'runtime', 'bin'), { recursive: true });
    mkdirSync(join(source, 'norm', 'lib'), { recursive: true });
    mkdirSync(assets);
    mkdirSync(stage);
    writeFileSync(join(source, 'norm', 'bin', 'norm'), '#!/bin/sh\nprintf "norm 1.2.3\\n"\n');
    chmodSync(join(source, 'norm', 'bin', 'norm'), 0o755);
    writeFileSync(join(source, 'norm', 'runtime', 'bin', 'java'), 'java');
    chmodSync(join(source, 'norm'), 0o700);
    const archive = join(assets, 'norm-v1.2.3-linux-x64.tar.gz');
    const packed = spawnSync('tar', ['-C', source, '-czf', archive, 'norm'], { encoding: 'utf8' });
    assert.equal(packed.status, 0, packed.stderr);
    writeFileSync(join(assets, 'SHA256SUMS'), `${createHash('sha256').update(readFileSync(archive)).digest('hex')}  norm-v1.2.3-linux-x64.tar.gz\n`);
    const runtime = stagePrivateRuntime('1.2.3', assets, stage);
    assert.equal(statSync(runtime).mode & 0o777, 0o755);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
