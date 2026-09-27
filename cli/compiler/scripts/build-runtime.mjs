import { spawnSync } from 'node:child_process';
import { join, resolve } from 'node:path';

export function buildRuntime(repository) {
  const root = resolve(repository);
  const wrapper = join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : wrapper;
  const args = process.platform === 'win32'
    ? ['/d', '/c', 'call', wrapper, ':compiler:installRuntimeDist', '--no-daemon']
    : [':compiler:installRuntimeDist', '--no-daemon'];
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Norm runtime build exited with ${result.status}`);
  return join(root, 'build', 'compiler', 'norm-runtime');
}
