import assert from 'node:assert/strict'
import { access, readdir, readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { docsRoot, repositoryRoot } from './brand-config.mjs'
import { repositoryName, siteBase, siteManifest, siteOrigin, siteUrl } from './site-config.mjs'

const manifestPath = resolve(docsRoot, 'public', 'site.webmanifest')
assert.deepEqual(JSON.parse(await readFile(manifestPath, 'utf8')), siteManifest)
await access(resolve(docsRoot, 'guide', 'index.md'))

const wrongCaseBase = `/${repositoryName.toLowerCase()}/`
const wrongCaseUrl = `${siteOrigin}${wrongCaseBase}`
if (wrongCaseBase !== siteBase) {
  const sourceFiles = [
    resolve(repositoryRoot, 'README.md'),
    resolve(repositoryRoot, 'CONTRIBUTING.md'),
    ...(await textFiles(resolve(repositoryRoot, '.github'))),
    ...(await textFiles(docsRoot)),
  ]
  for (const path of sourceFiles) {
    const source = await readFile(path, 'utf8')
    assert.equal(source.includes(wrongCaseUrl), false, `${path} contains ${wrongCaseUrl}`)
    assert.doesNotMatch(
      source,
      new RegExp(`['"]${wrongCaseBase.replaceAll('/', '\\/')}`),
      `${path} contains ${wrongCaseBase}`,
    )
  }
}

if (process.argv.includes('--dist')) {
  const html = await readFile(resolve(docsRoot, '.vitepress', 'dist', 'index.html'), 'utf8')
  const guide = await readFile(
    resolve(docsRoot, '.vitepress', 'dist', 'guide', 'index.html'),
    'utf8',
  )
  const learn = await readFile(
    resolve(docsRoot, '.vitepress', 'dist', 'learn', 'index.html'),
    'utf8',
  )
  assert.match(html, new RegExp(`(?:href|src)="${siteBase.replaceAll('/', '\\/')}`))
  assert.match(guide, /<h1[^>]*>Language/)
  assert.match(learn, /<h1[^>]*>Language Tour/)
  assert.equal(html.includes(wrongCaseBase), false)
  assert.equal(html.includes(wrongCaseUrl), false)
  assert.deepEqual(
    JSON.parse(await readFile(resolve(docsRoot, '.vitepress', 'dist', 'site.webmanifest'), 'utf8')),
    siteManifest,
  )
  await assertInternalLinks(resolve(docsRoot, '.vitepress', 'dist'))
}

console.log(`Norm site path verified: ${siteUrl}`)

async function textFiles(directory) {
  const paths = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'dist') continue
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) {
      paths.push(...(await textFiles(path)))
    } else if (/\.(?:json|md|mjs|mts|ts|ya?ml)$/.test(entry.name)) {
      paths.push(path)
    }
  }
  return paths
}

async function assertInternalLinks(directory) {
  const pages = await filesWithExtension(directory, '.html')
  const routes = new Set()
  for (const path of pages) {
    const relative = path.slice(directory.length + 1).replaceAll('\\', '/')
    if (relative === 'index.html') {
      routes.add(siteBase)
      continue
    }
    if (relative.endsWith('/index.html')) {
      const route = `${siteBase}${relative.slice(0, -'index.html'.length)}`
      routes.add(route)
      routes.add(route.slice(0, -1))
      continue
    }
    routes.add(`${siteBase}${relative}`)
    routes.add(`${siteBase}${relative.slice(0, -'.html'.length)}`)
  }
  for (const path of pages) {
    const source = await readFile(path, 'utf8')
    for (const match of source.matchAll(/<a\b[^>]*\bhref="([^"]+)"/g)) {
      const href = match[1].split('#', 1)[0].split('?', 1)[0]
      if (!href || !href.startsWith(siteBase)) continue
      assert.equal(routes.has(href), true, `${path} links to missing route ${href}`)
    }
  }
}

async function filesWithExtension(directory, extension) {
  const paths = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) paths.push(...(await filesWithExtension(path, extension)))
    else if (entry.name.endsWith(extension)) paths.push(path)
  }
  return paths
}
