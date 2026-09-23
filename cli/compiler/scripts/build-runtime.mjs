import { spawnSync } from 'node:child_process';
import { join, resolve } from 'node:path';

export function buildRuntime(repository) {
  const root = resolve(repository);
  const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : 'mvn';
  const args = process.platform === 'win32'
    ? ['/d', '/c', 'call', 'mvn', '-DskipTests', 'package']
    : ['-DskipTests', 'package'];
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Norm runtime build exited with ${result.status}`);
  return join(root, 'cli', 'compiler', 'target', 'norm-runtime');
}
