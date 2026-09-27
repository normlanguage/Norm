import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, chmodSync, symlinkSync, unlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { compareRuntimeTrees } from './verify-runtime-tree.mjs';

test('a delivered runtime must preserve files, modes, and symbolic links', async () => {
  const root = mkdtempSync(join(tmpdir(), 'norm-runtime-verify-'));
  try {
    const source = join(root, 'source');
    const delivered = join(root, 'delivered');
    for (const tree of [source, delivered]) {
      mkdirSync(join(tree, 'bin'), { recursive: true });
      mkdirSync(join(tree, 'lib'));
      writeFileSync(join(tree, 'bin', 'norm'), '#!/bin/sh\n');
      writeFileSync(join(tree, 'lib', 'compiler.jar'), 'compiler');
      if (process.platform !== 'win32') {
        chmodSync(join(tree, 'bin', 'norm'), 0o755);
        symlinkSync('compiler.jar', join(tree, 'lib', 'alias.jar'));
      }
    }

    const report = await compareRuntimeTrees(source, delivered);
    assert.equal(report.entries, process.platform === 'win32' ? 4 : 5);
    assert.equal(report.regularFiles, 2);
    assert.equal(report.symbolicLinks, process.platform === 'win32' ? 0 : 1);

    writeFileSync(join(delivered, 'lib', 'compiler.jar'), 'changed');
    await assert.rejects(compareRuntimeTrees(source, delivered), /compiler\.jar.*SHA-256/);
    writeFileSync(join(delivered, 'lib', 'compiler.jar'), 'compiler');

    if (process.platform !== 'win32') {
      chmodSync(join(delivered, 'bin', 'norm'), 0o644);
      await assert.rejects(compareRuntimeTrees(source, delivered), /bin\/norm.*mode/);
      chmodSync(join(delivered, 'bin', 'norm'), 0o755);
      unlinkSync(join(delivered, 'lib', 'alias.jar'));
      symlinkSync('missing.jar', join(delivered, 'lib', 'alias.jar'));
      await assert.rejects(compareRuntimeTrees(source, delivered), /alias\.jar.*target/);
    }
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
