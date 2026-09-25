import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { elfFiles, stagePrivateRuntime } from './linux-runtime.mjs';
import { releaseVersion } from './release-model.mjs';

function execute(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) throw new Error(`${command} failed: ${result.error?.message ?? `${result.stdout}\n${result.stderr}`}`);
  return result.stdout;
}

function publicKeyFingerprint(path) {
  const records = execute('gpg', ['--show-keys', '--with-colons', path]).split('\n');
  if (records.filter(line => line.startsWith('pub:')).length !== 1) throw new Error('RPM public key must contain exactly one primary key');
  const fingerprint = records.find(line => line.startsWith('fpr:'))?.split(':')[9];
  if (!/^[A-F0-9]{40}$/.test(fingerprint ?? '')) throw new Error('Invalid RPM public key fingerprint');
  return fingerprint;
}

function buildRpm(topdir, specPath) {
  execute('rpmbuild', ['-bb', specPath, '--define', `_topdir ${topdir}`, '--define', '_buildhost normlanguage.org', '--define', '_buildtime 0', '--define', 'build_mtime_policy clamp_to_source_date_epoch', '--define', '_rpmformat 4'], { env: { ...process.env, SOURCE_DATE_EPOCH: '0' } });
}

export function buildPackage(version, assetsDirectory, outputDirectory) {
  releaseVersion(version);
  assetsDirectory = resolve(assetsDirectory);
  outputDirectory = resolve(outputDirectory);
  mkdirSync(outputDirectory, { recursive: true });
  const packagePath = join(outputDirectory, `normlang-${version}-1.x86_64.rpm`);
  if (existsSync(packagePath)) throw new Error(`Immutable package already exists: ${packagePath}`);
  const workspace = mkdtempSync(join(outputDirectory, '.rpm-stage-'));
  const stage = join(workspace, 'payload');
  const topdir = join(workspace, 'rpmbuild');
  try {
    mkdirSync(stage);
    const runtime = stagePrivateRuntime(version, assetsDirectory, stage);
    const { files } = elfFiles(runtime);
    if (!files.length) throw new Error('Linux runtime has no ELF files');
    const rpmdeps = join(execute('rpm', ['--eval', '%{_rpmconfigdir}']).trim(), 'rpmdeps');
    const provides = new Set(execute(rpmdeps, ['--provides', ...files]).trim().split('\n').filter(Boolean));
    const requires = new Set(execute(rpmdeps, ['--requires', ...files]).trim().split('\n').filter(Boolean));
    const internal = [...requires].filter(value => provides.has(value)).sort();
    if (!internal.length) throw new Error('Private runtime has no internal ELF requirements');
    const escaped = internal.map(value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replaceAll('\\', '\\\\'));
    for (const directory of ['SOURCES', 'SPECS', 'RPMS']) mkdirSync(join(topdir, directory), { recursive: true });
    execute('tar', ['--sort=name', '--mtime=@0', '--owner=0', '--group=0', '--numeric-owner', '-C', stage, '-cf', join(topdir, 'SOURCES', 'payload.tar'), 'usr']);
    const spec = `Name: normlang
Version: ${version}
Release: 1
Summary: Norm programming language and toolchain
License: MPL-2.0
URL: https://github.com/normlanguage/Norm
Source0: payload.tar
BuildArch: x86_64
%global __provides_exclude_from ^/usr/lib/normlang/.*$
%global __requires_exclude ^(${escaped.join('|')})$
%global _enable_debug_packages 0
%global __os_install_post %{nil}

%description
Self-contained Norm CLI, language server, and Java runtime.

%install
mkdir -p %{buildroot}
tar --same-permissions -xf %{SOURCE0} -C %{buildroot}

%files
/usr/bin/norm
/usr/lib/normlang
`;
    const specPath = join(topdir, 'SPECS', 'normlang.spec');
    writeFileSync(specPath, spec);
    buildRpm(topdir, specPath);
    const built = join(topdir, 'RPMS', 'x86_64', `normlang-${version}-1.x86_64.rpm`);
    if (!existsSync(built)) throw new Error('RPM build did not produce the expected package');
    if (execute('rpm', ['-qp', '--qf', '%{NAME} %{VERSION} %{RELEASE} %{ARCH}', built]) !== `normlang ${version} 1 x86_64`) throw new Error('RPM package identity mismatch');
    renameSync(built, packagePath);
    return packagePath;
  } finally {
    rmSync(workspace, { recursive: true, force: true });
  }
}

