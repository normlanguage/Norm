import assert from 'node:assert/strict'
import { resolve } from 'node:path'
import test from 'node:test'
import MarkdownIt from 'markdown-it'
import { repositoryLinks } from './repository-links.mjs'
import { docsRoot } from './run-norm.mjs'

test('repository source links resolve before rendering while site links stay local', () => {
  const md = new MarkdownIt().use(repositoryLinks)
  const env = { path: resolve(docsRoot, 'design/release-process.md') }
  const html = md.render('[source](../../cli/compiler/build.gradle.kts#L1) [folder](../../cli/compiler/scripts/) [page](../index.md) [external](https://example.com)', env)
  assert.ok(html.includes('https://github.com/normlanguage/Norm/blob/main/cli/compiler/build.gradle.kts#L1'))
  assert.ok(html.includes('https://github.com/normlanguage/Norm/tree/main/cli/compiler/scripts'))
  assert.ok(html.includes('href="../index.md"'))
  assert.ok(html.includes('href="https://example.com"'))
  assert.throws(() => md.render('[missing](../../cli/not-a-real-source.java)', env), /Repository link does not exist/)
})
