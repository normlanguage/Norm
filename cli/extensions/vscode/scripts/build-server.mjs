import { cpSync, existsSync, rmSync } from 'node:fs';
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

export function buildServer() {
  const extensionRoot = resolve(import.meta.dirname, '..');
  const repository = resolve(extensionRoot, '..', '..', '..');
  return stageServerDistribution(buildRuntime(repository), extensionRoot);
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  buildServer();
}