export function buildReleasePackage(configPath, publicKeyPath, outputDirectory) {
  configPath = resolve(configPath);
  publicKeyPath = resolve(publicKeyPath);
  outputDirectory = resolve(outputDirectory);
  const config = JSON.parse(readFileSync(configPath, 'utf8'));
  if (!/^[1-9]\d*$/.test(config.packageVersion ?? '') || !/^[1-9]\d*$/.test(config.packageRelease ?? '') || !/^[a-fA-F0-9]{40}$/.test(config.fingerprint ?? '')) throw new Error('Invalid RPM release package identity');
  const baseurl = new URL(config.baseurl);
  if (!['https:', 'http:'].includes(baseurl.protocol) || !baseurl.pathname.endsWith('/$basearch') || baseurl.href !== config.baseurl || baseurl.username || baseurl.password || baseurl.search || baseurl.hash) throw new Error('Invalid RPM repository base URL');
  const keyurl = new URL(config.keyurl);
  if (!['https:', 'http:'].includes(keyurl.protocol) || keyurl.href !== config.keyurl || keyurl.username || keyurl.password || keyurl.search || keyurl.hash) throw new Error('Invalid RPM public key URL');
  const fingerprint = publicKeyFingerprint(publicKeyPath);
  if (fingerprint !== config.fingerprint) throw new Error('RPM release key fingerprint mismatch');
  mkdirSync(outputDirectory, { recursive: true });
  const name = `normlang-release-${config.packageVersion}-${config.packageRelease}.noarch.rpm`;
  const destination = join(outputDirectory, name);
  if (existsSync(destination)) throw new Error(`Immutable package already exists: ${destination}`);
  const workspace = mkdtempSync(join(outputDirectory, '.rpm-release-stage-'));
  const topdir = join(workspace, 'rpmbuild');
  try {
    for (const directory of ['SOURCES', 'SPECS', 'RPMS']) mkdirSync(join(topdir, directory), { recursive: true });
    const repo = `[normlang]\nname=Norm Language\nbaseurl=${config.baseurl}\nenabled=1\ngpgcheck=1\nrepo_gpgcheck=1\ngpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-normlang ${config.keyurl}\n`;
    writeFileSync(join(topdir, 'SOURCES', 'normlang.repo'), repo);
    copyFileSync(publicKeyPath, join(topdir, 'SOURCES', 'RPM-GPG-KEY-normlang'));
    const spec = `Name: normlang-release
Version: ${config.packageVersion}
Release: ${config.packageRelease}
Summary: Norm signed RPM repository configuration
License: MPL-2.0
URL: https://github.com/normlanguage/Norm
Source0: normlang.repo
Source1: RPM-GPG-KEY-normlang
BuildArch: noarch

%description
Norm RPM repository configuration and public signing key.

%install
install -D -m 0644 %{SOURCE0} %{buildroot}/etc/yum.repos.d/normlang.repo
install -D -m 0644 %{SOURCE1} %{buildroot}/etc/pki/rpm-gpg/RPM-GPG-KEY-normlang

%files
%config(noreplace) /etc/yum.repos.d/normlang.repo
/etc/pki/rpm-gpg/RPM-GPG-KEY-normlang
`;
    const specPath = join(topdir, 'SPECS', 'normlang-release.spec');
    writeFileSync(specPath, spec);
    buildRpm(topdir, specPath);
    const built = join(topdir, 'RPMS', 'noarch', name);
    if (!existsSync(built)) throw new Error('RPM release build did not produce the expected package');
    if (execute('rpm', ['-qp', '--qf', '%{NAME} %{VERSION} %{RELEASE} %{ARCH}', built]) !== `normlang-release ${config.packageVersion} ${config.packageRelease} noarch`) throw new Error('RPM release package identity mismatch');
    renameSync(built, destination);
    return destination;
  } finally {
    rmSync(workspace, { recursive: true, force: true });
  }
}

