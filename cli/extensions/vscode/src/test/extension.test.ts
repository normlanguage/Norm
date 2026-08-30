import * as assert from 'node:assert/strict';
import * as vscode from 'vscode';
import { cliInvocation } from '../cli-command';
import { ProcessTerminal } from '../process-terminal';

suite('Norm VS Code extension', () => {
  suiteSetup(async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    await vscode.workspace
      .getConfiguration('editor')
      .update('wordBasedSuggestions', 'off', vscode.ConfigurationTarget.Workspace);
    await vscode.workspace
      .getConfiguration('files')
      .update('autoSave', 'off', vscode.ConfigurationTarget.Workspace);
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

  test('completes user class fields, methods, and enum variants', async () => {
    await assertMembers('20_class_fields.norm', 'point.x', ['x', 'y'], ['add']);
    await assertMembers('21_class_method.norm', 'counter.add', ['add', 'current'], ['push']);
    await assertMembers('23_enum.norm', 'Color.Green', ['Red', 'Green', 'Blue'], ['length']);
  });

  test('publishes compiler diagnostics for invalid Norm source', async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    const uri = vscode.Uri.joinPath(root, 'cli/extensions/vscode/test-fixtures/invalid.norm');
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
      content: 'Void main() { missing(1) }',
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

  test('serves extension, reflection, and serialization language features', async () => {
    const document = await openProjectFixture('sample/Modern.norm');
    const completions = await atCompletionPoint(document, 'user.label()', (position) =>
      eventually(async () => {
        const value = await vscode.commands.executeCommand<vscode.CompletionList>(
          'vscode.executeCompletionItemProvider',
          document.uri,
          position,
          '.',
        );
        return value?.items.some((item) => labelOf(item) === 'label') ? value : undefined;
      }),
    );
    const label = completions.items.find((item) => labelOf(item) === 'label');
    const labels = completions.items.map(labelOf);

    assert.ok(label);
    assert.equal(completionText(label.insertText), 'label()');
    for (const name of ['toJson', 'toXml', 'toYaml']) {
      assert.ok(labels.includes(name), `completion lacks ${name}`);
    }

    const reflectionCompletions = await atCompletionPoint(document, 'type.fields()', (position) =>
      eventually(async () => {
        const value = await vscode.commands.executeCommand<vscode.CompletionList>(
          'vscode.executeCompletionItemProvider',
          document.uri,
          position,
          '.',
        );
        return value?.items.some((item) => labelOf(item) === 'fields') ? value : undefined;
      }),
    );

    assert.ok(reflectionCompletions.items.some((item) => labelOf(item) === 'fields'));
    assert.deepEqual(vscode.languages.getDiagnostics(document.uri), []);
  });

  test('accepts structured documentation on standard-library declarations', async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    const uri = vscode.Uri.joinPath(root, 'norm/stdlib/std/collections/sequences.norm');
    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);
    const offset = document.getText().indexOf('listContains');
    const hovers = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Hover[]>(
        'vscode.executeHoverProvider',
        document.uri,
        document.positionAt(offset),
      );
      return value?.length ? value : undefined;
    });

    assert.ok(hovers.length > 0);
    assert.deepEqual(vscode.languages.getDiagnostics(document.uri), []);
  });

  test('retains language features after a destructive edit', async () => {
    const broken =
      'Void main() {\n' +
      '  printLine(return 1)\n' +
      '  String message = "ok"\n' +
      '  message.\n' +
      '}';
    const document = await vscode.workspace.openTextDocument({ language: 'norm', content: broken });
    await vscode.window.showTextDocument(document);

    await eventually(() =>
      vscode.languages
        .getDiagnostics(document.uri)
        .some((diagnostic) => String(diagnosticCode(diagnostic)).startsWith('NORM-PARSER-'))
        ? true
        : undefined,
    );
    const position = document.positionAt(broken.indexOf('message.') + 'message.'.length);
    const completions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        document.uri,
        position,
        '.',
      );
      return value?.items.some((item) => labelOf(item) === 'graphemeSize') ? value : undefined;
    });

    assert.ok(completions.items.some((item) => labelOf(item) === 'graphemeSize'));

    const valid =
      'Void main() {\n' +
      '  printLine(1)\n' +
      '  String message = "ok"\n' +
      '  printLine(message)\n' +
      '}';
    const edit = new vscode.WorkspaceEdit();
    edit.replace(
      document.uri,
      new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)),
      valid,
    );
    assert.ok(await vscode.workspace.applyEdit(edit));
    await eventually(() =>
      vscode.languages.getDiagnostics(document.uri).length === 0 ? true : undefined,
    );
  });

  test('completes nullable receivers and type-level collection members', async () => {
    const nullable = await vscode.workspace.openTextDocument({
      language: 'norm',
      content: 'class Box { String value } Void main() { Box? box = null box?. }',
    });
    await vscode.window.showTextDocument(nullable);
    const nullablePosition = nullable.positionAt(nullable.getText().indexOf('box?.') + 5);
    const nullableCompletions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        nullable.uri,
        nullablePosition,
        '.',
      );
      return value?.items.some((item) => labelOf(item) === 'value') ? value : undefined;
    });
    assert.ok(nullableCompletions.items.some((item) => labelOf(item) === 'value'));

    const typeLevel = await vscode.workspace.openTextDocument({
      language: 'norm',
      content: 'Void main() { List<Integer> values = List.filled(size: 2, value: 0) }',
    });
    await vscode.window.showTextDocument(typeLevel);
    const typePosition = typeLevel.positionAt(typeLevel.getText().lastIndexOf('List.filled') + 5);
    const typeCompletions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        typeLevel.uri,
        typePosition,
        '.',
      );
      return value?.items.some((item) => labelOf(item) === 'filled') ? value : undefined;
    });
    assert.ok(typeCompletions.items.some((item) => labelOf(item) === 'filled'));
  });

  test('completes applied generic enum variant constructors', async () => {
    const source =
      'enum Result<T, E> { Ok(T value), Err(E error) } ' +
      'Void main() { Result<Integer, String> value = Result<Integer, String>. }';
    const document = await vscode.workspace.openTextDocument({ language: 'norm', content: source });
    await vscode.window.showTextDocument(document);
    const position = document.positionAt(source.lastIndexOf('.') + 1);

    const completions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        document.uri,
        position,
        '.',
      );
      return value?.items.some((item) => labelOf(item) === 'Ok') ? value : undefined;
    });
    const ok = completions.items.find((item) => labelOf(item) === 'Ok');

    assert.ok(ok);
    assert.equal(completionText(ok.insertText), 'Ok(value: ${1:value})');
    assert.ok(String(ok.detail).includes('Result<Integer, String> Ok(Integer value)'));
  });

  test('supports interface bounds and requirement navigation', async () => {
    const source =
      'interface Named { String name() } ' +
      'class User implements Named { public String name() { return "Norm" } } ' +
      'String display<T extends Named>(T value) { return value.name() } Void main() {}';
    const document = await vscode.workspace.openTextDocument({ language: 'norm', content: source });
    await vscode.window.showTextDocument(document);
    const memberPosition = document.positionAt(source.indexOf('value.name') + 'value.'.length);
    const completions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        document.uri,
        memberPosition,
        '.',
      );
      return value?.items.some((item) => labelOf(item) === 'name') ? value : undefined;
    });
    const implementationOffset = source.indexOf('name()', source.indexOf('class User'));
    const definitions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.Location[]>(
        'vscode.executeDefinitionProvider',
        document.uri,
        document.positionAt(implementationOffset),
      );
      return value?.length ? value : undefined;
    });
    const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
      'vscode.executeHoverProvider',
      document.uri,
      document.positionAt(source.indexOf('T value')),
    );

    assert.ok(completions.items.some((item) => labelOf(item) === 'name'));
    assert.equal(
      definitions[0].range.start.isEqual(document.positionAt(source.indexOf('name()'))),
      true,
    );
    assert.ok(
      hovers.flatMap((hover) => hover.contents).map(hoverText).join('\n').includes('T extends Named'),
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

  test('isolates standalone documents opened from the same directory', async () => {
    const first = await openFixture('01_hello.norm');
    const second = await openFixture('19_bubble_sort.norm');
    await vscode.commands.executeCommand<vscode.CompletionList>(
      'vscode.executeCompletionItemProvider',
      second.uri,
      new vscode.Position(0, 0),
    );

    assert.deepEqual(vscode.languages.getDiagnostics(first.uri), []);
    assert.deepEqual(vscode.languages.getDiagnostics(second.uri), []);
  });

  test('enables automatic suggestions for Norm code', () => {
    const extension = vscode.extensions.getExtension('normlang.norm-language-support');
    assert.ok(extension);
    const defaults = extension.packageJSON.contributes.configurationDefaults['[norm]'];
    assert.deepEqual(defaults['editor.quickSuggestions'], {
      strings: 'off',
      other: 'on',
    });
    assert.equal(defaults['editor.suggestOnTriggerCharacters'], true);
    assert.equal(defaults['editor.defaultFormatter'], 'normlang.norm-language-support');
    assert.equal(defaults['editor.formatOnSave'], true);
  });

  test('formats Norm documents through the language server', async () => {
    const document = await vscode.workspace.openTextDocument({
      language: 'norm',
      content: 'public main(){printLine(1)}',
    });
    await vscode.window.showTextDocument(document);

    const edits = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.TextEdit[]>(
        'vscode.executeFormatDocumentProvider',
        document.uri,
        { tabSize: 2, insertSpaces: true },
      );
      return value?.length ? value : undefined;
    });
    const workspaceEdit = new vscode.WorkspaceEdit();
    workspaceEdit.set(document.uri, edits);

    assert.ok(await vscode.workspace.applyEdit(workspaceEdit));
    assert.equal(document.getText().replaceAll('\r\n', '\n'), 'main() {\n  printLine(1)\n}\n');
  });

  test('completes a partially typed statement in unsaved code', async () => {
    const source = 'Void main() {\n  pr\n}';
    const document = await vscode.workspace.openTextDocument({ language: 'norm', content: source });
    await vscode.window.showTextDocument(document);
    const start = source.indexOf('pr');
    const position = document.positionAt(start + 2);
    const completions = await eventually(async () => {
      const value = await vscode.commands.executeCommand<vscode.CompletionList>(
        'vscode.executeCompletionItemProvider',
        document.uri,
        position,
      );
      return value?.items.some((item) => labelOf(item) === 'printLine') ? value : undefined;
    });
    const printLine = completions.items.find((item) => labelOf(item) === 'printLine');
    assert.ok(printLine);
    assert.equal(completionText(printLine.insertText), 'printLine(${1:value})');
    assert.ok(printLine.range instanceof vscode.Range);
    assert.equal(printLine.range.start.isEqual(document.positionAt(start)), true);
    assert.equal(printLine.range.end.isEqual(position), true);
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
    assert.ok(rendered.includes('Integer max(Integer left, Integer right)'));

    const definitions = await vscode.commands.executeCommand<vscode.Location[]>(
      'vscode.executeDefinitionProvider',
      document.uri,
      document.positionAt(offset),
    );
    assert.equal(definitions[0].uri.scheme, 'stdlib');
    const source = await vscode.workspace.openTextDocument(definitions[0].uri);
    assert.ok(source.getText().includes('Integer max'));
  });

  test('supports generics and cross-file project navigation', async () => {
    const document = await openProjectFixture('sample/Main.norm');
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
      assert.ok(library.getText().includes('T preserveValue<T>'));
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
      declarationReferences.some((location) => location.uri.path.endsWith('/sample/Main.norm')),
    );

    const main = await openProjectFixture('sample/Main.norm');
    const completions = await vscode.commands.executeCommand<vscode.CompletionList>(
      'vscode.executeCompletionItemProvider',
      main.uri,
      new vscode.Position(4, 0),
    );
    assert.ok(!completions.items.some((item) => labelOf(item) === 'preserve'));
  });

  test('ranks expected values and serves signature help for incomplete code', async () => {
    const document = await openProjectFixture('sample/Main.norm');
    const original = document.getText();
    const text =
      'package sample\n\nVoid consume(String value, Integer count) {} Void main() { ' +
      'String label = "ready" Integer count = 1 consume(';
    await replaceDocument(document, text);
    try {
      const position = document.positionAt(document.getText().length);
      const completions = await eventually(async () => {
        const value = await vscode.commands.executeCommand<vscode.CompletionList>(
          'vscode.executeCompletionItemProvider',
          document.uri,
          position,
        );
        return value?.items.some((item) => labelOf(item) === 'label') ? value : undefined;
      });
      const label = completions.items.find((item) => labelOf(item) === 'label');
      const count = completions.items.find((item) => labelOf(item) === 'count');
      assert.ok(label);
      assert.ok(count);
      assert.ok(label.sortText);
      assert.ok(count.sortText);
      assert.ok(label.sortText < count.sortText);
      assert.equal(label.preselect, true);

      const signature = await eventually(async () =>
        await vscode.commands.executeCommand<vscode.SignatureHelp>(
          'vscode.executeSignatureHelpProvider',
          document.uri,
          position,
          '(',
        ),
      );
      assert.equal(signature.signatures[0].label, 'Void consume(String value, Integer count)');
      assert.equal(signature.activeParameter, 0);
    } finally {
      await replaceDocument(document, original);
    }
  });

  test('offers exported declarations with precise auto-import edits', async () => {
    const document = await openProjectFixture('sample/Main.norm');
    const original = document.getText();
    const edited = original
      .replace(/import sample\.util\.identity\r?\n/, '')
      .replace('identity(value: box.value[0])', 'iden');
    await replaceDocument(document, edited);
    try {
      const start = edited.lastIndexOf('iden');
      const position = document.positionAt(start + 'iden'.length);
      const completions = await eventually(async () => {
        const value = await vscode.commands.executeCommand<vscode.CompletionList>(
          'vscode.executeCompletionItemProvider',
          document.uri,
          position,
        );
        return value?.items.some((item) => labelOf(item) === 'identity') ? value : undefined;
      });
      const identity = completions.items.find((item) => labelOf(item) === 'identity');
      assert.ok(identity);
      assert.equal(completionText(identity.insertText), 'identity(${1:value})');
      assert.ok(identity.range instanceof vscode.Range);
      assert.equal(identity.range.start.isEqual(document.positionAt(start)), true);
      assert.equal(identity.range.end.isEqual(position), true);
      assert.equal(identity.additionalTextEdits?.length, 1);
      assert.equal(
        identity.additionalTextEdits?.[0].newText,
        `${document.eol === vscode.EndOfLine.CRLF ? '\r\n' : '\n'}import sample.util.identity`,
      );
    } finally {
      await replaceDocument(document, original);
    }
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

  test('serializes language server restarts', async () => {
    await Promise.all([
      vscode.commands.executeCommand('norm.restartLanguageServer'),
      vscode.commands.executeCommand('norm.restartLanguageServer'),
    ]);
    const document = await openFixture('29_deque_pair_range.norm');
    const offset = document.getText().indexOf('Range indices') + 2;
    await eventually(async () => {
      const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
        'vscode.executeHoverProvider',
        document.uri,
        document.positionAt(offset),
      );
      return hovers?.length ? true : undefined;
    });
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

  test('saves open Norm project files before running', async () => {
    const module = await openProjectFixture('sample/module.norm');
    const original = module.getText();
    await replaceDocumentWithoutSave(
      module,
      'Module module() { return module(name: "sample", version: 0, exports: []) }',
    );
    assert.equal(module.isDirty, true);
    const main = await openProjectFixture('sample/Main.norm');

    try {
      const execution = await vscode.commands.executeCommand<vscode.TaskExecution | undefined>(
        'norm.runCurrentFile',
      );
      assert.ok(execution);
      const exitCode = await new Promise<number | undefined>((resolve) => {
        const subscription = vscode.tasks.onDidEndTaskProcess((event) => {
          if (event.execution === execution) {
            subscription.dispose();
            resolve(event.exitCode);
          }
        });
      });

      assert.equal(exitCode, 74);
      assert.equal(module.isDirty, false);
    } finally {
      await vscode.window.showTextDocument(module);
      await replaceDocument(module, original);
      await vscode.window.showTextDocument(main);
    }
  });

  test('reports a failed run through a custom execution', async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    const document = await vscode.workspace.openTextDocument(
      vscode.Uri.joinPath(root, 'norm/tests/recovery/missing_parenthesis.norm'),
    );
    await vscode.window.showTextDocument(document);

    const execution = await vscode.commands.executeCommand<vscode.TaskExecution | undefined>(
      'norm.runCurrentFile',
    );

    assert.ok(execution);
    assert.ok(execution.task.execution instanceof vscode.CustomExecution);
    const exitCode = await new Promise<number | undefined>((resolve) => {
      const subscription = vscode.tasks.onDidEndTaskProcess((event) => {
        if (event.execution === execution) {
          subscription.dispose();
          resolve(event.exitCode);
        }
      });
    });
    assert.equal(exitCode, 1);
  });

  test('forwards only Norm output from a failed process', async () => {
    const root = vscode.workspace.workspaceFolders?.[0]?.uri;
    assert.ok(root, 'test workspace was not opened');
    const source = vscode.Uri.joinPath(root, 'norm/tests/recovery/missing_parenthesis.norm');
    const cli =
      process.env.NORM_CLI ??
      vscode.Uri.joinPath(
        root,
        'cli/compiler/build/install/norm/bin',
        process.platform === 'win32' ? 'norm.bat' : 'norm',
      ).fsPath;
    const terminal = new ProcessTerminal(
      cliInvocation(cli, ['run', source.fsPath]),
      root.fsPath,
    );
    const output: string[] = [];
    const writeSubscription = terminal.onDidWrite((text) => output.push(text));
    const completion = new Promise<number>((resolve) => terminal.onDidClose(resolve));

    terminal.open();

    assert.equal(await completion, 1);
    writeSubscription.dispose();
    assert.equal(
      output.join('').replaceAll('\r\n', '\n'),
      `${source.fsPath}:4:3: error[NORM-PARSER-0001]: expected ')' after arguments\n` +
        '  Integer last = 2\n' +
        '  ^\n',
    );
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
  const uri = vscode.Uri.joinPath(root, 'cli/extensions/vscode/test-fixtures/project', file);
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

function completionText(text: string | vscode.SnippetString | undefined): string | undefined {
  return text instanceof vscode.SnippetString ? text.value : text;
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
  await replaceDocumentWithoutSave(document, text);
  assert.ok(await document.save());
}

async function replaceDocumentWithoutSave(
  document: vscode.TextDocument,
  text: string,
): Promise<void> {
  const edit = new vscode.WorkspaceEdit();
  edit.replace(
    document.uri,
    new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length)),
    text,
  );
  assert.ok(await vscode.workspace.applyEdit(edit));
}
