import { resolve } from 'node:path'
import { docsRoot, repositoryRoot, runNorm } from './run-norm.mjs'

await runNorm([
  'docs',
  resolve(repositoryRoot, 'norm', 'stdlib', 'std'),
  '--output',
  resolve(docsRoot, 'public', 'api', 'std'),
  '--strict',
])
