import { createHash } from 'node:crypto';
import {
  chmodSync,
  createWriteStream,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve, sep } from 'node:path';
import yauzl from 'yauzl';
import {
  releaseTargets,
  releaseVersion,
  targetLauncher,
  targetRuntimeJava,
  verifyCliVersion,
} from './release-package.mjs';

if (process.argv.length !== 5) {
  throw new Error('Usage: verify-vsix.mjs <version> <binaries> <vsix>');
}

const version = releaseVersion(process.argv[2]);
const binaries = resolve(process.argv[3]);
const vsix = resolve(process.argv[4]);
const targets = releaseTargets.map(({ target }) => target);
const launcherEntries = targets.map(
  (target) => `extension/bin/${target}/${targetLauncher(target).replaceAll('\\', '/')}`,
);
const runtimeEntries = targets.map(
  (target) => `extension/bin/${target}/${targetRuntimeJava(target).replaceAll('\\', '/')}`,
);
const compilerEntries = targets.map(
  (target) => `extension/bin/${target}/norm/lib/compiler-${version}.jar`,
);
const entries = await readEntries(vsix, [
  'extension/package.json',
  'extension.vsixmanifest',
  'extension/images/norm-256.png',
  'extension/images/norm-file.png',
  ...launcherEntries,
  ...runtimeEntries,
  ...compilerEntries,
], ['extension/server/']);
const extensionPackage = JSON.parse(entries.get('extension/package.json').content.toString('utf8'));
if (extensionPackage.version !== version) {
  throw new Error(
    `VSIX package version mismatch: expected ${version}, received ${extensionPackage.version}`,
  );
}
if (
  extensionPackage.icon !== 'images/norm-256.png' ||
  extensionPackage.contributes.languages[0].icon.light !== './images/norm-file.png' ||
  extensionPackage.contributes.languages[0].icon.dark !== './images/norm-file.png'
) {
  throw new Error('VSIX package does not declare the Norm extension and language icons');
}
const manifest = entries.get('extension.vsixmanifest').content.toString('utf8');
if (!manifest.includes(`Version="${version}"`) || manifest.includes('TargetPlatform=')) {
  throw new Error(`VSIX manifest is not universal version ${version}`);
}
for (const [index, target] of targets.entries()) {
  const embeddedEntry = entries.get(launcherEntries[index]);
  if (target !== 'win32-x64' && ((embeddedEntry.attributes >>> 16) & 0o111) === 0) {
    throw new Error(`VSIX embedded CLI is not executable: ${target}`);
  }
  const embeddedRuntime = entries.get(runtimeEntries[index]);
  if (target !== 'win32-x64' && ((embeddedRuntime.attributes >>> 16) & 0o111) === 0) {
    throw new Error(`VSIX embedded runtime is not executable: ${target}`);
  }
  for (const [entryName, source] of [
    [launcherEntries[index], join(binaries, `runtime-${target}`, targetLauncher(target))],
    [runtimeEntries[index], join(binaries, `runtime-${target}`, targetRuntimeJava(target))],
    [
      compilerEntries[index],
      join(binaries, `runtime-${target}`, 'norm', 'lib', `compiler-${version}.jar`),
    ],
  ]) {
    const expectedHash = createHash('sha256').update(readFileSync(source)).digest('hex');
    const embeddedHash = createHash('sha256').update(entries.get(entryName).content).digest('hex');
    if (embeddedHash !== expectedHash) {
      throw new Error(`VSIX embedded runtime hash mismatch for ${target}: ${entryName}`);
    }
  }
}
const hostTarget = `${process.platform}-${process.arch}`;
const directory = mkdtempSync(join(tmpdir(), 'norm-vsix-'));
try {
  await extractPrefix(vsix, `extension/bin/${hostTarget}/norm/`, directory);
  const launcher = join(
    directory,
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  chmodSync(launcher, 0o755);
  verifyCliVersion(launcher, version);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
console.log(`Universal VSIX verified with all embedded Norm ${version} CLIs.`);

function extractPrefix(path, prefix, destinationRoot) {
  return new Promise((resolvePromise, reject) => {
    yauzl.open(path, { lazyEntries: true }, (openError, archive) => {
      if (openError) return reject(openError);
      archive.on('error', reject);
      archive.on('entry', (entry) => {
        if (!entry.fileName.startsWith(prefix)) return archive.readEntry();
        const relative = entry.fileName.slice(prefix.length);
        if (!relative) return archive.readEntry();
        const destination = resolve(destinationRoot, relative);
        if (!destination.startsWith(resolve(destinationRoot) + sep)) {
          return reject(new Error(`Invalid VSIX runtime entry: ${entry.fileName}`));
        }
        if (entry.fileName.endsWith('/')) {
          mkdirSync(destination, { recursive: true });
          return archive.readEntry();
        }
        mkdirSync(dirname(destination), { recursive: true });
        archive.openReadStream(entry, (streamError, stream) => {
          if (streamError) return reject(streamError);
          const output = createWriteStream(destination);
          stream.on('error', reject);
          output.on('error', reject);
          output.on('close', () => {
            const mode = (entry.externalFileAttributes >>> 16) & 0o777;
            if (mode) chmodSync(destination, mode);
            archive.readEntry();
          });
          stream.pipe(output);
        });
      });
      archive.on('end', resolvePromise);
      archive.readEntry();
    });
  });
}

function readEntries(path, names, forbiddenPrefixes) {
  return new Promise((resolvePromise, reject) => {
    yauzl.open(path, { lazyEntries: true }, (openError, archive) => {
      if (openError) return reject(openError);
      const expected = new Set(names);
      const result = new Map();
      let forbiddenEntry;
      archive.on('error', reject);
      archive.on('entry', (entry) => {
        if (forbiddenPrefixes.some((prefix) => entry.fileName.startsWith(prefix))) {
          forbiddenEntry = entry.fileName;
        }
        if (!expected.has(entry.fileName)) return archive.readEntry();
        archive.openReadStream(entry, (streamError, stream) => {
          if (streamError) return reject(streamError);
          const chunks = [];
          stream.on('data', (chunk) => chunks.push(chunk));
          stream.on('error', reject);
          stream.on('end', () => {
            result.set(entry.fileName, {
              content: Buffer.concat(chunks),
              attributes: entry.externalFileAttributes,
            });
            archive.readEntry();
          });
        });
      });
      archive.on('end', () => {
        if (forbiddenEntry) {
          return reject(new Error(`VSIX contains forbidden release entry: ${forbiddenEntry}`));
        }
        const missing = names.filter((name) => !result.has(name));
        if (missing.length > 0) return reject(new Error(`VSIX is missing: ${missing.join(', ')}`));
        resolvePromise(result);
      });
      archive.readEntry();
    });
  });
}
