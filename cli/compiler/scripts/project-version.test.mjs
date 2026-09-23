import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { readProjectVersion } from './release-model.mjs';

const directory = mkdtempSync(join(tmpdir(), 'norm-project-version-'));
try {
  const pom = join(directory, 'pom.xml');
  writeFileSync(pom, '<project><properties><revision>0.24.0-SNAPSHOT</revision></properties></project>');
  assert.equal(readProjectVersion(directory), '0.24.0-SNAPSHOT');
  writeFileSync(pom, '<project><properties><revision>0.24.0</revision></properties></project>');
  assert.equal(readProjectVersion(directory), '0.24.0');
  writeFileSync(pom, '<project><properties></properties></project>');
  assert.throws(() => readProjectVersion(directory), /revision/);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
