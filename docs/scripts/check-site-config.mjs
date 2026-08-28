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
  assert.match(html, new RegExp(`(?:href|src)="${siteBase.replaceAll('/', '\\/')}`))
  assert.match(guide, /<h1[^>]*>认识 Norm/)
  assert.equal(html.includes(wrongCaseBase), false)
  assert.equal(html.includes(wrongCaseUrl), false)
  assert.deepEqual(
    JSON.parse(await readFile(resolve(docsRoot, '.vitepress', 'dist', 'site.webmanifest'), 'utf8')),
    siteManifest,
  )
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
