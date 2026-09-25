import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { chmodSync, closeSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, openSync, readFileSync, readSync, readdirSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { releaseTargets, releaseVersion } from './release-model.mjs';
import { verifiedReleaseAsset } from './release-assets.mjs';

const debianArchitecture = 'amd64';
const linuxTarget = releaseTargets.find(value => value.target === 'linux-x64');
if (linuxTarget?.platform !== 'linux-x64') throw new Error('Unsupported Debian release target');

function execute(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) throw new Error(`${command} failed: ${result.error?.message ?? result.stderr}`);
  return result.stdout;
}

function digest(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function verifyAsset(version, assetsDirectory) {
  const { path: archive } = verifiedReleaseAsset(version, 'linux-x64', assetsDirectory);
  const entries = execute('tar', ['-tzf', archive]).trim().split(/\r?\n/);
  if (!entries.length || entries.some(entry => !entry.startsWith('norm/') || entry.split('/').includes('..') || entry.includes('\\'))) {
    throw new Error('Invalid Linux release archive layout');
  }
  return archive;
}

function elfFiles(root) {
  const files = [];
  const directories = new Set();
  const visit = directory => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) visit(path);
      else if (entry.isFile()) {
        const descriptor = openSync(path, 'r');
        const signature = Buffer.alloc(4);
        const size = readSync(descriptor, signature, 0, 4, 0);
        closeSync(descriptor);
        if (size === 4 && signature.equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46]))) {
          files.push(path);
          directories.add(dirname(path));
        }
      }
    }
  };
  visit(root);
  return { files, directories: [...directories] };
}

export function buildPackage(version, assetsDirectory, outputDirectory) {
  releaseVersion(version);
  assetsDirectory = resolve(assetsDirectory);
  outputDirectory = resolve(outputDirectory);
  const archive = verifyAsset(version, assetsDirectory);
  mkdirSync(outputDirectory, { recursive: true });
  const packagePath = join(outputDirectory, `normlang_${version}_${debianArchitecture}.deb`);
  if (existsSync(packagePath)) throw new Error(`Immutable package already exists: ${packagePath}`);
  const stage = mkdtempSync(join(outputDirectory, '.stage-'));
  const runtime = join(stage, 'usr', 'lib', 'normlang');
  try {
    execute('tar', ['--same-permissions', '-xzf', archive, '-C', stage]);
    const extracted = join(stage, 'norm');
    if (!existsSync(join(extracted, 'bin', 'norm')) || !existsSync(join(extracted, 'runtime', 'bin', 'java')) || !existsSync(join(extracted, 'lib'))) {
      throw new Error('Incomplete Linux release runtime');
    }
    mkdirSync(dirname(runtime), { recursive: true });
    renameSync(extracted, runtime);
    const bin = join(stage, 'usr', 'bin');
    mkdirSync(bin, { recursive: true });
    for (const directory of [join(stage, 'usr'), dirname(runtime), bin]) chmodSync(directory, 0o755);
    const wrapper = join(bin, 'norm');
    writeFileSync(wrapper, '#!/bin/sh\nexec /usr/lib/normlang/bin/norm "$@"\n');
    chmodSync(wrapper, 0o755);
    chmodSync(join(runtime, 'bin', 'norm'), 0o755);
    const reportedVersion = execute(join(runtime, 'bin', 'norm'), ['--version']).trim();
    if (reportedVersion !== `norm ${version}`) throw new Error(`Release runtime version mismatch: ${reportedVersion}`);
    const { files, directories } = elfFiles(runtime);
    if (!files.length) throw new Error('Linux runtime has no ELF files');
    const debian = join(stage, 'debian');
    mkdirSync(debian);
    writeFileSync(join(debian, 'control'), `Source: normlang\nMaintainer: w0fv1 <wofbi1@outlook.com>\n\nPackage: normlang\nArchitecture: ${debianArchitecture}\nDescription: Norm programming language and toolchain\n`);
    const dependencies = execute('dpkg-shlibdeps', ['-O', ...directories.map(path => `-l${path}`), ...files.map(path => `-e${path}`)], { cwd: stage });
    rmSync(debian, { recursive: true });
    const match = /^shlibs:Depends=(.+)$/m.exec(dependencies);
    if (!match) throw new Error('dpkg-shlibdeps returned no runtime dependencies');
    const control = join(stage, 'DEBIAN');
    mkdirSync(control);
    writeFileSync(join(control, 'control'), `Package: normlang\nVersion: ${version}\nArchitecture: ${debianArchitecture}\nMaintainer: w0fv1 <wofbi1@outlook.com>\nSection: devel\nPriority: optional\nDepends: ${match[1]}\nDescription: Norm programming language and toolchain\n Self-contained Norm CLI, language server, and Java runtime.\n`);
    execute('dpkg-deb', ['--build', '--root-owner-group', stage, packagePath], { env: { ...process.env, SOURCE_DATE_EPOCH: '0' } });
    return packagePath;
  } finally {
    rmSync(stage, { recursive: true, force: true });
  }
}

