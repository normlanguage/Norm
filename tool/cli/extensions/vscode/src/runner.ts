import { basename, dirname } from 'node:path';
import * as vscode from 'vscode';
import { cliInvocation, ResolvedCliCommand } from './cli-command';
import { ProcessTerminal } from './process-terminal';

export class NormRunner {
  public constructor(private readonly cli: () => ResolvedCliCommand | undefined) {}

  public async runCurrentFile(): Promise<vscode.TaskExecution | undefined> {
    const document = vscode.window.activeTextEditor?.document;
    if (!document || document.languageId !== 'norm') {
      void vscode.window.showErrorMessage('Open a Norm file before running it.');
      return undefined;
    }
    if (basename(document.uri.path) === 'module.norm') {
      void vscode.window.showErrorMessage('module.norm runs automatically with the project.');
      return undefined;
    }
    if (document.uri.scheme !== 'file') {
      void vscode.window.showErrorMessage('Save the Norm file to disk before running it.');
      return undefined;
    }

    const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
    const documents = vscode.workspace.textDocuments.filter(
      (candidate) =>
        candidate.languageId === 'norm' &&
        candidate.uri.scheme === 'file' &&
        candidate.isDirty &&
        (workspaceFolder
          ? vscode.workspace.getWorkspaceFolder(candidate.uri)?.uri.toString() ===
            workspaceFolder.uri.toString()
          : candidate.uri.toString() === document.uri.toString()),
    );
    const saved = await Promise.all(documents.map((candidate) => candidate.save()));
    if (saved.some((value) => !value)) return undefined;

    const configuration = vscode.workspace.getConfiguration('norm', document.uri);
    const cli = this.cli();
    if (!cli) {
      void vscode.window.showErrorMessage(
        'Norm CLI is unavailable because the language server is not running.',
      );
      return undefined;
    }

    const workingDirectory =
      configuration.get<'workspace' | 'file'>('run.workingDirectory', 'workspace') === 'file'
        ? dirname(document.uri.fsPath)
        : (workspaceFolder?.uri.fsPath ?? dirname(document.uri.fsPath));
    const invocation = cliInvocation(cli.command, ['run', document.uri.fsPath]);
    const definition: vscode.TaskDefinition = { type: 'norm', file: document.uri.toString() };
    const scope: vscode.WorkspaceFolder | vscode.TaskScope =
      workspaceFolder ?? vscode.TaskScope.Global;
    const task = new vscode.Task(
      definition,
      scope,
      `Run ${basename(document.uri.fsPath)}`,
      'Norm',
      new vscode.CustomExecution(
        async () => new ProcessTerminal(invocation, workingDirectory),
      ),
      [],
    );
    task.presentationOptions = {
      reveal: vscode.TaskRevealKind.Always,
      panel: vscode.TaskPanelKind.Dedicated,
      clear: configuration.get<boolean>('run.clearTerminal', true),
      focus: false,
      echo: false,
      showReuseMessage: false,
    };
    return vscode.tasks.executeTask(task);
  }
}
