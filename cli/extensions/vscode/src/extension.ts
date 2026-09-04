import * as vscode from 'vscode';
import {
  cliInvocation,
  CliRejection,
  ResolvedCliCommand,
  resolveCliCommand,
} from './cli-command';
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
let activeCli: ResolvedCliCommand | undefined;
let toolchainStatus: vscode.StatusBarItem | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const runner = new NormRunner(() => activeCli);
  outputChannel = vscode.window.createOutputChannel('Norm Language Server', { log: true });
  toolchainStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 10);
  toolchainStatus.name = 'Norm Toolchain';
  toolchainStatus.command = 'norm.showToolchain';
  toolchainStatus.text = '$(sync~spin) Norm';
  toolchainStatus.show();
  const sourceProvider: vscode.TextDocumentContentProvider = {
    provideTextDocumentContent: (uri) => {
      if (!client) throw new Error('Norm language server is not running.');
      return client.sendRequest<string>('norm/source', uri.toString());
    },
  };
  context.subscriptions.push(
    outputChannel,
    toolchainStatus,
    vscode.workspace.registerTextDocumentContentProvider('stdlib', sourceProvider),
    vscode.workspace.registerTextDocumentContentProvider('norm-source', sourceProvider),
    vscode.commands.registerCommand('norm.restartLanguageServer', async () => {
      await restartClient(context);
    }),
    vscode.commands.registerCommand('norm.runCurrentFile', () => runner.runCurrentFile()),
    vscode.commands.registerCommand('norm.showToolchain', () => {
      if (!activeCli) {
        return vscode.window.showWarningMessage('No compatible Norm CLI is active.');
      }
      return vscode.window.showInformationMessage(
        `Norm ${activeCli.version} (${activeCli.source})\n${activeCli.command}`,
      );
    }),
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
  const extensionVersion = String(context.extension.packageJSON.version);
  const resolution = await resolveCliCommand({
    configured: configuration.get<string>('cli.path', '') || process.env.NORM_CLI || '',
    workspacePath: workspaceFolder?.uri.fsPath,
    extensionPath: context.extensionPath,
    extensionVersion,
    development: context.extensionMode === vscode.ExtensionMode.Development,
  });
  for (const rejection of resolution.rejected) logRejection(rejection, extensionVersion);
  const selected = resolution.selected;
  if (!selected) {
    if (toolchainStatus) {
      toolchainStatus.text = '$(error) Norm';
      toolchainStatus.tooltip = `No Norm CLI compatible with extension ${extensionVersion} was found.`;
    }
    void vscode.window.showErrorMessage(
      `No Norm CLI compatible with extension ${extensionVersion} was found. ` +
        'Install a matching extension package or update "norm.cli.path".',
    );
    return;
  }
  activeCli = selected;
  if (toolchainStatus) {
    toolchainStatus.text = `$(tools) Norm ${selected.version.replace(/-SNAPSHOT$/u, '')}`;
    toolchainStatus.tooltip = `Using ${selected.command} (${selected.source})`;
  }
  outputChannel?.info(
    `Using Norm CLI ${selected.version} from ${selected.command} (${selected.source}).`,
  );
  const rejectedConfiguration = resolution.rejected.find(
    ({ source }) => source === 'configured',
  );
  if (rejectedConfiguration) {
    void vscode.window.showWarningMessage(
      `Configured Norm CLI ${rejectedConfiguration.command} ${rejectionDescription(
        rejectedConfiguration,
        extensionVersion,
      )}; using ${selected.version} instead.`,
    );
  }
  const options = workspaceFolder ? { cwd: workspaceFolder.uri.fsPath } : undefined;
  const invocation = cliInvocation(selected.command, ['lsp']);
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
      { scheme: 'norm-source', language: 'norm' },
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
    if (toolchainStatus) {
      toolchainStatus.text = '$(error) Norm';
      toolchainStatus.tooltip = 'Norm language server failed to start.';
    }
    const message = error instanceof Error ? error.message : String(error);
    void vscode.window.showErrorMessage(`Norm language server failed to start: ${message}`);
  }
}

async function stopClient(): Promise<void> {
  const running = client;
  client = undefined;
  activeCli = undefined;
  if (running) await running.dispose(10_000);
}

function trace(value: string): Trace {
  if (value === 'verbose') return Trace.Verbose;
  if (value === 'messages') return Trace.Messages;
  return Trace.Off;
}

function logRejection(rejection: CliRejection, extensionVersion: string): void {
  outputChannel?.warn(
    `Ignoring Norm CLI ${rejection.command}: ${rejectionDescription(rejection, extensionVersion)}.`,
  );
}

function rejectionDescription(rejection: CliRejection, extensionVersion: string): string {
  if (rejection.reason === 'version-mismatch') {
    return `version ${rejection.version} does not match extension ${extensionVersion}`;
  }
  return rejection.reason === 'not-found'
    ? 'the executable was not found'
    : 'its version is unavailable';
}
