import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../../..');
test('the compiler and its release do not depend on ecosystem applications', () => {
  const examples = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', 'docs/examples'], { cwd: root, encoding: 'utf8' });
  assert.deepEqual(examples.trim().split(/\r?\n/).filter(file => file && existsSync(resolve(root, file))), []);
  for (const folder of ['cli/compiler/src/main', 'cli/compiler/scripts', '.github/workflows']) {
    for (const file of readdirSync(resolve(root, folder), { recursive: true })) {
      if (!/\.(java|mjs|yml)$/.test(file) || file.endsWith('.test.mjs')) continue;
      const content = readFileSync(resolve(root, folder, file), 'utf8');
      assert.doesNotMatch(content, /org\.hibernate|io\.micronaut|docs\/examples|verify-native-web/,
        `${folder}/${file} crosses the ecosystem boundary`);
    }
  }
});
