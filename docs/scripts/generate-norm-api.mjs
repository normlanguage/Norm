import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repositoryRoot = resolve(docsRoot, '..')
const windows = process.platform === 'win32'
const gradle = resolve(repositoryRoot, windows ? 'gradlew.bat' : 'gradlew')
run(gradle, [':cli:installDist', '--no-daemon'])
const cli = resolve(
  repositoryRoot,
  'tool',
  'cli',
  'app',
  'build',
  'install',
  'norm',
  'bin',
  windows ? 'norm.bat' : 'norm',
)
run(cli, [
  'docs',
  resolve(repositoryRoot, 'norm', 'stdlib', 'std'),
  '--output',
  resolve(docsRoot, 'public', 'api', 'std'),
  '--strict',
])

function run(command, args) {
  const executable = windows ? process.env.ComSpec ?? 'cmd.exe' : command
  const executableArgs = windows ? ['/d', '/s', '/c', command, ...args] : args
  const result = spawnSync(executable, executableArgs, {
    cwd: repositoryRoot,
    stdio: 'inherit',
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status ?? 1)
}
