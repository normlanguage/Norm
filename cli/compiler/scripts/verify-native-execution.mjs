import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, copyFileSync, rmSync, existsSync } from 'node:fs';
import { basename, dirname, resolve, sep } from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

export async function verifyNativeExecution(executable, source, evidence, expectedOutput, args = []) {
  const receipt = resolve(evidence.directory, 'execution-verification.json');
  assert.ok(!existsSync(receipt), 'Execution receipt already exists');
  const delivery = mkdtempSync(resolve(evidence.directory, 'execution-'));
  const attempts = [];
  try {
    const paths = new Set();
    for (const file of evidence.size.runtimeFiles) {
      const target = resolve(delivery, file.path);
      const origin = resolve(dirname(executable), file.path);
      assert.ok(target.startsWith(delivery + sep), 'Runtime path escapes delivery');
      assert.ok(origin.startsWith(resolve(dirname(executable)) + sep), 'Runtime path escapes source');
      assert.ok(!paths.has(target), 'Duplicate runtime path');
      paths.add(target);
      const bytes = readFileSync(origin);
      assert.equal(bytes.length, file.bytes, 'Runtime size mismatch');
      assert.equal(createHash('sha256').update(bytes).digest('hex'), file.sha256, 'Runtime hash mismatch');
      mkdirSync(dirname(target), { recursive: true });
      copyFileSync(origin, target);
    }
    const application = resolve(delivery, basename(executable));
    assert.ok(paths.has(application), 'Runtime delivery omits executable');
    assert.equal(createHash('sha256').update(readFileSync(application)).digest('hex'), evidence.size.sha256);
    const runs = [];
    for (let iteration = 1; iteration <= 3; iteration++) {
      const started = performance.now();
      const result = spawnSync(application, args, {
        cwd: delivery, windowsHide: true, encoding: 'utf8', timeout: 30000, input: '',
        env: { ...process.env, PATH: '', NORM_CACHE: resolve(delivery, 'unused-cache'),
          HOME: resolve(delivery, 'unused-home'), USERPROFILE: resolve(delivery, 'unused-profile'),
          LOCALAPPDATA: resolve(delivery, 'unused-local'), APPDATA: resolve(delivery, 'unused-roaming'),
          HTTP_PROXY: 'http://127.0.0.1:1', HTTPS_PROXY: 'http://127.0.0.1:1' },
      });
      attempts.push({ iteration, status: result.status, signal: result.signal,
        stdout: result.stdout, stderr: result.stderr,
        elapsedMs: Math.round(performance.now() - started) });
      if (result.error) throw result.error;
      assert.equal(result.status, 0, result.stderr || result.stdout);
      assert.equal(result.stderr, '', 'Unexpected execution stderr');
      const output = result.stdout.replaceAll('\r\n', '\n');
      assert.equal(output, expectedOutput, 'Unexpected execution output');
      runs.push({ iteration, elapsedMs: Math.round(performance.now() - started), output });
    }
    writeFileSync(receipt, JSON.stringify({
      schemaVersion: 1, executableSha256: evidence.size.sha256,
      sourceSha256: createHash('sha256').update(readFileSync(source)).digest('hex'), runs,
    }, null, 2) + '\n');
    console.log(`${basename(executable)}: three isolated native executions passed`);
  } catch (failure) {
    writeFileSync(resolve(evidence.directory, 'execution-failure.json'), JSON.stringify({
      schemaVersion: 1, executableSha256: evidence.size.sha256,
      delivery: basename(delivery), args, message: failure.message, attempts,
    }, null, 2) + '\n');
    console.error(`Native execution failure retained: ${delivery}`);
    throw failure;
  }
  rmSync(delivery, { recursive: true, force: true });
  return evidence;
}
