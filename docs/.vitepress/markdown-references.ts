import { relative, resolve, sep } from 'node:path'
import type { Plugin } from 'vite'
import { docsRoot, repositoryRoot, runNorm } from '../scripts/run-norm.mjs'

export function markdownReferences(root = docsRoot, moduleRoot = resolve(repositoryRoot, 'norm/stdlib/std')): Plugin {
  const check = () => runNorm(['docs', 'check', root, '--module', moduleRoot])
  let initialCheck: Promise<void> | undefined
  let pending = Promise.resolve()
  let timer: ReturnType<typeof setTimeout> | undefined

  return {
    name: 'norm-markdown-references',
    async buildStart() {
      await (initialCheck ??= check())
    },
    configureServer(server) {
      server.watcher.add(moduleRoot)
      const changed = (event: string, file: string) => {
        if (!['add', 'change', 'unlink'].includes(event)) return
        const sourceRoot = file.toLowerCase().endsWith('.md') ? root : file.endsWith('.norm') ? moduleRoot : undefined
        if (!sourceRoot) return
        const path = relative(sourceRoot, file)
        if (path.startsWith('..') || path.split(sep).some(part => part.startsWith('.') || part === 'node_modules')) return
        clearTimeout(timer)
        timer = setTimeout(() => {
          pending = pending.then(check).then(() => {
            server.ws.send({ type: 'full-reload' })
          }).catch(error => {
            server.config.logger.error(String(error))
            server.ws.send({ type: 'error', err: { message: String(error), stack: '', plugin: 'norm-markdown-references' } })
          })
        }, 150)
      }
      server.watcher.on('all', changed)
      server.httpServer?.once('close', () => {
        clearTimeout(timer)
        server.watcher.off('all', changed)
      })
    },
  }
}
