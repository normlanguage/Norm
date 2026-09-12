import { spawn } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
export const repositoryRoot = resolve(docsRoot, '..')

export async function runNorm(args) {
  const java = process.env.JAVA_HOME
    ? resolve(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java'
  const quoted = args.map(value => `"${value.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"`).join(' ')
  await new Promise((accept, reject) => {
    const child = spawn(java, [
      '-Xmx64m', '-Xms64m', '-jar', resolve(repositoryRoot, 'gradle/wrapper/gradle-wrapper.jar'),
      ':compiler:run', `--args=${quoted}`, '--no-daemon',
    ], { cwd: repositoryRoot, stdio: 'inherit' })
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (code === 0) accept()
      else reject(new Error(`norm ${args[0]} failed (${signal ?? code}); see compiler diagnostics above`))
    })
  })
}