export function signPackage(packagePath, publicKeyPath, fingerprint, outputDirectory) {
  packagePath = resolve(packagePath);
  publicKeyPath = resolve(publicKeyPath);
  outputDirectory = resolve(outputDirectory);
  if (!/^[A-F0-9]{40}$/i.test(fingerprint)) throw new Error('Invalid RPM signing fingerprint');
  const publicFingerprint = publicKeyFingerprint(publicKeyPath);
  if (publicFingerprint !== fingerprint) throw new Error('RPM signing key fingerprint mismatch');
  execute('gpg', ['--list-secret-keys', fingerprint]);
  const identity = execute('rpm', ['-qp', '--qf', '%{NAME} %{VERSION} %{RELEASE} %{ARCH}', packagePath]).trim();
  if (!/^normlang(?:-release)? [^ ]+ [^ ]+ (?:x86_64|noarch)$/.test(identity)) throw new Error('Unexpected RPM identity');
  mkdirSync(outputDirectory, { recursive: true });
  const destination = join(outputDirectory, basename(packagePath));
  if (existsSync(destination)) throw new Error(`Immutable package already exists: ${destination}`);
  try {
    copyFileSync(packagePath, destination);
    execute('rpmsign', ['--addsign', `--key-id=${fingerprint}`, destination]);
    const workspace = mkdtempSync(join(outputDirectory, '.rpm-keyring-'));
    try {
      execute('rpmdb', ['--dbpath', workspace, '--initdb']);
      execute('rpmkeys', ['--dbpath', workspace, '--import', publicKeyPath]);
      execute('rpmkeys', ['--dbpath', workspace, '--define', '_pkgverify_level all', '--checksig', destination]);
    } finally {
      rmSync(workspace, { recursive: true, force: true });
    }
    return destination;
  } catch (error) {
    rmSync(destination, { force: true });
    throw error;
  }
}

export function readRepositoryIndex(repositoryDirectory, publicKeyPath, fingerprint) {
  repositoryDirectory = resolve(repositoryDirectory);
  publicKeyPath = resolve(publicKeyPath);
  if (publicKeyFingerprint(publicKeyPath) !== fingerprint) throw new Error('RPM repository key fingerprint mismatch');
  const workspace = mkdtempSync(join(resolve(repositoryDirectory, '..'), '.rpm-index-'));
  try {
    const keyring = join(workspace, 'keyring.gpg');
    execute('gpg', ['--batch', '--dearmor', '--output', keyring, publicKeyPath]);
    execute('gpgv', ['--keyring', keyring, join(repositoryDirectory, 'repodata', 'repomd.xml.asc'), join(repositoryDirectory, 'repodata', 'repomd.xml')]);
    return JSON.parse(execute('python3', ['-c', `import gzip, hashlib, json, pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
namespace = '{http://linux.duke.edu/metadata/repo}'
common = '{http://linux.duke.edu/metadata/common}'
packages = []
metadata = []
for data in ET.parse(root / 'repodata/repomd.xml').getroot().findall(namespace + 'data'):
    location = data.find(namespace + 'location').attrib['href']
    checksum = data.find(namespace + 'checksum')
    path = (root / location).resolve()
    if not path.is_relative_to(root.resolve()) or checksum.attrib['type'] != 'sha256' or hashlib.sha256(path.read_bytes()).hexdigest() != checksum.text:
        raise SystemExit('Previous RPM metadata checksum mismatch')
    metadata.append(location)
    if data.attrib['type'] == 'primary':
        if path.suffix != '.gz':
            raise SystemExit('Unsupported RPM primary metadata compression')
        for package in ET.fromstring(gzip.decompress(path.read_bytes())).findall(common + 'package'):
            location = package.find(common + 'location').attrib['href']
            digest = package.find(common + 'checksum')
            if pathlib.PurePosixPath(location).parts != ('pool', pathlib.PurePosixPath(location).name) or not location.endswith('.rpm') or digest.attrib['type'] != 'sha256':
                raise SystemExit('Invalid RPM package metadata')
            packages.append({'path': location, 'sha256': digest.text})
if not packages or len(set(package['path'] for package in packages)) != len(packages):
    raise SystemExit('Invalid RPM package index')
print(json.dumps({'packages': packages, 'metadata': metadata}))`, repositoryDirectory]));
  } finally {
    rmSync(workspace, { recursive: true, force: true });
  }
}

