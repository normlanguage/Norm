import { createHash } from 'node:crypto';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import yauzl from 'yauzl';
import {
  releaseTargets,
  releaseVersion,
  targetExecutable,
  verifyCliVersion,
} from './release-package.mjs';

if (process.argv.length !== 5) {
  throw new Error('Usage: verify-vsix.mjs <version> <binaries> <vsix>');
}

const version = releaseVersion(process.argv[2]);
const binaries = resolve(process.argv[3]);
const vsix = resolve(process.argv[4]);
const targets = releaseTargets.map(({ target }) => target);
const executableEntries = targets.map(
  (target) => `extension/bin/${target}/${targetExecutable(target)}`,
);
const entries = await readEntries(vsix, [
  'extension/package.json',
  'extension.vsixmanifest',
  'extension/images/norm-256.png',
  'extension/images/norm-file.png',
  ...executableEntries,
]);
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
  const executable = targetExecutable(target);
  const embeddedEntry = entries.get(executableEntries[index]);
  if (executable !== 'norm.exe' && ((embeddedEntry.attributes >>> 16) & 0o111) === 0) {
    throw new Error(`VSIX embedded CLI is not executable: ${target}`);
  }
  const binary = join(binaries, `native-${target}`, executable);
  const expectedHash = createHash('sha256').update(readFileSync(binary)).digest('hex');
  const embeddedHash = createHash('sha256').update(embeddedEntry.content).digest('hex');
  if (embeddedHash !== expectedHash) {
    throw new Error(`VSIX embedded CLI hash mismatch for ${target}`);
  }
}
const hostTarget = `${process.platform}-${process.arch}`;
const hostExecutable = targetExecutable(hostTarget);
const embedded = entries.get(`extension/bin/${hostTarget}/${hostExecutable}`).content;
const directory = mkdtempSync(join(tmpdir(), 'norm-vsix-'));
try {
  const extracted = join(directory, hostExecutable);
  writeFileSync(extracted, embedded);
  chmodSync(extracted, 0o755);
  verifyCliVersion(extracted, version);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
console.log(`Universal VSIX verified with all embedded Norm ${version} CLIs.`);

function readEntries(path, names) {
  return new Promise((resolvePromise, reject) => {
    yauzl.open(path, { lazyEntries: true }, (openError, archive) => {
      if (openError) return reject(openError);
      const expected = new Set(names);
      const result = new Map();
      archive.on('error', reject);
      archive.on('entry', (entry) => {
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
        const missing = names.filter((name) => !result.has(name));
        if (missing.length > 0) return reject(new Error(`VSIX is missing: ${missing.join(', ')}`));
        resolvePromise(result);
      });
      archive.readEntry();
    });
  });
}
