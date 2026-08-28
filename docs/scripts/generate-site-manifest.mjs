import { writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { docsRoot } from './brand-config.mjs'
import { siteManifest } from './site-config.mjs'

await writeFile(
  resolve(docsRoot, 'public', 'site.webmanifest'),
  `${JSON.stringify(siteManifest, null, 2)}\n`,
)
