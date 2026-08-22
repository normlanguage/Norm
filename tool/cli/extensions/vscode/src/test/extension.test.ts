import * as assert from 'node:assert/strict';
import * as vscode from 'vscode';

suite('Norm VS Code extension', () => {
  suiteSetup(async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    const cli = vscode.Uri.joinPath(
      root,
      'tool/cli/app/build/install/norm/bin',
      process.platform === 'win32' ? 'norm.bat' : 'norm',
    ).fsPath;
    await vscode.workspace
      .getConfiguration('norm')
      .update('cli.path', cli, vscode.ConfigurationTarget.Global);
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
      ['append', 'toString', 'size'],
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

  test('resolves imported standard-library functions', async () => {
    const document = await openAlgorithmFixture('08_longest_consecutive.norm');
    const offset = document.getText().lastIndexOf('max');
    const hovers = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Hover[]>(
        'vscode.executeHoverProvider',
        document.uri,
        document.positionAt(offset),
      );
      return value?.length ? value : undefined;
    });
    const rendered = hovers.flatMap((hover) => hover.contents).map(hoverText).join('\n');
    assert.ok(rendered.includes('int max(int left, int right)'));

    const definitions = await vscode.commands.executeCommand<vscode.Location[]>(
      'vscode.executeDefinitionProvider',
      document.uri,
      document.positionAt(offset),
    );
    assert.equal(definitions[0].uri.scheme, 'stdlib');
    const source = await vscode.workspace.openTextDocument(definitions[0].uri);
    assert.ok(source.getText().includes('public int max'));
  });

  test('supports generics and cross-file project navigation', async () => {
    const document = await openProjectFixture('sample/app/Main.norm');
    const text = document.getText();
    const identityOffset = text.lastIndexOf('identity');
    const identityPosition = document.positionAt(identityOffset);
    const definitions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Location[]>(
        'vscode.executeDefinitionProvider',
        document.uri,
        identityPosition,
      );
      return value?.length ? value : undefined;
    });
    assert.ok(definitions[0].uri.path.endsWith('/sample/util/Identity.norm'));

    const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
      'vscode.executeHoverProvider',
      document.uri,
      identityPosition,
    );
    const rendered = hovers.flatMap((hover) => hover.contents).map(hoverText).join('\n');
    assert.ok(rendered.includes('T identity<T>(T value)'));

    const completions = await atCompletionPoint(document, 'box.value.add', (memberPosition) =>
      eventually(async () => {
        const value = await vscode.commands.executeCommand<vscode.CompletionList>(
          'vscode.executeCompletionItemProvider',
          document.uri,
          memberPosition,
          '.',
        );
        const labels = value?.items.map(labelOf) ?? [];
        return labels.includes('add') && labels.includes('size') ? value : undefined;
      }),
    );
    assert.ok(completions.items.some((item) => labelOf(item) === 'removeAt'));

    const references = await vscode.commands.executeCommand<vscode.Location[]>(
      'vscode.executeReferenceProvider',
      document.uri,
      identityPosition,
    );
    assert.ok(references.some((location) => location.uri.toString() === document.uri.toString()));
    assert.ok(references.some((location) => location.uri.path.endsWith('/sample/util/Identity.norm')));

    const rename = await vscode.commands.executeCommand<vscode.WorkspaceEdit>(
      'vscode.executeDocumentRenameProvider',
      document.uri,
      identityPosition,
      'preserveValue',
    );
    const libraryUri = definitions[0].uri;
    assert.equal(rename.get(document.uri)?.length, 2);
    assert.equal(rename.get(libraryUri)?.length, 1);
    const library = await vscode.workspace.openTextDocument(libraryUri);
    const originalMain = document.getText();
    const originalLibrary = library.getText();
    try {
      assert.ok(await vscode.workspace.applyEdit(rename));
      assert.ok(document.getText().includes('import sample.util.preserveValue'));
      assert.ok(document.getText().includes('preserveValue(value:'));
      assert.ok(library.getText().includes('public T preserveValue<T>'));
      await eventually(async () => {
        const updated = document.getText();
        const renamedOffset = updated.lastIndexOf('preserveValue');
        const values = await vscode.commands.executeCommand<vscode.Hover[]>(
          'vscode.executeHoverProvider',
          document.uri,
          document.positionAt(renamedOffset),
        );
        return values
          ?.flatMap((hover) => hover.contents)
          .map(hoverText)
          .some((value) => value.includes('preserveValue<T>'))
          ? true
          : undefined;
      });
      await eventually(() =>
        vscode.languages
          .getDiagnostics(document.uri)
          .some((diagnostic) => diagnostic.severity === vscode.DiagnosticSeverity.Error)
          ? undefined
          : true,
      );
    } finally {
      await replaceDocument(document, originalMain);
      await replaceDocument(library, originalLibrary);
    }
  });

  test('navigates generic type parameters and hides private declarations', async () => {
    const document = await openProjectFixture('sample/util/Identity.norm');
    const text = document.getText();
    const declarationOffset = text.indexOf('<T>') + 1;
    const fieldOffset = text.indexOf('T value');
    const definitions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Location[]>(
        'vscode.executeDefinitionProvider',
        document.uri,
        document.positionAt(fieldOffset),
      );
      return value?.length ? value : undefined;
    });
    assert.equal(definitions[0].range.start.character, document.positionAt(declarationOffset).character);

    const declarationReferences = await vscode.commands.executeCommand<vscode.Location[]>(
      'vscode.executeReferenceProvider',
      document.uri,
      document.positionAt(text.lastIndexOf('identity<T>')),
    );
    assert.ok(
      declarationReferences.some((location) => location.uri.path.endsWith('/sample/app/Main.norm')),
    );

    const main = await openProjectFixture('sample/app/Main.norm');
    const completions = await vscode.commands.executeCommand<vscode.CompletionList>(
      'vscode.executeCompletionItemProvider',
      main.uri,
      new vscode.Position(4, 0),
    );
    assert.ok(!completions.items.some((item) => labelOf(item) === 'preserve'));
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
  const completions = await atCompletionPoint(document, marker, (position) =>
    eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        document.uri,
        position,
        '.',
      );
      const labels = value?.items.map(labelOf) ?? [];
      return expected.every((label) => labels.includes(label)) ? value : undefined;
    }),
  );
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

