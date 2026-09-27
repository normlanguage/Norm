import { readdir, mkdir, writeFile } from 'node:fs/promises'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { sitePath } from './site-config.mjs'

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const distRoot = resolve(docsRoot, '.vitepress', 'dist')

const pages = await markdownPages(docsRoot)
for (const source of pages) {
  const page = relative(docsRoot, source).replaceAll('\\', '/').replace(/\.md$/, '')
  const route = page === 'index' ? '' : page.endsWith('/index') ? page.slice(0, -5) : page
  const target = sitePath(route)
  const output = resolve(distRoot, 'en', `${page}.html`)
  await mkdir(dirname(output), { recursive: true })
  const quoted = JSON.stringify(target)
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="robots" content="noindex"><link rel="canonical" href="${target}"></head><body><script>location.replace(${quoted} + location.search + location.hash)</script><a href="${target}">Continue to English documentation</a></body></html>\n`
  await writeFile(output, html, 'utf8')
}

async function markdownPages(directory) {
  const result = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.name === '.vitepress' || entry.name === 'node_modules' || entry.name === 'zh') continue
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) result.push(...await markdownPages(path))
    else if (entry.name.endsWith('.md')) result.push(path)
  }
  return result
}
