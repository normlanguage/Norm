import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';
import { agentTasks, moduleSource } from './fixtures/agent-tasks.mjs';

class BenchmarkInfrastructureError extends Error {}

export function prepareTask(id, target) {
  const task = agentTasks.find(task => task.id === id);
  assert.ok(task, `unknown task: ${id}`);
  const directory = resolve(target);
  assert.equal(existsSync(directory), false, 'submission directory already exists');
  mkdirSync(join(directory, 'app'), { recursive: true });
  writeFileSync(join(directory, 'app/module.norm'), moduleSource);
  for (const [file, source] of Object.entries(task.files)) {
    mkdirSync(dirname(join(directory, 'app', file)), { recursive: true });
    writeFileSync(join(directory, 'app', file), source);
  }
  writeFileSync(join(directory, 'TASK.md'), `${task.prompt}\n`);
  return { id, prompt: task.prompt, directory };
}

export function verifyTask(id, submission, launcher, reportPath) {
  const task = agentTasks.find(task => task.id === id);
  assert.ok(task, `unknown task: ${id}`);
  assert.ok(Array.isArray(launcher) && launcher.length > 0 && launcher.every(item => typeof item === 'string' && item.length > 0), 'launcher must be a nonempty argv array');
  const reportFile = resolve(reportPath);
  assert.equal(existsSync(reportFile), false, 'report already exists');
  mkdirSync(dirname(reportFile), { recursive: true });
  const evidence = mkdtempSync(join(dirname(reportFile), `${id}-evidence-`));
  const original = join(evidence, 'submission', 'app');
  cpSync(join(resolve(submission), 'app'), original, { recursive: true, dereference: true });
  const report = { task: id, kind: 'task-acceptance', outcome: 'invalid', passed: false, launcher: [...launcher], evidence, inputs: [], commands: [], failures: [] };
  for (const file of readdirSync(original, { recursive: true, withFileTypes: true })) {
    if (!file.isFile()) continue;
    const path = join(file.parentPath, file.name);
    report.inputs.push({ path, sha256: createHash('sha256').update(readFileSync(path)).digest('hex') });
  }
  const run = args => {
    const start = performance.now();
    const result = spawnSync(launcher[0], [...launcher.slice(1), ...args], { encoding: 'utf8', timeout: 120000, windowsHide: true, maxBuffer: 16 * 1024 * 1024 });
    const entry = { args, exitCode: result.status, durationMs: performance.now() - start, stdoutBytes: Buffer.byteLength(result.stdout ?? ''), stderrBytes: Buffer.byteLength(result.stderr ?? ''), stdout: result.stdout, stderr: result.stderr };
    report.commands.push(entry);
    try {
      if (result.error) throw result.error;
      const output = JSON.parse(result.stdout);
      assert.equal(output.schemaVersion, 1, 'unsupported CLI protocol');
      assert.equal(output.exitCode, result.status, 'CLI result and process exit disagree');
      assert.equal(output.command, args[0], 'unexpected CLI command result');
      assert.equal(typeof output.status, 'string', 'missing CLI status');
      assert.notEqual(output.status, 'internal_error', 'compiler infrastructure failed');
      return output;
    } catch (failure) {
      throw new BenchmarkInfrastructureError(failure.message, { cause: failure });
    }
  };
  try {
    assert.equal(readFileSync(join(original, 'module.norm'), 'utf8'), moduleSource, 'task module configuration must remain unchanged');
    if (task.mutants) {
      assert.equal(readFileSync(join(original, 'api.norm'), 'utf8'), task.files['api.norm'], 'test task must preserve production source');
      const tests = run(['test', original, '--format', 'json']);
      assert.equal(tests.status, 'success', 'submission tests must pass and must exist');
      const query = run(['query', original, '--search', 'clamp']);
      const selected = query.query.symbols.items.find(item => item.name === 'clamp');
      assert.ok(selected, 'clamp must remain present');
      const context = run(['query', original, selected.selector, '--tests']);
      assert.ok(context.query.context.tests.total > 0, 'tests must associate with clamp');
      for (const [index, source] of task.mutants.entries()) {
        const mutant = join(evidence, `mutant-${index}`, 'app');
        cpSync(original, mutant, { recursive: true });
        writeFileSync(join(mutant, 'api.norm'), source);
        assert.equal(run(['test', mutant, '--format', 'json']).status, 'test_failure', `submission tests must reject behavioral mutant ${index}`);
      }
    }
    const acceptance = join(evidence, 'acceptance', 'app');
    cpSync(original, acceptance, { recursive: true });
    const acceptanceFile = join(acceptance, 'tests/agent_acceptance.norm');
    assert.equal(existsSync(acceptanceFile), false, 'reserved acceptance file already exists');
    mkdirSync(dirname(acceptanceFile), { recursive: true });
    writeFileSync(acceptanceFile, task.acceptance);
    const accepted = run(['test', acceptance, '--filter', 'app.acceptance', '--format', 'json']);
    assert.equal(accepted.status, 'success', 'independent behavior acceptance failed');
    assert.equal(accepted.tests.found, 1, 'independent acceptance was not discovered exactly once');
    for (const absent of task.absentSymbols ?? []) {
      const symbols = run(['query', original, '--search', absent]);
      assert.equal(symbols.query.symbols.items.some(item => item.name === absent), false, 'obsolete declaration remains');
      assert.equal(symbols.query.symbols.hasMore, false, 'symbol check must not be truncated');
    }
    if (task.rejectedCall) {
      const incompatible = join(evidence, 'obsolete-call', 'app');
      cpSync(original, incompatible, { recursive: true });
      writeFileSync(join(incompatible, 'obsolete.norm'), task.rejectedCall);
      assert.equal(run(['check', incompatible, '--format', 'json']).status, 'compilation_error', 'old call must stop compiling');
    }
    report.passed = true;
    report.outcome = 'passed';
  } catch (failure) {
    report.outcome = failure instanceof BenchmarkInfrastructureError ? 'invalid' : 'failed';
    report.failures.push(failure.message);
  }
  writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [action, id, directory, launcherPath, reportPath] = process.argv.slice(2);
  if (action === 'prepare' && id && directory && !launcherPath) {
    console.log(JSON.stringify(prepareTask(id, directory)));
  } else if (action === 'verify' && id && directory && launcherPath && reportPath) {
    const report = verifyTask(id, directory, JSON.parse(readFileSync(launcherPath, 'utf8')), reportPath);
    console.log(JSON.stringify({ task: id, passed: report.passed, report: resolve(reportPath) }));
    process.exitCode = report.outcome === 'invalid' ? 2 : report.passed ? 0 : 1;
  } else {
    throw new Error('Usage: agent-benchmark.mjs prepare <task> <new-directory> | verify <task> <submission> <launcher-argv.json> <new-report.json>');
  }
}