export function buildRepository(packageDirectory, repositoryDirectory, signingKey) {
  if (!signingKey) throw new Error('A GPG signing key is required');
  packageDirectory = resolve(packageDirectory);
  repositoryDirectory = resolve(repositoryDirectory);
  const packages = readdirSync(packageDirectory).filter(name => new RegExp(`^normlang_\\d+\\.\\d+\\.\\d+_${debianArchitecture}\\.deb$`).test(name));
  if (!packages.length) throw new Error('No Norm Debian packages found');
  const pool = join(repositoryDirectory, 'pool', 'main', 'n', 'normlang');
  mkdirSync(pool, { recursive: true });
  for (const name of packages) {
    const source = join(packageDirectory, name);
    const destination = join(pool, name);
    if (existsSync(destination)) {
      if (digest(source) !== digest(destination)) throw new Error(`Immutable package changed: ${name}`);
    } else copyFileSync(source, destination);
  }
  const directory = join(repositoryDirectory, 'dists', 'stable', 'main', `binary-${debianArchitecture}`);
  mkdirSync(directory, { recursive: true });
  const packageIndex = execute('apt-ftparchive', ['packages', 'pool'], { cwd: repositoryDirectory });
  writeFileSync(join(directory, 'Packages'), packageIndex);
  const releaseDirectory = join(repositoryDirectory, 'dists', 'stable');
  const metadata = execute('apt-ftparchive', ['-o', 'APT::FTPArchive::Release::Origin=NormLanguage', '-o', 'APT::FTPArchive::Release::Label=Norm', '-o', 'APT::FTPArchive::Release::Suite=stable', '-o', 'APT::FTPArchive::Release::Codename=stable', '-o', `APT::FTPArchive::Release::Architectures=${debianArchitecture}`, '-o', 'APT::FTPArchive::Release::Components=main', 'release', 'dists/stable'], { cwd: repositoryDirectory });
  const release = join(releaseDirectory, 'Release');
  writeFileSync(release, metadata);
  execute('gpg', ['--batch', '--yes', '--local-user', signingKey, '--armor', '--detach-sign', '--output', join(releaseDirectory, 'Release.gpg'), release]);
  execute('gpg', ['--batch', '--yes', '--local-user', signingKey, '--clearsign', '--output', join(releaseDirectory, 'InRelease'), release]);
  writeFileSync(join(repositoryDirectory, 'normlang-archive-keyring.asc'), execute('gpg', ['--batch', '--armor', '--export', signingKey]));
  return repositoryDirectory;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [, , mode, ...args] = process.argv;
  if (mode === 'package' && args.length === 3) console.log(buildPackage(...args));
  else if (mode === 'repository' && args.length === 3) console.log(buildRepository(...args));
  else throw new Error('Usage: apt-repository.mjs package <version> <release-assets> <package-output> | repository <package-directory> <repository-output> <gpg-key>');
}
