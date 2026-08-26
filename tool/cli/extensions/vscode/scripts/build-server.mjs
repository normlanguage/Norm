import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const repository = resolve(process.cwd(), '..', '..', '..', '..');
const wrapper = resolve(repository, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : wrapper;
const args =
  process.platform === 'win32'
    ? ['/d', '/c', 'call', wrapper, ':cli:installVsCodeTestServer']
    : [':cli:installVsCodeTestServer'];
const result = spawnSync(command, args, { cwd: repository, stdio: 'inherit' });

if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);
