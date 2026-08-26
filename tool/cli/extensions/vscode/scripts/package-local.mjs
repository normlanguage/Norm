import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import yauzl from 'yauzl';
import { buildServer } from './build-server.mjs';
import { releaseTargets, releaseVersion } from './release-package.mjs';
import { packageVsix } from './vsce-package.mjs';

export function localPackageName(version) {
  return `norm-language-support-${releaseVersion(version)}-local.vsix`;
}

export async function packageLocal() {
  const extensionRoot = resolve(import.meta.dirname, '..');
  const metadata = JSON.parse(readFileSync(join(extensionRoot, 'package.json'), 'utf8'));
  const version = releaseVersion(metadata.version);
  const server = buildServer();
  verifyServerVersion(server, version);
  const destination = join(extensionRoot, localPackageName(version));
  packageVsix({
    extensionRoot,
    destination,
  });
  await verifyPackage(destination, version);
  console.log(`Created ${destination}`);
  return destination;
}

function verifyServerVersion(server, version) {
  const launcher = join(server, 'bin', process.platform === 'win32' ? 'norm.bat' : 'norm');
  const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : launcher;
  const args =
    process.platform === 'win32' ? ['/d', '/c', 'call', launcher, '--version'] : ['--version'];
  const result = spawnSync(command, args, { encoding: 'utf8' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Norm server version check exited with ${result.status}`);
  const actual = result.stdout.trim();
  if (actual !== `norm ${version}` && actual !== `norm ${version}-SNAPSHOT`) {
    throw new Error(`Norm server version does not match extension ${version}: ${actual}`);
  }
}

function verifyPackage(path, version) {
  const expected = new Set([
    'extension/package.json',
    `extension/server/bin/${process.platform === 'win32' ? 'norm.bat' : 'norm'}`,
  ]);
  const expectedReleaseEntries = new Set(
    releaseTargets.map(({ target, executable }) => `extension/bin/${target}/${executable}`),
  );
  return new Promise((resolvePromise, reject) => {
    yauzl.open(path, { lazyEntries: true }, (openError, archive) => {
      if (openError) return reject(openError);
      let packagedVersion;
      const packagedReleaseEntries = new Set();
      archive.on('error', reject);
      archive.on('entry', (entry) => {
        if (entry.fileName.startsWith('extension/bin/')) {
          packagedReleaseEntries.add(entry.fileName);
        }
        if (!expected.has(entry.fileName)) return archive.readEntry();
        expected.delete(entry.fileName);
        if (entry.fileName !== 'extension/package.json') return archive.readEntry();
        archive.openReadStream(entry, (streamError, stream) => {
          if (streamError) return reject(streamError);
          const chunks = [];
          stream.on('data', (chunk) => chunks.push(chunk));
          stream.on('error', reject);
          stream.on('end', () => {
            packagedVersion = JSON.parse(Buffer.concat(chunks).toString('utf8')).version;
            archive.readEntry();
          });
        });
      });
      archive.on('end', () => {
        if (expected.size > 0) return reject(new Error(`VSIX is missing: ${[...expected].join(', ')}`));
        if (
          packagedReleaseEntries.size > 0 &&
          (packagedReleaseEntries.size !== expectedReleaseEntries.size ||
            [...expectedReleaseEntries].some((entry) => !packagedReleaseEntries.has(entry)))
        ) {
          return reject(new Error('Local VSIX contains an incomplete release CLI bundle'));
        }
        if (packagedVersion !== version) {
          return reject(
            new Error(`VSIX version mismatch: expected ${version}, received ${packagedVersion}`),
          );
        }
        resolvePromise();
      });
      archive.readEntry();
    });
  });
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  await packageLocal();
}
