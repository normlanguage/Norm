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



const banner = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 400" role="img" aria-label="Norm — Familiar syntax. Explicit semantics.">
  <rect width="1280" height="400" rx="20" fill="#0d1117"/>
  <svg x="72" y="72" width="80" height="80">${source.toString().trim()}</svg>
  <g font-family="Segoe UI, Arial, sans-serif" fill="#f0f6fc">
    <text x="176" y="130" font-size="56" font-weight="700">Norm</text>
    <text x="72" y="235" font-size="48" font-weight="600">Familiar syntax.</text>
    <text x="72" y="297" font-size="48" font-weight="600" fill="#79b8ff">Explicit semantics.</text>
    <text x="74" y="350" font-size="20" fill="#a9b7c6">A statically typed, application-oriented language.</text>
  </g>
  <g transform="translate(980 112) rotate(12 90 90)" opacity="0.16">
    <svg width="180" height="180">${source.toString().trim()}</svg>
  </g>
</svg>
`
await writeFile(resolve(brandRoot, 'norm-banner.svg'), banner)
await sharp(Buffer.from(banner)).png({ compressionLevel: 9 }).toFile(resolve(brandRoot, 'norm-banner.png'))

console.log('Norm brand assets generated.')
