import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { appendFileSync, chmodSync, copyFileSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, readlinkSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { buildPackage, buildReleasePackage, buildRepository, readRepositoryIndex, signPackage } from './rpm-repository.mjs';

const fedora = process.platform === 'linux' ? readFileSync('/etc/os-release', 'utf8') : '';
const fedora44 = /^ID=fedora$/m.test(fedora) && /^VERSION_ID=44$/m.test(fedora);

function command(name, args, options = {}) {
  const result = spawnSync(name, args, { encoding: 'utf8', ...options });
  assert.equal(result.status, 0, `${name}: ${result.stderr}`);
  return result.stdout;
}

function manifest(root) {
  const entries = [];
  const visit = (directory, relative = '') => {
    for (const entry of readdirSync(directory)) {
      const name = relative ? `${relative}/${entry}` : entry;
      const path = join(directory, entry);
      const stat = lstatSync(path);
      entries.push([name, stat.isDirectory() ? 'directory' : stat.isSymbolicLink() ? 'symlink' : 'file', stat.mode & 0o777, stat.isSymbolicLink() ? readlinkSync(path) : stat.isFile() ? createHash('sha256').update(readFileSync(path)).digest('hex') : null]);
      if (stat.isDirectory()) visit(path, name);
    }
  };
  visit(root);
  return entries;
}

function primaryPackages(repository) {
  return JSON.parse(command('python3', ['-c', `import gzip, json, pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
repo = '{http://linux.duke.edu/metadata/repo}'
common = '{http://linux.duke.edu/metadata/common}'
data = next(item for item in ET.parse(root / 'repodata/repomd.xml').getroot().findall(repo + 'data') if item.attrib['type'] == 'primary')
path = root / data.find(repo + 'location').attrib['href']
print(json.dumps(sorted(pathlib.PurePosixPath(item.find(common + 'location').attrib['href']).name for item in ET.fromstring(gzip.decompress(path.read_bytes())).findall(common + 'package'))))`, repository]));
}

test('real Fedora tools build a private-runtime RPM with only external ELF requirements', { skip: !fedora44 }, () => {
  const root = mkdtempSync(join(tmpdir(), 'norm-rpm-integration-'));
  try {
    const source = join(root, 'source');
    const assets = join(root, 'assets');
    const packages = join(root, 'packages');
    mkdirSync(join(source, 'norm', 'bin'), { recursive: true });
    mkdirSync(join(source, 'norm', 'runtime', 'bin'), { recursive: true });
    mkdirSync(join(source, 'norm', 'lib'), { recursive: true });
    chmodSync(join(source, 'norm', 'lib'), 0o775);
    mkdirSync(assets);
    writeFileSync(join(source, 'norm', 'bin', 'norm'), '#!/bin/sh\nprintf "norm 1.2.3\\n"\n');
    chmodSync(join(source, 'norm', 'bin', 'norm'), 0o755);
    writeFileSync(join(source, 'private.c'), 'int norm_private(void) { return 1; }\n');
    command('cc', ['-shared', '-fPIC', '-Wl,-soname,libnormprivate.so', '-o', join(source, 'norm', 'runtime', 'bin', 'libnormprivate.so'), join(source, 'private.c')]);
    writeFileSync(join(source, 'java.c'), '#include <stdio.h>\nint norm_private(void); int main(void) { printf("%d\\n", norm_private()); return 0; }\n');
    command('cc', ['-o', join(source, 'norm', 'runtime', 'bin', 'java'), join(source, 'java.c'), '-L' + join(source, 'norm', 'runtime', 'bin'), '-lnormprivate', '-Wl,-rpath,$ORIGIN']);
    writeFileSync(join(source, 'norm', 'lib', 'compiler.jar'), 'jar');
    symlinkSync('compiler.jar', join(source, 'norm', 'lib', 'compiler-link.jar'));
    const archive = join(assets, 'norm-v1.2.3-linux-x64.tar.gz');
    command('tar', ['-C', source, '-czf', archive, 'norm']);
    writeFileSync(join(assets, 'SHA256SUMS'), `${createHash('sha256').update(readFileSync(archive)).digest('hex')}  norm-v1.2.3-linux-x64.tar.gz\n`);
    const altered = join(root, 'altered');
    mkdirSync(altered);
    copyFileSync(join(assets, 'SHA256SUMS'), join(altered, 'SHA256SUMS'));
    writeFileSync(join(altered, 'norm-v1.2.3-linux-x64.tar.gz'), 'altered');
    assert.throws(() => buildPackage('1.2.3', altered, join(root, 'rejected')), /checksum/i);
    const rpm = buildPackage('1.2.3', assets, packages);
    const repeated = buildPackage('1.2.3', assets, join(root, 'repeated-packages'));
    assert.equal(createHash('sha256').update(readFileSync(rpm)).digest('hex'), createHash('sha256').update(readFileSync(repeated)).digest('hex'));
    assert.equal(command('rpm', ['-qp', '--qf', '%{NAME} %{VERSION} %{ARCH}', rpm]), 'normlang 1.2.3 x86_64');
    assert.match(command('rpm', ['-qlp', rpm]), /\/usr\/lib\/normlang\/runtime\/bin\/java/);
    assert.match(command('rpm', ['-qlp', rpm]), /\/usr\/bin\/norm/);
    const requires = command('rpm', ['-qp', '--requires', rpm]);
    const provides = command('rpm', ['-qp', '--provides', rpm]);
    assert.match(requires, /libc\.so\.6/);
    assert.doesNotMatch(requires, /libnormprivate\.so/);
    assert.doesNotMatch(provides, /libnormprivate\.so/);
    const extracted = join(root, 'extracted');
    mkdirSync(extracted);
    const extraction = spawnSync('bash', ['-o', 'pipefail', '-c', 'umask 000; rpm2cpio "$1" | cpio -idm --quiet', 'bash', rpm], { cwd: extracted, encoding: 'utf8' });
    assert.equal(extraction.status, 0, extraction.stderr);
    assert.deepEqual(manifest(join(extracted, 'usr', 'lib', 'normlang')), manifest(join(source, 'norm')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('real Fedora tools build an independently versioned repository configuration RPM', { skip: !fedora44 }, () => {
  const root = mkdtempSync(join(tmpdir(), 'norm-rpm-release-'));
  const previousGpgHome = process.env.GNUPGHOME;
  try {
    const home = join(root, 'gnupg');
    mkdirSync(home, { mode: 0o700 });
    process.env.GNUPGHOME = home;
    const env = { ...process.env, GNUPGHOME: home };
    command('gpg', ['--batch', '--pinentry-mode', 'loopback', '--passphrase', '', '--quick-generate-key', 'Norm RPM Test <norm-rpm-test@example.invalid>', 'ed25519', 'cert', '1d'], { env });
    const fingerprints = command('gpg', ['--with-colons', '--list-keys'], { env }).split('\n');
    const fingerprint = fingerprints.find(line => line.startsWith('fpr:'))?.split(':')[9];
    assert.match(fingerprint, /^[A-F0-9]{40}$/);
    command('gpg', ['--batch', '--pinentry-mode', 'loopback', '--passphrase', '', '--quick-add-key', fingerprint, 'ed25519', 'sign', '1d'], { env });
    const publicKey = join(root, 'public.asc');
    writeFileSync(publicKey, command('gpg', ['--armor', '--export', fingerprint], { env }));
    const signingSubkey = command('gpg', ['--show-keys', '--with-colons', publicKey], { env }).split('\n').filter(line => line.startsWith('fpr:'))[1]?.split(':')[9];
    assert.match(signingSubkey, /^[A-F0-9]{40}$/);
    const subkey = join(root, 'subkey.asc');
    writeFileSync(subkey, command('gpg', ['--armor', '--export-secret-subkeys', fingerprint], { env }));
    const signingHome = join(root, 'signing-gnupg');
    mkdirSync(signingHome, { mode: 0o700 });
    process.env.GNUPGHOME = signingHome;
    command('gpg', ['--batch', '--import', subkey], { env: process.env });
    const privateRecords = command('gpg', ['--with-colons', '--list-secret-keys', fingerprint], { env: process.env }).split('\n').map(line => line.split(':'));
    assert.ok(privateRecords.some(fields => fields[0] === 'sec' && fields[14] === '#'));
    assert.ok(privateRecords.some(fields => fields[0] === 'ssb' && fields[14] === '+'));
    const config = join(root, 'release.json');
    const output = join(root, 'packages');
    const baseurl = 'https://normlanguage.github.io/rpm/fedora/44/$basearch';
    const keyurl = 'https://normlanguage.github.io/rpm/RPM-GPG-KEY-normlang';
    writeFileSync(config, JSON.stringify({ packageVersion: '1', packageRelease: '1', fingerprint, baseurl, keyurl }));
    const release = buildReleasePackage(config, publicKey, output);
    assert.equal(command('rpm', ['-qp', '--qf', '%{NAME} %{VERSION} %{RELEASE} %{ARCH}', release]), 'normlang-release 1 1 noarch');
    const extracted = join(root, 'extracted');
    mkdirSync(extracted);
    const extraction = spawnSync('bash', ['-o', 'pipefail', '-c', 'rpm2cpio "$1" | cpio -idm --quiet', 'bash', release], { cwd: extracted, encoding: 'utf8' });
    assert.equal(extraction.status, 0, extraction.stderr);
    const repo = readFileSync(join(extracted, 'etc', 'yum.repos.d', 'normlang.repo'), 'utf8');
    assert.match(repo, /gpgcheck=1\nrepo_gpgcheck=1\n/);
    assert.ok(repo.includes(`gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-normlang ${keyurl}\n`));
    assert.ok(repo.includes(`baseurl=${baseurl}\n`));
    assert.equal(readFileSync(join(extracted, 'etc', 'pki', 'rpm-gpg', 'RPM-GPG-KEY-normlang'), 'utf8'), readFileSync(publicKey, 'utf8'));
    assert.throws(() => buildReleasePackage(config, publicKey, output), /Immutable package already exists/);
    writeFileSync(config, JSON.stringify({ packageVersion: '1', packageRelease: '2', fingerprint, baseurl, keyurl }));
    const updated = buildReleasePackage(config, publicKey, output);
    assert.equal(command('rpm', ['-qp', '--qf', '%{NAME} %{VERSION} %{RELEASE} %{ARCH}', updated]), 'normlang-release 1 2 noarch');
    assert.match(command('rpm', ['-qp', '--qf', '[%{FILENAMES} %{FILEFLAGS}\\n]', updated]), /\/etc\/yum\.repos\.d\/normlang\.repo 17/);
    writeFileSync(config, JSON.stringify({ packageVersion: '1', packageRelease: '3', fingerprint, baseurl, keyurl }));
    const third = buildReleasePackage(config, publicKey, output);
    writeFileSync(config, JSON.stringify({ packageVersion: '1', packageRelease: '4', fingerprint, baseurl, keyurl }));
    const fourth = buildReleasePackage(config, publicKey, output);
    const signed = join(root, 'signed');
    const oldSigned = signPackage(release, publicKey, fingerprint, signed);
    const newSigned = signPackage(updated, publicKey, fingerprint, signed);
    const thirdSigned = signPackage(third, publicKey, fingerprint, signed);
    const fourthSigned = signPackage(fourth, publicKey, fingerprint, signed);
    assert.throws(() => buildRepository([release], publicKey, fingerprint, join(root, 'unsigned-repo')), error => {
      assert.match(error.message, /rpmkeys failed:[\s\S]*digests SIGNATURES NOT OK/);
      console.log(JSON.stringify({ rejection: 'unsigned', diagnostic: error.message }));
      return true;
    });
    const tampered = join(root, 'tampered.rpm');
    const damaged = readFileSync(oldSigned);
    damaged[damaged.length - 100] ^= 1;
    writeFileSync(tampered, damaged);
    assert.throws(() => buildRepository([tampered], publicKey, fingerprint, join(root, 'tampered-repo')), error => {
      assert.match(error.message, /rpmkeys failed:[\s\S]*DIGESTS SIGNATURES NOT OK/);
      console.log(JSON.stringify({ rejection: 'tampered', diagnostic: error.message }));
      return true;
    });
    const otherHome = join(root, 'other-gnupg');
    mkdirSync(otherHome, { mode: 0o700 });
    command('gpg', ['--batch', '--pinentry-mode', 'loopback', '--passphrase', '', '--quick-generate-key', 'Other RPM Test <other-rpm-test@example.invalid>', 'ed25519', 'sign', '1d'], { env: { ...process.env, GNUPGHOME: otherHome } });
    const otherFingerprint = command('gpg', ['--with-colons', '--list-keys'], { env: { ...process.env, GNUPGHOME: otherHome } }).split('\n').find(line => line.startsWith('fpr:'))?.split(':')[9];
    const otherKey = join(root, 'other.asc');
    writeFileSync(otherKey, command('gpg', ['--armor', '--export', otherFingerprint], { env: { ...process.env, GNUPGHOME: otherHome } }));
    const mixedKey = join(root, 'mixed.asc');
    writeFileSync(mixedKey, readFileSync(publicKey, 'utf8') + readFileSync(otherKey, 'utf8'));
    assert.throws(() => buildReleasePackage(config, mixedKey, join(root, 'mixed-packages')), /exactly one primary key/);
    process.env.GNUPGHOME = otherHome;
    const otherSigned = signPackage(release, otherKey, otherFingerprint, join(root, 'other-signed'));
    process.env.GNUPGHOME = signingHome;
    assert.throws(() => buildRepository([otherSigned], publicKey, fingerprint, join(root, 'other-repo')), error => {
      assert.match(error.message, /rpmkeys failed:[\s\S]*digests SIGNATURES NOT OK/);
      console.log(JSON.stringify({ rejection: 'wrong-key', diagnostic: error.message }));
      return true;
    });
    const rpmdb = join(root, 'rpmdb');
    command('rpmdb', ['--dbpath', rpmdb, '--initdb']);
    command('rpmkeys', ['--dbpath', rpmdb, '--import', publicKey]);
    const verification = command('rpmkeys', ['--dbpath', rpmdb, '--define', '_pkgverify_level all', '--checksig', oldSigned]);
    assert.match(verification, /OK/);
    console.log(JSON.stringify({ verifiedPrimary: fingerprint, signingSubkey, packageVerification: verification.trim() }));
    const oldRepo = buildRepository([oldSigned, newSigned], publicKey, fingerprint, join(root, 'old-repo'));
    const newRepo = buildRepository([newSigned, thirdSigned], publicKey, fingerprint, join(root, 'new-repo'), oldRepo);
    const newestRepo = buildRepository([thirdSigned, fourthSigned], publicKey, fingerprint, join(root, 'newest-repo'), newRepo);
    assert.deepEqual(readRepositoryIndex(oldRepo, publicKey, fingerprint).packages.map(value => value.path).sort(), [oldSigned, newSigned].map(path => `pool/${path.split(/[\\/]/).at(-1)}`).sort());
    const keyring = join(root, 'keyring.gpg');
    command('gpg', ['--batch', '--dearmor', '--output', keyring, publicKey]);
    command('gpgv', ['--keyring', keyring, join(newRepo, 'repodata', 'repomd.xml.asc'), join(newRepo, 'repodata', 'repomd.xml')]);
    const metadata = readdirSync(join(oldRepo, 'repodata')).filter(name => name !== 'repomd.xml' && name !== 'repomd.xml.asc');
    for (const name of metadata) assert.ok(readdirSync(join(newRepo, 'repodata')).includes(name));
    for (const name of metadata) assert.ok(!readdirSync(join(newestRepo, 'repodata')).includes(name));
    assert.deepEqual(primaryPackages(oldRepo), [oldSigned, newSigned].map(path => path.split(/[\\/]/).at(-1)).sort());
    assert.deepEqual(primaryPackages(newRepo), [newSigned, thirdSigned].map(path => path.split(/[\\/]/).at(-1)).sort());
    assert.deepEqual(primaryPackages(newestRepo), [thirdSigned, fourthSigned].map(path => path.split(/[\\/]/).at(-1)).sort());
    assert.deepEqual(readdirSync(join(newRepo, 'pool')).sort(), [oldSigned, newSigned, thirdSigned].map(path => path.split(/[\\/]/).at(-1)).sort());
    assert.deepEqual(readdirSync(join(newestRepo, 'pool')).sort(), [newSigned, thirdSigned, fourthSigned].map(path => path.split(/[\\/]/).at(-1)).sort());
    assert.equal(createHash('sha256').update(readFileSync(join(newRepo, 'pool', oldSigned.split(/[\\/]/).at(-1)))).digest('hex'), createHash('sha256').update(readFileSync(oldSigned)).digest('hex'));
    const alternateKey = join(root, 'alternate-public.asc');
    copyFileSync(publicKey, alternateKey);
    appendFileSync(alternateKey, '\n');
    writeFileSync(config, JSON.stringify({ packageVersion: '1', packageRelease: '3', fingerprint, baseurl, keyurl }));
    const alternate = buildReleasePackage(config, alternateKey, join(root, 'alternate-packages'));
    const alternateSigned = signPackage(alternate, publicKey, fingerprint, join(root, 'alternate-signed'));
    assert.throws(() => buildRepository([alternateSigned, fourthSigned], publicKey, fingerprint, join(root, 'replacement-repo'), newRepo), /Immutable RPM changed/);
    writeFileSync(join(newRepo, 'pool', 'stray.rpm'), 'unindexed');
    const cleanRepo = buildRepository([thirdSigned, fourthSigned], publicKey, fingerprint, join(root, 'clean-repo'), newRepo);
    assert.ok(!readdirSync(join(cleanRepo, 'pool')).includes('stray.rpm'));
    assert.throws(() => buildRepository([oldSigned], publicKey, fingerprint, join(root, 'new-repo'), oldRepo), /already exists/i);
  } finally {
    if (previousGpgHome === undefined) delete process.env.GNUPGHOME;
    else process.env.GNUPGHOME = previousGpgHome;
    rmSync(root, { recursive: true, force: true });
  }
});
