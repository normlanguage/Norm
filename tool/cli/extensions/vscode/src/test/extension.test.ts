import * as assert from 'node:assert/strict';
import * as vscode from 'vscode';

suite('Norm VS Code extension', () => {
  suiteSetup(async () => {
    const extension = vscode.extensions.getExtension('normlang.norm-language-support');
    assert.ok(extension, 'development extension was not loaded');
    await extension.activate();
  });

  test('activates and supplies type-specific built-in container completion', async () => {
    await assertMembers('27_stack.norm', 'values.push', ['push', 'pop', 'peek'], ['addFirst']);
    await assertMembers('28_queue.norm', 'values.add', ['add', 'remove', 'peek'], ['pop']);
    await assertMembers(
      '29_deque_pair_range.norm',
      'values.addLast',
      ['addFirst', 'addLast', 'removeLast'],
      ['push'],
    );
    await assertMembers(
      '30_string_builder.norm',
      'builder.append',
      ['append', 'toString', 'length'],
      ['put'],
    );
  });

  test('completes user class fields, methods, and enum members', async () => {
    await assertMembers('20_class_fields.norm', 'point.x', ['x', 'y'], ['add']);
    await assertMembers('21_class_method.norm', 'counter.add', ['add', 'current'], ['push']);
    await assertMembers('23_enum.norm', 'Color.Green', ['Red', 'Green', 'Blue'], ['length']);
  });

  test('publishes compiler diagnostics for invalid Norm source', async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    const uri = vscode.Uri.joinPath(root, 'tool/cli/extensions/vscode/test-fixtures/invalid.norm');
    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);

    const diagnostics = await eventually(() => {
      const current = vscode.languages.getDiagnostics(uri);
      return current.some((diagnostic) => diagnosticCode(diagnostic) === 'NORM-NAME-0003')
        ? current
        : undefined;
    });
    assert.ok(diagnostics.some((diagnostic) => diagnostic.message.includes('missing')));
  });

  test('publishes diagnostics for unsaved Norm documents', async () => {
    const document = await vscode.workspace.openTextDocument({
      language: 'norm',
      content: 'void main() { missing(1) }',
    });
    await vscode.window.showTextDocument(document);

    await eventually(() =>
      vscode.languages
        .getDiagnostics(document.uri)
        .some((diagnostic) => diagnosticCode(diagnostic) === 'NORM-NAME-0003')
        ? true
        : undefined,
    );
  });

  test('returns hover documentation for core types', async () => {
    const document = await openFixture('29_deque_pair_range.norm');
    const offset = document.getText().indexOf('Range indices') + 2;
    const hovers = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Hover[]>(
        'vscode.executeHoverProvider',
        document.uri,
        document.positionAt(offset),
      );
      return value?.length ? value : undefined;
    });
    const rendered = hovers.flatMap((hover) => hover.contents).map(hoverText).join('\n');
    assert.ok(rendered.includes('end-exclusive integer range'));
  });

  test('provides definition, references, and semantic rename edits', async () => {
    const document = await openFixture('04_assignment.norm');
    const text = document.getText();
    const declarationOffset = text.indexOf('value');
    const useOffset = text.lastIndexOf('value');
    assert.ok(useOffset > declarationOffset);

    const definitions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Location[]>(
        'vscode.executeDefinitionProvider',
        document.uri,
        document.positionAt(useOffset),
      );
      return value?.length ? value : undefined;
    });
    const references = await vscode.commands.executeCommand<vscode.Location[]>(
      'vscode.executeReferenceProvider',
      document.uri,
      document.positionAt(useOffset),
    );
    const rename = await vscode.commands.executeCommand<vscode.WorkspaceEdit>(
      'vscode.executeDocumentRenameProvider',
      document.uri,
      document.positionAt(useOffset),
      'result',
    );

    assert.equal(definitions[0].range.start.character, document.positionAt(declarationOffset).character);
    assert.ok(references.length >= 2);
    assert.ok(rename.get(document.uri).length >= 2);
  });

  test('runs the current Norm file as a VS Code task', async () => {
    await openFixture('01_hello.norm');
    const execution = await vscode.commands.executeCommand<vscode.TaskExecution | undefined>(
      'norm.runCurrentFile',
    );

    assert.ok(execution);
    assert.equal(execution.task.definition.type, 'norm');
    assert.equal(execution.task.presentationOptions.echo, false);
    assert.equal(execution.task.presentationOptions.showReuseMessage, false);
    const exitCode = await new Promise<number | undefined>((resolve) => {
      const subscription = vscode.tasks.onDidEndTaskProcess((event) => {
        if (event.execution === execution) {
          subscription.dispose();
          resolve(event.exitCode);
        }
      });
    });
    assert.equal(exitCode, 0);
  });
});

function diagnosticCode(diagnostic: vscode.Diagnostic): string | number | undefined {
  return typeof diagnostic.code === 'object' ? diagnostic.code.value : diagnostic.code;
}

async function assertMembers(
  file: string,
  marker: string,
  expected: string[],
  forbidden: string[],
): Promise<void> {
  const document = await openFixture(file);
  const markerOffset = document.getText().indexOf(marker);
  assert.ok(markerOffset >= 0, `marker ${marker} was not found in ${file}`);
  const dotOffset = marker.indexOf('.') + 1;
  const position = document.positionAt(markerOffset + dotOffset);
  const completions = await eventually(async () => {
    const value = await vscode.commands.executeCommand<vscode.CompletionList>(
      'vscode.executeCompletionItemProvider',
      document.uri,
      position,
      '.',
    );
    const labels = value?.items.map(labelOf) ?? [];
    return expected.every((label) => labels.includes(label)) ? value : undefined;
  });
  const labels = completions.items.map(labelOf);
  for (const label of expected) assert.ok(labels.includes(label), `${file} lacks ${label}`);
  for (const label of forbidden) assert.ok(!labels.includes(label), `${file} contains ${label}`);
}

async function openFixture(file: string): Promise<vscode.TextDocument> {
  const root = vscode.workspace.workspaceFolders?.[0]?.uri;
  assert.ok(root, 'test workspace was not opened');
  const uri = vscode.Uri.joinPath(root, 'norm/tests/base', file);
  const document = await vscode.workspace.openTextDocument(uri);
  assert.equal(document.languageId, 'norm');
  await vscode.window.showTextDocument(document);
  return document;
}

function labelOf(item: vscode.CompletionItem): string {
  return typeof item.label === 'string' ? item.label : item.label.label;
}

function hoverText(content: vscode.MarkedString | vscode.MarkdownString): string {
  return typeof content === 'string' ? content : content.value;
}

async function eventually<T>(operation: () => T | undefined | Promise<T | undefined>): Promise<T> {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const value = await operation();
    if (value !== undefined) return value;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('condition was not satisfied before timeout');
}
