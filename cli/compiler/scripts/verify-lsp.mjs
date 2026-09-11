import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const repository = resolve(import.meta.dirname, '../../..');
const distribution = resolve(process.argv[2] ?? join(repository, 'cli/compiler/build/install/norm'));
const evidence = resolve(process.argv[3] ?? join(repository, 'build/reports/lsp'));
mkdirSync(evidence, { recursive: true });
const directory = mkdtempSync(join(tmpdir(), 'norm-lsp-acceptance-'));
const sessions = [];
const checks = [];
try {
  const normal = session('normal');
  const initialized = await normal.request('initialize', { processId: process.pid, capabilities: {} }).response;
  assert.equal(initialized.result.capabilities.textDocumentSync, 1);
  normal.notify('initialized', {});
  const source = join(directory, 'editing.norm');
  const uri = pathToFileURL(source).href;
  writeFileSync(source, 'Void main() { missing() }');
  normal.notify('textDocument/didOpen', { textDocument: { uri, languageId: 'norm', version: 1, text: readFileSync(source, 'utf8') } });
  const diagnostics = await normal.notification('textDocument/publishDiagnostics', value => value.params.uri === uri && value.params.diagnostics.some(diagnostic => diagnostic.code === 'NORM-NAME-0003'));
  assert.equal(diagnostics.params.version, 1);
  const text = 'Void main() { List<Integer> values = List<Integer>() values. }';
  normal.notify('textDocument/didChange', { textDocument: { uri, version: 2 }, contentChanges: [{ text }] });
  const completion = await normal.request('textDocument/completion', { textDocument: { uri }, position: { line: 0, character: text.indexOf('values.') + 7 } }).response;
  const items = Array.isArray(completion.result) ? completion.result : completion.result.items;
  assert.ok(items.some(item => item.label === 'add'));
  assert.ok(items.some(item => item.label === 'removeAt'));
  const cancelled = normal.request('textDocument/completion', { textDocument: { uri }, position: { line: 0, character: text.indexOf('values.') + 7 } });
  normal.notify('$/cancelRequest', { id: cancelled.id });
  const cancellation = await cancelled.response;
  assert.ok(cancellation.result !== undefined || cancellation.error?.code === -32800, JSON.stringify(cancellation));
  const alive = await normal.request('norm/source', 'stdlib:/std/math/integer.norm').response;
  assert.ok(alive.result.includes('Integer clamp'));
  await normal.request('shutdown').response;
  normal.notify('exit');
  assert.equal(await normal.exit, 0);
  checks.push({ name: 'initialize-diagnostics-completion-cancel-shutdown-exit', passed: true, cancellation: cancellation.error?.code === -32800 ? 'cancelled' : 'response-won-race', clientInputStayedOpen: true });

  const early = session('exit-without-shutdown');
  early.notify('exit');
  assert.equal(await early.exit, 1);
  checks.push({ name: 'exit-without-shutdown', passed: true });

  const eof = session('eof');
  eof.process.stdin.end();
  assert.equal(await eof.exit, 1);
  checks.push({ name: 'input-eof', passed: true });

  const disconnected = session('partial-message');
  disconnected.process.stdin.end('Content-Length: 100\r\n\r\n{');
  assert.equal(await disconnected.exit, 1);
  checks.push({ name: 'disconnect-during-message', passed: true });
  writeFileSync(join(evidence, 'verification.json'), JSON.stringify({ passed: true, checks }, null, 2) + '\n');
  console.log(`Stdio LSP handshake, editing, cancellation and session termination verified: ${evidence}`);
} finally {
  for (const value of sessions) {
    if (value.process.exitCode === null && value.process.signalCode === null) value.process.kill();
    writeFileSync(join(evidence, `${value.name}.stderr.log`), value.stderr);
    writeFileSync(join(evidence, `${value.name}.messages.json`), JSON.stringify(value.messages, null, 2) + '\n');
  }
  rmSync(directory, { recursive: true, force: true });
}

function session(name) {
  const java = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  const child = spawn(java, ['--sun-misc-unsafe-memory-access=allow', '--enable-native-access=org.graalvm.truffle', '-Dpolyglot.engine.WarnInterpreterOnly=false', '--module-path', join(distribution, 'lib'), '--module', 'dev.w0fv1.norm/dev.w0fv1.norm.cli.Main', 'lsp'], { cwd: directory, windowsHide: true });
  let buffer = Buffer.alloc(0);
  let nextId = 0;
  const pending = new Map();
  const notifications = new Set();
  const value = { name, process: child, stderr: '', messages: [] };
  sessions.push(value);
  child.stderr.on('data', chunk => { value.stderr += chunk.toString('utf8'); });
  child.stdout.on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk]);
    for (;;) {
      const headerEnd = buffer.indexOf('\r\n\r\n');
      if (headerEnd < 0) return;
      const match = /Content-Length:\s*(\d+)/i.exec(buffer.subarray(0, headerEnd).toString('ascii'));
      assert.ok(match, buffer.toString());
      const length = Number(match[1]);
      if (buffer.length < headerEnd + 4 + length) return;
      const message = JSON.parse(buffer.subarray(headerEnd + 4, headerEnd + 4 + length).toString('utf8'));
      buffer = buffer.subarray(headerEnd + 4 + length);
      value.messages.push(message);
      pending.get(message.id)?.(message);
      for (const callback of notifications) callback(message);
    }
  });
  value.exit = new Promise((resolveExit, reject) => {
    const timer = setTimeout(() => { child.kill(); reject(new Error(`${name}: LSP process did not terminate\n${value.stderr}`)); }, 45000);
    child.once('error', error => { clearTimeout(timer); reject(error); });
    child.once('close', code => { clearTimeout(timer); resolveExit(code); });
  });
  value.exit.catch(() => {});
  const send = message => {
    const bytes = Buffer.from(JSON.stringify({ jsonrpc: '2.0', ...message }));
    child.stdin.write(`Content-Length: ${bytes.length}\r\n\r\n`);
    child.stdin.write(bytes);
  };
  value.notify = (method, params) => send({ method, params });
  value.request = (method, params) => {
    const id = ++nextId;
    const response = new Promise((resolveResponse, reject) => {
      const timer = setTimeout(() => { pending.delete(id); reject(new Error(`${name}: timed out waiting for ${method}\n${value.stderr}`)); }, 30000);
      pending.set(id, message => { clearTimeout(timer); pending.delete(id); resolveResponse(message); });
    });
    send({ id, method, params });
    return { id, response };
  };
  value.notification = (method, predicate) => {
    const existing = value.messages.find(message => message.method === method && predicate(message));
    if (existing) return Promise.resolve(existing);
    return new Promise((resolveNotification, reject) => {
      const timer = setTimeout(() => { notifications.delete(callback); reject(new Error(`${name}: missing notification ${method}`)); }, 30000);
      const callback = message => {
        if (message.method !== method || !predicate(message)) return;
        clearTimeout(timer);
        notifications.delete(callback);
        resolveNotification(message);
      };
      notifications.add(callback);
    });
  };
  return value;
}
