import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { generateManifests } from './distribution-manifests.mjs';

test('channel manifests consume verified release assets and preserve the runtime layout', () => {
  const directory = mkdtempSync(join(tmpdir(), 'norm-channels-'));
  try {
    const assets = join(directory, 'assets');
    const output = join(directory, 'channels');
    mkdirSync(assets);
    const hashes = new Map();
    for (const name of ['norm.exe', 'norm-v1.2.3-linux-x64.tar.gz', 'norm-v1.2.3-macos-arm64.tar.gz']) {
      const bytes = Buffer.from(`fixture for ${name}`);
      writeFileSync(join(assets, name), bytes);
      hashes.set(name, createHash('sha256').update(bytes).digest('hex'));
    }
    writeFileSync(join(assets, 'SHA256SUMS'), [...hashes].map(([name, hash]) => `${hash}  ${name}`).join('\n') + '\n');
    generateManifests('1.2.3', assets, output);
    const scoop = JSON.parse(readFileSync(join(output, 'scoop-bucket/bucket/normlang.json')));
    assert.equal(scoop.version, '1.2.3');
    assert.equal(scoop.architecture['64bit'].url, 'https://github.com/normlanguage/Norm/releases/download/v1.2.3/norm.exe');
    assert.equal(scoop.architecture['64bit'].hash, hashes.get('norm.exe'));
    assert.equal(scoop.bin, 'norm.exe');
    assert.equal(scoop.installer, undefined);
    assert.equal(scoop.post_install, undefined);
    const formula = readFileSync(join(output, 'homebrew-tap/Formula/normlang.rb'), 'utf8');
    assert.match(formula, /libexec\.install Dir\["\*"\]/);
    assert.match(formula, /bin\.write_exec_script libexec\/"bin\/norm"/);
    assert.match(formula, /norm run hello\.norm/);
    for (const name of ['norm-v1.2.3-linux-x64.tar.gz', 'norm-v1.2.3-macos-arm64.tar.gz']) {
      assert.ok(formula.includes(name));
      assert.ok(formula.includes(hashes.get(name)));
    }
    const installer = readFileSync(join(output, 'winget/manifests/n/NormLanguage/Norm/1.2.3/NormLanguage.Norm.installer.yaml'), 'utf8');
    assert.match(installer, /InstallerType: portable/);
    assert.ok(installer.includes(hashes.get('norm.exe').toUpperCase()));
    assert.doesNotMatch(installer, /InstallerSwitches|setup/);
    const snap = readFileSync(join(output, 'snap/snapcraft.yaml'), 'utf8');
    assert.match(snap, /name: normlang\n/);
    assert.match(snap, /version: '1\.2\.3'/);
    assert.match(snap, /command: bin\/norm/);
    assert.match(snap, /plugin: dump/);
    assert.ok(snap.includes('https://github.com/normlanguage/Norm/releases/download/v1.2.3/norm-v1.2.3-linux-x64.tar.gz'));
    assert.ok(snap.includes(`source-checksum: sha256/${hashes.get('norm-v1.2.3-linux-x64.tar.gz')}`));
    assert.doesNotMatch(snap, /gradle|override-build/);
    assert.throws(() => generateManifests('v1.2.3', assets, join(directory, 'invalid')), /version/i);
    writeFileSync(join(assets, 'norm.exe'), 'corrupted');
    assert.throws(() => generateManifests('1.2.3', assets, join(directory, 'corrupt')), /checksum/i);
    assert.equal(existsSync(join(directory, 'corrupt')), false);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
