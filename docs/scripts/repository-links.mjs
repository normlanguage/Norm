import { existsSync, statSync } from 'node:fs'
import { dirname, relative, resolve, sep } from 'node:path'
import { docsRoot, repositoryRoot } from './run-norm.mjs'
import { repositoryUrl } from './site-config.mjs'

export function repositoryLinks(md) {
  md.core.ruler.after('inline', 'repository-links', state => {
    for (const block of state.tokens) {
      for (const token of block.children ?? []) {
        if (token.type !== 'link_open') continue
        const href = token.attrGet('href')
        if (!href?.startsWith('.')) continue
        const [path] = href.split(/[?#]/, 1)
        const target = resolve(dirname(state.env.path), decodeURIComponent(path))
        const docsRelative = relative(docsRoot, target)
        if (docsRelative !== '..' && !docsRelative.startsWith(`..${sep}`)) continue
        const source = relative(repositoryRoot, target)
        if (source === '..' || source.startsWith(`..${sep}`) || !existsSync(target)) {
          throw new Error(`Repository link does not exist: ${href} in ${state.env.path}`)
        }
        const kind = statSync(target).isDirectory() ? 'tree' : 'blob'
        const encoded = source.split(sep).map(encodeURIComponent).join('/')
        token.attrSet('href', `${repositoryUrl}/${kind}/main/${encoded}${href.slice(path.length)}`)
      }
    }
  })
}
