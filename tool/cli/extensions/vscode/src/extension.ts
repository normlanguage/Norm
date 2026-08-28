import * as vscode from 'vscode';
import { cliInvocation, resolveCliCommand } from './cli-command';
import { NormRunner } from './runner';
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  Trace,
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;
let lifecycle = Promise.resolve();
let outputChannel: vscode.LogOutputChannel | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const runner = new NormRunner(context.extensionPath);
  outputChannel = vscode.window.createOutputChannel('Norm Language Server', { log: true });
  context.subscriptions.push(
    outputChannel,
    vscode.workspace.registerTextDocumentContentProvider('stdlib', {
      provideTextDocumentContent: (uri) => {
        if (!client) throw new Error('Norm language server is not running.');
        return client.sendRequest<string>('norm/standardLibrarySource', uri.toString());
      },
    }),
    vscode.commands.registerCommand('norm.restartLanguageServer', async () => {
      await restartClient(context);
    }),
    vscode.commands.registerCommand('norm.runCurrentFile', () => runner.runCurrentFile()),
    vscode.commands.registerCommand('norm.openSettings', () =>
      vscode.commands.executeCommand(
        'workbench.action.openSettings',
        '@ext:normlang.norm-language-support',
      ),
    ),
    vscode.workspace.onDidChangeConfiguration(async (event) => {
      if (event.affectsConfiguration('norm.cli.path')) {
        await restartClient(context);
      } else if (event.affectsConfiguration('norm.trace.server')) {
        client?.setTrace(
          trace(vscode.workspace.getConfiguration('norm').get<string>('trace.server', 'off')),
        );
      }
    }),
  );
  await restartClient(context);
}

export async function deactivate(): Promise<void> {
  lifecycle = lifecycle.catch(() => undefined).then(stopClient);
  await lifecycle;
}

function restartClient(context: vscode.ExtensionContext): Promise<void> {
  lifecycle = lifecycle
    .catch(() => undefined)
    .then(async () => {
      await stopClient();
      await startClient(context);
    });
  return lifecycle;
}

async function startClient(context: vscode.ExtensionContext): Promise<void> {
  const configuration = vscode.workspace.getConfiguration('norm');
  const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
  const command = resolveCliCommand(
    configuration.get<string>('cli.path', ''),
    workspaceFolder?.uri.fsPath,
    context.extensionPath,
  );
  if (!command) {
    void vscode.window.showErrorMessage(
      'Norm CLI was not found. Install Norm or configure "norm.cli.path".',
    );
    return;
  }
  const options = workspaceFolder ? { cwd: workspaceFolder.uri.fsPath } : undefined;
  const invocation = cliInvocation(command, ['lsp']);
  const serverOptions: ServerOptions = {
    command: invocation.command,
    args: [...invocation.args],
    options,
  };
  const clientOptions: LanguageClientOptions = {
    outputChannel,
    documentSelector: [
      { scheme: 'file', language: 'norm' },
      { scheme: 'untitled', language: 'norm' },
      { scheme: 'stdlib', language: 'norm' },
    ],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.norm'),
    },
  };

  client = new LanguageClient(
    'normLanguageServer',
    'Norm Language Server',
    serverOptions,
    clientOptions,
  );
  client.setTrace(trace(configuration.get<string>('trace.server', 'off')));
  try {
    await client.start();
  } catch (error) {
    client = undefined;
    const message = error instanceof Error ? error.message : String(error);
    void vscode.window.showErrorMessage(`Norm language server failed to start: ${message}`);
  }
}

async function stopClient(): Promise<void> {
  const running = client;
  client = undefined;
  if (running) await running.dispose(10_000);
}

function trace(value: string): Trace {
  if (value === 'verbose') return Trace.Verbose;
  if (value === 'messages') return Trace.Messages;
  return Trace.Off;
}
