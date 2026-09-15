import { chmodSync, createWriteStream, mkdirSync, readFileSync } from 'node:fs';
import { dirname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import yauzl from 'yauzl';
import { releaseVersion } from '../../../compiler/scripts/release-model.mjs';

export function extractPrefix(path, prefix, destinationRoot) {
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

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 5) throw new Error('Usage: extract-vsix.mjs <vsix> <destination> <version>');
  const version = releaseVersion(process.argv[4]);
  await extractPrefix(process.argv[2], 'extension/', process.argv[3]);
  const manifest = JSON.parse(readFileSync(resolve(process.argv[3], 'package.json'), 'utf8'));
  if (manifest.version !== version) throw new Error(`VSIX version mismatch: ${manifest.version} != ${version}`);
}
