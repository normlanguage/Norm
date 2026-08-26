import { cpSync, existsSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

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
  const repository = resolve(extensionRoot, '..', '..', '..', '..');
  const wrapper = resolve(repository, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : wrapper;
  const args =
    process.platform === 'win32'
      ? ['/d', '/c', 'call', wrapper, ':cli:installVsCodeTestServer']
      : [':cli:installVsCodeTestServer'];
  const result = spawnSync(command, args, { cwd: repository, stdio: 'inherit' });

  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Norm server build exited with ${result.status}`);
  return stageServerDistribution(
    join(repository, 'tool', 'cli', 'app', 'build', 'vscode-test-server'),
    extensionRoot,
  );
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  buildServer();
}
