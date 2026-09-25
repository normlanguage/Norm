import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { chmodSync, copyFileSync, existsSync, mkdtempSync, mkdirSync, readFileSync, readlinkSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { buildPackage, buildRepository } from './apt-repository.mjs';

const command = (name, args, options = {}) => {
  const result = spawnSync(name, args, { encoding: 'utf8', ...options });
  assert.equal(result.status, 0, `${name}: ${result.stderr}`);
  return result.stdout;
};

const debian = process.platform === 'linux' ? readFileSync('/etc/os-release', 'utf8') : '';
const debian13 = /^ID=debian$/m.test(debian) && /^VERSION_CODENAME=trixie$/m.test(debian);

test('real Debian tools build a private-runtime package and signed APT repository', { skip: !debian13 }, () => {
  const root = mkdtempSync(join(tmpdir(), 'norm-apt-integration-'));
  const oldGnuPgHome = process.env.GNUPGHOME;
  try {
    const source = join(root, 'source');
    const assets = join(root, 'assets');
    const output = join(root, 'packages');
    const gpgHome = join(root, 'gnupg');
    mkdirSync(join(source, 'norm', 'bin'), { recursive: true });
    mkdirSync(join(source, 'norm', 'runtime', 'bin'), { recursive: true });
    mkdirSync(join(source, 'norm', 'lib'), { recursive: true });
    chmodSync(join(source, 'norm', 'lib'), 0o775);
    mkdirSync(assets);
    mkdirSync(gpgHome, { mode: 0o700 });
    process.env.GNUPGHOME = gpgHome;
    writeFileSync(join(source, 'norm', 'bin', 'norm'), '#!/bin/sh\nprintf "norm 1.2.3\\n"\n', { mode: 0o755 });
    writeFileSync(join(source, 'norm', 'runtime', 'bin', 'java.c'), '#include <stdio.h>\nint main(void) { puts("java"); return 0; }\n');
    command('cc', ['-o', join(source, 'norm', 'runtime', 'bin', 'java'), join(source, 'norm', 'runtime', 'bin', 'java.c')]);
    rmSync(join(source, 'norm', 'runtime', 'bin', 'java.c'));
    writeFileSync(join(source, 'norm', 'lib', 'compiler.jar'), 'jar');
    symlinkSync('compiler.jar', join(source, 'norm', 'lib', 'compiler-link.jar'));
    const archive = join(assets, 'norm-v1.2.3-linux-x64.tar.gz');
    command('tar', ['-C', source, '-czf', archive, 'norm']);
    writeFileSync(join(assets, 'SHA256SUMS'), `${createHash('sha256').update(readFileSync(archive)).digest('hex')}  norm-v1.2.3-linux-x64.tar.gz\n`);
    const alteredAssets = join(root, 'altered-assets');
    mkdirSync(alteredAssets);
    copyFileSync(join(assets, 'SHA256SUMS'), join(alteredAssets, 'SHA256SUMS'));
    writeFileSync(join(alteredAssets, 'norm-v1.2.3-linux-x64.tar.gz'), 'altered');
    assert.throws(() => buildPackage('1.2.3', alteredAssets, join(root, 'rejected')), /checksum/i);
    const originalUmask = process.umask(0o002);
    let deb;
    let repeatedDeb;
    try {
      deb = buildPackage('1.2.3', assets, output);
      process.umask(0o022);
      repeatedDeb = buildPackage('1.2.3', assets, join(root, 'repeated-packages'));
    } finally {
      process.umask(originalUmask);
    }
    assert.equal(createHash('sha256').update(readFileSync(deb)).digest('hex'), createHash('sha256').update(readFileSync(repeatedDeb)).digest('hex'));
    assert.equal(command('dpkg-deb', ['--field', deb, 'Version']).trim(), '1.2.3');
    assert.match(command('dpkg-deb', ['--field', deb, 'Depends']), /libc6/);
    const contents = command('dpkg-deb', ['--contents', deb]);
    assert.match(contents, /usr\/lib\/normlang\/runtime\/bin\/java/);
    assert.match(contents, /usr\/bin\/norm/);
    for (const directory of ['./usr/', './usr/bin/', './usr/lib/']) {
      assert.ok(contents.split('\n').some(line => line.startsWith('drwxr-xr-x ') && line.endsWith(` ${directory}`)), `Incorrect package directory mode: ${directory}`);
    }
    assert.ok(contents.split('\n').some(line => line.startsWith('drwxrwxr-x ') && line.endsWith(' ./usr/lib/normlang/lib/')));
    const extracted = join(root, 'extracted');
    command('dpkg-deb', ['-x', deb, extracted]);
    assert.equal(readlinkSync(join(extracted, 'usr', 'lib', 'normlang', 'lib', 'compiler-link.jar')), 'compiler.jar');
    command('gpg', ['--batch', '--passphrase', '', '--quick-generate-key', 'Norm APT Test <apt-test@normlanguage.org>', 'ed25519', 'sign', '1d']);
    const fingerprint = command('gpg', ['--with-colons', '--list-secret-keys']).split('\n').find(line => line.startsWith('fpr:')).split(':')[9];
    const repo = join(root, 'repo');
    buildRepository(output, repo, fingerprint);
    writeFileSync(join(source, 'norm', 'bin', 'norm'), '#!/bin/sh\nprintf "norm 1.2.4\\n"\n', { mode: 0o755 });
    const nextArchive = join(assets, 'norm-v1.2.4-linux-x64.tar.gz');
    command('tar', ['-C', source, '-czf', nextArchive, 'norm']);
    writeFileSync(join(assets, 'SHA256SUMS'), `${createHash('sha256').update(readFileSync(archive)).digest('hex')}  norm-v1.2.3-linux-x64.tar.gz\n${createHash('sha256').update(readFileSync(nextArchive)).digest('hex')}  norm-v1.2.4-linux-x64.tar.gz\n`);
    buildPackage('1.2.4', assets, output);
    buildRepository(output, repo, fingerprint);
    assert.ok(existsSync(join(repo, 'pool', 'main', 'n', 'normlang', 'normlang_1.2.3_amd64.deb')));
    assert.ok(existsSync(join(repo, 'pool', 'main', 'n', 'normlang', 'normlang_1.2.4_amd64.deb')));
    assert.match(readFileSync(join(repo, 'dists', 'stable', 'main', 'binary-amd64', 'Packages'), 'utf8'), /Version: 1\.2\.4/);
    const alteredPackages = join(root, 'altered-packages');
    mkdirSync(alteredPackages);
    writeFileSync(join(alteredPackages, 'normlang_1.2.3_amd64.deb'), 'altered');
    assert.throws(() => buildRepository(alteredPackages, repo, fingerprint), /immutable/i);
    assert.ok(existsSync(join(repo, 'dists', 'stable', 'InRelease')));
    const keyring = join(root, 'keyring.gpg');
    const exported = spawnSync('gpg', ['--batch', '--export', fingerprint]);
    assert.equal(exported.status, 0);
    writeFileSync(keyring, exported.stdout);
    command('gpgv', ['--keyring', keyring, join(repo, 'dists', 'stable', 'InRelease')]);
    const inRelease = join(repo, 'dists', 'stable', 'InRelease');
    writeFileSync(inRelease, readFileSync(inRelease, 'utf8').replace('Origin: NormLanguage', 'Origin: Other'));
    assert.notEqual(spawnSync('gpgv', ['--keyring', keyring, inRelease]).status, 0);
  } finally {
    if (oldGnuPgHome === undefined) delete process.env.GNUPGHOME;
    else process.env.GNUPGHOME = oldGnuPgHome;
    rmSync(root, { recursive: true, force: true });
  }
});
