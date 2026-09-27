import { cpSync, existsSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { buildRuntime } from '../../../compiler/scripts/build-runtime.mjs';

export function stageServerDistribution(distribution, extensionRoot) {
  const source = resolve(distribution);
  const extension = resolve(extensionRoot);
  const server = join(extension, 'server');
  if (!existsSync(join(source, 'bin')) || !existsSync(join(source, 'lib'))) {
    throw new Error(`Invalid Norm server distribution: ${source}`);
  }
  if (dirname(server) !== extension) throw new Error(`Invalid extension root: ${extension}`);
  rmSync(server, { recursive: true, force: true });
  cpSync(source, server, { recursive: true });
  return server;
}

export function buildServer(version) {
  const extensionRoot = resolve(import.meta.dirname, '..');
  const repository = resolve(extensionRoot, '..', '..', '..');
  const selectedVersion = version ?? JSON.parse(readFileSync(join(extensionRoot, 'package.json'), 'utf8')).version;
  return stageServerDistribution(buildRuntime(repository, selectedVersion), extensionRoot);
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  buildServer();
}
