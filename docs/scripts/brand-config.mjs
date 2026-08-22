import { resolve } from 'node:path'

export const docsRoot = resolve(import.meta.dirname, '..')
export const repositoryRoot = resolve(docsRoot, '..')
export const brandRoot = resolve(docsRoot, 'public', 'brand')
export const extensionImages = resolve(
  repositoryRoot,
  'tool',
  'cli',
  'extensions',
  'vscode',
  'images',
)
export const sizes = [16, 24, 32, 48, 64, 128, 180, 192, 256, 512, 1024]
export const icoSizes = [16, 24, 32, 48, 64, 128, 256]
