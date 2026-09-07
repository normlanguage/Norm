import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { verifyNativeSize } from './verify-native-size.mjs';
import { verifyNativeExecution } from './verify-native-execution.mjs';

if (process.argv.length !== 4) throw new Error('Usage: verify-cli.mjs <norm-cli> <version>');
const cli = resolve(process.argv[2]);
const version = process.argv[3];
const repository = resolve(import.meta.dirname, '../../..');
const directory = mkdtempSync(resolve(tmpdir(), 'norm-cli-acceptance-'));
try {
  assert.equal(run(cli, ['--version']).trim(), `norm ${version}`);
  const source = resolve(directory, 'application.norm');
  copyFileSync(resolve(import.meta.dirname, 'fixtures/hello.norm'), source);
  assert.equal(run(cli, ['run', source]), 'Hello from Norm\n');
  const classes = resolve(directory, 'classes');
  mkdirSync(classes);
  run('javac', ['-d', classes, resolve(import.meta.dirname, 'fixtures/BindingApi.java')]);
  const module = resolve(directory, 'dependencies/fixture');
  mkdirSync(module, { recursive: true });
  const jar = resolve(module, 'fixture.jar');
  run('jar', ['--create', '--date=2020-01-01T00:00:00Z', '--file', jar, '-C', classes, '.']);
  const digest = createHash('sha256').update(readFileSync(jar)).digest('hex');
  writeFileSync(resolve(module, 'module.norm'), `
Module module() {
  return module(name: "fixture", version: 1, binding: jarBinding(
    target: localJar(path: "fixture.jar", integrity: sha256("${digest}")),
    api: [jarType(name: "BindingApi", members: ["greet"])]
  ))
}
`);
  writeFileSync(source, `
import fixture.bindingApiGreet
Module module() {
  return module(dependencies: [dependency(repository: "github", name: "fixture", version: 1)])
}
Void main() { printLine(bindingApiGreet("Norm") ?? "missing") }
`);
  assert.equal(run(cli, ['run', source]), 'Hello, Norm!\n');
  const before = readdirSync(directory);
  const output = run(cli, ['build', source, '--diagnostics']);
  const executable = process.platform === 'win32' ? `${source}.exe` : resolve(directory, 'application');
  if (process.platform === 'win32') {
    assert.deepEqual(readdirSync(directory).sort(), [...before, 'application.norm.exe'].sort(),
      'Single-file build must publish only its executable');
  }
  const evidence = await verifyNativeSize(executable, resolve(repository, 'build/reports/native-size'), output);
  await verifyNativeExecution(executable, source, evidence, 'Hello, Norm!\n');
  console.log('Norm CLI and native Java interoperability verified.');
} finally {
  rmSync(directory, { recursive: true, force: true });
}

function run(command, args) {
  const script = process.platform === 'win32' && /\.(bat|cmd)$/i.test(command);
  const result = spawnSync(script ? process.env.ComSpec : command,
    script ? ['/d', '/c', 'call', command, ...args] : args,
    { cwd: directory, encoding: 'utf8', timeout: 1200000, windowsHide: true });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `${command} ${args.join(' ')}\n${result.stderr}\n${result.stdout}`);
  return result.stdout.replaceAll('\r\n', '\n');
}
