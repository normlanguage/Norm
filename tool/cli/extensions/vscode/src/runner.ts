import { basename, dirname } from 'node:path';
import * as vscode from 'vscode';
import { cliInvocation, resolveCliCommand } from './cli-command';

export class NormRunner {
  public constructor(private readonly extensionPath: string) {}

  public async runCurrentFile(): Promise<vscode.TaskExecution | undefined> {
    const document = vscode.window.activeTextEditor?.document;
    if (!document || document.languageId !== 'norm') {
      void vscode.window.showErrorMessage('Open a Norm file before running it.');
      return undefined;
    }
    if (basename(document.uri.path) === 'module.norm') {
      void vscode.window.showErrorMessage('module.norm is a compile-time module descriptor.');
      return undefined;
    }
    if (!(await document.save())) return undefined;
    if (document.uri.scheme !== 'file') {
      void vscode.window.showErrorMessage('Save the Norm file to disk before running it.');
      return undefined;
    }

    const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
    const configuration = vscode.workspace.getConfiguration('norm', document.uri);
    const cli = resolveCliCommand(
      configuration.get<string>('cli.path', ''),
      workspaceFolder?.uri.fsPath,
      this.extensionPath,
    );
    if (!cli) {
      void vscode.window.showErrorMessage(
        'Norm CLI was not found. Install Norm or configure "norm.cli.path".',
      );
      return undefined;
    }

    const workingDirectory =
      configuration.get<'workspace' | 'file'>('run.workingDirectory', 'workspace') === 'file'
        ? dirname(document.uri.fsPath)
        : (workspaceFolder?.uri.fsPath ?? dirname(document.uri.fsPath));
    const invocation = cliInvocation(cli, ['run', document.uri.fsPath]);
    const definition: vscode.TaskDefinition = { type: 'norm', file: document.uri.toString() };
    const scope: vscode.WorkspaceFolder | vscode.TaskScope =
      workspaceFolder ?? vscode.TaskScope.Global;
    const task = new vscode.Task(
      definition,
      scope,
      `Run ${basename(document.uri.fsPath)}`,
      'Norm',
      new vscode.ProcessExecution(invocation.command, [...invocation.args], { cwd: workingDirectory }),
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