export function buildRepository(packagePaths, publicKeyPath, fingerprint, outputDirectory, previousRepositoryDirectory) {
  outputDirectory = resolve(outputDirectory);
  publicKeyPath = resolve(publicKeyPath);
  if (!Array.isArray(packagePaths) || !packagePaths.length || !/^[A-F0-9]{40}$/i.test(fingerprint)) throw new Error('Invalid RPM repository input');
  if (existsSync(outputDirectory)) throw new Error(`RPM repository already exists: ${outputDirectory}`);
  mkdirSync(resolve(outputDirectory, '..'), { recursive: true });
  const workspace = mkdtempSync(join(resolve(outputDirectory, '..'), '.rpm-repo-'));
  const keyring = join(workspace, 'keyring.gpg');
  const rpmdb = join(workspace, 'rpmdb');
  try {
    const publicFingerprint = publicKeyFingerprint(publicKeyPath);
    if (publicFingerprint !== fingerprint) throw new Error('RPM repository key fingerprint mismatch');
    execute('gpg', ['--batch', '--dearmor', '--output', keyring, publicKeyPath]);
    execute('rpmdb', ['--dbpath', rpmdb, '--initdb']);
    execute('rpmkeys', ['--dbpath', rpmdb, '--import', publicKeyPath]);
    const stage = join(workspace, 'repository');
    let published = {};
    if (previousRepositoryDirectory) {
      previousRepositoryDirectory = resolve(previousRepositoryDirectory);
      const previous = readRepositoryIndex(previousRepositoryDirectory, publicKeyPath, fingerprint);
      published = Object.fromEntries(previous.packages.map(value => [basename(value.path), value.sha256]));
      mkdirSync(stage);
      mkdirSync(join(stage, 'repodata'));
      for (const name of ['repodata/repomd.xml', 'repodata/repomd.xml.asc', ...previous.metadata]) {
        const source = join(previousRepositoryDirectory, name);
        const destination = join(stage, name);
        mkdirSync(resolve(destination, '..'), { recursive: true });
        copyFileSync(source, destination);
      }
    } else mkdirSync(stage);
    const pool = join(stage, 'pool');
    mkdirSync(pool, { recursive: true });
    for (const [name, expected] of Object.entries(published)) {
      const source = join(previousRepositoryDirectory, 'pool', name);
      const actual = createHash('sha256').update(readFileSync(source)).digest('hex');
      if (actual !== expected) throw new Error(`Published RPM checksum mismatch: ${name}`);
      execute('rpmkeys', ['--dbpath', rpmdb, '--define', '_pkgverify_level all', '--checksig', source]);
      copyFileSync(source, join(pool, name));
    }
    const included = new Set();
    for (const candidate of packagePaths) {
      const path = resolve(candidate);
      execute('rpmkeys', ['--dbpath', rpmdb, '--define', '_pkgverify_level all', '--checksig', path]);
      const name = basename(path);
      if (included.has(name)) throw new Error(`Duplicate RPM package: ${name}`);
      included.add(name);
      const destination = join(pool, name);
      const incoming = createHash('sha256').update(readFileSync(path)).digest('hex');
      if (published[name] && published[name] !== incoming) throw new Error(`Immutable RPM changed: ${name}`);
      if (!published[name]) copyFileSync(path, destination);
    }
    const packageList = join(workspace, 'packages.list');
    writeFileSync(packageList, [...included].sort().map(name => `pool/${name}\n`).join(''));
    execute('createrepo_c', ['--update', '--retain-old-md=1', '--general-compress-type=gz', '--pkglist', packageList, stage]);
    rmSync(join(stage, 'repodata', 'repomd.xml.asc'), { force: true });
    execute('gpg', ['--batch', '--armor', '--local-user', fingerprint, '--detach-sign', '--output', join(stage, 'repodata', 'repomd.xml.asc'), join(stage, 'repodata', 'repomd.xml')]);
    execute('gpgv', ['--keyring', keyring, join(stage, 'repodata', 'repomd.xml.asc'), join(stage, 'repodata', 'repomd.xml')]);
    renameSync(stage, outputDirectory);
    return outputDirectory;
  } finally {
    rmSync(workspace, { recursive: true, force: true });
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [, , mode, ...args] = process.argv;
  if (mode === 'package' && args.length === 3) console.log(buildPackage(...args));
  else if (mode === 'release-package' && args.length === 3) console.log(buildReleasePackage(...args));
  else if (mode === 'sign-package' && args.length === 4) console.log(signPackage(...args));
  else if (mode === 'repository' && (args.length === 4 || args.length === 5)) console.log(buildRepository(args[0].split(','), ...args.slice(1)));
  else throw new Error('Usage: rpm-repository.mjs package <version> <release-assets> <package-output> | release-package <config-json> <public-key> <package-output>');
}
