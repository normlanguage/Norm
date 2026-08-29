import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compileFromFile } from 'json-schema-to-typescript'

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const schema = resolve(docsRoot, 'public', 'schemas', 'norm-api-v1.json')
const output = resolve(docsRoot, '.vitepress', 'theme', 'generated', 'norm-api.ts')
const source = await compileFromFile(schema, {
  bannerComment: '',
  cwd: dirname(schema),
  style: { singleQuote: true, semi: false },
})
await mkdir(dirname(output), { recursive: true })
await writeFile(output, source, 'utf8')