async function atCompletionPoint<T>(
  document: vscode.TextDocument,
  marker: string,
  operation: (position: vscode.Position) => Promise<T>,
): Promise<T> {
  const markerOffset = document.getText().indexOf(marker);
  assert.ok(markerOffset >= 0, `marker ${marker} was not found`);
  const dotOffset = marker.lastIndexOf('.') + 1;
  const position = document.positionAt(markerOffset + dotOffset);
  const suffix = marker.substring(dotOffset);
  const edit = new vscode.WorkspaceEdit();
  edit.delete(document.uri, new vscode.Range(position, document.positionAt(markerOffset + marker.length)));
  assert.ok(await vscode.workspace.applyEdit(edit));
  try {
    return await operation(position);
  } finally {
    const restore = new vscode.WorkspaceEdit();
    restore.insert(document.uri, position, suffix);
    assert.ok(await vscode.workspace.applyEdit(restore));
    assert.ok(await document.save());
  }
}

async function openAlgorithmFixture(file: string): Promise<vscode.TextDocument> {
  const root = vscode.workspace.workspaceFolders?.[0]?.uri;
  assert.ok(root, 'test workspace was not opened');
  const uri = vscode.Uri.joinPath(root, 'norm/tests/algorithms', file);
  const document = await vscode.workspace.openTextDocument(uri);
  assert.equal(document.languageId, 'norm');
  await vscode.window.showTextDocument(document);
  return document;
}

async function openProjectFixture(file: string): Promise<vscode.TextDocument> {
  const root = vscode.workspace.workspaceFolders?.[0]?.uri;
  assert.ok(root, 'test workspace was not opened');
  const uri = vscode.Uri.joinPath(root, 'tool/cli/extensions/vscode/test-fixtures/project', file);
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

async function replaceDocument(document: vscode.TextDocument, text: string): Promise<void> {
  const edit = new vscode.WorkspaceEdit();
  edit.replace(
    document.uri,
    new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)),
    text,
  );
  assert.ok(await vscode.workspace.applyEdit(edit));
  assert.ok(await document.save());
}
