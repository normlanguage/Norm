import { readFile, readdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import Ajv2020 from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const schemasRoot = resolve(docsRoot, 'public', 'schemas')
const apiRoot = resolve(docsRoot, 'public', 'api', 'std')
const common = await json(resolve(schemasRoot, 'norm-api-v1.json'))
const moduleSchema = await json(resolve(schemasRoot, 'module-api-v1.json'))
const fileSchema = await json(resolve(schemasRoot, 'file-api-v1.json'))
const ajv = new Ajv2020({ allErrors: true })
addFormats(ajv)
ajv.addSchema(common)
const validateModule = ajv.compile(moduleSchema)
const validateFile = ajv.compile(fileSchema)
const descriptions = new Set()
const moduleDocument = await json(resolve(apiRoot, 'module.api.json'))
validate(validateModule, moduleDocument, 'module.api.json')
collectDescriptions(moduleDocument, descriptions)
for (const path of await apiFiles(apiRoot)) {
  if (path.endsWith('module.api.json')) continue
  const document = await json(path)
  validate(validateFile, document, path)
  collectDescriptions(document, descriptions)
}
const translations = await json(resolve(docsRoot, 'translations', 'zh-CN', 'api.json'))
const missing = [...descriptions].filter(description =>
  typeof translations[description] !== 'string' || !translations[description].trim(),
)
const stale = Object.keys(translations).filter(description => !descriptions.has(description))
if (missing.length || stale.length) {
  throw new Error(`Chinese API descriptions: ${missing.length} missing, ${stale.length} stale\n${[
    ...missing.map(description => `missing: ${description}`),
    ...stale.map(description => `stale: ${description}`),
  ].join('\n')}`)
}

async function json(path) {
  return JSON.parse(await readFile(path, 'utf8'))
}

async function apiFiles(directory) {
  const result = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) result.push(...await apiFiles(path))
    else if (entry.name.endsWith('.api.json')) result.push(path)
  }
  return result
}

function validate(validator, value, path) {
  if (validator(value)) return
  throw new Error(`${path}: ${ajv.errorsText(validator.errors, { separator: '\n' })}`)
}

function collectDescriptions(value, descriptions) {
  if (!value || typeof value !== 'object') return
  if (Array.isArray(value)) {
    for (const item of value) collectDescriptions(item, descriptions)
    return
  }
  for (const [key, child] of Object.entries(value)) {
    if (key === 'description' && typeof child === 'string' && child.trim()) descriptions.add(child)
    collectDescriptions(child, descriptions)
  }
}
