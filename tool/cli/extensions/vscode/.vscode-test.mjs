import { defineConfig } from '@vscode/test-cli';

export default defineConfig({
  files: 'out/test/**/*.test.js',
  version: 'stable',
  workspaceFolder: '../../../..',
  launchArgs: ['--disable-extensions'],
  mocha: {
    timeout: 30_000,
  },
});
