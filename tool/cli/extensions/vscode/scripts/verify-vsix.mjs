import { createHash } from 'node:crypto';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import yauzl from 'yauzl';
import { releaseVersion, targetExecutable, verifyCliVersion } from './release-package.mjs';

if (process.argv.length !== 6) {
  throw new Error('Usage: verify-vsix.mjs <version> <target> <binary> <vsix>');
}

const version = releaseVersion(process.argv[2]);
const target = process.argv[3];
const executable = targetExecutable(target);
const binary = resolve(process.argv[4]);
const vsix = resolve(process.argv[5]);
const entries = await readEntries(vsix, [
  'extension/package.json',
  'extension.vsixmanifest',
  `extension/bin/${executable}`,
]);
const extensionPackage = JSON.parse(entries.get('extension/package.json').content.toString('utf8'));
if (extensionPackage.version !== version) {
  throw new Error(
    `VSIX package version mismatch: expected ${version}, received ${extensionPackage.version}`,
  );
}
const manifest = entries.get('extension.vsixmanifest').content.toString('utf8');
if (!manifest.includes(`Version="${version}"`) || !manifest.includes(`TargetPlatform="${target}"`)) {
  throw new Error(`VSIX manifest does not identify version ${version} and target ${target}`);
}
const embeddedEntry = entries.get(`extension/bin/${executable}`);
const embedded = embeddedEntry.content;
if (target !== 'win32-x64' && ((embeddedEntry.attributes >>> 16) & 0o111) === 0) {
  throw new Error('VSIX embedded CLI is not executable');
}
const expectedHash = createHash('sha256').update(readFileSync(binary)).digest('hex');
const embeddedHash = createHash('sha256').update(embedded).digest('hex');
if (embeddedHash !== expectedHash) {
  throw new Error(`VSIX embedded CLI hash mismatch: expected ${expectedHash}, received ${embeddedHash}`);
}
const directory = mkdtempSync(join(tmpdir(), 'norm-vsix-'));
try {
  const extracted = join(directory, executable);
  writeFileSync(extracted, embedded);
  chmodSync(extracted, 0o755);
  verifyCliVersion(extracted, version);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
console.log(`VSIX verified for ${target} with embedded Norm ${version}.`);

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
