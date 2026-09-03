import { defineConfig } from '@vscode/test-cli';
import { resolve } from 'node:path';

const shared = {
  files: 'out/test/**/*.test.js',
  version: 'stable',
  workspaceFolder: '../../..',
  launchArgs: ['--disable-extensions'],
  mocha: {
    timeout: 30_000,
  },
};

const testCli = resolve(
  process.cwd(),
  '../../compiler/build/vscode-test-server/bin',
  process.platform === 'win32' ? 'norm.bat' : 'norm',
);

export default defineConfig([
  { ...shared, label: 'jvm', env: { NORM_CLI: testCli } },
  { ...shared, label: 'release' },
]);
