import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { createHash } from 'node:crypto';
import { verifyNativeSize } from './verify-native-size.mjs';
import { observeProcess } from './observe-process.mjs';

export async function verifyNativeWeb(repository, build, measure) {
  const directory = mkdtempSync(resolve(tmpdir(), 'norm-native-web-'));
  try {
    const example = readFileSync(resolve(repository,
      'docs/examples/micronaut-single-file/web.norm'), 'utf8');
    const verification = readFileSync(resolve(repository,
      'cli/compiler/scripts/fixtures/native-web-verification.norm'), 'utf8');
    const configuration = 'MicronautConfig(';
    if (example.split(configuration).length !== 2) {
      throw new Error('Native Web verification requires one application configuration');
    }
    const source = resolve(directory, 'web.norm');
    writeFileSync(source,
      'import micronaut.web.Micronaut\nimport micronaut.web.Server\nimport std.core.Exception\n'
      + 'import micronaut.validation.Validated\nimport jakarta.validation.constraints.NotBlank\n'
      + example.replace(configuration,
        `${configuration}\nmicronaut: Micronaut(server: Server(port: 0)),`) + '\n' + verification);
    build(['build', source], undefined, directory);
    const executable = process.platform === 'win32' ? `${source}.exe` : resolve(directory, 'web');
    const evidence = await verifyNativeSize(executable, resolve(repository, 'build/reports/native-size'));
    const runs = [];
    for (let iteration = 1; iteration <= 3; iteration++) {
      const started = performance.now();
      const application = spawn(executable, [], {
        cwd: directory,
        windowsHide: true,
        env: {
          ...process.env,
          PATH: '',
          NORM_CACHE: resolve(directory, 'unused-cache'),
          HTTP_PROXY: 'http://127.0.0.1:1',
          HTTPS_PROXY: 'http://127.0.0.1:1',
        },
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      const observed = observeProcess(application, /Micronaut: (http:\/\/127\.0\.0\.1:\d+)/, 30_000);
      try {
        const address = (await observed.ready)[1];
        const name = `NativeDatabase${iteration}`;
        const response = await fetch(`${address}/hello/${name}`, {
          signal: AbortSignal.timeout(10_000),
        });
        const body = await response.text();
        if (response.status !== 200 || body !== `Hello, ${name}!`) {
          throw new Error(`Native Web database request failed: ${response.status} ${body}\n${observed.diagnostics()}`);
        }
        const validated = await requestText(address, `/native-verification/validate/${name}`);
        if (validated !== `validated ${name}`) throw new Error(`Valid input was rejected: ${validated}`);
        const invalid = await fetch(`${address}/native-verification/validate/%20`, {
          signal: AbortSignal.timeout(10_000),
        });
        const rejection = await invalid.text();
        if (invalid.status !== 400 || !rejection.includes('Native validation rejected blank')) {
          throw new Error(`Bean Validation did not reject blank input: ${invalid.status} ${rejection}`);
        }
        const before = await requestText(address, '/native-verification/count');
        const id = await requestText(address, `/native-verification/create/${name}`);
        if (!/^\d+$/.test(id)) throw new Error(`Saved entity identity is invalid: ${id}`);
        const stored = await requestText(address, `/native-verification/read/${id}`);
        if (stored !== name) throw new Error(`Database read returned ${stored}, expected ${name}`);
        const committed = await requestText(address, '/native-verification/count');
        if (BigInt(committed) !== BigInt(before) + 1n) {
          throw new Error(`Commit count mismatch: ${before} -> ${committed}`);
        }
        const rollback = await requestText(address, `/native-verification/rollback/${name}`);
        if (rollback !== 'rolled back') throw new Error(`Rollback did not throw: ${rollback}`);
        const after = await requestText(address, '/native-verification/count');
        if (after !== committed) throw new Error(`Rolled-back row persisted: ${committed} -> ${after}`);
        const elapsedMs = Math.round(performance.now() - started);
        const measurement = measure ? await measure(address, iteration) : undefined;
        runs.push({ iteration, elapsedMs, checks: ['http', 'dependency-injection', 'bean-validation', 'commit', 'database-read', 'rollback'], measurement });
        console.log(`Native Web/ORM run ${iteration}: ${elapsedMs} ms; commit, database read and rollback passed`);
      } finally {
        if (application.pid !== undefined && application.exitCode === null && application.signalCode === null) {
          const exited = once(application, 'exit');
          application.kill();
          await exited;
        }
      }
    }
    writeFileSync(resolve(evidence.directory, 'web-verification.json'), JSON.stringify({
      schemaVersion: 1,
      executableSha256: evidence.size.sha256,
      sourceSha256: createHash('sha256').update(readFileSync(source)).digest('hex'),
      runs,
    }, null, 2) + '\n');
  } catch (error) {
    console.error(`Native Web failure directory retained: ${directory}`);
    throw error;
  }
  rmSync(directory, { recursive: true, force: true });
}

async function requestText(address, path) {
  const response = await fetch(address + path, { signal: AbortSignal.timeout(10_000) });
  const body = await response.text();
  if (response.status !== 200) {
    throw new Error(`Native Web ${path} failed: ${response.status} ${body}`);
  }
  return body;
}
