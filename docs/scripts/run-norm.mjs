import { spawn } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildRuntime } from '../../cli/compiler/scripts/build-runtime.mjs'

export const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
export const repositoryRoot = resolve(docsRoot, '..')

export async function runNorm(args) {
  const distribution = buildRuntime(repositoryRoot)
  const launcher = resolve(distribution, 'bin', process.platform === 'win32' ? 'norm.bat' : 'norm')
  const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : launcher
  const invocation = process.platform === 'win32' ? ['/d', '/c', 'call', launcher, ...args] : args
  await new Promise((accept, reject) => {
    const child = spawn(command, invocation, { cwd: repositoryRoot, stdio: 'inherit' })
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (code === 0) accept()
      else reject(new Error(`norm ${args[0]} failed (${signal ?? code}); see compiler diagnostics above`))
    })
  })
}
