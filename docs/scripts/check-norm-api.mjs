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
validate(validateModule, await json(resolve(apiRoot, 'module.api.json')), 'module.api.json')
for (const path of await apiFiles(apiRoot)) {
  if (path.endsWith('module.api.json')) continue
  validate(validateFile, await json(path), path)
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
