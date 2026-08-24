import { spawnSync } from 'node:child_process';
import { cpSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';

const repository = resolve(process.cwd(), '..', '..', '..', '..');
const wrapper = resolve(repository, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : wrapper;
const args =
  process.platform === 'win32'
    ? ['/d', '/c', 'call', wrapper, ':cli:installDist']
    : [':cli:installDist'];
const result = spawnSync(command, args, { cwd: repository, stdio: 'inherit' });

if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);

const source = resolve(repository, 'tool', 'cli', 'app', 'build', 'install', 'norm');
const target = resolve(process.cwd(), 'server');
rmSync(target, { recursive: true, force: true });
cpSync(source, target, { recursive: true });
