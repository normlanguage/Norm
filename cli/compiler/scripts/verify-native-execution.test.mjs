import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync, existsSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, basename } from 'node:path';
import { createHash } from 'node:crypto';
import { verifyNativeExecution } from './verify-native-execution.mjs';

test('execution receipt requires three real isolated processes and matching output', async () => {
  const root = mkdtempSync(resolve(tmpdir(), 'norm-native-execution-test-'));
  try {
    const source = resolve(root, 'sample.norm');
    writeFileSync(source, 'Void main() {}');
    const bytes = readFileSync(process.execPath);
    const hash = createHash('sha256').update(bytes).digest('hex');
    const size = { sha256: hash, runtimeFiles: [{ path: basename(process.execPath), bytes: bytes.length, sha256: hash }] };
    for (const expected of ['isolated\n', 'incorrect']) {
      const directory = resolve(root, expected === 'incorrect' ? 'failure' : 'success');
      mkdirSync(directory);
      const evidence = { directory, size };
      const operation = verifyNativeExecution(process.execPath, source, evidence, expected,
        ['-e', 'if(process.env.PATH !== "") process.exit(2); console.log("isolated")']);
      if (expected === 'incorrect') {
        await assert.rejects(operation, /output/);
        assert.equal(existsSync(resolve(directory, 'execution-verification.json')), false);
        const retained = readdirSync(directory).filter(name => name.startsWith('execution-') && !name.endsWith('.json'));
        assert.equal(retained.length, 1);
        assert.equal(createHash('sha256').update(readFileSync(resolve(directory, retained[0], basename(process.execPath)))).digest('hex'), hash);
        const failure = JSON.parse(readFileSync(resolve(directory, 'execution-failure.json')));
        assert.equal(failure.attempts[0].stdout, 'isolated\n');
        assert.equal(failure.attempts[0].status, 0);
      } else {
        await operation;
        const receipt = JSON.parse(readFileSync(resolve(directory, 'execution-verification.json')));
        assert.equal(receipt.executableSha256, hash);
        assert.equal(receipt.sourceSha256, createHash('sha256').update(readFileSync(source)).digest('hex'));
        assert.deepEqual(receipt.runs.map(run => run.iteration), [1, 2, 3]);
        assert.ok(receipt.runs.every(run => run.output === expected));
        assert.deepEqual(readdirSync(directory), ['execution-verification.json']);
      }
    }
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
