import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

const defaultCli = resolve(
  process.cwd(),
  '../../app/build/install/norm/bin',
  process.platform === 'win32' ? 'norm.bat' : 'norm',
);
const cli = process.env.NORM_CLI ?? defaultCli;
const child =
  process.platform === 'win32'
    ? spawn(process.env.ComSpec ?? 'cmd.exe', ['/d', '/c', 'call', cli, 'lsp'], {
        stdio: ['pipe', 'pipe', 'pipe'],
      })
    : spawn(cli, ['lsp'], { stdio: ['pipe', 'pipe', 'pipe'] });

let buffer = Buffer.alloc(0);
let stderr = '';
let settled = false;
let protocolComplete = false;
const timeout = setTimeout(() => finish(new Error(`LSP initialize timed out. stderr: ${stderr}`)), 10_000);

child.stderr.setEncoding('utf8');
child.stderr.on('data', (chunk) => {
  stderr += chunk;
});
child.on('error', finish);
child.on('exit', (code) => {
  if (settled) return;
  if (protocolComplete && code === 0) {
    finish();
  } else {
    finish(new Error(`Norm LSP exited with ${code}. stderr: ${stderr}`));
  }
});
child.stdout.on('data', (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  readMessages();
});

send({
  jsonrpc: '2.0',
  id: 1,
  method: 'initialize',
  params: { processId: process.pid, rootUri: null, capabilities: {} },
});

function send(message) {
  const json = JSON.stringify(message);
  child.stdin.write(`Content-Length: ${Buffer.byteLength(json)}\r\n\r\n${json}`);
}

function readMessages() {
  while (true) {
    const headerEnd = buffer.indexOf('\r\n\r\n');
    if (headerEnd < 0) return;
    const header = buffer.subarray(0, headerEnd).toString('ascii');
    const match = /Content-Length:\s*(\d+)/i.exec(header);
    if (!match) return finish(new Error(`Invalid LSP header: ${header}`));
    const length = Number(match[1]);
    const bodyStart = headerEnd + 4;
    if (buffer.length < bodyStart + length) return;
    const message = JSON.parse(buffer.subarray(bodyStart, bodyStart + length).toString('utf8'));
    buffer = buffer.subarray(bodyStart + length);
    if (message.id === 1) {
      const capabilities = message.result?.capabilities;
      if (
        !capabilities?.completionProvider ||
        !capabilities.definitionProvider ||
        !capabilities.referencesProvider ||
        !capabilities.renameProvider?.prepareProvider
      ) {
        return finish(new Error(`Initialize response lacks Norm capabilities: ${JSON.stringify(message)}`));
      }
      send({ jsonrpc: '2.0', method: 'initialized', params: {} });
      send({
        jsonrpc: '2.0',
        method: 'textDocument/didOpen',
        params: {
          textDocument: {
            uri: 'file:///module.norm',
            languageId: 'norm',
            version: 1,
            text: 'Module(name: "sample", version: 1, exports: [])',
          },
        },
      });
    } else if (
      message.method === 'textDocument/publishDiagnostics' &&
      message.params?.uri === 'file:///module.norm'
    ) {
      if (message.params.diagnostics?.length) {
        return finish(new Error(`Module descriptor diagnostics failed: ${JSON.stringify(message)}`));
      }
      send({
        jsonrpc: '2.0',
        id: 2,
        method: 'norm/standardLibrarySource',
        params: 'stdlib:/std/math/integer.norm',
      });
    } else if (message.id === 2) {
      if (typeof message.result !== 'string' || !message.result.includes('public int clamp')) {
        return finish(new Error(`Standard-library source request failed: ${JSON.stringify(message)}`));
      }
      send({ jsonrpc: '2.0', id: 3, method: 'shutdown', params: null });
    } else if (message.id === 3) {
      protocolComplete = true;
      send({ jsonrpc: '2.0', method: 'exit', params: null });
      child.stdin.end();
    }
  }
}

function finish(error) {
  if (settled) return;
  settled = true;
  clearTimeout(timeout);
  if (error) {
    child.kill();
    console.error(error.message);
    process.exitCode = 1;
  } else {
    console.log('Norm LSP stdio handshake succeeded.');
  }
}
