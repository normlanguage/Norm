import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import pngToIco from 'png-to-ico'
import sharp from 'sharp'
import { brandRoot, extensionImages, icoSizes, sizes } from './brand-config.mjs'

const sourcePath = resolve(brandRoot, 'norm.svg')

await mkdir(brandRoot, { recursive: true })
await mkdir(extensionImages, { recursive: true })

const source = await readFile(sourcePath)
await Promise.all(
  sizes.map((size) =>
    sharp(source)
      .resize(size, size)
      .png({ compressionLevel: 9 })
      .toFile(resolve(brandRoot, `norm-${size}.png`)),
  ),
)

await writeFile(
  resolve(brandRoot, 'norm.ico'),
  await pngToIco(icoSizes.map((size) => resolve(brandRoot, `norm-${size}.png`))),
)
await copyFile(resolve(brandRoot, 'norm-256.png'), resolve(extensionImages, 'norm-256.png'))
await copyFile(resolve(brandRoot, 'norm-32.png'), resolve(extensionImages, 'norm-file.png'))

console.log('Norm brand assets generated.')
